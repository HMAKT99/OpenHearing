package app.openhearing.ui.assist

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.openhearing.R
import app.openhearing.assist.AssistService
import app.openhearing.common.SafetyConstants
import app.openhearing.data.HearingProfile
import kotlinx.coroutines.launch

/**
 * Assist-mode screen: turn on real-time amplification using the saved profile.
 * Requests microphone (and notification) permission, then starts the foreground
 * [AssistService]. A large Stop control is always available (instant off); the
 * amplification slider applies live while running. Warns before starting on the
 * phone speaker, where mic->speaker feedback (howl) is likely.
 */
@Composable
fun AssistScreen(onBack: () -> Unit, viewModel: AssistViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var speakerWarning by remember { mutableStateOf(false) }

    val permissions =
        buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()

    fun proceedStart() {
        speakerWarning = false
        scope.launch { if (viewModel.prepare()) AssistService.start(context) }
    }

    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val micGranted = result[Manifest.permission.RECORD_AUDIO] == true
            if (micGranted) {
                if (headphonesConnected(context)) proceedStart() else speakerWarning = true
            }
        }

    fun startAssist() {
        val micGranted =
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        when {
            !micGranted -> launcher.launch(permissions)
            !headphonesConnected(context) && !speakerWarning -> speakerWarning = true
            else -> proceedStart()
        }
    }

    Column(
        modifier =
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Text(stringResource(R.string.assist_title), style = MaterialTheme.typography.headlineSmall)
        SafetyNote()

        if (!state.hasProfile) {
            Text(
                stringResource(R.string.assist_no_profile),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(vertical = 16.dp),
            )
        } else {
            AssistControls(
                state = state,
                speakerWarning = speakerWarning,
                onStart = ::startAssist,
                onStop = { AssistService.stop(context) },
                onGainChange = viewModel::setMasterGain,
                onSelectProfile = viewModel::selectProfile,
                onDeleteProfile = viewModel::deleteProfile,
            )
        }

        Spacer(Modifier.padding(4.dp))
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.back))
        }
    }
}

@Composable
private fun AssistControls(
    state: AssistUiState,
    speakerWarning: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onGainChange: (Double) -> Unit,
    onSelectProfile: (String) -> Unit,
    onDeleteProfile: (String) -> Unit,
) {
    Text(
        if (state.running) {
            stringResource(R.string.assist_on)
        } else {
            stringResource(R.string.assist_off)
        },
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 8.dp),
    )

    GainControl(masterGainDb = state.masterGainDb, onChange = onGainChange)

    if (speakerWarning && !state.running) SpeakerWarning()

    Spacer(Modifier.padding(8.dp))
    if (state.running) {
        Button(
            onClick = onStop,
            modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
        ) { Text(stringResource(R.string.assist_stop_button), style = MaterialTheme.typography.titleMedium) }
    } else {
        Button(
            onClick = onStart,
            modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
        ) {
            Text(
                if (speakerWarning) {
                    stringResource(R.string.assist_start_anyway)
                } else {
                    stringResource(R.string.assist_start)
                },
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }

    if (state.profiles.size > 1) {
        ProfilesCard(
            profiles = state.profiles,
            activeProfileId = state.activeProfileId,
            running = state.running,
            onSelect = onSelectProfile,
            onDelete = onDeleteProfile,
        )
    }
}

private fun headphonesConnected(context: Context): Boolean {
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val headphoneTypes =
        setOf(
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_HEARING_AID,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
        )
    return audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { it.type in headphoneTypes }
}

@Composable
private fun GainControl(masterGainDb: Double, onChange: (Double) -> Unit) {
    val sliderDescription = stringResource(R.string.assist_amplification_slider)
    Card(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.assist_amplification, masterGainDb.toInt()),
                style = MaterialTheme.typography.labelLarge,
            )
            Slider(
                value = masterGainDb.toFloat(),
                onValueChange = { onChange(it.toDouble()) },
                valueRange = 0f..SafetyConstants.MAX_MASTER_GAIN_CAP_DB.toFloat(),
                modifier = Modifier.semantics { contentDescription = sliderDescription },
            )
        }
    }
}

@Composable
private fun ProfilesCard(
    profiles: List<HearingProfile>,
    activeProfileId: String?,
    running: Boolean,
    onSelect: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.profiles_title), style = MaterialTheme.typography.titleSmall)
            profiles.forEach { profile ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .selectable(
                            selected = profile.id == activeProfileId,
                            role = Role.RadioButton,
                            onClick = { onSelect(profile.id) },
                        ),
                ) {
                    RadioButton(selected = profile.id == activeProfileId, onClick = null)
                    Text(
                        profile.name,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(start = 8.dp).weight(1f),
                    )
                    TextButton(onClick = { onDelete(profile.id) }) {
                        Text(stringResource(R.string.profile_delete))
                    }
                }
            }
            if (running) {
                Text(
                    stringResource(R.string.profile_switch_while_running),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun SpeakerWarning() {
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Text(
            stringResource(R.string.assist_speaker_warning),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Composable
private fun SafetyNote() {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Text(
            stringResource(R.string.assist_safety_note),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(12.dp),
        )
    }
}
