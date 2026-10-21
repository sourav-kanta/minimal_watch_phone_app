
package com.example.mw_watch_companion.OSM_backend

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan

class ViewportRenderer(
    private val zoomLevel: Int = 16,
    private val viewportWidth: Int = 128,
    private val viewportHeight: Int = 160
) {
    companion object {
        private const val EARTH_RADIUS = 6378137.0
        private const val MAX_LATITUDE = 85.0511287798066
    }

    private val metersPerPixel =
        (2.0 * PI * EARTH_RADIUS) /
            (256.0 * (1 shl zoomLevel))

    private val centerX = (viewportWidth - 1) / 2.0
    private val centerY = (viewportHeight - 1) / 2.0

    init {
        require(zoomLevel in 0..22)
        require(viewportWidth > 0 && viewportHeight > 0)
    }

    private fun projectX(longitude: Double): Double =
        EARTH_RADIUS * longitude * PI / 180.0

    private fun projectY(latitude: Double): Double {
        val radians =
            latitude.coerceIn(-MAX_LATITUDE, MAX_LATITUDE) * PI / 180.0
        return EARTH_RADIUS * ln(tan(PI / 4.0 + radians / 2.0))
    }

    fun render(
        cache: MapCache,
        latitude: Double,
        longitude: Double,
        bearingDegrees: Float
    ): View {
        require(latitude.isFinite() && latitude in -90.0..90.0)
        require(longitude.isFinite() && longitude in -180.0..180.0)
        require(bearingDegrees.isFinite())

        val originX = projectX(longitude)
        val originY = projectY(latitude)

        val bearing = Math.toRadians(
            ((bearingDegrees.toDouble() % 360.0) + 360.0) % 360.0
        )
        val cosBearing = cos(bearing)
        val sinBearing = sin(bearing)

        val segments = ArrayList<ViewRoadSegment>()

        for (road in cache.roads) {
            var previous: ViewPoint? = null

            for (point in road.vertices) {
                val east = (projectX(point.longitude) - originX) /
                    metersPerPixel
                val north = (projectY(point.latitude) - originY) /
                    metersPerPixel

                // Rotate the map opposite to the direction of travel.
                val screenX = centerX +
                    east * cosBearing - north * sinBearing
                val screenY = centerY -
                    east * sinBearing - north * cosBearing

                val current = ViewPoint(
                    screenX.roundToInt(),
                    screenY.roundToInt()
                )

                previous?.let { start ->
                    clipSegment(
                        start.x.toDouble(),
                        start.y.toDouble(),
                        current.x.toDouble(),
                        current.y.toDouble()
                    )?.let { clipped ->
                        segments.add(
                            ViewRoadSegment(
                                highwayType = road.highwayType,
                                start = ViewPoint(
                                    clipped[0].roundToInt(),
                                    clipped[1].roundToInt()
                                ),
                                end = ViewPoint(
                                    clipped[2].roundToInt(),
                                    clipped[3].roundToInt()
                                )
                            )
                        )
                    }
                }

                previous = current
            }
        }

        return View(
            width = viewportWidth,
            height = viewportHeight,
            segments = segments
        )
    }

    private fun clipSegment(
        x0: Double,
        y0: Double,
        x1: Double,
        y1: Double
    ): DoubleArray? {
        var ax = x0
        var ay = y0
        var bx = x1
        var by = y1

        fun outCode(x: Double, y: Double): Int {
            var code = 0
            if (x < 0.0) code = code or 1
            if (x > viewportWidth - 1.0) code = code or 2
            if (y < 0.0) code = code or 4
            if (y > viewportHeight - 1.0) code = code or 8
            return code
        }

        var codeA = outCode(ax, ay)
        var codeB = outCode(bx, by)

        while (true) {
            if ((codeA or codeB) == 0) {
                return doubleArrayOf(ax, ay, bx, by)
            }

            if ((codeA and codeB) != 0) return null

            val code = if (codeA != 0) codeA else codeB
            val dx = bx - ax
            val dy = by - ay

            val x: Double
            val y: Double

            when {
                (code and 8) != 0 -> {
                    if (abs(dy) < 1e-12) return null
                    x = ax + dx * ((viewportHeight - 1.0 - ay) / dy)
                    y = viewportHeight - 1.0
                }

                (code and 4) != 0 -> {
                    if (abs(dy) < 1e-12) return null
                    x = ax + dx * ((0.0 - ay) / dy)
                    y = 0.0
                }

                (code and 2) != 0 -> {
                    if (abs(dx) < 1e-12) return null
                    x = viewportWidth - 1.0
                    y = ay + dy * ((x - ax) / dx)
                }

                else -> {
                    if (abs(dx) < 1e-12) return null
                    x = 0.0
                    y = ay + dy * ((x - ax) / dx)
                }
            }

            if (code == codeA) {
                ax = x
                ay = y
                codeA = outCode(ax, ay)
            } else {
                bx = x
                by = y
                codeB = outCode(bx, by)
            }
        }
    }
}
