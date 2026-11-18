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
