package com.pace.app.ui.components

import android.animation.ValueAnimator
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pace.app.ui.theme.LocalReducedMotion

data class PaceNavigationDestination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector = selectedIcon,
    val enabled: Boolean = true,
    val emphasized: Boolean = false,
)

@Composable
fun PaceBottomNavigation(
    destinations: List<PaceNavigationDestination>,
    selectedRoute: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    require(destinations.isNotEmpty()) { "Bottom navigation needs at least one destination." }
    NavigationBar(modifier = modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.surface) {
        destinations.forEach { destination ->
            val selected = selectedRoute == destination.route
            val colors = MaterialTheme.colorScheme
            NavigationBarItem(
                selected = selected,
                onClick = { onSelect(destination.route) },
                enabled = destination.enabled,
                icon = {
                    val image = if (selected) destination.selectedIcon else destination.unselectedIcon
                    if (destination.emphasized) {
                        Surface(shape = RoundedCornerShape(20.dp), color = colors.primary, contentColor = colors.onPrimary) {
                            Box(Modifier.size(width = 52.dp, height = 32.dp), contentAlignment = Alignment.Center) {
                                androidx.compose.material3.Icon(image, contentDescription = null)
                            }
                        }
                    } else {
                        androidx.compose.material3.Icon(image, contentDescription = null)
                    }
                },
                label = { Text(destination.label) },
                alwaysShowLabel = true,
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                    indicatorColor = if (destination.emphasized) androidx.compose.ui.graphics.Color.Transparent else colors.primaryContainer,
                ),
                modifier = Modifier.semantics { contentDescription = destination.label },
            )
        }
    }
}

enum class PaceSport { Cycling, Running, Jogging, Walking }

@Composable
fun PaceSportSelectorTile(
    sport: PaceSport,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selectionRole: Role = Role.RadioButton,
) {
    val colors = MaterialTheme.colorScheme
    val motion = !LocalReducedMotion.current && ValueAnimator.areAnimatorsEnabled()
    val tileColor by animateColorAsState(
        targetValue = if (selected) colors.primaryContainer else colors.surface,
        animationSpec = if (motion) tween(180) else snap(),
        label = "Sport selection",
    )
    Surface(
        modifier = modifier.defaultMinSize(minWidth = 96.dp, minHeight = 64.dp)
            .selectable(selected = selected, onClick = onClick, role = selectionRole),
        shape = RoundedCornerShape(20.dp),
        color = tileColor,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            ) {
                Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                    PaceSportGlyph(sport, androidx.compose.material3.LocalContentColor.current, Modifier.size(24.dp))
                }
            }
            Text(label, style = MaterialTheme.typography.titleMedium)
        }
    }
}
