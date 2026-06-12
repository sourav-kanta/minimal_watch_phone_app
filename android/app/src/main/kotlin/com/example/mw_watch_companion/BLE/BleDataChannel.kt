package com.example.mw_watch_companion.BLE

import android.util.Log
import android.os.Handler

import com.example.mw_watch_companion.common.DataQueue
import com.example.mw_watch_companion.common.DataConsumer

class BleDataChannel (){

    val rxQueue = DataQueue<BleMessage>("WatchRxQueue")
    val txQueue = DataQueue<BleMessage>("WatchTxQueue")

    private val rxIngestor = BleRequestHandler(txQueue)
    private var txIngestor = BleResponseHandler(BleConnectionManager.effectiveMtu)

    private val rxConsumer = DataConsumer<BleMessage>(rxQueue, rxIngestor, "MWRxConsumer")
    private var txConsumer = DataConsumer<BleMessage>(txQueue, txIngestor, "MWTxConsumer")

    private val rxParser = BlePacketParser(rxQueue)
    private var txburstManager = BleBurstManager(BleServiceParams.connInterval, txIngestor) 

    init {
        start()
        pause()
    }

    @Synchronized
    fun updateMtu(mtu: Int) {
        // Drops the current TX buffers and creates new ones
        // as we can no longer use the old packets
        // In future we may write a refuribish packet method 
        // that consumes unsent packets and creates new packets 
        // out of them for updated MTU
        txIngestor = BleResponseHandler(mtu)
        txConsumer.stop()
        txConsumer = DataConsumer<BleMessage>(txQueue, txIngestor, "NewMWTxConsumer")
        txConsumer.start()
        txConsumer.pause()
        txburstManager = BleBurstManager(BleServiceParams.connInterval, txIngestor)
        Log.i("BleDataChannel", "Hotswapped TX channel")
    }

    @Synchronized
    fun start() {
        Log.i("BleDataChannel", "Initializing data link lanes...")
        rxConsumer.start()
        txConsumer.start()
    }

    @Synchronized
    fun pause() {
        Log.i("BleDataChannel", "Pausing data link lanes...")
        rxConsumer.pause()
        txConsumer.pause()
        txburstManager.stop()
    }

    @Synchronized
    fun resume() {
        Log.i("BleDataChannel", "Resuming data link lanes...")
        rxConsumer.resume()
        txConsumer.resume()
        txburstManager.start()
    }

    fun acceptNewPacket(mainHandler:Handler, rawBytes: ByteArray) {
        mainHandler.post {
            rxParser.parseNextPacket(rawBytes.asUByteArray())
        }
    }

    fun acceptNotification(notification: UByteArray) {
        val hdrBytes = ubyteArrayOf(BleServiceParams.msgHeaderMagic, BleOpCode.NOTIFICATION_SEND.raw,
                                    0u, notification.size.toUByte())
        val hdr = BleMessageHeader.parseHeader(hdrBytes)?: return 
        val msg = BleMessage(hdr).apply { this.payload = notification }
        txQueue.push(msg)
    }

}