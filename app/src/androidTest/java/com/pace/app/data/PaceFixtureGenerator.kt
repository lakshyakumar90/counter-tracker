package com.pace.app.data

import com.pace.app.data.local.entity.ActivityEntity
import com.pace.app.data.local.entity.SensorSampleEntity
import com.pace.app.data.local.entity.SplitEntity
import com.pace.app.data.local.entity.TrackPointEntity
import com.pace.app.domain.repository.FinalizedActivity

/** Repeatable synthetic data only. Never seed fixtures from a person's location history. */
internal object PaceFixtureGenerator {
    fun longRoute(
        id: String,
        pointCount: Int,
        startedAtEpochMillis: Long = 1_700_000_000_000,
        sport: String = "RUNNING",
    ): FinalizedActivity {
        require(pointCount > 0)
        val durationMillis = pointCount * POINT_INTERVAL_MILLIS
        val distanceMeters = pointCount * POINT_DISTANCE_METERS
        val track = (0 until pointCount).map { sequence ->
            val offset = sequence * COORDINATE_STEP
            TrackPointEntity(
                activityId = id,
                sequence = sequence,
                timestampEpochMillis = startedAtEpochMillis + sequence * POINT_INTERVAL_MILLIS,
                latitude = ORIGIN_LATITUDE + offset,
                longitude = ORIGIN_LONGITUDE + offset / 2,
                altitudeMeters = 12.0 + (sequence % 9),
                horizontalAccuracyMeters = 3.0f + (sequence % 4),
                speedMetersPerSecond = 2.8f + (sequence % 5) / 10f,
            )
        }
        val splitCount = (distanceMeters / SPLIT_DISTANCE_METERS).coerceAtLeast(1)
        val splits = (1..splitCount).map { number ->
            SplitEntity(
                activityId = id,
                splitNumber = number,
                distanceMeters = SPLIT_DISTANCE_METERS.toDouble(),
                elapsedSeconds = number * 300L,
                movingSeconds = number * 285L,
            )
        }
        val sensorSamples = (0 until pointCount step SENSOR_SAMPLE_INTERVAL).map { sampleIndex ->
            val sampleType = when ((sampleIndex / SENSOR_SAMPLE_INTERVAL) % 3) {
                0 -> "HEART_RATE"
                1 -> "CADENCE"
                else -> "POWER"
            }
            val unit = when (sampleType) {
                "HEART_RATE" -> "bpm"
                "CADENCE" -> "steps/min"
                else -> "W"
            }
            SensorSampleEntity(
                activityId = id,
                sampleType = sampleType,
                timestampEpochMillis = startedAtEpochMillis + sampleIndex * POINT_INTERVAL_MILLIS,
                value = 100.0 + (sampleIndex % 70),
                unit = unit,
            )
        }
        val summary = ActivityEntity(
            id = id,
            sport = sport,
            startedAtEpochMillis = startedAtEpochMillis,
            endedAtEpochMillis = startedAtEpochMillis + durationMillis,
            elapsedSeconds = durationMillis / 1_000,
            movingSeconds = durationMillis / 1_000 - 30,
            distanceMeters = distanceMeters.toLong(),
            elevationGainMeters = pointCount * 0.25,
            title = "Synthetic $sport fixture",
            dataQualityFlags = "SYNTHETIC",
            createdAtEpochMillis = startedAtEpochMillis,
            updatedAtEpochMillis = startedAtEpochMillis,
        )
        return FinalizedActivity(summary, track, splits, sensorSamples)
    }

    private const val POINT_INTERVAL_MILLIS = 5_000L
    private const val POINT_DISTANCE_METERS = 5
    private const val SPLIT_DISTANCE_METERS = 1_000
    private const val SENSOR_SAMPLE_INTERVAL = 5
    private const val ORIGIN_LATITUDE = 12.900000
    private const val ORIGIN_LONGITUDE = 77.600000
    private const val COORDINATE_STEP = 0.000001
}
