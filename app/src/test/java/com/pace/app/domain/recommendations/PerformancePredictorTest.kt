package com.pace.app.domain.recommendations

import com.pace.app.data.local.entity.ActivityEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.pow

class PerformancePredictorTest {
    private val zone = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 11, 18)

    @Test fun riegelPredictionMatchesReferenceAndSameDistance() {
        val tenK = PerformancePredictor.riegel(1_500.0, 5_000.0, 10_000.0)!!
        assertEquals(1_500.0 * 2.0.pow(1.06), tenK, 0.01)
        assertEquals(1_500.0, PerformancePredictor.riegel(1_500.0, 5_000.0, 5_000.0)!!, 0.01)
    }

    @Test fun invalidAndUnsupportedInputsAreRejected() {
        assertNull(PerformancePredictor.riegel(Double.NaN, 5_000.0, 10_000.0))
        assertNull(PerformancePredictor.riegel(60.0, 5_000.0, 10_000.0))
        assertNull(PerformancePredictor.bestRecentFiveKilometer(listOf(activity("a", today, 5_000, 1_500)), today, zone, 6_000.0))
    }

    @Test fun onlyRecentEligibleRunEffortsAreUsedAndConfidenceIsShown() {
        val rows = listOf(
            activity("best", today.minusDays(5), 5_000, 1_500),
            activity("other", today.minusDays(45), 5_050, 1_600),
            activity("old", today.minusDays(100), 5_000, 1_300),
            activity("cycling", today.minusDays(1), 5_000, 1_200, sport = "CYCLING"),
        )
        val result = PerformancePredictor.bestRecentFiveKilometer(rows, today, zone, 10_000.0)!!
        assertEquals("best", result.sourceActivity.id)
        assertEquals(1, result.recentEffortCount)
        assertEquals(PredictionConfidence.LIMITED, result.confidence)
        assertTrue(result.predictedSeconds > result.sourceFiveKilometerSeconds)
    }

    private fun activity(id: String, date: LocalDate, distance: Long, moving: Long, sport: String = "RUNNING") = ActivityEntity(
        id = id,
        sport = sport,
        startedAtEpochMillis = date.atStartOfDay(zone).toInstant().toEpochMilli(),
        elapsedSeconds = moving,
        movingSeconds = moving,
        distanceMeters = distance,
        createdAtEpochMillis = 1,
        updatedAtEpochMillis = 1,
    )
}
