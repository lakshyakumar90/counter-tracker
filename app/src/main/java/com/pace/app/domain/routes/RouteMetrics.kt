package com.pace.app.domain.routes

import kotlin.math.cos
import kotlin.math.hypot

object RouteMetrics {
    /** Local equirectangular projection is accurate for point-to-route deviation checks at workout scale. */
    fun distanceToRouteMeters(latitude: Double, longitude: Double, route: List<GpxPoint>): Double? {
        if (!latitude.isFinite() || latitude !in -90.0..90.0 || !longitude.isFinite() || longitude !in -180.0..180.0) return null
        if (route.size < 2) return null
        val referenceLatitude = Math.toRadians(latitude)
        val scaleX = 111_320.0 * cos(referenceLatitude)
        val px = longitude * scaleX
        val py = latitude * 111_320.0
        return route.zipWithNext().asSequence().filter { (a, b) -> a.segmentIndex == b.segmentIndex }.map { (a, b) ->
            val ax = a.longitude * scaleX; val ay = a.latitude * 111_320.0
            val bx = b.longitude * scaleX; val by = b.latitude * 111_320.0
            val dx = bx - ax; val dy = by - ay
            val lengthSquared = dx * dx + dy * dy
            val t = if (lengthSquared == 0.0) 0.0 else (((px - ax) * dx + (py - ay) * dy) / lengthSquared).coerceIn(0.0, 1.0)
            hypot(px - (ax + t * dx), py - (ay + t * dy))
        }.minOrNull()
    }

    fun isOffRoute(distanceMeters: Double?, thresholdMeters: Double = 50.0): Boolean =
        thresholdMeters.isFinite() && thresholdMeters > 0 && distanceMeters?.let { it.isFinite() && it > thresholdMeters } == true
}
