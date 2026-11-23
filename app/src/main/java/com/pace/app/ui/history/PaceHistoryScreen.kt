package com.pace.app.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import android.content.ClipData
import com.pace.app.PacePreferences
import com.pace.app.PaceAppIcons
import com.pace.app.R
import com.pace.app.domain.repository.ActivityRepository
import com.pace.app.ui.components.PaceTextAction
import com.pace.app.ui.maps.PaceMapPanel
import com.pace.app.domain.workouts.HomeWorkoutActivityCodec
import com.pace.app.domain.workouts.HomeWorkoutActivityDetails
import com.pace.app.ui.workouts.homeWorkoutExerciseNameResource
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import com.pace.app.domain.workouts.HomeWorkoutExerciseResult

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PaceHistoryScreen(repository: ActivityRepository, preferences: PacePreferences, onBack: () -> Unit, onRecord: () -> Unit, modifier: Modifier = Modifier) {
    var activities by remember { mutableStateOf(emptyList<com.pace.app.data.local.entity.ActivityEntity>()) }
    var selectedId by remember { mutableStateOf<String?>(null) }; var query by remember { mutableStateOf("") }
    androidx.activity.compose.BackHandler(enabled = selectedId != null) { selectedId = null }
    var selectedSport by remember { mutableStateOf("ALL") }; var deleting by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<String?>(null) }; val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }; var loadError by remember { mutableStateOf(false) }
    var hasMore by remember { mutableStateOf(true) }
    var pageJob by remember { mutableStateOf<Job?>(null) }; var requestId by remember { mutableIntStateOf(0) }
    var sort by remember { mutableStateOf("NEWEST") }
    var startDate by remember { mutableStateOf<Long?>(null) }; var endDate by remember { mutableStateOf<Long?>(null) }
    var pickStart by remember { mutableStateOf(false) }; var pickEnd by remember { mutableStateOf(false) }
    var detailReload by remember { mutableIntStateOf(0) }
    var filtersOpen by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val exportLabel = stringResource(R.string.pace_history_export_action)
    val exportFailed = stringResource(R.string.pace_history_export_failed)
    val loadPage: (Boolean) -> Unit = { reset ->
        requestId += 1
        val request = requestId
        pageJob?.cancel()
        pageJob = scope.launch {
            loading = true; loadError = false
            try {
                val cursor = if (reset) emptyList() else activities
                val from = startDate?.let(::localDateBoundary)
                val until = endDate?.let { localDateBoundary(it + 86_400_000) }
                if (from != null && until != null && from >= until) { loadError = true; hasMore = false; loading = false; return@launch }
                val next = repository.page(sport = selectedSport.takeUnless { it == "ALL" }, query = query, limit = 100, sortBy = sort, offset = cursor.size, fromStartedAt = from, toStartedAt = until)
                if (request == requestId) {
                    activities = if (reset) next else (activities + next).distinctBy { it.id }
                    hasMore = next.size == 100
                }
            } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { if (request == requestId) loadError = true } finally { if (request == requestId) loading = false }
        }
    }
    LaunchedEffect(query, selectedSport, sort, startDate, endDate) { loadPage(true) }
    val invalidDateRange = startDate != null && endDate != null && localDateBoundary(startDate!!) >= localDateBoundary(endDate!! + 86_400_000)
    if (pickStart || pickEnd) {
        val picker = rememberDatePickerState(initialSelectedDateMillis = if (pickStart) startDate else endDate)
        DatePickerDialog(onDismissRequest = { pickStart = false; pickEnd = false }, confirmButton = {
            TextButton(onClick = { if (pickStart) startDate = picker.selectedDateMillis else endDate = picker.selectedDateMillis; pickStart = false; pickEnd = false }) { Text(stringResource(R.string.pace_history_apply_date)) }
        }, dismissButton = { TextButton(onClick = { pickStart = false; pickEnd = false }) { Text(stringResource(R.string.pace_cancel)) } }) { DatePicker(state = picker) }
    }
    val selected = activities.firstOrNull { it.id == selectedId }
    deleting?.let { id ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.pace_history_delete_title)) },
            text = { Text(stringResource(R.string.pace_history_delete_body)) },
            confirmButton = { TextButton(onClick = { deleting = null; scope.launch { if (repository.deleteActivity(id, System.currentTimeMillis())) activities = activities.filterNot { it.id == id }; selectedId = null } }) { Text(stringResource(R.string.pace_history_delete_action)) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.pace_cancel)) } },
        )
    }
    editing?.let { id ->
        val item = activities.firstOrNull { it.id == id }
        if (item != null) {
            val workoutDetails = remember(id, item.notes) { HomeWorkoutActivityCodec.decode(item.notes) }
            if (workoutDetails != null) {
                HomeWorkoutActivityEditDialog(
                    initialTitle = item.title.orEmpty(),
                    initialDetails = workoutDetails,
                    onDismiss = { editing = null },
                    onSave = { title, updatedDetails ->
                        val cleanTitle = title.trim().ifBlank { null }
                        val encoded = runCatching { HomeWorkoutActivityCodec.encode(updatedDetails) }.getOrNull()
                        if (encoded == null) {
                            editing = null
                        } else {
                            editing = null
                            scope.launch {
                                if (repository.updateMetadata(id, cleanTitle, encoded, System.currentTimeMillis())) {
                                    activities = activities.map { if (it.id == id) it.copy(title = cleanTitle, notes = encoded) else it }
                                    detailReload++
                                }
                            }
                        }
                    },
                )
            } else {
                var title by remember(id) { mutableStateOf(item.title.orEmpty()) }
                var notes by remember(id) { mutableStateOf(item.notes.orEmpty()) }
                AlertDialog(
                    onDismissRequest = { editing = null }, title = { Text(stringResource(R.string.pace_history_edit_title)) },
                    text = { Column { OutlinedTextField(title, { title = it.take(80) }, label = { Text(stringResource(R.string.pace_history_title_label)) }); OutlinedTextField(notes, { notes = it.take(1000) }, label = { Text(stringResource(R.string.pace_history_notes_label)) }) } },
                    confirmButton = { TextButton(onClick = { editing = null; val cleanTitle = title.trim().ifBlank { null }; val cleanNotes = notes.trim().ifBlank { null }; scope.launch { if (repository.updateMetadata(id, cleanTitle, cleanNotes, System.currentTimeMillis())) activities = activities.map { if (it.id == id) it.copy(title = cleanTitle, notes = cleanNotes) else it } } }) { Text(stringResource(R.string.pace_history_save_action)) } },
                    dismissButton = { TextButton(onClick = { editing = null }) { Text(stringResource(R.string.pace_cancel)) } },
                )
            }
        }
    }
    Column(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            IconButton(onClick = { if (selected == null) onBack() else selectedId = null }) {
                Icon(PaceAppIcons.Back, contentDescription = stringResource(R.string.pace_action_back))
            }
            Text(
                stringResource(if (selected == null) R.string.pace_history_title else R.string.pace_history_detail),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f),
            )
            if (selected == null) {
                FilledTonalIconToggleButton(checked = filtersOpen, onCheckedChange = { filtersOpen = it }) {
                    Icon(PaceAppIcons.Filters, contentDescription = stringResource(R.string.pace_history_filters))
                }
            }
        }
        if (selected == null) {
            if (filtersOpen) {
                OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.pace_history_search)) })
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("ALL", "CYCLING", "RUNNING", "JOGGING", "WALKING").forEach { value -> FilterChip(selectedSport == value, { selectedSport = value }, label = { Text(stringResource(when (value) { "ALL" -> R.string.pace_history_all_sports; "CYCLING" -> R.string.sport_cycling; "RUNNING" -> R.string.sport_running; "JOGGING" -> R.string.sport_jogging; else -> R.string.sport_walking })) }) } }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("NEWEST", "OLDEST", "DISTANCE", "DURATION").forEach { value -> FilterChip(sort == value, { sort = value }, label = { Text(stringResource(when (value) { "OLDEST" -> R.string.pace_history_sort_oldest; "DISTANCE" -> R.string.pace_history_sort_distance; "DURATION" -> R.string.pace_history_sort_duration; else -> R.string.pace_history_sort_newest })) }) } }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { TextButton(onClick = { pickStart = true }) { Text(stringResource(R.string.pace_history_from_date)) }; TextButton(onClick = { pickEnd = true }) { Text(stringResource(R.string.pace_history_to_date)) }; TextButton(onClick = { startDate = null; endDate = null }) { Text(stringResource(R.string.pace_history_clear_dates)) } }
            }
            if (!filtersOpen && (query.isNotBlank() || selectedSport != "ALL" || startDate != null || endDate != null || sort != "NEWEST")) {
                AssistChip(onClick = { filtersOpen = true }, label = { Text(stringResource(R.string.pace_history_filters_active)) })
            }
            val rows = activities
            if (rows.isEmpty() && !loading && !loadError) { Text(stringResource(R.string.pace_history_empty)); PaceTextAction(stringResource(R.string.pace_home_record_action), onClick = onRecord) }
            else if (rows.isEmpty() && loading) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { repeat(4) { Card(Modifier.fillMaxWidth().height(76.dp)) { Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f)) {} } } }
            else LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                rows.groupBy { java.text.SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date(it.startedAtEpochMillis)) }.forEach { (month, group) ->
                    stickyHeader(key = "month:$month") { Surface(color = MaterialTheme.colorScheme.background) { Text(month, Modifier.fillMaxWidth().padding(vertical = 8.dp), style = MaterialTheme.typography.titleSmall) } }
                    items(group, key = { it.id }) { row ->
                        Card(Modifier.fillMaxWidth().pointerInput(row.id) {
                            var horizontal = 0f
                            detectHorizontalDragGestures(
                                onHorizontalDrag = { change, amount -> horizontal += amount; change.consume() },
                                onDragEnd = { if (horizontal < -96f) deleting = row.id; horizontal = 0f },
                                onDragCancel = { horizontal = 0f },
                            )
                        }.clickable { selectedId = row.id }) {
                            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                RouteThumbnail(repository, row.id, Modifier.width(72.dp).height(52.dp))
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(row.title?.ifBlank { null } ?: row.sport, style = MaterialTheme.typography.titleMedium)
                                    Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(row.startedAtEpochMillis)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        "${formatDistance(row.distanceMeters.toDouble(), preferences.units == com.pace.app.PaceUnits.Imperial)} · ${formatActivityTime(row.elapsedSeconds)}",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    if (activities.any { other -> other.id != row.id && kotlin.math.abs(other.startedAtEpochMillis - row.startedAtEpochMillis) < 120_000 && kotlin.math.abs(other.distanceMeters - row.distanceMeters) < 100 }) Text(stringResource(R.string.pace_history_possible_duplicate), color = MaterialTheme.colorScheme.tertiary)
                                }
                            }
                        }
                    }
                }
            }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (invalidDateRange) Text(stringResource(R.string.pace_history_invalid_dates))
            else if (loadError) { Text(stringResource(R.string.pace_history_load_error)); TextButton(onClick = { loadPage(activities.isEmpty()) }) { Text(stringResource(R.string.pace_history_retry)) } }
            if (hasMore && activities.isNotEmpty()) TextButton(onClick = { loadPage(false) }, enabled = !loading) { Text(stringResource(if (loading) R.string.pace_history_loading else R.string.pace_history_load_more)) }
        } else {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val detailResult by produceState<Result<com.pace.app.data.local.dao.ActivityWithTrack?>?>(null, selected.id, detailReload) { value = runCatching { repository.getActivity(selected.id) } }
            val detail = detailResult?.getOrNull()
            if (detailResult?.isFailure == true) {
                Text(stringResource(R.string.pace_history_load_error))
                TextButton(onClick = { detailReload++ }) { Text(stringResource(R.string.pace_history_retry)) }
            } else if (detail?.trackPoints.isNullOrEmpty()) {
                if (detailResult != null) Text(stringResource(R.string.pace_history_no_route))
            } else {
                PaceMapPanel(false, false, routePoints = detail!!.trackPoints, fitRoute = true, modifier = Modifier.fillMaxWidth().height(240.dp))
            }
            Text(selected.title?.ifBlank { null } ?: selected.sport, style = MaterialTheme.typography.titleLarge)
            Text(DateFormat.getDateTimeInstance(DateFormat.FULL, DateFormat.SHORT).format(Date(selected.startedAtEpochMillis)))
            val workoutDetails = remember(selected.id, selected.notes) { HomeWorkoutActivityCodec.decode(selected.notes) }
            if (workoutDetails != null) {
                Text(stringResource(R.string.pace_workout_activity_rounds, workoutDetails.rounds, workoutDetails.exercises.size), style = MaterialTheme.typography.bodyMedium)
                workoutDetails.exercises.forEach { exercise ->
                    val name = homeWorkoutExerciseNameResource(exercise.exerciseId)
                    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Text(name?.let { stringResource(it) } ?: HomeWorkoutActivityCodec.customExerciseName(exercise.exerciseId) ?: exercise.exerciseId, style = MaterialTheme.typography.titleSmall)
                        if (exercise.plannedRepetitions != null) Text(stringResource(R.string.pace_workout_activity_completed_reps, exercise.completedRepetitions))
                        if (exercise.durationSeconds != null) Text(stringResource(R.string.pace_workout_activity_completed_duration, exercise.completedDurationSeconds))
                        exercise.notes.takeIf(String::isNotBlank)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                }
                workoutDetails.notes.takeIf(String::isNotBlank)?.let { Text(it) }
            } else selected.notes?.takeIf { it.isNotBlank() }?.let { Text(it) }
            val calculated = detail?.let { com.pace.app.domain.metrics.ActivityMetrics.track(it.trackPoints) }
            val detailDistance = if (calculated != null && calculated.distanceMeters > 0) calculated.distanceMeters else selected.distanceMeters.toDouble()
            val detailMoving = selected.movingSeconds
            val imperial = preferences.units == com.pace.app.PaceUnits.Imperial
            val paceSeconds = if (imperial) com.pace.app.domain.metrics.ActivityMetrics.paceSecondsPerMile(detailDistance, detailMoving) else com.pace.app.domain.metrics.ActivityMetrics.paceSecondsPerKilometer(detailDistance, detailMoving)
            val paceSuffix = if (imperial) "/mi" else "/km"
            val paceLabel = paceSeconds?.takeIf { selected.sport.uppercase() in setOf("RUNNING", "JOGGING", "WALKING") }?.let { "%d:%02d %s".format((it / 60).toInt(), it.toInt() % 60, paceSuffix) } ?: "—"
            val averageSpeed = if (detailMoving > 0) "%.1f %s".format(if (imperial) detailDistance * 2.2369362921 / detailMoving else detailDistance * 3.6 / detailMoving, if (imperial) "mph" else "km/h") else "—"
            val maxSpeed = calculated?.maximumSpeedMetersPerSecond?.takeIf { it > 0 }?.let { "%.1f %s".format(if (imperial) it * 2.2369362921 else it * 3.6, if (imperial) "mph" else "km/h") } ?: "—"
            val ascent = calculated?.ascentMeters?.takeIf { it > 0 }?.let { "%.0f m".format(it) } ?: selected.elevationGainMeters?.let { "%.0f m".format(it) } ?: "—"
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    DetailMetric(stringResource(R.string.pace_history_distance_label), formatDistance(detailDistance, imperial), Modifier.weight(1f))
                    DetailMetric(stringResource(R.string.pace_history_elapsed_label), formatActivityTime(selected.elapsedSeconds), Modifier.weight(1f))
                    DetailMetric(stringResource(R.string.pace_history_moving_label), formatActivityTime(detailMoving), Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth()) {
                    DetailMetric(stringResource(R.string.pace_history_speed_label), averageSpeed, Modifier.weight(1f))
                    DetailMetric(stringResource(R.string.pace_history_pace_label), paceLabel, Modifier.weight(1f))
                    DetailMetric(stringResource(R.string.pace_history_max_speed_label), maxSpeed, Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth()) { DetailMetric(stringResource(R.string.pace_history_ascent_label), ascent, Modifier.weight(1f)) }
            }
            val qualityPoints = detail?.trackPoints.orEmpty()
            val qualityMessage = when {
                selected.dataQualityFlags.isNotBlank() -> stringResource(R.string.pace_history_quality_flags, selected.dataQualityFlags)
                qualityPoints.isEmpty() -> stringResource(R.string.pace_history_quality_no_gps)
                qualityPoints.any { it.horizontalAccuracyMeters == null || it.horizontalAccuracyMeters > 25f } -> stringResource(R.string.pace_history_quality_low)
                else -> stringResource(R.string.pace_history_quality_good)
            }
            Text(qualityMessage, color = MaterialTheme.colorScheme.onSurfaceVariant)
            detail?.trackPoints?.takeIf { it.size >= 2 }?.let { points ->
                MetricLineChart(stringResource(R.string.pace_history_speed_chart), points.mapNotNull { it.speedMetersPerSecond?.toDouble() })
                val pace = points.zipWithNext().mapNotNull { (a, b) ->
                    if (a.segmentIndex != b.segmentIndex || b.timestampEpochMillis <= a.timestampEpochMillis) null else {
                        val distance = com.pace.app.tracking.haversineMeters(a.latitude, a.longitude, b.latitude, b.longitude)
                        ((b.timestampEpochMillis - a.timestampEpochMillis) / 1000.0 * 1000 / distance) * if (imperial) com.pace.app.domain.metrics.ActivityMetrics.METERS_PER_MILE / 1000 else 1.0
                    }
                }.filter { it.isFinite() && it > 0 }
                MetricLineChart(stringResource(R.string.pace_history_pace_chart), pace)
                MetricLineChart(stringResource(R.string.pace_history_elevation_chart), points.mapNotNull { it.altitudeMeters })
            }
            Text(stringResource(R.string.pace_history_splits), style = MaterialTheme.typography.titleMedium)
            detail?.splits?.sortedBy { it.splitNumber }?.forEach { split ->
                val splitDistance = if (imperial) split.distanceMeters / com.pace.app.domain.metrics.ActivityMetrics.METERS_PER_MILE else split.distanceMeters / 1000.0
                val splitUnit = if (imperial) "mi" else "km"
                val splitPace = (split.averagePaceSecondsPerKm ?: 0.0) * if (imperial) com.pace.app.domain.metrics.ActivityMetrics.METERS_PER_MILE / 1000 else 1.0
                Text("${split.splitNumber} · ${"%.2f".format(splitDistance)} $splitUnit · ${split.elapsedSeconds / 60}:${(split.elapsedSeconds % 60).toString().padStart(2, '0')} · ${"%.0f".format(splitPace)} sec/$splitUnit")
            }
            Row { PaceTextAction(stringResource(R.string.pace_history_export_action), enabled = detail != null && detailResult?.isFailure != true, onClick = {
                val title = (selected.title ?: selected.sport).replace("&", "&amp;").replace("<", "&lt;").replace("\"", "&quot;")
                val points = detail?.trackPoints.orEmpty().joinToString("") { "<trkpt lat=\"${it.latitude}\" lon=\"${it.longitude}\"><time>${Instant.ofEpochMilli(it.timestampEpochMillis)}</time></trkpt>" }
                val gpx = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><gpx version=\"1.1\" creator=\"Pace\" xmlns=\"http://www.topografix.com/GPX/1/1\"><trk><name>$title</name><trkseg>$points</trkseg></trk></gpx>"
                scope.launch(Dispatchers.IO) {
                    try {
                        val directory = File(context.cacheDir, "activity-exports").apply { mkdirs() }
                        val safeId = selected.id.filter { it.isLetterOrDigit() || it == '-' || it == '_' }
                        val file = File(directory, "$safeId.gpx").apply { writeText(gpx, Charsets.UTF_8) }
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                        withContext(Dispatchers.Main) {
                            val send = Intent(Intent.ACTION_SEND).setType("application/gpx+xml")
                                .putExtra(Intent.EXTRA_SUBJECT, selected.title ?: selected.sport)
                                .putExtra(Intent.EXTRA_STREAM, uri)
                            send.clipData = ClipData.newUri(context.contentResolver, exportLabel, uri)
                            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            val chooser = Intent.createChooser(send, exportLabel)
                            chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            context.startActivity(chooser)
                        }
                    } catch (_: Exception) {
                        withContext(Dispatchers.Main) { Toast.makeText(context, exportFailed, Toast.LENGTH_LONG).show() }
                    }
                }
            }); PaceTextAction(stringResource(R.string.pace_history_edit_action), onClick = { editing = selected.id }); PaceTextAction(stringResource(R.string.pace_history_delete_action), onClick = { deleting = selected.id }) }
            }
        }
    }
}

