package com.pace.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                PaceWelcomeScreen()
            }
        }
    }
}

@Composable
private fun PaceWelcomeScreen() {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.welcome_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.welcome_body),
                modifier = Modifier.padding(top = 12.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            BuildInformation(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp),
            )
            SampleDataDeveloperControl(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
            )
        }
    }
}

@Composable
private fun BuildInformation(modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.build_info_title),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = stringResource(R.string.build_info_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(R.string.build_info_variant, BuildConfig.BUILD_TYPE),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun SampleDataDeveloperControl(modifier: Modifier = Modifier) {
    if (!BuildConfig.DEBUG || !BuildConfig.SAMPLE_DATA_ENABLED) return

    var sampleDataEnabled by rememberSaveable { mutableStateOf(false) }
    val sampleDataLabel = stringResource(R.string.sample_data_toggle)

    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = sampleDataLabel,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Switch(
                    checked = sampleDataEnabled,
                    onCheckedChange = { sampleDataEnabled = it },
                    modifier = Modifier
                        .testTag("sample-data-toggle")
                        .semantics {
                            contentDescription = sampleDataLabel
                        },
                )
            }

            if (sampleDataEnabled) {
                Text(
                    text = stringResource(R.string.sample_data_title),
                    modifier = Modifier.padding(top = 12.dp),
                    style = MaterialTheme.typography.titleSmall,
                )
                PaceSampleData.activities.forEach { activity ->
                    val sampleText = when (activity.kind) {
                        SampleActivityKind.Run -> stringResource(
                            R.string.sample_activity_run,
                            activity.distanceMeters / 1000f,
                            activity.durationMinutes,
                        )

                        SampleActivityKind.Cycle -> stringResource(
                            R.string.sample_activity_cycle,
                            activity.distanceMeters / 1000f,
                            activity.durationMinutes,
                        )
                    }
                    Text(
                        text = sampleText,
                        modifier = Modifier.padding(top = 4.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PaceWelcomeScreenPreview() {
    MaterialTheme { PaceWelcomeScreen() }
}
