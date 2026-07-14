package com.example.mw_watch_companion.notifications

import java.nio.charset.StandardCharsets
import com.example.mw_watch_companion.common.PhoneApp

data class MWNotification(
    val appId: PhoneApp,
    val appName: String,
    val title: String,
    val text: String,
    val msgId: String = ""
) {
    
    fun toUByteArray(): UByteArray {
        val appNameBytes = appName.toByteArray(StandardCharsets.US_ASCII)
        val bodyRaw = if (title.isNotEmpty()) "$title: $text" else text
        val bodyBytes = bodyRaw.toByteArray(StandardCharsets.US_ASCII)

        val safeName = appNameBytes.take(13).toByteArray()
        val safeBody = bodyBytes.take(99).toByteArray()

        val buffer = mutableListOf<Byte>()
        
        buffer.add(appId.id)
        buffer.add(safeName.size.toByte())
        buffer.addAll(safeName.toList())
        buffer.add(safeBody.size.toByte())
        buffer.addAll(safeBody.toList())

        return buffer.toByteArray().asUByteArray()
    }
}