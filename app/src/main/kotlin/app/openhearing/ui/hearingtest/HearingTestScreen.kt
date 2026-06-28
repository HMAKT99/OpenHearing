package app.openhearing.ui.hearingtest

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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.openhearing.audiogram.Audiogram
import app.openhearing.common.Ear

/**
 * Phase 1 debug screen: runs the pure-tone screening through the phone speaker or
 * any connected headset (no AirPods needed), then shows the resulting audiogram
 * and the prescribed half-gain curve. Lets the maintainer exercise the engine on
 * real hardware.
 */
@Composable
fun HearingTestScreen(onBack: () -> Unit, viewModel: HearingTestViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier =
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Text("Hearing screening (debug)", style = MaterialTheme.typography.headlineSmall)
        CalibrationNotice()

        when (state.phase) {
            TestPhase.NOT_STARTED ->
                NotStarted(onStart = viewModel::start, onBack = onBack)
            TestPhase.IN_PROGRESS ->
                InProgress(state = state, viewModel = viewModel)
            TestPhase.DONE ->
                Results(state = state, onRestart = viewModel::start, onBack = onBack)
        }
    }
}

@Composable
private fun CalibrationNotice() {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Text(
            "Estimate only — uncalibrated. This screening is not a medical test and " +
                "not a substitute for a professional hearing exam. Use headphones in a " +
                "quiet room. Keep the volume comfortable; stop if anything is too loud.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(12.dp),
        )
    }
}

@Composable
private fun NotStarted(onStart: () -> Unit, onBack: () -> Unit) {
    Column {
        Text(
            "You'll hear quiet tones at different pitches in each ear. After each " +
                "tone, tap whether you heard it. It takes a couple of minutes.",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
        )
        BigButton("Start screening", onClick = onStart)
        Spacer(Modifier.padding(4.dp))
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
    }
}

@Composable
private fun InProgress(state: HearingTestUiState, viewModel: HearingTestViewModel) {
    Column {
        LinearProgressIndicator(
            progress = { state.progress },
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        )
        Text(
            "Point ${state.completed + 1} of ${state.total}",
            style = MaterialTheme.typography.labelLarge,
        )
        Text(
            earLabel(state.currentEar) + " ear · ${state.currentFrequencyHz?.toInt() ?: "—"} Hz",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            if (state.isPlaying) "Playing tone…" else "Did you hear the tone?",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 16.dp, bottom = 12.dp),
        )

        BigButton("Yes, I heard it", onClick = viewModel::onHeard)
        Spacer(Modifier.padding(4.dp))
        BigButton("No, I didn't", onClick = viewModel::onNotHeard)
        Spacer(Modifier.padding(4.dp))
        OutlinedButton(
            onClick = viewModel::replay,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        ) { Text("Play tone again") }

        SafetyControls(state = state, viewModel = viewModel)
    }
}

@Composable
private fun SafetyControls(state: HearingTestUiState, viewModel: HearingTestViewModel) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Volume cap", style = MaterialTheme.typography.labelLarge)
            Slider(
                value = state.masterCap,
                onValueChange = viewModel::setMasterCap,
                modifier = Modifier.semantics { contentDescription = "Maximum volume cap" },
            )
            Button(
                onClick = viewModel::mute,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) { Text("Stop / mute now") }
        }
    }
}

@Composable
private fun Results(state: HearingTestUiState, onRestart: () -> Unit, onBack: () -> Unit) {
    Column {
        Text(
            "Screening complete",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
        )
        state.audiogram?.let { AudiogramTable(it) }
        state.gains.forEach { GainTable(it) }
        Text(
            "These are uncalibrated estimates, not a diagnosis. See an audiologist " +
                "for a professional hearing assessment.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(vertical = 16.dp),
        )
        BigButton("Run again", onClick = onRestart)
        Spacer(Modifier.padding(4.dp))
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
    }
}

@Composable
private fun AudiogramTable(audiogram: Audiogram) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text("Audiogram (estimated dB HL)", style = MaterialTheme.typography.titleSmall)
            listOf(Ear.RIGHT, Ear.LEFT).forEach { ear ->
                Text(
                    "${earLabel(ear)} ear",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 8.dp),
                )
                audiogram.frequenciesFor(ear).forEach { f ->
                    val hl = audiogram.thresholdAt(ear, f)?.value?.toInt()
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${f.value.toInt()} Hz")
                        Text("${hl ?: "—"} dB HL")
                    }
                }
            }
        }
    }
}

@Composable
private fun GainTable(summary: GainSummary) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "Prescribed gain — ${earLabel(summary.ear)} ear (half-gain rule)",
                style = MaterialTheme.typography.titleSmall,
            )
            summary.points.forEach { p ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${p.frequency.value.toInt()} Hz")
                    Text("+${p.gainDb.toInt()} dB")
                }
            }
        }
    }
}

@Composable
private fun BigButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier =
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp),
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium)
    }
}

private fun earLabel(ear: Ear?): String = when (ear) {
    Ear.LEFT -> "Left"
    Ear.RIGHT -> "Right"
    null -> "—"
}
