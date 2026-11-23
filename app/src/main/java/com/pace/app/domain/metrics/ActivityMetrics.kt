package com.pace.app.domain.metrics

import com.pace.app.data.local.entity.ActivityEntity
import com.pace.app.data.local.entity.SplitEntity
import com.pace.app.data.local.entity.TrackPointEntity
import com.pace.app.data.local.entity.PlannedSessionEntity
import com.pace.app.tracking.haversineMeters
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.math.roundToLong

/** Offline, deterministic metrics. All distances are meters, durations seconds, speeds m/s. */
object ActivityMetrics {
    const val METERS_PER_MILE = 1609.344

    fun kilometers(meters: Double) = meters / 1000.0
    fun miles(meters: Double) = meters / METERS_PER_MILE
    fun metersFromKilometers(km: Double) = km * 1000.0
    fun metersFromMiles(mi: Double) = mi * METERS_PER_MILE
    fun paceSecondsPerKilometer(distanceMeters: Double, movingSeconds: Long): Double? =
        if (distanceMeters > 0 && movingSeconds > 0) movingSeconds * 1000.0 / distanceMeters else null
    fun paceSecondsPerMile(distanceMeters: Double, movingSeconds: Long): Double? =
        if (distanceMeters > 0 && movingSeconds > 0) movingSeconds * METERS_PER_MILE / distanceMeters else null
    fun speedMetersPerSecond(distanceMeters: Double, movingSeconds: Long): Double? =
        if (distanceMeters > 0 && movingSeconds > 0) distanceMeters / movingSeconds else null
    fun speedKilometersPerHour(metersPerSecond: Double) = metersPerSecond * 3.6
    fun speedMilesPerHour(metersPerSecond: Double) = metersPerSecond * 2.2369362921

    fun elapsedSeconds(startMillis: Long, endMillis: Long?, fallback: Long): Long =
        if (endMillis != null && endMillis >= startMillis) (endMillis - startMillis) / 1000 else fallback.coerceAtLeast(0)
    /** Per P00, only explicitly paused time is subtracted; GPS stationary detection is not moving time. */
    fun movingSeconds(elapsedSeconds: Long, explicitlyPausedSeconds: Long): Long =
        (elapsedSeconds.coerceAtLeast(0) - explicitlyPausedSeconds.coerceAtLeast(0)).coerceAtLeast(0)

    /** Recomputes track distance and duration while ignoring segment breaks and implausible fixes. */
    fun track(points: List<TrackPointEntity>, maxGapMillis: Long = 30_000): TrackMetrics {
        var distance = 0.0; var covered = 0L
        val acceptedSpeeds = mutableListOf<Double>()
        var gain = 0.0; var loss = 0.0
        val ordered = points.sortedBy { it.sequence }
        ordered.zipWithNext().forEach { (a, b) ->
            if (a.segmentIndex != b.segmentIndex) return@forEach
            val dt = b.timestampEpochMillis - a.timestampEpochMillis
            if (dt <= 0 || dt > maxGapMillis) return@forEach
            if (!valid(a) || !valid(b)) return@forEach
            val d = haversineMeters(a.latitude, a.longitude, b.latitude, b.longitude)
            val speed = d / (dt / 1000.0)
            if (!speed.isFinite() || speed > 15.0) return@forEach
            distance += d
            covered += (dt / 1000.0).roundToLong()
            acceptedSpeeds += speed
            val altA = a.altitudeMeters; val altB = b.altitudeMeters
            if (altA != null && altB != null && altA.isFinite() && altB.isFinite() && (a.horizontalAccuracyMeters ?: 0f) <= 25f && (b.horizontalAccuracyMeters ?: 0f) <= 25f) {
                val change = altB - altA
                if (kotlin.math.abs(change) >= 3.0) if (change > 0) gain += change else loss -= change
            }
        }
        return TrackMetrics(distance, covered, smoothedMaximum(acceptedSpeeds), gain, loss)
    }

