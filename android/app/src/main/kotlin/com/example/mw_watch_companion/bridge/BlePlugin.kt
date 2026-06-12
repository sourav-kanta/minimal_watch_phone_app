package com.example.mw_watch_companion.bridge

import android.app.Activity
import android.companion.AssociationRequest
import android.companion.BluetoothLeDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.Context
import android.content.Intent
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanResult
import android.bluetooth.BluetoothDevice
import android.os.Build
import android.util.Log
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.PluginRegistry

import com.example.mw_watch_companion.BLE.BleConnectionManager

class BlePlugin: FlutterPlugin, MethodChannel.MethodCallHandler, EventChannel.StreamHandler,
                 ActivityAware, PluginRegistry.ActivityResultListener {
    private lateinit var methodChannel: MethodChannel
    private lateinit var eventChannel: EventChannel
    private var eventSink: EventChannel.EventSink? = null
    private var activity: Activity? = null
    private var pendingResult: MethodChannel.Result? = null
    private val COMPANION_REQ_CODE = 8998

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        methodChannel = MethodChannel(binding.binaryMessenger, "com.example.mw_watch_companion/methods")
        methodChannel.setMethodCallHandler(this)

        eventChannel = EventChannel(binding.binaryMessenger, "com.example.mw_watch_companion/events")
        eventChannel.setStreamHandler(this)

    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "startCompanionAssociation" -> {
                if (activity == null) {
                    result.error("NO_ACTIVITY", "Valid activity framework instance unavailable", null)
                    return
                }
                pendingResult = result
                disassociateOldDevices()
                launchCompanionPicker()
            }

            else -> result.notImplemented()
        }
    }

    private fun disassociateOldDevices() {
        Log.i("BlePlugin", "Deleting old associations")
        val deviceManager = activity?.getSystemService(Context.COMPANION_DEVICE_SERVICE) as CompanionDeviceManager
        val associations = deviceManager?.associations ?: return
        for(address in associations) {
            deviceManager.disassociate(address)
            Log.i("BlePlugin", "Cleared association $address")
        }
    }

    private fun launchCompanionPicker() {
        Log.i("Plugin", "Selecting new device")
        val ctx = activity ?: return
        val manager = ctx.getSystemService(Context.COMPANION_DEVICE_SERVICE) as CompanionDeviceManager
        val filter = BluetoothLeDeviceFilter.Builder().build()
        val request = AssociationRequest.Builder().setDeviceProfile(AssociationRequest.DEVICE_PROFILE_WATCH)
                                                  .addDeviceFilter(filter).setSingleDevice(false).build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            manager.associate(request, ctx.mainExecutor, object : CompanionDeviceManager.Callback() {
                override fun onAssociationPending(intentSender: android.content.IntentSender) {
                    activity?.startIntentSenderForResult(intentSender, COMPANION_REQ_CODE, null, 0, 0, 0)
                }
                override fun onFailure(error: CharSequence?) {
                    pendingResult?.error("ASSOCIATION_ERROR", error?.toString(), null)
                    pendingResult = null
                }
            })
        } else {
            manager.associate(request, object : CompanionDeviceManager.Callback() {
                override fun onDeviceFound(launcher: android.content.IntentSender) {
                    activity?.startIntentSenderForResult(launcher, COMPANION_REQ_CODE, null, 0, 0, 0)
                }
                override fun onFailure(error: CharSequence?) {
                    Log.e("BlePlugin", "Error : " + error.toString())
                    pendingResult?.error("ASSOCIATION_ERROR", error?.toString(), null)
                    pendingResult = null
                }
            }, null)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (requestCode == COMPANION_REQ_CODE) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                val device: BluetoothDevice? = when {
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                        val info = data.getParcelableExtra(CompanionDeviceManager.EXTRA_ASSOCIATION, android.companion.AssociationInfo::class.java)
                        info?.associatedDevice?.bleDevice?.device ?: info?.associatedDevice?.bluetoothDevice
                    }
                    else -> {
                        val scan: ScanResult? = data.getParcelableExtra(CompanionDeviceManager.EXTRA_DEVICE)
                        scan?.device ?: data.getParcelableExtra(CompanionDeviceManager.EXTRA_DEVICE)
                    }
                }
                if (device != null) {
                    pendingResult?.success(device.address)
                    Log.i("Plugin", device.address)
                    val ctx = activity
                    if(ctx!=null) {
                        BleConnectionManager.connect(ctx, device.address.toString())
                    }
                    val deviceManager = activity?.getSystemService(CompanionDeviceManager::class.java)
                    deviceManager?.startObservingDevicePresence(device.address.toString())
                } else {
                    pendingResult?.error("NULL_DEVICE", "Failed to parse hardware device details", null)
                }
            } else {
                pendingResult?.error("CANCELLED", "User rejected association request", null)
            }
            pendingResult = null
            return true
        }
        return false
    }

    override fun onAttachedToActivity(b: ActivityPluginBinding) { activity = b.activity; b.addActivityResultListener(this) }
    override fun onDetachedFromActivityForConfigChanges() { activity = null }
    override fun onReattachedToActivityForConfigChanges(b: ActivityPluginBinding) { activity = b.activity; b.addActivityResultListener(this) }
    override fun onDetachedFromActivity() { activity = null }
    override fun onListen(a: Any?, e: EventChannel.EventSink?) { eventSink = e }
    override fun onCancel(a: Any?) { eventSink = null }
    override fun onDetachedFromEngine(b: FlutterPlugin.FlutterPluginBinding) {
        methodChannel.setMethodCallHandler(null)
        eventChannel.setStreamHandler(null)
    }
}