package com.pace.app.ui.recommendations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pace.app.PaceAppContainer
import com.pace.app.PaceAppIcons
import com.pace.app.R
import com.pace.app.domain.recommendations.RecommendationReason
import com.pace.app.domain.recommendations.WorkoutGoal
import com.pace.app.domain.recommendations.WorkoutRecommendation
import com.pace.app.domain.recommendations.WorkoutRecommendationEngine
import com.pace.app.domain.recommendations.WorkoutRecommendationRequest
import java.time.LocalDate
import java.time.ZoneId

private val sports = listOf("Running", "Cycling", "Walking", "Jogging")
private val durations = listOf(15, 30, 45, 60)
private val goals = listOf(
    WorkoutGoal.CONSISTENCY,
    WorkoutGoal.ENDURANCE,
    WorkoutGoal.SPEED,
    WorkoutGoal.RECOVERY,
)

/**
 * A small, self-contained workout suggestion. The user can change sport, time and goal
 * instead of being handed one fixed 30-minute running plan.
 */
@Composable
fun PaceInstantWorkoutPanel(
    container: PaceAppContainer,
    preferredSport: String = "Running",
    onUse: (WorkoutRecommendation) -> Unit,
    modifier: Modifier = Modifier,
) {
    val history by container.activityRepository.observeHistory().collectAsState(initial = emptyList())
    val templates by container.workoutRepository.observeTemplates().collectAsState(initial = emptyList())
    var sport by rememberSaveable { mutableStateOf(preferredSport) }
    var minutes by rememberSaveable { mutableStateOf(30) }
    var goal by rememberSaveable { mutableStateOf(WorkoutGoal.CONSISTENCY) }
    val recommendations = remember(history, templates, sport, minutes, goal) {
        WorkoutRecommendationEngine.recommend(
            WorkoutRecommendationRequest(history, templates, sport, minutes, goal, LocalDate.now(), ZoneId.systemDefault()),
        )
    }
    var showCustomizer by rememberSaveable { mutableStateOf(false) }
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(PaceAppIcons.Spark, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Text(stringResource(R.string.pace_recommendations_title), style = MaterialTheme.typography.titleMedium)
                }
                TextButton(onClick = { showCustomizer = !showCustomizer }) {
                    Text(if (showCustomizer) "Done" else "Customize")
                }
            }

            if (showCustomizer) {
                SelectorRow(label = stringResource(R.string.pace_recommendations_sport_label)) {
                    sports.forEach { option ->
                        FilterChip(
                            selected = sport == option,
                            onClick = { sport = option },
                            label = { Text(option, maxLines = 1) },
                        )
                    }
                }
                SelectorRow(label = stringResource(R.string.pace_recommendations_time_label)) {
                    durations.forEach { option ->
                        FilterChip(
                            selected = minutes == option,
                            onClick = { minutes = option },
                            label = { Text(stringResource(R.string.pace_recommendation_minutes, option), maxLines = 1) },
                        )
                    }
                }
                SelectorRow(label = stringResource(R.string.pace_recommendations_goal_label)) {
                    goals.forEach { option ->
                        FilterChip(
                            selected = goal == option,
                            onClick = { goal = option },
                            label = { Text(stringResource(goalLabel(option)), maxLines = 1) },
                        )
                    }
                }
            }

            val workout = recommendations.firstOrNull()
            if (workout != null) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(workout.name, style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                stringResource(R.string.pace_recommendation_detail, workout.sport, workout.durationMinutes),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Text(
                            stringResource(reasonLabel(workout.reason)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                com.pace.app.ui.components.PacePrimaryButton(
                    label = stringResource(R.string.pace_recommendation_start),
                    onClick = { onUse(workout) },
                    modifier = Modifier.fillMaxWidth(),
                    icon = PaceAppIcons.Play,
                )
            } else {
                Text(stringResource(R.string.pace_recommendations_no_time_fit), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun SelectorRow(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            content()
        }
    }
}

private fun goalLabel(goal: WorkoutGoal): Int = when (goal) {
    WorkoutGoal.CONSISTENCY -> R.string.pace_recommendations_goal_consistency
    WorkoutGoal.ENDURANCE -> R.string.pace_recommendations_goal_endurance
    WorkoutGoal.SPEED -> R.string.pace_recommendations_goal_speed
    WorkoutGoal.RECOVERY -> R.string.pace_recommendations_goal_recovery
}

private fun reasonLabel(reason: RecommendationReason): Int = when (reason) {
    RecommendationReason.RETURN_TO_ROUTINE -> R.string.pace_recommendations_reason_routine
    RecommendationReason.RECOVERY -> R.string.pace_recommendations_reason_recovery
    RecommendationReason.SPORT_PREFERENCE -> R.string.pace_recommendations_reason_sport
    RecommendationReason.GOAL_FOCUS -> R.string.pace_recommendations_reason_goal
    RecommendationReason.TIME_FIT -> R.string.pace_recommendations_reason_time
    RecommendationReason.STARTER -> R.string.pace_recommendations_reason_starter
}
