package com.pace.app.tracking

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

enum class RecordingState { IDLE, ACQUIRING, RECORDING, PAUSED, FINISHING, RECOVERED }

data class TrackSample(
    val timestampEpochMillis: Long,
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double?,
    val accuracyMeters: Float?,
    val speedMetersPerSecond: Float?,
    val bearingDegrees: Float?,
)

/** Pure validation and distance logic shared by real and replay location sources. */
class TrackSampleFilter(private val maximumSpeedMetersPerSecond: Double) {
    private var previous: TrackSample? = null

    fun accept(sample: TrackSample): Boolean {
        if (sample.timestampEpochMillis <= 0L || !sample.latitude.isFinite() || !sample.longitude.isFinite()) return false
        if (sample.latitude !in -90.0..90.0 || sample.longitude !in -180.0..180.0) return false
        if (sample.accuracyMeters != null && (!sample.accuracyMeters.isFinite() || sample.accuracyMeters <= 0f || sample.accuracyMeters > 100f)) return false
        val before = previous
        if (before != null) {
            val deltaMillis = sample.timestampEpochMillis - before.timestampEpochMillis
            if (deltaMillis <= 0L) return false
            val speed = haversineMeters(before.latitude, before.longitude, sample.latitude, sample.longitude) / (deltaMillis / 1000.0)
            if (speed > maximumSpeedMetersPerSecond) return false
        }
        previous = sample
        return true
    }
}

fun haversineMeters(latitudeA: Double, longitudeA: Double, latitudeB: Double, longitudeB: Double): Double {
    val earthRadiusMeters = 6_371_000.0
    val latDelta = Math.toRadians(latitudeB - latitudeA)
    val lonDelta = Math.toRadians(longitudeB - longitudeA)
    val a = sin(latDelta / 2) * sin(latDelta / 2) +
        cos(Math.toRadians(latitudeA)) * cos(Math.toRadians(latitudeB)) * sin(lonDelta / 2) * sin(lonDelta / 2)
    val boundedA = a.coerceIn(0.0, 1.0)
    return earthRadiusMeters * 2 * atan2(sqrt(boundedA), sqrt(1 - boundedA))
}

fun transition(state: RecordingState, action: RecordingAction): RecordingState = when (state to action) {
    RecordingState.IDLE to RecordingAction.SELECT -> RecordingState.ACQUIRING
    RecordingState.ACQUIRING to RecordingAction.START -> RecordingState.RECORDING
    RecordingState.ACQUIRING to RecordingAction.CANCEL -> RecordingState.IDLE
    RecordingState.RECORDING to RecordingAction.PAUSE -> RecordingState.PAUSED
    RecordingState.RECORDING to RecordingAction.FINISH -> RecordingState.FINISHING
    RecordingState.RECORDING to RecordingAction.INTERRUPT -> RecordingState.RECOVERED
    RecordingState.PAUSED to RecordingAction.RESUME -> RecordingState.RECORDING
    RecordingState.PAUSED to RecordingAction.FINISH -> RecordingState.FINISHING
    RecordingState.PAUSED to RecordingAction.INTERRUPT -> RecordingState.RECOVERED
    RecordingState.RECOVERED to RecordingAction.RESUME -> RecordingState.RECORDING
    RecordingState.RECOVERED to RecordingAction.FINISH -> RecordingState.FINISHING
    RecordingState.FINISHING to RecordingAction.SAVED -> RecordingState.IDLE
    RecordingState.FINISHING to RecordingAction.PERSISTENCE_FAILED -> RecordingState.RECOVERED
    else -> error("Invalid recording transition: $state -> $action")
}

enum class RecordingAction { SELECT, START, CANCEL, PAUSE, RESUME, FINISH, INTERRUPT, SAVED, PERSISTENCE_FAILED }