    fun completedSplits(activityId: String, points: List<TrackPointEntity>, splitMeters: Double = 1000.0): List<SplitEntity> {
        require(splitMeters.isFinite() && splitMeters > 0)
        val result = mutableListOf<SplitEntity>(); var n = 1; var dSplit = 0.0; var tSplit = 0.0
        points.sortedBy { it.sequence }.zipWithNext().forEach { (a, b) ->
            if (a.segmentIndex != b.segmentIndex) return@forEach
            val dt = b.timestampEpochMillis - a.timestampEpochMillis
            if (dt <= 0 || dt > 30_000 || !valid(a) || !valid(b)) return@forEach
            val d = haversineMeters(a.latitude, a.longitude, b.latitude, b.longitude)
            if (!d.isFinite() || d <= 0 || d / (dt / 1000.0) > 15.0) return@forEach
            var remaining = d
            while (remaining > 1e-6) {
                val piece = minOf(remaining, splitMeters - dSplit); val seconds = dt / 1000.0 * piece / d
                dSplit += piece; tSplit += seconds; remaining -= piece
                if (dSplit >= splitMeters - 0.001) {
                    result += SplitEntity(activityId, n++, splitMeters, tSplit.roundToLong(), tSplit.roundToLong(), tSplit * 1000 / splitMeters)
                    dSplit = 0.0; tSplit = 0.0
                }
            }
        }
        return result
    }

