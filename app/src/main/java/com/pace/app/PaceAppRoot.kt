package com.pace.app

import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.net.Uri
import android.provider.Settings
import android.Manifest
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.activity.compose.BackHandler
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import com.pace.app.ui.components.PaceSecondaryButton
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.semantics.Role
import com.pace.app.ui.components.PaceSetupArtwork
import com.pace.app.ui.components.PaceSetupIllustration
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import com.pace.app.auth.AuthMessage
import com.pace.app.auth.PaceAccountState
import com.pace.app.auth.PaceAuthRepository
import com.pace.app.auth.isValidNewPassword
import com.pace.app.auth.normalizeEmail
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.material3.Icon
import androidx.compose.ui.semantics.heading
import com.pace.app.ui.components.PaceBottomNavigation
import com.pace.app.ui.components.PaceCalendarStatus
import com.pace.app.ui.components.PaceEmptyState
import com.pace.app.ui.components.PaceNavigationDestination
import com.pace.app.ui.components.PaceOfflineBanner
import com.pace.app.ui.components.PacePrimaryButton
import com.pace.app.ui.components.PaceSegment
import com.pace.app.ui.components.PaceSegmentedControl
import com.pace.app.ui.components.PaceSport
import com.pace.app.ui.components.PaceSportSelectorTile
import com.pace.app.ui.maps.PaceMapPanel
import com.pace.app.ui.maps.PaceOfflineMapPackControls
import com.pace.app.ui.components.PaceTextAction
import com.pace.app.ui.components.PaceTextField
import com.pace.app.ui.theme.PaceTheme
import com.pace.app.R
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import com.pace.app.data.local.entity.ActivityEntity
import org.json.JSONObject
import com.pace.app.data.local.LocalDataExporter
import com.pace.app.data.local.OwnerDataEraser
import com.pace.app.data.sync.PaceRestoreScheduler
import com.pace.app.data.sync.PaceRestoreWorker
import com.pace.app.data.sync.SupabaseBackupClient
import com.pace.app.data.sync.PaceAccountDataLinker
import com.pace.app.data.sync.PaceAccountDeletionClient
import com.pace.app.data.sync.PaceAiGatewayClient
import com.pace.app.data.sync.PaceSyncScheduler
import com.pace.app.tracking.RecordingService
import java.util.UUID
import java.time.LocalDate
import java.util.Date
import java.text.DateFormat
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first

private const val HOME = "home"
private const val RECORD = "record"
private const val MAPS = "maps"
private const val PLAN = "plan"
private const val INSIGHTS = "insights"
private const val PROFILE = "profile"
private const val PLAN_DESTINATION_ENABLED = true

@Composable
fun PaceAppRoot(
    container: PaceAppContainer,
    forceHome: Boolean = false,
    forceOnlineForTest: Boolean? = null,
) {
    val preferences by container.settingsRepository.preferences.collectAsState(initial = null)
    val currentPreferences = preferences
    val scope = rememberCoroutineScope()
    val updatePreferences: (PacePreferences) -> Unit = { updated -> scope.launch { container.settingsRepository.save(updated) } }
    val accountState by container.authRepository.accountState.collectAsState()
    val darkTheme = false
    val online = rememberPaceConnectivity(forceOnlineForTest)

    PaceTheme(darkTheme = darkTheme, highContrast = currentPreferences?.highContrast ?: true, reducedMotion = currentPreferences?.reducedMotion == true) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
                if (currentPreferences == null) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {}
                } else if (accountState is PaceAccountState.Loading) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {}
                } else if (currentPreferences.backupOwnerUserId != null && accountState is PaceAccountState.SignedIn &&
                    (accountState as PaceAccountState.SignedIn).userId != currentPreferences.backupOwnerUserId
                ) {
                    PaceAccountMismatchScreen(authRepository = container.authRepository)
                } else if (currentPreferences.onboardingComplete) {
                    PaceShell(
                        container = container,
                        preferences = currentPreferences,
                        authRepository = container.authRepository,
                        onPreferencesChanged = updatePreferences,
                        forceHome = forceHome,
                        isOnline = online,
                    )
                } else {
                    PaceOnboarding(preferences = currentPreferences, isOnline = online, onComplete = updatePreferences)
                }
            }
        }
    }
}

@Composable
private fun PaceAccountMismatchScreen(authRepository: PaceAuthRepository) {
    val scope = rememberCoroutineScope()
    var signingOut by remember { mutableStateOf(false) }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(stringResource(R.string.pace_account_data_locked_title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
            Text(stringResource(R.string.pace_account_data_locked_body), style = MaterialTheme.typography.bodyLarge)
            PacePrimaryButton(
                label = if (signingOut) stringResource(R.string.pace_account_loading) else stringResource(R.string.pace_account_sign_out),
                onClick = {
                    if (!signingOut) scope.launch {
                        signingOut = true
                        authRepository.signOut()
                        signingOut = false
                    }
                },
                enabled = !signingOut,
            )
        }
    }
}

