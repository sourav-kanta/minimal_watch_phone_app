package com.example.mw_watch_companion.notifications

import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.CRC32C

class NotificationDeduplicator(
    private val scope: CoroutineScope,
    private val ttlMillis: Long = 3_600_000 
) {
    private val cache = ConcurrentHashMap<Long, Long>()

    init {
        scope.launch {
            while (isActive) {
                // 5 min cleanup
                delay(300_000) 
                cleanup()
            }
        }
    }

    fun generateCRC(payload: UByteArray): Long {
        val crc = CRC32C()
        crc.update(payload.asByteArray())
        return crc.value
    }

    private fun cleanup() {
        val now = System.currentTimeMillis()
        val beforeSize = cache.size
        
        cache.values.removeIf { expiry -> expiry < now }
        
        val removed = beforeSize - cache.size
        if (removed > 0) {
            println("Deduplicator: Cleaned up $removed expired entries.")
        }
    }

    fun checkDuplicateAndUpdateCache(crc: Long): Boolean {
        val now = System.currentTimeMillis()
        val expiry = cache[crc]

        if (expiry != null) {
            if (now < expiry) {
                return true 
            } else {
                cache.remove(crc) 
            }
        }

        cache[crc] = now + ttlMillis
        return false
    }
}