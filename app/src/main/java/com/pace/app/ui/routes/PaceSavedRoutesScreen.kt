package com.pace.app.ui.routes

import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.content.ClipData
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import androidx.core.content.ContextCompat
import com.pace.app.PaceAppIcons
import com.pace.app.PaceAppContainer
import com.pace.app.PacePreferences
import com.pace.app.R
import com.pace.app.data.local.dao.SavedRouteWithPoints
import com.pace.app.data.local.entity.SavedRouteEntity
import com.pace.app.data.local.entity.SavedRoutePointEntity
import com.pace.app.domain.metrics.ActivityMetrics
import com.pace.app.domain.routes.GpxPoint
import com.pace.app.domain.routes.GpxRouteCodec
import com.pace.app.domain.routes.ManualWaypointCodec
import com.pace.app.domain.routes.RouteMetrics
import com.pace.app.tracking.FallbackLocationSource
import com.pace.app.tracking.TrackSample
import com.pace.app.ui.maps.PaceMapPanel
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun PaceSavedRoutesScreen(
    container: PaceAppContainer,
    preferences: PacePreferences,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    startWithEditor: Boolean = false,
    onStartHandled: () -> Unit = {},
    onStartRecordingRoute: (SavedRouteWithPoints) -> Unit = {},
    isOnline: Boolean = true,
) {
    val routes by container.savedRouteRepository.observeRoutes().collectAsState(initial = emptyList())
    val activities by container.activityRepository.observeHistory().collectAsState(initial = emptyList())
    val context = LocalContext.current
    val permissionDeniedMessage = stringResource(R.string.routes_location_permission_denied)
    val exportFailedMessage = stringResource(R.string.routes_export_failed)
    val shareTitle = stringResource(R.string.routes_share)
    val validationMessage = stringResource(R.string.routes_validation)
    val saveFailedMessage = stringResource(R.string.routes_save_failed)
    val activityNoTrackMessage = stringResource(R.string.routes_activity_no_track)
    val notAvailableMessage = stringResource(R.string.routes_not_available)
    val scope = rememberCoroutineScope()
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var showEditor by rememberSaveable { mutableStateOf(false) }
    var showActivityPicker by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var editingRouteId by rememberSaveable { mutableStateOf<String?>(null) }
    var hasLocationStart by rememberSaveable { mutableStateOf(false) }
    var title by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var sport by rememberSaveable { mutableStateOf("RUNNING") }
    var pointText by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var exportPayload by remember { mutableStateOf<ByteArray?>(null) }
    var followingRouteId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingFollowId by rememberSaveable { mutableStateOf<String?>(null) }
    var currentSample by remember { mutableStateOf<TrackSample?>(null) }
    var locationUnavailable by remember { mutableStateOf(false) }
    var completedRouteSummary by remember { mutableStateOf<String?>(null) }
    var locatingStart by remember { mutableStateOf(false) }
    val locationToken = remember { com.google.android.gms.tasks.CancellationTokenSource() }
    DisposableEffect(locationToken) { onDispose { locationToken.cancel() } }
    fun appendCurrentLocation() {
        locatingStart = true
        error = null
        try {
            com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(context)
                .getCurrentLocation(com.google.android.gms.location.Priority.PRIORITY_BALANCED_POWER_ACCURACY, locationToken.token)
                .addOnCompleteListener { task ->
                    locatingStart = false
                    val fix = if (task.isSuccessful) task.result else null
                    if (fix == null) error = permissionDeniedMessage
                    else {
                        val points = ManualWaypointCodec.parse(pointText).orEmpty()
                        val destinations = if (hasLocationStart) points.drop(1) else points
                        if (destinations.size >= GpxRouteCodec.MAX_POINTS) error = validationMessage
                        else if (showEditor) {
                            pointText = ManualWaypointCodec.format(listOf(GpxPoint(fix.latitude, fix.longitude)) + destinations)
                            hasLocationStart = true
                        }
                    }
                }
        } catch (_: SecurityException) {
            locatingStart = false
            error = permissionDeniedMessage
        }
    }
    val startLocationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants.values.any { it }) appendCurrentLocation() else error = permissionDeniedMessage
    }
    LaunchedEffect(startWithEditor) {
        if (startWithEditor) {
            title = ""
            notes = ""
            pointText = ""
            editingRouteId = null
            hasLocationStart = false
            sport = "RUNNING"
            error = null
            showEditor = true
            onStartHandled()
        }
    }
    val followLocation = remember(context) { FallbackLocationSource(context) }
    val permissionRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants.values.any { it } && pendingFollowId != null) {
            followingRouteId = pendingFollowId
            pendingFollowId = null
        } else {
            pendingFollowId = null
            error = permissionDeniedMessage
        }
    }
    LaunchedEffect(followingRouteId) {
        currentSample = null
        locationUnavailable = false
        if (followingRouteId != null) followLocation.start(2_000L, { currentSample = it }, { locationUnavailable = true })
        else followLocation.stop()
    }
    DisposableEffect(followLocation) { onDispose { followLocation.stop() } }
    val selected = routes.firstOrNull { it.route.id == selectedId }
    val dark = false

    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/gpx+xml")) { uri ->
        val payload = exportPayload
        if (uri != null && payload != null) scope.launch {
            runCatching { withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.use { it.write(payload) } ?: throw IllegalStateException("Cannot write this file.") } }
                .onFailure { error = it.message ?: exportFailedMessage }
        }
        exportPayload = null
    }

    if (showEditor) {
        var query by rememberSaveable { mutableStateOf("") }
        var results by remember { mutableStateOf(emptyList<android.location.Address>()) }
        var searching by remember { mutableStateOf(false) }
        val noResults = stringResource(R.string.routes_search_empty)
        val editedPoints = selected?.takeIf { it.route.id == editingRouteId }?.points.orEmpty()
            .map { GpxPoint(it.latitude, it.longitude, it.altitudeMeters, it.segmentIndex) }
        val parsed = if (editedPoints.isNotEmpty() && pointText == ManualWaypointCodec.format(editedPoints)) {
            editedPoints
        } else {
            ManualWaypointCodec.parse(pointText).orEmpty()
        }
        var saving by remember { mutableStateOf(false) }
        val mapTrack = parsed.mapIndexed { index, point ->
            com.pace.app.data.local.entity.TrackPointEntity(
                "route-editor", index, index.toLong(), point.latitude, point.longitude,
                point.altitudeMeters, segmentIndex = point.segmentIndex,
            )
        }
        val closeEditor = { showEditor = false; editingRouteId = null; hasLocationStart = false; error = null }
        androidx.activity.compose.BackHandler(onBack = closeEditor)
        Column(
            modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TextButton(onClick = closeEditor) { Text(stringResource(R.string.pace_action_back)) }
            Text(stringResource(if (editingRouteId == null) R.string.routes_create else R.string.routes_edit), style = MaterialTheme.typography.headlineMedium)
            OutlinedButton(
                onClick = {
                    val permissions = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                    if (permissions.any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }) appendCurrentLocation()
                    else startLocationPermission.launch(permissions)
                },
                enabled = !locatingStart,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(if (locatingStart) R.string.routes_locating_start else R.string.routes_go_to_location)) }
            OutlinedTextField(
                query, { query = it.take(200); results = emptyList() },
                label = { Text(stringResource(R.string.routes_search_place)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(
                enabled = query.isNotBlank() && !searching,
                onClick = {
                    val requestedQuery = query.trim()
                    searching = true
                    error = null
                    scope.launch {
                        @Suppress("DEPRECATION")
                        val matches = withContext(Dispatchers.IO) {
                            try {
                                android.location.Geocoder(context).getFromLocationName(requestedQuery, 5).orEmpty()
                                    .filter { it.hasLatitude() && it.hasLongitude() }
                            } catch (_: java.io.IOException) {
                                emptyList()
                            }
                        }
                        searching = false
                        if (showEditor && query.trim() == requestedQuery) {
                            results = matches
                            if (matches.isEmpty()) error = noResults
                        }
                    }
                },
            ) { Text(stringResource(if (searching) R.string.routes_searching else R.string.routes_search_action)) }
            results.forEach { address ->
                val label = address.getAddressLine(0) ?: address.featureName ?: address.locality.orEmpty()
                if (label.isNotBlank()) TextButton(onClick = {
                    val points = ManualWaypointCodec.parse(pointText).orEmpty()
                    if (points.size < GpxRouteCodec.MAX_POINTS) {
                        pointText = ManualWaypointCodec.format(points + GpxPoint(address.latitude, address.longitude))
                        query = label
                        results = emptyList()
                    }
                }) { Text(label) }
            }
            PaceMapPanel(
                darkTheme = dark,
                isOnline = isOnline,
                routePoints = mapTrack,
                followLocation = mapTrack.size == 1,
                fitRoute = mapTrack.size >= 2,
                onMapTap = { latitude, longitude ->
                    if (parsed.size < GpxRouteCodec.MAX_POINTS) {
                        pointText = ManualWaypointCodec.format(parsed + GpxPoint(latitude, longitude))
                    }
                },
                modifier = Modifier.fillMaxWidth().height(340.dp),
            )
            if (parsed.size >= 2) {
                val distance = GpxRouteCodec.distanceMeters(parsed)
                Text(stringResource(
                    R.string.routes_distance_and_time,
                    formatDistance(distance, preferences.units == com.pace.app.PaceUnits.Imperial),
                    kotlin.math.ceil(distance / 80.0).toInt().coerceAtLeast(1),
                ), style = MaterialTheme.typography.titleMedium)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { pointText = ManualWaypointCodec.format(parsed.dropLast(1)); if (parsed.size == 1) hasLocationStart = false }, enabled = parsed.isNotEmpty()) {
                    Text(stringResource(R.string.routes_undo_waypoint))
                }
                OutlinedButton(onClick = { pointText = ""; hasLocationStart = false }, enabled = parsed.isNotEmpty()) {
                    Text(stringResource(R.string.routes_clear_waypoints))
                }
            }
            OutlinedTextField(title, { title = it.take(80) }, label = { Text(stringResource(R.string.routes_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(notes, { notes = it.take(500) }, label = { Text(stringResource(R.string.routes_notes)) }, modifier = Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                enabled = !saving && title.isNotBlank() && parsed.size >= 2,
                modifier = Modifier.fillMaxWidth(),
                onClick = { scope.launch {
                    saving = true
                    val routeId = editingRouteId ?: UUID.randomUUID().toString()
                    val existing = selected?.route?.takeIf { it.id == editingRouteId }
                    val savedPoints = parsed.take(GpxRouteCodec.MAX_POINTS).mapIndexed { index, p -> SavedRoutePointEntity(routeId, index, p.latitude, p.longitude, p.altitudeMeters, p.segmentIndex) }
                    runCatching {
                        container.savedRouteRepository.saveRoute(SavedRouteEntity(routeId, title.trim(), notes.trim().ifBlank { null }, sport, GpxRouteCodec.distanceMeters(parsed), existing?.sourceActivityId, existing?.createdAtEpochMillis ?: System.currentTimeMillis(), System.currentTimeMillis()), savedPoints)
                    }.onSuccess { selectedId = routeId; showEditor = false; editingRouteId = null; hasLocationStart = false; error = null }
                        .onFailure { error = it.message ?: saveFailedMessage }
                    saving = false
                } },
            ) { Text(stringResource(R.string.routes_save)) }
        }
        return
    }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.routes_title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
            TextButton(onClick = onBack) { Text(stringResource(R.string.insights_done)) }
        }
        if (selected == null) {
            Text(stringResource(R.string.routes_offline_notice), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { title = ""; notes = ""; pointText = ""; editingRouteId = null; hasLocationStart = false; sport = "RUNNING"; error = null; showEditor = true }) { Text(stringResource(R.string.routes_create)) }
            }
            OutlinedButton(onClick = { showActivityPicker = true }) { Text(stringResource(R.string.routes_save_activity)) }
            if (routes.isEmpty()) Text(stringResource(R.string.routes_empty))
            routes.forEach { route ->
                Card(Modifier.fillMaxWidth().clickable { selectedId = route.route.id }) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(route.route.title, style = MaterialTheme.typography.titleMedium)
                        Text(formatDistance(route.route.distanceMeters, preferences.units == com.pace.app.PaceUnits.Imperial), style = MaterialTheme.typography.bodySmall)
                        Text(stringResource(R.string.routes_points_count, route.points.size), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        } else {
            val route = selected.route
            TextButton(onClick = { selectedId = null }) { Text(stringResource(R.string.routes_back_to_list)) }
            Text(route.title, style = MaterialTheme.typography.titleLarge)
            Text(formatDistance(route.distanceMeters, preferences.units == com.pace.app.PaceUnits.Imperial))
            route.notes?.takeIf(String::isNotBlank)?.let { Text(it) }
            RouteMap(selected.points, dark, Modifier.fillMaxWidth().height(280.dp))
            selected.points.mapNotNull { it.altitudeMeters }.takeIf { it.isNotEmpty() }?.let { Text(stringResource(R.string.routes_elevation_summary, it.minOrNull()!!.toInt(), it.maxOrNull()!!.toInt())) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onStartRecordingRoute(selected) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(PaceAppIcons.Play, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Record this route", style = MaterialTheme.typography.titleSmall)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    val hasLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    if (hasLocation) followingRouteId = route.id else {
                        pendingFollowId = route.id
                        permissionRequest.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                    }
                }) { Text(stringResource(R.string.routes_follow)) }
                OutlinedButton(onClick = { title = route.title; notes = route.notes.orEmpty(); sport = route.sport; editingRouteId = route.id; hasLocationStart = false; pointText = ManualWaypointCodec.format(selected.points.map { GpxPoint(it.latitude, it.longitude, it.altitudeMeters, it.segmentIndex) }); error = null; showEditor = true }) { Text(stringResource(R.string.routes_edit)) }
                OutlinedButton(onClick = {
                    exportPayload = GpxRouteCodec.write(route.title, selected.points.map { GpxPoint(it.latitude, it.longitude, it.altitudeMeters, it.segmentIndex) })
                    exportPicker.launch(route.title.replace(Regex("[^A-Za-z0-9_-]"), "_") + ".gpx")
                }) { Text(stringResource(R.string.routes_export)) }
                OutlinedButton(onClick = {
                    scope.launch {
                        runCatching {
                            val file = withContext(Dispatchers.IO) {
                                val directory = java.io.File(context.cacheDir, "activity-exports/routes").apply { mkdirs() }
                                java.io.File(directory, route.id.filter { it.isLetterOrDigit() || it == '-' || it == '_' } + ".gpx").apply {
                                    writeBytes(GpxRouteCodec.write(route.title, selected.points.map { GpxPoint(it.latitude, it.longitude, it.altitudeMeters, it.segmentIndex) }))
                                }
                            }
                            val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
                            val send = Intent(Intent.ACTION_SEND).setType("application/gpx+xml").putExtra(Intent.EXTRA_STREAM, uri)
                            send.clipData = ClipData.newUri(context.contentResolver, route.title, uri)
                            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            context.startActivity(Intent.createChooser(send, shareTitle))
                        }.onFailure { error = it.message ?: exportFailedMessage }
                    }
                }) { Text(stringResource(R.string.routes_share)) }
                OutlinedButton(onClick = { confirmDelete = true }) { Text(stringResource(R.string.routes_delete)) }
            }
            Text(stringResource(R.string.routes_follow_deferred), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }

    if (showActivityPicker) AlertDialog(
        onDismissRequest = { showActivityPicker = false },
        title = { Text(stringResource(R.string.routes_pick_activity)) },
        text = { Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
            activities.filter { ActivityMetrics.isEligibleSummary(it) }.take(60).forEach { activity ->
                Text((activity.title ?: activity.sport) + " · " + formatDistance(activity.distanceMeters.toDouble(), preferences.units == com.pace.app.PaceUnits.Imperial), Modifier.fillMaxWidth().clickable {
                    scope.launch {
                        val detail = container.activityRepository.getActivity(activity.id)
                        val track = detail?.trackPoints.orEmpty().sortedBy { it.sequence }
                        if (track.size < 2) error = activityNoTrackMessage
                        else {
                            val routeId = UUID.randomUUID().toString()
                            val converted = track.mapIndexed { index, point -> SavedRoutePointEntity(routeId, index, point.latitude, point.longitude, point.altitudeMeters, point.segmentIndex) }
                            val distance = ActivityMetrics.track(track).distanceMeters
                            container.savedRouteRepository.saveRoute(SavedRouteEntity(routeId, activity.title?.ifBlank { null } ?: activity.sport, activity.notes, activity.sport, distance, activity.id, System.currentTimeMillis(), System.currentTimeMillis()), converted)
                            showActivityPicker = false
                            selectedId = routeId
                        }
                    }
                }.padding(vertical = 8.dp))
            }
        } },
        confirmButton = { TextButton({ showActivityPicker = false }) { Text(stringResource(R.string.insights_done)) } },
    )
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text(stringResource(R.string.routes_delete_title)) },
        text = { Text(stringResource(R.string.routes_delete_body, selected?.route?.title.orEmpty())) },
        confirmButton = { TextButton(onClick = { val id = selectedId; confirmDelete = false; if (id != null) scope.launch { container.savedRouteRepository.deleteRoute(id, System.currentTimeMillis()); selectedId = null } }) { Text(stringResource(R.string.routes_delete)) } },
        dismissButton = { TextButton({ confirmDelete = false }) { Text(stringResource(R.string.insights_done)) } },
    )
    followingRouteId?.let { id ->
        val following = routes.firstOrNull { it.route.id == id }
        Dialog(onDismissRequest = { followingRouteId = null }) {
            Surface(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.routes_follow), style = MaterialTheme.typography.titleLarge)
                        TextButton({ followingRouteId = null }) { Text(stringResource(R.string.insights_done)) }
                    }
                    if (following == null) Text(stringResource(R.string.routes_follow_unavailable)) else {
                        Text(following.route.title, style = MaterialTheme.typography.titleMedium)
                        val routeGpx = following.points.map { GpxPoint(it.latitude, it.longitude, it.altitudeMeters, it.segmentIndex) }
                        val deviation = currentSample?.let { RouteMetrics.distanceToRouteMeters(it.latitude, it.longitude, routeGpx) }
                        val summaryDistance = formatDistance(following.route.distanceMeters, false)
                        val finalDeviation = deviation?.toInt()?.toString() ?: notAvailableMessage
                        val completionSummary = stringResource(R.string.routes_completed_summary, following.route.title, summaryDistance, finalDeviation)
                        when {
                            currentSample != null && deviation != null && RouteMetrics.isOffRoute(deviation) -> Text(stringResource(R.string.routes_off_route_warning, deviation.toInt()), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleMedium)
                            currentSample != null && deviation != null -> Text(stringResource(R.string.routes_on_route_distance, deviation.toInt()), color = MaterialTheme.colorScheme.primary)
                            locationUnavailable -> Text(stringResource(R.string.routes_location_unavailable), color = MaterialTheme.colorScheme.error)
                            else -> Text(stringResource(R.string.routes_waiting_for_location), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        val currentPoint = currentSample?.let { sample ->
                            com.pace.app.data.local.entity.TrackPointEntity("route-follow-current", 0, sample.timestampEpochMillis, sample.latitude, sample.longitude, sample.altitudeMeters, sample.accuracyMeters, sample.speedMetersPerSecond, sample.bearingDegrees)
                        }
                        RouteFollowMap(following.points, currentPoint, dark, Modifier.fillMaxWidth().height(380.dp))
                        Text(stringResource(R.string.routes_follow_disclaimer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onClick = {
                            completedRouteSummary = completionSummary
                            followingRouteId = null
                        }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.routes_finish_follow)) }
                    }
                }
            }
        }
    }
    completedRouteSummary?.let { summary -> AlertDialog(
        onDismissRequest = { completedRouteSummary = null },
        title = { Text(stringResource(R.string.routes_completed_title)) },
        text = { Text(summary) },
        confirmButton = { TextButton({ completedRouteSummary = null }) { Text(stringResource(R.string.insights_done)) } },
    ) }
}

