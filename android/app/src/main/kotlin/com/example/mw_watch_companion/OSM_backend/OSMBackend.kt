package com.example.mw_watch_companion.OSM_backend

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

import com.example.mw_watch_companion.BLE.BleConnectionManager

class OSMBackend(
    private val destinationLatitude: Double,
    private val destinationLongitude: Double
) {

    private val mapFetch = MapFetch()
    @Volatile
    private var cache: MapCache? = null
    private val navigationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val fetchInProgress = AtomicBoolean(false)
    private val viewportRenderer = ViewportRenderer()
    private val viewSerializer = ViewSerializer()

    fun refreshData(latitude: Double, longitude: Double, bearingDegrees: Float?) {
        val currentCache = cache
        val insideCache = currentCache != null &&
                          mapFetch.containsLocation(currentCache, latitude, longitude)
        val refreshNeeded = currentCache == null ||
                            mapFetch.isRefreshNeeded(currentCache, latitude, longitude)

        // Cache valid and pos within 3*3 grid
        if (insideCache) {
            // Start processing viewport
            val outView = viewportRenderer.render(
                cache = currentCache!!,
                latitude = latitude,
                longitude = longitude,
                bearingDegrees = bearingDegrees ?: 0f
            )
            // outView.printView()
            // Serialize and queue packet
            val blob = viewSerializer.serialize(outView)
            BleConnectionManager.forwardMapFrames(blob)
        }
        if(!refreshNeeded) return
        if (!fetchInProgress.compareAndSet(false, true)) {
            // A request has already been fired
            return
        }

        // Trigger a refresh and dont render anything
        // Next refresh will pickup the new cache
        navigationScope.launch {
            try {
                val newCache = mapFetch.fetch(latitude, longitude)
                // Publish the complete cache only after a successful fetch.
                cache = newCache
                Log.i("OSMBackend", "Map cache refreshed")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("OSMBackend", "Map refresh failed", e)
            } finally {
                fetchInProgress.set(false)
            }
        }
    }

    fun close() {
        navigationScope.cancel()
    }
}
