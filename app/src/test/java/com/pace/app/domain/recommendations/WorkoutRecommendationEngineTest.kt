package com.pace.app.domain.recommendations

import com.pace.app.data.local.dao.WorkoutTemplateWithIntervals
import com.pace.app.data.local.entity.ActivityEntity
import com.pace.app.data.local.entity.WorkoutIntervalEntity
import com.pace.app.data.local.entity.WorkoutTemplateEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class WorkoutRecommendationEngineTest {
    private val today = LocalDate.of(2026, 11, 18)
    private val zone = ZoneId.of("UTC")

    @Test fun newAthleteGetsConservativeOfflineStarterOptions() {
        val result = WorkoutRecommendationEngine.recommend(request())
        assertEquals(3, result.size)
        assertEquals(RecommendationReason.STARTER, result.first().reason)
        assertTrue(result.all { it.durationMinutes <= 30 })
    }

    @Test fun timeLimitFiltersOutLongTemplatesAndSportPreferenceBreaksTies() {
        val templates = listOf(
            template("short-run", "Easy run", "Running", 20),
            template("short-cycle", "Easy ride", "Cycling", 20),
            template("long-run", "Long endurance ride", "Cycling", 60),
        )
        val result = WorkoutRecommendationEngine.recommend(request(templates = templates, preferred = "Cycling", minutes = 30))
        assertEquals(listOf("short-cycle", "short-run"), result.map { it.id })
        assertTrue(result.none { it.id == "long-run" })
    }

    @Test fun noTemplateFittingTimeReturnsNoSuggestion() {
        val result = WorkoutRecommendationEngine.recommend(request(templates = listOf(template("long", "Long run", "Running", 45)), minutes = 20))
        assertTrue(result.isEmpty())
    }

    @Test fun recentLoadAvoidsHardTemplatesAndExplainsRecoveryChoice() {
        val history = (0..3).map { daysAgo -> activity("a$daysAgo", today.minusDays(daysAgo.toLong()), 5_000, 4_000) }
        val templates = listOf(
            template("intervals", "Speed intervals", "Running", 30),
            template("easy", "Easy run", "Running", 25),
        )
        val result = WorkoutRecommendationEngine.recommend(request(templates = templates, minutes = 40, history = history))
        assertTrue(result.none { it.id == "intervals" })
        assertEquals(RecommendationReason.RECOVERY, result.single().reason)
        assertEquals("easy", result.single().id)
    }

    private fun request(
        templates: List<WorkoutTemplateWithIntervals> = emptyList(),
        preferred: String = "Running",
        minutes: Int = 30,
        history: List<ActivityEntity> = emptyList(),
    ) = WorkoutRecommendationRequest(history, templates, preferred, minutes, WorkoutGoal.CONSISTENCY, today, zone)

    private fun template(id: String, name: String, sport: String, minutes: Int) = WorkoutTemplateWithIntervals(
        WorkoutTemplateEntity(id, name, sport, targetDurationSeconds = minutes * 60L, createdAtEpochMillis = 1, updatedAtEpochMillis = 1),
        listOf(WorkoutIntervalEntity(id, 0, "WORK", durationSeconds = minutes * 60L)),
    )

    private fun activity(id: String, date: LocalDate, distance: Long, moving: Long) = ActivityEntity(
        id = id,
        sport = "RUNNING",
        startedAtEpochMillis = date.atStartOfDay(zone).toInstant().toEpochMilli(),
        elapsedSeconds = moving,
        movingSeconds = moving,
        distanceMeters = distance,
        createdAtEpochMillis = 1,
        updatedAtEpochMillis = 1,
    )
}
