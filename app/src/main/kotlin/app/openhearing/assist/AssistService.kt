package app.openhearing.assist

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.openhearing.MainActivity
import app.openhearing.R
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Foreground service that runs assist mode (continuous microphone capture +
 * processing). A foreground service with the `microphone` type is the required
 * Android pattern for ongoing mic use; the persistent notification keeps the user
 * aware the mic is live and offers a one-tap **Stop**.
 *
 * The actual engine lives in [AssistController]; this service owns the
 * foreground lifecycle and starts/stops it.
 */
@AndroidEntryPoint
class AssistService : Service() {
    @Inject
    lateinit var controller: AssistController

    // Headphones unplugged / Bluetooth disconnected: stop immediately rather than
    // fall back to the phone speaker, where mic->speaker feedback (howl) is likely.
    private val becomingNoisyReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) stopSelf()
            }
        }

    override fun onCreate() {
        super.onCreate()
        ContextCompat.registerReceiver(
            this,
            becomingNoisyReceiver,
            IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForegroundNotification()
        controller.startEngine()
        return START_STICKY
    }

    override fun onDestroy() {
        unregisterReceiver(becomingNoisyReceiver)
        controller.stopEngine()
        super.onDestroy()
    }

    private fun startForegroundNotification() {
        createChannel()
        val openIntent =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE,
            )
        val stopIntent =
            PendingIntent.getService(
                this,
                1,
                Intent(this, AssistService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE,
            )
        val notification =
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.assist_active_title))
                .setContentText(getString(R.string.assist_active_text))
                .setSmallIcon(R.drawable.ic_stat_assist)
                .setOngoing(true)
                .setContentIntent(openIntent)
                .addAction(0, getString(R.string.assist_stop), stopIntent)
                .build()

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }

    private fun createChannel() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel =
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.assist_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            )
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "assist"
        private const val NOTIFICATION_ID = 1
        const val ACTION_STOP = "app.openhearing.assist.STOP"

        fun start(context: Context) {
            val intent = Intent(context, AssistService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, AssistService::class.java).setAction(ACTION_STOP),
            )
        }
    }
}