    fun distanceBySport(activities: List<ActivityEntity>): Map<String, Long> = eligible(activities).groupBy { it.sport.uppercase() }.mapValues { (_, rows) -> rows.sumOf { it.distanceMeters } }
    fun activityCounts(activities: List<ActivityEntity>, zone: ZoneId, locale: Locale = Locale.getDefault()): Map<String, Int> = eligible(activities).groupingBy {
        val date = Instant.ofEpochMilli(it.startedAtEpochMillis).atZone(zone).toLocalDate(); val wf = WeekFields.of(locale)
        "${date.get(wf.weekBasedYear())}-W${date.get(wf.weekOfWeekBasedYear())}"
    }.eachCount()
    fun activityCountsByDay(activities: List<ActivityEntity>, zone: ZoneId): Map<LocalDate, Int> = eligible(activities).groupingBy { Instant.ofEpochMilli(it.startedAtEpochMillis).atZone(zone).toLocalDate() }.eachCount()
    fun activityCountsByMonth(activities: List<ActivityEntity>, zone: ZoneId): Map<String, Int> = eligible(activities).groupingBy { Instant.ofEpochMilli(it.startedAtEpochMillis).atZone(zone).toLocalDate().let { d -> "%04d-%02d".format(Locale.ROOT, d.year, d.monthValue) } }.eachCount()
    fun distanceByWeek(activities: List<ActivityEntity>, zone: ZoneId, locale: Locale = Locale.getDefault()): Map<String, Long> = weekly(activities, zone, locale) { it.distanceMeters }
    fun durationByWeek(activities: List<ActivityEntity>, zone: ZoneId, locale: Locale = Locale.getDefault(), moving: Boolean = true): Map<String, Long> = weekly(activities, zone, locale) { if (moving) it.movingSeconds else it.elapsedSeconds }
    fun load(activities: List<ActivityEntity>, now: LocalDate, zone: ZoneId, days: Long): Long {
        require(days in 1..3_650) { "Load window must be from 1 to 3,650 days." }
        val from = now.minusDays(days - 1).atStartOfDay(zone).toInstant().toEpochMilli(); val until = now.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return eligible(activities).filter { it.startedAtEpochMillis in from until until }.sumOf { it.movingSeconds }
    }
    fun longest(activities: List<ActivityEntity>): ActivityEntity? = eligible(activities).maxByOrNull { it.distanceMeters }
    fun averageSpeedBySport(activities: List<ActivityEntity>): Map<String, Double> = eligible(activities).filter { it.movingSeconds > 0 }.groupBy { it.sport.uppercase() }.mapValues { (_, rows) -> rows.sumOf { it.distanceMeters.toDouble() } / rows.sumOf { it.movingSeconds } }
    fun averagePaceBySport(activities: List<ActivityEntity>): Map<String, Double> = eligible(activities).filter { it.movingSeconds > 0 }.groupBy { it.sport.uppercase() }.mapNotNull { (sport, rows) ->
        if (sport in setOf("RUNNING", "JOGGING", "WALKING")) (sport to rows.sumOf { it.movingSeconds.toDouble() } * 1000 / rows.sumOf { it.distanceMeters }) else null
    }.toMap()
    fun averageSpeedByWeekAndSport(activities: List<ActivityEntity>, zone: ZoneId, locale: Locale = Locale.getDefault()): Map<String, Map<String, Double>> = eligible(activities).filter { it.movingSeconds > 0 }.groupBy { weekKey(it.startedAtEpochMillis, zone, locale) }.mapValues { (_, week) -> week.groupBy { it.sport.uppercase() }.mapValues { (_, rows) -> rows.sumOf { it.distanceMeters.toDouble() } / rows.sumOf { it.movingSeconds } } }
    fun averagePaceByWeekAndSport(activities: List<ActivityEntity>, zone: ZoneId, locale: Locale = Locale.getDefault()): Map<String, Map<String, Double>> = eligible(activities).filter { it.movingSeconds > 0 && it.sport.uppercase() in setOf("RUNNING", "JOGGING", "WALKING") }.groupBy { weekKey(it.startedAtEpochMillis, zone, locale) }.mapValues { (_, week) -> week.groupBy { it.sport.uppercase() }.mapValues { (_, rows) -> rows.sumOf { it.movingSeconds.toDouble() } * 1000 / rows.sumOf { it.distanceMeters } } }
    fun workoutCompletionRate(sessions: List<PlannedSessionEntity>): Double? {
        val eligible = sessions.filter { it.deletedAtEpochMillis == null && it.status in setOf("PLANNED", "COMPLETED", "SKIPPED") }
        return eligible.takeIf { it.isNotEmpty() }?.let { rows -> rows.count { it.status == "COMPLETED" } * 100.0 / rows.size }
    }
    fun personalBest(pointsByActivity: Map<ActivityEntity, List<TrackPointEntity>>, targetMeters: Double): Double? {
        if (!targetMeters.isFinite() || targetMeters <= 0) return null
        return pointsByActivity.entries.filter { eligible(listOf(it.key)).isNotEmpty() }.mapNotNull { (_, pts) -> bestWindowSeconds(pts, targetMeters) }.minOrNull()?.let { it * 1000 / targetMeters }
    }
    fun personalBest1k(points: Map<ActivityEntity, List<TrackPointEntity>>) = personalBest(points, 1_000.0)
    fun personalBest5k(points: Map<ActivityEntity, List<TrackPointEntity>>) = personalBest(points, 5_000.0)
    fun personalBest10k(points: Map<ActivityEntity, List<TrackPointEntity>>) = personalBest(points, 10_000.0)
    fun goalPercent(current: Double, target: Double): Double? = if (current.isFinite() && target.isFinite() && target > 0) (100 * current / target).coerceAtLeast(0.0) else null
    fun periodChange(current: Double, previous: Double): Double? = if (current.isFinite() && previous.isFinite() && previous > 0) (current - previous) / previous * 100 else null
    fun consistencyStreak(dates: Set<LocalDate>, today: LocalDate): Int {
        var cursor = if (today in dates) today else today.minusDays(1); var streak = 0
        while (cursor in dates) { streak++; cursor = cursor.minusDays(1) }
        return streak
    }

