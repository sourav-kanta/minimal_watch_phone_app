package com.example.mw_watch_companion.BLE

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.UUID

import com.example.mw_watch_companion.common.NotificationForwarder

@SuppressLint("MissingPermission")
object BleConnectionManager: NotificationForwarder{
    var connectionState = BleConnectionState.DISCONNECTED
        private set 
    @Volatile
    var effectiveMtu = 20
        private set
    private var bluetoothGatt: BluetoothGatt? = null
    private var retryCount = 0
    private const val MAX_RETRIES = 5
    private val mainHandler = Handler(Looper.getMainLooper())
    private var dataChannel: BleDataChannel = BleDataChannel()
    private var appContext: Context? = null
    
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            Log.i("BLE", "status=$status newState=$newState")
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.i("BLE_Manager", "Connected to device")
                    Log.i("BLE Manager", "State changed : ${connectionState.name} -> ${BleConnectionState.CONNECTED.name}")
                    connectionState = BleConnectionState.CONNECTED
                    retryCount = 0
                    Log.i("BLE Manager", "State changed : ${connectionState.name} -> ${BleConnectionState.MTU_REQUESTED.name}")
                    connectionState = BleConnectionState.MTU_REQUESTED
                    val success = gatt?.requestMtu(BleServiceParams.maxMTU) ?: false
                    if(!success) {
                        Log.i("BLE Manager", "State changed : ${connectionState.name} -> ${BleConnectionState.MTU_REJECTED.name}")
                        connectionState = BleConnectionState.MTU_REJECTED
                        // Check return type
                        gatt?.discoverServices()
                        Log.i("BLE Manager", "State changed : ${connectionState.name} -> ${BleConnectionState.SUBSCRIPTION_STARTED.name}")
                        connectionState = BleConnectionState.SUBSCRIPTION_STARTED
                    }
                    
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.i("BLE_Manager", "Disconnected from device")
                    if(connectionState != BleConnectionState.DISCONNECTED) {
                        val device = gatt.device
                        dataChannel.pause()
                        closeGatt()
                        handleRetryLogic(device)
                    }
                }
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
            if(status == BluetoothGatt.GATT_SUCCESS) {
                effectiveMtu = if(mtu>=3) mtu-3 else 0
                Log.i("BleManager", "Updated MTU to $effectiveMtu")
                // Pause the consumers, hotswap the txChannel and
                // then resume work
                dataChannel.pause()
                dataChannel.updateMtu(effectiveMtu)
                // Already subscribed just updated MTU
                if(connectionState!= BleConnectionState.READY) {
                    // Check return
                    gatt?.discoverServices()
                }
                else {
                    dataChannel.resume()
                }  
            }
            else {
                Log.i("BLE Manager", "State changed : ${connectionState.name} -> ${BleConnectionState.MTU_REJECTED.name}")
                connectionState = BleConnectionState.MTU_REJECTED
                // Check return 
                gatt?.discoverServices()
                Log.i("BLE Manager", "State changed : ${connectionState.name} -> ${BleConnectionState.SUBSCRIPTION_STARTED.name}")
                connectionState = BleConnectionState.SUBSCRIPTION_STARTED            
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if(status == BluetoothGatt.GATT_SUCCESS) {
                Log.i("BleManager", "Discovered Services")
                val success = subscribeToCharacteristic(BleServiceParams.SERVICE_UUID, BleServiceParams.TX_CHAR_UUID)  
                if(!success) {
                    Log.i("BleManager", "Failed subscribing to Services")
                    disconnectDevice(gatt)
                }
                else {
                    Log.i("BLE Manager", "State changed : ${connectionState.name} -> ${BleConnectionState.SUBSCRIPTION_STARTED.name}")
                    connectionState = BleConnectionState.SUBSCRIPTION_STARTED
                }
            }
            else {
                Log.i("BleManager", "Failed discovering Services")
                disconnectDevice(gatt)
            }
        }

        // Android 13+
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            if(characteristic.uuid == BleServiceParams.TX_CHAR_UUID) {
                val rawBytes: ByteArray = value.clone()
                Log.i("BleManager", "Received notification of size ${rawBytes.size}")
                val hexData = rawBytes.joinToString(separator = " ") { String.format("%02X", it) }
                dataChannel.acceptNewPacket(mainHandler, rawBytes)
                Log.i("BleManager", "Data : [ $hexData ]")
            
            }
        }

        // Android 12
        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if(characteristic.uuid == BleServiceParams.TX_CHAR_UUID) {
                val rawBytes = characteristic.value?.clone() ?: return
                Log.i("BleManager", "Received notification of size ${rawBytes.size}")
                val hexData = rawBytes.joinToString(separator = " ") { String.format("%02X", it) }
                dataChannel.acceptNewPacket(mainHandler, rawBytes)
                Log.i("BleManager", "Data : [ $hexData ]")
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            val CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
            if (status == BluetoothGatt.GATT_SUCCESS && descriptor.uuid == CCCD_UUID) {
                val parentCharUuid = descriptor.characteristic.uuid
                Log.d("BLE", "Successfully subscribed to notifications for: $parentCharUuid")
                Log.i("BLE Manager", "State changed : ${connectionState.name} -> ${BleConnectionState.READY.name}")
                connectionState = BleConnectionState.READY
                dataChannel.resume() 
            }
            else {
                Log.i("BleManager", "Failed discovering Services")
                disconnectDevice(gatt)
            }
        }
    }

    // Android 13+
    fun connect(context: Context, device: BluetoothDevice) {
        if(connectionState == BleConnectionState.DISCONNECTED) {
            appContext = context.applicationContext

            mainHandler.post {
                bluetoothGatt = device.connectGatt(context, false, gattCallback)
            }
        }
        else { 
            Log.e("BleManager", "Already in connected state")
        }
    }

    // Android 12
    fun connect(context: Context, macid: String) {
        appContext = context.applicationContext
        val bleManager: BluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bleManager.adapter
        val device:BluetoothDevice? = bluetoothAdapter?.getRemoteDevice(macid)

        if( device!= null) {
            val deviceName = device.name ?: "Unknown device"
            Log.i("BleManager", "Trying to connect to $deviceName")
            connect(context, device)
        }
        else {
            Log.i("BleManager", "Unable to fetch device")
        }
    }

    private fun handleRetryLogic(device: BluetoothDevice) {
        val currentContext = appContext ?: return
        if(connectionState == BleConnectionState.DISCONNECTED) {
            if (retryCount < MAX_RETRIES) {
                retryCount++
                val delayMs = (Math.pow(2.0, retryCount.toDouble()) * 1000).toLong()
                mainHandler.postDelayed({
                    if (connectionState == BleConnectionState.DISCONNECTED) {
                        bluetoothGatt = device.connectGatt(currentContext, false, gattCallback)
                    }
                }, delayMs)
            }
            else {
                Log.e("BleManager", "Couldn't reconnect to device")
            }
        }
    }

    // Write to RX characteristic
    @SuppressLint("MissingPermission")
    fun writeCharacteristic(serviceUuid: UUID, charUuid: UUID, payload: ByteArray): Boolean {
        val gatt = bluetoothGatt ?: return false
        val service = gatt.getService(serviceUuid) ?: return false
        val char = service.getCharacteristic(charUuid) ?: return false

        if(connectionState != BleConnectionState.READY) return false

        val writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        val isSuccess = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(char, payload, writeType) == BluetoothStatusCodes.SUCCESS
        } else {
            char.writeType = writeType
            char.value = payload
            gatt.writeCharacteristic(char)
        }

        if (!isSuccess) {
            Log.e("BleConnManager", "!!! PACKET DROPPED !!! Failed to queue write. Size: ${payload.size} bytes. Status: $isSuccess")
        } else {
            Log.d("BleConnManager", "Packet queued successfully. Size: ${payload.size} bytes")
        }

        return isSuccess
    }

    // Subscribe to notifications on the TX char
    private fun subscribeToCharacteristic(serviceUuid: UUID, charUuid: UUID): Boolean {
        val gatt = bluetoothGatt ?: return false
        val service = gatt.getService(serviceUuid) ?: return false
        val char = service.getCharacteristic(charUuid) ?: return false
        
        gatt.setCharacteristicNotification(char, true)
        val descriptor = char.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")) ?: return false
        
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) == BluetoothStatusCodes.SUCCESS
        } else {
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            gatt.writeDescriptor(descriptor)
        }
    }

    fun closeGatt() {
        if(connectionState != BleConnectionState.DISCONNECTED) {
            bluetoothGatt?.close()
            bluetoothGatt = null
            Log.i("BLE Manager", "State changed : ${connectionState.name} -> ${BleConnectionState.DISCONNECTED.name}")
            connectionState = BleConnectionState.DISCONNECTED
        }
    }

    override fun forwardNotification(notification: UByteArray) {
        dataChannel.acceptNotification(notification)
    }

    private fun disconnectDevice(gatt: BluetoothGatt?) {
        val device = gatt?.device
        if(device != null) {
            dataChannel.pause()
            closeGatt()
            handleRetryLogic(device)
        }
        else {
            Log.i("BLE Manager", "State changed : ${connectionState.name} -> ${BleConnectionState.DISCONNECTED.name}")
            connectionState = BleConnectionState.DISCONNECTED
        }
    }
}