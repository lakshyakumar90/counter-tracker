package com.pace.app.ui.components

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.pace.app.R
import com.pace.app.ui.theme.paceColors
import com.pace.app.ui.theme.LocalReducedMotion
import kotlinx.coroutines.launch

@Composable
fun PaceLoadingSkeleton(
    modifier: Modifier = Modifier,
    lines: Int = 3,
    label: String,
) {
    require(lines > 0) { "Skeleton needs at least one line." }
    Column(
        modifier = modifier.semantics { contentDescription = label; liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(lines) { index ->
            Box(
                Modifier
                    .fillMaxWidth(if (index == lines - 1) 0.62f else 1f)
                    .height(if (index == 0) 24.dp else 16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
        }
    }
}

@Composable
fun PaceEmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (actionLabel != null && onAction != null) PacePrimaryButton(actionLabel, onAction)
    }
}

@Composable
fun PaceOfflineBanner(
    message: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth().semantics { stateDescription = message; liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite },
        color = MaterialTheme.paceColors.informationContainer,
        contentColor = MaterialTheme.paceColors.information,
        shape = MaterialTheme.shapes.small,
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun PaceNetworkFailure(
    title: String,
    message: String,
    retryLabel: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    retrying: Boolean = false,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            PacePrimaryButton(label = retryLabel, onClick = onRetry, loading = retrying)
        }
    }
}

@Composable
fun PacePermissionGuidance(
    title: String,
    rationale: String,
    requestLabel: String,
    settingsLabel: String,
    shouldOpenSettings: Boolean,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(rationale, style = MaterialTheme.typography.bodyMedium)
            PacePrimaryButton(
                label = if (shouldOpenSettings) settingsLabel else requestLabel,
                onClick = if (shouldOpenSettings) onOpenSettings else onRequestPermission,
            )
        }
    }
}

@Composable
fun PaceChartTooltip(
    title: String,
    value: String,
    unit: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = description },
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        tonalElevation = 2.dp,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(value, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(4.dp))
                Text(unit, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun PaceMapControls(
    controls: List<PaceMapControl>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        controls.forEach { control ->
            PaceCircularAction(
                image = { androidx.compose.material3.Icon(control.icon, contentDescription = null) },
                label = control.label,
                onClick = control.onClick,
                enabled = control.enabled,
            )
        }
    }
}

data class PaceMapControl(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
)

@Composable
fun PaceWorkoutIntervalChip(
    phaseLabel: String,
    intervalLabel: String,
    remainingSeconds: Int,
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    require(remainingSeconds >= 0) { "Remaining time cannot be negative." }
    val minutes = remainingSeconds / 60
    val seconds = remainingSeconds % 60
    val timer = java.util.Locale.getDefault().let { locale -> "%d:%02d".format(locale, minutes, seconds) }
    val timerDescription = stringResource(R.string.pace_workout_timer_accessibility, phaseLabel, intervalLabel, timer)
    val inactiveLabel = stringResource(R.string.pace_interval_inactive)
    Surface(
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = timerDescription
            stateDescription = if (active) phaseLabel else inactiveLabel
        },
        color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(50),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(phaseLabel, style = MaterialTheme.typography.labelLarge)
            Text(intervalLabel, style = MaterialTheme.typography.bodyMedium)
            Text(timer, style = MaterialTheme.typography.labelLarge, fontFamily = MaterialTheme.typography.displaySmall.fontFamily)
        }
    }
}

enum class PaceCalendarStatus { Scheduled, Completed, Missed }

@Composable
fun PaceCalendarDayChip(
    dayLabel: String,
    dateLabel: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    androidx.compose.material3.FilterChip(
        selected = selected,
        onClick = onClick,
        modifier = modifier.semantics { contentDescription = "$dayLabel, $dateLabel" },
        label = { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(dayLabel); Text(dateLabel, style = MaterialTheme.typography.labelMedium) } },
    )
}

@Composable
fun PaceCalendarEventChip(
    title: String,
    status: PaceCalendarStatus,
    modifier: Modifier = Modifier,
) {
    val (background, foreground, statusText) = when (status) {
        PaceCalendarStatus.Scheduled -> Triple(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer, stringResource(R.string.pace_calendar_scheduled))
        PaceCalendarStatus.Completed -> Triple(MaterialTheme.paceColors.successContainer, MaterialTheme.paceColors.success, stringResource(R.string.pace_calendar_completed))
        PaceCalendarStatus.Missed -> Triple(MaterialTheme.paceColors.warningContainer, MaterialTheme.paceColors.warning, stringResource(R.string.pace_calendar_missed))
    }
    Surface(
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = "$title, $statusText" },
        color = background,
        contentColor = foreground,
        shape = MaterialTheme.shapes.small,
    ) { Text(title, Modifier.padding(horizontal = 10.dp, vertical = 8.dp), style = MaterialTheme.typography.labelMedium) }
}

@Composable
fun PaceSnackbar(
    hostState: SnackbarHostState,
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val show: () -> Unit = {
        scope.launch {
            val result = hostState.showSnackbar(message, actionLabel)
            if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) onAction?.invoke()
        }
    }
    androidx.compose.runtime.LaunchedEffect(message) { show() }
}

@Composable
fun PaceNumericTransition(
    value: String,
    modifier: Modifier = Modifier,
    contentDescription: String,
) {
    val context = LocalContext.current
    val reducedMotion = LocalReducedMotion.current
    val animationsEnabled = remember(context, reducedMotion) { systemAnimationsEnabled(context) && !reducedMotion }
    androidx.compose.animation.AnimatedContent(
        targetState = value,
        modifier = modifier.semantics { this.contentDescription = contentDescription },
        transitionSpec = {
            androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(if (animationsEnabled) 120 else 0)) togetherWith
                androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(if (animationsEnabled) 80 else 0))
        },
        label = "pace-metric-transition",
    ) { current ->
        Text(current, style = MaterialTheme.typography.displayMedium, fontFamily = MaterialTheme.typography.displayLarge.fontFamily)
    }
}

private fun systemAnimationsEnabled(context: Context): Boolean = runCatching {
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
}.getOrDefault(true)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaceModalBottomSheet(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!visible) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        content = content,
    )
}
