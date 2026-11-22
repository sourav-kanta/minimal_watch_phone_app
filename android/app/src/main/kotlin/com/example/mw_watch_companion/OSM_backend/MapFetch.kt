package com.example.mw_watch_companion.OSM_backend

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.tan

class MapFetch(
    private val zoomLevel: Int = 16,
    private val viewportWidth: Int = 128,
    private val viewportHeight: Int = 160,
    private val gridSize: Int = 5
) {
    companion object {
        private const val EARTH_RADIUS = 6378137.0
        private const val MAX_LATITUDE = 85.0511287798066
        private const val OVERPASS_URL =
            "https://overpass-api.de/api/interpreter"
    }

    @Volatile
    private var cache: MapCache? = null
    private val fetchInProgress = AtomicBoolean(false)
    private val metersPerPixel =
        (2.0 * PI * EARTH_RADIUS) / (256.0 * (1 shl zoomLevel))

    private val cacheWidth = viewportWidth * gridSize
    private val cacheHeight = viewportHeight * gridSize

    init {
        require(zoomLevel in 0..22)
        require(viewportWidth > 0 && viewportHeight > 0)
        require(gridSize >= 3 && gridSize % 2 == 1)
    }

    private fun projectX(lon: Double) =
        EARTH_RADIUS * lon * PI / 180.0

    private fun projectY(lat: Double): Double {
        val clamped = lat.coerceIn(-MAX_LATITUDE, MAX_LATITUDE)
        val radians = clamped * PI / 180.0
        return EARTH_RADIUS * ln(tan(PI / 4.0 + radians / 2.0))
    }

    private fun unprojectX(x: Double) =
        x / EARTH_RADIUS * 180.0 / PI

    private fun unprojectY(y: Double) =
        (2.0 * atan(exp(y / EARTH_RADIUS)) - PI / 2.0) *
            180.0 / PI

    private fun normalizeLongitude(lon: Double): Double =
        ((lon + 180.0) % 360.0 + 360.0) % 360.0 - 180.0

    private fun projectedBounds(center: GeoPoint): GeoBounds {
        val x = projectX(center.longitude)
        val y = projectY(center.latitude)
        val halfWidth = cacheWidth * metersPerPixel / 2.0
        val halfHeight = cacheHeight * metersPerPixel / 2.0

        return GeoBounds(
            north = unprojectY(y + halfHeight),
            south = unprojectY(y - halfHeight),
            east = normalizeLongitude(unprojectX(x + halfWidth)),
            west = normalizeLongitude(unprojectX(x - halfWidth))
        )
    }

    private fun boundary(bounds: GeoBounds) = listOf(
        GeoPoint(bounds.north, bounds.west),
        GeoPoint(bounds.north, bounds.east),
        GeoPoint(bounds.south, bounds.east),
        GeoPoint(bounds.south, bounds.west)
    )

    fun getMapCache(
        lat: Double,
        lon: Double,
        navigationScope: CoroutineScope
    ): MapCache? {
        require(lat.isFinite() && lat in -90.0..90.0)
        require(lon.isFinite() && lon in -180.0..180.0)
        val currentCache = cache

        val insideCache = currentCache != null &&
            containsLocation(currentCache, lat, lon)

        val refreshNeeded = currentCache == null ||
            isRefreshNeeded(currentCache, lat, lon)

        if (insideCache) {
            if (refreshNeeded) {
                refreshCache(lat, lon, navigationScope)
            }
            return currentCache
        }

        if (refreshNeeded) {
            refreshCache(lat, lon, navigationScope)
        }

        return null
    }

    private fun refreshCache(
        latitude: Double,
        longitude: Double,
        scope: CoroutineScope
    ) {
        if (!fetchInProgress.compareAndSet(false, true)) return

        scope.launch {
            try {
                val newCache = fetch(latitude, longitude)
                Log.i("MapFetch", "Map cache refreshed")
                cache = newCache
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("MapFetch", "Map refresh failed", e)
            } finally {
                fetchInProgress.set(false)
            }
        }
    }

    private suspend fun fetch(
        latitude: Double,
        longitude: Double
    ): MapCache = withContext(Dispatchers.IO) {
        require(latitude.isFinite() && latitude in -90.0..90.0)
        require(longitude.isFinite() && longitude in -180.0..180.0)

        val center = GeoPoint(latitude, longitude)
        val bounds = projectedBounds(center)

        require(bounds.west < bounds.east) {
            "Cache crosses the antimeridian; split into two Overpass requests"
        }

       val query = """
            [out:json][timeout:25];
            way["highway"~"^(motorway|motorway_link|trunk|trunk_link|primary|primary_link|secondary|secondary_link|tertiary|tertiary_link|unclassified|residential|living_street|service)$"]
            (${bounds.south},${bounds.west},${bounds.north},${bounds.east});
            out geom;
        """.trimIndent()

        val connection = URL(OVERPASS_URL).openConnection()
            as HttpURLConnection

        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 30_000
            connection.readTimeout = 60_000
            connection.doOutput = true
            connection.setRequestProperty(
                "Content-Type",
                "application/x-www-form-urlencoded; charset=UTF-8"
            )
            connection.setRequestProperty(
                "User-Agent",
                "MinimalWatchCompanion/1.0"
            )

            val body = "data=" + URLEncoder.encode(query, "UTF-8")
            connection.outputStream.use {
                it.write(body.toByteArray(Charsets.UTF_8))
            }

            val status = connection.responseCode
            if (status !in 200..299) {
                val error = connection.errorStream?.bufferedReader()
                    ?.use { it.readText() }.orEmpty()
                throw IOException("Overpass HTTP $status: $error")
            }

            val response = connection.inputStream.bufferedReader()
                .use { it.readText() }

            MapCache(
                center = center,
                bounds = bounds,
                boundaryVertices = boundary(bounds),
                roads = parseRoads(response)
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun isRefreshNeeded(
        cache: MapCache,
        latitude: Double,
        longitude: Double
    ): Boolean {
        require(latitude.isFinite() && latitude in -90.0..90.0)
        require(longitude.isFinite() && longitude in -180.0..180.0)

        val currentX = projectX(longitude)
        val currentY = projectY(latitude)
        val centerX = projectX(cache.center.longitude)
        val centerY = projectY(cache.center.latitude)

        val safeHalfWidth = (cacheWidth / 2.0 - viewportWidth) * metersPerPixel
        val safeHalfHeight = (cacheHeight / 2.0 - viewportHeight) * metersPerPixel

        // A wrapped longitude needs special handling near the antimeridian.
        // Unimplemented for now. TODO in future
        val dx = currentX - centerX

        return abs(dx) >= safeHalfWidth ||
            abs(currentY - centerY) >= safeHalfHeight
    }

    private fun containsLocation(
        cache: MapCache,
        latitude: Double,
        longitude: Double
    ): Boolean {
        require(latitude.isFinite() && latitude in -90.0..90.0)
        require(longitude.isFinite() && longitude in -180.0..180.0)

        val currentX = projectX(longitude)
        val currentY = projectY(latitude)
        val centerX = projectX(cache.center.longitude)
        val centerY = projectY(cache.center.latitude)

        val halfWidth = cacheWidth * metersPerPixel / 2.0
        val halfHeight = cacheHeight * metersPerPixel / 2.0

        return abs(currentX - centerX) < halfWidth &&
            abs(currentY - centerY) < halfHeight
    }

    private fun parseRoads(response: String): List<RoadWay> {
        val elements = JSONObject(response).getJSONArray("elements")
        val roads = ArrayList<RoadWay>(elements.length())

        for (i in 0 until elements.length()) {
            val element = elements.getJSONObject(i)
            if (element.optString("type") != "way") continue

            val tags = element.optJSONObject("tags") ?: continue
            val highway = tags.optString("highway", "")
            val geometry = element.optJSONArray("geometry") ?: continue

            val vertices = ArrayList<GeoPoint>(geometry.length())
            for (j in 0 until geometry.length()) {
                val point = geometry.getJSONObject(j)
                vertices.add(
                    GeoPoint(
                        latitude = point.getDouble("lat"),
                        longitude = point.getDouble("lon")
                    )
                )
            }

            if (highway.isNotEmpty() && vertices.size >= 2) {
                roads.add(
                    RoadWay(
                        id = element.getLong("id"),
                        highwayType = highway,
                        vertices = vertices
                    )
                )
            }
        }

        return roads
    }
}
