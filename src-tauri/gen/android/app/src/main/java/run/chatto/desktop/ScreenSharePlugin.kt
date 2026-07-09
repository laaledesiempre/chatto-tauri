package run.chatto.desktop

import android.app.Activity
import android.content.Context
import android.util.Log
import app.tauri.annotation.Command
import app.tauri.annotation.TauriPlugin
import app.tauri.plugin.JSObject
import app.tauri.plugin.Plugin
import app.tauri.plugin.Invoke

@TauriPlugin
class ScreenSharePlugin(private val activity: Activity) : Plugin(activity) {

    private val tag = "ChattoScreenSharePlugin"

    @Command
    fun start(invoke: Invoke) {
        val ctx = activity.applicationContext
        val url = invoke.getString("livekit_url")
        val token = invoke.getString("token")
        val e2eeKey = invoke.getString("e2ee_key")

        if (url.isNullOrBlank() || token.isNullOrBlank()) {
            Log.w(tag, "livekit_url or token missing")
            invoke.reject("livekit_url and token are required")
            return
        }

        ScreenShareService.start(ctx, url, token, e2eeKey)
        invoke.resolve()
    }

    @Command
    fun stop(invoke: Invoke) {
        val ctx = activity.applicationContext
        ScreenShareService.stop(ctx)
        invoke.resolve()
    }

    @Command
    fun isSharing(invoke: Invoke) {
        val ret = JSObject()
        ret.put("active", ScreenShareManager.isConnected)
        invoke.resolve(ret)
    }
}
