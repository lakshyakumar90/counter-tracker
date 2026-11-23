package com.pace.app.domain.metrics

import com.pace.app.data.local.entity.ActivityEntity
import com.pace.app.data.local.entity.TrackPointEntity
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

class ActivityMetricsTest {
    private fun activity(id: String, start: Long, distance: Long = 1_000, sport: String = "RUNNING", status: String = "SAVED", deleted: Long? = null) =
        ActivityEntity(id, sport = sport, startedAtEpochMillis = start, elapsedSeconds = 600, movingSeconds = 500, distanceMeters = distance, createdAtEpochMillis = start, updatedAtEpochMillis = start, status = status, deletedAtEpochMillis = deleted)

    private fun point(seq: Int, time: Long, lon: Double, segment: Int = 0, altitude: Double? = null, accuracy: Float? = 4f) =
        TrackPointEntity("fixture", seq, time, 0.0, lon, altitude, accuracy, segmentIndex = segment)

    @Test fun unitConversionsAreExactAndZeroDistanceIsUnavailable() {
        assertEquals(1.0, ActivityMetrics.miles(ActivityMetrics.METERS_PER_MILE), 1e-9)
        assertEquals(5.0, ActivityMetrics.speedKilometersPerHour(5.0 / 3.6), 1e-9)
        assertNull(ActivityMetrics.paceSecondsPerKilometer(0.0, 600))
        assertNull(ActivityMetrics.paceSecondsPerKilometer(100.0, 0))
        assertNull(ActivityMetrics.speedMetersPerSecond(100.0, 0))
        assertEquals(540L, ActivityMetrics.movingSeconds(600, 60))
        assertEquals(0L, ActivityMetrics.movingSeconds(30, 90))
    }

    @Test fun trackExcludesSegmentGapsInvalidTimeAndGpsSpikes() {
        val pts = listOf(point(0, 1_000, 0.0), point(1, 11_000, .0005), point(2, 21_000, .02), point(3, 31_000, .0006), point(4, 41_000, .0011, segment = 1))
        val metrics = ActivityMetrics.track(pts)
        assertEquals(55.6, metrics.distanceMeters, 2.0)
        assertEquals(10, metrics.coveredDurationSeconds)
        assertTrue(metrics.maximumSpeedMetersPerSecond < 15)
    }

    @Test fun maximumSpeedUsesThreePointMedianToSuppressAnIsolatedGpsSpike() {
        val points = listOf(point(0, 1_000, 0.0), point(1, 11_000, .0001), point(2, 21_000, .0011), point(3, 31_000, .0012))
        val track = ActivityMetrics.track(points)
        assertTrue(track.maximumSpeedMetersPerSecond < 2.0)
    }

    @Test fun distanceSplitsInterpolateAndDoNotBridgePauseSegments() {
        val points = (0..11).map { i -> point(i, i * 10_000L, i * .001) }
        val splits = ActivityMetrics.completedSplits("fixture", points, splitMeters = 500.0)
        assertEquals(2, splits.size)
        assertEquals(500.0, splits[0].distanceMeters, 0.001)
        assertEquals(splits[0].elapsedSeconds, splits[1].elapsedSeconds)
        val gap = points.map { if (it.sequence >= 6) it.copy(segmentIndex = 1) else it }
        val resumed = ActivityMetrics.completedSplits("fixture", gap, 1_000.0)
        assertEquals(1, resumed.size)
        assertEquals(90L, resumed.single().elapsedSeconds)
        val mileSplits = ActivityMetrics.completedSplits("fixture", (0..30).map { i -> point(i, i * 10_000L, i * .001) }, ActivityMetrics.METERS_PER_MILE)
        assertEquals(2, mileSplits.size)
        assertEquals(ActivityMetrics.METERS_PER_MILE, mileSplits.first().distanceMeters, 0.001)
    }

    @Test fun elevationJitterAndLowQualityFixesDoNotCreateAscent() {
        val points = listOf(point(0, 1_000, 0.0, altitude = 100.0), point(1, 11_000, .0001, altitude = 101.0), point(2, 21_000, .0002, altitude = 105.0, accuracy = 60f), point(3, 31_000, .0003, altitude = 104.0))
        assertEquals(0.0, ActivityMetrics.track(points).ascentMeters, 0.0)
        val climb = listOf(point(0, 1_000, 0.0, altitude = 100.0), point(1, 11_000, .0001, altitude = 105.0), point(2, 21_000, .0002, altitude = 98.0))
        assertEquals(5.0, ActivityMetrics.track(climb).ascentMeters, 0.0)
        assertEquals(7.0, ActivityMetrics.track(climb).descentMeters, 0.0)
    }

