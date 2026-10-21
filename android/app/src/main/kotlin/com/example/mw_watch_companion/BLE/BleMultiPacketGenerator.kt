package com.example.mw_watch_companion.BLE

import android.util.Log
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32

class BleMultiPacketGenerator {

    companion object {
        private const val TAG = "BleMultiPacketGenerator"
        private const val MULTIPACKET_HEADER_SIZE = 5
        private const val CHUNK_ID_SIZE = 1
        private const val CRC_SIZE = 4
    }

    fun generate(
        opcode: UByte,
        reqApp: UByte,
        payload: UByteArray
    ): List<BleMessage>? {
        val maxPayload = BleServiceParams.maxMsgPayloadSize
        val totalSize = payload.size

        if (totalSize == 0 ||
            totalSize > BleServiceParams.maxMultiMsgPayloadSize ||
            totalSize > 0xFFFF ||
            maxPayload <= MULTIPACKET_HEADER_SIZE + CHUNK_ID_SIZE ||
            maxPayload > 255
        ) {
            Log.e(TAG, "Invalid multipacket size")
            return null
        }

        val firstCapacity =
            maxPayload - MULTIPACKET_HEADER_SIZE - CHUNK_ID_SIZE
        val subsequentCapacity = maxPayload - CHUNK_ID_SIZE

        val remainingAfterFirst = (totalSize - firstCapacity).coerceAtLeast(0)
        val totalChunks = 1 +
            (remainingAfterFirst + subsequentCapacity - 1) /
            subsequentCapacity

        if (totalChunks > 255) {
            Log.e(TAG, "Too many chunks: $totalChunks")
            return null
        }

        val multiHeader = ubyteArrayOf(
            BleServiceParams.multipacketHeaderMagic,
            opcode,
            totalChunks.toUByte(),
            (totalSize shr 8).toUByte(),
            (totalSize and 0xFF).toUByte()
        )

        val crcEngine = CRC32()
        crcEngine.update(toByteArray(payload))
        val crc = crcEngine.value.toUInt()

        // The firmware reads CRC bytes in big-endian order.
        val crcBytes = ubyteArrayOf(
            ((crc shr 24) and 0xFFu).toUByte(),
            ((crc shr 16) and 0xFFu).toUByte(),
            ((crc shr 8) and 0xFFu).toUByte(),
            (crc and 0xFFu).toUByte()
        )

        val packets = ArrayList<BleMessage>(totalChunks + 1)
        val payloadBytes = toByteArray(payload)
        var offset = 0

        for (chunkId in 0 until totalChunks) {
            val chunk = ByteArrayOutputStream()
            val isFirst = chunkId == 0

            if (isFirst) {
                chunk.write(toByteArray(multiHeader))
            }

            chunk.write(chunkId)

            val capacity =
                if (isFirst) firstCapacity else subsequentCapacity
            val dataSize = minOf(capacity, totalSize - offset)

            if (dataSize <= 0) {
                Log.e(TAG, "Invalid data size at chunk $chunkId")
                return null
            }

            chunk.write(payloadBytes, offset, dataSize)
            offset += dataSize

            val chunkBytes = chunk.toByteArray().toUByteArray()

            if (chunkId != totalChunks - 1) {
                val message = createBleMessage(reqApp, chunkBytes)
                    ?: return null
                packets.add(message)
                continue
            }

            // Final data chunk: append as much CRC as fits.
            val available = maxPayload - chunkBytes.size
            val crcInChunk = minOf(available, CRC_SIZE)

            val finalChunk = chunkBytes +
                crcBytes.sliceArray(0 until crcInChunk)

            val finalMessage = createBleMessage(reqApp, finalChunk)
                ?: return null
            packets.add(finalMessage)

            // Remaining CRC bytes use a separate message without a chunk ID.
            if (crcInChunk < CRC_SIZE) {
                val remainingCrc = crcBytes.sliceArray(
                    crcInChunk until CRC_SIZE
                )

                val crcMessage = createBleMessage(reqApp, remainingCrc)
                    ?: return null
                packets.add(crcMessage)
            }
        }

        if (offset != totalSize) {
            Log.e(TAG, "Payload was not fully consumed")
            return null
        }

        return packets
    }

    private fun createBleMessage(
        reqApp: UByte,
        payload: UByteArray
    ): BleMessage? {
        if (payload.isEmpty() ||
            payload.size > BleServiceParams.maxMsgPayloadSize ||
            payload.size > 255
        ) {
            Log.e(TAG, "Invalid BLE payload length: ${payload.size}")
            return null
        }

        val headerBytes = ubyteArrayOf(
            BleServiceParams.msgHeaderMagic,
            BleOpCode.MULTICHUNK_SEND.raw,
            reqApp,
            payload.size.toUByte()
        )

        val header = BleMessageHeader.parseHeader(headerBytes) ?: return null

        return BleMessage(header).apply {
            this.payload = payload
        }
    }

    private fun toByteArray(bytes: UByteArray): ByteArray =
        ByteArray(bytes.size) { bytes[it].toByte() }
}