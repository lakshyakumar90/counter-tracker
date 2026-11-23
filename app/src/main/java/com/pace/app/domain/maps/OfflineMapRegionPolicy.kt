package com.pace.app.domain.maps

import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan

/** Conservative preflight for the published provider limits; actual bytes are shown during download. */
object OfflineMapRegionPolicy {
    const val MIN_ZOOM = 10
    const val MAX_ZOOM = 15
    const val TILE_LIMIT = 3_000L
    const val PACK_BYTE_LIMIT = 50L * 1024 * 1024
    const val TOTAL_BYTE_LIMIT = 150L * 1024 * 1024
    private const val ESTIMATED_BYTES_PER_TILE = 18L * 1024
    private const val MAX_MERCATOR_LATITUDE = 85.05112878

    data class Estimate(val tileCount: Long, val estimatedBytes: Long) {
        val withinLimits: Boolean get() = tileCount in 1..TILE_LIMIT && estimatedBytes <= PACK_BYTE_LIMIT
    }
    data class Area(val south: Double, val west: Double, val north: Double, val east: Double)

    fun around(latitude: Double, longitude: Double, radiusKm: Int): Area? {
        if (!latitude.isFinite() || !longitude.isFinite() || radiusKm !in setOf(10, 20, 30)) return null
        val latDelta = Math.toDegrees(radiusKm / 6371.0088)
        val south = latitude - latDelta
        val north = latitude + latDelta
        if (south < -85.0 || north > 85.0) return null
        val lngDelta = Math.toDegrees(kotlin.math.asin(kotlin.math.sin(radiusKm / 6371.0088) / kotlin.math.cos(Math.toRadians(latitude))))
        val west = longitude - lngDelta
        val east = longitude + lngDelta
        return Area(south, west, north, east).takeIf { west >= -180.0 && east <= 180.0 }
    }

    fun estimate(south: Double, west: Double, north: Double, east: Double, maxZoom: Int = MAX_ZOOM): Estimate? {
        if (!south.isFinite() || !north.isFinite() || !west.isFinite() || !east.isFinite()) return null
        if (south < -85.0 || north > 85.0 || south >= north || west < -180.0 || east > 180.0 || west >= east) return null
        var tiles = 0L
        for (zoom in MIN_ZOOM..maxZoom.coerceIn(MIN_ZOOM, MAX_ZOOM)) {
            val scale = 1L shl zoom
            val xMin = longitudeTile(west, scale)
            val xMax = longitudeTile(east, scale)
            val yMin = latitudeTile(north, scale)
            val yMax = latitudeTile(south, scale)
            val levelCount = (xMax - xMin + 1).coerceAtLeast(0) * (yMax - yMin + 1).coerceAtLeast(0)
            tiles = (tiles + levelCount).coerceAtMost(TILE_LIMIT + 1)
        }
        return Estimate(tiles, tiles * ESTIMATED_BYTES_PER_TILE)
    }

    private fun longitudeTile(longitude: Double, scale: Long): Long =
        floor(((longitude + 180.0) / 360.0 * scale).coerceIn(0.0, (scale - 1).toDouble())).toLong()

    private fun latitudeTile(latitude: Double, scale: Long): Long {
        val clamped = latitude.coerceIn(-MAX_MERCATOR_LATITUDE, MAX_MERCATOR_LATITUDE)
        val mercator = ln(tan(PI / 4.0 + Math.toRadians(clamped) / 2.0))
        return floor(((1.0 - mercator / PI) / 2.0 * scale).coerceIn(0.0, (scale - 1).toDouble())).toLong()
    }
}
