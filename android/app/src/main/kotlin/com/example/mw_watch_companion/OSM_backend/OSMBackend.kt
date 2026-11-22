package com.example.mw_watch_companion.OSM_backend

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.util.concurrent.atomic.AtomicBoolean

import com.example.mw_watch_companion.common.Event
import com.example.mw_watch_companion.common.Events

class OSMBackend(
    private val destinationLatitude: Double,
    private val destinationLongitude: Double
) {

    private val navigationActive = AtomicBoolean(false)
    private val mapFetch = MapFetch()
    @Volatile
    private var firmwareApp : UByte = 0u
    private val navigationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val viewportRenderer = ViewportRenderer()
    private val viewSerializer = ViewSerializer()
    private val osmRouter = OSRMRouter(GeoPoint(destinationLatitude,destinationLongitude))

    init {
        Events.bus.subscribe<Event.NavigationStart>(navigationScope) { event ->
            firmwareApp = event.reqApp
            navigationActive.set(true)
        }

        Events.bus.subscribe<Event.NavigationEnd>(navigationScope) { event->
            if(event.reqApp == firmwareApp) {
                navigationActive.set(false)
            }
        }
    }

    fun refreshData(latitude: Double, longitude: Double, bearingDegrees: Float?) {
        val currentCache = mapFetch.getMapCache(latitude, longitude, navigationScope)
        val routeRoadList = osmRouter.getRoute(GeoPoint(latitude, longitude), navigationScope)
        if (currentCache == null && routeRoadList == null) return
        // Start processing viewport
        val outView = viewportRenderer.render(
            cache = currentCache,
            route = routeRoadList,
            latitude = latitude,
            longitude = longitude,
            bearingDegrees = bearingDegrees ?: 0f
        )
        // outView.printView()
        // Serialize and queue packet
        val blob = viewSerializer.serialize(outView)
        if (navigationActive.get()) {
            if (!Events.bus.tryPublish(Event.MapFrameSend(blob, firmwareApp))) {
                Log.i("OSMBackend", "Event bus rejected map frame")
            }
        }

    }

    fun close() {
        navigationScope.cancel()
    }
}
