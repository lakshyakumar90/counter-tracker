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
    private val maximumContinuousGapMillis = 30_000L

    fun accept(sample: TrackSample): Boolean = evaluate(sample).accepted

    fun evaluate(sample: TrackSample): SampleDecision {
        if (sample.timestampEpochMillis <= 0L || !sample.latitude.isFinite() || !sample.longitude.isFinite()) return SampleDecision(false)
        if (sample.latitude !in -90.0..90.0 || sample.longitude !in -180.0..180.0) return SampleDecision(false)
        if (sample.accuracyMeters != null && (!sample.accuracyMeters.isFinite() || sample.accuracyMeters <= 0f || sample.accuracyMeters > MAXIMUM_ACCURACY_METERS)) return SampleDecision(false)
        val before = previous
        if (before != null) {
            val deltaMillis = sample.timestampEpochMillis - before.timestampEpochMillis
            if (deltaMillis <= 0L) return SampleDecision(false)
            if (deltaMillis > maximumContinuousGapMillis) {
                previous = sample
                return SampleDecision(accepted = true, startsNewSegment = true)
            }
            val speed = haversineMeters(before.latitude, before.longitude, sample.latitude, sample.longitude) / (deltaMillis / 1000.0)
            if (speed > maximumSpeedMetersPerSecond) return SampleDecision(false)
        }
        previous = sample
        return SampleDecision(accepted = true)
    }

    private companion object {
        /**
         * A fix worse than this is only useful for showing that we are still searching.
         * Keeping it in the track would let Wi-Fi/cell drift inflate distance while standing still.
         */
        const val MAXIMUM_ACCURACY_METERS = 20f
    }
}

/**
 * Accumulates distance from an anchor instead of from every single fix.
 *
 * GPS coordinates wobble by several metres even when the phone is stationary. Summing every
 * fix-to-fix hop turned 20-30 idle seconds into tens of metres. This gate only advances once the
 * fix has moved further than the combined accuracy noise, so jitter is ignored while real motion
 * still accumulates across several samples.
 */
class TrackDistanceGate(
    private val minimumDistanceMeters: Double = 5.0,
    private val accuracyFactor: Double = 1.0,
) {
    private var anchor: TrackSample? = null

    fun reset(sample: TrackSample? = null) {
        anchor = sample
    }

    /** Returns the metres travelled since the last accepted anchor, or 0 when within GPS noise. */
    fun add(sample: TrackSample): Double {
        val before = anchor
        if (before == null) {
            anchor = sample
            return 0.0
        }
        val moved = haversineMeters(before.latitude, before.longitude, sample.latitude, sample.longitude)
        if (!moved.isFinite()) return 0.0
        val deltaMillis = sample.timestampEpochMillis - before.timestampEpochMillis
        val impliedSpeed = if (deltaMillis > 0) moved / (deltaMillis / 1000.0) else 0.0
        val speed = sample.speedMetersPerSecond?.takeIf { it.isFinite() }?.toDouble() ?: impliedSpeed

        // Doppler or implied speed below minimum walking speed is treated as stationary.
        // Importantly: do NOT update anchor to the drifted position while stationary,
        // otherwise jumping anchor to extreme noise points causes jitter hops to accumulate.
        if (speed < STATIONARY_SPEED_METERS_PER_SECOND) {
            return 0.0
        }
        val accuracy = maxOf(before.accuracyMeters ?: 0f, sample.accuracyMeters ?: 0f)
        val threshold = maxOf(minimumDistanceMeters, accuracy.toDouble() * accuracyFactor)
        if (moved < threshold) return 0.0
        anchor = sample
        return moved
    }

    private companion object {
        /** Anything slower than 0.6 m/s (~2.16 km/h) is treated as standing still. */
        const val STATIONARY_SPEED_METERS_PER_SECOND = 0.6
    }
}

data class SampleDecision(val accepted: Boolean, val startsNewSegment: Boolean = false)

internal fun isFreshRecordingFix(sample: TrackSample, nowMillis: Long): Boolean =
    sample.accuracyMeters != null && nowMillis - sample.timestampEpochMillis in 0L..10_000L

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
