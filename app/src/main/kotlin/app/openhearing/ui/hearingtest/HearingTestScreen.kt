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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.openhearing.R
import app.openhearing.audiogram.Audiogram
import app.openhearing.common.Ear

/**
 * Hearing-check screen: runs the pure-tone screening through the phone speaker or
 * any connected headset (no AirPods needed), then shows the result as an
 * audiogram-style chart plus detailed tables and the suggested amplification.
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
        Text(stringResource(R.string.check_title), style = MaterialTheme.typography.headlineSmall)
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
            stringResource(R.string.check_notice),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(12.dp),
        )
    }
}

@Composable
private fun NotStarted(onStart: () -> Unit, onBack: () -> Unit) {
    Column {
        Text(
            stringResource(R.string.check_intro),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
        )
        BigButton(stringResource(R.string.check_start), onClick = onStart)
        Spacer(Modifier.padding(4.dp))
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.back))
        }
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
            stringResource(R.string.check_progress, state.completed + 1, state.total),
            style = MaterialTheme.typography.labelLarge,
        )
        Text(
            stringResource(
                R.string.check_ear_freq,
                earLabel(state.currentEar),
                state.currentFrequencyHz?.toInt() ?: 0,
            ),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            if (state.isPlaying) {
                stringResource(R.string.check_playing)
            } else {
                stringResource(R.string.check_question)
            },
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 16.dp, bottom = 12.dp),
        )

        BigButton(stringResource(R.string.check_heard), onClick = viewModel::onHeard)
        Spacer(Modifier.padding(4.dp))
        BigButton(stringResource(R.string.check_not_heard), onClick = viewModel::onNotHeard)
        Spacer(Modifier.padding(4.dp))
        OutlinedButton(
            onClick = viewModel::replay,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        ) { Text(stringResource(R.string.check_replay)) }

        SafetyControls(state = state, viewModel = viewModel)
    }
}

@Composable
private fun SafetyControls(state: HearingTestUiState, viewModel: HearingTestViewModel) {
    val sliderDescription = stringResource(R.string.check_volume_cap_slider)
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.check_volume_cap), style = MaterialTheme.typography.labelLarge)
            Slider(
                value = state.masterCap,
                onValueChange = viewModel::setMasterCap,
                modifier = Modifier.semantics { contentDescription = sliderDescription },
            )
            Button(
                onClick = viewModel::mute,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) { Text(stringResource(R.string.check_mute)) }
        }
    }
}

@Composable
private fun Results(state: HearingTestUiState, onRestart: () -> Unit, onBack: () -> Unit) {
    Column {
        Text(
            stringResource(R.string.check_complete),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
        )
        state.audiogram?.let { ResultsChart(it) }
        state.audiogram?.let { AudiogramTable(it) }
        state.gains.forEach { GainTable(it) }
        Text(
            stringResource(R.string.check_results_disclaimer),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(vertical = 16.dp),
        )
        BigButton(stringResource(R.string.check_run_again), onClick = onRestart)
        Spacer(Modifier.padding(4.dp))
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.back))
        }
    }
}

@Composable
private fun ResultsChart(audiogram: Audiogram) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.check_chart_title), style = MaterialTheme.typography.titleSmall)
            AudiogramChart(audiogram, modifier = Modifier.padding(top = 12.dp))
            Text(
                stringResource(R.string.check_chart_hint),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun AudiogramTable(audiogram: Audiogram) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.check_table_title), style = MaterialTheme.typography.titleSmall)
            listOf(Ear.RIGHT, Ear.LEFT).forEach { ear ->
                Text(
                    stringResource(R.string.ear_label, earLabel(ear)),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 8.dp),
                )
                audiogram.frequenciesFor(ear).forEach { f ->
                    val hl = audiogram.thresholdAt(ear, f)?.value?.toInt()
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.hz_value, f.value.toInt()))
                        Text(stringResource(R.string.db_hl_value, hl?.toString() ?: "—"))
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
                stringResource(R.string.gain_title, earLabel(summary.ear)),
                style = MaterialTheme.typography.titleSmall,
            )
            summary.points.forEach { p ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.hz_value, p.frequency.value.toInt()))
                    Text(stringResource(R.string.gain_db_value, p.gainDb.toInt()))
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

@Composable
private fun earLabel(ear: Ear?): String = when (ear) {
    Ear.LEFT -> stringResource(R.string.ear_left)
    Ear.RIGHT -> stringResource(R.string.ear_right)
    null -> "—"
}
