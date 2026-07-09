package run.chatto.desktop

import android.app.Activity
import android.content.Context
import android.util.Log

/**
 * Bridge called from Rust via JNI. Exposes static methods so we don't need to
 * pass an Activity reference through Tauri's command layer.
 */
object ScreenShareBridge {

    private const val TAG = "ChattoScreenShareBridge"

    /**
     * Start screen sharing. The [context] should be the application context or an
     * Activity. We use the [Activity] if available, otherwise start the
     * transparent trampoline permission activity.
     */
    @JvmStatic
    fun start(context: Context, livekitUrl: String, token: String, e2eeKey: String?) {
        Log.i(TAG, "Starting screen share")
        ScreenShareService.start(context, livekitUrl, token, e2eeKey)
    }

    /**
     * Stop screen sharing.
     */
    @JvmStatic
    fun stop(context: Context) {
        Log.i(TAG, "Stopping screen share")
        ScreenShareService.stop(context)
    }

    /**
     * Returns whether the manager is currently connected and sharing.
     */
    @JvmStatic
    fun isSharing(): Boolean {
        return ScreenShareManager.isConnected
    }
}
