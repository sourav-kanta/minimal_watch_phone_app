package com.example.mw_watch_companion.OSM_backend

import android.util.Log

data class GeoPoint(
    val latitude: Double,
    val longitude: Double
)

data class GeoBounds(
    val north: Double,
    val south: Double,
    val east: Double,
    val west: Double
)

data class RoadWay(
    val id: Long,
    val highwayType: String,
    val vertices: List<GeoPoint>
)

data class MapCache(
    val center: GeoPoint,
    val bounds: GeoBounds,
    val boundaryVertices: List<GeoPoint>,
    val roads: List<RoadWay>
)

data class ViewPoint(
    val x: Int,
    val y: Int
)

data class ViewRoadSegment(
    val highwayType: String,
    val start: ViewPoint,
    val end: ViewPoint
)

data class View(
    val width: Int,
    val height: Int,
    val segments: List<ViewRoadSegment>
)

fun View.printView(tag: String = "Viewport") {
    Log.d(tag, "View: ${width}x${height}, segments=${segments.size}")

    segments.forEachIndexed { index, segment ->
        Log.d(
            tag,
            "[$index] ${segment.highwayType}: " +
                "(${segment.start.x},${segment.start.y}) -> " +
                "(${segment.end.x},${segment.end.y})"
        )
    }
}
