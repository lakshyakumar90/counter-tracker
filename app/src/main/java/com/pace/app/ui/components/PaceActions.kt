package com.pace.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.pace.app.ui.theme.paceColors

@Composable
fun PacePrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    Button(
        onClick = onClick,
        modifier = modifier.widthIn(min = 48.dp),
        enabled = enabled && !loading,
        shape = androidx.compose.material3.MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(
            containerColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
            contentColor = androidx.compose.material3.MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onPrimary,
                )
            }
            Text(label)
        }
    }
}

@Composable
fun PaceSecondaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(onClick = onClick, modifier = modifier.widthIn(min = 48.dp), enabled = enabled) {
        Text(label)
    }
}

@Composable
fun PaceTextAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    TextButton(onClick = onClick, modifier = modifier.widthIn(min = 48.dp), enabled = enabled) {
        Text(label)
    }
}

@Composable
fun PaceDestructiveAction(
    label: String,
    dialogTitle: String,
    dialogMessage: String,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var showConfirmation by remember { mutableStateOf(false) }
    OutlinedButton(
        onClick = { showConfirmation = true },
        modifier = modifier.widthIn(min = 48.dp),
        enabled = enabled,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = androidx.compose.material3.MaterialTheme.paceColors.error),
    ) { Text(label) }

    if (showConfirmation) {
        AlertDialog(
            onDismissRequest = { showConfirmation = false },
            title = { Text(dialogTitle) },
            text = { Text(dialogMessage) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConfirmation = false
                        onConfirm()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = androidx.compose.material3.MaterialTheme.paceColors.error),
                ) { Text(confirmLabel) }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmation = false }) { Text(dismissLabel) }
            },
        )
    }
}

@Composable
fun PaceTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    helperText: String? = null,
    errorText: String? = null,
    password: Boolean = false,
    enabled: Boolean = true,
    singleLine: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = { Text(label) },
        enabled = enabled,
        singleLine = singleLine,
        isError = errorText != null,
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        supportingText = {
            when {
                errorText != null -> Text(errorText)
                helperText != null -> Text(helperText)
            }
        },
    )
}

data class PaceSegment<T>(val value: T, val label: String)

@Composable
fun <T> PaceSegmentedControl(
    options: List<PaceSegment<T>>,
    selected: T,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    require(options.isNotEmpty()) { "A segmented control needs at least one option." }
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option.value == selected,
                onClick = { onSelected(option.value) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                modifier = Modifier.semantics { contentDescription = option.label },
            ) { Text(option.label) }
        }
    }
}

@Composable
fun PaceErrorDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissLabel: String? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = dismissLabel?.let { label ->
            { TextButton(onClick = onDismiss) { Text(label) } }
        },
    )
}

@Composable
fun PaceCircularAction(
    image: @Composable () -> Unit,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    androidx.compose.material3.IconButton(
        onClick = onClick,
        modifier = modifier.size(48.dp).semantics { contentDescription = label },
        enabled = enabled,
    ) {
        androidx.compose.material3.Surface(shape = CircleShape, color = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant) {
            androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) { image() }
        }
    }
}
