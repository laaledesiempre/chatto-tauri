package run.chatto.desktop

import android.app.Application
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.util.Log
import androidx.activity.ComponentActivity
import io.livekit.android.LiveKit
import io.livekit.android.RoomOptions
import io.livekit.android.e2ee.E2EEOptions
import io.livekit.android.room.Room
import io.livekit.android.room.track.screencapture.ScreenCaptureParams
import kotlinx.coroutines.*

/**
 * Manages a native LiveKit peer used only for Android screen sharing.
 *
 * The peer connects as a separate participant and publishes a screen share
 * video track. This works around Android WebView's lack of getDisplayMedia
 * support.
 */
object ScreenShareManager {

    private const val TAG = "ChattoScreenShareMgr"

    @Volatile
    private var room: Room? = null

    @Volatile
    private var isStarting = false

    @Volatile
    private var pendingData: Intent? = null

    @Volatile
    private var isConnected = false

    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Start screen sharing. This launches the MediaProjection permission dialog
     * if needed, then connects to LiveKit and publishes the screen share track.
     */
    fun start(context: Context, livekitUrl: String, token: String, e2eeKey: String?) {
        if (isStarting || isConnected) return
        isStarting = true

        managerScope.launch {
            try {
                val data = requestMediaProjection(context)
                    ?: throw IllegalStateException("Media projection permission denied")

                connectAndPublish(context.applicationContext, livekitUrl, token, e2eeKey, data)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start screen share", e)
                isStarting = false
            }
        }
    }

    /**
     * Stop screen sharing and disconnect from LiveKit.
     */
    fun stop() {
        managerScope.launch {
            try {
                room?.localParticipant?.setScreenShareEnabled(false)
                room?.disconnect()
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping screen share", e)
            } finally {
                room = null
                isConnected = false
                isStarting = false
            }
        }
    }

    /**
     * Request the MediaProjection permission. If the context is an Activity we use
     * its ActivityResultLauncher. Otherwise we fall back to the transparent
     * trampoline activity.
     */
    private suspend fun requestMediaProjection(context: Context): Intent? = suspendCancellableCoroutine { cont ->
        val activity = context as? ComponentActivity
        if (activity != null) {
            try {
                val launcher = activity.activityResultRegistry.register(
                    "chatto_screen_share",
                    androidx.activity.result.ActivityResultContracts.StartActivityForResult()
                ) { result ->
                    if (result.resultCode == ComponentActivity.RESULT_OK) {
                        cont.resume(result.data) {}
                    } else {
                        cont.resume(null) {}
                    }
                }
                val mediaProjectionManager = activity.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                launcher.launch(mediaProjectionManager.createScreenCaptureIntent())
            } catch (e: Exception) {
                Log.e(TAG, "Failed to register activity result", e)
                cont.resume(null) {}
            }
        } else {
            // Fallback: start transparent trampoline activity.
            pendingData = null
            val intent = Intent(context, ScreenSharePermissionActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)

            // Wait for the activity to deliver the result.
            managerScope.launch(Dispatchers.Main) {
                var waited = 0
                while (pendingData == null && waited < 60_000 && cont.isActive) {
                    delay(100)
                    waited += 100
                }
                cont.resume(pendingData) { }
                pendingData = null
            }
        }
    }

    /**
     * Called by [ScreenSharePermissionActivity] when the user accepts/denies
     * the MediaProjection dialog.
     */
    internal fun onMediaProjectionResult(data: Intent?) {
        pendingData = data
    }

    private suspend fun connectAndPublish(
        context: Context,
        livekitUrl: String,
        token: String,
        e2eeKey: String?,
        data: Intent
    ) = withContext(Dispatchers.IO) {
        val options = if (!e2eeKey.isNullOrBlank()) {
            RoomOptions(e2eeOptions = E2EEOptions(e2eeKey))
        } else {
            RoomOptions()
        }
        val newRoom = LiveKit.create(context, options)
        room = newRoom

        newRoom.connect(livekitUrl, token)

        val localParticipant = newRoom.localParticipant
        localParticipant.setScreenShareEnabled(true, ScreenCaptureParams(data))

        isConnected = true
        isStarting = false
        Log.i(TAG, "Screen share started")
    }
}
