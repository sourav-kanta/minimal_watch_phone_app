package com.example.mw_watch_companion.BLE

import android.util.Log
import com.example.mw_watch_companion.common.DataQueue

class BlePacketParser (private val targetQueue: DataQueue<BleMessage>) {
    var parseCtx = BleParserContext()
    
    private fun resetContext() {
        parseCtx.state = BlePacketParserState.RX_WAIT_MAGIC
        parseCtx.currMsg = null
        parseCtx.offset = 0u
        parseCtx.msgActive = false
    }

    fun parseNextPacket(packet: UByteArray) {
        if (packet.isEmpty()) {
            Log.e("BlePacketParser", "Invalid ble packet")
            return
        }
        var rPos: Int = 0

        val firstByte = packet[rPos++]
        if (firstByte == 0u.toUByte()) {
            resetContext()
        } 
        // Continuation packet
        else {
            // Check if we expect a non continuation packet
            if (!parseCtx.msgActive) {
                Log.w("BlePacketParser", "Unexpected continuation packet -> resync")
                resetContext()
            }
        }

        while (rPos < packet.size) { 
            when (parseCtx.state) {
                BlePacketParserState.RX_WAIT_MAGIC -> {
                    // Skip till we find magic byte for resync                    
                    if (packet[rPos] != BleServiceParams.msgHeaderMagic) {
                        rPos++
                    } else {
                        // Check if sufficient len available
                        // We made sure not to split headers from firmware
                        if (rPos + 4 > packet.size) { 
                            // No more possibility of any messages
                            // So avoid false positives for magic
                            return
                        }

                        // Magic byte found
                        parseCtx.state = BlePacketParserState.RX_READ_HEADER
                    }
                }
                BlePacketParserState.RX_READ_HEADER -> {
                    // Guard against split header
                    if (rPos + 4 > packet.size) {
                        return
                    }
                    
                    val probableHeader: BleMessageHeader? = BleMessageHeader.parseHeader(packet.copyOfRange(rPos, rPos + 4))
                    if (probableHeader == null) {
                        // Header is invalid, consume magic and proceed
                        rPos++
                        resetContext()
                    } else {
                        // Valid header parsed
                        parseCtx.offset = 0u
                        parseCtx.msgActive = true
                        parseCtx.currMsg = BleMessage(probableHeader)
                        rPos += 4

                        val msg = parseCtx.currMsg
                        if (msg != null) {
                            if (msg.hdr.len == 0u.toUByte()) {
                                targetQueue.push(msg)
                                //msg.dumpMessage()
                                parseCtx.msgActive = false
                                parseCtx.state = BlePacketParserState.RX_WAIT_MAGIC
                            } else {
                                // Prepare for payload
                                parseCtx.state = BlePacketParserState.RX_READ_PAYLOAD
                            }
                        }
                    }
                }
                BlePacketParserState.RX_READ_PAYLOAD -> {
                    val msg = parseCtx.currMsg
                    if (msg == null) {
                        resetContext()
                        return
                    }

                    // Payload overflow guard
                    if (parseCtx.offset > msg.hdr.len.toUInt()) {
                        Log.e("BlePacketParser", "RX offset overflow -> resync")
                        resetContext()
                        return
                    }

                    val remainingLength = msg.hdr.len.toUInt() - parseCtx.offset
                    val remainingPacket = (packet.size - rPos).toUInt()
                    
                    val chunkLen: UInt = if (remainingLength < remainingPacket) remainingLength else remainingPacket  
                    
                    packet.copyInto(
                        destination = msg.payload, 
                        destinationOffset = parseCtx.offset.toInt(),
                        startIndex = rPos, 
                        endIndex = rPos + chunkLen.toInt()
                    )
                    
                    parseCtx.offset += chunkLen
                    rPos += chunkLen.toInt()

                    // Check if current message is complete
                    if (parseCtx.offset >= msg.hdr.len.toUInt()) {
                        targetQueue.push(msg)
                        //msg.dumpMessage()
                        resetContext()
                    }
                }
                else -> { Log.e("BlePacketParser", "Undefined parser state") }
            }
        }
    }
}