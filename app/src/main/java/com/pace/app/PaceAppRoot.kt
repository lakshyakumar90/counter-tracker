package com.pace.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
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
import com.pace.app.ui.components.PaceTextAction
import com.pace.app.ui.components.PaceTextField
import com.pace.app.ui.theme.PaceTheme
import com.pace.app.R
import kotlinx.coroutines.launch

private const val HOME = "home"
private const val RECORD = "record"
private const val MAPS = "maps"
private const val PLAN = "plan"
private const val INSIGHTS = "insights"
private const val PROFILE = "profile"
private const val PLAN_DESTINATION_ENABLED = false

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
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val darkTheme = when (currentPreferences?.theme ?: PaceThemeChoice.System) {
        PaceThemeChoice.System -> systemDark
        PaceThemeChoice.Light -> false
        PaceThemeChoice.Dark -> true
    }
    val online = rememberPaceConnectivity(forceOnlineForTest)

    PaceTheme(darkTheme = darkTheme) {
        if (currentPreferences == null) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {}
        } else if (currentPreferences.onboardingComplete) {
            PaceShell(
                preferences = currentPreferences,
                onPreferencesChanged = updatePreferences,
                forceHome = forceHome,
                isOnline = online,
            )
        } else {
            PaceOnboarding(preferences = currentPreferences, isOnline = online, onComplete = updatePreferences)
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
    var draft by remember { mutableStateOf(preferences) }
    val titles = listOf(
        stringResource(R.string.pace_onboarding_welcome_title),
        stringResource(R.string.pace_onboarding_sports_title),
        stringResource(R.string.pace_onboarding_units_title),
        stringResource(R.string.pace_onboarding_profile_title),
    )
    val descriptions = listOf(
        stringResource(R.string.pace_onboarding_welcome_body),
        stringResource(R.string.pace_onboarding_sports_body),
        stringResource(R.string.pace_onboarding_units_body),
        stringResource(R.string.pace_onboarding_profile_body),
    )
    var name by rememberSaveable { mutableStateOf(draft.profileName) }
    val finishSetup = {
        onComplete(draft.copy(onboardingComplete = true, profileName = name.trim()))
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            if (!isOnline) PaceOfflineBanner(stringResource(R.string.pace_offline_status), Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 32.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
            Text(stringResource(R.string.pace_onboarding_step, page + 1, titles.size), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(titles[page], style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
            Text(descriptions[page], style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)

            when (page) {
                0 -> Card {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.pace_profile_local_state), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.pace_profile_no_account), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                1 -> OnboardingSportChoices(draft = draft, onDraft = { draft = it })
                2 -> OnboardingUnitChoices(draft = draft, onDraft = { draft = it })
                else -> {
                    OnboardingThemeChoices(draft = draft, onDraft = { draft = it })
                    PaceTextField(
                        value = name,
                        onValueChange = { name = it.take(60) },
                        label = stringResource(R.string.pace_profile_name_label),
                        helperText = stringResource(R.string.pace_profile_name_helper),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (page > 0) PaceTextAction(stringResource(R.string.pace_action_back), onClick = { page-- })
                Spacer(Modifier.weight(1f))
                PaceTextAction(
                    label = stringResource(R.string.pace_action_skip),
                    onClick = { onComplete(PacePreferences(onboardingComplete = true)) },
                )
                if (page == titles.lastIndex) {
                    PacePrimaryButton(stringResource(R.string.pace_action_get_started), onClick = finishSetup)
                } else {
                    PacePrimaryButton(stringResource(R.string.pace_action_continue), onClick = { page++ })
                }
            }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OnboardingSportChoices(draft: PacePreferences, onDraft: (PacePreferences) -> Unit) {
    val sports = sportOptions()
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        sports.forEach { (sport, label) ->
            FilterChip(
                selected = sport in draft.preferredSports,
                onClick = {
                    val next = draft.preferredSports.toMutableSet().apply {
                        if (sport in this && size > 1) remove(sport) else add(sport)
                    }
                    onDraft(draft.copy(preferredSports = next))
                },
                label = { Text(label) },
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
private fun OnboardingThemeChoices(draft: PacePreferences, onDraft: (PacePreferences) -> Unit) {
    PaceSegmentedControl(
        options = listOf(
            PaceSegment(PaceThemeChoice.System, stringResource(R.string.pace_theme_system)),
            PaceSegment(PaceThemeChoice.Light, stringResource(R.string.pace_theme_light)),
            PaceSegment(PaceThemeChoice.Dark, stringResource(R.string.pace_theme_dark)),
        ),
        selected = draft.theme,
        onSelected = { onDraft(draft.copy(theme = it)) },
    )
}

@Composable
private fun PaceShell(
    preferences: PacePreferences,
    onPreferencesChanged: (PacePreferences) -> Unit,
    forceHome: Boolean,
    isOnline: Boolean,
) {
    var tab by rememberSaveable { mutableStateOf(if (forceHome) HOME else safeTab(preferences.lastSelectedTab)) }
    var profileOpen by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = profileOpen) { profileOpen = false }
    val destinations = paceDestinations()
    val shownDestinations = if (PLAN_DESTINATION_ENABLED) destinations else destinations.filterNot { it.route == PLAN }
    val selectedRoute = if (profileOpen) PROFILE else tab
    val selectTab: (String) -> Unit = { selected ->
        if (shownDestinations.any { it.route == selected && it.enabled }) {
            tab = selected
            profileOpen = false
            onPreferencesChanged(preferences.copy(lastSelectedTab = selected))
        }
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
                            preferences = preferences,
                            onPreferencesChanged = onPreferencesChanged,
                            onSelectTab = selectTab,
                            onOpenProfile = openProfile,
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
                            preferences = preferences,
                            onPreferencesChanged = onPreferencesChanged,
                            onSelectTab = selectTab,
                            onOpenProfile = openProfile,
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
    preferences: PacePreferences,
    onPreferencesChanged: (PacePreferences) -> Unit,
    onSelectTab: (String) -> Unit,
    onOpenProfile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (route) {
        HOME -> PaceHomeScreen(preferences, onSelectTab, onOpenProfile, modifier)
        RECORD -> PaceRecordScreen(preferences, modifier)
        MAPS -> PaceMapsScreen(modifier)
        INSIGHTS -> PaceInsightsScreen(onSelectTab, modifier)
        PROFILE -> PaceProfileScreen(preferences, onPreferencesChanged, modifier)
        PLAN -> if (PLAN_DESTINATION_ENABLED) PacePlanScopeScreen(modifier) else PaceHomeScreen(preferences, onSelectTab, onOpenProfile, modifier)
        else -> PaceHomeScreen(preferences, onSelectTab, onOpenProfile, modifier)
    }
}

@Composable
private fun PaceHomeScreen(
    preferences: PacePreferences,
    onSelectTab: (String) -> Unit,
    onOpenProfile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.pace_nav_home), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
            PaceTextAction(stringResource(R.string.pace_profile_action), onClick = onOpenProfile)
        }
        Text(stringResource(R.string.pace_home_greeting), style = MaterialTheme.typography.titleLarge)
        PaceEmptyState(
            title = stringResource(R.string.pace_home_empty_title),
            message = stringResource(R.string.pace_home_empty_body),
            actionLabel = stringResource(R.string.pace_home_record_action),
            onAction = { onSelectTab(RECORD) },
        )
        DebugDeveloperCard()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PaceRecordScreen(preferences: PacePreferences, modifier: Modifier = Modifier) {
    var selectedSport by rememberSaveable { mutableStateOf(preferences.preferredSports.firstOrNull() ?: PaceSport.Running) }
    val options = sportOptions()
    Column(modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.pace_record_title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            options.forEach { (sport, label) ->
                PaceSportSelectorTile(sport = sport, label = label, selected = sport == selectedSport, onClick = { selectedSport = sport })
            }
        }
        Text(stringResource(R.string.pace_record_selected_sport, options.first { it.first == selectedSport }.second), style = MaterialTheme.typography.titleMedium)
        Card {
            Text(
                stringResource(R.string.pace_record_preflight_body),
                Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PaceMapsScreen(modifier: Modifier = Modifier) {
    Column(modifier.verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.pace_map_title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
        PaceEmptyState(
            title = stringResource(R.string.pace_map_unavailable_title),
            message = stringResource(R.string.pace_map_unavailable_body),
        )
    }
}

@Composable
private fun PaceInsightsScreen(onSelectTab: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.pace_insights_title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
        PaceEmptyState(
            title = stringResource(R.string.pace_home_empty_title),
            message = stringResource(R.string.pace_insights_empty_body),
            actionLabel = stringResource(R.string.pace_home_record_action),
            onAction = { onSelectTab(RECORD) },
        )
    }
}

@Composable
private fun PacePlanScopeScreen(modifier: Modifier = Modifier) {
    Column(modifier.padding(20.dp)) {
        Text(stringResource(R.string.pace_nav_plan), style = MaterialTheme.typography.headlineMedium)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PaceProfileScreen(
    preferences: PacePreferences,
    onPreferencesChanged: (PacePreferences) -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by rememberSaveable { mutableStateOf(preferences.profileName) }
    var saved by rememberSaveable { mutableStateOf(false) }
    val sports = sportOptions()
    Column(modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.pace_profile_title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
        Text(stringResource(R.string.pace_profile_local_state), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.pace_profile_no_account), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        PaceTextField(
            value = name,
            onValueChange = { name = it.take(60); saved = false },
            label = stringResource(R.string.pace_profile_name_label),
            helperText = stringResource(R.string.pace_profile_name_helper),
            modifier = Modifier.fillMaxWidth(),
        )
        PacePrimaryButton(
            label = stringResource(R.string.pace_profile_save),
            onClick = {
                onPreferencesChanged(preferences.copy(profileName = name.trim()))
                saved = true
            },
        )
        if (saved) Text(stringResource(R.string.pace_profile_saved), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)

        Text(stringResource(R.string.pace_settings_units), style = MaterialTheme.typography.titleMedium)
        PaceSegmentedControl(
            options = listOf(
                PaceSegment(PaceUnits.Metric, stringResource(R.string.pace_unit_metric)),
                PaceSegment(PaceUnits.Imperial, stringResource(R.string.pace_unit_imperial)),
            ),
            selected = preferences.units,
            onSelected = { onPreferencesChanged(preferences.copy(units = it)) },
        )

        Text(stringResource(R.string.pace_settings_theme), style = MaterialTheme.typography.titleMedium)
        PaceSegmentedControl(
            options = listOf(
                PaceSegment(PaceThemeChoice.System, stringResource(R.string.pace_theme_system)),
                PaceSegment(PaceThemeChoice.Light, stringResource(R.string.pace_theme_light)),
                PaceSegment(PaceThemeChoice.Dark, stringResource(R.string.pace_theme_dark)),
            ),
            selected = preferences.theme,
            onSelected = { onPreferencesChanged(preferences.copy(theme = it)) },
        )

        Text(stringResource(R.string.pace_settings_sports), style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            sports.forEach { (sport, label) ->
                FilterChip(
                    selected = sport in preferences.preferredSports,
                    onClick = {
                        val next = preferences.preferredSports.toMutableSet().apply {
                            if (sport in this && size > 1) remove(sport) else add(sport)
                        }
                        onPreferencesChanged(preferences.copy(preferredSports = next))
                    },
                    label = { Text(label) },
                )
            }
        }
        DebugBuildInformation()
    }
}

@Composable
private fun DebugBuildInformation() {
    if (!BuildConfig.DEBUG) return
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.pace_debug_build_info), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.build_info_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.build_info_variant, BuildConfig.BUILD_TYPE), style = MaterialTheme.typography.bodyMedium)
        }
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
    PaceNavigationDestination(RECORD, stringResource(R.string.pace_nav_record), PaceAppIcons.Record, PaceAppIcons.Record),
    PaceNavigationDestination(MAPS, stringResource(R.string.pace_nav_maps), PaceAppIcons.Maps, PaceAppIcons.Maps),
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

private fun safeTab(route: String): String = route.takeIf { it in setOf(HOME, RECORD, MAPS, INSIGHTS) } ?: HOME

private object PaceAppIcons {
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
    val Profile = icon("Profile", Color(0xFF1E6674)) {
        moveTo(12f, 12f); curveTo(14.2f, 12f, 16f, 10.2f, 16f, 8f); curveTo(16f, 5.8f, 14.2f, 4f, 12f, 4f); curveTo(9.8f, 4f, 8f, 5.8f, 8f, 8f); curveTo(8f, 10.2f, 9.8f, 12f, 12f, 12f); close(); moveTo(12f, 14f); curveTo(8f, 14f, 4f, 16f, 4f, 19f); lineTo(4f, 21f); lineTo(20f, 21f); lineTo(20f, 19f); curveTo(20f, 16f, 16f, 14f, 12f, 14f); close()
    }
}

private fun icon(name: String, color: Color, buildPath: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit): ImageVector =
    ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .apply { path(fill = SolidColor(color)) { buildPath() } }
        .build()
