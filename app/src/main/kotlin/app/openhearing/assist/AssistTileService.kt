package app.openhearing.assist

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat
import app.openhearing.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Quick-settings tile: one-tap assist on/off from anywhere. If the mic permission
 * or a usable profile is missing, it opens the app instead of starting. The tile
 * mirrors the shared [AssistController] state, so it stays in sync with the
 * in-app controls.
 */
@AndroidEntryPoint
class AssistTileService : TileService() {
    @Inject
    lateinit var controller: AssistController

    @Inject
    lateinit var sessionFactory: AssistSessionFactory

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var listenJob: Job? = null

    override fun onStartListening() {
        listenJob =
            scope.launch {
                controller.running.collect { running -> renderTile(running) }
            }
    }

    override fun onStopListening() {
        listenJob?.cancel()
        listenJob = null
    }

    override fun onClick() {
        if (controller.running.value) {
            AssistService.stop(this)
            return
        }
        val micGranted =
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        if (!micGranted) {
            openApp()
            return
        }
        scope.launch {
            if (sessionFactory.prepare()) AssistService.start(this@AssistTileService) else openApp()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun renderTile(running: Boolean) {
        val tile = qsTile ?: return
        tile.state = if (running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }

    private fun openApp() {
        val intent =
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE),
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