@Composable private fun RouteMap(points: List<SavedRoutePointEntity>, dark: Boolean, modifier: Modifier) {
    val track = remember(points) { points.mapIndexed { index, point ->
        com.pace.app.data.local.entity.TrackPointEntity("route-preview", index, index * 1_000L, point.latitude, point.longitude, point.altitudeMeters, segmentIndex = point.segmentIndex)
    } }
    PaceMapPanel(darkTheme = dark, isOnline = false, routePoints = track, fitRoute = true, modifier = modifier)
}

private fun formatDistance(meters: Double, imperial: Boolean): String = if (imperial) "%.1f mi".format(Locale.getDefault(), meters / ActivityMetrics.METERS_PER_MILE) else "%.1f km".format(Locale.getDefault(), meters / 1000.0)

@Composable private fun RouteFollowMap(points: List<SavedRoutePointEntity>, current: com.pace.app.data.local.entity.TrackPointEntity?, dark: Boolean, modifier: Modifier) {
    val track = remember(points) { points.mapIndexed { index, point -> com.pace.app.data.local.entity.TrackPointEntity("route-follow", index, index * 1_000L, point.latitude, point.longitude, point.altitudeMeters, segmentIndex = point.segmentIndex) }
    }
    PaceMapPanel(darkTheme = dark, isOnline = false, routePoints = track, highlightedTrackPoint = current, fitRoute = true, modifier = modifier)
}
