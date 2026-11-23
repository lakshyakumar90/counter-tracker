package com.pace.app.domain.metrics

import com.pace.app.data.local.entity.ActivityEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.WeekFields
import java.util.Locale

enum class InsightsPeriod { WEEK, MONTH, YEAR }

data class InsightsBucket(val date: LocalDate, val label: String, val distanceMeters: Long, val durationSeconds: Long, val activityCount: Int, val elevationGainMeters: Double?)

data class InsightsSummary(
    val start: LocalDate,
    val endExclusive: LocalDate,
    val buckets: List<InsightsBucket>,
    val distanceMeters: Long,
    val durationSeconds: Long,
    val activityCount: Int,
    val activeDays: Int,
    val sportDistanceMeters: Map<String, Long>,
    val elevationGainMeters: Double?,
    val paceSecondsPerKm: Double?,
    val speedMetersPerSecond: Double?,
)

/** Builds gap-preserving calendar buckets from eligible local summaries. */
object InsightsAnalytics {
    fun range(period: InsightsPeriod, today: LocalDate, locale: Locale = Locale.getDefault()): Pair<LocalDate, LocalDate> = when (period) {
        InsightsPeriod.WEEK -> {
            val start = today.with(WeekFields.of(locale).dayOfWeek(), 1)
            start to start.plusDays(7)
        }
        InsightsPeriod.MONTH -> today.withDayOfMonth(1).let { it to it.plusMonths(1) }
        InsightsPeriod.YEAR -> today.withDayOfYear(1).let { it to it.plusYears(1) }
    }

    fun summarize(
        activities: List<ActivityEntity>, period: InsightsPeriod, today: LocalDate,
        sport: String? = null, zone: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.getDefault(),
    ): InsightsSummary {
        val (start, end) = range(period, today, locale)
        val filtered = activities.asSequence()
            .filter(ActivityMetrics::isEligibleSummary)
            .filter { sport == null || it.sport.equals(sport, ignoreCase = true) }
            .filter { val date = Instant.ofEpochMilli(it.startedAtEpochMillis).atZone(zone).toLocalDate(); date >= start && date < end }
            .toList()
        val dates = when (period) {
            InsightsPeriod.WEEK -> (0L..6L).map(start::plusDays)
            InsightsPeriod.MONTH -> (0L until java.time.temporal.ChronoUnit.DAYS.between(start, end)).map(start::plusDays)
            InsightsPeriod.YEAR -> (0L..11L).map(start::plusMonths)
        }
        val byDate = filtered.groupBy { Instant.ofEpochMilli(it.startedAtEpochMillis).atZone(zone).toLocalDate() }
        val buckets = dates.map { date ->
            val rows = if (period == InsightsPeriod.YEAR) filtered.filter { Instant.ofEpochMilli(it.startedAtEpochMillis).atZone(zone).toLocalDate().month == date.month } else byDate[date].orEmpty()
            InsightsBucket(
                date, when (period) {
                    InsightsPeriod.WEEK -> date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, locale)
                    InsightsPeriod.MONTH -> date.dayOfMonth.toString()
                    InsightsPeriod.YEAR -> date.month.getDisplayName(java.time.format.TextStyle.SHORT, locale)
                }, rows.sumOf { it.distanceMeters }, rows.sumOf { it.movingSeconds }, rows.size,
                rows.takeIf { it.isNotEmpty() && it.all { row -> row.elevationGainMeters != null } }?.sumOf { it.elevationGainMeters!! },
            )
        }
        val distance = filtered.sumOf { it.distanceMeters }
        val duration = filtered.sumOf { it.movingSeconds }
        return InsightsSummary(
            start, end, buckets, distance, duration, filtered.size,
            filtered.map { Instant.ofEpochMilli(it.startedAtEpochMillis).atZone(zone).toLocalDate() }.distinct().size,
            filtered.groupBy { it.sport.uppercase() }.mapValues { (_, rows) -> rows.sumOf { it.distanceMeters } },
            filtered.takeIf { it.isNotEmpty() && it.all { row -> row.elevationGainMeters != null } }?.sumOf { it.elevationGainMeters!! },
            ActivityMetrics.paceSecondsPerKilometer(distance.toDouble(), duration),
            ActivityMetrics.speedMetersPerSecond(distance.toDouble(), duration),
        )
    }
}
