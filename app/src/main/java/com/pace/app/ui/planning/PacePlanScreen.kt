package com.pace.app.ui.planning

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pace.app.PaceAppContainer
import com.pace.app.PaceAppIcons
import com.pace.app.ui.components.PacePrimaryButton
import com.pace.app.R
import com.pace.app.data.local.entity.PlannedSessionEntity
import com.pace.app.data.local.entity.WorkoutIntervalEntity
import com.pace.app.data.local.entity.WorkoutTemplateEntity
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.ZoneId
import java.time.YearMonth
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters
import java.util.UUID

/** Offline planner backed by Room. Dates are ISO local dates to avoid UTC day drift. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun PacePlanScreen(container: PaceAppContainer, onStartWorkout: (String) -> Unit = {}, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val today = remember { LocalDate.now() }
    var month by remember { mutableStateOf(YearMonth.from(today)) }
    var weekAnchor by remember { mutableStateOf(today) }
    var weekView by remember { mutableStateOf(false) }
    var monthPickerOpen by remember { mutableStateOf(false) }
    var durationMultiplier by remember { mutableStateOf(60L) }
    var distanceMultiplier by remember { mutableStateOf(1000) }
    val scope = rememberCoroutineScope()
    val weekStart = remember(weekAnchor) { weekAnchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }
    val start = if (weekView) weekStart.toString() else month.atDay(1).toString()
    val end = if (weekView) weekStart.plusDays(7).toString() else month.plusMonths(1).atDay(1).toString()
    val sessions by container.workoutRepository.observeCalendar(start, end).collectAsState(initial = emptyList())
    val templates by container.workoutRepository.observeTemplates().collectAsState(initial = emptyList())
    var isCreatingTemplateOnly by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var editorOpen by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    androidx.activity.compose.BackHandler(enabled = editorOpen) { editorOpen = false }
    var title by remember { mutableStateOf("") }
    var targetDurationText by remember { mutableStateOf("") }
    var targetDistanceText by remember { mutableStateOf("") }
    var editingTemplateId by remember { mutableStateOf<String?>(null) }
    var reschedulingSessionId by remember { mutableStateOf<String?>(null) }
    var undoReschedule by remember { mutableStateOf<Triple<String, String, Boolean>?>(null) }
    var intervals by remember { mutableStateOf(emptyList<WorkoutIntervalEntity>()) }
    var dateText by remember(month) { mutableStateOf(today.coerceIn(month.atDay(1), month.atEndOfMonth()).toString()) }
    var sport by remember { mutableStateOf("Running") }
    var error by remember { mutableStateOf<String?>(null) }
    androidx.compose.runtime.LaunchedEffect(error) {
        error?.let { android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_LONG).show() }
    }
    fun feedback(resource: Int) = android.widget.Toast.makeText(context, resource, android.widget.Toast.LENGTH_SHORT).show()
    var pendingReminderSession by remember { mutableStateOf<PlannedSessionEntity?>(null) }
    var detailSessionId by remember { mutableStateOf<String?>(null) }
    var deletingTemplateId by remember { mutableStateOf<String?>(null) }
    var deletingSessionId by remember { mutableStateOf<String?>(null) }
    var scheduleTemplateId by remember { mutableStateOf<String?>(null) }
    var weekCopyCount by remember { mutableStateOf<Int?>(null) }
    val reminderPermissionDeniedMessage = stringResource(R.string.pace_plan_reminder_permission_denied)
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val pending = pendingReminderSession
        pendingReminderSession = null
        if (granted && pending != null) {
            scope.launch {
                val enabled = pending.copy(reminderEnabled = true, updatedAtEpochMillis = System.currentTimeMillis())
                container.workoutRepository.savePlannedSession(enabled)
                PlanReminderScheduler.schedule(context, enabled)
            }
        } else if (!granted) {
            error = reminderPermissionDeniedMessage
        }
    }
    val starterTemplates = listOf(
        Triple(R.string.pace_plan_starter_easy_run, "Running", listOf(
            WorkoutIntervalEntity("", 0, "WARMUP", durationSeconds = 300),
            WorkoutIntervalEntity("", 1, "WORK", durationSeconds = 1200),
            WorkoutIntervalEntity("", 2, "COOLDOWN", durationSeconds = 300),
        )),
        Triple(R.string.pace_plan_starter_recovery_walk, "Walking", listOf(
            WorkoutIntervalEntity("", 0, "WARMUP", durationSeconds = 180),
            WorkoutIntervalEntity("", 1, "WORK", durationSeconds = 1200),
            WorkoutIntervalEntity("", 2, "COOLDOWN", durationSeconds = 180),
        )),
        Triple(R.string.pace_plan_starter_ride, "Cycling", listOf(
            WorkoutIntervalEntity("", 0, "WARMUP", durationSeconds = 300),
            WorkoutIntervalEntity("", 1, "WORK", durationSeconds = 1800),
            WorkoutIntervalEntity("", 2, "COOLDOWN", durationSeconds = 300),
        )),
        Triple(R.string.pace_plan_starter_jog, "Jogging", listOf(
            WorkoutIntervalEntity("", 0, "WARMUP", durationSeconds = 300),
            WorkoutIntervalEntity("", 1, "WORK", durationSeconds = 900),
            WorkoutIntervalEntity("", 2, "COOLDOWN", durationSeconds = 300),
        )),
    )

    val calendarScroll = rememberScrollState()
    val editorScroll = rememberScrollState()
    Column(modifier.fillMaxSize().verticalScroll(if (editorOpen) editorScroll else calendarScroll).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (!editorOpen) {
            Text(stringResource(R.string.pace_nav_plan), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {
                    if (weekView) weekAnchor = weekStart.minusWeeks(1) else month = month.minusMonths(1)
                }) { Icon(PaceAppIcons.Back, contentDescription = stringResource(if (weekView) R.string.pace_plan_previous_week else R.string.pace_plan_previous_month)) }
                val formatter = java.time.format.DateTimeFormatter.ofPattern(
                    if (weekView) "d MMM" else "LLLL yyyy",
                    java.util.Locale.getDefault(),
                )
                val period = if (weekView) {
                    stringResource(R.string.pace_plan_date_range, weekStart.format(formatter), weekStart.plusDays(6).format(formatter))
                } else month.format(formatter)
                TextButton(onClick = { monthPickerOpen = true }, modifier = Modifier.weight(1f)) {
                    Text(period, style = MaterialTheme.typography.titleMedium)
                }
                IconButton(onClick = {
                    if (weekView) weekAnchor = weekStart.plusWeeks(1) else month = month.plusMonths(1)
                }) { Icon(PaceAppIcons.Back, contentDescription = stringResource(if (weekView) R.string.pace_plan_next_week else R.string.pace_plan_next_month), modifier = Modifier.rotate(180f)) }
            }
            com.pace.app.ui.components.PaceSegmentedControl(
                options = listOf(
                    com.pace.app.ui.components.PaceSegment(false, stringResource(R.string.pace_plan_month_short)),
                    com.pace.app.ui.components.PaceSegment(true, stringResource(R.string.pace_plan_week_short)),
                ),
                selected = weekView,
                onSelected = { weekView = it },
            )
            if (monthPickerOpen) {
                var year by remember { mutableStateOf(if (weekView) weekStart.year else month.year) }
                AlertDialog(
                    onDismissRequest = { monthPickerOpen = false },
                    title = { Text(stringResource(R.string.pace_plan_choose_month)) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { year-- }) { Icon(PaceAppIcons.Back, stringResource(R.string.pace_plan_previous_year)) }
                                Text(year.toString(), Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                IconButton(onClick = { year++ }) { Icon(PaceAppIcons.Back, stringResource(R.string.pace_plan_next_year), Modifier.rotate(180f)) }
                            }
                            java.time.Month.entries.chunked(3).forEach { row ->
                                Row(Modifier.fillMaxWidth()) {
                                    row.forEach { option ->
                                        TextButton(onClick = {
                                            month = YearMonth.of(year, option)
                                            if (weekView) weekAnchor = month.atDay(1)
                                            monthPickerOpen = false
                                        }, modifier = Modifier.weight(1f)) {
                                            Text(option.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault()))
                                        }
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = { TextButton(onClick = { monthPickerOpen = false }) { Text(stringResource(R.string.pace_cancel)) } },
                )
            }
            if (weekView) {
                TextButton(onClick = {
                    scope.launch {
                        val toStart = weekStart.plusWeeks(1)
                        val nextWeek = container.workoutRepository.observeCalendar(toStart.toString(), toStart.plusDays(7).toString()).first()
                        val existing = nextWeek.filter { it.status == "PLANNED" }.map { "${it.templateId}:${it.scheduledDate}" }.toSet()
                        val planned = sessions.filter { it.status == "PLANNED" && it.templateId != null }
                        val copies = planned.filterNot { "${it.templateId}:${LocalDate.parse(it.scheduledDate).plusWeeks(1)}" in existing }
                        val now = System.currentTimeMillis()
                        copies.forEach { session ->
                            container.workoutRepository.savePlannedSession(session.copy(
                                id = UUID.randomUUID().toString(),
                                scheduledDate = LocalDate.parse(session.scheduledDate).plusWeeks(1).toString(),
                                status = "PLANNED", completedActivityId = null, reminderEnabled = false,
                                updatedAtEpochMillis = now, deletedAtEpochMillis = null,
                            ))
                        }
                        weekCopyCount = copies.size
                    }
                }) { Text(stringResource(R.string.pace_plan_copy_next_week)) }
                weekCopyCount?.let { Text(stringResource(R.string.pace_plan_copied_count, it), style = MaterialTheme.typography.bodySmall) }
            }
            com.pace.app.ui.components.PaceSecondaryButton(
                label = stringResource(R.string.pace_plan_start_workout_separate),
                onClick = { onStartWorkout(sport) },
                modifier = Modifier.fillMaxWidth(),
                icon = PaceAppIcons.Play,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PacePrimaryButton(
                    label = stringResource(R.string.pace_plan_add_session),
                    onClick = {
                        isCreatingTemplateOnly = false
                        editorOpen = true
                    },
                    modifier = Modifier.weight(1f),
                    icon = PaceAppIcons.Plan,
                )
                com.pace.app.ui.components.PaceSecondaryButton(
                    label = stringResource(R.string.pace_plan_create_workout),
                    onClick = {
                        isCreatingTemplateOnly = true
                        editingTemplateId = null
                        reschedulingSessionId = null
                        title = ""
                        intervals = emptyList()
                        editorOpen = true
                    },
                    modifier = Modifier.weight(1f),
                    icon = PaceAppIcons.Add,
                )
            }
        }
        val motion = !com.pace.app.ui.theme.LocalReducedMotion.current && android.animation.ValueAnimator.areAnimatorsEnabled()
        androidx.compose.animation.AnimatedVisibility(
            visible = editorOpen,
            enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(if (motion) 180 else 0)),
            exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(if (motion) 100 else 0)),
        ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { editorOpen = false }) { Icon(PaceAppIcons.Back, stringResource(R.string.pace_action_back)) }
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(if (isCreatingTemplateOnly) R.string.pace_plan_create_workout else R.string.pace_plan_editor_title),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(stringResource(R.string.pace_plan_editor_subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        OutlinedTextField(value = title, onValueChange = { title = it.take(60); error = null }, label = { Text(stringResource(R.string.pace_plan_title_label)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        PlanTargetField(
            label = R.string.pace_plan_duration_target,
            value = targetDurationText,
            onValueChange = { targetDurationText = it; error = null },
            units = listOf(60L to R.string.pace_plan_unit_minutes, 1L to R.string.pace_plan_unit_seconds, 3600L to R.string.pace_plan_unit_hours),
            selected = durationMultiplier,
            onUnit = { durationMultiplier = it },
        )
        PlanTargetField(
            label = R.string.pace_plan_distance_target,
            value = targetDistanceText,
            onValueChange = { targetDistanceText = it; error = null },
            units = listOf(1000 to R.string.pace_plan_unit_km, 1 to R.string.pace_plan_unit_m),
            selected = distanceMultiplier,
            onUnit = { distanceMultiplier = it },
        )
        if (!isCreatingTemplateOnly) {
            var datePickerOpen by remember { mutableStateOf(false) }
            val date = runCatching { LocalDate.parse(dateText) }.getOrDefault(today)
            val dateLabel = date.format(java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM))
            com.pace.app.ui.components.PaceSecondaryButton(
                label = dateLabel,
                onClick = { datePickerOpen = true },
                modifier = Modifier.fillMaxWidth(),
                icon = PaceAppIcons.Plan,
            )
            if (datePickerOpen) {
                val utc = java.time.ZoneOffset.UTC
                val picker = androidx.compose.material3.rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDay().toInstant(utc).toEpochMilli())
                androidx.compose.material3.DatePickerDialog(
                    onDismissRequest = { datePickerOpen = false },
                    confirmButton = {
                        TextButton(onClick = {
                            picker.selectedDateMillis?.let {
                                dateText = java.time.Instant.ofEpochMilli(it).atZone(utc).toLocalDate().toString()
                                error = null
                            }
                            datePickerOpen = false
                        }) { Text(stringResource(R.string.pace_history_apply_date)) }
                    },
                ) { androidx.compose.material3.DatePicker(picker) }
            }
        }
        val sports = listOf(
            Triple("Running", com.pace.app.ui.components.PaceSport.Running, R.string.sport_running),
            Triple("Cycling", com.pace.app.ui.components.PaceSport.Cycling, R.string.sport_cycling),
            Triple("Walking", com.pace.app.ui.components.PaceSport.Walking, R.string.sport_walking),
            Triple("Jogging", com.pace.app.ui.components.PaceSport.Jogging, R.string.sport_jogging),
        )
        sports.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { (value, activity, label) ->
                    com.pace.app.ui.components.PaceSportSelectorTile(
                        sport = activity, label = stringResource(label), selected = sport == value,
                        onClick = { sport = value }, modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        androidx.compose.foundation.layout.Spacer(Modifier.height(18.dp))
        androidx.compose.material3.HorizontalDivider(Modifier.padding(vertical = 4.dp))
        androidx.compose.foundation.layout.Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.pace_plan_starter_gallery), style = MaterialTheme.typography.titleMedium)
        starterTemplates.forEach { (nameResource, starterSport, starterIntervals) ->
            val starterName = stringResource(nameResource)
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text(starterName, style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(R.string.pace_plan_template_sport, starterSport), style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = {
                        title = starterName
                        sport = starterSport
                        intervals = starterIntervals
                        durationMultiplier = 1L
                        targetDurationText = starterIntervals.sumOf { it.durationSeconds ?: 0 }.toString()
                        targetDistanceText = ""
                        editingTemplateId = null
                        reschedulingSessionId = null
                        error = null
                    }) { Text(stringResource(R.string.pace_plan_use_starter)) }
                }
            }
        }
        androidx.compose.foundation.layout.Spacer(Modifier.height(20.dp))
        androidx.compose.material3.HorizontalDivider(Modifier.padding(vertical = 4.dp))
        androidx.compose.foundation.layout.Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.pace_plan_intervals_heading), style = MaterialTheme.typography.titleMedium)
        listOf("WARMUP", "WORK", "REST", "COOLDOWN").chunked(2).forEach { group ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                group.forEach { kind ->
                    com.pace.app.ui.components.PaceSecondaryButton(
                        label = stringResource(when (kind) {
                            "WARMUP" -> R.string.pace_plan_warmup
                            "REST" -> R.string.pace_plan_rest
                            "COOLDOWN" -> R.string.pace_plan_cooldown
                            else -> R.string.pace_plan_work
                        }),
                        onClick = {
                            val duration = if (kind == "REST") 60L else 300L
                            intervals = intervals + WorkoutIntervalEntity("", intervals.size, kind, durationSeconds = duration)
                        },
                        modifier = Modifier.weight(1f),
                        icon = PaceAppIcons.Add,
                    )
                }
            }
        }
        intervals.forEachIndexed { index, interval ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.pace_plan_interval_label, index + 1, interval.intervalType), style = MaterialTheme.typography.titleSmall)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = interval.durationSeconds?.toString().orEmpty(),
                        onValueChange = { value ->
                            val seconds = value.toLongOrNull()
                            intervals = intervals.toMutableList().also { list ->
                                list[index] = interval.copy(durationSeconds = seconds)
                            }
                        },
                        label = { Text(stringResource(R.string.pace_plan_seconds)) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = interval.repeatCount.takeIf { it > 0 }?.toString().orEmpty(),
                        onValueChange = { value ->
                            val repeats = value.toIntOrNull() ?: 0
                            intervals = intervals.toMutableList().also { list ->
                                list[index] = interval.copy(repeatCount = repeats)
                            }
                        },
                        label = { Text(stringResource(R.string.pace_plan_repeats)) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                    TextButton(onClick = { intervals = intervals.filterIndexed { row, _ -> row != index } }) {
                        Text(stringResource(R.string.pace_history_delete_action))
                    }
                }
                val metricOptions = listOf(null, "PACE_SECONDS_PER_KILOMETER", "SPEED_METERS_PER_SECOND", "DISTANCE_METERS")
                val metricIndex = metricOptions.indexOf(interval.targetMetric).coerceAtLeast(0)
                val targetUnit = when (interval.targetMetric) {
                    "PACE_SECONDS_PER_KILOMETER" -> "s/km"
                    "SPEED_METERS_PER_SECOND" -> "m/s"
                    "DISTANCE_METERS" -> "m"
                    else -> ""
                }
                TextButton(onClick = {
                    val nextMetric = metricOptions[(metricIndex + 1) % metricOptions.size]
                    intervals = intervals.toMutableList().also { list ->
                        list[index] = interval.copy(targetMetric = nextMetric, targetMin = null, targetMax = null)
                    }
                }) {
                    val targetLabel = when (interval.targetMetric) {
                        "PACE_SECONDS_PER_KILOMETER" -> R.string.pace_plan_target_pace
                        "SPEED_METERS_PER_SECOND" -> R.string.pace_plan_target_speed
                        "DISTANCE_METERS" -> R.string.pace_plan_target_distance
                        else -> R.string.pace_plan_target_none
                    }
                    Text(stringResource(R.string.pace_plan_target_label, stringResource(targetLabel)))
                }
                if (interval.targetMetric != null) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = interval.targetMin?.toString().orEmpty(),
                            onValueChange = { value ->
                                intervals = intervals.toMutableList().also { list ->
                                    list[index] = interval.copy(targetMin = value.toDoubleOrNull())
                                }
                            },
                            label = { Text(stringResource(R.string.pace_plan_target_min, targetUnit)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = interval.targetMax?.toString().orEmpty(),
                            onValueChange = { value ->
                                intervals = intervals.toMutableList().also { list ->
                                    list[index] = interval.copy(targetMax = value.toDoubleOrNull())
                                }
                            },
                            label = { Text(stringResource(R.string.pace_plan_target_max, targetUnit)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                        )
                    }
                }
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        undoReschedule?.let { (sessionId, previousDate, reminderEnabled) ->
            TextButton(onClick = {
                scope.launch {
                    val changed = container.workoutRepository.reschedulePlannedSession(sessionId, previousDate, System.currentTimeMillis())
                    if (changed && reminderEnabled) {
                        PlanReminderScheduler.schedule(
                            context,
                            PlannedSessionEntity(sessionId, scheduledDate = previousDate, timeZoneId = ZoneId.systemDefault().id, reminderEnabled = true, updatedAtEpochMillis = System.currentTimeMillis()),
                        )
                    }
                    undoReschedule = null
                }
            }) { Text(stringResource(R.string.pace_plan_undo_reschedule)) }
        }
        val targetValidationError = stringResource(R.string.pace_plan_target_validation_error)
        val intervalDurationError = stringResource(R.string.pace_plan_interval_duration_error)
        val workoutNameRequiredError = stringResource(R.string.pace_plan_workout_name_required)
        Button(onClick = {
            val parsedDate = runCatching { LocalDate.parse(dateText) }.getOrNull()
            val durationTarget = targetDurationText.toDoubleOrNull()?.let { kotlin.math.round(it * durationMultiplier).toLong() }
            val distanceTarget = targetDistanceText.toDoubleOrNull()?.times(distanceMultiplier)
            if (reschedulingSessionId == null && intervals.any {
                    (it.durationSeconds ?: 0L) <= 0L || it.repeatCount <= 0 ||
                        (it.targetMetric != null && (it.targetMin == null || it.targetMax == null || it.targetMin <= 0.0 || it.targetMax < it.targetMin))
                }) {
                error = intervalDurationError
            } else if (reschedulingSessionId == null && (
                    targetDurationText.isNotBlank() && (durationTarget == null || durationTarget <= 0L) ||
                        targetDistanceText.isNotBlank() && (distanceTarget == null || distanceTarget <= 0.0)
                    )) {
                error = targetValidationError
            } else if (reschedulingSessionId == null && title.isBlank()) {
                error = workoutNameRequiredError
            } else if (!isCreatingTemplateOnly && parsedDate == null) {
                error = "Enter a workout date in YYYY-MM-DD format."
            } else {
                scope.launch {
                    val now = System.currentTimeMillis()
                    val rescheduling = sessions.firstOrNull { it.id == reschedulingSessionId }
                    if (reschedulingSessionId != null && rescheduling == null) {
                        error = "This planned session is no longer available."
                        return@launch
                    }
                    if (rescheduling != null) {
                        val changed = container.workoutRepository.reschedulePlannedSession(
                            rescheduling.id, parsedDate.toString(), now,
                        )
                        if (changed) undoReschedule = Triple(rescheduling.id, rescheduling.scheduledDate, rescheduling.reminderEnabled)
                        if (changed && rescheduling.reminderEnabled) {
                            PlanReminderScheduler.schedule(context, rescheduling.copy(scheduledDate = parsedDate.toString()))
                        } else if (changed) {
                            PlanReminderScheduler.cancel(context, rescheduling.id)
                        }
                        reschedulingSessionId = null
                        error = null
                        return@launch
                    }
                    val existing = templates.firstOrNull { it.template.id == editingTemplateId }
                    val templateId = editingTemplateId ?: UUID.randomUUID().toString()
                    val template = WorkoutTemplateEntity(
                        id = templateId,
                        name = title.trim(),
                        sport = sport,
                        description = existing?.template?.description,
                        targetDurationSeconds = durationTarget,
                        targetDistanceMeters = distanceTarget,
                        createdAtEpochMillis = existing?.template?.createdAtEpochMillis ?: now,
                        updatedAtEpochMillis = now,
                    )
                    val savedIntervals = intervals.mapIndexed { index, interval ->
                        interval.copy(templateId = templateId, position = index)
                    }
                    container.workoutRepository.saveTemplate(template, savedIntervals)
                    if (!isCreatingTemplateOnly && editingTemplateId == null && parsedDate != null) {
                        container.workoutRepository.savePlannedSession(PlannedSessionEntity(UUID.randomUUID().toString(), templateId, parsedDate.toString(), ZoneId.systemDefault().id, updatedAtEpochMillis = now))
                    }
                    title = ""
                    targetDurationText = ""
                    targetDistanceText = ""
                    editingTemplateId = null
                    intervals = emptyList()
                    error = null
                    feedback(R.string.pace_plan_saved_feedback)
                }
            }
        }, modifier = Modifier.fillMaxWidth()) {
            val action = when {
                reschedulingSessionId != null -> R.string.pace_plan_save_reschedule
                editingTemplateId != null -> R.string.pace_plan_save_template
                isCreatingTemplateOnly -> R.string.pace_plan_create_workout
                else -> R.string.pace_plan_add_session
            }
            Text(stringResource(action))
        }
        if (reschedulingSessionId == null) {
            androidx.compose.material3.OutlinedButton(onClick = {
                val durationTarget = targetDurationText.toDoubleOrNull()?.let { kotlin.math.round(it * durationMultiplier).toLong() }
                val distanceTarget = targetDistanceText.toDoubleOrNull()?.times(distanceMultiplier)
                if (title.isBlank()) {
                    error = workoutNameRequiredError
                } else if (intervals.any {
                        (it.durationSeconds ?: 0L) <= 0L || it.repeatCount <= 0 ||
                            (it.targetMetric != null && (it.targetMin == null || it.targetMax == null || it.targetMin <= 0.0 || it.targetMax < it.targetMin))
                    }) {
                    error = intervalDurationError
                } else if (
                    targetDurationText.isNotBlank() && (durationTarget == null || durationTarget <= 0L) ||
                        targetDistanceText.isNotBlank() && (distanceTarget == null || distanceTarget <= 0.0)
                ) {
                    error = targetValidationError
                } else scope.launch {
                    val now = System.currentTimeMillis()
                    val existing = templates.firstOrNull { it.template.id == editingTemplateId }
                    val templateId = editingTemplateId ?: UUID.randomUUID().toString()
                    runCatching {
                        container.workoutRepository.saveTemplate(
                            WorkoutTemplateEntity(
                                id = templateId,
                                name = title.trim(),
                                sport = sport,
                                description = existing?.template?.description,
                                targetDurationSeconds = durationTarget,
                                targetDistanceMeters = distanceTarget,
                                createdAtEpochMillis = existing?.template?.createdAtEpochMillis ?: now,
                                updatedAtEpochMillis = now,
                            ),
                            intervals.mapIndexed { index, interval -> interval.copy(templateId = templateId, position = index) },
                        )
                    }.onSuccess {
                        title = ""
                        targetDurationText = ""
                        targetDistanceText = ""
                        editingTemplateId = null
                        intervals = emptyList()
                        error = null
                        feedback(R.string.pace_plan_saved_feedback)
                    }.onFailure { error = it.message }
                }
            }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.pace_plan_save_workout_only))
            }
        }
        }
        }
        if (!editorOpen) {
            if (templates.isNotEmpty()) {
                Text(stringResource(R.string.pace_plan_templates_count, templates.size), style = MaterialTheme.typography.labelMedium)
                templates.forEach { saved ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Column {
                                Text(saved.template.name, style = MaterialTheme.typography.titleSmall)
                                Text(stringResource(R.string.pace_plan_template_sport, saved.template.sport), style = MaterialTheme.typography.bodySmall)
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                androidx.compose.material3.FilledTonalButton(
                                    onClick = { scheduleTemplateId = saved.template.id },
                                    modifier = Modifier.weight(1.4f),
                                ) { Text(stringResource(R.string.pace_plan_schedule_template)) }
                                androidx.compose.material3.OutlinedButton(onClick = {
                                    editorOpen = true
                                    editingTemplateId = saved.template.id
                                    title = saved.template.name
                                    durationMultiplier = 1L
                                    distanceMultiplier = 1
                                    targetDurationText = saved.template.targetDurationSeconds?.toString().orEmpty()
                                    targetDistanceText = saved.template.targetDistanceMeters?.toString().orEmpty()
                                    sport = saved.template.sport
                                    intervals = saved.intervals
                                    error = null
                                }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.pace_plan_edit_template)) }
                                IconButton(onClick = { deletingTemplateId = saved.template.id }) {
                                    Icon(PaceAppIcons.Delete, stringResource(R.string.pace_history_delete_action), tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                sessions.forEach { session ->
                    val template = templates.firstOrNull { it.template.id == session.templateId }?.template
                    Card(Modifier.fillMaxWidth().pointerInput(session.id) {
                        var horizontalDrag = 0f
                        detectDragGesturesAfterLongPress(
                            onDrag = { change, amount -> horizontalDrag += amount.x; change.consume() },
                            onDragEnd = {
                                val dayDelta = when {
                                    horizontalDrag > 100f -> 1L
                                    horizontalDrag < -100f -> -1L
                                    else -> 0L
                                }
                                horizontalDrag = 0f
                                if (dayDelta != 0L && session.status == "PLANNED") scope.launch {
                                    val targetDate = LocalDate.parse(session.scheduledDate).plusDays(dayDelta).toString()
                                    val changed = container.workoutRepository.reschedulePlannedSession(session.id, targetDate, System.currentTimeMillis())
                                    if (changed) {
                                        undoReschedule = Triple(session.id, session.scheduledDate, session.reminderEnabled)
                                        if (session.reminderEnabled) PlanReminderScheduler.schedule(context, session.copy(scheduledDate = targetDate))
                                    }
                                }
                            },
                            onDragCancel = { horizontalDrag = 0f },
                        )
                    }) {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column {
                                Text(template?.name ?: stringResource(R.string.pace_plan_session_missing), style = MaterialTheme.typography.titleMedium)
                                Text("${session.scheduledDate} · ${template?.sport.orEmpty()} · ${session.status}", style = MaterialTheme.typography.bodyMedium)
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (session.status == "PLANNED") androidx.compose.material3.FilledTonalButton(onClick = { onStartWorkout(template?.sport ?: "Running") }) {
                                    Text(stringResource(R.string.pace_plan_start))
                                }
                                androidx.compose.material3.OutlinedButton(onClick = { detailSessionId = session.id }) {
                                    Text(stringResource(R.string.pace_plan_view_details))
                                }
                            }
                            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (session.status == "PLANNED") Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                    Text(stringResource(R.string.pace_plan_reminder_switch))
                                    Switch(
                                        checked = session.reminderEnabled,
                                        onCheckedChange = { enabled ->
                                            if (enabled && Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                                                pendingReminderSession = session
                                                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                            } else {
                                                scope.launch {
                                                    val updated = session.copy(reminderEnabled = enabled, updatedAtEpochMillis = System.currentTimeMillis())
                                                    container.workoutRepository.savePlannedSession(updated)
                                                    if (enabled) PlanReminderScheduler.schedule(context, updated)
                                                    else PlanReminderScheduler.cancel(context, session.id)
                                                    feedback(if (enabled) R.string.pace_plan_reminder_on_feedback else R.string.pace_plan_reminder_off_feedback)
                                                }
                                            }
                                        },
                                    )
                                }
                            }
                            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (session.status == "PLANNED" || session.status == "SKIPPED" || session.status == "MISSED") {
                                    val isPast = runCatching { LocalDate.parse(session.scheduledDate).isBefore(today) }.getOrDefault(false)
                                    androidx.compose.material3.OutlinedButton(onClick = {
                                        val nextStatus = when {
                                            session.status == "SKIPPED" || session.status == "MISSED" -> "PLANNED"
                                            isPast -> "MISSED"
                                            else -> "SKIPPED"
                                        }
                                        scope.launch {
                                            container.workoutRepository.savePlannedSession(
                                                session.copy(status = nextStatus, reminderEnabled = if (nextStatus == "PLANNED") session.reminderEnabled else false, updatedAtEpochMillis = System.currentTimeMillis()),
                                            )
                                            if (nextStatus == "PLANNED" && session.reminderEnabled) PlanReminderScheduler.schedule(context, session)
                                            else PlanReminderScheduler.cancel(context, session.id)
                                        }
                                    }) {
                                        val action = when {
                                            session.status == "SKIPPED" || session.status == "MISSED" -> R.string.pace_plan_unskip
                                            isPast -> R.string.pace_plan_mark_missed
                                            else -> R.string.pace_plan_skip
                                        }
                                        Text(stringResource(action))
                                    }
                                }
                                androidx.compose.material3.OutlinedButton(onClick = {
                                    editorOpen = true
                                    reschedulingSessionId = session.id
                                    editingTemplateId = null
                                    dateText = session.scheduledDate
                                }) { Text(stringResource(R.string.pace_plan_reschedule)) }
                                androidx.compose.material3.OutlinedButton(onClick = { deletingSessionId = session.id }) {
                                    Text(stringResource(R.string.pace_history_delete_action), color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
                if (sessions.isEmpty()) Text(stringResource(R.string.pace_plan_empty), style = MaterialTheme.typography.bodyMedium)
            }
            if (weekView) {
                val plannedCount = sessions.count { it.status == "PLANNED" }
                val skippedCount = sessions.count { it.status == "SKIPPED" }
                val completedCount = sessions.count { it.status == "COMPLETED" }
                Text(
                    stringResource(R.string.pace_plan_week_summary, plannedCount, completedCount, skippedCount),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
    val detailSession = sessions.firstOrNull { it.id == detailSessionId }
    if (detailSession != null) {
        val detailTemplate = templates.firstOrNull { it.template.id == detailSession.templateId }
        AlertDialog(
            onDismissRequest = { detailSessionId = null },
            title = { Text(detailTemplate?.template?.name ?: stringResource(R.string.pace_plan_session_missing)) },
            icon = { Icon(PaceAppIcons.Plan, null, tint = MaterialTheme.colorScheme.primary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val date = LocalDate.parse(detailSession.scheduledDate)
                    Text(date.format(java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM)), style = MaterialTheme.typography.titleMedium)
                    detailTemplate?.template?.sport?.let { Text(it, style = MaterialTheme.typography.labelLarge) }
                    detailTemplate?.template?.targetDurationSeconds?.let { seconds ->
                        Text(stringResource(R.string.pace_plan_duration_detail, seconds / 60, seconds % 60))
                    }
                    detailTemplate?.template?.targetDistanceMeters?.let { meters ->
                        Text(stringResource(R.string.pace_plan_distance_detail, meters / 1_000.0))
                    }
                    Text(stringResource(if (detailSession.reminderEnabled) R.string.pace_plan_reminder_on_feedback else R.string.pace_plan_reminder_off_feedback), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                if (detailSession.status == "PLANNED") TextButton(onClick = {
                    detailSessionId = null
                    onStartWorkout(detailTemplate?.template?.sport ?: "Running")
                }) { Text(stringResource(R.string.pace_plan_start)) }
            },
            dismissButton = { TextButton(onClick = { detailSessionId = null }) { Text(stringResource(R.string.pace_action_back)) } },
        )
    }
    scheduleTemplateId?.let { templateId ->
        val utc = java.time.ZoneOffset.UTC
        val initialDay = runCatching { LocalDate.parse(dateText) }.getOrDefault(today)
        val picker = androidx.compose.material3.rememberDatePickerState(initialSelectedDateMillis = initialDay.atStartOfDay().toInstant(utc).toEpochMilli())
        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = { scheduleTemplateId = null },
            confirmButton = {
                TextButton(enabled = picker.selectedDateMillis != null, onClick = {
                    val millis = picker.selectedDateMillis ?: return@TextButton
                    val day = java.time.Instant.ofEpochMilli(millis).atZone(utc).toLocalDate()
                    scheduleTemplateId = null
                    scope.launch {
                        runCatching {
                            container.workoutRepository.savePlannedSession(
                                PlannedSessionEntity(
                                    UUID.randomUUID().toString(), templateId,
                                    day.toString(), ZoneId.systemDefault().id,
                                    updatedAtEpochMillis = System.currentTimeMillis(),
                                ),
                            )
                        }.onSuccess { feedback(R.string.pace_plan_saved_feedback) }
                            .onFailure { feedback(R.string.pace_plan_action_failed) }
                    }
                }) { Text(stringResource(R.string.pace_plan_schedule_template)) }
            },
            dismissButton = { TextButton(onClick = { scheduleTemplateId = null }) { Text(stringResource(R.string.pace_cancel)) } },
        ) { androidx.compose.material3.DatePicker(picker) }
    }
    deletingSessionId?.let { id ->
        AlertDialog(
            onDismissRequest = { deletingSessionId = null },
            title = { Text(stringResource(R.string.pace_plan_delete_session_title)) },
            text = { Text(stringResource(R.string.pace_plan_delete_session_body)) },
            confirmButton = {
                TextButton(onClick = {
                    deletingSessionId = null
                    scope.launch {
                        container.workoutRepository.deletePlannedSession(id, System.currentTimeMillis())
                        PlanReminderScheduler.cancel(context, id)
                        feedback(R.string.pace_plan_deleted_feedback)
                    }
                }) { Text(stringResource(R.string.pace_history_delete_action), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deletingSessionId = null }) { Text(stringResource(R.string.pace_cancel)) } },
        )
    }
    val deletingTemplate = templates.firstOrNull { it.template.id == deletingTemplateId }
    if (deletingTemplate != null) AlertDialog(
        onDismissRequest = { deletingTemplateId = null },
        title = { Text(stringResource(R.string.pace_plan_delete_template_title)) },
        text = { Text(stringResource(R.string.pace_plan_delete_template_body)) },
        confirmButton = { TextButton(onClick = {
            val id = deletingTemplate.template.id
            deletingTemplateId = null
            scope.launch { container.workoutRepository.deleteTemplate(id, System.currentTimeMillis()); feedback(R.string.pace_plan_deleted_feedback) }
        }) { Text(stringResource(R.string.pace_history_delete_action)) } },
        dismissButton = { TextButton(onClick = { deletingTemplateId = null }) { Text(stringResource(R.string.pace_action_back)) } },
    )
}


@Composable
private fun <T> PlanTargetField(
    label: Int,
    value: String,
    onValueChange: (String) -> Unit,
    units: List<Pair<T, Int>>,
    selected: T,
    onUnit: (T) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = value,
            onValueChange = { onValueChange(it.filter { char -> char.isDigit() || char == '.' || char == ',' }.take(9).replace(',', '.')) },
            label = { Text(stringResource(label)) },
            modifier = Modifier.weight(1f),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        androidx.compose.foundation.layout.Box {
            androidx.compose.material3.OutlinedButton(onClick = { open = true }) {
                Text(stringResource(units.first { it.first == selected }.second))
                Icon(PaceAppIcons.Chevron, null)
            }
            androidx.compose.material3.DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                units.forEach { (unit, title) ->
                    androidx.compose.material3.DropdownMenuItem(text = { Text(stringResource(title)) }, onClick = { onUnit(unit); open = false })
                }
            }
        }
    }
}