@Composable
private fun HomeWorkoutActivityEditDialog(
    initialTitle: String,
    initialDetails: HomeWorkoutActivityDetails,
    onDismiss: () -> Unit,
    onSave: (String, HomeWorkoutActivityDetails) -> Unit,
) {
    var title by remember(initialDetails) { mutableStateOf(initialTitle) }
    var completedReps by remember(initialDetails) { mutableStateOf(initialDetails.exercises.map { it.completedRepetitions.toString() }) }
    var completedDurations by remember(initialDetails) { mutableStateOf(initialDetails.exercises.map { it.completedDurationSeconds.toString() }) }
    var exerciseNotes by remember(initialDetails) { mutableStateOf(initialDetails.exercises.map { it.notes }) }
    var notes by remember(initialDetails) { mutableStateOf(initialDetails.notes) }
    var error by remember { mutableStateOf(false) }
    val invalidText = stringResource(R.string.pace_workout_activity_edit_invalid)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pace_workout_activity_edit_title)) },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(title, { title = it.take(80) }, label = { Text(stringResource(R.string.pace_history_title_label)) }, singleLine = true)
                Text(stringResource(R.string.pace_workout_activity_edit_help), style = MaterialTheme.typography.bodySmall)
                initialDetails.exercises.forEachIndexed { index, exercise ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        val nameResource = homeWorkoutExerciseNameResource(exercise.exerciseId)
                        Text(nameResource?.let { stringResource(it) } ?: exercise.exerciseId, style = MaterialTheme.typography.titleSmall)
                        if (exercise.plannedRepetitions != null) {
                            OutlinedTextField(
                                value = completedReps[index],
                                onValueChange = { value -> completedReps = completedReps.toMutableList().also { it[index] = value.filter(Char::isDigit).take(5) } },
                                label = { Text(stringResource(R.string.pace_workout_activity_completed_reps_label)) },
                                singleLine = true,
                            )
                        }
                        if (exercise.durationSeconds != null) {
                            OutlinedTextField(
                                value = completedDurations[index],
                                onValueChange = { value -> completedDurations = completedDurations.toMutableList().also { it[index] = value.filter(Char::isDigit).take(5) } },
                                label = { Text(stringResource(R.string.pace_workout_activity_completed_duration_label)) },
                                singleLine = true,
                            )
                        }
                        OutlinedTextField(
                            value = exerciseNotes[index],
                            onValueChange = { value -> exerciseNotes = exerciseNotes.toMutableList().also { it[index] = value.take(HomeWorkoutActivityCodec.MAX_NOTES_LENGTH) } },
                            label = { Text(stringResource(R.string.pace_workout_activity_exercise_note)) },
                            supportingText = { Text("${exerciseNotes[index].length}/${HomeWorkoutActivityCodec.MAX_NOTES_LENGTH}") },
                        )
                    }
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it.take(HomeWorkoutActivityCodec.MAX_SUMMARY_NOTES_LENGTH) },
                    label = { Text(stringResource(R.string.pace_history_notes_label)) },
                    supportingText = { Text("${notes.length}/${HomeWorkoutActivityCodec.MAX_SUMMARY_NOTES_LENGTH}") },
                )
                if (error) Text(invalidText, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                var countsValid = true
                val updated = initialDetails.exercises.mapIndexed { index, exercise ->
                    val reps = completedReps[index].toIntOrNull()
                    val duration = completedDurations[index].toIntOrNull()
                    if (reps == null || duration == null) countsValid = false
                    exercise.copy(
                        completedRepetitions = reps ?: exercise.completedRepetitions,
                        completedDurationSeconds = duration ?: exercise.completedDurationSeconds,
                        notes = exerciseNotes[index],
                    )
                }
                val candidate = initialDetails.copy(exercises = updated, notes = notes)
                if (countsValid && runCatching { HomeWorkoutActivityCodec.encode(candidate) }.isSuccess) onSave(title, candidate) else error = true
            }) { Text(stringResource(R.string.pace_history_save_action)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.pace_cancel)) } },
    )
}

