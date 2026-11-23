package com.pace.app.ui.record

import android.Manifest
import android.animation.ValueAnimator
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.pace.app.PaceApplication
import com.pace.app.PaceAppIcons
import com.pace.app.PacePreferences
import com.pace.app.ui.theme.LocalReducedMotion
import com.pace.app.PaceUnits
import com.pace.app.R
import com.pace.app.data.local.entity.ActivityEntity
import com.pace.app.data.local.entity.PlannedSessionEntity
import com.pace.app.data.local.entity.SplitEntity
import com.pace.app.data.local.entity.TrackPointEntity
import com.pace.app.tracking.RecordingService
import com.pace.app.tracking.buildDistanceSplits
import com.pace.app.ui.components.PacePrimaryButton
import com.pace.app.ui.components.PaceSecondaryButton
import com.pace.app.ui.components.PaceSport
import com.pace.app.ui.components.PaceSportSelectorTile
import com.pace.app.ui.maps.PaceMapPanel
import java.util.Locale
import java.util.UUID
import java.time.Instant
import java.time.ZoneId
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
    initialSport: PaceSport? = null,
    onInitialSportApplied: () -> Unit = {},
    activeGuideRoute: com.pace.app.data.local.dao.SavedRouteWithPoints? = null,
    onClearGuideRoute: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val app = context.applicationContext as PaceApplication
    val coroutineScope = rememberCoroutineScope()
    val database = app.container.database
    val ownerId = app.container.currentOwnerId()
    val activeFlow = remember(database, ownerId) { database.activityDao().observeActiveRecording(ownerId) }
    val active by activeFlow.collectAsState(initial = null)
    var completedId by rememberSaveable { mutableStateOf<String?>(null) }
    var sharingActivity by rememberSaveable { mutableStateOf(false) }
    val completedFlow = remember(database, completedId, ownerId) {
        completedId?.let { database.activityDao().observeById(it, ownerId) } ?: flowOf(null)
    }
    val completed by completedFlow.collectAsState(initial = null)
    val workoutTemplates by remember { app.container.workoutRepository.observeTemplates() }.collectAsState(initial = emptyList())
    val completedDate = completed?.let { Instant.ofEpochMilli(it.startedAtEpochMillis).atZone(ZoneId.systemDefault()).toLocalDate() }
    val plannedFlow = remember(completedDate) {
        completedDate?.let { date -> app.container.workoutRepository.observeCalendar(date.toString(), date.plusDays(1).toString()) }
            ?: flowOf(emptyList())
    }
    val plannedSessions by plannedFlow.collectAsState(initial = emptyList())
    val trackId = completedId ?: active?.id
    val pointsFlow = remember(database, trackId) {
        trackId?.let(database.trackPointDao()::observeForActivity) ?: flowOf(emptyList())
    }
    val trackPoints by androidx.compose.runtime.key(trackId) { pointsFlow.collectAsState(initial = emptyList()) }
    val splitsFlow = remember(database, trackId) {
        trackId?.let(database.splitDao()::observeForActivity) ?: flowOf(emptyList())
    }
    val splits by splitsFlow.collectAsState(initial = emptyList())
    val visibleSplits = active?.let { session -> remember(session.id, trackPoints) { buildDistanceSplits(session.id, trackPoints) } } ?: splits

    var selectedSport by rememberSaveable { mutableStateOf(preferences.lastUsedSport) }
    LaunchedEffect(initialSport) {
        if (initialSport != null) {
            selectedSport = initialSport
            onPreferencesChanged(preferences.copy(lastUsedSport = initialSport))
            onInitialSportApplied()
        }
    }
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
    val darkTheme = false

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
    val exitSummary = { sharingActivity = false; completedId = null; onSelectTab("home") }
    androidx.activity.compose.BackHandler(enabled = completedActivity != null && !sharingActivity, onBack = exitSummary)
    val isRecovered = session?.let { it.status == "RECOVERED" || (it.status == "RECORDING" && !RecordingService.isRunning) } == true
    val waitingForFirstFix = session != null && trackPoints.isEmpty()
    val acquisition by RecordingService.gpsAcquisition.collectAsState()
    val requestStart = {
        val permissions = mutableListOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
        if (android.os.Build.VERSION.SDK_INT >= 33) permissions += Manifest.permission.POST_NOTIFICATIONS
        permissionLauncher.launch(permissions.toTypedArray())
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (completedActivity == null && session == null) {
                Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
                    PacePrimaryButton(
                        label = stringResource(R.string.pace_record_start),
                        onClick = requestStart,
                        enabled = acquisition == RecordingService.Acquisition.Idle || acquisition == RecordingService.Acquisition.TimedOut,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp).heightIn(min = 64.dp),
                        icon = PaceAppIcons.Play,
                    )
                }
            } else if (session != null && completedActivity == null) {
                val paused = session.status == "PAUSED" || isRecovered
                Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PacePrimaryButton(
                            label = stringResource(if (paused) R.string.pace_record_resume else R.string.pace_record_pause),
                            onClick = {
                                if (paused) RecordingService.resume(context, session.id)
                                else RecordingService.control(context, RecordingService.ACTION_PAUSE, session.id)
                            },
                            modifier = Modifier.weight(1.25f).heightIn(min = 64.dp),
                            enabled = !finishing,
                            icon = if (paused) PaceAppIcons.Play else PaceAppIcons.Pause,
                        )
                        PaceSecondaryButton(
                            label = stringResource(R.string.pace_record_finish_short),
                            onClick = { confirmFinish = true },
                            modifier = Modifier.weight(1f).heightIn(min = 64.dp),
                            enabled = !finishing,

                        )
                        androidx.compose.material3.IconButton(onClick = { confirmDiscard = true }, enabled = !finishing) {
                            Icon(PaceAppIcons.Delete, stringResource(R.string.pace_record_discard_action), tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        },
    ) { screenPadding ->
        Column(
            Modifier.fillMaxSize().padding(screenPadding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (session == null && completedActivity == null) {
                when (acquisition) {
                    RecordingService.Acquisition.Waiting, RecordingService.Acquisition.Starting -> {
                        androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text(stringResource(R.string.pace_record_acquiring_gps))
                        if (acquisition == RecordingService.Acquisition.Waiting) {
                            TextButton(onClick = { RecordingService.cancelAcquisition(context) }) { Text(stringResource(R.string.pace_cancel)) }
                        }
                    }
                    RecordingService.Acquisition.TimedOut -> Text(stringResource(R.string.pace_record_gps_timeout))
                    RecordingService.Acquisition.Idle -> Unit
                }
            }
            if (!sharingActivity) Row(verticalAlignment = Alignment.CenterVertically) {
                if (completedActivity != null || active == null) {
                    androidx.compose.material3.IconButton(onClick = exitSummary) {
                        Icon(PaceAppIcons.Back, stringResource(R.string.pace_action_back))
                    }
                }
                Text(
                    stringResource(if (completedActivity != null) R.string.pace_record_summary_title else R.string.pace_record_title),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.semantics { heading() },
                )
            }

            if (completedActivity != null && sharingActivity) {
                PaceActivityShare(
                    trackPoints,
                    formatDistance(completedActivity.distanceMeters, preferences.units),
                    formatDuration(completedActivity.elapsedSeconds),
                    isOnline = isOnline,
                    onBack = { sharingActivity = false },
                )
            } else if (completedActivity != null) {
                AnimatedVisibility(visible = true, enter = fadeIn(tween(if (ValueAnimator.areAnimatorsEnabled() && !LocalReducedMotion.current) 220 else 0))) {
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
                                android.widget.Toast.makeText(context, R.string.pace_record_save_changes, android.widget.Toast.LENGTH_SHORT).show()
                                titleDirty = false
                            }
                        },
                        onDashboard = exitSummary,
                        onShare = { sharingActivity = true },
                        plannedSessions = plannedSessions.filter { planned ->
                            planned.status == "PLANNED" && workoutTemplates.firstOrNull { it.template.id == planned.templateId }?.template?.sport == completedActivity.sport
                        }.map { planned ->
                            planned to (workoutTemplates.firstOrNull { it.template.id == planned.templateId }?.template?.name.orEmpty())
                        },
                        onLinkPlannedSession = { planned ->
                            coroutineScope.launch {
                                app.container.workoutRepository.linkCompletedActivity(planned.id, completedActivity.id, System.currentTimeMillis())
                                com.pace.app.ui.planning.PlanReminderScheduler.cancel(context, planned.id)
                            }
                        },
                        units = preferences.units,
                    )
                }
            } else if (session == null) {
                Preflight(
                    selectedSport = selectedSport,
                    availableSports = options,
                    permissionDenied = permissionDenied,
                    onSelectSport = { sport ->
                        selectedSport = sport
                        onPreferencesChanged(preferences.copy(lastUsedSport = sport))
                    },
                )
            } else {
                ActiveWorkout(
                    session = session,
                    elapsedSeconds = elapsedDisplay,
                    routePoints = trackPoints,
                    splits = visibleSplits,
                    sport = sportFromStorage(session.sport),
                    darkTheme = darkTheme,
                    isOnline = isOnline,
                    mapFirst = mapFirst,
                    followLocation = followLocation,
                    recenterKey = recenterKey.toInt(),
                    isRecovered = isRecovered,
                    waitingForFirstFix = waitingForFirstFix,
                    onMapFirstChange = { mapFirst = it },
                    onFollowChange = { followLocation = it },
                    onRecenter = { recenterKey += 1 },
                    finishing = finishing,
                    units = preferences.units,
                    guideRoute = activeGuideRoute,
                    onClearGuideRoute = onClearGuideRoute,
                )
            }
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
) {
    val context = LocalContext.current
    val hasLocationPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.pace_record_preflight_heading), style = MaterialTheme.typography.titleLarge)
        availableSports.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { sport ->
                    PaceSportSelectorTile(
                        sport = sport,
                        label = stringResource(sportLabel(sport)),
                        selected = sport == selectedSport,
                        onClick = { onSelectSport(sport) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        if (!hasLocationPermission && !permissionDenied) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(PaceAppIcons.Maps, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.pace_record_location_short), style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (permissionDenied) Text(stringResource(R.string.pace_record_permission_denied), color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun ActiveWorkout(
    session: ActivityEntity,
    elapsedSeconds: Long,
    routePoints: List<TrackPointEntity>,
    splits: List<SplitEntity>,
    sport: PaceSport,
    darkTheme: Boolean,
    isOnline: Boolean,
    mapFirst: Boolean,
    followLocation: Boolean,
    recenterKey: Int,
    isRecovered: Boolean,
    waitingForFirstFix: Boolean,
    onMapFirstChange: (Boolean) -> Unit,
    onFollowChange: (Boolean) -> Unit,
    onRecenter: () -> Unit,
    finishing: Boolean,
    units: PaceUnits,
    guideRoute: com.pace.app.data.local.dao.SavedRouteWithPoints? = null,
    onClearGuideRoute: () -> Unit = {},
) {
    val accuracy = routePoints.lastOrNull()?.horizontalAccuracyMeters
    val hasFix = routePoints.isNotEmpty()
    val signalLost = routePoints.lastOrNull()?.let { System.currentTimeMillis() - it.timestampEpochMillis > 30_000L } == true
    val motion = !LocalReducedMotion.current && ValueAnimator.areAnimatorsEnabled()
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        guideRoute?.let { route ->
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(PaceAppIcons.Maps, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(18.dp))
                        Column {
                            Text(
                                "Route: ${route.route.title}",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            )
                            Text(
                                "%.1f km planned".format(route.route.distanceMeters / 1000.0),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                            )
                        }
                    }
                    IconButton(onClick = onClearGuideRoute, modifier = Modifier.size(28.dp)) {
                        Icon(PaceAppIcons.Delete, contentDescription = "Clear route", modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(PaceAppIcons.Maps, contentDescription = null, tint = if (signalLost) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            Text(
                stringResource(
                    when {
                        finishing -> R.string.pace_record_finishing
                        isRecovered -> R.string.pace_record_recovered
                        session.status == "PAUSED" -> R.string.pace_record_paused
                        signalLost -> R.string.pace_record_gps_signal_lost
                        waitingForFirstFix -> R.string.pace_record_acquiring_gps
                        hasFix -> R.string.pace_record_tracking
                        else -> R.string.pace_record_waiting_fix
                    },
                ),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        if (isRecovered) Text(stringResource(R.string.pace_record_recovery_body), color = MaterialTheme.colorScheme.error)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !mapFirst, onClick = { onMapFirstChange(false) }, label = { Text(stringResource(R.string.pace_record_metrics_first)) })
            FilterChip(selected = mapFirst, onClick = { onMapFirstChange(true) }, label = { Text(stringResource(R.string.pace_record_map_first)) })
        }
        if (!mapFirst) LiveMetrics(session, elapsedSeconds, routePoints.lastOrNull(), sport, units)
        if (mapFirst) {
            MapWorkoutPanel(routePoints, accuracy, darkTheme, isOnline, followLocation, recenterKey, onFollowChange, onRecenter)
            LiveMetrics(session, elapsedSeconds, routePoints.lastOrNull(), sport, units)
        }
        if (splits.isNotEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.pace_record_splits_title), style = MaterialTheme.typography.titleMedium)
                    splits.takeLast(3).forEach { split ->
                        Text(stringResource(R.string.pace_record_split_row, split.splitNumber, formatDistance(split.distanceMeters.toLong(), PaceUnits.Metric), formatDuration(split.elapsedSeconds)))
                    }
                }
            }
        }

    }
}

@Composable
private fun MapWorkoutPanel(
    points: List<TrackPointEntity>,
    accuracyMeters: Float?,
    darkTheme: Boolean,
    isOnline: Boolean,
    follow: Boolean,
    recenterKey: Int,
    onFollowChange: (Boolean) -> Unit,
    onRecenter: () -> Unit,
) {
    val hasFix = points.isNotEmpty()
    val signalLost = points.lastOrNull()?.let { System.currentTimeMillis() - it.timestampEpochMillis > 30_000L } == true
    Box(
        Modifier
            .fillMaxWidth()
            .height(350.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(22.dp))
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
            ),
    ) {
        PaceMapPanel(
            darkTheme = darkTheme,
            isOnline = isOnline,
            routePoints = points,
            followLocation = follow,
            recenterKey = recenterKey,
            modifier = Modifier.fillMaxSize(),
        )
        Surface(
            modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
            tonalElevation = 3.dp,
            shadowElevation = 2.dp,
        ) {
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Surface(
                    shape = androidx.compose.foundation.shape.CircleShape,
                    color = when {
                        !hasFix || signalLost -> MaterialTheme.colorScheme.error
                        (accuracyMeters ?: 99f) <= 12f -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.tertiary
                    },
                    modifier = Modifier.size(9.dp),
                ) {}
                Text(
                    when {
                        !hasFix -> stringResource(R.string.pace_record_waiting_fix)
                        signalLost -> stringResource(R.string.pace_record_map_signal_lost_short)
                        accuracyMeters != null -> stringResource(R.string.pace_record_map_accuracy, accuracyMeters)
                        else -> stringResource(R.string.pace_record_map_locating)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                )
            }
        }
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                onClick = onRecenter,
                shape = androidx.compose.foundation.shape.CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                tonalElevation = 4.dp,
                shadowElevation = 3.dp,
                modifier = Modifier.size(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(PaceAppIcons.Crosshair, contentDescription = stringResource(R.string.pace_record_recenter), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
            }
            Surface(
                onClick = { onFollowChange(!follow) },
                shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                color = if (follow) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                contentColor = if (follow) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                tonalElevation = 4.dp,
                shadowElevation = 3.dp,
                modifier = Modifier.height(44.dp),
            ) {
                Row(
                    Modifier.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        if (follow) PaceAppIcons.Crosshair else PaceAppIcons.Maps,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        stringResource(if (follow) R.string.pace_record_following else R.string.pace_record_follow_location),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(start = 2.dp),
                        maxLines = 1,
                    )
                }
            }
        }
    }
    if (!isOnline) {
        Text(
            stringResource(R.string.pace_map_recording_independent),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LiveMetrics(
    activity: ActivityEntity,
    elapsedSeconds: Long,
    lastPoint: TrackPointEntity?,
    sport: PaceSport,
    units: PaceUnits,
) {
    val movingSeconds = activity.movingSeconds.coerceAtLeast(0)
    val runningPaceSeconds = if (activity.distanceMeters > 0 && movingSeconds > 0) movingSeconds / (activity.distanceMeters / 1_000.0) else null
    val speedMetersPerSecond = if (lastPoint?.speedMetersPerSecond != null) lastPoint.speedMetersPerSecond.toDouble() else if (movingSeconds > 0) activity.distanceMeters / movingSeconds.toDouble() else null
    val speed = speedMetersPerSecond?.let { if (units == PaceUnits.Metric) it * 3.6 else it * 2.236936 }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.pace_record_elapsed), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(formatDuration(elapsedSeconds), style = MaterialTheme.typography.displayLarge)
        MetricCard(stringResource(R.string.pace_record_distance), formatDistance(activity.distanceMeters, units), Modifier.fillMaxWidth())
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
        Spacer(Modifier.height(2.dp))
    }
}

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier, shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp), colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
    onShare: () -> Unit,
    plannedSessions: List<Pair<PlannedSessionEntity, String>>,
    onLinkPlannedSession: (PlannedSessionEntity) -> Unit,
    units: PaceUnits,
) {
    val options = PaceSport.entries
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(title.ifBlank { stringResource(sportLabel(sport)) }, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        PaceMapPanel(
            darkTheme = darkTheme,
            isOnline = isOnline,
            routePoints = routePoints,
            fitRoute = routePoints.size > 1,
            modifier = Modifier.fillMaxWidth().height(280.dp),
        )
        if (routePoints.isEmpty()) Text(stringResource(R.string.pace_history_no_route), color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        PaceSecondaryButton(
            label = stringResource(R.string.pace_share_activity),
            onClick = onShare,
            modifier = Modifier.fillMaxWidth(),
            icon = PaceAppIcons.Export,
        )
        Text(stringResource(R.string.pace_record_post_workout_splits), style = MaterialTheme.typography.titleMedium)
        if (splits.isEmpty()) Text(stringResource(R.string.pace_record_no_splits_saved), color = MaterialTheme.colorScheme.onSurfaceVariant)
        val longestSplit = splits.maxOfOrNull { it.elapsedSeconds }?.coerceAtLeast(1L) ?: 1L
        splits.forEach { split ->
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.pace_record_split_row, split.splitNumber, formatDistance(split.distanceMeters.toLong(), units), formatDuration(split.elapsedSeconds)))
                androidx.compose.material3.LinearProgressIndicator(
                    progress = { (split.elapsedSeconds.toFloat() / longestSplit).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
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
        if (plannedSessions.isNotEmpty()) {
            Text(stringResource(R.string.pace_plan_link_completed_title), style = MaterialTheme.typography.titleMedium)
            plannedSessions.forEach { (planned, name) ->
                TextButton(onClick = { onLinkPlannedSession(planned) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.pace_plan_link_completed_action, name))
                }
            }
        }
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
