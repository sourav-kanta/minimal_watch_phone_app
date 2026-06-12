package com.example.mw_watch_companion.BLE

import java.util.concurrent.ConcurrentLinkedQueue
import android.util.Log

import com.example.mw_watch_companion.common.DataIngestor

class BleResponseHandler(private val targetMtu:Int) : DataIngestor<BleMessage>{

    private val readyPackets = ConcurrentLinkedQueue<ByteArray>()
    private var currMsg: UByteArray = UByteArray(targetMtu)
    private var wPos: Int = 0


    init {
        wPos = 0
        // Set flag to New Packet (0)
        currMsg[wPos++] = 0u 
        readyPackets.clear()
    }

    override fun ingest(message: BleMessage) {
        pack(message)
    }

    @Synchronized
    fun pack(message: BleMessage) {
        val msgData = message.serializeMessage()
        packData(msgData)
    }

    @Synchronized
    private fun packData(data: UByteArray) {
        var offset = 0
        val totalSize = data.size

        while (offset < totalSize) {
            val remainingInPacket = currMsg.size - wPos
            
            if (remainingInPacket < 4) {
                val isContinuing = (offset < totalSize)
                flushCurrentBuffer(isContinuation = isContinuing)
                continue
            }

            val bytesToWrite = minOf(totalSize - offset, currMsg.size - wPos)
            
            data.copyInto(
                destination = currMsg,
                destinationOffset = wPos,
                startIndex = offset,
                endIndex = offset + bytesToWrite
            )

            wPos += bytesToWrite
            offset += bytesToWrite

            // 3. If we filled the buffer, flush it
            if (wPos >= currMsg.size) {
                val isContinuing = (offset < totalSize)
                flushCurrentBuffer(isContinuation = isContinuing)
            }
        }
    }

    private fun flushCurrentBuffer(isContinuation: Boolean) {
        val finalBytes = currMsg.sliceArray(0 until wPos).toByteArray()
        readyPackets.add(finalBytes)
        
        wPos = 0
        currMsg[wPos++] = if (isContinuation) 1u else 0u
    }

    @Synchronized
    fun getPacketsForBurst(): List<ByteArray> {
        if (wPos > 1) {
            // Add the current buffer directly to the queue
            val finalBytes = currMsg.sliceArray(0 until wPos).toByteArray()
            readyPackets.add(finalBytes)
            
            // Reset the buffer for the next time we start packing
            wPos = 0
            currMsg[wPos++] = 0u 
        }

        // Collect all ready packets
        val list = mutableListOf<ByteArray>()
        while (!readyPackets.isEmpty()) {
            readyPackets.poll()?.let { list.add(it) }
        }
        return list
    }
}