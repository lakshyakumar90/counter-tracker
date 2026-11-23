package com.pace.app.tracking.steps

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pace.app.PaceAppIcons
import com.pace.app.R
import com.pace.app.data.local.dao.DailyStepsDao
import com.pace.app.ui.components.PaceSecondaryButton
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.roundToLong
import kotlinx.coroutines.delay

@Composable
internal fun PaceDailyStepsCard(dao: DailyStepsDao, imperial: Boolean) {
    val context = LocalContext.current
    val available = remember(context) { PaceSteps.sensor(context) != null }
    val widgetManager = remember(context) { AppWidgetManager.getInstance(context) }
    val today by produceState(LocalDate.now()) {
        while (true) {
            value = LocalDate.now()
            delay(30_000)
        }
    }
    val data by remember(dao, today) { dao.observeRange(today.toString(), today.toString()) }.collectAsState(initial = emptyList())
    val count = data.firstOrNull()?.count ?: 0L
    var showHistory by rememberSaveable { mutableStateOf(false) }
    var enabled by remember { mutableStateOf(PaceSteps.enabled(context) && PaceSteps.hasPermission(context)) }
    var warning by remember { mutableStateOf<Int?>(null) }
    val activityPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) warning = R.string.pace_steps_permission
        else {
            enabled = PaceSteps.start(context, userEnabled = true)
            warning = if (enabled) null else R.string.pace_steps_tracking_error
        }
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(PaceAppIcons.Insights, null, tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.pace_steps_title), style = MaterialTheme.typography.titleMedium)
            }
            androidx.compose.foundation.layout.Spacer(Modifier.height(2.dp))
            if (available) {
                Text(
                    stringResource(R.string.pace_steps_today_value, NumberFormat.getIntegerInstance().format(count)),
                    style = MaterialTheme.typography.headlineMedium,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StepValue(stringResource(R.string.pace_steps_distance), estimatedDistance(count, imperial), Modifier.weight(1f))
                    StepValue(stringResource(R.string.pace_steps_calories), stringResource(R.string.pace_steps_kcal, estimatedCalories(count)), Modifier.weight(1f))
                }
                PaceSecondaryButton(
                    stringResource(if (enabled) R.string.pace_steps_disable else R.string.pace_steps_enable),
                    onClick = {
                        if (enabled) {
                            PaceSteps.stop(context)
                            enabled = false
                        } else if (Build.VERSION.SDK_INT >= 29 && !PaceSteps.hasPermission(context)) {
                            activityPermission.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                        } else {
                            enabled = PaceSteps.start(context, userEnabled = true)
                            warning = if (enabled) null else R.string.pace_steps_tracking_error
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (enabled && PaceSteps.batteryOptimisationActive(context)) {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ) {
                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(stringResource(R.string.pace_steps_battery_title), style = MaterialTheme.typography.titleSmall)
                            Text(stringResource(R.string.pace_steps_battery_body), style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = {
                                runCatching { context.startActivity(PaceSteps.batterySettingsIntent()) }
                            }) { Text(stringResource(R.string.pace_steps_battery_action)) }
                        }
                    }
                }
                if (widgetManager.isRequestPinAppWidgetSupported) {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(
                                    stringResource(R.string.pace_steps_add_widget),
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(
                                    stringResource(R.string.pace_steps_widget_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            OutlinedButton(
                                onClick = {
                                    widgetManager.requestPinAppWidget(ComponentName(context, PaceStepWidget::class.java), null, null)
                                },
                            ) {
                                Icon(PaceAppIcons.Add, null, modifier = Modifier.size(16.dp))
                                androidx.compose.foundation.layout.Spacer(Modifier.size(4.dp))
                                Text(stringResource(R.string.pace_plan_use_starter))
                            }
                        }
                    }
                }
                TextButton(onClick = { showHistory = true }) { Text(stringResource(R.string.pace_steps_history)) }
                warning?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
            } else Text(stringResource(R.string.pace_steps_unsupported), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (showHistory) StepHistory(dao, today, imperial) { showHistory = false }
}

@Composable
private fun StepValue(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun StepMetrics(count: Long, imperial: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            R.string.pace_steps_title to NumberFormat.getIntegerInstance().format(count),
            R.string.pace_steps_distance to estimatedDistance(count, imperial),
            R.string.pace_steps_calories to stringResource(R.string.pace_steps_kcal, estimatedCalories(count)),
        ).forEach { (label, value) ->
            Column(Modifier.weight(1f)) {
                Text(value, style = MaterialTheme.typography.titleLarge)
                Text(stringResource(label), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun StepHistory(dao: DailyStepsDao, today: LocalDate, imperial: Boolean, close: () -> Unit) {
    var offset by rememberSaveable { mutableStateOf(0L) }
    var selected by rememberSaveable { mutableStateOf(today.toString()) }
    val month = YearMonth.from(today).plusMonths(offset)
    val rows by remember(dao, month) {
        dao.observeRange(month.atDay(1).toString(), month.atEndOfMonth().toString())
    }.collectAsState(initial = emptyList())
    val counts = rows.associate { it.day to it.count }
    val first = month.atDay(1)
    val blanks = first.dayOfWeek.value - 1
    val max = counts.values.maxOrNull()?.takeIf { it > 0 } ?: 1L
    AlertDialog(
        onDismissRequest = close,
        title = { Text(stringResource(R.string.pace_steps_history)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { offset--; selected = month.minusMonths(1).atDay(1).toString() }) {
                        Icon(PaceAppIcons.Back, stringResource(R.string.pace_plan_previous_month))
                    }
                    Text(month.format(DateTimeFormatter.ofPattern("LLLL yyyy")), Modifier.weight(1f))
                    TextButton(enabled = offset < 0, onClick = { offset++; selected = month.plusMonths(1).atDay(1).toString() }) {
                        Icon(PaceAppIcons.Chevron, stringResource(R.string.pace_plan_next_month))
                    }
                }
                (0 until blanks + month.lengthOfMonth()).chunked(7).forEach { week ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        week.forEach { index ->
                            val date = (index - blanks + 1).takeIf { it > 0 }?.let(month::atDay)
                            val key = date?.toString()
                            val count = counts[key] ?: 0L
                            if (date == null) androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                            else Surface(
                                enabled = date <= today,
                                onClick = { selected = date.toString() },
                                modifier = Modifier.weight(1f).semantics {
                                    contentDescription = date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)) +
                                        ": " + NumberFormat.getIntegerInstance().format(count)
                                },
                                shape = MaterialTheme.shapes.extraSmall,
                                color = if (selected == key) MaterialTheme.colorScheme.primaryContainer
                                else if (count == 0L) MaterialTheme.colorScheme.surfaceVariant
                                else MaterialTheme.colorScheme.primary.copy(alpha = .16f + .28f * count.toFloat() / max),
                            ) {
                                Box(Modifier.height(35.dp), contentAlignment = Alignment.Center) {
                                    Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                        repeat(7 - week.size) { androidx.compose.foundation.layout.Spacer(Modifier.weight(1f)) }
                    }
                }
                Text(LocalDate.parse(selected).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)), style = MaterialTheme.typography.titleMedium)
                StepMetrics(counts[selected] ?: 0L, imperial)
            }
        },
        confirmButton = { TextButton(onClick = close) { Text(stringResource(R.string.insights_done)) } },
    )
}

internal fun estimatedDistance(steps: Long, imperial: Boolean): String {
    val meters = steps.coerceAtLeast(0) * .75
    val unit = if (imperial) "mi" else "km"
    return String.format(Locale.getDefault(), "%.2f %s", meters / if (imperial) 1609.344 else 1000.0, unit)
}

internal fun estimatedCalories(steps: Long): Long = (steps.coerceAtLeast(0) * .04).roundToLong()