@Composable
private fun RouteThumbnail(repository: ActivityRepository, activityId: String, modifier: Modifier = Modifier) {
    val route by produceState<List<com.pace.app.data.local.entity.TrackPointEntity>>(emptyList(), activityId) { value = repository.routeThumbnail(activityId) }
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        if (route.size >= 2) {
            val minLat = route.minOf { it.latitude }; val maxLat = route.maxOf { it.latitude }
            val minLon = route.minOf { it.longitude }; val maxLon = route.maxOf { it.longitude }
            val path = Path()
            route.forEachIndexed { index, point ->
                val x = ((point.longitude - minLon) / (maxLon - minLon).coerceAtLeast(1e-7) * size.width).toFloat()
                val y = (size.height - (point.latitude - minLat) / (maxLat - minLat).coerceAtLeast(1e-7) * size.height).toFloat()
                if (index == 0 || route[index - 1].segmentIndex != point.segmentIndex) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, color, style = Stroke(width = 2.dp.toPx()))
        }
    }
}

@Composable
private fun DetailMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(8.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

private fun localDateBoundary(utcMidnightMillis: Long): Long = Instant.ofEpochMilli(utcMidnightMillis)
    .atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

private fun formatActivityTime(seconds: Long): String {
    val safe = seconds.coerceAtLeast(0)
    val minutes = safe / 60
    return if (minutes < 60) String.format(Locale.getDefault(), "%d:%02d", minutes, safe % 60)
    else String.format(Locale.getDefault(), "%d:%02d:%02d", minutes / 60, minutes % 60, safe % 60)
}

private fun formatDistance(meters: Double, imperial: Boolean): String = if (imperial) "%.2f mi".format(meters / com.pace.app.domain.metrics.ActivityMetrics.METERS_PER_MILE) else "%.2f km".format(meters / 1000)

@Composable
private fun MetricLineChart(label: String, values: List<Double>) {
    val color = MaterialTheme.colorScheme.primary
    Column {
        Text(label, style = MaterialTheme.typography.titleSmall)
        if (values.size < 2) Text(stringResource(R.string.pace_history_chart_unavailable)) else Canvas(Modifier.fillMaxWidth().height(90.dp)) {
            val min = values.minOrNull() ?: 0.0; val range = (values.maxOrNull() ?: 0.0) - min
            val path = Path()
            values.forEachIndexed { index, value ->
                val x = size.width * index / (values.lastIndex.coerceAtLeast(1))
                val y = size.height - if (range == 0.0) size.height / 2 else ((value - min) / range * size.height).toFloat()
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, color, style = Stroke(width = 3.dp.toPx()))
        }
    }
}
