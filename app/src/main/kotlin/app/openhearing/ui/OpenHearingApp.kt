package app.openhearing.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.openhearing.R
import app.openhearing.ui.hearingtest.HearingTestScreen
import app.openhearing.ui.theme.OpenHearingTheme

/**
 * Phase 0 root UI. It shows only the project title and the mandatory safety /
 * legal disclaimer — every health-related surface in the app must carry this,
 * and onboarding (Phase 4) will require acknowledging it before any test starts.
 * Feature screens (screening, assist mode, AirPods) are wired up in later phases.
 */
private enum class Screen { HOME, HEARING_TEST }

@Composable
fun OpenHearingApp() {
    OpenHearingTheme {
        var screen by remember { mutableStateOf(Screen.HOME) }
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            Column(modifier = Modifier.padding(innerPadding)) {
                when (screen) {
                    Screen.HOME -> HomeScreen(onRunHearingTest = { screen = Screen.HEARING_TEST })
                    Screen.HEARING_TEST -> HearingTestScreen(onBack = { screen = Screen.HOME })
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(onRunHearingTest: () -> Unit) {
    Column(
        modifier =
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = stringResource(R.string.tagline),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 8.dp),
        )
        DisclaimerCard(modifier = Modifier.padding(top = 24.dp))
        Button(
            onClick = onRunHearingTest,
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
        ) {
            Text("Run hearing screening (debug)")
        }
    }
}

@Composable
private fun DisclaimerCard(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.disclaimer_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                text = stringResource(R.string.disclaimer_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun OpenHearingAppPreview() {
    OpenHearingApp()
}
