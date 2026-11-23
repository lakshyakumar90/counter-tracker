package com.pace.app.domain.routes

/** Parses the accessible latitude/longitude editor used alongside map-tap route creation. */
object ManualWaypointCodec {
    fun parse(text: String): List<GpxPoint>? {
        val lines = text.lineSequence().filter { it.isNotBlank() }.toList()
        if (lines.size > GpxRouteCodec.MAX_POINTS) return null
        val points = lines.map { line ->
            val pair = line.trim().split(Regex("[,;\\s]+"))
            val latitude = pair.getOrNull(0)?.toDoubleOrNull()
            val longitude = pair.getOrNull(1)?.toDoubleOrNull()
            if (latitude != null && longitude != null && latitude.isFinite() && latitude in -90.0..90.0 && longitude.isFinite() && longitude in -180.0..180.0) {
                GpxPoint(latitude, longitude)
            } else {
                return null
            }
        }
        return points
    }

    fun format(points: List<GpxPoint>): String = points.joinToString("\n") { "${it.latitude},${it.longitude}" }
}
