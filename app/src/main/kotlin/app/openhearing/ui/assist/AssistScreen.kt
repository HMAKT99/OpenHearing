package app.openhearing.ui.assist

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.openhearing.assist.AssistService
import app.openhearing.common.SafetyConstants
import kotlinx.coroutines.launch

/**
 * Assist-mode screen: turn on real-time amplification using the saved profile.
 * Requests microphone (and notification) permission, then starts the foreground
 * [AssistService]. A large Stop control is always available (instant off).
 */
@Composable
fun AssistScreen(onBack: () -> Unit, viewModel: AssistViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val permissions =
        buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()

    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val micGranted = result[Manifest.permission.RECORD_AUDIO] == true
            if (micGranted) {
                scope.launch {
                    if (viewModel.prepare()) AssistService.start(context)
                }
            }
        }

    fun startAssist() {
        val micGranted =
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        if (micGranted) {
            scope.launch { if (viewModel.prepare()) AssistService.start(context) }
        } else {
            launcher.launch(permissions)
        }
    }

    Column(
        modifier =
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Text("Hearing assist", style = MaterialTheme.typography.headlineSmall)
        SafetyNote()

        if (!state.hasProfile) {
            Text(
                "Run the hearing screening first so assist mode can use your profile.",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(vertical = 16.dp),
            )
            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
            return@Column
        }

        Text(
            if (state.running) "Assist is ON" else "Assist is off",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 8.dp),
        )

        GainControl(
            masterGainDb = state.masterGainDb,
            onChange = viewModel::setMasterGain,
            enabled = !state.running,
        )

        Spacer(Modifier.padding(8.dp))
        if (state.running) {
            Button(
                onClick = { AssistService.stop(context) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
            ) { Text("Stop assist", style = MaterialTheme.typography.titleMedium) }
        } else {
            Button(
                onClick = ::startAssist,
                modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
            ) { Text("Start assist", style = MaterialTheme.typography.titleMedium) }
        }

        Spacer(Modifier.padding(4.dp))
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
    }
}

@Composable
private fun GainControl(masterGainDb: Double, onChange: (Double) -> Unit, enabled: Boolean) {
    Card(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text("Amplification: +${masterGainDb.toInt()} dB", style = MaterialTheme.typography.labelLarge)
            Slider(
                value = masterGainDb.toFloat(),
                onValueChange = { onChange(it.toDouble()) },
                valueRange = 0f..SafetyConstants.MAX_MASTER_GAIN_CAP_DB.toFloat(),
                enabled = enabled,
                modifier = Modifier.semantics { contentDescription = "Amplification level in decibels" },
            )
            if (!enabled) {
                Text(
                    "Stop assist to change the amplification level.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun SafetyNote() {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Text(
            "Amplifies sound around you in real time. Output is hard-limited for safety, " +
                "but keep the level comfortable and stop if anything is too loud. Not a medical device.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(12.dp),
        )
    }
}
