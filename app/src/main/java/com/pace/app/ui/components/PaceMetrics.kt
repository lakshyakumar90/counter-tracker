package com.pace.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pace.app.ui.theme.PaceSpace
import com.pace.app.ui.theme.paceColors

@Composable
fun PaceLargeMetric(
    label: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    contentDescription: String = "$label: $value $unit",
) {
    Column(modifier = modifier.semantics(mergeDescendants = true) { this.contentDescription = contentDescription }) {
        Text(label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(value, style = MaterialTheme.typography.displayMedium, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.width(PaceSpace.xs))
            Text(unit, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (supportingText != null) {
            Text(supportingText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

sealed interface PaceComparison {
    data class Absolute(val value: String, val unit: String) : PaceComparison
    data class Relative(val percent: String) : PaceComparison
    data class Missing(val explanation: String) : PaceComparison
}

@Composable
fun PaceStatisticComparison(
    label: String,
    currentValue: String,
    currentUnit: String,
    comparison: PaceComparison,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(PaceSpace.md), verticalArrangement = Arrangement.spacedBy(PaceSpace.xs)) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            Text("$currentValue $currentUnit", style = MaterialTheme.typography.headlineSmall, fontFamily = FontFamily.Monospace)
            when (comparison) {
                is PaceComparison.Absolute -> Text(
                    "${comparison.value} ${comparison.unit}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                is PaceComparison.Relative -> Text(
                    comparison.percent,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.paceColors.information,
                )
                is PaceComparison.Missing -> Text(
                    comparison.explanation,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
