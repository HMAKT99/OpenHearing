package app.openhearing.ui.manualentry

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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.openhearing.R
import app.openhearing.common.Ear

/**
 * Manual audiogram entry screen: one slider per ear and pitch (dB HL, 5 dB
 * steps). Intended for people who already have results from a professional
 * hearing test and want assist mode without running the on-device check.
 */
@Composable
fun ManualEntryScreen(onBack: () -> Unit, viewModel: ManualEntryViewModel = hiltViewModel()) {
    val levels by viewModel.levels.collectAsStateWithLifecycle()

    Column(
        modifier =
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Text(stringResource(R.string.manual_title), style = MaterialTheme.typography.headlineSmall)
        Text(
            stringResource(R.string.manual_intro),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(vertical = 12.dp),
        )

        listOf(Ear.RIGHT, Ear.LEFT).forEach { ear ->
            EarCard(
                ear = ear,
                frequencies = viewModel.frequencies,
                levels = levels[ear].orEmpty(),
                onChange = { freq, db -> viewModel.setLevel(ear, freq, db) },
            )
        }

        Button(
            onClick = { viewModel.save(onSaved = onBack) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(top = 16.dp),
        ) { Text(stringResource(R.string.manual_save), style = MaterialTheme.typography.titleMedium) }
        Spacer(Modifier.padding(4.dp))
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.back))
        }
    }
}

@Composable
private fun EarCard(ear: Ear, frequencies: List<Double>, levels: Map<Double, Int>, onChange: (Double, Int) -> Unit) {
    val earName =
        stringResource(if (ear == Ear.RIGHT) R.string.ear_right else R.string.ear_left)
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.ear_label, earName),
                style = MaterialTheme.typography.titleSmall,
            )
            frequencies.forEach { freq ->
                val level = levels[freq] ?: 0
                val sliderDescription =
                    stringResource(R.string.manual_slider, earName, freq.toInt())
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(stringResource(R.string.hz_value, freq.toInt()))
                    Text(stringResource(R.string.manual_value, level))
                }
                Slider(
                    value = level.toFloat(),
                    onValueChange = {
                        val stepped =
                            (it / ManualEntryViewModel.LEVEL_STEP_DB).toInt() *
                                ManualEntryViewModel.LEVEL_STEP_DB
                        onChange(freq, stepped)
                    },
                    valueRange =
                    ManualEntryViewModel.MIN_LEVEL_DB_HL.toFloat()..ManualEntryViewModel.MAX_LEVEL_DB_HL.toFloat(),
                    modifier = Modifier.semantics { contentDescription = sliderDescription },
                )
            }
        }
    }
}
