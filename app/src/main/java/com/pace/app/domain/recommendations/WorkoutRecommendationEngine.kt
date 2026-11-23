package com.pace.app.domain.recommendations

import com.pace.app.data.local.dao.WorkoutTemplateWithIntervals
import com.pace.app.data.local.entity.ActivityEntity
import com.pace.app.domain.metrics.ActivityMetrics
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class WorkoutGoal { CONSISTENCY, ENDURANCE, SPEED, RECOVERY }

enum class RecommendationReason { RETURN_TO_ROUTINE, RECOVERY, SPORT_PREFERENCE, GOAL_FOCUS, TIME_FIT, STARTER }

data class WorkoutRecommendationRequest(
    val history: List<ActivityEntity>,
    val templates: List<WorkoutTemplateWithIntervals>,
    val preferredSport: String,
    val availableMinutes: Int,
    val goal: WorkoutGoal,
    val today: LocalDate = LocalDate.now(),
    val zoneId: ZoneId = ZoneId.systemDefault(),
)

data class WorkoutRecommendation(
    val id: String,
    val name: String,
    val sport: String,
    val durationMinutes: Int,
    val reason: RecommendationReason,
)

/** Deterministic local rules. Inputs are activity summaries only; no network or AI is used. */
object WorkoutRecommendationEngine {
    fun recommend(request: WorkoutRecommendationRequest): List<WorkoutRecommendation> {
        require(request.availableMinutes in 5..240)
        val history = request.history.filter(ActivityMetrics::isEligibleSummary)
        val cutoff = request.today.minusDays(6).atStartOfDay(request.zoneId).toInstant().toEpochMilli()
        val lastWeek = history.filter { it.startedAtEpochMillis >= cutoff }
        val inactive = history.maxOfOrNull { Instant.ofEpochMilli(it.startedAtEpochMillis).atZone(request.zoneId).toLocalDate().toEpochDay() }
            ?.let { request.today.toEpochDay() - it >= 7 } ?: true
        val recentLoadSeconds = lastWeek.sumOf { it.movingSeconds }
        val latestActivityDate = history.maxOfOrNull { Instant.ofEpochMilli(it.startedAtEpochMillis).atZone(request.zoneId).toLocalDate() }
        val consecutiveDay = latestActivityDate != null && latestActivityDate >= request.today.minusDays(1)
        val highLoad = lastWeek.size >= 4 || recentLoadSeconds >= 3 * 60 * 60 || consecutiveDay

        val candidates = if (request.templates.isEmpty()) starterSuggestions() else request.templates.map { joined ->
            val template = joined.template
            val intervalSeconds = joined.intervals.sumOf { (it.durationSeconds ?: 0L) * it.repeatCount.coerceAtLeast(1) }
            val duration = template.targetDurationSeconds?.div(60)?.toInt()
                ?: intervalSeconds.takeIf { it > 0 }?.let { ((it + 59) / 60).toInt() }
                ?: 20
            WorkoutRecommendation(template.id, template.name, template.sport, duration.coerceAtLeast(1), RecommendationReason.GOAL_FOCUS)
        }
        val safeCandidates = if (highLoad) candidates.filterNot(::isHardSession).ifEmpty {
            starterSuggestions().filter { it.sport.equals("Home workout", true) || it.sport.equals("Walking", true) }
        } else candidates
        val fits = safeCandidates.filter { it.durationMinutes <= request.availableMinutes }
        val pool = fits
        return pool.distinctBy { it.id }.sortedWith(
            compareByDescending<WorkoutRecommendation> { score(it, request, inactive, highLoad) }
                .thenBy { it.durationMinutes }.thenBy { it.id },
        ).take(3).map { recommendation ->
            recommendation.copy(reason = reason(recommendation, request, inactive, highLoad))
        }
    }

    private fun starterSuggestions() = listOf(
        WorkoutRecommendation("starter-easy-run", "Easy run", "Running", 20, RecommendationReason.STARTER),
        WorkoutRecommendation("starter-recovery-walk", "Recovery walk", "Walking", 20, RecommendationReason.STARTER),
        WorkoutRecommendation("starter-mobility", "Gentle mobility", "Home workout", 12, RecommendationReason.STARTER),
    )

    private fun score(item: WorkoutRecommendation, request: WorkoutRecommendationRequest, inactive: Boolean, highLoad: Boolean): Int {
        val name = item.name.lowercase()
        var score = 0
        if (item.sport.equals(request.preferredSport, true)) score += 30
        if (item.durationMinutes <= request.availableMinutes) score += 20
        when (request.goal) {
            WorkoutGoal.CONSISTENCY -> if (name.contains("easy") || name.contains("recovery") || name.contains("starter")) score += 25
            WorkoutGoal.ENDURANCE -> if (name.contains("endurance") || name.contains("long") || name.contains("steady")) score += 25
            WorkoutGoal.SPEED -> if (name.contains("speed") || name.contains("interval") || name.contains("tempo")) score += 25
            WorkoutGoal.RECOVERY -> if (name.contains("recovery") || name.contains("walk") || name.contains("mobility")) score += 30
        }
        if (inactive && (name.contains("easy") || name.contains("recovery") || item.sport.equals("Walking", true))) score += 35
        if (highLoad && (name.contains("hard") || name.contains("speed") || name.contains("interval"))) score -= 50
        return score
    }

    private fun isHardSession(item: WorkoutRecommendation): Boolean = item.name.lowercase().let { name ->
        listOf("hard", "speed", "interval", "tempo", "sprint", "intense").any(name::contains)
    }

    private fun reason(item: WorkoutRecommendation, request: WorkoutRecommendationRequest, inactive: Boolean, highLoad: Boolean): RecommendationReason = when {
        request.templates.isEmpty() -> RecommendationReason.STARTER
        highLoad -> RecommendationReason.RECOVERY
        inactive -> RecommendationReason.RETURN_TO_ROUTINE
        highLoad && item.name.lowercase().let { it.contains("recovery") || it.contains("easy") || it.contains("walk") } -> RecommendationReason.RECOVERY
        item.sport.equals(request.preferredSport, true) -> RecommendationReason.SPORT_PREFERENCE
        item.durationMinutes <= request.availableMinutes -> if (request.goal == WorkoutGoal.SPEED || request.goal == WorkoutGoal.ENDURANCE) RecommendationReason.GOAL_FOCUS else RecommendationReason.TIME_FIT
        else -> RecommendationReason.GOAL_FOCUS
    }
}
