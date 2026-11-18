package com.pace.app.ui.record

import android.Manifest
import android.animation.ValueAnimator
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.pace.app.PaceApplication
import com.pace.app.PacePreferences
import com.pace.app.PaceThemeChoice
import com.pace.app.PaceUnits
import com.pace.app.R
import com.pace.app.data.local.entity.ActivityEntity
import com.pace.app.data.local.entity.SplitEntity
import com.pace.app.data.local.entity.TrackPointEntity
import com.pace.app.tracking.RecordingService
import com.pace.app.tracking.buildDistanceSplits
import com.pace.app.ui.components.PacePrimaryButton
import com.pace.app.ui.components.PaceSport
import com.pace.app.ui.components.PaceSportSelectorTile
import com.pace.app.ui.maps.PaceMapPanel
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun PaceRecordFlow(
    preferences: PacePreferences,
    onPreferencesChanged: (PacePreferences) -> Unit,
    onSelectTab: (String) -> Unit,
    isOnline: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val app = context.applicationContext as PaceApplication
    val coroutineScope = rememberCoroutineScope()
    val database = app.container.database
    val activeFlow = remember(database) { database.activityDao().observeActiveRecording() }
    val active by activeFlow.collectAsState(initial = null)
    var completedId by rememberSaveable { mutableStateOf<String?>(null) }
    val completedFlow = remember(database, completedId) {
        completedId?.let(database.activityDao()::observeById) ?: flowOf(null)
    }
    val completed by completedFlow.collectAsState(initial = null)
    val trackId = active?.id ?: completed?.takeIf { it.status == "SAVED" }?.id
    val pointsFlow = remember(database, trackId) {
        trackId?.let(database.trackPointDao()::observeForActivity) ?: flowOf(emptyList())
    }
    val trackPoints by pointsFlow.collectAsState(initial = emptyList())
    val splitsFlow = remember(database, trackId) {
        trackId?.let(database.splitDao()::observeForActivity) ?: flowOf(emptyList())
    }
    val splits by splitsFlow.collectAsState(initial = emptyList())
    val visibleSplits = active?.let { session -> remember(session.id, trackPoints) { buildDistanceSplits(session.id, trackPoints) } } ?: splits

    var selectedSport by rememberSaveable { mutableStateOf(preferences.lastUsedSport) }
    var mapFirst by rememberSaveable { mutableStateOf(true) }
    var followLocation by rememberSaveable { mutableStateOf(true) }
    var recenterKey by rememberSaveable { mutableLongStateOf(0L) }
    var finishing by rememberSaveable { mutableStateOf(false) }
    var confirmFinish by rememberSaveable { mutableStateOf(false) }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    var permissionDenied by rememberSaveable { mutableStateOf(false) }
    var titleDraft by rememberSaveable(active?.id ?: completed?.id) { mutableStateOf(active?.title ?: completed?.title.orEmpty()) }
    var titleDirty by rememberSaveable(active?.id ?: completed?.id) { mutableStateOf(false) }
    var summarySport by rememberSaveable(completed?.id) {
        mutableStateOf(completed?.sport?.let(::sportFromStorage) ?: selectedSport)
    }
    var elapsedDisplay by remember { mutableLongStateOf(active?.elapsedSeconds ?: 0L) }
    val options = remember { PaceSport.entries }
    val selectedOptions = preferences.preferredSports.ifEmpty { PaceSport.entries.toSet() }
    val darkTheme = when (preferences.theme) {
        PaceThemeChoice.System -> androidx.compose.foundation.isSystemInDarkTheme()
        PaceThemeChoice.Light -> false
        PaceThemeChoice.Dark -> true
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val allowed = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        permissionDenied = !allowed
        if (allowed) {
            completedId = null
            finishing = false
            RecordingService.start(context, selectedSport.name.uppercase(), UUID.randomUUID().toString())
        }
    }

    LaunchedEffect(active?.id, active?.elapsedSeconds, active?.status) {
        val session = active ?: return@LaunchedEffect
        elapsedDisplay = session.elapsedSeconds
        if (session.status == "RECORDING" && RecordingService.isRunning) {
            val anchor = android.os.SystemClock.elapsedRealtime()
            while (true) {
                delay(1_000)
                elapsedDisplay = session.elapsedSeconds + (android.os.SystemClock.elapsedRealtime() - anchor).coerceAtLeast(0) / 1_000
            }
        }
    }

    LaunchedEffect(active?.id, titleDraft, titleDirty) {
        val session = active ?: return@LaunchedEffect
        if (!titleDirty) return@LaunchedEffect
        delay(450)
        RecordingService.updateTitle(context, session.id, titleDraft)
        titleDirty = false
    }

    LaunchedEffect(completed?.id, completed?.status) {
        if (completed?.status == "SAVED") finishing = false
    }

    val session = active
    val completedActivity = completed?.takeIf { it.status == "SAVED" }
    val isRecovered = session?.let { it.status == "RECOVERED" || (it.status == "RECORDING" && !RecordingService.isRunning) } == true
    val waitingForFirstFix = session != null && trackPoints.isEmpty()

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            stringResource(if (completedActivity != null) R.string.pace_record_summary_title else R.string.pace_record_title),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() },
        )

        if (completedActivity != null) {
            AnimatedVisibility(visible = true, enter = fadeIn(tween(if (ValueAnimator.areAnimatorsEnabled()) 220 else 0))) {
                ActivityCompletion(
                    activity = completedActivity,
                    routePoints = trackPoints,
                    splits = splits,
                    sport = summarySport,
                    title = titleDraft,
                    darkTheme = darkTheme,
                    isOnline = isOnline,
                    onTitleChange = { titleDraft = it; titleDirty = true },
                    onSportChange = { summarySport = it },
                    onSave = {
                        val metadata = completedActivity.copy(
                            title = titleDraft.trim().takeIf(String::isNotEmpty),
                            sport = summarySport.name.uppercase(),
                            updatedAtEpochMillis = System.currentTimeMillis(),
                        )
                        coroutineScope.launch {
                            withContext(Dispatchers.IO) { database.activityDao().upsert(metadata) }
                            titleDirty = false
                        }
                    },
                    onDashboard = { completedId = null; onSelectTab("home") },
                    units = preferences.units,
                )
            }
        } else if (session == null) {
            Preflight(
                selectedSport = selectedSport,
                availableSports = options.filter { it in selectedOptions },
                permissionDenied = permissionDenied,
                onSelectSport = { sport ->
                    selectedSport = sport
                    onPreferencesChanged(preferences.copy(lastUsedSport = sport))
                },
                onStart = {
                    val permissions = mutableListOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
                    if (android.os.Build.VERSION.SDK_INT >= 33) permissions += Manifest.permission.POST_NOTIFICATIONS
                    permissionLauncher.launch(permissions.toTypedArray())
                },
            )
        } else {
            ActiveWorkout(
                session = session,
                elapsedSeconds = elapsedDisplay,
                routePoints = trackPoints,
                splits = visibleSplits,
                title = titleDraft,
                sport = sportFromStorage(session.sport),
                darkTheme = darkTheme,
                isOnline = isOnline,
                mapFirst = mapFirst,
                followLocation = followLocation,
                recenterKey = recenterKey.toInt(),
                isRecovered = isRecovered,
                waitingForFirstFix = waitingForFirstFix,
                onTitleChange = { titleDraft = it; titleDirty = true },
                onMapFirstChange = { mapFirst = it },
                onFollowChange = { followLocation = it },
                onRecenter = { recenterKey += 1 },
                onPauseResume = {
                    if (session.status == "PAUSED" || isRecovered) {
                        RecordingService.resume(context, session.id)
                    } else {
                        RecordingService.control(context, RecordingService.ACTION_PAUSE, session.id)
                    }
                },
                onFinish = { confirmFinish = true },
                onDiscard = { confirmDiscard = true },
                finishing = finishing,
                units = preferences.units,
            )
        }
    }

    if (confirmFinish && session != null) AlertDialog(
        onDismissRequest = { confirmFinish = false },
        title = { Text(stringResource(R.string.pace_record_finish_title)) },
        text = { Text(stringResource(R.string.pace_record_finish_body)) },
        confirmButton = {
            TextButton(onClick = {
                completedId = session.id
                finishing = true
                confirmFinish = false
                RecordingService.control(context, RecordingService.ACTION_FINISH, session.id, titleDraft)
            }) { Text(stringResource(R.string.pace_record_finish)) }
        },
        dismissButton = { TextButton(onClick = { confirmFinish = false }) { Text(stringResource(R.string.pace_cancel)) } },
    )

    if (confirmDiscard && session != null) AlertDialog(
        onDismissRequest = { confirmDiscard = false },
        title = { Text(stringResource(R.string.pace_record_discard_title)) },
        text = { Text(stringResource(R.string.pace_record_discard_body)) },
        confirmButton = {
            TextButton(onClick = {
                confirmDiscard = false
                RecordingService.discard(context, session.id)
            }) { Text(stringResource(R.string.pace_record_discard_action), color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(R.string.pace_cancel)) } },
    )
}

