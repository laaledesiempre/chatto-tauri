package run.chatto.desktop

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat

/**
 * Foreground service that keeps the screen share MediaProjection alive while
 * the app is in the background. The actual LiveKit connection and capture
 * logic lives in [ScreenShareManager].
 */
class ScreenShareService : Service() {

    companion object {
        private const val TAG = "ChattoScreenShareSvc"
        private const val CHANNEL_ID = "chatto_screen_share"
        private const val NOTIFICATION_ID = 2

        private const val EXTRA_LIVEKIT_URL = "livekit_url"
        private const val EXTRA_TOKEN = "token"
        private const val EXTRA_E2EE_KEY = "e2ee_key"

        fun start(context: Context, livekitUrl: String, token: String, e2eeKey: String?) {
            val intent = Intent(context, ScreenShareService::class.java).apply {
                putExtra(EXTRA_LIVEKIT_URL, livekitUrl)
                putExtra(EXTRA_TOKEN, token)
                putExtra(EXTRA_E2EE_KEY, e2eeKey)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ScreenShareService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val livekitUrl = intent?.getStringExtra(EXTRA_LIVEKIT_URL)
        val token = intent?.getStringExtra(EXTRA_TOKEN)
        val e2eeKey = intent?.getStringExtra(EXTRA_E2EE_KEY)

        if (livekitUrl.isNullOrBlank() || token.isNullOrBlank()) {
            Log.w(TAG, "Missing livekitUrl or token, stopping")
            stopSelf(startId)
            return START_NOT_STICKY
        }

        try {
            startForeground(NOTIFICATION_ID, buildNotification())
        } catch (e: Exception) {
            Log.w(TAG, "Could not start foreground: ${e.message}")
            stopSelf(startId)
            return START_NOT_STICKY
        }

        ScreenShareManager.start(this, livekitUrl, token, e2eeKey)
        return START_STICKY
    }

    override fun onDestroy() {
        ScreenShareManager.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Screen share",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Ongoing screen share"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending = android.app.PendingIntent.getActivity(
            this,
            0,
            intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Chatto")
            .setContentText("Sharing screen")
            .setSmallIcon(R.drawable.ic_stat_chatto)
            .setContentIntent(pending)
            .setOngoing(true)
            .build()
    }
}