@Composable
private fun PaceOnboarding(
    preferences: PacePreferences,
    isOnline: Boolean,
    onComplete: (PacePreferences) -> Unit,
) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    androidx.activity.compose.BackHandler(enabled = page > 0) { page-- }
    var draftUnits by rememberSaveable { mutableStateOf(preferences.units.name) }
    var draftSports by rememberSaveable { mutableStateOf(ArrayList(preferences.preferredSports.map { it.name })) }
    val draft = preferences.copy(
        units = PaceUnits.valueOf(draftUnits),
        preferredSports = draftSports.map(PaceSport::valueOf).toSet(),
    )
    val titles = listOf(
        stringResource(R.string.pace_onboarding_welcome_title),
        stringResource(R.string.pace_onboarding_sports_title),
        stringResource(R.string.pace_onboarding_units_title),
    )
    val descriptions = listOf(
        stringResource(R.string.pace_onboarding_welcome_body),
        stringResource(R.string.pace_onboarding_sports_body),
        stringResource(R.string.pace_onboarding_units_body),
    )
    val finishSetup = {
        onComplete(draft.copy(onboardingComplete = true))
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 520.dp).fillMaxSize()) {
                if (!isOnline) {
                    PaceOfflineBanner(
                        stringResource(R.string.pace_offline_status),
                        Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    )
                }
                val motion = !preferences.reducedMotion && android.animation.ValueAnimator.areAnimatorsEnabled()
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        stringResource(R.string.pace_onboarding_step, page + 1, titles.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { (page + 1f) / titles.size },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    AnimatedContent(
                        targetState = page,
                        transitionSpec = { fadeIn(tween(if (motion) 180 else 0)) togetherWith fadeOut(tween(if (motion) 100 else 0)) },
                        label = "onboarding step",
                    ) { step ->
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            PaceSetupIllustration(PaceSetupArtwork.entries[step])
                            Text(
                                titles[step],
                                style = if (step == 0) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineMedium,
                                modifier = Modifier.semantics { heading() },
                            )
                            Text(descriptions[step], style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            when (step) {
                                1 -> OnboardingSportChoices(draft = draft, onDraft = { next ->
                                    draftSports = ArrayList(next.preferredSports.map { it.name })
                                })
                                2 -> OnboardingUnitChoices(draft = draft, onDraft = { draftUnits = it.units.name })
                            }
                        }
                    }
                }
                Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
                    val isLastPage = page == titles.lastIndex
                    PacePrimaryButton(
                        label = stringResource(if (isLastPage) R.string.pace_action_get_started else R.string.pace_action_continue),
                        onClick = { if (isLastPage) finishSetup() else page++ },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        if (page > 0) {
                            PaceTextAction(stringResource(R.string.pace_action_back), onClick = { page-- })
                        } else {
                            Spacer(Modifier.weight(1f))
                        }
                        PaceTextAction(stringResource(R.string.pace_action_skip), onClick = finishSetup)
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardingSportChoices(draft: PacePreferences, onDraft: (PacePreferences) -> Unit) {
    val sports = sportOptions()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        sports.forEach { (sport, label) ->
            com.pace.app.ui.components.PaceSportSelectorTile(
                sport = sport,
                modifier = Modifier.fillMaxWidth(),
                selected = sport in draft.preferredSports,
                selectionRole = Role.Checkbox,
                onClick = {
                    val next = draft.preferredSports.toMutableSet().apply {
                        if (sport in this && size > 1) remove(sport) else add(sport)
                    }
                    onDraft(draft.copy(preferredSports = next))
                },
                label = label,
            )
        }
    }
}

@Composable
private fun OnboardingUnitChoices(draft: PacePreferences, onDraft: (PacePreferences) -> Unit) {
    PaceSegmentedControl(
        options = listOf(
            PaceSegment(PaceUnits.Metric, stringResource(R.string.pace_unit_metric)),
            PaceSegment(PaceUnits.Imperial, stringResource(R.string.pace_unit_imperial)),
        ),
        selected = draft.units,
        onSelected = { onDraft(draft.copy(units = it)) },
    )
}

@Composable
private fun PaceShell(
    container: PaceAppContainer,
    preferences: PacePreferences,
    authRepository: PaceAuthRepository,
    onPreferencesChanged: (PacePreferences) -> Unit,
    forceHome: Boolean,
    isOnline: Boolean,
) {
    var tab by rememberSaveable { mutableStateOf(if (forceHome) HOME else safeTab(preferences.lastSelectedTab)) }
    var profileOpen by rememberSaveable { mutableStateOf(false) }
    var initialRecordSport by rememberSaveable { mutableStateOf<PaceSport?>(null) }
    var routePlanRequested by rememberSaveable { mutableStateOf(false) }
    var activeGuideRoute by remember { mutableStateOf<com.pace.app.data.local.dao.SavedRouteWithPoints?>(null) }
    BackHandler(enabled = profileOpen) { profileOpen = false }
    BackHandler(enabled = tab == "routes") { tab = MAPS }
    BackHandler(enabled = tab == "predictions") { tab = INSIGHTS }
    BackHandler(enabled = !profileOpen && tab in setOf("workouts", "history")) { tab = HOME }
    val destinations = paceDestinations()
    val shownDestinations = if (PLAN_DESTINATION_ENABLED) destinations else destinations.filterNot { it.route == PLAN }
    val selectedRoute = if (profileOpen) PROFILE else if (tab == "routes") MAPS else if (tab == "predictions") INSIGHTS else tab
    val selectTab: (String) -> Unit = { selected ->
        if (selected == "routes-plan") {
            routePlanRequested = true
            tab = "routes"
            profileOpen = false
        } else if (selected == "routes") {
            tab = "routes"
            profileOpen = false
        } else if (selected == "history") {
            tab = selected
            profileOpen = false
        } else if (selected == "workouts") {
            tab = selected
            profileOpen = false
        } else if (selected == "predictions") {
            tab = selected
            profileOpen = false
        } else if (shownDestinations.any { it.route == selected && it.enabled }) {
            tab = selected
            profileOpen = false
            onPreferencesChanged(preferences.copy(lastSelectedTab = selected))
        }
    }
    val startRecordingRoute: (com.pace.app.data.local.dao.SavedRouteWithPoints) -> Unit = { routeWithPoints ->
        activeGuideRoute = routeWithPoints
        initialRecordSport = paceSportFromName(routeWithPoints.route.sport)
        selectTab(RECORD)
    }
    val openProfile = {
        profileOpen = true
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            if (maxWidth < 600.dp) {
                Column(Modifier.fillMaxSize()) {
                    if (!isOnline) PaceOfflineBanner(stringResource(R.string.pace_offline_status), Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                    Box(Modifier.weight(1f)) {
                        PaceDestinationContent(
                            route = if (profileOpen) PROFILE else tab,
                            container = container,
                            preferences = preferences,
                            authRepository = authRepository,
                            onPreferencesChanged = onPreferencesChanged,
                            onSelectTab = selectTab,
                            onOpenProfile = openProfile,
                            onCloseProfile = { profileOpen = false },
                            isOnline = isOnline,
                            initialRecordSport = initialRecordSport,
                            onInitialSportApplied = { initialRecordSport = null },
                            onStartPlannedWorkout = { sport ->
                                initialRecordSport = paceSportFromName(sport)
                                selectTab(RECORD)
                            },
                            routePlanRequested = routePlanRequested,
                            onRoutePlanHandled = { routePlanRequested = false },
                            activeGuideRoute = activeGuideRoute,
                            onClearGuideRoute = { activeGuideRoute = null },
                            onStartRecordingRoute = startRecordingRoute,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    if (!profileOpen) {
                        PaceBottomNavigation(
                            destinations = shownDestinations,
                            selectedRoute = selectedRoute,
                            onSelect = selectTab,
                        )
                    }
                }
            } else {
                Row(Modifier.fillMaxSize()) {
                    NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
                        shownDestinations.forEach { destination ->
                            NavigationRailItem(
                                selected = selectedRoute == destination.route,
                                onClick = { selectTab(destination.route) },
                                enabled = destination.enabled,
                                icon = { Icon(if (selectedRoute == destination.route) destination.selectedIcon else destination.unselectedIcon, contentDescription = null) },
                                label = { Text(destination.label) },
                                modifier = Modifier.semantics { contentDescription = destination.label },
                            )
                        }
                        if (!profileOpen) {
                            NavigationRailItem(
                                selected = false,
                                onClick = openProfile,
                                icon = { Icon(PaceAppIcons.Profile, contentDescription = null) },
                                label = { Text(stringResource(R.string.pace_profile_action)) },
                            )
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        if (!isOnline) PaceOfflineBanner(stringResource(R.string.pace_offline_status), Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
                        PaceDestinationContent(
                            route = if (profileOpen) PROFILE else tab,
                            container = container,
                            preferences = preferences,
                            authRepository = authRepository,
                            onPreferencesChanged = onPreferencesChanged,
                            onSelectTab = selectTab,
                            onOpenProfile = openProfile,
                            onCloseProfile = { profileOpen = false },
                            isOnline = isOnline,
                            initialRecordSport = initialRecordSport,
                            onInitialSportApplied = { initialRecordSport = null },
                            onStartPlannedWorkout = { sport ->
                                initialRecordSport = paceSportFromName(sport)
                                selectTab(RECORD)
                            },
                            routePlanRequested = routePlanRequested,
                            onRoutePlanHandled = { routePlanRequested = false },
                            activeGuideRoute = activeGuideRoute,
                            onClearGuideRoute = { activeGuideRoute = null },
                            onStartRecordingRoute = startRecordingRoute,
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PaceDestinationContent(
    route: String,
    container: PaceAppContainer,
    preferences: PacePreferences,
    authRepository: PaceAuthRepository,
    onPreferencesChanged: (PacePreferences) -> Unit,
    onSelectTab: (String) -> Unit,
    onOpenProfile: () -> Unit,
    onCloseProfile: () -> Unit,
    isOnline: Boolean,
    initialRecordSport: PaceSport?,
    onInitialSportApplied: () -> Unit,
    onStartPlannedWorkout: (String) -> Unit,
    routePlanRequested: Boolean = false,
    onRoutePlanHandled: () -> Unit = {},
    activeGuideRoute: com.pace.app.data.local.dao.SavedRouteWithPoints? = null,
    onClearGuideRoute: () -> Unit = {},
    onStartRecordingRoute: (com.pace.app.data.local.dao.SavedRouteWithPoints) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    when (route) {
        HOME -> PaceHomeScreen(container, preferences, onSelectTab, onOpenProfile, modifier, onHistory = { onSelectTab("history") }, onStartPlannedWorkout = onStartPlannedWorkout)
        "workouts" -> com.pace.app.ui.workouts.PaceHomeWorkoutLibrary(
            activityRepository = container.activityRepository,
            draftRepository = container.homeWorkoutDraftRepository,
            ownerId = container.currentOwnerId(),
            onBack = { onSelectTab(HOME) },
            modifier = modifier,
        )
        "history" -> com.pace.app.ui.history.PaceHistoryScreen(container.activityRepository, preferences, onBack = { onSelectTab(HOME) }, onRecord = { onSelectTab(RECORD) }, modifier = modifier)
        RECORD -> com.pace.app.ui.record.PaceRecordFlow(
            preferences = preferences,
            onPreferencesChanged = onPreferencesChanged,
            onSelectTab = onSelectTab,
            isOnline = isOnline,
            initialSport = initialRecordSport,
            onInitialSportApplied = onInitialSportApplied,
            activeGuideRoute = activeGuideRoute,
            onClearGuideRoute = onClearGuideRoute,
            modifier = modifier,
        )
        MAPS -> PaceMapsScreen(container, preferences, isOnline, onSelectTab, onStartRecordingRoute = onStartRecordingRoute, modifier = modifier)
        "routes" -> com.pace.app.ui.routes.PaceSavedRoutesScreen(container, preferences, onBack = { onSelectTab(MAPS) }, modifier = modifier, startWithEditor = routePlanRequested, onStartHandled = onRoutePlanHandled, onStartRecordingRoute = onStartRecordingRoute, isOnline = isOnline)
        INSIGHTS -> PaceInsightsDashboard(container, preferences, onPreferencesChanged, onSelectTab, modifier)
        PROFILE -> PaceProfileScreen(container, preferences, authRepository, onPreferencesChanged, onCloseProfile, onOpenAi = { onSelectTab(INSIGHTS) }, modifier = modifier)
        PLAN -> if (PLAN_DESTINATION_ENABLED) com.pace.app.ui.planning.PacePlanScreen(container, onStartWorkout = onStartPlannedWorkout, modifier = modifier) else PaceHomeScreen(container, preferences, onSelectTab, onOpenProfile, modifier)
        "predictions" -> com.pace.app.ui.recommendations.PacePredictionsScreen(container, preferences, onBack = { onSelectTab(INSIGHTS) }, modifier = modifier)
        else -> PaceHomeScreen(container, preferences, onSelectTab, onOpenProfile, modifier)
    }
}

@Composable
private fun PaceHomeScreen(
    container: PaceAppContainer,
    preferences: PacePreferences,
    onSelectTab: (String) -> Unit,
    onOpenProfile: () -> Unit,
    modifier: Modifier = Modifier,
    onHistory: () -> Unit = {},
    onStartPlannedWorkout: (String) -> Unit = {},
) {
    val ownerId = container.currentOwnerId()
    val history = remember(container.activityRepository, ownerId) { container.activityRepository.observeHistory(ownerId) }
    val savedActivities by history.collectAsState(initial = emptyList(), context = kotlinx.coroutines.Dispatchers.IO)
    val recent = savedActivities.firstOrNull { it.status == "SAVED" && it.deletedAtEpochMillis == null && it.ownerId == ownerId }
    val today = remember { LocalDate.now() }
    val upcomingFlow = remember(container.workoutRepository) { container.workoutRepository.observeCalendar(today.toString(), today.plusDays(31).toString()) }
    val upcoming by upcomingFlow.collectAsState(initial = emptyList())
    val templates by container.workoutRepository.observeTemplates().collectAsState(initial = emptyList())
    val nextSession = upcoming.firstOrNull { it.status == "PLANNED" }
    Column(modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.pace_home_today), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
            androidx.compose.material3.FilledTonalIconButton(onClick = onOpenProfile) {
                Icon(PaceAppIcons.Profile, contentDescription = stringResource(R.string.pace_profile_action))
            }
        }
        val imperial = preferences.units == PaceUnits.Imperial
        val divisor = if (imperial) 1609.344 else 1000.0
        val distanceUnit = if (imperial) "mi" else "km"
        val firstDay = today.minusDays((today.dayOfWeek.value - 1).toLong())
        val zone = java.time.ZoneId.systemDefault()
        val weekStart = firstDay.atStartOfDay(zone).toInstant().toEpochMilli()
        val weekEnd = firstDay.plusDays(7).atStartOfDay(zone).toInstant().toEpochMilli()
        val weekly = savedActivities.filter {
            it.ownerId == ownerId && it.startedAtEpochMillis in weekStart until weekEnd &&
                com.pace.app.domain.metrics.ActivityMetrics.isEligibleSummary(it)
        }
        var selectedDay by remember(firstDay) { mutableStateOf<java.time.DayOfWeek?>(null) }
        val weekStepsFlow = remember(firstDay, container) {
            container.database.dailyStepsDao().observeRange(firstDay.toString(), firstDay.plusDays(6).toString())
        }
        val weekSteps by weekStepsFlow.collectAsState(initial = emptyList())
        val daily = weekly.groupBy { java.time.Instant.ofEpochMilli(it.startedAtEpochMillis).atZone(zone).dayOfWeek }
            .mapValues { (_, activities) -> activities.sumOf { it.distanceMeters } / divisor }
        Card(
            Modifier.fillMaxWidth(),
            colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.pace_stitch_this_week), style = MaterialTheme.typography.labelLarge)
                Text(
                    stringResource(R.string.pace_stitch_distance, weekly.sumOf { it.distanceMeters } / divisor, distanceUnit),
                    style = MaterialTheme.typography.displayMedium,
                )
                val maxDistance = daily.values.maxOrNull()?.coerceAtLeast(0.01) ?: 1.0
                val animate = !com.pace.app.ui.theme.LocalReducedMotion.current && android.animation.ValueAnimator.areAnimatorsEnabled()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    java.time.DayOfWeek.entries.forEach { day ->
                        val distance = daily[day]?.coerceAtLeast(0.0) ?: 0.0
                        val dayName = day.getDisplayName(java.time.format.TextStyle.NARROW, java.util.Locale.getDefault())
                        val barHeight by androidx.compose.animation.core.animateDpAsState(
                            targetValue = (4 + 60 * distance / maxDistance).toFloat().dp,
                            animationSpec = if (animate) tween(240) else androidx.compose.animation.core.snap(),
                            label = "Weekly distance",
                        )
                        val daySummary = stringResource(
                            R.string.pace_home_day_distance,
                            day.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault()),
                            stringResource(R.string.pace_stitch_distance, distance, distanceUnit),
                        )
                        Column(
                            Modifier.weight(1f).clickable { selectedDay = day }.semantics(mergeDescendants = true) { contentDescription = daySummary },
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(Modifier.height(68.dp), contentAlignment = Alignment.BottomCenter) {
                                Surface(
                                    modifier = Modifier.fillMaxWidth().height(barHeight),
                                    shape = MaterialTheme.shapes.extraSmall,
                                    color = when {
                                        selectedDay == day -> MaterialTheme.colorScheme.tertiary
                                        distance > 0 -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.outlineVariant
                                    },
                                ) {}
                            }
                            Text(dayName, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                selectedDay?.let { day ->
                    val date = firstDay.plusDays((day.value - 1).toLong())
                    val steps = weekSteps.firstOrNull { it.day == date.toString() }?.count ?: 0L
                    Text(stringResource(
                        R.string.pace_home_day_totals,
                        stringResource(R.string.pace_stitch_distance, daily[day] ?: 0.0, distanceUnit),
                        java.text.NumberFormat.getIntegerInstance().format(steps),
                    ), style = MaterialTheme.typography.titleSmall)
                }
            }
        }
        Text(stringResource(R.string.pace_stitch_quick_start), style = MaterialTheme.typography.titleMedium)
        sportOptions().chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { (sport, label) ->
                    com.pace.app.ui.components.PaceSportSelectorTile(
                        sport = sport,
                        label = label,
                        selected = preferences.lastUsedSport == sport,
                        onClick = { onStartPlannedWorkout(sport.name) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        Card(onClick = { onSelectTab("workouts") }, modifier = Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PaceSettingsGlyph(PaceAppIcons.Play)
                Text(stringResource(R.string.pace_home_workouts_action), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Icon(PaceAppIcons.Back, null, modifier = Modifier.graphicsLayer { rotationZ = 180f })
            }
        }
        com.pace.app.tracking.steps.PaceDailyStepsCard(container.database.dailyStepsDao(), preferences.units == PaceUnits.Imperial)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.pace_stitch_recent), style = MaterialTheme.typography.titleMedium)
            PaceTextAction(stringResource(R.string.pace_history_title), onClick = onHistory)
        }
        recent?.let { activity ->
            val sportEnum = paceSportFromName(activity.sport)
            Card(
                onClick = onHistory,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Surface(
                                shape = androidx.compose.foundation.shape.CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(36.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    com.pace.app.ui.components.PaceSportGlyph(
                                        sport = sportEnum,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                            Column {
                                Text(activity.title?.takeIf(String::isNotBlank) ?: activity.sport, style = MaterialTheme.typography.titleMedium)
                                val date = java.time.Instant.ofEpochMilli(activity.startedAtEpochMillis).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                                Text(date.format(java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Icon(PaceAppIcons.Back, null, modifier = Modifier.graphicsLayer { rotationZ = 180f })
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                    ) {
                        Column {
                            Text(stringResource(R.string.pace_record_distance), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(stringResource(R.string.pace_stitch_distance, activity.distanceMeters / divisor, distanceUnit), style = MaterialTheme.typography.titleLarge)
                        }
                        Column {
                            Text(stringResource(R.string.pace_record_elapsed), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            val minutes = activity.movingSeconds / 60
                            val seconds = activity.movingSeconds % 60
                            Text("%d:%02d".format(minutes, seconds), style = MaterialTheme.typography.titleLarge)
                        }
                    }
                }
            }
        }
        nextSession?.let { session ->
            val workout = templates.firstOrNull { it.template.id == session.templateId }?.template
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.pace_plan_upcoming_title), style = MaterialTheme.typography.titleMedium)
                    Text("${workout?.name ?: stringResource(R.string.pace_plan_session_missing)} · ${session.scheduledDate}", style = MaterialTheme.typography.bodyMedium)
                    PacePrimaryButton(
                        label = stringResource(R.string.pace_plan_start),
                        onClick = { onStartPlannedWorkout(workout?.sport ?: "Running") },
                        icon = PaceAppIcons.Play,
                    )
                }
            }
        }
        if (PLAN_DESTINATION_ENABLED) {
            com.pace.app.ui.components.PaceSecondaryButton(
                label = stringResource(R.string.pace_home_open_workout_plan),
                onClick = { onSelectTab(PLAN) },
                icon = PaceAppIcons.Plan,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun PaceMapsScreen(
    container: PaceAppContainer,
    preferences: PacePreferences,
    isOnline: Boolean,
    onSelectTab: (String) -> Unit,
    onStartRecordingRoute: (com.pace.app.data.local.dao.SavedRouteWithPoints) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var mapCamera by remember { mutableStateOf<org.maplibre.android.camera.CameraPosition?>(null) }
    var location by remember { mutableStateOf<org.maplibre.android.geometry.LatLng?>(null) }
    var locationJumpKey by remember { mutableStateOf(0) }
    var locating by remember { mutableStateOf(false) }
    var locationError by remember { mutableStateOf(false) }
    var destination by remember { mutableStateOf<org.maplibre.android.geometry.LatLng?>(null) }
    var routeName by remember { mutableStateOf("Route to Destination") }
    var routeSport by remember { mutableStateOf("Running") }
    var routeSavedFeedback by remember { mutableStateOf<String?>(null) }
    val token = remember { com.google.android.gms.tasks.CancellationTokenSource() }
    androidx.compose.runtime.DisposableEffect(token) { onDispose { token.cancel() } }
    fun locate() {
        locating = true
        locationError = false
        try {
            com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(context)
                .getCurrentLocation(com.google.android.gms.location.Priority.PRIORITY_BALANCED_POWER_ACCURACY, token.token)
                .addOnCompleteListener { task ->
                    locating = false
                    val fix = if (task.isSuccessful) task.result else null
                    if (fix == null) locationError = true
                    else {
                        location = org.maplibre.android.geometry.LatLng(fix.latitude, fix.longitude)
                        locationJumpKey++
                    }
                }
        } catch (_: SecurityException) {
            locating = false
            locationError = true
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (it.values.any { granted -> granted }) locate() else locationError = true
    }
    Column(modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.pace_map_title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f).semantics { heading() })
            PaceSecondaryButton(
                label = stringResource(if (locating) R.string.pace_map_locating else R.string.pace_map_current_location),
                enabled = !locating,
                icon = PaceAppIcons.Maps,
                onClick = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    ) locate() else permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                },
            )
        }
        if (locationError) Text(stringResource(R.string.pace_map_location_unavailable), color = MaterialTheme.colorScheme.error)
        PaceMapPanel(
            darkTheme = false,
            isOnline = isOnline,
            reducedMotion = preferences.reducedMotion,
            modifier = Modifier.weight(1.35f).fillMaxWidth(),
            onCameraIdle = { mapCamera = it },
            locationJump = location,
            locationJumpKey = locationJumpKey,
            onMapTap = { lat, lon -> destination = org.maplibre.android.geometry.LatLng(lat, lon) },
            selection = destination,
        )
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            destination?.let { dest ->
                val startLat = location?.latitude ?: mapCamera?.target?.latitude ?: 0.0
                val startLon = location?.longitude ?: mapCamera?.target?.longitude ?: 0.0
                val distMeters = com.pace.app.tracking.haversineMeters(startLat, startLon, dest.latitude, dest.longitude)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Save route to destination", style = MaterialTheme.typography.titleMedium)
                            TextButton(onClick = { destination = null }) { Text(stringResource(R.string.pace_cancel)) }
                        }
                        Text(
                            "%.2f km from your position (tap map to change destination)".format(distMeters / 1000.0),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedTextField(
                            value = routeName,
                            onValueChange = { routeName = it.take(60) },
                            label = { Text("Route title") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Running", "Cycling", "Walking").forEach { sportOption ->
                                FilterChip(
                                    selected = routeSport == sportOption,
                                    onClick = { routeSport = sportOption },
                                    label = { Text(sportOption) },
                                )
                            }
                        }
                        fun buildAndSaveRoute(): Pair<com.pace.app.data.local.entity.SavedRouteEntity, List<com.pace.app.data.local.entity.SavedRoutePointEntity>> {
                            val routeId = UUID.randomUUID().toString()
                            val now = System.currentTimeMillis()
                            val route = com.pace.app.data.local.entity.SavedRouteEntity(
                                id = routeId,
                                title = routeName.trim().ifBlank { "Route to destination" },
                                sport = routeSport,
                                distanceMeters = distMeters,
                                createdAtEpochMillis = now,
                                updatedAtEpochMillis = now,
                                ownerId = container.currentOwnerId(),
                            )
                            val points = listOf(
                                com.pace.app.data.local.entity.SavedRoutePointEntity(routeId, 0, startLat, startLon),
                                com.pace.app.data.local.entity.SavedRoutePointEntity(routeId, 1, dest.latitude, dest.longitude),
                            )
                            return route to points
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PacePrimaryButton(
                                label = "Record this route",
                                onClick = {
                                    scope.launch {
                                        val (route, points) = buildAndSaveRoute()
                                        container.savedRouteRepository.saveRoute(route, points)
                                        destination = null
                                        onStartRecordingRoute(com.pace.app.data.local.dao.SavedRouteWithPoints(route, points))
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                icon = PaceAppIcons.Play,
                            )
                            com.pace.app.ui.components.PaceSecondaryButton(
                                label = "Save route",
                                onClick = {
                                    scope.launch {
                                        val (route, points) = buildAndSaveRoute()
                                        container.savedRouteRepository.saveRoute(route, points)
                                        destination = null
                                        routeSavedFeedback = "Route saved!"
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                icon = PaceAppIcons.Plan,
                            )
                        }
                    }
                }
            }
            routeSavedFeedback?.let {
                Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
            }
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PacePrimaryButton(
                    label = stringResource(R.string.pace_map_plan_route),
                    onClick = { onSelectTab("routes-plan") },
                    modifier = Modifier.fillMaxWidth(),
                    icon = PaceAppIcons.Plan,
                )
                PaceSecondaryButton(
                    label = stringResource(R.string.pace_map_saved_routes_open),
                    onClick = { onSelectTab("routes") },
                    modifier = Modifier.fillMaxWidth(),
                    icon = PaceAppIcons.Maps,
                )
            }
            Card(Modifier.fillMaxWidth(), colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                PaceOfflineMapPackControls(container, isOnline, Modifier.fillMaxWidth().padding(16.dp), previewCamera = mapCamera)
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun PaceProfileScreen(
    container: PaceAppContainer,
    preferences: PacePreferences,
    authRepository: PaceAuthRepository,
    onPreferencesChanged: (PacePreferences) -> Unit,
    onBack: () -> Unit,
    onOpenAi: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by rememberSaveable { mutableStateOf(preferences.profileName) }
    var saved by rememberSaveable { mutableStateOf(false) }
    var exportMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var showEraseDialog by rememberSaveable { mutableStateOf(false) }
    var showPrivacyPolicy by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val accountState by authRepository.accountState.collectAsState()
    val exportCancelledText = stringResource(R.string.pace_privacy_export_cancelled)
    val exportDoneText = stringResource(R.string.pace_privacy_export_done)
    val exportFailedText = stringResource(R.string.pace_privacy_export_failed)
    val eraseDoneText = stringResource(R.string.pace_privacy_erase_done)
    val eraseFailedText = stringResource(R.string.pace_privacy_erase_failed)
    val eraseRecordingText = stringResource(R.string.pace_privacy_erase_active_recording)
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) {
            exportMessage = exportCancelledText
        } else {
            scope.launch {
                exportMessage = runCatching {
                    val signedIn = accountState as? PaceAccountState.SignedIn
                    val aiUsage = if (signedIn != null && authRepository.isConfigured) {
                        authRepository.accessTokenOrNull()?.let { token ->
                            runCatching { PaceAiGatewayClient(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_PUBLISHABLE_KEY).exportUsage(token) }.getOrNull()
                        }
                    } else null
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use { output ->
                            LocalDataExporter.write(
                                container.database.openHelper.writableDatabase,
                                output,
                                settings = org.json.JSONObject()
                                    .put("profile_name", preferences.profileName)
                                    .put("units", preferences.units.name)
                                    .put("theme", preferences.theme.name)
                                    .put("preferred_sports", org.json.JSONArray(preferences.preferredSports.map { it.name }))
                                    .put("last_used_sport", preferences.lastUsedSport.name)
                                    .put("ai_opt_in", preferences.aiEnabled)
                                    .put("cached_ai_kind", preferences.cachedAiKind ?: org.json.JSONObject.NULL)
                                    .put("cached_ai_response", preferences.cachedAiResponse ?: org.json.JSONObject.NULL)
                                    .put("cloud_ai_usage_exported", aiUsage != null),
                                ownerId = container.currentOwnerId(),
                                cloudAiUsage = aiUsage ?: org.json.JSONArray(),
                            )
                        } ?: error("Export destination is unavailable.")
                    }
                    exportDoneText
                }.getOrElse { exportFailedText }
            }
        }
    }
    var confirmAiConsent by rememberSaveable { mutableStateOf(false) }
    var aiBusy by remember { mutableStateOf(false) }
    var aiStatus by rememberSaveable { mutableStateOf<String?>(null) }
    val aiConsentFailedText = stringResource(R.string.pace_ai_consent_failed)
    val aiEnabledText = stringResource(R.string.pace_ai_enabled)
    val aiDisabledText = stringResource(R.string.pace_ai_disabled)
    val sports = sportOptions()
    Column(modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        var accountOpen by rememberSaveable { mutableStateOf(false) }
        var restoreOpen by rememberSaveable { mutableStateOf(false) }
        fun closeAccountPage() {
            if (restoreOpen) restoreOpen = false else accountOpen = false
        }
        BackHandler(enabled = accountOpen, onBack = ::closeAccountPage)
        if (accountOpen) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = ::closeAccountPage) {
                    Icon(PaceAppIcons.Back, contentDescription = stringResource(R.string.pace_action_back))
                }
                Text(stringResource(if (restoreOpen) R.string.pace_backup_restore_action else R.string.pace_account_page_title), style = MaterialTheme.typography.headlineMedium)
            }
            if (restoreOpen) PaceRestoreCard(container, preferences, authRepository, onPreferencesChanged)
            else PaceAccountCard(container, authRepository, onRestore = { restoreOpen = true })
            return@Column
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconButton(onClick = onBack) { Icon(PaceAppIcons.Back, stringResource(R.string.pace_action_back)) }
            Text(stringResource(R.string.pace_profile_title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
        }
        PacePrimaryButton(
            label = stringResource(if (accountState is PaceAccountState.SignedIn) R.string.pace_account_page_title else R.string.pace_account_sign_in),
            onClick = { accountOpen = true },
            modifier = Modifier.fillMaxWidth(),
            icon = PaceAppIcons.Profile,
        )
        if (accountState is PaceAccountState.SignedIn) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(PaceAppIcons.Spark, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(stringResource(R.string.pace_ai_title), style = MaterialTheme.typography.titleMedium)
                    }
                    when (val state = accountState) {
                        is PaceAccountState.SignedIn -> {
                            if (BuildConfig.SUPABASE_URL.isBlank() || BuildConfig.SUPABASE_PUBLISHABLE_KEY.isBlank()) {
                                Text(stringResource(R.string.pace_ai_not_configured), style = MaterialTheme.typography.bodySmall)
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.pace_ai_enable), modifier = Modifier.weight(1f))
                                Switch(
                                    checked = preferences.aiEnabled && preferences.aiOwnerUserId == state.userId,
                                    enabled = !aiBusy,
                                    onCheckedChange = { enabled ->
                                        aiStatus = null
                                        if (enabled) confirmAiConsent = true
                                        else {
                                            onPreferencesChanged(preferences.copy(aiEnabled = false, cachedAiResponse = null, cachedAiKind = null, cachedAiOwnerUserId = null))
                                            scope.launch {
                                                aiBusy = true
                                                val token = authRepository.accessTokenOrNull()
                                                val result = runCatching { if (token != null) PaceAiGatewayClient(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_PUBLISHABLE_KEY).setConsent(token, false) else error("No session") }
                                                aiStatus = if (result.isSuccess) aiDisabledText else aiConsentFailedText
                                                aiBusy = false
                                            }
                                        }
                                    },
                                )
                            }
                        }
                    }
                    PaceAccountState.LocalOnly, PaceAccountState.SignedOut, PaceAccountState.NeedsReauthentication -> Text(stringResource(R.string.pace_ai_need_account), style = MaterialTheme.typography.bodySmall)
                    PaceAccountState.Loading -> Unit
                }
                aiStatus?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (preferences.aiEnabled && preferences.aiOwnerUserId == (accountState as? PaceAccountState.SignedIn)?.userId) {
                    PacePrimaryButton(
                        label = stringResource(R.string.pace_ai_open),
                        onClick = onOpenAi,
                        modifier = Modifier.fillMaxWidth(),
                        icon = PaceAppIcons.Spark,
                    )
                }
            }
        }
        }
        PaceTextField(
            value = name,
            onValueChange = { name = it.take(60); saved = false },
            label = stringResource(R.string.pace_profile_name_label),
            modifier = Modifier.fillMaxWidth(),
        )
        PaceSecondaryButton(
            label = stringResource(R.string.pace_profile_save),
            onClick = {
                onPreferencesChanged(preferences.copy(profileName = name.trim()))
                saved = true
            },
            enabled = name.trim() != preferences.profileName,
        )
        if (saved) Text(stringResource(R.string.pace_profile_saved), color = MaterialTheme.colorScheme.primary)
        if (showEraseDialog) {
            AlertDialog(
                onDismissRequest = { showEraseDialog = false },
                icon = { Icon(PaceAppIcons.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                title = { Text(stringResource(R.string.pace_privacy_erase_title)) },
                text = { Text(stringResource(R.string.pace_privacy_erase_confirm)) },
                confirmButton = {
                    TextButton(onClick = {
                        showEraseDialog = false
                        scope.launch {
                            exportMessage = runCatching {
                                val recordingActive = withContext(Dispatchers.IO) {
                                    container.database.activityDao().countActiveRecordingsAcrossOwners() > 0
                                }
                                if (recordingActive) eraseRecordingText
                                else {
                                    withContext(Dispatchers.IO) {
                                        PaceRestoreScheduler.cancelAll(context).result.get()
                                        PaceSyncScheduler.cancel(context)
                                        container.database.clearAllTables()
                                    }
                                    eraseDoneText
                                }
                            }.getOrElse {
                                eraseFailedText
                            }
                            if (exportMessage == eraseDoneText) onPreferencesChanged(preferences.copy(backupOwnerUserId = null, backupEnabled = false, lastSuccessfulSyncEpochMillis = null, aiEnabled = false, aiOwnerUserId = null, cachedAiResponse = null, cachedAiKind = null, cachedAiOwnerUserId = null))
                        }
                    }) { Text(stringResource(R.string.pace_privacy_erase)) }
                },
                dismissButton = { TextButton(onClick = { showEraseDialog = false }) { Text(stringResource(R.string.pace_action_back)) } },
            )
        }
        if (showPrivacyPolicy) {
            AlertDialog(
                onDismissRequest = { showPrivacyPolicy = false },
                title = { Text(stringResource(R.string.pace_privacy_policy_title)) },
                text = { Text(stringResource(R.string.pace_privacy_policy_body)) },
                confirmButton = { TextButton(onClick = { showPrivacyPolicy = false }) { Text(stringResource(R.string.pace_action_back)) } },
            )
        }
        if (confirmAiConsent) {
            AlertDialog(
                onDismissRequest = { if (!aiBusy) confirmAiConsent = false },
                icon = { Icon(PaceAppIcons.Spark, contentDescription = null) },
                title = { Text(stringResource(R.string.pace_ai_consent_title)) },
                text = {
                    Text(
                        stringResource(R.string.pace_ai_consent_body),
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                    )
                },
                confirmButton = {
                    TextButton(enabled = !aiBusy, onClick = {
                        val signedIn = accountState as? PaceAccountState.SignedIn ?: return@TextButton
                        val token = authRepository.accessTokenOrNull()
                        confirmAiConsent = false
                        scope.launch {
                            aiBusy = true
                            val result = runCatching { if (token != null) PaceAiGatewayClient(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_PUBLISHABLE_KEY).setConsent(token, true) else error("No session") }
                            if (result.isSuccess) {
                                onPreferencesChanged(preferences.copy(aiEnabled = true, aiOwnerUserId = signedIn.userId))
                                aiStatus = aiEnabledText
                            } else aiStatus = aiConsentFailedText
                            aiBusy = false
                        }
                    }) { Text(stringResource(R.string.pace_ai_enable)) }
                },
                dismissButton = { TextButton(enabled = !aiBusy, onClick = { confirmAiConsent = false }) { Text(stringResource(R.string.pace_action_back)) } },
            )
        }

        Text(stringResource(R.string.pace_settings_units), style = MaterialTheme.typography.titleMedium)
        PaceSegmentedControl(
            options = listOf(
                PaceSegment(PaceUnits.Metric, stringResource(R.string.pace_unit_metric)),
                PaceSegment(PaceUnits.Imperial, stringResource(R.string.pace_unit_imperial)),
            ),
            selected = preferences.units,
            onSelected = { onPreferencesChanged(preferences.copy(units = it)) },
        )

        Card(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.pace_settings_notifications), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.pace_settings_notifications_description), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = preferences.notificationsEnabled, onCheckedChange = { enabled ->
                    onPreferencesChanged(preferences.copy(notificationsEnabled = enabled))
                    if (enabled) scope.launch {
                        withContext(Dispatchers.IO) {
                            container.database.workoutDao().remindersToSchedule(container.currentOwnerId())
                                .forEach { com.pace.app.ui.planning.PlanReminderScheduler.schedule(context, it) }
                        }
                    } else com.pace.app.ui.planning.PlanReminderScheduler.cancelAll(context)
                })
            }
        }
        var permissionsOpen by rememberSaveable { mutableStateOf(false) }
        PaceSecondaryButton(
            stringResource(R.string.pace_settings_permissions),
            onClick = { permissionsOpen = true },
            modifier = Modifier.fillMaxWidth(),
            icon = PaceAppIcons.Lock,
        )
        if (permissionsOpen) AlertDialog(
            onDismissRequest = { permissionsOpen = false },
            title = { Text(stringResource(R.string.pace_settings_permissions)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PaceLocationDiagnosticsCard()
                    Text(stringResource(
                        if (androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled())
                            R.string.pace_permission_notifications_allowed else R.string.pace_permission_notifications_blocked,
                    ))
                }
            },
            confirmButton = { TextButton(onClick = { permissionsOpen = false }) { Text(stringResource(R.string.pace_action_back)) } },
        )
        val mapPacks by container.mapPackRepository.observeMapPacks().collectAsState(initial = emptyList())
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.pace_settings_map_storage), style = MaterialTheme.typography.titleSmall)
                if (mapPacks.isEmpty()) Text(stringResource(R.string.pace_settings_map_storage_empty), style = MaterialTheme.typography.bodySmall)
                else Text(stringResource(R.string.pace_settings_map_storage_summary, mapPacks.size, android.text.format.Formatter.formatShortFileSize(context, mapPacks.sumOf { it.downloadedBytes })), style = MaterialTheme.typography.bodySmall)
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.pace_settings_high_contrast), modifier = Modifier.weight(1f))
                Switch(checked = preferences.highContrast, onCheckedChange = { onPreferencesChanged(preferences.copy(highContrast = it)) })
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.pace_settings_reduced_motion), modifier = Modifier.weight(1f))
                Switch(checked = preferences.reducedMotion, onCheckedChange = { onPreferencesChanged(preferences.copy(reducedMotion = it)) })
            }
        }

        Text(stringResource(R.string.pace_settings_sports), style = MaterialTheme.typography.titleMedium)
        sports.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { (sport, label) ->
                    PaceSportSelectorTile(
                        sport = sport,
                        label = label,
                        selected = sport in preferences.preferredSports,
                        onClick = {
                            val next = preferences.preferredSports.toMutableSet()
                            if (sport !in next) next.add(sport) else if (next.size > 1) next.remove(sport)
                            onPreferencesChanged(preferences.copy(preferredSports = next))
                        },
                        modifier = Modifier.weight(1f),
                        selectionRole = androidx.compose.ui.semantics.Role.Checkbox,
                    )
                }
            }
        }
        HorizontalDivider()
        Text(stringResource(R.string.pace_privacy_title), style = MaterialTheme.typography.titleMedium)
        PaceTextAction(stringResource(R.string.pace_privacy_policy_action), onClick = { showPrivacyPolicy = true })
        PaceTextAction(stringResource(R.string.pace_privacy_export), onClick = {
            exportMessage = null
            exportLauncher.launch("pace-data-export.json")
        })
        exportMessage?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        PaceTextAction(stringResource(R.string.pace_privacy_erase), onClick = { showEraseDialog = true })
    }
}

