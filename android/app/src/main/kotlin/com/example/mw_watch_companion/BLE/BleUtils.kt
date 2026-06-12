package com.example.mw_watch_companion.BLE 

import java.util.UUID

object BleServiceParams {
    val SERVICE_UUID = UUID.fromString("12345678-1234-5678-1234-56789abcdef0")
    val RX_CHAR_UUID = UUID.fromString("87654321-4321-8765-4321-876543210987")
    val TX_CHAR_UUID = UUID.fromString("87654321-4321-8765-4321-876543210988")
    const val maxMTU = 247
    const val msgHeaderMagic: UByte = 0xA5u
    const val maxMsgPayloadSize: Int = 256
    val connInterval = (820L*1.25).toLong()
}

enum class BleConnectionState {
    CONNECTED,
    MTU_REQUESTED,
    MTU_REJECTED,
    SUBSCRIPTION_STARTED,
    READY,
    DISCONNECTED
}

enum class BlePacketParserState {
    RX_WAIT_MAGIC,
    RX_READ_HEADER,
    RX_READ_PAYLOAD
}

class BleParserContext {
    var state: BlePacketParserState = BlePacketParserState.RX_WAIT_MAGIC
    var currMsg: BleMessage? = null
    var offset: UInt = 0u
    var msgActive: Boolean = false
}

interface BleMessageIngestor {
    fun ingest(message: BleMessage)
}

enum class BleOpCode(val raw: UByte) {
    TIME_UPDATE_REQ(0x01u),
    WEATHER_REQ(0x02u),
    DATED_WEATHER_REQ(0x03u),
    NOTIFICATION_SEND(0x04u);

    companion object {
        fun fromUByte(value: UByte): BleOpCode? {
            return values().find { it.raw == value }
        }
    }
}
