package app.openhearing.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.openhearing.R
import app.openhearing.ui.assist.AssistScreen
import app.openhearing.ui.hearingtest.HearingTestScreen
import app.openhearing.ui.theme.OpenHearingTheme

private enum class Screen { HOME, HEARING_TEST, ASSIST, SETTINGS }

@Composable
fun OpenHearingApp(rootViewModel: RootViewModel = hiltViewModel()) {
    val root by rootViewModel.uiState.collectAsStateWithLifecycle()

    OpenHearingTheme(highContrast = root.highContrast) {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            Column(modifier = Modifier.padding(innerPadding)) {
                when (root.consentAccepted) {
                    null -> Unit // loading
                    false -> OnboardingScreen(onAccept = rootViewModel::acceptDisclaimer)
                    true -> MainNav()
                }
            }
        }
    }
}

@Composable
private fun MainNav() {
    var screen by remember { mutableStateOf(Screen.HOME) }
    when (screen) {
        Screen.HOME ->
            HomeScreen(
                onRunHearingTest = { screen = Screen.HEARING_TEST },
                onAssist = { screen = Screen.ASSIST },
                onSettings = { screen = Screen.SETTINGS },
            )
        Screen.HEARING_TEST -> HearingTestScreen(onBack = { screen = Screen.HOME })
        Screen.ASSIST -> AssistScreen(onBack = { screen = Screen.HOME })
        Screen.SETTINGS -> SettingsScreen(onBack = { screen = Screen.HOME })
    }
}

@Composable
private fun HomeScreen(onRunHearingTest: () -> Unit, onAssist: () -> Unit, onSettings: () -> Unit) {
    Column(
        modifier =
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
        Text(
            stringResource(R.string.tagline),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 8.dp),
        )
        DisclaimerCard(modifier = Modifier.padding(top = 24.dp))

        HomeButton("Run hearing screening", onRunHearingTest)
        HomeButton("Hearing assist", onAssist)
        HomeButton("Settings", onSettings)
    }
}

@Composable
private fun HomeButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(top = 12.dp),
    ) { Text(label, style = MaterialTheme.typography.titleMedium) }
}

@Composable
private fun OnboardingScreen(onAccept: () -> Unit) {
    Column(
        modifier =
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
        Text(
            "Welcome",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = 8.dp),
        )
        DisclaimerCard(modifier = Modifier.padding(top = 16.dp))
        Text(
            "Please read and acknowledge the above before using OpenHearing.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 16.dp),
        )
        Button(
            onClick = onAccept,
            modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(top = 16.dp),
        ) { Text("I understand and agree", style = MaterialTheme.typography.titleMedium) }
    }
}

@Composable
private fun SettingsScreen(onBack: () -> Unit, rootViewModel: RootViewModel = hiltViewModel()) {
    val root by rootViewModel.uiState.collectAsStateWithLifecycle()
    Column(
        modifier =
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("High-contrast theme", style = MaterialTheme.typography.bodyLarge)
            Switch(checked = root.highContrast, onCheckedChange = rootViewModel::setHighContrast)
        }

        ComfortCalibration(
            ceiling = root.comfortCeiling,
            onChange = rootViewModel::setComfortCeiling,
            onPreview = { rootViewModel.previewComfort(root.comfortCeiling) },
        )

        DisclaimerCard(modifier = Modifier.padding(top = 24.dp))
        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
        ) { Text("Back") }
    }
}

@Composable
private fun ComfortCalibration(ceiling: Float, onChange: (Float) -> Unit, onPreview: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text("Comfort calibration", style = MaterialTheme.typography.titleSmall)
            Text(
                "Sets the maximum loudness assist mode will ever reach. Tap Preview, " +
                    "then lower the level if the tone is too loud. (For a precise dB SPL " +
                    "calibration you'd need a sound-level meter — see docs.)",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            Slider(
                value = ceiling,
                onValueChange = onChange,
                valueRange = 0.1f..0.9f,
                modifier = Modifier.semantics { contentDescription = "Maximum comfortable loudness" },
            )
            OutlinedButton(onClick = onPreview, modifier = Modifier.fillMaxWidth()) {
                Text("Preview maximum loudness")
            }
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
                stringResource(R.string.disclaimer_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                stringResource(R.string.disclaimer_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(top = 8.dp),
            )
            Spacer(Modifier.padding(2.dp))
        }
    }
}
