package run.chatto.desktop

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.util.Log

/**
 * Transparent trampoline activity used to request the MediaProjection permission
 * when the screen share is triggered from outside of an Activity context (e.g.
 * from a background service or a Rust command).
 */
class ScreenSharePermissionActivity : Activity() {

    companion object {
        private const val TAG = "ChattoScreenSharePerm"
        private const val REQUEST_CODE = 1001

        fun start(context: Context) {
            val intent = Intent(context, ScreenSharePermissionActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // No setContentView: keep the activity transparent.
        try {
            val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            startActivityForResult(mediaProjectionManager.createScreenCaptureIntent(), REQUEST_CODE)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to request media projection", e)
            ScreenShareManager.onMediaProjectionResult(null)
            finish()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE) {
            if (resultCode == RESULT_OK && data != null) {
                ScreenShareManager.onMediaProjectionResult(data)
            } else {
                ScreenShareManager.onMediaProjectionResult(null)
            }
        }
        finish()
    }
}
