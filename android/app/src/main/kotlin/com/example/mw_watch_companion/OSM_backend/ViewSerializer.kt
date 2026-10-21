
package com.example.mw_watch_companion.OSM_backend

class ViewSerializer {

    private fun highwayCode(type: String): Int = when (type) {
        "motorway", "motorway_link" -> 1
        "trunk", "trunk_link" -> 2
        "primary", "primary_link" -> 3
        "secondary", "secondary_link" -> 4
        "tertiary", "tertiary_link" -> 5
        "residential", "living_street" -> 6
        "unclassified", "road" -> 7
        "service" -> 8
        "pedestrian", "footway", "path", "cycleway",
        "steps", "track", "bridleway" -> 9
        else -> 0
    }

    fun serialize(view: View): UByteArray {
        require(view.width == 128 && view.height == 160)

        val SEGMENT_SIZE = 5
        val segments = view.segments.filter {
            (it.start.x != it.end.x || it.start.y != it.end.y) &&
            highwayCode(it.highwayType) in 1..5
        }
        val output = UByteArray(segments.size * SEGMENT_SIZE)
        var offset = 0

        for (segment in segments) {
            require(segment.start.x in 0 until view.width)
            require(segment.start.y in 0 until view.height)
            require(segment.end.x in 0 until view.width)
            require(segment.end.y in 0 until view.height)

            output[offset++] = highwayCode(segment.highwayType).toUByte()
            output[offset++] = segment.start.x.toUByte()
            output[offset++] = segment.start.y.toUByte()
            output[offset++] = segment.end.x.toUByte()
            output[offset++] = segment.end.y.toUByte()
        }

        return output
    }

}
