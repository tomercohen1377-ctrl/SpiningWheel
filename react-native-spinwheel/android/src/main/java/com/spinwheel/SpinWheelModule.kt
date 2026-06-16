package com.spinwheel

import com.example.spinwheel.SpinWheelAssets
import com.example.spinwheel.SpinWheelWidgetReceiver
import com.example.spinwheel.WidgetSyncService
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod

/**
 * React Native Native Module: **SpinWheelModule**
 *
 * | Method                    | Description                                       |
 * |---------------------------|---------------------------------------------------|
 * | `syncWidgetConfiguration` | Download JSON + 4 images → cache → refresh widget |
 * | `getLastFetchTime`        | Epoch-ms of last successful sync (0 if never)     |
 * | `clearCache`              | Remove all cached images + reset timestamp        |
 */
class SpinWheelModule(
    private val reactContext: ReactApplicationContext
) : ReactContextBaseJavaModule(reactContext) {

    override fun getName(): String = "SpinWheelModule"

    /**
     * Syncs the widget assets from Google Drive.
     *
     * @param configUrl  Remote JSON config URL (for spin duration / settings).
     * @param bgUrl      Direct download URL for the background image.
     * @param wheelUrl   Direct download URL for the spinning wheel image.
     * @param frameUrl   Direct download URL for the static frame overlay.
     * @param spinUrl    Direct download URL for the spin button image.
     * @param promise    Resolves `true` on success; rejects on failure.
     */
    @ReactMethod
    fun syncWidgetConfiguration(
        configUrl: String,
        bgUrl: String,
        wheelUrl: String,
        frameUrl: String,
        spinUrl: String,
        promise: Promise
    ) {
        Thread {
            try {
                val assets = SpinWheelAssets(
                    bgUrl    = bgUrl,
                    wheelUrl = wheelUrl,
                    frameUrl = frameUrl,
                    spinUrl  = spinUrl
                )
                val success = WidgetSyncService(reactContext).fetchAndCache(configUrl, assets)
                if (success) {
                    SpinWheelWidgetReceiver.requestUpdate(reactContext)
                    promise.resolve(true)
                } else {
                    promise.reject("SYNC_ERROR", "Asset download failed — check logs.")
                }
            } catch (e: Exception) {
                promise.reject("SYNC_ERROR", e.localizedMessage ?: "Unknown error", e)
            }
        }.start()
    }

    @ReactMethod
    fun getLastFetchTime(promise: Promise) {
        try {
            promise.resolve(WidgetSyncService(reactContext).getLastFetchTime().toDouble())
        } catch (e: Exception) {
            promise.reject("PREFS_ERROR", e.localizedMessage ?: "Unknown error", e)
        }
    }

    @ReactMethod
    fun clearCache(promise: Promise) {
        Thread {
            try {
                WidgetSyncService(reactContext).clearCache()
                promise.resolve(true)
            } catch (e: Exception) {
                promise.reject("CLEAR_ERROR", e.localizedMessage ?: "Unknown error", e)
            }
        }.start()
    }
}
