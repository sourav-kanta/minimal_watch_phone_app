package com.example.mw_watch_companion.BLE

import android.util.Log

class BleMessageHeader (var opCode: UByte = 0u, var reqApp: UByte = 0u, var len: UByte = 0u) {
    fun serializeHeader(): UByteArray {
        return ubyteArrayOf(BleServiceParams.msgHeaderMagic, opCode, reqApp, len)
    }

    companion object {
        fun parseHeader(bytes: UByteArray): BleMessageHeader? {
            // Return null if invalid header or doesnt start with Magic
            if(bytes.size != 4 || bytes[0] != BleServiceParams.msgHeaderMagic)
                return null
            return BleMessageHeader(bytes[1], bytes[2], bytes[3])
        }
    }

    fun dumpHeader() {
        Log.i("BleMessageHeader", 
              "Header -> Opcode: 0x%02X, ReqApp: 0x%02X, Len: %d bytes".format(opCode.toInt(), reqApp.toInt(), len.toInt()))
    }

}

class BleMessage (var hdr: BleMessageHeader) {
    var payload: UByteArray = UByteArray(hdr.len.toInt())

    fun serializeMessage() :UByteArray{
        return hdr.serializeHeader() + payload
    }

    fun dumpMessage() {
        Log.i("BleMessage", "═══ [ Incoming BLE Message ] ═══")
        
        hdr.dumpHeader()
        
        if (payload.isEmpty()) {
            Log.i("BleMessage", "Payload: [ Empty ]")
        } else {
            val hexString = payload.joinToString(separator = ", ", prefix = "[ ", postfix = " ]") { 
                "0x%02X".format(it.toInt()) 
            }
            Log.i("BleMessage", "Payload: $hexString")
        }
        Log.i("BleMessage", "════════════════════════════════")
    }

}