    @Test fun aggregatesRespectSavedEligibilityAndLocaleWeek() {
        val monday = LocalDate.of(2026, 11, 16).atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
        val rows = listOf(activity("a", monday), activity("b", monday + 60_000, distance = 2_000, sport = "CYCLING"), activity("c", monday, status = "RECORDING"), activity("d", monday, deleted = monday))
            .plus(activity("partial", monday, distance = 80_000).copy(dataQualityFlags = "PARTIAL"))
        assertEquals(mapOf("RUNNING" to 1_000L, "CYCLING" to 2_000L), ActivityMetrics.distanceBySport(rows))
        assertEquals(3_000L, ActivityMetrics.distanceByWeek(rows, ZoneId.of("UTC"), Locale.UK).values.single())
        assertEquals(2, ActivityMetrics.activityCounts(rows, ZoneId.of("UTC"), Locale.UK).values.single())
    }

    @Test fun comparisonsGoalAndStreakHaveDefinedZeroBehavior() {
        assertNull(ActivityMetrics.periodChange(3.0, 0.0))
        assertEquals(150.0, ActivityMetrics.goalPercent(15.0, 10.0)!!, 0.0)
        assertEquals(2, ActivityMetrics.consistencyStreak(setOf(LocalDate.parse("2026-11-17"), LocalDate.parse("2026-11-18")), LocalDate.parse("2026-11-18")))
    }

    @Test fun personalBestUsesBestRollingWindowInsideAActivity() {
        val summary = activity("pb", 1_000, distance = 5_000)
        val points = (0..50).map { index ->
            val secondsPerHundredMeters = if (index < 20) 25L else 20L
            point(index, 1_000 + (if (index <= 20) index * 25L else 500L + (index - 20) * secondsPerHundredMeters) * 1_000, index * .0009)
        }
        val pace = ActivityMetrics.personalBest1k(mapOf(summary to points))
        assertNotNull(pace)
        assertEquals(200.0, pace!!, 3.0)
        assertEquals(220.0, ActivityMetrics.personalBest5k(mapOf(summary to points))!!, 4.0)
        val longSummary = activity("pb10", 1_000, distance = 10_000)
        val longPoints = (0..100).map { index -> point(index, 1_000 + index * 30_000L, index * .0009) }
        assertEquals(300.0, ActivityMetrics.personalBest10k(mapOf(longSummary to longPoints))!!, 3.0)
    }

    @Test fun monthlyFrequencyAndWorkoutCompletionExcludeDeletedRows() {
        val start = LocalDate.of(2026, 11, 1).atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
        assertEquals(2, ActivityMetrics.activityCountsByMonth(listOf(activity("a", start), activity("b", start + 86_400_000)), ZoneId.of("UTC")).values.single())
        val sessions = listOf(
            com.pace.app.data.local.entity.PlannedSessionEntity("1", scheduledDate = "2026-11-01", timeZoneId = "UTC", status = "COMPLETED", updatedAtEpochMillis = 1),
            com.pace.app.data.local.entity.PlannedSessionEntity("2", scheduledDate = "2026-11-02", timeZoneId = "UTC", status = "PLANNED", updatedAtEpochMillis = 1),
        )
        assertEquals(50.0, ActivityMetrics.workoutCompletionRate(sessions)!!, 0.0)
    }

    @Test fun weeklyDurationLoadTrendsAndLongestUseComparableEligibleActivities() {
        val zone = ZoneId.of("UTC")
        val monday = LocalDate.of(2026, 11, 16).atStartOfDay(zone).toInstant().toEpochMilli()
        val run = activity("run", monday, distance = 5_000)
        val jog = activity("jog", monday + 86_400_000, sport = "JOGGING", distance = 3_000)
        val rows = listOf(run, jog)
        assertEquals(8_000L, ActivityMetrics.distanceByWeek(rows, zone, Locale.UK).values.single())
        assertEquals(1_000L, ActivityMetrics.durationByWeek(rows, zone, Locale.UK).values.single())
        assertEquals(1_000L, ActivityMetrics.load(rows, LocalDate.of(2026, 11, 18), zone, 7))
        assertEquals(5_000L, ActivityMetrics.longest(rows)?.distanceMeters)
        assertEquals(setOf("RUNNING", "JOGGING"), ActivityMetrics.averageSpeedByWeekAndSport(rows, zone, Locale.UK).values.single().keys)
        assertEquals(setOf("RUNNING", "JOGGING"), ActivityMetrics.averagePaceByWeekAndSport(rows, zone, Locale.UK).values.single().keys)
    }

    @Test fun tenThousandActivityAggregateCompletesWithinFiveSeconds() {
        val start = 1_700_000_000_000L
        val rows = (0 until 10_000).map { activity("$it", start + it * 86_400_000L, distance = 5_000) }
        val began = System.nanoTime()
        val grouped = ActivityMetrics.distanceByWeek(rows, ZoneId.of("UTC"), Locale.UK)
        val elapsedMillis = (System.nanoTime() - began) / 1_000_000
        assertTrue("10K weekly aggregation exceeded 5s: ${elapsedMillis}ms", elapsedMillis < 5_000)
        assertEquals(10_000L, grouped.values.sum() / 5_000L)
    }
}
