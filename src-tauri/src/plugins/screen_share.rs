use serde::Serialize;
use tauri::plugin::{Builder, PluginHandle, TauriPlugin};
use tauri::{AppHandle, Manager, Runtime, Wry};

pub fn init() -> TauriPlugin<Wry> {
    Builder::new("screenShare")
        .invoke_handler(tauri::generate_handler![start, stop, is_sharing])
        .setup(|app, api| {
            #[cfg(target_os = "android")]
            {
                let handle = api
                    .register_android_plugin("run.chatto.desktop", "ScreenSharePlugin")
                    .map_err(|e| e.to_string())?;
                app.manage(AndroidHandle { handle });
            }
            let _ = app;
            Ok(())
        })
        .build()
}

#[cfg(target_os = "android")]
struct AndroidHandle {
    handle: PluginHandle<Wry>,
}

#[derive(Serialize)]
struct StartPayload {
    livekit_url: String,
    token: String,
    e2ee_key: Option<String>,
}

#[derive(serde::Deserialize, Debug)]
struct BoolResult {
    active: bool,
}

#[tauri::command]
fn start(
    app: AppHandle<Wry>,
    livekit_url: String,
    token: String,
    e2ee_key: Option<String>,
) -> Result<(), String> {
    #[cfg(target_os = "android")]
    {
        let state = app.state::<AndroidHandle>();
        state
            .handle
            .run_mobile_plugin::<()>(
                "start",
                StartPayload {
                    livekit_url,
                    token,
                    e2ee_key,
                },
            )
            .map_err(|e| e.to_string())?;
    }
    #[cfg(not(target_os = "android"))]
    {
        let _ = (app, livekit_url, token, e2ee_key);
    }
    Ok(())
}

#[tauri::command]
fn stop(app: AppHandle<Wry>) -> Result<(), String> {
    #[cfg(target_os = "android")]
    {
        let state = app.state::<AndroidHandle>();
        state
            .handle
            .run_mobile_plugin::<()>("stop", serde_json::json!({}))
            .map_err(|e| e.to_string())?;
    }
    #[cfg(not(target_os = "android"))]
    {
        let _ = app;
    }
    Ok(())
}

#[tauri::command]
fn is_sharing(app: AppHandle<Wry>) -> Result<bool, String> {
    #[cfg(target_os = "android")]
    {
        let state = app.state::<AndroidHandle>();
        let result = state
            .handle
            .run_mobile_plugin::<BoolResult>("isSharing", serde_json::json!({}))
            .map_err(|e| e.to_string())?;
        Ok(result.active)
    }
    #[cfg(not(target_os = "android"))]
    {
        let _ = app;
        Ok(false)
    }
}
