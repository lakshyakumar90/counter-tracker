package com.pace.app.domain.metrics

import com.pace.app.data.local.entity.ActivityEntity
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InsightsAnalyticsTest {
    private val zone = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 11, 18)

    @Test fun emptyAndSingleActivitySummariesAreSafe() {
        val empty = InsightsAnalytics.summarize(emptyList(), InsightsPeriod.MONTH, today, zone = zone, locale = Locale.UK)
        assertEquals(0, empty.activityCount)
        assertEquals(30, empty.buckets.size)
        assertNull(empty.paceSecondsPerKm)

        val row = activity("one", today, 5_000, 1_500)
        val one = InsightsAnalytics.summarize(listOf(row), InsightsPeriod.MONTH, today, zone = zone, locale = Locale.UK)
        assertEquals(1, one.activityCount)
        assertEquals(1, one.activeDays)
        assertEquals(5_000, one.distanceMeters)
        assertEquals(300.0, one.paceSecondsPerKm!!, 0.001)
    }

    @Test fun thousandActivitiesAggregateWithoutMissingDaysBecomingActivities() {
        val rows = (0 until 1_000).map { index -> activity("a$index", today.minusDays((index % 18).toLong()), 1_000, 600) }
        val result = InsightsAnalytics.summarize(rows, InsightsPeriod.MONTH, today, zone = zone, locale = Locale.UK)
        assertEquals(1_000, result.activityCount)
        assertEquals(18, result.activeDays)
        assertEquals(1_000_000, result.distanceMeters)
        assertEquals(1_000, result.buckets.sumOf { it.activityCount })
        assertEquals(18, result.buckets.count { it.activityCount > 0 })
    }

    @Test fun periodsUseCalendarBoundariesAndSportFilters() {
        val cycle = activity("ride", today, 20_000, 3_600, sport = "CYCLING")
        val run = activity("run", today, 5_000, 1_800, sport = "RUNNING")
        val weekly = InsightsAnalytics.summarize(listOf(cycle, run), InsightsPeriod.WEEK, today, sport = "CYCLING", zone = zone, locale = Locale.UK)
        assertEquals(LocalDate.of(2026, 11, 16), weekly.start)
        assertEquals(20_000, weekly.distanceMeters)
        assertEquals(1, weekly.activityCount)
        val annual = InsightsAnalytics.summarize(listOf(cycle, run), InsightsPeriod.YEAR, today, zone = zone, locale = Locale.UK)
        assertEquals(12, annual.buckets.size)
        assertEquals(25_000, annual.distanceMeters)
    }

    private fun activity(id: String, date: LocalDate, distance: Long, moving: Long, sport: String = "RUNNING") = ActivityEntity(
        id = id, sport = sport,
        startedAtEpochMillis = date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli(),
        elapsedSeconds = moving, movingSeconds = moving, distanceMeters = distance,
        createdAtEpochMillis = 0, updatedAtEpochMillis = 0,
    )
}
