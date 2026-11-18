package com.pace.app.tracking

import com.pace.app.data.local.entity.SplitEntity
import com.pace.app.data.local.entity.TrackPointEntity

private const val SPLIT_DISTANCE_METERS = 1_000.0

/** Create completed 1 km splits, excluding elapsed gaps between resumed GPS segments. */
fun buildDistanceSplits(activityId: String, points: List<TrackPointEntity>): List<SplitEntity> {
    if (points.size < 2) return emptyList()
    val result = mutableListOf<SplitEntity>()
    var splitNumber = 1
    var splitDistance = 0.0
    var splitSeconds = 0.0
    var previous = points.first()

    points.drop(1).forEach { point ->
        if (point.segmentIndex != previous.segmentIndex) {
            previous = point
            return@forEach
        }
        val distance = haversineMeters(previous.latitude, previous.longitude, point.latitude, point.longitude)
        val seconds = ((point.timestampEpochMillis - previous.timestampEpochMillis).coerceAtLeast(0L)) / 1_000.0
        if (distance > 0.0) {
            var remainingDistance = distance
            while (remainingDistance > 0.0) {
                val piece = minOf(SPLIT_DISTANCE_METERS - splitDistance, remainingDistance)
                splitDistance += piece
                splitSeconds += seconds * (piece / distance)
                remainingDistance -= piece
                if (splitDistance >= SPLIT_DISTANCE_METERS - 0.001) {
                    val elapsed = splitSeconds.toLong().coerceAtLeast(0L)
                    result += SplitEntity(
                        activityId = activityId,
                        splitNumber = splitNumber++,
                        distanceMeters = SPLIT_DISTANCE_METERS,
                        elapsedSeconds = elapsed,
                        movingSeconds = elapsed,
                        averagePaceSecondsPerKm = splitSeconds,
                    )
                    splitDistance = 0.0
                    splitSeconds = 0.0
                }
            }
        }
        previous = point
    }
    return result
}
