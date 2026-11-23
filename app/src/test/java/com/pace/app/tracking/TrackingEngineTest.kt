package com.pace.app.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackingEngineTest {
    @Test
    fun stateMachineAllowsExpectedRecordingAndRecoveryTransitions() {
        assertEquals(RecordingState.ACQUIRING, transition(RecordingState.IDLE, RecordingAction.SELECT))
        assertEquals(RecordingState.RECORDING, transition(RecordingState.ACQUIRING, RecordingAction.START))
        assertEquals(RecordingState.PAUSED, transition(RecordingState.RECORDING, RecordingAction.PAUSE))
        assertEquals(RecordingState.RECORDING, transition(RecordingState.PAUSED, RecordingAction.RESUME))
        assertEquals(RecordingState.FINISHING, transition(RecordingState.RECORDING, RecordingAction.FINISH))
        assertEquals(RecordingState.IDLE, transition(RecordingState.FINISHING, RecordingAction.SAVED))
        assertEquals(RecordingState.RECOVERED, transition(RecordingState.FINISHING, RecordingAction.PERSISTENCE_FAILED))
    }

    @Test(expected = IllegalStateException::class)
    fun stateMachineRejectsInvalidTransitions() {
        transition(RecordingState.IDLE, RecordingAction.PAUSE)
    }

    @Test
    fun filterRejectsInvalidAndImplausibleSamplesButKeepsValidSprints() {
        val filter = TrackSampleFilter(maximumSpeedMetersPerSecond = 15.0)
        assertTrue(filter.accept(sample(time = 1_000, lat = 0.0, lon = 0.0)))
        assertFalse(filter.accept(sample(time = 2_000, lat = Double.NaN, lon = 0.0)))
        assertFalse(filter.accept(sample(time = 2_000, lat = 0.01, lon = 0.0)))
        assertTrue(filter.accept(sample(time = 3_000, lat = 0.0001, lon = 0.0)))
        assertFalse(filter.accept(sample(time = 3_000, lat = 0.0002, lon = 0.0)))
    }

    @Test
    fun longLocationGapStartsNewRouteSegmentInsteadOfBridgingDistance() {
        val filter = TrackSampleFilter(maximumSpeedMetersPerSecond = 15.0)
        assertTrue(filter.evaluate(sample(time = 1_000, lat = 0.0, lon = 0.0)).accepted)
        val next = filter.evaluate(sample(time = 32_000, lat = 0.0, lon = 0.01))
        assertTrue(next.accepted)
        assertTrue(next.startsNewSegment)
    }

    @Test
    fun recordingAcquisitionRejectsCachedAndUnmeasuredFixes() {
        val now = 60_000L
        val fresh = sample(now - 2_000L, 0.0, 0.0)
        assertTrue(isFreshRecordingFix(fresh, now))
        assertFalse(isFreshRecordingFix(fresh.copy(timestampEpochMillis = now - 30_000L), now))
        assertFalse(isFreshRecordingFix(fresh.copy(accuracyMeters = null), now))
    }

    @Test
    fun stationaryGpsDriftDoesNotAdvanceDistanceAnchor() {
        val gate = TrackDistanceGate()
        gate.reset(sample(time = 1_000, lat = 0.0, lon = 0.0))

        for (second in 2..30) {
            val driftedLongitude = if (second % 2 == 0) 0.00018 else -0.00018
            val stationaryFix = sample(second * 1_000L, 0.0, driftedLongitude)
                .copy(speedMetersPerSecond = 0.1f)
            assertEquals(0.0, gate.add(stationaryFix), 0.0)
        }

        val walkingFix = sample(31_000, 0.0, 0.00006).copy(speedMetersPerSecond = 1.2f)
        assertEquals(6.67, gate.add(walkingFix), 0.1)
    }

    @Test
    fun haversineProducesExpectedDistanceForKnownCoordinateDelta() {
        val meters = haversineMeters(0.0, 0.0, 0.0, 0.001)
        assertEquals(111.2, meters, 1.0)
    }

    @Test
    fun parsesSyntheticGpxGoldenTrackWithoutNetwork() {
        val fixture = javaClass.getResourceAsStream("/gpx/golden-short-run.gpx")!!
        val samples = parseGpx(fixture)
        assertEquals(3, samples.size)
        assertEquals(0.0, samples.first().latitude, 0.0)
        assertTrue(samples.last().timestampEpochMillis > samples.first().timestampEpochMillis)
    }

    private fun sample(time: Long, lat: Double, lon: Double) = TrackSample(time, lat, lon, null, 4f, null, null)
}
