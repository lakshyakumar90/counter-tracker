package com.pace.app.ui.recommendations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pace.app.PaceAppContainer
import com.pace.app.PaceUnits
import com.pace.app.PacePreferences
import com.pace.app.R
import com.pace.app.domain.recommendations.PerformancePredictor
import com.pace.app.domain.recommendations.PredictionConfidence
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

@Composable
fun PacePredictionsScreen(container: PaceAppContainer, preferences: PacePreferences, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val activities by container.activityRepository.observeHistory().collectAsState(initial = emptyList())
    var targetDistance by remember { mutableStateOf(10_000.0) }
    var targetMinutes by remember { mutableStateOf(50) }
    var showDetails by remember { mutableStateOf(false) }
    var showEstimator by remember { mutableStateOf(false) }
    val forecast = remember(activities, targetDistance) {
        PerformancePredictor.bestRecentFiveKilometer(activities, LocalDate.now(), ZoneId.systemDefault(), targetDistance)
    }
    val zoneId = if (preferences.units == PaceUnits.Imperial) R.string.pace_predictions_per_mile else R.string.pace_predictions_per_kilometer
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            androidx.compose.material3.IconButton(onClick = onBack) {
                androidx.compose.material3.Icon(com.pace.app.PaceAppIcons.Back, stringResource(R.string.pace_action_back))
            }
            Text(stringResource(R.string.pace_predictions_title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(1_000.0, 5_000.0, 10_000.0, 21_097.5).forEach { distance ->
                FilterChip(targetDistance == distance, { targetDistance = distance }, label = { Text(distanceLabel(distance)) })
            }
        }
        if (forecast == null) {
            Card(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.pace_predictions_no_eligible_effort), Modifier.padding(16.dp))
            }
        } else {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        androidx.compose.material3.Icon(com.pace.app.PaceAppIcons.Insights, null, tint = MaterialTheme.colorScheme.primary)
                        Text(stringResource(R.string.pace_predictions_forecast), style = MaterialTheme.typography.titleMedium)
                    }
                    Text(formatDuration(forecast.predictedSeconds), style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.primary)
                    val pace = PerformancePredictor.secondsPerKilometer(forecast.predictedSeconds, forecast.targetDistanceMeters)
                    val perDistance = pace?.let { if (preferences.units == PaceUnits.Imperial) it * 1.609344 else it }
                    if (perDistance != null) Text(stringResource(R.string.pace_predictions_target_pace, formatPace(perDistance), stringResource(zoneId)))
                    androidx.compose.material3.Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ) {
                        Text(stringResource(confidenceLabel(forecast.confidence)), Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelLarge)
                    }
                    androidx.compose.material3.OutlinedButton(
                        onClick = { showDetails = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        androidx.compose.material3.Icon(com.pace.app.PaceAppIcons.Spark, contentDescription = null, modifier = Modifier.size(16.dp))
                        androidx.compose.foundation.layout.Spacer(Modifier.size(6.dp))
                        Text(stringResource(R.string.pace_predictions_details))
                    }
                }
            }
        }
        com.pace.app.ui.components.PaceSecondaryButton(
            stringResource(R.string.pace_predictions_target_estimator),
            onClick = { showEstimator = !showEstimator },
            modifier = Modifier.fillMaxWidth(),
            icon = com.pace.app.PaceAppIcons.Add,
        )
        if (showEstimator) Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = targetMinutes.toString(),
                    onValueChange = { targetMinutes = it.filter(Char::isDigit).take(4).toIntOrNull()?.coerceIn(1, 2_160) ?: 1 },
                    label = { Text(stringResource(R.string.pace_predictions_target_minutes)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                val targetPace = PerformancePredictor.secondsPerKilometer(targetMinutes * 60.0, targetDistance)
                if (targetPace != null) {
                    val displayed = if (preferences.units == PaceUnits.Imperial) targetPace * 1.609344 else targetPace
                    Text(stringResource(R.string.pace_predictions_goal_pace, formatPace(displayed), stringResource(zoneId)))
                }
            }
        }
        if (showDetails && forecast != null) androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDetails = false },
            title = { Text(stringResource(R.string.pace_predictions_details)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.pace_predictions_source_effort, forecast.sourceFiveKilometerSeconds.toInt() / 60, forecast.sourceFiveKilometerSeconds.toInt() % 60, forecast.recentEffortCount))
                    if (targetDistance >= 10_000) Text(stringResource(R.string.pace_predictions_long_distance_limit))
                    Text(stringResource(R.string.pace_predictions_disclaimer))
                }
            },
            confirmButton = { TextButton(onClick = { showDetails = false }) { Text(stringResource(R.string.pace_action_back)) } },
        )
    }
}

private fun distanceLabel(meters: Double) = when (meters) {
    1_000.0 -> "1K"
    5_000.0 -> "5K"
    10_000.0 -> "10K"
    else -> "Half"
}

private fun formatDuration(seconds: Double): String {
    val value = seconds.toLong().coerceAtLeast(0)
    return "%d:%02d:%02d".format(Locale.getDefault(), value / 3_600, value % 3_600 / 60, value % 60)
}

private fun formatPace(seconds: Double): String {
    val value = seconds.toLong().coerceAtLeast(0)
    return "%d:%02d".format(Locale.getDefault(), value / 60, value % 60)
}

private fun confidenceLabel(confidence: PredictionConfidence) = when (confidence) {
    PredictionConfidence.LIMITED -> R.string.pace_predictions_confidence_limited
    PredictionConfidence.MODERATE -> R.string.pace_predictions_confidence_moderate
    PredictionConfidence.BETTER_SUPPORTED -> R.string.pace_predictions_confidence_supported
}
