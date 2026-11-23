package com.pace.app

import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.text.font.FontWeight
import com.pace.app.data.local.entity.GoalEntity
import com.pace.app.data.local.entity.WorkoutTemplateEntity
import com.pace.app.data.local.entity.WorkoutIntervalEntity
import com.pace.app.auth.PaceAccountState
import com.pace.app.data.sync.PaceAiGatewayClient
import com.pace.app.ui.components.PacePrimaryButton
import com.pace.app.domain.metrics.ActivityMetrics
import com.pace.app.domain.metrics.InsightsAnalytics
import com.pace.app.domain.metrics.InsightsPeriod
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun PaceInsightsDashboard(container: PaceAppContainer, preferences: PacePreferences, onPreferencesChanged: (PacePreferences) -> Unit, onSelectTab: (String) -> Unit, modifier: Modifier = Modifier) {
    val activities by container.activityRepository.observeHistory().collectAsState(initial = emptyList())
    val goals by container.goalRepository.observeGoals().collectAsState(initial = emptyList())
    val records by container.goalRepository.observePersonalRecords().collectAsState(initial = emptyList())
    var periodName by rememberSaveable { mutableStateOf(preferences.insightsPeriod) }
    var sport by rememberSaveable { mutableStateOf("ALL") }
    var selected by rememberSaveable { mutableIntStateOf(-1) }
    var goalDialog by rememberSaveable { mutableStateOf(false) }
    var editingGoalId by rememberSaveable { mutableStateOf<String?>(null) }
    var goalType by rememberSaveable { mutableStateOf("DISTANCE") }
    var goalTarget by rememberSaveable { mutableStateOf("") }
    var goalMonths by rememberSaveable { mutableStateOf("1") }
    var chartDuration by rememberSaveable { mutableStateOf(false) }
    var chartZoom by rememberSaveable { mutableFloatStateOf(1f) }
    var heatmapDate by rememberSaveable { mutableStateOf<String?>(null) }
    var routeActivityId by rememberSaveable { mutableStateOf<String?>(null) }
    var routePointIndex by rememberSaveable { mutableIntStateOf(0) }
    var fullChart by rememberSaveable { mutableStateOf(false) }
    val period = runCatching { InsightsPeriod.valueOf(periodName) }.getOrDefault(InsightsPeriod.WEEK)
    val today = LocalDate.now()
    val summary = remember(activities, period, sport, today) { InsightsAnalytics.summarize(activities, period, today, sport.takeUnless { it == "ALL" }) }
    val aiSummary = remember(activities, today) {
        val startMillis = today.minusDays(27).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val recent = activities.filter { ActivityMetrics.isEligibleSummary(it) && it.startedAtEpochMillis >= startMillis }
        JSONObject()
            .put("periodDays", 28)
            .put("activityCount", recent.size)
            .put("distanceMeters", recent.sumOf { it.distanceMeters })
            .put("movingSeconds", recent.sumOf { it.movingSeconds })
            .put("sports", JSONArray(recent.map { it.sport.uppercase() }.distinct().filter { it in setOf("CYCLING", "RUNNING", "JOGGING", "WALKING", "HOME_WORKOUT") }))
            .put("focus", "consistency")
    }
    val context = LocalContext.current
    val exportTitle = stringResource(R.string.insights_export_title)
    val routeScrubberDescription = stringResource(R.string.insights_route_scrubber_description)
    val scope = rememberCoroutineScope()
    val routeResult by produceState<Result<com.pace.app.data.local.dao.ActivityWithTrack?>?>(initialValue = null, routeActivityId) {
        value = routeActivityId?.let { id -> runCatching { container.activityRepository.getActivity(id) } }
    }
    val unit = if (preferences.units == PaceUnits.Imperial) "mi" else "km"
    val divisor = if (unit == "mi") 1609.344 else 1000.0
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(stringResource(R.string.pace_insights_title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            FilledTonalIconButton(onClick = { onSelectTab("predictions") }) {
                Icon(PaceAppIcons.Insights, contentDescription = stringResource(R.string.pace_predictions_open))
            }
            IconButton(onClick = {
                val text = "Pace · " + summary.start + " – " + summary.endExclusive.minusDays(1) + "\nDistance: " + "%.1f".format(Locale.getDefault(), summary.distanceMeters / divisor) + " " + unit + "\nActivities: " + summary.activityCount + "\nMoving time: " + summary.durationSeconds / 60 + " min"
                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }, exportTitle))
            }) {
                Icon(PaceAppIcons.Export, contentDescription = stringResource(R.string.insights_export))
            }
        }
        if (preferences.aiEnabled) PaceAiInsightsCard(container, preferences, onPreferencesChanged, aiSummary)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { InsightsPeriod.entries.forEach { item -> FilterChip(period == item, { periodName = item.name; selected = -1; onPreferencesChanged(preferences.copy(insightsPeriod = item.name)) }, label = { Text(item.name.lowercase().replaceFirstChar(Char::uppercase)) }) } }
        val sports = listOf(
            Triple("ALL", R.string.pace_history_all_sports, null),
            Triple("RUNNING", R.string.sport_running, com.pace.app.ui.components.PaceSport.Running),
            Triple("CYCLING", R.string.sport_cycling, com.pace.app.ui.components.PaceSport.Cycling),
            Triple("JOGGING", R.string.sport_jogging, com.pace.app.ui.components.PaceSport.Jogging),
            Triple("WALKING", R.string.sport_walking, com.pace.app.ui.components.PaceSport.Walking),
        )
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            sports.forEach { (key, label, activity) ->
                FilterChip(
                    selected = sport == key,
                    onClick = { sport = key; selected = -1; heatmapDate = null },
                    label = { Text(stringResource(label)) },
                    leadingIcon = activity?.let { selectedSport ->
                        { com.pace.app.ui.components.PaceSportGlyph(selectedSport, LocalContentColor.current, Modifier.size(18.dp)) }
                    },
                )
            }
        }
        Text(summary.start.toString() + " – " + summary.endExclusive.minusDays(1), style = MaterialTheme.typography.titleMedium)

        if (activities.none(ActivityMetrics::isEligibleSummary)) {
            Text(stringResource(R.string.pace_insights_short_empty))
            TextButton(onClick = { onSelectTab("record") }) { Text(stringResource(R.string.pace_home_record_action)) }
        } else {
            Card(
                Modifier.fillMaxWidth(),
                colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.insights_distance), style = MaterialTheme.typography.labelLarge)
                    Text(
                        stringResource(R.string.pace_stitch_distance, summary.distanceMeters / divisor, unit),
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        InsightsMetric(stringResource(R.string.insights_activities), summary.activityCount.toString(), Modifier.weight(1f))
                        InsightsMetric(stringResource(R.string.insights_active_days), summary.activeDays.toString(), Modifier.weight(1f))
                        InsightsMetric(stringResource(R.string.insights_duration), stringResource(R.string.pace_stitch_duration, summary.durationSeconds / 3600, summary.durationSeconds % 3600 / 60), Modifier.weight(1f))
                    }
                }
            }
            Card { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.fillMaxWidth()) {
                    Text(stringResource(if (chartDuration) R.string.insights_duration_chart else R.string.insights_distance_chart), style = MaterialTheme.typography.titleMedium)
                    Row { TextButton({ chartDuration = !chartDuration; selected = -1 }) { Text(stringResource(if (chartDuration) R.string.insights_distance else R.string.insights_duration)) }; TextButton({ fullChart = true }) { Text(stringResource(R.string.insights_expand_chart)) } }
                }
                val chartValues = summary.buckets.map { if (chartDuration) it.durationSeconds / 60.0 else it.distanceMeters.toDouble() / divisor }
                val chartUnit = if (chartDuration) "min" else unit
                InsightsBars(chartValues, summary.buckets.map { it.label }, selected, { selected = it }, Modifier.fillMaxWidth().height(170.dp), chartUnit)
            } }
            if (period == InsightsPeriod.WEEK && !chartDuration) {
                Card { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.insights_weekly_bars), style = MaterialTheme.typography.titleMedium)
                    WeeklyDistanceBars(summary.buckets.map { it.distanceMeters.toDouble() / divisor }, summary.buckets.map { it.label }, selected, { selected = it }, Modifier.fillMaxWidth().height(120.dp), unit)
                } }
            }
            if (selected in summary.buckets.indices) {
                val bucket = summary.buckets[selected]
                val selectedValue = if (chartDuration) "%.0f min".format(Locale.getDefault(), bucket.durationSeconds / 60.0) else "%.1f %s".format(Locale.getDefault(), bucket.distanceMeters / divisor, unit)
                Text(bucket.label + " · " + selectedValue + " · " + bucket.activityCount + " activities", Modifier.semantics { contentDescription = bucket.date.toString() + " selected; " + selectedValue })
                activities.filter { row -> ActivityMetrics.isEligibleSummary(row) && (sport == "ALL" || row.sport.equals(sport, true)) && Instant.ofEpochMilli(row.startedAtEpochMillis).atZone(ZoneId.systemDefault()).toLocalDate().let { if (period == InsightsPeriod.YEAR) it.month == bucket.date.month else it == bucket.date } }.forEach { activity ->
                    Text((activity.title ?: activity.sport) + " · " + "%.1f".format(Locale.getDefault(), activity.distanceMeters / divisor) + " " + unit, Modifier.clickable { routeActivityId = activity.id; routePointIndex = 0 }, style = MaterialTheme.typography.bodySmall)
                }
            }
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.insights_sport_breakdown), style = MaterialTheme.typography.titleMedium)
                summary.sportDistanceMeters.forEach { (name, meters) -> Text(name.lowercase().replaceFirstChar(Char::uppercase) + " · " + "%.1f".format(Locale.getDefault(), meters / divisor) + " " + unit + " · " + if (summary.distanceMeters == 0L) "0%" else (meters * 100 / summary.distanceMeters).toInt().toString() + "%") }
            } }
            val hasPace = sport in listOf("RUNNING", "JOGGING", "WALKING") && summary.paceSecondsPerKm != null
            val hasSpeed = sport == "CYCLING" && summary.speedMetersPerSecond != null
            if (hasPace || hasSpeed || summary.elevationGainMeters != null) Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.insights_trends), style = MaterialTheme.typography.titleMedium)
                val run = summary.paceSecondsPerKm
                val cycle = summary.speedMetersPerSecond
                val paceValue = run?.let { it * if (unit == "mi") 1.609344 else 1.0 }
                val paceText = paceValue?.let { "%d:%02d".format((it / 60).toInt(), (it % 60).toInt()) }
                if (hasPace && paceText != null) Text(stringResource(R.string.insights_pace_metric, paceText, if (unit == "mi") "min/mi" else "min/km"))
                val speedValue = cycle?.let { if (unit == "mi") it * 2.236936 else it * 3.6 }
                if (hasSpeed && speedValue != null) Text(stringResource(R.string.insights_speed_metric, "%.1f".format(Locale.getDefault(), speedValue), if (unit == "mi") "mph" else "km/h"))
                summary.elevationGainMeters?.let { Text(stringResource(R.string.insights_elevation_metric, it.toInt())) }
                if (hasPace) {
                    val paceSeries = summary.buckets.map { bucket ->
                        val rows = activities.filter { row -> ActivityMetrics.isEligibleSummary(row) && row.sport.equals(sport, true) && inBucket(row, bucket.date, period) }
                        val pace = ActivityMetrics.paceSecondsPerKilometer(rows.sumOf { it.distanceMeters }.toDouble(), rows.sumOf { it.movingSeconds })
                        pace?.let { it * if (unit == "mi") 1.609344 / 60.0 else 1.0 / 60.0 }
                    }
                    InsightsTrendGraph(stringResource(R.string.insights_pace_trend), if (unit == "mi") "min/mi · lower is better" else "min/km · lower is better", paceSeries, summary.buckets.map { it.label }, selected, { selected = it })
                }
                if (hasSpeed) {
                    val speedSeries = summary.buckets.map { bucket ->
                        val rows = activities.filter { row -> ActivityMetrics.isEligibleSummary(row) && row.sport.equals("CYCLING", true) && inBucket(row, bucket.date, period) }
                        ActivityMetrics.speedMetersPerSecond(rows.sumOf { it.distanceMeters }.toDouble(), rows.sumOf { it.movingSeconds })?.let { if (unit == "mi") it * 2.236936 else it * 3.6 }
                    }
                    InsightsTrendGraph(stringResource(R.string.insights_speed_trend), if (unit == "mi") "mph · higher is better" else "km/h · higher is better", speedSeries, summary.buckets.map { it.label }, selected, { selected = it })
                }
                val elevationSeries = summary.buckets.map { it.elevationGainMeters }
                if (elevationSeries.any { it != null }) InsightsTrendGraph(stringResource(R.string.insights_elevation_trend), "m · gaps mean unavailable", elevationSeries, summary.buckets.map { it.label }, selected, { selected = it })
            } }
            Card { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.insights_frequency), style = MaterialTheme.typography.titleMedium)
                val dayCounts = remember(activities, sport) {
                    val zone = ZoneId.systemDefault()
                    activities.asSequence()
                        .filter { ActivityMetrics.isEligibleSummary(it) && (sport == "ALL" || it.sport.equals(sport, true)) }
                        .groupingBy { Instant.ofEpochMilli(it.startedAtEpochMillis).atZone(zone).toLocalDate() }
                        .eachCount()
                }
                val scroll = rememberScrollState()
                LaunchedEffect(scroll.maxValue) { if (scroll.maxValue != Int.MAX_VALUE && scroll.value == 0) scroll.scrollTo(scroll.maxValue) }
                Row(Modifier.horizontalScroll(scroll), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    (11 downTo 0).forEach { monthsAgo ->
                        val month = java.time.YearMonth.from(today).minusMonths(monthsAgo.toLong())
                        val first = month.atDay(1)
                        val weekStart = first.minusDays((first.dayOfWeek.value - 1).toLong())
                        val columns = (month.lengthOfMonth() + first.dayOfWeek.value + 5) / 7
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(month.month.getDisplayName(java.time.format.TextStyle.SHORT, Locale.getDefault()), style = MaterialTheme.typography.labelSmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                repeat(columns) { week ->
                                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        repeat(7) { day ->
                                            val date = weekStart.plusDays((week * 7 + day).toLong())
                                            val count = dayCounts[date] ?: 0
                                            val available = date.month == month.month && !date.isAfter(today)
                                            val color = when {
                                                !available -> androidx.compose.ui.graphics.Color.Transparent
                                                count == 0 -> MaterialTheme.colorScheme.surfaceVariant
                                                else -> MaterialTheme.colorScheme.primary.copy(alpha = (.45f + count * .17f).coerceAtMost(1f))
                                            }
                                            val cellLabel = stringResource(R.string.pace_insights_heatmap_day, date.toString(), count)
                                            Box(Modifier.size(12.dp).background(color, RoundedCornerShape(3.dp)).then(
                                                if (available) Modifier.clickable { heatmapDate = date.toString() }
                                                    .semantics { contentDescription = cellLabel }
                                                else Modifier,
                                            ))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } }
            heatmapDate?.let { dateText ->
                val date = LocalDate.parse(dateText)
                Text(stringResource(R.string.insights_selected_day, dateText), style = MaterialTheme.typography.titleSmall)
                val dayRows = activities.filter { row -> ActivityMetrics.isEligibleSummary(row) && (sport == "ALL" || row.sport.equals(sport, true)) && Instant.ofEpochMilli(row.startedAtEpochMillis).atZone(ZoneId.systemDefault()).toLocalDate() == date }
                if (dayRows.isEmpty()) Text(stringResource(R.string.insights_no_period_data), style = MaterialTheme.typography.bodySmall)
                dayRows.forEach { activity -> Text((activity.title ?: activity.sport) + " · " + "%.1f".format(Locale.getDefault(), activity.distanceMeters / divisor) + " " + unit, Modifier.clickable { routeActivityId = activity.id; routePointIndex = 0 }, style = MaterialTheme.typography.bodySmall) }
            }
            Card { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(stringResource(R.string.insights_goals), style = MaterialTheme.typography.titleMedium); TextButton({ goalDialog = true }) { Text(stringResource(R.string.insights_goal_add)) } }
                val active = goals.filter { it.endDate >= today.toString() }
                if (active.isEmpty()) Text(stringResource(R.string.insights_goals_empty))
                active.forEach { goal ->
                    val from = LocalDate.parse(goal.startDate); val end = LocalDate.parse(goal.endDate).plusDays(1)
                    val rows = activities.filter { row ->
                        val date = Instant.ofEpochMilli(row.startedAtEpochMillis).atZone(ZoneId.systemDefault()).toLocalDate()
                        ActivityMetrics.isEligibleSummary(row) && (goal.sport == null || row.sport.equals(goal.sport, true)) && date >= from && date < end
                    }
                    val goalDivisor = if (goal.unit == "mi") 1609.344 else 1000.0
                    val progress = when (goal.goalType) { "DISTANCE" -> rows.sumOf { it.distanceMeters } / goalDivisor; "DURATION" -> rows.sumOf { it.movingSeconds } / 3600.0; else -> rows.size.toDouble() }
                    Text(goal.goalType + " · " + "%.1f".format(Locale.getDefault(), progress) + " / " + goal.targetValue + " " + goal.unit)
                    LinearProgressIndicator({ (progress / goal.targetValue).toFloat().coerceIn(0f, 1f) }, Modifier.fillMaxWidth())
                    Row {
                        TextButton(onClick = { editingGoalId = goal.id; goalType = goal.goalType; goalTarget = goal.targetValue.toString(); goalMonths = "1"; goalDialog = true }) { Text(stringResource(R.string.insights_goal_edit)) }
                        TextButton(onClick = { scope.launch { container.goalRepository.saveGoal(goal.copy(endDate = today.minusDays(1).toString(), updatedAtEpochMillis = System.currentTimeMillis())) } }) { Text(stringResource(R.string.insights_goal_archive)) }
                    }
                }
                val archived = goals.filter { it.endDate < today.toString() }
                if (archived.isNotEmpty()) {
                    Text(stringResource(R.string.insights_archived_goals, archived.size), style = MaterialTheme.typography.labelMedium)
                    archived.forEach { Text(it.goalType + " · " + it.startDate + " – " + it.endDate + " · " + it.targetValue + " " + it.unit, style = MaterialTheme.typography.bodySmall) }
                }
            } }
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.insights_records), style = MaterialTheme.typography.titleMedium)
                if (records.isEmpty()) Text(stringResource(R.string.insights_records_empty))
                records.take(8).forEach { record -> Text(record.sport + " · " + record.metric + ": " + record.recordValue + " " + record.unit + (record.activityId?.let { id -> activities.firstOrNull { it.id == id }?.let { " · " + it.title.orEmpty() } ?: "" } ?: "")) }
            } }
        }
    }
    if (goalDialog) AlertDialog(onDismissRequest = { goalDialog = false; editingGoalId = null }, title = { Text(stringResource(if (editingGoalId == null) R.string.insights_goal_add else R.string.insights_goal_edit)) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row { listOf("DISTANCE", "DURATION", "ACTIVITY_COUNT").forEach { type -> FilterChip(goalType == type, { goalType = type }, label = { Text(type.take(4)) }) } }
            OutlinedTextField(goalTarget, { goalTarget = it }, label = { Text(stringResource(R.string.insights_goal_target)) })
            OutlinedTextField(goalMonths, { goalMonths = it.filter(Char::isDigit) }, label = { Text(stringResource(R.string.insights_goal_months)) })
        }
    }, confirmButton = { TextButton(onClick = {
        val amount = goalTarget.toDoubleOrNull(); val months = goalMonths.toLongOrNull()
        if (amount != null && amount > 0 && months != null && months in 1..24) scope.launch {
            val existing = goals.firstOrNull { it.id == editingGoalId }
            val goalUnit = existing?.unit ?: when (goalType) { "DISTANCE" -> unit; "DURATION" -> "hours"; else -> "activities" }
            val goalSport = existing?.sport ?: sport.takeUnless { it == "ALL" }
            val startDate = existing?.startDate ?: today.toString()
            val endDate = existing?.endDate ?: today.plusMonths(months).minusDays(1).toString()
            container.goalRepository.saveGoal(GoalEntity(existing?.id ?: UUID.randomUUID().toString(), goalType, goalSport, amount, goalUnit, startDate, endDate, existing?.createdAtEpochMillis ?: System.currentTimeMillis(), System.currentTimeMillis()))
            goalDialog = false
            editingGoalId = null
        }
    }) { Text(stringResource(R.string.insights_goal_save)) } }, dismissButton = { TextButton({ goalDialog = false }) { Text(stringResource(R.string.insights_done)) } })
    if (routeActivityId != null) {
        val route = routeResult?.getOrNull()
        val points = route?.trackPoints.orEmpty().sortedBy { it.sequence }
        val selectedPoint = points.getOrNull(routePointIndex.coerceIn(0, (points.size - 1).coerceAtLeast(0)))
        Dialog(onDismissRequest = { routeActivityId = null }) {
            Surface(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.insights_route_scrub_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                        TextButton({ routeActivityId = null }) { Text(stringResource(R.string.insights_done)) }
                    }
                    if (route == null) Text(stringResource(R.string.pace_history_load_error)) else {
                        Text(route.activity.title?.ifBlank { null } ?: route.activity.sport, style = MaterialTheme.typography.titleMedium)
                        if (points.isEmpty()) Text(stringResource(R.string.insights_route_unavailable)) else {
                            val selectedTime = Instant.ofEpochMilli(selectedPoint!!.timestampEpochMillis).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MMM d, HH:mm:ss", Locale.getDefault()))
                            Text(stringResource(R.string.insights_route_point_time, selectedTime, routePointIndex + 1, points.size), Modifier.semantics { contentDescription = "Selected route point " + (routePointIndex + 1) + " of " + points.size + " at " + selectedTime })
                            if (points.size > 1) Slider(value = routePointIndex.toFloat(), onValueChange = { routePointIndex = it.roundToInt().coerceIn(points.indices) }, valueRange = 0f..points.lastIndex.toFloat(), modifier = Modifier.semantics { contentDescription = routeScrubberDescription })
                            RouteTimeline(points, routePointIndex, onSelectPoint = { routePointIndex = it })
                            com.pace.app.ui.maps.PaceMapPanel(
                                darkTheme = preferences.theme == PaceThemeChoice.Dark || (preferences.theme == PaceThemeChoice.System && isSystemInDarkTheme()),
                                isOnline = false,
                                routePoints = points,
                                highlightedTrackPoint = selectedPoint,
                                fitRoute = true,
                                modifier = Modifier.fillMaxWidth().height(340.dp),
                            )
                            Text(stringResource(R.string.insights_route_scrubber_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
    if (fullChart) Dialog(onDismissRequest = { fullChart = false }) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Column(Modifier.fillMaxWidth()) {
                    Text(stringResource(if (chartDuration) R.string.insights_duration_chart else R.string.insights_chart_fullscreen), style = MaterialTheme.typography.titleLarge)
                    TextButton({ fullChart = false }) { Text(stringResource(R.string.insights_done)) }
                }
                Text(stringResource(R.string.insights_zoom_pan), style = MaterialTheme.typography.bodySmall)
                Slider(value = chartZoom, onValueChange = { chartZoom = it }, valueRange = 1f..3f, modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                    InsightsBars(summary.buckets.map { if (chartDuration) it.durationSeconds / 60.0 else it.distanceMeters / divisor }, summary.buckets.map { it.label }, selected, { selected = it }, Modifier.width((320 * chartZoom).dp).height(320.dp), if (chartDuration) "min" else unit)
                }
            }
        }
    }
}

@Composable
private fun PaceAiInsightsCard(
    container: PaceAppContainer,
    preferences: PacePreferences,
    onPreferencesChanged: (PacePreferences) -> Unit,
    summary: JSONObject,
) {
    val account by container.authRepository.accountState.collectAsState()
    val userId = (account as? PaceAccountState.SignedIn)?.userId
    val cached = remember(preferences.cachedAiResponse, preferences.cachedAiOwnerUserId, userId) {
        if (userId != null && preferences.cachedAiOwnerUserId == userId) runCatching { preferences.cachedAiResponse?.let(::JSONObject) }.getOrNull() else null
    }
    var result by remember(cached) { mutableStateOf(cached) }
    var kind by remember(preferences.cachedAiKind, preferences.cachedAiOwnerUserId, userId) { mutableStateOf(preferences.cachedAiKind ?: "insight") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var workoutName by remember(result) { mutableStateOf(result?.optString("name").orEmpty()) }
    var workoutDuration by remember(result) { mutableStateOf(result?.optInt("durationMinutes", 0)?.toString().orEmpty()) }
    val scope = rememberCoroutineScope()
    val savedWorkoutText = stringResource(R.string.pace_ai_saved_workout)
    val unavailableText = stringResource(R.string.pace_ai_unavailable)
    val cloudConfigured = BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_PUBLISHABLE_KEY.isNotBlank()
    val cloudMissingText = stringResource(R.string.pace_ai_reason_not_configured)
    val signInText = stringResource(R.string.pace_ai_reason_signed_out)
    val serverDisabledText = stringResource(R.string.pace_ai_reason_server_disabled)
    val serverSetupText = stringResource(R.string.pace_ai_reason_server_setup)
    val quotaText = stringResource(R.string.pace_ai_reason_quota)
    val distanceUnit = if (preferences.units == PaceUnits.Imperial) "mi" else "km"
    val distanceDivisor = if (preferences.units == PaceUnits.Imperial) 1609.344 else 1000.0
    fun request(requestKind: String) {
        val activeUserId = userId ?: return
        if (!cloudConfigured) { message = cloudMissingText; return }
        val token = container.authRepository.accessTokenOrNull() ?: run { message = signInText; return }
        scope.launch {
            busy = true
            message = null
            try {
                val response = PaceAiGatewayClient(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_PUBLISHABLE_KEY).generate(token, requestKind, summary)
                val generated = response.getJSONObject("result")
                require(generated.toString().length <= 12_000)
                if (requestKind == "insight") {
                    require(generated.optString("title").length <= 80 && generated.optString("summary").length <= 500)
                } else {
                    val blocks = generated.optJSONArray("blocks") ?: error("Invalid workout")
                    require(generated.optString("name").length <= 80 && generated.optInt("durationMinutes") in 10..120 && blocks.length() in 1..12)
                }
                result = generated
                kind = requestKind
                onPreferencesChanged(preferences.copy(cachedAiResponse = generated.toString(), cachedAiKind = requestKind, cachedAiOwnerUserId = activeUserId))
            } catch (cancellation: kotlinx.coroutines.CancellationException) {
                throw cancellation
            } catch (failure: com.pace.app.data.sync.PaceAiGatewayException) {
                message = when (failure.safeCode) {
                    "AI_DISABLED" -> serverDisabledText
                    "AI_NOT_CONFIGURED" -> serverSetupText
                    "AUTH_REQUIRED", "SESSION_INVALID" -> signInText
                    "AI_QUOTA_REACHED" -> quotaText
                    else -> unavailableText
                }
            } catch (_: Exception) {
                message = unavailableText
            } finally {
                busy = false
            }
        }
    }
    val cachedResult = result
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(PaceAppIcons.Spark, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                Text(stringResource(R.string.pace_ai_title), style = MaterialTheme.typography.titleMedium)
            }
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${summary.optInt("periodDays")}d window · ${summary.optInt("activityCount")} activities · ${"%.1f".format(summary.optDouble("distanceMeters") / distanceDivisor)} $distanceUnit",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            when {
                !cloudConfigured -> Text(cloudMissingText, style = MaterialTheme.typography.bodySmall)
                userId == null -> Text(signInText, style = MaterialTheme.typography.bodySmall)
                cachedResult != null -> {
                    Text(stringResource(R.string.pace_ai_result_title), style = MaterialTheme.typography.titleSmall)
                    if (kind == "insight") {
                        Text(cachedResult.optString("title"), style = MaterialTheme.typography.titleSmall)
                        Text(cachedResult.optString("summary"), style = MaterialTheme.typography.bodyMedium)
                        val observations = cachedResult.optJSONArray("observations") ?: JSONArray()
                        for (index in 0 until observations.length()) Text("• ${observations.optString(index)}", style = MaterialTheme.typography.bodySmall)
                        Text(cachedResult.optString("suggestedAction"), style = MaterialTheme.typography.bodyMedium)
                    } else {
                        val blocks = cachedResult.optJSONArray("blocks") ?: JSONArray()
                        Text(cachedResult.optString("rationale"), style = MaterialTheme.typography.bodySmall)
                        for (index in 0 until blocks.length()) {
                            val block = blocks.optJSONObject(index) ?: continue
                            Text("${block.optString("type").lowercase().replaceFirstChar(Char::uppercase)} · ${block.optInt("durationMinutes")} min · effort ${block.optInt("effort")}/10", style = MaterialTheme.typography.bodySmall)
                        }
                        if (preferences.aiEnabled && preferences.aiOwnerUserId == userId) {
                            OutlinedTextField(workoutName, { workoutName = it.take(80) }, label = { Text(stringResource(R.string.pace_ai_workout_name)) }, singleLine = true)
                            OutlinedTextField(workoutDuration, { workoutDuration = it.filter(Char::isDigit).take(3) }, label = { Text(stringResource(R.string.pace_ai_workout_duration)) }, singleLine = true)
                            PacePrimaryButton(
                                label = stringResource(R.string.pace_ai_save_workout),
                                enabled = !busy && workoutName.isNotBlank() && workoutDuration.toIntOrNull()?.let { it in 10..120 } == true,
                                onClick = {
                                    val duration = workoutDuration.toIntOrNull() ?: return@PacePrimaryButton
                                    scope.launch {
                                        busy = true
                                        val result = runCatching {
                                            val rows = (0 until blocks.length()).mapNotNull { blocks.optJSONObject(it) }
                                            require(rows.isNotEmpty() && duration >= rows.size)
                                            var allotted = 0
                                            val originalTotal = rows.sumOf { it.optInt("durationMinutes").coerceAtLeast(1) }
                                            val intervals = rows.mapIndexed { index, block ->
                                                val remainingBlocks = rows.size - index - 1
                                                val minutes = if (index == rows.lastIndex) (duration - allotted).coerceAtLeast(1) else
                                                    (block.optInt("durationMinutes").coerceAtLeast(1) * duration / originalTotal).coerceIn(1, duration - allotted - remainingBlocks)
                                                allotted += minutes
                                                WorkoutIntervalEntity(
                                                    templateId = UUID.randomUUID().toString(), position = index,
                                                    intervalType = block.optString("type", "WORK"), durationSeconds = minutes * 60L,
                                                )
                                            }
                                            val templateId = UUID.randomUUID().toString()
                                            container.workoutRepository.saveTemplate(
                                                WorkoutTemplateEntity(id = templateId, name = workoutName.trim(), sport = cachedResult.optString("sport", "HOME_WORKOUT"),
                                                    description = cachedResult.optString("rationale").take(500), targetDurationSeconds = duration * 60L,
                                                    createdAtEpochMillis = System.currentTimeMillis(), updatedAtEpochMillis = System.currentTimeMillis()),
                                                intervals.map { it.copy(templateId = templateId) },
                                            )
                                        }
                                        message = if (result.isSuccess) savedWorkoutText else unavailableText
                                        busy = false
                                    }
                                },
                            )
                        }
                    }
                }
                !preferences.aiEnabled || preferences.aiOwnerUserId != userId -> Text(stringResource(R.string.pace_ai_disabled), style = MaterialTheme.typography.bodySmall)
            }
            if (cloudConfigured && preferences.aiEnabled && preferences.aiOwnerUserId == userId && userId != null) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    PacePrimaryButton(
                        label = stringResource(R.string.pace_ai_insight_action),
                        onClick = { request("insight") },
                        enabled = !busy,
                        icon = PaceAppIcons.Spark,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    com.pace.app.ui.components.PaceSecondaryButton(
                        label = stringResource(R.string.pace_ai_workout_action),
                        onClick = { request("workout") },
                        enabled = !busy,
                        icon = PaceAppIcons.Plan,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            if (busy) Text(stringResource(R.string.pace_ai_loading), style = MaterialTheme.typography.bodySmall)
            message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }

}

@Composable private fun RouteTimeline(points: List<com.pace.app.data.local.entity.TrackPointEntity>, selectedIndex: Int, onSelectPoint: (Int) -> Unit) {
    val routeSpeedChartTitle = stringResource(R.string.insights_route_speed_chart)
    val speedValues = points.mapIndexed { index, point ->
        point.speedMetersPerSecond?.toDouble()?.takeIf { it.isFinite() && it >= 0.0 } ?: points.getOrNull(index + 1)?.let { next ->
            val duration = next.timestampEpochMillis - point.timestampEpochMillis
            if (duration <= 0 || duration > 30_000 || next.segmentIndex != point.segmentIndex) null
            else com.pace.app.tracking.haversineMeters(point.latitude, point.longitude, next.latitude, next.longitude) / (duration / 1000.0)
        }
    }
    Text(stringResource(R.string.insights_route_speed_chart), style = MaterialTheme.typography.titleSmall)
    val chartColor = MaterialTheme.colorScheme.primary
    val selectedColor = MaterialTheme.colorScheme.tertiary
    val context = LocalContext.current
    val finite = speedValues.filterNotNull().filter(Double::isFinite)
    if (finite.isEmpty()) {
        Text(stringResource(R.string.insights_route_speed_unavailable), style = MaterialTheme.typography.bodySmall)
        return
    }
    val min = finite.minOrNull()!!
    val range = (finite.maxOrNull()!! - min).takeIf { it > 0 } ?: 1.0
    val firstTime = points.first().timestampEpochMillis.toDouble()
    val timeSpan = (points.last().timestampEpochMillis - points.first().timestampEpochMillis).toDouble().coerceAtLeast(1.0)
    Canvas(Modifier.fillMaxWidth().height(130.dp).semantics {
        contentDescription = routeSpeedChartTitle + ": " + speedValues.indices.mapNotNull { index -> speedValues[index]?.let { "point " + (index + 1) + ", " + it + " meters per second" } }.joinToString()
    }.pointerInput(points) {
        detectTapGestures { tap ->
            val time = firstTime + tap.x / size.width * timeSpan
            onSelectPoint(points.indices.minByOrNull { kotlin.math.abs(points[it].timestampEpochMillis - time) } ?: selectedIndex)
        }
    }) {
        val chartHeight = size.height - 8.dp.toPx()
        var previous: Offset? = null
        speedValues.forEachIndexed { index, speed ->
            val current = speed?.takeIf(Double::isFinite)?.let {
                val x = ((points[index].timestampEpochMillis - firstTime) / timeSpan * size.width).toFloat()
                val y = chartHeight - ((it - min) / range * chartHeight).toFloat()
                Offset(x, y)
            }
            if (previous != null && current != null && points[index - 1].segmentIndex == points[index].segmentIndex) drawLine(chartColor, previous!!, current, 2.dp.toPx())
            if (current != null && index == selectedIndex) drawCircle(selectedColor, 7.dp.toPx(), current)
            else if (current != null) drawCircle(chartColor, 3.dp.toPx(), current)
            previous = current
        }
    }
    speedValues.getOrNull(selectedIndex)?.let { Text(stringResource(R.string.insights_route_speed_value, "%.1f".format(Locale.getDefault(), it)), style = MaterialTheme.typography.labelSmall) }
}

private fun inBucket(row: com.pace.app.data.local.entity.ActivityEntity, bucketDate: LocalDate, period: InsightsPeriod): Boolean {
    val date = Instant.ofEpochMilli(row.startedAtEpochMillis).atZone(ZoneId.systemDefault()).toLocalDate()
    return if (period == InsightsPeriod.YEAR) date.year == bucketDate.year && date.month == bucketDate.month else date == bucketDate
}

@Composable private fun InsightsTrendGraph(title: String, axisLabel: String, values: List<Double?>, labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Text(title + " · " + axisLabel, style = MaterialTheme.typography.labelMedium)
    val color = MaterialTheme.colorScheme.primary
    val focus = MaterialTheme.colorScheme.tertiary
    val finite = values.filterNotNull().filter(Double::isFinite)
    if (finite.isEmpty()) {
        Text(stringResource(R.string.insights_no_period_data), style = MaterialTheme.typography.bodySmall)
        return
    }
    val minimum = finite.minOrNull()!!; val maximum = finite.maxOrNull()!!
    Canvas(Modifier.fillMaxWidth().height(72.dp).semantics { contentDescription = title + ": " + values.indices.mapNotNull { i -> values[i]?.let { labels[i] + " " + it } }.joinToString() }.pointerInput(values) {
        detectTapGestures { point -> if (values.isNotEmpty()) onSelect((point.x / size.width * values.size).toInt().coerceIn(values.indices)) }
    }) {
        val step = size.width / values.size.coerceAtLeast(1)
        val span = (maximum - minimum).takeIf { it > 0.0 } ?: 1.0
        var previous: Offset? = null
        values.forEachIndexed { index, value ->
            val current = value?.takeIf(Double::isFinite)?.let { Offset(step * (index + 0.5f), size.height - ((it - minimum) / span * (size.height - 8.dp.toPx())).toFloat() - 4.dp.toPx()) }
            if (previous != null && current != null) drawLine(color, previous!!, current, 2.dp.toPx())
            if (current != null) drawCircle(if (index == selected) focus else color, if (index == selected) 5.dp.toPx() else 3.dp.toPx(), current)
            previous = current
        }
    }
    values.getOrNull(selected)?.let { Text(labels[selected] + ": " + "%.2f".format(Locale.getDefault(), it) + " · " + axisLabel, style = MaterialTheme.typography.labelSmall) }
}

@Composable private fun WeeklyDistanceBars(values: List<Double>, labels: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier, unit: String) {
    val active = MaterialTheme.colorScheme.primary
    val selectedColor = MaterialTheme.colorScheme.tertiary
    val maximum = (values.maxOrNull() ?: 0.0).coerceAtLeast(1.0)
    Canvas(modifier.semantics { contentDescription = "Calendar week distances in $unit: " + values.indices.joinToString { labels[it] + " " + values[it] + " $unit" } }.pointerInput(values) {
        detectTapGestures { point -> if (values.isNotEmpty()) onSelect((point.x / size.width * values.size).toInt().coerceIn(values.indices)) }
    }) {
        val gap = 4.dp.toPx()
        val cell = size.width / values.size.coerceAtLeast(1)
        val base = size.height - 5.dp.toPx()
        values.forEachIndexed { index, value ->
            val height = (value / maximum * (size.height - 12.dp.toPx())).toFloat()
            drawRect(if (index == selected) selectedColor else active, Offset(index * cell + gap, base - height), androidx.compose.ui.geometry.Size((cell - gap * 2).coerceAtLeast(1f), height))
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { labels.forEach { Text(it, style = MaterialTheme.typography.labelSmall) } }
}

@Composable private fun InsightsMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier) { Column(Modifier.padding(12.dp)) { Text(label, style = MaterialTheme.typography.labelMedium); Text(value, style = MaterialTheme.typography.titleLarge) } }
}

@Composable private fun InsightsBars(values: List<Double>, labels: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier, unit: String) {
    val line = MaterialTheme.colorScheme.primary; val focus = MaterialTheme.colorScheme.tertiary
    val maximum = (values.maxOrNull() ?: 0.0).coerceAtLeast(1.0)
    Canvas(modifier.semantics { contentDescription = "Distance in $unit: " + values.indices.joinToString { labels[it] + " " + values[it] + " " + unit } }.pointerInput(values) {
        detectTapGestures { point -> if (values.isNotEmpty()) onSelect((point.x / size.width * values.size).toInt().coerceIn(values.indices)) }
    }) {
        val step = size.width / values.size.coerceAtLeast(1); val base = size.height - 8.dp.toPx(); val path = Path()
        values.forEachIndexed { index, value ->
            val y = base - (value / maximum * (size.height - 24.dp.toPx())).toFloat(); val x = step * index + step / 2
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            drawCircle(if (index == selected) focus else line, if (index == selected) 6.dp.toPx() else 3.dp.toPx(), Offset(x, y))
        }
        if (values.size > 1) drawPath(path, line, style = Stroke(2.dp.toPx()))
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { listOf(0, labels.size / 2, labels.lastIndex).distinct().filter { it >= 0 }.forEach { Text(labels[it], style = MaterialTheme.typography.labelSmall) } }
}
