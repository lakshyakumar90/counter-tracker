package com.pace.app.domain.recommendations

import com.pace.app.data.local.entity.ActivityEntity
import com.pace.app.domain.metrics.ActivityMetrics
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.pow

enum class PredictionConfidence { LIMITED, MODERATE, BETTER_SUPPORTED }

data class PerformanceForecast(
    val sourceActivity: ActivityEntity,
    val sourceFiveKilometerSeconds: Double,
    val targetDistanceMeters: Double,
    val predictedSeconds: Double,
    val confidence: PredictionConfidence,
    val recentEffortCount: Int,
)

object PerformancePredictor {
    val targetDistancesMeters = listOf(1_000.0, 5_000.0, 10_000.0, 21_097.5)

    /** Standard Riegel power law estimate; returns null for implausible or invalid inputs. */
    fun riegel(baseSeconds: Double, baseMeters: Double, targetMeters: Double, exponent: Double = 1.06): Double? {
        if (!baseSeconds.isFinite() || !baseMeters.isFinite() || !targetMeters.isFinite() || !exponent.isFinite()) return null
        if (baseSeconds !in 120.0..18_000.0 || baseMeters !in 1_000.0..50_000.0 || targetMeters !in 1_000.0..42_195.0 || exponent !in 1.0..1.15) return null
        val result = baseSeconds * (targetMeters / baseMeters).pow(exponent)
        return result.takeIf { it.isFinite() && it in 60.0..129_600.0 }
    }

    fun bestRecentFiveKilometer(
        activities: List<ActivityEntity>,
        today: LocalDate,
        zone: ZoneId,
        targetMeters: Double,
    ): PerformanceForecast? {
        if (targetMeters !in targetDistancesMeters) return null
        val cutoff = today.minusDays(90).atStartOfDay(zone).toInstant().toEpochMilli()
        val eligible = activities.filter { row ->
            ActivityMetrics.isEligibleSummary(row) && row.sport.uppercase() in setOf("RUNNING", "JOGGING") &&
                row.startedAtEpochMillis >= cutoff && row.movingSeconds in 120..18_000 && row.distanceMeters in 4_500..5_500
        }
        val source = eligible.minByOrNull { it.movingSeconds.toDouble() / it.distanceMeters } ?: return null
        val baseSeconds = source.movingSeconds * 5_000.0 / source.distanceMeters
        val estimate = riegel(baseSeconds, 5_000.0, targetMeters) ?: return null
        val recent = eligible.count { it.startedAtEpochMillis >= today.minusDays(30).atStartOfDay(zone).toInstant().toEpochMilli() }
        val confidence = when {
            recent >= 4 -> PredictionConfidence.BETTER_SUPPORTED
            recent >= 2 -> PredictionConfidence.MODERATE
            else -> PredictionConfidence.LIMITED
        }
        return PerformanceForecast(source, baseSeconds, targetMeters, estimate, confidence, recent)
    }

    fun secondsPerKilometer(targetSeconds: Double, targetMeters: Double): Double? {
        if (!targetSeconds.isFinite() || !targetMeters.isFinite() || targetSeconds !in 60.0..129_600.0 || targetMeters !in 1_000.0..42_195.0) return null
        return targetSeconds * 1_000.0 / targetMeters
    }
}
