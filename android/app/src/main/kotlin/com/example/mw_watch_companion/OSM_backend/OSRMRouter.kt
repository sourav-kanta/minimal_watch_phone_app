
package com.example.mw_watch_companion.OSM_backend

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sqrt

class OSRMRouter(
    private val destination: GeoPoint,
    private val baseUrl: String = "https://router.project-osrm.org",
    private val timeoutMs: Int = 60_000,
    private val deviationRadiusMeters: Double = 50.0
) {
    companion object {
        private const val TAG = "OSRMRouter"
        private const val ROUTE_HIGHWAY_TYPE = "navigation_route"
        private const val ROUTE_WAY_ID = -1L
        private const val EARTH_RADIUS = 6378137.0
    }

    private data class ClosestRoutePoint(
        val roadIndex: Int,
        val segmentIndex: Int,
        val point: GeoPoint,
        val distanceMeters: Double
    )

    @Volatile
    private var cachedRoute: List<RoadWay>? = null

    private val fetchInProgress = AtomicBoolean(false)

    init {
        requireValidCoordinate(destination)

        require(baseUrl.isNotBlank()) {
            "OSRM base URL cannot be blank"
        }
        require(timeoutMs > 0) {
            "Timeout must be positive"
        }
        require(deviationRadiusMeters.isFinite() && deviationRadiusMeters > 0.0) {
            "Deviation radius must be positive and finite"
        }
    }

    fun getRoute(
        currentPosition: GeoPoint,
        navigationScope: CoroutineScope
    ): List<RoadWay>? {
        requireValidCoordinate(currentPosition)

        val currentRoute = cachedRoute

        if (currentRoute == null) {
            refreshRoute(currentPosition, navigationScope)
            return null
        }

        val closestPoint = findClosestPoint(
            position = currentPosition,
            route = currentRoute
        ) ?: run {
            refreshRoute(currentPosition, navigationScope)
            return currentRoute
        }

        if (closestPoint.distanceMeters > deviationRadiusMeters) {
            Log.i(
                TAG,
                "Route deviation detected: ${closestPoint.distanceMeters}m"
            )
            refreshRoute(currentPosition, navigationScope)
        }

        return clipRoute(currentRoute, closestPoint)
    }

    private fun findClosestPoint(
        position: GeoPoint,
        route: List<RoadWay>
    ): ClosestRoutePoint? {
        val latitudeRadians = position.latitude * PI / 180.0
        val longitudeScale = EARTH_RADIUS * cos(latitudeRadians)
        val latitudeScale = EARTH_RADIUS * PI / 180.0

        val safeLongitudeScale = longitudeScale.coerceAtLeast(1e-6)

        var closest: ClosestRoutePoint? = null
        var minimumDistanceSquared = Double.POSITIVE_INFINITY

        for ((roadIndex, road) in route.withIndex()) {
            val vertices = road.vertices

            for (i in 0 until vertices.size - 1) {
                val start = vertices[i]
                val end = vertices[i + 1]

                val startX = safeLongitudeScale *
                    longitudeDifference(start.longitude, position.longitude) * PI / 180.0
                val startY = latitudeScale *
                    (start.latitude - position.latitude)

                val endX = safeLongitudeScale *
                    longitudeDifference(end.longitude, position.longitude) * PI / 180.0
                val endY = latitudeScale *
                    (end.latitude - position.latitude)

                val segmentX = endX - startX
                val segmentY = endY - startY

                val segmentLengthSquared =
                    segmentX * segmentX + segmentY * segmentY

                val t = if (segmentLengthSquared > 0.0) {
                    (-(startX * segmentX + startY * segmentY) /
                        segmentLengthSquared).coerceIn(0.0, 1.0)
                } else {
                    0.0
                }

                val closestX = startX + t * segmentX
                val closestY = startY + t * segmentY

                val distanceSquared =
                    closestX * closestX + closestY * closestY

                if (distanceSquared < minimumDistanceSquared) {
                    minimumDistanceSquared = distanceSquared

                    val closestLatitude =
                        position.latitude + closestY / latitudeScale

                    val closestLongitude =
                        position.longitude +
                            closestX / safeLongitudeScale * 180.0 / PI

                    closest = ClosestRoutePoint(
                        roadIndex = roadIndex,
                        segmentIndex = i,
                        point = GeoPoint(
                            latitude = closestLatitude.coerceIn(-90.0, 90.0),
                            longitude = normalizeLongitude(closestLongitude)
                        ),
                        distanceMeters = sqrt(distanceSquared)
                    )
                }
            }
        }

        return closest
    }

    private fun clipRoute(
        route: List<RoadWay>,
        closest: ClosestRoutePoint
    ): List<RoadWay> {
        val result = ArrayList<RoadWay>()

        for (roadIndex in closest.roadIndex until route.size) {
            val road = route[roadIndex]

            val remainingVertices = if (roadIndex == closest.roadIndex) {
                buildList {
                    add(closest.point)

                    for (i in closest.segmentIndex + 1 until road.vertices.size) {
                        add(road.vertices[i])
                    }
                }
            } else {
                road.vertices
            }

            if (remainingVertices.size >= 2) {
                result.add(
                    road.copy(vertices = remainingVertices)
                )
            }
        }

        return result
    }

    private fun longitudeDifference(
        longitude: Double,
        referenceLongitude: Double
    ): Double {
        return ((longitude - referenceLongitude + 540.0) % 360.0) - 180.0
    }

    private fun normalizeLongitude(longitude: Double): Double {
        return ((longitude + 180.0) % 360.0 + 360.0) % 360.0 - 180.0
    }

    private fun refreshRoute(
        start: GeoPoint,
        scope: CoroutineScope
    ) {
        if (!fetchInProgress.compareAndSet(false, true)) return

        scope.launch {
            try {
                val newRoute = fetchRoute(start)
                cachedRoute = newRoute

                Log.i(TAG, "Route refreshed successfully")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Route refresh failed", e)
            } finally {
                fetchInProgress.set(false)
            }
        }
    }

    private suspend fun fetchRoute(
        start: GeoPoint
    ): List<RoadWay> = withContext(Dispatchers.IO) {
        requireValidCoordinate(start)

        val url = buildString {
            append(baseUrl.trimEnd('/'))
            append("/route/v1/driving/")
            append("${start.longitude},${start.latitude}")
            append(";")
            append("${destination.longitude},${destination.latitude}")
            append("?overview=full&geometries=geojson&steps=false")
        }

        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = timeoutMs
            readTimeout = timeoutMs

            setRequestProperty("User-Agent", "MWWatchCompanion/0.1.0")
            setRequestProperty("Accept", "application/json")
        }

        try {
            val statusCode = connection.responseCode

            val stream = if (statusCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }

            val body = stream?.bufferedReader()?.use { it.readText() }
                ?: throw IOException(
                    "OSRM returned HTTP $statusCode without a response body"
                )

            if (statusCode !in 200..299) {
                throw IOException("OSRM HTTP $statusCode: $body")
            }

            val response = JSONObject(body)
            val code = response.optString("code")

            if (code != "Ok") {
                throw IOException(
                    "OSRM routing failed: ${response.optString("message", code)}"
                )
            }

            val routes = response.optJSONArray("routes")
                ?: throw IOException("OSRM response has no routes")

            if (routes.length() == 0) {
                throw IOException("OSRM returned no routes")
            }

            val geometry = routes
                .getJSONObject(0)
                .getJSONObject("geometry")

            if (geometry.optString("type") != "LineString") {
                throw IOException("Unexpected OSRM geometry type")
            }

            val points = geometry.getJSONArray("coordinates")
            val coordinates = ArrayList<GeoPoint>(points.length())

            for (i in 0 until points.length()) {
                val point = points.getJSONArray(i)

                if (point.length() < 2) {
                    throw IOException("Invalid route coordinate at index $i")
                }

                val coordinate = GeoPoint(
                    latitude = point.getDouble(1),
                    longitude = point.getDouble(0)
                )

                try {
                    requireValidCoordinate(coordinate)
                } catch (e: IllegalArgumentException) {
                    throw IOException(
                        "Invalid route coordinate at index $i",
                        e
                    )
                }

                coordinates.add(coordinate)
            }

            if (coordinates.size < 2) {
                throw IOException("OSRM returned fewer than two route points")
            }

            listOf(
                RoadWay(
                    id = ROUTE_WAY_ID,
                    highwayType = ROUTE_HIGHWAY_TYPE,
                    vertices = coordinates
                )
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun requireValidCoordinate(point: GeoPoint) {
        require(point.latitude.isFinite() && point.latitude in -90.0..90.0) {
            "Invalid latitude: ${point.latitude}"
        }
        require(point.longitude.isFinite() && point.longitude in -180.0..180.0) {
            "Invalid longitude: ${point.longitude}"
        }
    }
}