    fun isEligibleSummary(row: ActivityEntity): Boolean = row.status == "SAVED" && row.deletedAtEpochMillis == null && row.distanceMeters > 0 && row.elapsedSeconds > 0 && row.movingSeconds in 0..row.elapsedSeconds && row.dataQualityFlags.split(';').none { it.substringBefore('=').uppercase() in setOf("PARTIAL", "INCOMPLETE", "CORRUPT", "INVALID") }
    private fun eligible(rows: List<ActivityEntity>) = rows.filter(::isEligibleSummary)
    private fun valid(p: TrackPointEntity) = p.latitude.isFinite() && p.longitude.isFinite() && p.latitude in -90.0..90.0 && p.longitude in -180.0..180.0 && (p.horizontalAccuracyMeters == null || p.horizontalAccuracyMeters.isFinite() && p.horizontalAccuracyMeters > 0f && p.horizontalAccuracyMeters <= 100f)
    private fun weekly(rows: List<ActivityEntity>, zone: ZoneId, locale: Locale, value: (ActivityEntity) -> Long): Map<String, Long> {
        return eligible(rows).groupBy { row -> weekKey(row.startedAtEpochMillis, zone, locale) }.mapValues { (_, group) -> group.sumOf(value) }
    }

    private fun weekKey(timestamp: Long, zone: ZoneId, locale: Locale): String = Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate().let { date ->
        val fields = WeekFields.of(locale)
        "${date.get(fields.weekBasedYear())}-W${date.get(fields.weekOfWeekBasedYear())}"
    }

    private fun smoothedMaximum(speeds: List<Double>): Double {
        if (speeds.isEmpty()) return 0.0
        if (speeds.size < 3) return speeds.maxOrNull() ?: 0.0
        return speeds.windowed(3).maxOf { window -> window.sorted()[1] }
    }

    private fun bestWindowSeconds(points: List<TrackPointEntity>, targetMeters: Double): Double? {
        val segments = mutableListOf<MutableList<Pair<Double, Double>>>()
        val ordered = points.sortedBy { it.sequence }
        var current = mutableListOf<Pair<Double, Double>>()
        ordered.zipWithNext().forEach { (a, b) ->
            val dt = b.timestampEpochMillis - a.timestampEpochMillis
            val distance = if (a.segmentIndex == b.segmentIndex && dt in 1..30_000 && valid(a) && valid(b)) haversineMeters(a.latitude, a.longitude, b.latitude, b.longitude) else Double.NaN
            if (!distance.isFinite() || distance <= 0 || distance / (dt / 1000.0) > 15.0) {
                if (current.size > 1) segments += current
                current = mutableListOf(); return@forEach
            }
            if (current.isEmpty()) current += 0.0 to a.timestampEpochMillis.toDouble()
            val priorDistance = current.last().first
            current += (priorDistance + distance) to b.timestampEpochMillis.toDouble()
        }
        if (current.size > 1) segments += current
        return segments.flatMap { path ->
            val total = path.last().first
            if (total < targetMeters) emptyList() else path.drop(1).mapNotNull { (endDistance, _) ->
                if (endDistance < targetMeters) return@mapNotNull null
                val startTime = interpolatedTime(path, endDistance - targetMeters) ?: return@mapNotNull null
                val endTime = interpolatedTime(path, endDistance) ?: return@mapNotNull null
                (endTime - startTime) / 1000.0
            }
        }.filter { it > 0 }.minOrNull()
    }

    private fun interpolatedTime(path: List<Pair<Double, Double>>, distance: Double): Double? {
        val index = path.indexOfFirst { it.first >= distance }
        if (index < 0) return null
        if (index == 0) return path[0].second
        val (beforeDistance, beforeTime) = path[index - 1]; val (afterDistance, afterTime) = path[index]
        val fraction = (distance - beforeDistance) / (afterDistance - beforeDistance)
        return beforeTime + (afterTime - beforeTime) * fraction
    }
}

data class TrackMetrics(val distanceMeters: Double, val coveredDurationSeconds: Long, val maximumSpeedMetersPerSecond: Double, val ascentMeters: Double, val descentMeters: Double)