@Composable
private fun PaceLocationDiagnosticsCard() {
    val context = LocalContext.current
    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    val gpsEnabled = runCatching {
        context.getSystemService(LocationManager::class.java)?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true
    }.getOrDefault(false)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.pace_settings_location_diagnostics), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(if (granted) R.string.pace_settings_location_permission_granted else R.string.pace_settings_location_permission_missing), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(if (gpsEnabled) R.string.pace_settings_gps_enabled else R.string.pace_settings_gps_disabled), style = MaterialTheme.typography.bodySmall)
            if (!granted) PaceTextAction(stringResource(R.string.pace_settings_open_permissions), onClick = {
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            })
        }
    }
}

@Composable
private fun PaceRestoreCard(
    container: PaceAppContainer,
    preferences: PacePreferences,
    authRepository: PaceAuthRepository,
    onPreferencesChanged: (PacePreferences) -> Unit,
) {
    val account by authRepository.accountState.collectAsState()
    val backupContext = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirmRestore by rememberSaveable { mutableStateOf(false) }
    var confirmBackup by rememberSaveable { mutableStateOf(false) }
    var queueingRestore by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var backupPreview by remember { mutableStateOf<String?>(null) }
    var previewBusy by remember { mutableStateOf(false) }
    val readyText = stringResource(R.string.pace_backup_restore_done)
    val errorText = stringResource(R.string.pace_backup_restore_failed)
    val progressText = stringResource(R.string.pace_backup_restore_progress)
    val linkRecordingText = stringResource(R.string.pace_backup_link_recording)
    val linkFailedText = stringResource(R.string.pace_backup_link_failed)
    val linkDonePattern = stringResource(R.string.pace_backup_link_done)
    val restoreCancelledText = stringResource(R.string.pace_backup_restore_cancelled)
    val previewReadyText = stringResource(R.string.pace_backup_preview_result)
    val previewFailedText = stringResource(R.string.pace_backup_preview_failed)
    val quotaNotConfiguredText = stringResource(R.string.pace_backup_quota_not_configured)
    val quotaUsageText = stringResource(R.string.pace_backup_quota_usage)
    val quotaNearText = stringResource(R.string.pace_backup_quota_near)
    val quotaOverText = stringResource(R.string.pace_backup_quota_over)

    if (authRepository.isConfigured && account is PaceAccountState.SignedIn) {
        val signedIn = account as PaceAccountState.SignedIn
        val matchingOwner = preferences.backupOwnerUserId == null || preferences.backupOwnerUserId == signedIn.userId
        val restoreWorkName = remember(signedIn.userId) { PaceRestoreScheduler.workName(signedIn.userId) }
        val restoreWorkFlow = remember(backupContext, restoreWorkName) {
            WorkManager.getInstance(backupContext).getWorkInfosForUniqueWorkFlow(restoreWorkName)
        }
        val restoreWorkInfos by restoreWorkFlow.collectAsState(initial = emptyList())
        val restoreWork = restoreWorkInfos.firstOrNull()
        val restoring = queueingRestore || restoreWork?.state in setOf(WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING, WorkInfo.State.BLOCKED)
        val queuedFlow = remember(signedIn.userId) { container.database.syncOperationDao().observeQueuedCount(signedIn.userId) }
        val errorFlow = remember(signedIn.userId) { container.database.syncOperationDao().observeLastErrorCode(signedIn.userId) }
        val queued by queuedFlow.collectAsState(initial = 0)
        val lastError by errorFlow.collectAsState(initial = null)
        val requestSync: () -> Unit = {
            scope.launch {
                container.syncOperationRepository.retryOutstandingNow(signedIn.userId, System.currentTimeMillis())
                PaceSyncScheduler.enqueueNow(backupContext, preferences.allowMeteredBackup)
            }
        }
        LaunchedEffect(restoreWork?.id, restoreWork?.state) {
            val work = restoreWork ?: return@LaunchedEffect
            when (work.state) {
                WorkInfo.State.SUCCEEDED -> message = readyText.format(
                    work.outputData.getInt(PaceRestoreWorker.KEY_ROWS_APPLIED, 0),
                    work.outputData.getInt(PaceRestoreWorker.KEY_TRACKS_RESTORED, 0),
                )
                WorkInfo.State.FAILED -> message = errorText
                WorkInfo.State.CANCELLED -> message = restoreCancelledText
                else -> Unit
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.pace_backup_restore_description), style = MaterialTheme.typography.bodyMedium)
                if (!matchingOwner) {
                    Text(stringResource(R.string.pace_backup_other_account), color = MaterialTheme.colorScheme.error)
                } else {
                    PaceTextAction(
                        if (previewBusy) stringResource(R.string.pace_backup_preview_loading)
                        else stringResource(R.string.pace_backup_preview_action),
                        enabled = !previewBusy && !restoring,
                        onClick = {
                            val accessToken = authRepository.accessTokenOrNull()
                            if (accessToken == null) {
                                backupPreview = previewFailedText
                            } else {
                                previewBusy = true
                                backupPreview = null
                                scope.launch {
                                    try {
                                        require(BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_PUBLISHABLE_KEY.isNotBlank())
                                        val result = SupabaseBackupClient(
                                            BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_PUBLISHABLE_KEY,
                                        ).backupPreview(accessToken)
                                        val previewDetails = previewReadyText.format(
                                            result.optLong("metadata_bytes_estimate"),
                                            result.optLong("storage_used_bytes", result.optLong("track_archive_bytes")),
                                            result.optLong("activity_count"),
                                            result.optLong("route_count"),
                                        )
                                        val usedBytes = result.optLong("storage_used_bytes", result.optLong("track_archive_bytes"))
                                        val quotaValue = result.opt("storage_quota_bytes")
                                        val quotaDetails = when {
                                            quotaValue == null || quotaValue == JSONObject.NULL -> quotaNotConfiguredText.format(usedBytes)
                                            result.optBoolean("storage_over_quota") -> quotaOverText.format(usedBytes, (quotaValue as Number).toLong())
                                            result.optBoolean("storage_quota_warning") -> quotaNearText.format(usedBytes, (quotaValue as Number).toLong())
                                            else -> quotaUsageText.format(usedBytes, (quotaValue as Number).toLong())
                                        }
                                        backupPreview = "$previewDetails\n$quotaDetails"
                                    } catch (_: Exception) {
                                        backupPreview = previewFailedText
                                    } finally {
                                        previewBusy = false
                                    }
                                }
                            }
                        },
                    )
                    backupPreview?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    PaceTextAction(stringResource(R.string.pace_backup_link_existing), onClick = { confirmBackup = true })
                    if (preferences.backupEnabled && preferences.backupOwnerUserId == signedIn.userId) {
                        Text(stringResource(R.string.pace_backup_queue, queued), style = MaterialTheme.typography.bodySmall)
                        val syncDate = preferences.lastSuccessfulSyncEpochMillis?.let { DateFormat.getDateTimeInstance().format(Date(it)) }
                        Text(syncDate?.let { stringResource(R.string.pace_backup_last_sync, it) } ?: stringResource(R.string.pace_backup_never_synced), style = MaterialTheme.typography.bodySmall)
                    lastError?.let {
                        if (it == "BACKUP_QUOTA_EXCEEDED") {
                            Text(stringResource(R.string.pace_backup_quota_exceeded), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        } else {
                            Text(stringResource(R.string.pace_backup_last_error, it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = preferences.allowMeteredBackup, onCheckedChange = { allow ->
                                onPreferencesChanged(preferences.copy(allowMeteredBackup = allow))
                                PaceSyncScheduler.cancel(backupContext)
                                PaceSyncScheduler.enqueue(backupContext, allow)
                            })
                            Text(stringResource(R.string.pace_backup_metered), style = MaterialTheme.typography.bodyMedium)
                        }
                        PaceTextAction(stringResource(R.string.pace_backup_sync_now), onClick = requestSync)
                    } else {
                        Text(stringResource(R.string.pace_backup_sync_paused), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    PacePrimaryButton(
                        label = if (restoring) stringResource(R.string.pace_backup_restoring) else stringResource(R.string.pace_backup_restore_action),
                        onClick = { if (!restoring) confirmRestore = true },
                        enabled = !restoring,
                    )
                    if (restoring) {
                        val workProgress = restoreWork?.progress
                        val table = workProgress?.getString(PaceRestoreWorker.KEY_TABLE)
                        if (table != null) Text(
                            progressText.format(
                                table,
                                workProgress.getInt(PaceRestoreWorker.KEY_ROWS_READ, 0),
                                workProgress.getInt(PaceRestoreWorker.KEY_ROWS_APPLIED, 0),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        TextButton(onClick = { PaceRestoreScheduler.cancel(backupContext, signedIn.userId) }) { Text(stringResource(R.string.pace_backup_restore_cancel)) }
                    }
                    message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }

    if (confirmBackup) {
        AlertDialog(
            onDismissRequest = { confirmBackup = false },
            icon = { Icon(PaceAppIcons.Cloud, contentDescription = null) },
            title = { Text(stringResource(R.string.pace_backup_link_existing)) },
            text = { Text(stringResource(R.string.pace_account_link_notice)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmBackup = false
                    val signedIn = account as? PaceAccountState.SignedIn ?: return@TextButton
                    scope.launch {
                        val result = runCatching {
                            withContext(Dispatchers.IO) {
                                PaceAccountDataLinker(container.database).linkLocalData(signedIn.userId)
                            }
                        }
                        result.onSuccess { report ->
                            onPreferencesChanged(preferences.copy(backupOwnerUserId = signedIn.userId, backupEnabled = true))
                            PaceSyncScheduler.enqueue(backupContext, preferences.allowMeteredBackup)
                            message = linkDonePattern.format(report.activities, report.plannedSessions, report.goals, report.personalRecords, report.savedRoutes)
                        }.onFailure { failure -> message = if (failure.message == "active recording") linkRecordingText else linkFailedText }
                    }
                }) { Text(stringResource(R.string.pace_backup_link_existing)) }
            },
            dismissButton = { TextButton(onClick = { confirmBackup = false }) { Text(stringResource(R.string.pace_action_back)) } },
        )
    }

    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            icon = { Icon(PaceAppIcons.Cloud, contentDescription = null) },
            title = { Text(stringResource(R.string.pace_backup_restore_confirm_title)) },
            text = { Text(stringResource(R.string.pace_backup_restore_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmRestore = false
                    val signedIn = account as? PaceAccountState.SignedIn ?: return@TextButton
                    queueingRestore = true
                    message = null
                    scope.launch {
                        try {
                            container.settingsRepository.save(preferences.copy(backupOwnerUserId = signedIn.userId))
                            PaceRestoreScheduler.enqueue(backupContext, signedIn.userId)
                        } catch (_: Exception) {
                            message = errorText
                        } finally {
                            queueingRestore = false
                        }
                    }
                }) { Text(stringResource(R.string.pace_backup_restore_action)) }
            },
            dismissButton = { TextButton(onClick = { confirmRestore = false }) { Text(stringResource(R.string.pace_action_back)) } },
        )
    }
}

@Composable
private fun PaceAccountCard(container: PaceAppContainer, authRepository: PaceAuthRepository, onRestore: () -> Unit) {
    val accountState by authRepository.accountState.collectAsState()
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var createAccount by rememberSaveable { mutableStateOf(false) }
    var forgotPassword by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<AuthMessage?>(null) }
    var signOutBlocked by remember { mutableStateOf(false) }
    var confirmAccountDeletion by rememberSaveable { mutableStateOf(false) }
    var deletingAccount by remember { mutableStateOf(false) }
    var deletionMessage by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val emailValue = normalizeEmail(email)
    val signOutRecordingText = stringResource(R.string.pace_account_signout_recording)
    val deletionDoneText = stringResource(R.string.pace_account_delete_done)
    val deletionFailedText = stringResource(R.string.pace_account_delete_failed)
    val deletionLocalFailedText = stringResource(R.string.pace_account_delete_local_failed)
    val deletionRecordingText = stringResource(R.string.pace_account_delete_active_recording)
    val deletionProgressText = stringResource(R.string.pace_account_delete_progress)

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!authRepository.isConfigured) {
                Text(stringResource(R.string.pace_account_not_configured), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.pace_account_local_mode), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                when (val state = accountState) {
                    PaceAccountState.LocalOnly -> Text(stringResource(R.string.pace_account_local_mode), style = MaterialTheme.typography.bodyMedium)
                    PaceAccountState.Loading -> Text(stringResource(R.string.pace_account_loading), style = MaterialTheme.typography.bodyMedium)
                    is PaceAccountState.SignedIn -> {
                        Text(
                            if (state.email.isNullOrBlank()) stringResource(R.string.pace_account_signed_in_no_email)
                            else stringResource(R.string.pace_account_signed_in, state.email),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it.take(128); message = null },
                            label = { Text(stringResource(R.string.pace_account_new_password_short), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
                            placeholder = { Text(stringResource(R.string.pace_account_new_password_short), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
                            visualTransformation = if (passwordVisible) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                        Icon(
                                            if (passwordVisible) PaceAppIcons.EyeOff else PaceAppIcons.Eye,
                                            contentDescription = stringResource(if (passwordVisible) R.string.pace_hide_password else R.string.pace_show_password),
                                        )
                                    }
                                },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        PacePrimaryButton(
                            label = stringResource(R.string.pace_account_update_password),
                            onClick = {
                                scope.launch {
                                    busy = true
                                    val result = if (isValidNewPassword(password)) authRepository.updatePassword(password)
                                    else com.pace.app.auth.AuthActionResult(AuthMessage.InvalidInput, false)
                                    message = result.message
                                    password = ""
                                    busy = false
                                }
                            },
                            enabled = !busy && password.isNotEmpty(),
                        )
                        PaceTextAction(
                            label = stringResource(R.string.pace_account_sign_out),
                            onClick = {
                                scope.launch {
                                    busy = true
                                    val hasRecording = withContext(Dispatchers.IO) {
                                        container.database.activityDao().getActiveRecording(container.currentOwnerId()) != null
                                    }
                                    if (hasRecording) {
                                        signOutBlocked = true
                                    } else {
                                        val userId = (accountState as? PaceAccountState.SignedIn)?.userId
                                        if (userId != null) PaceRestoreScheduler.cancel(context, userId)
                                        val result = authRepository.signOut()
                                        message = result.message
                                    }
                                    busy = false
                                }
                            },
                        )
                    }
                    PaceAccountState.SignedOut, PaceAccountState.NeedsReauthentication -> {
                        if (state == PaceAccountState.NeedsReauthentication) {
                            Text(stringResource(R.string.pace_account_reauthenticate), style = MaterialTheme.typography.bodyMedium)
                        }
                        if (!forgotPassword) {
                            PaceSegmentedControl(
                                options = listOf(
                                    PaceSegment(false, stringResource(R.string.pace_account_sign_in)),
                                    PaceSegment(true, stringResource(R.string.pace_account_create)),
                                ),
                                selected = createAccount,
                                onSelected = { createAccount = it; passwordVisible = false; message = null },
                            )
                        }
                        if (!forgotPassword) Text(stringResource(R.string.pace_account_backup_notice), style = MaterialTheme.typography.bodySmall)
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it.take(254); message = null },
                            label = { Text(stringResource(R.string.pace_account_email)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (!forgotPassword) {
                            OutlinedTextField(
                                value = password,
                                onValueChange = { password = it.take(128); message = null },
                                label = { Text(stringResource(R.string.pace_account_password)) },
                                visualTransformation = if (passwordVisible) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                        Icon(
                                            if (passwordVisible) PaceAppIcons.EyeOff else PaceAppIcons.Eye,
                                            contentDescription = stringResource(if (passwordVisible) R.string.pace_hide_password else R.string.pace_show_password),
                                        )
                                    }
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        PacePrimaryButton(
                            label = stringResource(
                                when {
                                    forgotPassword -> R.string.pace_account_send_reset
                                    createAccount -> R.string.pace_account_create
                                    else -> R.string.pace_account_sign_in
                                },
                            ),
                            onClick = {
                                scope.launch {
                                    busy = true
                                    message = when {
                                        emailValue == null -> AuthMessage.InvalidInput
                                        forgotPassword -> authRepository.sendPasswordReset(emailValue).let { it.message }
                                        createAccount && !isValidNewPassword(password) -> AuthMessage.InvalidInput
                                        createAccount -> authRepository.register(emailValue, password).let { it.message }
                                        else -> authRepository.signIn(emailValue, password).let { it.message }
                                    }
                                    busy = false
                                }
                            },
                            enabled = !busy && emailValue != null && (forgotPassword || password.isNotEmpty()),
                        )
                        PaceTextAction(
                            label = stringResource(if (forgotPassword) R.string.pace_account_back_to_sign_in else R.string.pace_account_forgot_password),
                            onClick = { forgotPassword = !forgotPassword; message = null },
                        )
                        if (!forgotPassword) {
                            com.pace.app.ui.components.PaceSecondaryButton(
                                label = stringResource(if (createAccount) R.string.pace_account_sign_in else R.string.pace_account_sign_up),
                                onClick = { createAccount = !createAccount; passwordVisible = false; message = null },
                                icon = PaceAppIcons.Profile,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
                if (accountState is PaceAccountState.SignedIn) {
                    HorizontalDivider()
                    TextButton(onClick = { confirmAccountDeletion = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(PaceAppIcons.Delete, null, tint = MaterialTheme.colorScheme.error)
                        Text(stringResource(R.string.pace_account_delete), color = MaterialTheme.colorScheme.error)
                    }
                    com.pace.app.ui.components.PaceSecondaryButton(
                        label = stringResource(R.string.pace_backup_restore_action),
                        onClick = onRestore,
                        icon = PaceAppIcons.Cloud,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }

    val authFeedback = when (message) {
        AuthMessage.CheckEmail -> R.string.pace_account_check_email
        AuthMessage.ResetEmailSent -> R.string.pace_account_reset_sent
        AuthMessage.PasswordUpdated -> R.string.pace_account_password_updated
        AuthMessage.SignedOut -> R.string.pace_account_signed_out
        AuthMessage.SignedIn -> R.string.pace_account_signed_in_notice
        AuthMessage.InvalidInput -> R.string.pace_account_input_error
        AuthMessage.AccountUnavailable -> R.string.pace_account_not_configured
        AuthMessage.NetworkOrServiceError -> R.string.pace_account_service_error
        AuthMessage.EmailUnconfirmed -> R.string.pace_account_email_unconfirmed
        AuthMessage.InvalidCredentials -> R.string.pace_account_invalid_credentials
        AuthMessage.RateLimited -> R.string.pace_account_rate_limited
        AuthMessage.EmailUnavailable -> R.string.pace_account_email_unavailable
        null -> null
    }
    LaunchedEffect(authFeedback) {
        if (authFeedback != null) {
            android.widget.Toast.makeText(context, authFeedback, android.widget.Toast.LENGTH_LONG).show()
            message = null
        }
    }
    LaunchedEffect(signOutBlocked) {
        if (signOutBlocked) {
            android.widget.Toast.makeText(context, signOutRecordingText, android.widget.Toast.LENGTH_LONG).show()
            signOutBlocked = false
        }
    }
    LaunchedEffect(deletionMessage) {
        deletionMessage?.let { android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_LONG).show() }
    }

    if (confirmAccountDeletion) {
        AlertDialog(
            onDismissRequest = { if (!deletingAccount) confirmAccountDeletion = false },
            icon = {
                Icon(PaceAppIcons.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            },
            title = { Text(stringResource(R.string.pace_account_delete_title)) },
            text = { Text(stringResource(R.string.pace_account_delete_body)) },
            confirmButton = {
                TextButton(enabled = !deletingAccount, onClick = {
                    val signedIn = accountState as? PaceAccountState.SignedIn ?: return@TextButton
                    val token = authRepository.accessTokenOrNull()
                    confirmAccountDeletion = false
                    if (token == null) {
                        deletionMessage = deletionFailedText
                    } else {
                        scope.launch {
                            deletingAccount = true
                            deletionMessage = deletionProgressText
                            val recording = withContext(Dispatchers.IO) { container.database.activityDao().getActiveRecording(signedIn.userId) != null }
                            if (recording) {
                                deletionMessage = deletionRecordingText
                            } else {
                                val current = container.settingsRepository.preferences.first()
                                container.settingsRepository.save(current.copy(backupEnabled = false))
                                PaceSyncScheduler.cancel(context)
                                PaceRestoreScheduler.cancel(context, signedIn.userId)
                                val cloudDeleted = runCatching {
                                    PaceAccountDeletionClient(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_PUBLISHABLE_KEY).deleteAccount(token)
                                }.isSuccess
                                if (!cloudDeleted) {
                                    deletionMessage = deletionFailedText
                                } else {
                                    val localDeleted = runCatching { OwnerDataEraser(container.database).erase(signedIn.userId) }.isSuccess
                                    if (!localDeleted) {
                                        deletionMessage = deletionLocalFailedText
                                    } else {
                                        container.settingsRepository.save(current.copy(backupOwnerUserId = null, backupEnabled = false, lastSuccessfulSyncEpochMillis = null, aiEnabled = false, aiOwnerUserId = null, cachedAiResponse = current.cachedAiResponse.takeUnless { current.cachedAiOwnerUserId == signedIn.userId }, cachedAiKind = current.cachedAiKind.takeUnless { current.cachedAiOwnerUserId == signedIn.userId }, cachedAiOwnerUserId = current.cachedAiOwnerUserId.takeUnless { it == signedIn.userId }))
                                        authRepository.signOut()
                                        deletionMessage = deletionDoneText
                                    }
                                }
                            }
                            deletingAccount = false
                        }
                    }
                }) { Text(stringResource(R.string.pace_account_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(enabled = !deletingAccount, onClick = { confirmAccountDeletion = false }) { Text(stringResource(R.string.pace_action_back)) } },
        )
    }
}

@Composable
private fun DebugDeveloperCard() {
    if (!BuildConfig.DEBUG || !BuildConfig.SAMPLE_DATA_ENABLED) return
    var sampleDataEnabled by rememberSaveable { mutableStateOf(false) }
    val label = stringResource(R.string.sample_data_toggle)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            androidx.compose.material3.Switch(
                checked = sampleDataEnabled,
                onCheckedChange = { sampleDataEnabled = it },
                modifier = Modifier.semantics { contentDescription = label },
            )
            Text(label, style = MaterialTheme.typography.bodyMedium)
            if (sampleDataEnabled) {
                Text(stringResource(R.string.sample_data_title), style = MaterialTheme.typography.titleSmall)
                PaceSampleData.activities.forEach { activity ->
                    val sampleText = when (activity.kind) {
                        SampleActivityKind.Run -> stringResource(R.string.sample_activity_run, activity.distanceMeters / 1000f, activity.durationMinutes)
                        SampleActivityKind.Cycle -> stringResource(R.string.sample_activity_cycle, activity.distanceMeters / 1000f, activity.durationMinutes)
                    }
                    Text(sampleText, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun paceDestinations(): List<PaceNavigationDestination> = listOf(
    PaceNavigationDestination(HOME, stringResource(R.string.pace_nav_home), PaceAppIcons.Home, PaceAppIcons.HomeOutline),
    PaceNavigationDestination(MAPS, stringResource(R.string.pace_nav_maps), PaceAppIcons.Maps, PaceAppIcons.Maps),
    PaceNavigationDestination(RECORD, stringResource(R.string.pace_nav_record), PaceAppIcons.Record, emphasized = true),
    PaceNavigationDestination(PLAN, stringResource(R.string.pace_nav_plan), PaceAppIcons.Plan, PaceAppIcons.Plan),
    PaceNavigationDestination(INSIGHTS, stringResource(R.string.pace_nav_insights), PaceAppIcons.Insights, PaceAppIcons.Insights),
)

@Composable
private fun sportOptions(): List<Pair<PaceSport, String>> = listOf(
    PaceSport.Cycling to stringResource(R.string.sport_cycling),
    PaceSport.Running to stringResource(R.string.sport_running),
    PaceSport.Jogging to stringResource(R.string.sport_jogging),
    PaceSport.Walking to stringResource(R.string.sport_walking),
)

@Composable
private fun rememberPaceConnectivity(forceOnlineForTest: Boolean?): Boolean {
    val context = LocalContext.current
    val manager = remember(context) { context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager }
    var online by remember(forceOnlineForTest, manager) {
        mutableStateOf(forceOnlineForTest ?: manager.hasValidatedNetwork())
    }
    if (forceOnlineForTest != null) return forceOnlineForTest
    DisposableEffect(manager) {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { online = manager.hasValidatedNetwork() }
            override fun onLost(network: Network) { online = manager.hasValidatedNetwork() }
            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                online = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            }
        }
        runCatching { manager.registerDefaultNetworkCallback(callback) }
        online = manager.hasValidatedNetwork()
        onDispose { runCatching { manager.unregisterNetworkCallback(callback) } }
    }
    return online
}

private fun ConnectivityManager.hasValidatedNetwork(): Boolean = runCatching {
    activeNetwork?.let { network ->
        getNetworkCapabilities(network)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
    } == true
}.getOrDefault(false)

private fun safeTab(route: String): String = route.takeIf { it in setOf(HOME, RECORD, MAPS, PLAN, INSIGHTS) } ?: HOME

private fun paceSportFromName(sport: String): PaceSport = when (sport.uppercase()) {
    "CYCLING" -> PaceSport.Cycling
    "JOGGING" -> PaceSport.Jogging
    "WALKING" -> PaceSport.Walking
    else -> PaceSport.Running
}

internal object PaceAppIcons {
    val Eye = icon("Eye", Color.Black) {
        moveTo(2f, 12f); curveTo(6f, 4f, 18f, 4f, 22f, 12f); curveTo(18f, 20f, 6f, 20f, 2f, 12f); close()
        moveTo(12f, 8f); curveTo(9.8f, 8f, 8f, 9.8f, 8f, 12f); curveTo(8f, 14.2f, 9.8f, 16f, 12f, 16f)
        curveTo(14.2f, 16f, 16f, 14.2f, 16f, 12f); curveTo(16f, 9.8f, 14.2f, 8f, 12f, 8f); close()
    }
    val EyeOff = icon("EyeOff", Color.Black) {
        moveTo(3f, 4f); lineTo(4.5f, 2.5f); lineTo(21.5f, 19.5f); lineTo(20f, 21f); close()
        moveTo(6f, 12f); curveTo(8f, 16f, 13f, 17f, 16f, 15f); lineTo(17.5f, 16.5f)
        curveTo(12f, 20f, 5f, 17f, 2f, 12f); lineTo(5f, 8f); lineTo(6.5f, 9.5f); close()
        moveTo(10f, 7f); curveTo(15f, 5f, 20f, 8f, 22f, 12f); lineTo(19.5f, 15f)
        lineTo(18f, 13.5f); lineTo(19f, 12f); curveTo(17f, 9f, 14f, 8f, 11.5f, 8.5f); close()
    }
    val Chevron = icon("Chevron", Color.Black) {
        moveTo(5f, 9f); lineTo(6.5f, 7.5f); lineTo(12f, 13f); lineTo(17.5f, 7.5f)
        lineTo(19f, 9f); lineTo(12f, 16f); close()
    }
    val Export = icon("Export", Color.Black) {
        moveTo(11f, 15f); lineTo(11f, 6f); lineTo(8f, 9f); lineTo(6.5f, 7.5f); lineTo(12f, 2f)
        lineTo(17.5f, 7.5f); lineTo(16f, 9f); lineTo(13f, 6f); lineTo(13f, 15f); close()
        moveTo(4f, 12f); lineTo(6f, 12f); lineTo(6f, 20f); lineTo(18f, 20f); lineTo(18f, 12f)
        lineTo(20f, 12f); lineTo(20f, 22f); lineTo(4f, 22f); close()
    }
    val Add = icon("Add", Color.Black) {
        moveTo(11f, 4f); lineTo(13f, 4f); lineTo(13f, 11f); lineTo(20f, 11f)
        lineTo(20f, 13f); lineTo(13f, 13f); lineTo(13f, 20f); lineTo(11f, 20f)
        lineTo(11f, 13f); lineTo(4f, 13f); lineTo(4f, 11f); lineTo(11f, 11f); close()
    }
    val Back = icon("Back", Color.Black) {
        moveTo(12f, 4f); lineTo(4f, 12f); lineTo(12f, 20f); lineTo(13.5f, 18.5f)
        lineTo(8f, 13f); lineTo(20f, 13f); lineTo(20f, 11f); lineTo(8f, 11f); lineTo(13.5f, 5.5f); close()
    }
    val Filters = icon("Filters", Color.Black) {
        moveTo(3f, 5f); lineTo(21f, 5f); lineTo(21f, 7f); lineTo(3f, 7f); close()
        moveTo(6f, 11f); lineTo(18f, 11f); lineTo(18f, 13f); lineTo(6f, 13f); close()
        moveTo(9f, 17f); lineTo(15f, 17f); lineTo(15f, 19f); lineTo(9f, 19f); close()
    }
    val Play = icon("Play", Color.Black) {
        moveTo(8f, 5f); lineTo(19f, 12f); lineTo(8f, 19f); close()
    }
    val Pause = icon("Pause", Color.Black) {
        moveTo(6f, 5f); lineTo(10f, 5f); lineTo(10f, 19f); lineTo(6f, 19f); close()
        moveTo(14f, 5f); lineTo(18f, 5f); lineTo(18f, 19f); lineTo(14f, 19f); close()
    }
    val Finish = icon("Finish", Color.Black) {
        moveTo(6f, 6f); lineTo(18f, 6f); lineTo(18f, 18f); lineTo(6f, 18f); close()
    }
    val Delete = icon("Delete", Color.Black) {
        moveTo(4f, 5f); lineTo(9f, 5f); lineTo(9f, 3f); lineTo(15f, 3f)
        lineTo(15f, 5f); lineTo(20f, 5f); lineTo(20f, 7f); lineTo(4f, 7f); close()
        moveTo(6f, 9f); lineTo(18f, 9f); lineTo(17f, 21f); lineTo(7f, 21f); close()
    }
    val Lock = icon("Lock", Color.Black) {
        moveTo(6f, 10f); lineTo(7f, 10f); lineTo(7f, 8f)
        curveTo(7f, 1.5f, 17f, 1.5f, 17f, 8f)
        lineTo(17f, 10f); lineTo(18f, 10f); lineTo(18f, 21f); lineTo(6f, 21f); close()
        moveTo(9f, 10f); lineTo(15f, 10f); lineTo(15f, 8f)
        curveTo(15f, 4f, 9f, 4f, 9f, 8f); close()
    }
    val Cloud = icon("Cloud", Color.Black) {
        moveTo(7f, 19f)
        curveTo(0f, 19f, 0f, 9f, 6.8f, 9f)
        curveTo(8f, 2f, 18f, 2.5f, 19f, 10f)
        curveTo(24f, 10f, 25f, 19f, 18f, 19f)
        close()
    }
    val Spark = icon("Spark", Color.Black) {
        moveTo(12f, 2f); lineTo(15f, 9f); lineTo(22f, 12f); lineTo(15f, 15f)
        lineTo(12f, 22f); lineTo(9f, 15f); lineTo(2f, 12f); lineTo(9f, 9f); close()
    }
    val Home = icon("Home", Color(0xFF1E6674)) {
        moveTo(3f, 10f); lineTo(12f, 3f); lineTo(21f, 10f); lineTo(19f, 12f); lineTo(18f, 11f); lineTo(18f, 20f); lineTo(13.5f, 20f); lineTo(13.5f, 14f); lineTo(10.5f, 14f); lineTo(10.5f, 20f); lineTo(6f, 20f); lineTo(6f, 11f); lineTo(5f, 12f); close()
    }
    val HomeOutline = Home
    val Record = icon("Record", Color(0xFF1E6674)) {
        moveTo(12f, 3f); lineTo(12f, 5f); curveTo(15.9f, 5f, 19f, 8.1f, 19f, 12f); curveTo(19f, 15.9f, 15.9f, 19f, 12f, 19f); curveTo(8.1f, 19f, 5f, 15.9f, 5f, 12f); curveTo(5f, 8.1f, 8.1f, 5f, 12f, 5f); close()
    }
    val Maps = icon("Maps", Color(0xFF1E6674)) {
        moveTo(12f, 22f); curveTo(12f, 22f, 4f, 13.8f, 4f, 9f); curveTo(4f, 4.6f, 7.6f, 2f, 12f, 2f); curveTo(16.4f, 2f, 20f, 4.6f, 20f, 9f); curveTo(20f, 13.8f, 12f, 22f, 12f, 22f); close(); moveTo(12f, 6f); curveTo(10.3f, 6f, 9f, 7.3f, 9f, 9f); curveTo(9f, 10.7f, 10.3f, 12f, 12f, 12f); curveTo(13.7f, 12f, 15f, 10.7f, 15f, 9f); curveTo(15f, 7.3f, 13.7f, 6f, 12f, 6f); close()
    }
    val Plan = icon("Plan", Color(0xFF1E6674)) {
        moveTo(7f, 2f); lineTo(9f, 2f); lineTo(9f, 5f); lineTo(15f, 5f); lineTo(15f, 2f); lineTo(17f, 2f); lineTo(17f, 5f); lineTo(20f, 5f); lineTo(20f, 22f); lineTo(4f, 22f); lineTo(4f, 5f); lineTo(7f, 5f); close(); moveTo(6f, 9f); lineTo(18f, 9f); lineTo(18f, 11f); lineTo(6f, 11f); close(); moveTo(6f, 14f); lineTo(11f, 14f); lineTo(11f, 16f); lineTo(6f, 16f); close()
    }
    val Insights = icon("Insights", Color(0xFF1E6674)) {
        moveTo(4f, 19f); lineTo(4f, 13f); lineTo(7f, 13f); lineTo(7f, 19f); close(); moveTo(10f, 19f); lineTo(10f, 8f); lineTo(13f, 8f); lineTo(13f, 19f); close(); moveTo(16f, 19f); lineTo(16f, 4f); lineTo(19f, 4f); lineTo(19f, 19f); close()
    }
    val Crosshair = icon("Crosshair", Color.Black) {
        moveTo(11f, 2f); lineTo(13f, 2f); lineTo(13f, 5.1f)
        curveTo(16.9f, 5.6f, 18.4f, 8.1f, 18.9f, 11f); lineTo(22f, 11f); lineTo(22f, 13f); lineTo(18.9f, 13f)
        curveTo(18.4f, 15.9f, 16.9f, 18.4f, 13f, 18.9f); lineTo(13f, 22f); lineTo(11f, 22f); lineTo(11f, 18.9f)
        curveTo(8.1f, 18.4f, 5.6f, 15.9f, 5.1f, 13f); lineTo(2f, 13f); lineTo(2f, 11f); lineTo(5.1f, 11f)
        curveTo(5.6f, 8.1f, 8.1f, 5.6f, 11f, 5.1f); close()
        moveTo(12f, 8f); curveTo(9.8f, 8f, 8f, 9.8f, 8f, 12f); curveTo(8f, 14.2f, 9.8f, 16f, 12f, 16f)
        curveTo(14.2f, 16f, 16f, 14.2f, 16f, 12f); curveTo(16f, 9.8f, 14.2f, 8f, 12f, 8f); close()
    }
    val Profile = icon("Profile", Color(0xFF1E6674)) {
        moveTo(12f, 12f); curveTo(14.2f, 12f, 16f, 10.2f, 16f, 8f); curveTo(16f, 5.8f, 14.2f, 4f, 12f, 4f); curveTo(9.8f, 4f, 8f, 5.8f, 8f, 8f); curveTo(8f, 10.2f, 9.8f, 12f, 12f, 12f); close(); moveTo(12f, 14f); curveTo(8f, 14f, 4f, 16f, 4f, 19f); lineTo(4f, 21f); lineTo(20f, 21f); lineTo(20f, 19f); curveTo(20f, 16f, 16f, 14f, 12f, 14f); close()
    }
}

private fun icon(name: String, color: Color, buildPath: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit): ImageVector =
    ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .apply { path(fill = SolidColor(color)) { buildPath() } }
        .build()

@Composable
private fun PaceSettingsGlyph(icon: ImageVector) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun PaceDisclosure(title: String, icon: ImageVector? = null, content: @Composable () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val expandedLabel = stringResource(R.string.pace_disclosure_expanded)
    val collapsedLabel = stringResource(R.string.pace_disclosure_collapsed)
    val motion = !com.pace.app.ui.theme.LocalReducedMotion.current && android.animation.ValueAnimator.areAnimatorsEnabled()
    val rotation by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = if (motion) tween(180) else androidx.compose.animation.core.snap(),
        label = "Disclosure",
    )
    Column(Modifier.fillMaxWidth()) {
        androidx.compose.material3.OutlinedButton(
            onClick = { expanded = !expanded },
            modifier = Modifier.fillMaxWidth().semantics {
                stateDescription = if (expanded) expandedLabel else collapsedLabel
            },
        ) {
            Row(
                Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            Icon(PaceAppIcons.Chevron, contentDescription = null, modifier = Modifier.graphicsLayer { rotationZ = rotation })
        }
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(tween(if (motion) 180 else 0)),
            exit = fadeOut(tween(if (motion) 100 else 0)),
        ) {
            Column(Modifier.padding(top = 8.dp)) { content() }
        }
    }
}
