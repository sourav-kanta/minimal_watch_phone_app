package com.example.mw_watch_companion.BLE

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Calendar
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

import com.example.mw_watch_companion.common.DataIngestor
import com.example.mw_watch_companion.common.DataQueue

object BleConfig {
    const val TARGET_LATITUDE = 22.4954988
    const val TARGET_LONGITUDE = 88.3709008
    const val NETWORK_TIMEOUT_SECONDS = 5L
    const val WEATHER_BASE_URL = "https://api.open-meteo.com/v1/forecast"
    const val WEATHER_QUERY_PARAMS = "&hourly=temperature_2m,relative_humidity_2m,precipitation_probability,weather_code,wind_speed_10m&timezone=auto"
    const val WEATHER_BUFFER_SIZE = 148
    const val HOURLY_SAMPLES_COUNT = 24
    const val PAYLOAD_HEADER_OFFSET = 4
    const val HISTORICAL_NEVER_EXPIRES_HEADER = 0xFF.toByte()
}

class BleRequestHandler(private val targetQueue: DataQueue<BleMessage>) : DataIngestor<BleMessage>  {

    private val scope = CoroutineScope(Dispatchers.IO)

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(BleConfig.NETWORK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(BleConfig.NETWORK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    override fun ingest(message: BleMessage) {

        val opCode = BleOpCode.fromUByte(message.hdr.opCode)
        Log.d("BleRequestHandler", "Processing packet -> OpCode: ${opCode?.name ?: "UNKNOWN"}")

        when (opCode) {
            BleOpCode.TIME_UPDATE_REQ -> handleTimeUpdate(message)
            BleOpCode.WEATHER_REQ -> handleWeatherUpdate(message)
            BleOpCode.DATED_WEATHER_REQ -> handleDatedWeatherQuery(message)
            else -> {
                Log.e("BleRequestHandler", "Unsupported OpCode (0x%02X). Dropping message.".format(message.hdr.opCode.toInt()))
            }
        }
    }

    private fun sendResponse(opCode: BleOpCode, reqApp: UByte, payload: UByteArray) {
        val header = BleMessageHeader(opCode.raw, reqApp, payload.size.toUByte())
        val response = BleMessage(header).apply { this.payload = payload }
        targetQueue.push(response)
    }

    private fun handleTimeUpdate(m: BleMessage) {
        val cal = Calendar.getInstance()
        val totalSeconds = (cal.timeInMillis + cal.timeZone.getOffset(cal.timeInMillis)) / 1000

        val payload = ubyteArrayOf(
            ((totalSeconds shr 24) and 0xFF).toUByte(),
            ((totalSeconds shr 16) and 0xFF).toUByte(),
            ((totalSeconds shr 8) and 0xFF).toUByte(),
            (totalSeconds and 0xFF).toUByte()
        )

        sendResponse(BleOpCode.TIME_UPDATE_REQ, m.hdr.reqApp, payload)
    }

    private fun handleWeatherUpdate(m: BleMessage) {
        scope.launch {
            fetchWeather(BleConfig.TARGET_LATITUDE, BleConfig.TARGET_LONGITUDE, null)?.let {
                sendResponse(BleOpCode.WEATHER_REQ, m.hdr.reqApp, it)
            }
        }
    }

    private fun handleDatedWeatherQuery(m: BleMessage) {
        if (m.payload.size < 4) return

        val day = m.payload[0].toInt()
        val month = m.payload[1].toInt()
        val year = (m.payload[2].toInt() shl 8) or m.payload[3].toInt()
        val dateStr = "%04d-%02d-%02d".format(year, month, day)

        scope.launch {
            fetchWeather(BleConfig.TARGET_LATITUDE, BleConfig.TARGET_LONGITUDE, dateStr)?.let {
                sendResponse(BleOpCode.DATED_WEATHER_REQ, m.hdr.reqApp, it)
            }
        }
    }

    private fun fetchWeather(lat: Double, lon: Double, dateStr: String?): UByteArray? {
        val apiUrl = "${BleConfig.WEATHER_BASE_URL}?latitude=$lat&longitude=$lon${BleConfig.WEATHER_QUERY_PARAMS}"
        val targetUrl = if (dateStr != null) "$apiUrl&start_date=$dateStr&end_date=$dateStr" else apiUrl

        try {
            val responseJson = httpGet(targetUrl) ?: return null
            val hourly = JSONObject(responseJson).optJSONObject("hourly") ?: return null

            val buffer = ByteArray(BleConfig.WEATHER_BUFFER_SIZE)

            if (dateStr != null) {
                buffer[0] = BleConfig.HISTORICAL_NEVER_EXPIRES_HEADER
                buffer[1] = BleConfig.HISTORICAL_NEVER_EXPIRES_HEADER
                buffer[2] = BleConfig.HISTORICAL_NEVER_EXPIRES_HEADER
                buffer[3] = BleConfig.HISTORICAL_NEVER_EXPIRES_HEADER
            } else {
                val cal = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 0)
                }
                val expiry = (cal.timeInMillis + cal.timeZone.getOffset(cal.timeInMillis)) / 1000
                buffer[0] = ((expiry shr 24) and 0xFF).toByte()
                buffer[1] = ((expiry shr 16) and 0xFF).toByte()
                buffer[2] = ((expiry shr 8) and 0xFF).toByte()
                buffer[3] = (expiry and 0xFF).toByte()
            }

            val temp = hourly.optJSONArray("temperature_2m")
            val humidity = hourly.optJSONArray("relative_humidity_2m")
            val precip = hourly.optJSONArray("precipitation_probability")
            val code = hourly.optJSONArray("weather_code")
            val wind = hourly.optJSONArray("wind_speed_10m")

            var idx = BleConfig.PAYLOAD_HEADER_OFFSET
            for (i in 0 until BleConfig.HOURLY_SAMPLES_COUNT) {
                val scaledTemp = ((temp?.optDouble(i, 0.0) ?: 0.0) * 10.0).roundToInt().coerceIn(-32768, 32767)
                buffer[idx++] = ((scaledTemp shr 8) and 0xFF).toByte()
                buffer[idx++] = (scaledTemp and 0xFF).toByte()
                buffer[idx++] = (humidity?.optInt(i, 0) ?: 0).coerceIn(0, 100).toByte()
                buffer[idx++] = (precip?.optInt(i, 0) ?: 0).coerceIn(0, 100).toByte()
                buffer[idx++] = (code?.optInt(i, 0) ?: 0).coerceIn(0, 255).toByte()
                buffer[idx++] = (wind?.optDouble(i, 0.0) ?: 0.0).roundToInt().coerceIn(0, 255).toByte()
            }

            return buffer.asUByteArray()
        } catch (e: Exception) {
            Log.e("BleRequestHandler", "Weather parsing exception", e)
            return null
        }
    }

    private fun httpGet(urlStr: String): String? {
        val request = Request.Builder().url(urlStr).build()
        return try {
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.body?.string() else null
            }
        } catch (e: Exception) {
            Log.e("BleRequestHandler", "Network connection execution failed", e)
            null
        }
    }
}