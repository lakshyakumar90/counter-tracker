package com.pace.app.domain.sync

import com.pace.app.data.local.entity.SensorSampleEntity
import com.pace.app.data.local.entity.SplitEntity
import com.pace.app.data.local.entity.TrackPointEntity
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.FilterInputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

data class ActivityTrackArchive(
    val activityId: String,
    val trackPoints: List<TrackPointEntity>,
    val splits: List<SplitEntity>,
    val sensorSamples: List<SensorSampleEntity>,
)

/** Versioned, compressed track payload. Never log its bytes or coordinates. */
object ActivityTrackArchiveCodec {
    private const val MAGIC = 0x50414345
    const val FORMAT_VERSION = 1
    private const val MAX_ROWS = 250_000
    private const val MAX_COMPRESSED_BYTES = 100 * 1024 * 1024
    private const val MAX_EXPANDED_BYTES = 64 * 1024 * 1024L

    fun encode(activityId: String, points: List<TrackPointEntity>, splits: List<SplitEntity>, sensors: List<SensorSampleEntity>): ByteArray {
        require(activityId.isNotBlank() && activityId.length <= 128)
        require(points.size <= MAX_ROWS && splits.size <= MAX_ROWS && sensors.size <= MAX_ROWS)
        require(points.all { it.activityId == activityId } && splits.all { it.activityId == activityId } && sensors.all { it.activityId == activityId })
        val bytes = ByteArrayOutputStream()
        DataOutputStream(GZIPOutputStream(bytes)).use { out ->
            out.writeInt(MAGIC)
            out.writeInt(FORMAT_VERSION)
            out.writeUTF(activityId)
            out.writeInt(points.size)
            points.sortedBy { it.sequence }.forEach { point ->
                require(point.sequence >= 0 && point.latitude.isFinite() && point.latitude in -90.0..90.0 && point.longitude.isFinite() && point.longitude in -180.0..180.0)
                out.writeInt(point.sequence)
                out.writeLong(point.timestampEpochMillis)
                out.writeDouble(point.latitude)
                out.writeDouble(point.longitude)
                writeNullableDouble(out, point.altitudeMeters)
                writeNullableFloat(out, point.horizontalAccuracyMeters)
                writeNullableFloat(out, point.speedMetersPerSecond)
                writeNullableFloat(out, point.bearingDegrees)
                out.writeInt(point.segmentIndex)
            }
            out.writeInt(splits.size)
            splits.sortedBy { it.splitNumber }.forEach { split ->
                out.writeInt(split.splitNumber)
                out.writeDouble(split.distanceMeters)
                out.writeLong(split.elapsedSeconds)
                out.writeLong(split.movingSeconds)
                writeNullableDouble(out, split.averagePaceSecondsPerKm)
                out.writeBoolean(split.averageHeartRateBpm != null)
                split.averageHeartRateBpm?.let(out::writeInt)
            }
            out.writeInt(sensors.size)
            sensors.sortedBy { it.timestampEpochMillis }.forEach { sensor ->
                require(sensor.sampleType.length <= 100 && sensor.unit.length <= 40 && sensor.value.isFinite())
                out.writeUTF(sensor.sampleType)
                out.writeLong(sensor.timestampEpochMillis)
                out.writeDouble(sensor.value)
                out.writeUTF(sensor.unit)
            }
        }
        return bytes.toByteArray().also { require(it.size <= MAX_COMPRESSED_BYTES) { "Track archive exceeds size limit." } }
    }

    fun decode(bytes: ByteArray, expectedActivityId: String): ActivityTrackArchive {
        require(bytes.size <= MAX_COMPRESSED_BYTES) { "Track archive exceeds size limit." }
        DataInputStream(GZIPInputStream(LimitedInputStream(ByteArrayInputStream(bytes), MAX_EXPANDED_BYTES))).use { input ->
            require(input.readInt() == MAGIC) { "Track archive header is invalid." }
            require(input.readInt() == FORMAT_VERSION) { "Track archive version is unsupported." }
            val activityId = input.readUTF()
            require(activityId == expectedActivityId) { "Track archive activity ID does not match." }
            val pointCount = input.readBoundedCount()
            val points = List(pointCount) {
                val sequence = input.readInt()
                val timestamp = input.readLong()
                val latitude = input.readDouble()
                val longitude = input.readDouble()
                val altitude = readNullableDouble(input)
                val accuracy = readNullableFloat(input)
                val speed = readNullableFloat(input)
                val bearing = readNullableFloat(input)
                val segment = input.readInt()
                require(sequence >= 0 && latitude.isFinite() && latitude in -90.0..90.0 && longitude.isFinite() && longitude in -180.0..180.0 && segment >= 0)
                TrackPointEntity(activityId, sequence, timestamp, latitude, longitude, altitude, accuracy, speed, bearing, segment)
            }
            require(points.map { it.sequence }.distinct().size == points.size) { "Track archive has duplicate point sequence numbers." }
            val splitCount = input.readBoundedCount()
            val splits = List(splitCount) {
                val number = input.readInt()
                val distance = input.readDouble()
                val elapsed = input.readLong()
                val moving = input.readLong()
                val pace = readNullableDouble(input)
                val heartRate = if (input.readBoolean()) input.readInt() else null
                require(number > 0 && distance.isFinite() && distance >= 0.0 && elapsed >= 0 && moving in 0..elapsed)
                SplitEntity(activityId, number, distance, elapsed, moving, pace, heartRate)
            }
            val sensorCount = input.readBoundedCount()
            val sensors = List(sensorCount) {
                val kind = input.readUTF()
                val timestamp = input.readLong()
                val value = input.readDouble()
                val unit = input.readUTF()
                require(value.isFinite() && kind.length <= 100 && unit.length <= 40)
                SensorSampleEntity(activityId = activityId, sampleType = kind, timestampEpochMillis = timestamp, value = value, unit = unit)
            }
            require(input.read() == -1) { "Track archive contains trailing data." }
            return ActivityTrackArchive(activityId, points, splits, sensors)
        }
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun DataInputStream.readBoundedCount(): Int = readInt().also { require(it in 0..MAX_ROWS) { "Track archive row count is invalid." } }
    private fun writeNullableDouble(out: DataOutputStream, value: Double?) { out.writeBoolean(value != null); value?.let(out::writeDouble) }
    private fun writeNullableFloat(out: DataOutputStream, value: Float?) { out.writeBoolean(value != null); value?.let(out::writeFloat) }
    private fun readNullableDouble(input: DataInputStream): Double? = if (input.readBoolean()) input.readDouble().also { require(it.isFinite()) } else null
    private fun readNullableFloat(input: DataInputStream): Float? = if (input.readBoolean()) input.readFloat().also { require(it.isFinite()) } else null

    private class LimitedInputStream(input: InputStream, private var remaining: Long) : FilterInputStream(input) {
        override fun read(): Int {
            if (remaining <= 0) throw IllegalArgumentException("Track archive expands beyond the size limit.")
            val result = super.read()
            if (result >= 0) remaining--
            return result
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (remaining <= 0) throw IllegalArgumentException("Track archive expands beyond the size limit.")
            val result = super.read(buffer, offset, minOf(length.toLong(), remaining).toInt())
            if (result > 0) remaining -= result
            return result
        }
    }
}
