package com.pace.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.pace.app.ui.theme.PaceTheme

@Preview(name = "Pace components · light", showBackground = true, widthDp = 420, heightDp = 900)
@Composable
private fun PaceComponentGalleryLightPreview() {
    PaceTheme(darkTheme = false) { PaceComponentGallery() }
}

@Preview(name = "Pace components · dark", showBackground = true, widthDp = 420, heightDp = 900)
@Composable
private fun PaceComponentGalleryDarkPreview() {
    PaceTheme(darkTheme = true) { PaceComponentGallery() }
}

@Composable
private fun PaceComponentGallery() {
    val icon = remember { previewMarkIcon() }
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Pace · component states", style = MaterialTheme.typography.headlineMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PacePrimaryButton("Continue", onClick = {})
            PacePrimaryButton("Saving", onClick = {}, loading = true)
            PaceSecondaryButton("Secondary", onClick = {}, enabled = false)
        }
        PaceDestructiveAction("Delete activity", "Delete activity?", "This action cannot be undone.", "Delete", "Cancel", {})
        PaceTextField(value = "Morning run", onValueChange = {}, label = "Activity name", helperText = "Shown only in your history")
        PaceTextField(value = "", onValueChange = {}, label = "Password", password = true, errorText = "Enter a password")
        PaceSegmentedControl(listOf(PaceSegment("week", "Week"), PaceSegment("month", "Month"), PaceSegment("year", "Year")), "month", {})
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PaceSport.values().forEachIndexed { index, sport ->
                PaceSportSelectorTile(sport, listOf("Cycling", "Running", "Jogging", "Walking")[index], index == 1, {})
            }
        }
        PaceLargeMetric("Distance", "12.48", "km", supportingText = "This activity")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PaceStatisticComparison("Distance", "12.48", "km", PaceComparison.Absolute("+1.20", "km"), Modifier.weight(1f))
            PaceStatisticComparison("Moving time", "54", "min", PaceComparison.Relative("8% faster"), Modifier.weight(1f))
        }
        PaceStatisticComparison("Elevation", "—", "m", PaceComparison.Missing("Not available for this activity"))
        PaceLoadingSkeleton(lines = 3, label = "Loading activity summary")
        PaceEmptyState(
            title = "No activities yet",
            message = "Record a walk, jog, run, or ride to see it here.",
            actionLabel = "Record",
            onAction = {},
        )
        PaceOfflineBanner("Offline · saved activities remain available")
        PaceNetworkFailure("Backup unavailable", "Your local activity is safe. Try the backup again when connected.", "Retry", {})
        PacePermissionGuidance("Location access", "Location is used only while recording an activity.", "Allow location", "Open settings", true, {}, {})
        PacePermissionGuidance("Location permission needed", "Allow location to record an outdoor activity.", "Allow location", "Open settings", false, {}, {})
        PaceErrorDialogPreview()
        PaceChartTooltip("Week 24", "18.6", "km", "Week 24, 18.6 kilometers")
        PaceMapControls(listOf(PaceMapControl("Zoom in", icon, {}), PaceMapControl("My location", icon, {})))
        PaceWorkoutIntervalChip("Work", "Interval 2 of 5", 86, active = true)
        PaceWorkoutIntervalChip("Rest", "Interval 3 of 5", 30, active = false)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PaceCalendarDayChip("Mon", "12", true, {})
            PaceCalendarEventChip("Easy run", PaceCalendarStatus.Scheduled)
            PaceCalendarEventChip("Walk", PaceCalendarStatus.Completed)
            PaceCalendarEventChip("Intervals", PaceCalendarStatus.Missed)
        }
        PaceNumericTransition("12:48", contentDescription = "Elapsed time, 12 minutes 48 seconds")
        PaceBottomNavigation(
            destinations = listOf(
                PaceNavigationDestination("home", "Home", icon),
                PaceNavigationDestination("record", "Record", icon),
                PaceNavigationDestination("maps", "Maps", icon),
                PaceNavigationDestination("plan", "Plan", icon, enabled = false),
                PaceNavigationDestination("insights", "Insights", icon),
            ),
            selectedRoute = "home",
            onSelect = {},
        )
    }
}

@Composable
private fun PaceErrorDialogPreview() {
    PaceErrorDialog(
        title = "Could not save changes",
        message = "Your saved activity is safe. Check the details and try again.",
        confirmLabel = "OK",
        onConfirm = {},
        onDismiss = {},
    )
}

private fun previewMarkIcon(): ImageVector = ImageVector.Builder(
    name = "PacePreviewMark",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(fill = SolidColor(Color(0xFF18343A))) {
        moveTo(4f, 5f)
        lineTo(20f, 5f)
        lineTo(20f, 9f)
        lineTo(4f, 9f)
        close()
        moveTo(4f, 12f)
        lineTo(20f, 12f)
        lineTo(20f, 16f)
        lineTo(4f, 16f)
        close()
        moveTo(4f, 19f)
        lineTo(20f, 19f)
        lineTo(20f, 22f)
        lineTo(4f, 22f)
        close()
    }
}.build()
