package com.pace.app.domain.sync

import com.pace.app.data.local.entity.SensorSampleEntity
import com.pace.app.data.local.entity.SplitEntity
import com.pace.app.data.local.entity.TrackPointEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ActivityTrackArchiveCodecTest {
    @Test fun compressedArchiveRoundTripsPointsSplitsAndSensors() {
        val id = "00000000-0000-0000-0000-000000000123"
        val points = listOf(
            TrackPointEntity(id, 0, 10_000, 12.3, 45.6, segmentIndex = 0),
            TrackPointEntity(id, 1, 11_000, 12.31, 45.61, altitudeMeters = 38.5, speedMetersPerSecond = 3.2f, segmentIndex = 1),
        )
        val splits = listOf(SplitEntity(id, 1, 1_000.0, 500, 450, 500.0, 145))
        val sensors = listOf(SensorSampleEntity(activityId = id, sampleType = "HEART_RATE", timestampEpochMillis = 10_500, value = 145.0, unit = "bpm"))
        val encoded = ActivityTrackArchiveCodec.encode(id, points, splits, sensors)
        val decoded = ActivityTrackArchiveCodec.decode(encoded, id)
        assertEquals(points, decoded.trackPoints)
        assertEquals(splits, decoded.splits)
        assertEquals(sensors.single().copy(id = 0), decoded.sensorSamples.single())
        assertEquals(64, ActivityTrackArchiveCodec.sha256(encoded).length)
        assertNotEquals(points.toString().length, encoded.size)
    }

    @Test fun wrongActivityIdentityAndMalformedPayloadAreRejected() {
        val id = "a"
        val encoded = ActivityTrackArchiveCodec.encode(id, emptyList(), emptyList(), emptyList())
        assertThrows(IllegalArgumentException::class.java) { ActivityTrackArchiveCodec.decode(encoded, "b") }
        assertThrows(Exception::class.java) { ActivityTrackArchiveCodec.decode(byteArrayOf(1, 2, 3), id) }
    }

    @Test fun invalidCoordinatesCannotBeUploadedOrRestored() {
        val invalid = TrackPointEntity("a", 0, 1, 91.0, 0.0)
        assertThrows(IllegalArgumentException::class.java) { ActivityTrackArchiveCodec.encode("a", listOf(invalid), emptyList(), emptyList()) }
    }
}