@Composable
private fun Preflight(
    selectedSport: PaceSport,
    availableSports: List<PaceSport>,
    permissionDenied: Boolean,
    onSelectSport: (PaceSport) -> Unit,
    onStart: () -> Unit,
) {
    val context = LocalContext.current
    val hasLocationPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.pace_record_preflight_heading), style = MaterialTheme.typography.titleLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            availableSports.forEach { sport ->
                PaceSportSelectorTile(
                    sport = sport,
                    label = stringResource(sportLabel(sport)),
                    selected = sport == selectedSport,
                    onClick = { onSelectSport(sport) },
                )
            }
        }
        Text(stringResource(R.string.pace_record_selected_sport, stringResource(sportLabel(selectedSport))), style = MaterialTheme.typography.titleMedium)
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.pace_record_preflight_body), style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(if (hasLocationPermission) R.string.pace_record_gps_ready else R.string.pace_record_permission_rationale),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(if (selectedSport == PaceSport.Cycling) R.string.pace_record_cycling_sensor_status else R.string.pace_record_gps_ready_status),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (permissionDenied) Text(stringResource(R.string.pace_record_permission_denied), color = MaterialTheme.colorScheme.error)
        PacePrimaryButton(label = stringResource(R.string.pace_record_start), onClick = onStart, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun ActiveWorkout(
    session: ActivityEntity,
    elapsedSeconds: Long,
    routePoints: List<TrackPointEntity>,
    splits: List<SplitEntity>,
    title: String,
    sport: PaceSport,
    darkTheme: Boolean,
    isOnline: Boolean,
    mapFirst: Boolean,
    followLocation: Boolean,
    recenterKey: Int,
    isRecovered: Boolean,
    waitingForFirstFix: Boolean,
    onTitleChange: (String) -> Unit,
    onMapFirstChange: (Boolean) -> Unit,
    onFollowChange: (Boolean) -> Unit,
    onRecenter: () -> Unit,
    onPauseResume: () -> Unit,
    onFinish: () -> Unit,
    onDiscard: () -> Unit,
    finishing: Boolean,
    units: PaceUnits,
) {
    val accuracy = routePoints.lastOrNull()?.horizontalAccuracyMeters
    val hasFix = routePoints.isNotEmpty()
    val signalLost = routePoints.lastOrNull()?.let { System.currentTimeMillis() - it.timestampEpochMillis > 30_000L } == true
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            stringResource(
                when {
                    finishing -> R.string.pace_record_finishing
                    isRecovered -> R.string.pace_record_recovered
                    session.status == "PAUSED" -> R.string.pace_record_paused
                    waitingForFirstFix -> R.string.pace_record_acquiring_gps
                    hasFix -> R.string.pace_record_tracking
                    else -> R.string.pace_record_waiting_fix
                },
            ),
            style = MaterialTheme.typography.titleMedium,
        )
        if (isRecovered) Text(stringResource(R.string.pace_record_recovery_body), color = MaterialTheme.colorScheme.error)
        OutlinedTextField(
            value = title,
            onValueChange = onTitleChange,
            label = { Text(stringResource(R.string.pace_record_activity_title)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )
        if (mapFirst) {
            MapWorkoutPanel(routePoints, darkTheme, isOnline, followLocation, recenterKey, onFollowChange, onRecenter)
            LiveMetrics(session, elapsedSeconds, routePoints.lastOrNull(), accuracy, sport, units)
        } else {
            LiveMetrics(session, elapsedSeconds, routePoints.lastOrNull(), accuracy, sport, units)
            MapWorkoutPanel(routePoints, darkTheme, isOnline, followLocation, recenterKey, onFollowChange, onRecenter)
        }
        if (session.status == "PAUSED" || isRecovered) {
            PacePrimaryButton(label = stringResource(R.string.pace_record_resume), onClick = onPauseResume, modifier = Modifier.fillMaxWidth())
        } else {
            PacePrimaryButton(label = stringResource(R.string.pace_record_pause), onClick = onPauseResume, modifier = Modifier.fillMaxWidth())
        }
        PacePrimaryButton(
            label = stringResource(R.string.pace_record_finish),
            onClick = onFinish,
            modifier = Modifier.fillMaxWidth(),
            enabled = !finishing,
        )
        TextButton(onClick = onDiscard, modifier = Modifier.fillMaxWidth().height(56.dp), enabled = !finishing) {
            Text(stringResource(R.string.pace_record_discard_action), color = MaterialTheme.colorScheme.error)
        }
        FilterChip(selected = mapFirst, onClick = { onMapFirstChange(true) }, label = { Text(stringResource(R.string.pace_record_map_first)) })
        FilterChip(selected = !mapFirst, onClick = { onMapFirstChange(false) }, label = { Text(stringResource(R.string.pace_record_metrics_first)) })
        if (splits.isNotEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.pace_record_splits_title), style = MaterialTheme.typography.titleMedium)
                    splits.takeLast(3).forEach { split ->
                        Text(stringResource(R.string.pace_record_split_row, split.splitNumber, formatDistance(split.distanceMeters.toLong(), PaceUnits.Metric), formatDuration(split.elapsedSeconds)))
                    }
                }
            }
        } else {
            Text(stringResource(R.string.pace_record_no_splits_yet), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            accuracy?.let { stringResource(R.string.pace_record_accuracy, it) } ?: stringResource(R.string.pace_record_waiting_fix),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (signalLost) Text(stringResource(R.string.pace_record_gps_signal_lost), color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun MapWorkoutPanel(
    points: List<TrackPointEntity>,
    darkTheme: Boolean,
    isOnline: Boolean,
    follow: Boolean,
    recenterKey: Int,
    onFollowChange: (Boolean) -> Unit,
    onRecenter: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PaceMapPanel(
            darkTheme = darkTheme,
            isOnline = isOnline,
            routePoints = points,
            followLocation = follow,
            recenterKey = recenterKey,
            modifier = Modifier.fillMaxWidth().height(300.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilterChip(selected = follow, onClick = { onFollowChange(!follow) }, label = { Text(stringResource(R.string.pace_record_follow_location)) })
            FilterChip(selected = false, onClick = onRecenter, label = { Text(stringResource(R.string.pace_record_recenter)) })
        }
    }
}

@Composable
private fun LiveMetrics(
    activity: ActivityEntity,
    elapsedSeconds: Long,
    lastPoint: TrackPointEntity?,
    accuracy: Float?,
    sport: PaceSport,
    units: PaceUnits,
) {
    val km = activity.distanceMeters / 1_000.0
    val mi = activity.distanceMeters / 1_609.344
    val movingSeconds = elapsedSeconds.coerceAtLeast(0)
    val runningPaceSeconds = if (activity.distanceMeters > 0) movingSeconds / (activity.distanceMeters / 1_000.0) else null
    val speedMetersPerSecond = if (lastPoint?.speedMetersPerSecond != null) lastPoint.speedMetersPerSecond.toDouble() else if (movingSeconds > 0) activity.distanceMeters / movingSeconds.toDouble() else null
    val speed = speedMetersPerSecond?.let { if (units == PaceUnits.Metric) it * 3.6 else it * 2.236936 }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricCard(stringResource(R.string.pace_record_elapsed), formatDuration(elapsedSeconds), Modifier.weight(1f))
            MetricCard(stringResource(R.string.pace_record_distance), formatDistance(activity.distanceMeters, units), Modifier.weight(1f))
        }
        if (activity.distanceMeters > 0) {
            val toNextSplit = (1_000L - activity.distanceMeters.rem(1_000L)).coerceAtLeast(1L)
            Text(stringResource(R.string.pace_record_next_split, formatDistance(toNextSplit, units)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (sport == PaceSport.Cycling) {
                MetricCard(stringResource(R.string.pace_record_speed), speed?.let { formatNumber(it, 1) + if (units == PaceUnits.Metric) " km/h" else " mph" } ?: "—", Modifier.weight(1f))
            } else {
                val pace = runningPaceSeconds?.let { it * if (units == PaceUnits.Metric) 1.0 else 1.609344 }
                MetricCard(stringResource(R.string.pace_record_pace), pace?.takeIf { it.isFinite() }?.let { formatPace(it) + if (units == PaceUnits.Metric) " /km" else " /mi" } ?: "—", Modifier.weight(1f))
            }
            MetricCard(stringResource(R.string.pace_record_elevation), activity.elevationGainMeters?.let { formatNumber(it, 0) + " m" } ?: "—", Modifier.weight(1f))
        }
        Text(
            accuracy?.let { stringResource(R.string.pace_record_accuracy, it) } ?: stringResource(R.string.pace_record_waiting_fix),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(2.dp))
    }
}

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ActivityCompletion(
    activity: ActivityEntity,
    routePoints: List<TrackPointEntity>,
    splits: List<SplitEntity>,
    sport: PaceSport,
    title: String,
    darkTheme: Boolean,
    isOnline: Boolean,
    onTitleChange: (String) -> Unit,
    onSportChange: (PaceSport) -> Unit,
    onSave: () -> Unit,
    onDashboard: () -> Unit,
    units: PaceUnits,
) {
    val options = PaceSport.entries
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(title.ifBlank { stringResource(sportLabel(sport)) }, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricCard(stringResource(R.string.pace_record_distance), formatDistance(activity.distanceMeters, units), Modifier.weight(1f))
            MetricCard(stringResource(R.string.pace_record_elapsed), formatDuration(activity.elapsedSeconds), Modifier.weight(1f))
        }
        val primary = if (sport == PaceSport.Cycling) {
            if (activity.elapsedSeconds > 0) (activity.distanceMeters / activity.elapsedSeconds.toDouble()) * if (units == PaceUnits.Metric) 3.6 else 2.236936 else null
        } else if (activity.distanceMeters > 0) activity.elapsedSeconds / (activity.distanceMeters / if (units == PaceUnits.Metric) 1_000.0 else 1_609.344) else null
        MetricCard(
            stringResource(if (sport == PaceSport.Cycling) R.string.pace_record_average_speed else R.string.pace_record_average_pace),
            primary?.let {
                if (sport == PaceSport.Cycling) formatNumber(it, 1) + if (units == PaceUnits.Metric) " km/h" else " mph"
                else formatPace(it) + if (units == PaceUnits.Metric) " /km" else " /mi"
            } ?: "—",
            Modifier.fillMaxWidth(),
        )
        PaceMapPanel(
            darkTheme = darkTheme,
            isOnline = isOnline,
            routePoints = routePoints,
            fitRoute = routePoints.size > 1,
            modifier = Modifier.fillMaxWidth().height(280.dp),
        )
        Text(stringResource(R.string.pace_record_post_workout_splits), style = MaterialTheme.typography.titleMedium)
        if (splits.isEmpty()) Text(stringResource(R.string.pace_record_no_splits_saved), color = MaterialTheme.colorScheme.onSurfaceVariant)
        splits.forEach { split ->
            Text(stringResource(R.string.pace_record_split_row, split.splitNumber, formatDistance(split.distanceMeters.toLong(), units), formatDuration(split.elapsedSeconds)))
        }
        OutlinedTextField(
            value = title,
            onValueChange = onTitleChange,
            label = { Text(stringResource(R.string.pace_record_activity_title)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(stringResource(R.string.pace_record_edit_sport), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                FilterChip(selected = sport == option, onClick = { onSportChange(option) }, label = { Text(stringResource(sportLabel(option))) })
            }
        }
        PacePrimaryButton(label = stringResource(R.string.pace_record_save_changes), onClick = onSave, modifier = Modifier.fillMaxWidth())
        PacePrimaryButton(label = stringResource(R.string.pace_record_dashboard), onClick = onDashboard, modifier = Modifier.fillMaxWidth())
    }
}

private fun sportLabel(sport: PaceSport): Int = when (sport) {
    PaceSport.Cycling -> R.string.sport_cycling
    PaceSport.Running -> R.string.sport_running
    PaceSport.Jogging -> R.string.sport_jogging
    PaceSport.Walking -> R.string.sport_walking
}

private fun sportFromStorage(sport: String): PaceSport = when (sport.uppercase()) {
    "CYCLING" -> PaceSport.Cycling
    "JOGGING" -> PaceSport.Jogging
    "WALKING" -> PaceSport.Walking
    else -> PaceSport.Running
}

private fun formatDuration(seconds: Long): String {
    val safe = seconds.coerceAtLeast(0)
    return String.format(Locale.getDefault(), "%02d:%02d:%02d", safe / 3600, (safe % 3600) / 60, safe % 60)
}

private fun formatDistance(meters: Long, units: PaceUnits): String = if (units == PaceUnits.Metric) {
    if (meters < 1_000) "$meters m" else String.format(Locale.getDefault(), "%.2f km", meters / 1_000.0)
} else {
    String.format(Locale.getDefault(), "%.2f mi", meters / 1_609.344)
}

private fun formatNumber(value: Double, decimals: Int): String = String.format(Locale.getDefault(), "%1$.${decimals}f", value)

private fun formatPace(seconds: Double): String {
    val safe = seconds.toLong().coerceAtLeast(0)
    return String.format(Locale.getDefault(), "%d:%02d", safe / 60, safe % 60)
}
