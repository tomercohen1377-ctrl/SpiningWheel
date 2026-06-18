package com.spinwheel

import com.example.spinwheel.SpinWheelAssets
import com.example.spinwheel.WidgetSyncService
import com.example.spinwheel.ui.SpinWheelWidgetReceiver
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod

/**
 * React Native Native Module: **SpinWheelModule**
 *
 * | Method                    | Description                                       |
 * |---------------------------|---------------------------------------------------|
 * | `syncWidgetConfiguration` | Download JSON + 4 images → cache → refresh widget  |
 * | `getLastFetchTime`        | Epoch-ms of last successful sync (0 if never)      |
 * | `clearCache`              | Remove all cached images + reset timestamp         |
 * | `requestWidgetUpdate`     | Ask every pinned widget instance to re-render      |
 */
class SpinWheelModule(
    private val reactContext: ReactApplicationContext,
) : ReactContextBaseJavaModule(reactContext) {

    override fun getName(): String = "SpinWheelModule"

    /**
     * Syncs the widget assets from the host app's URL.
     *
     * Runs on a dedicated `Thread` because:
     * 1. RN `@ReactMethod` calls come on the Native Modules thread,
     * 2. OkHttp + image decoding + disk I/O must not block it.
     */
    @ReactMethod
    fun syncWidgetConfiguration(
        configUrl: String,
        bgUrl: String,
        wheelUrl: String,
        frameUrl: String,
        spinUrl: String,
        promise: Promise,
    ) {
        Thread {
            try {
                val assets = SpinWheelAssets(
                    bgUrl    = bgUrl,
                    wheelUrl = wheelUrl,
                    frameUrl = frameUrl,
                    spinUrl  = spinUrl,
                )
                val success = WidgetSyncService.fetchAndCache(
                    reactContext, configUrl, assets,
                )
                if (success) {
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
            val ts = WidgetSyncService.lastFetchEpoch(reactContext)
            promise.resolve(ts.toDouble())
        } catch (e: Exception) {
            promise.reject("PREFS_ERROR", e.localizedMessage ?: "Unknown error", e)
        }
    }

    @ReactMethod
    fun clearCache(promise: Promise) {
        Thread {
            try {
                kotlinx.coroutines.runBlocking {
                    com.example.spinwheel.di.SpinWheelGraph
                        .get(reactContext)
                        .repository
                        .clear()
                }
                SpinWheelWidgetReceiver.requestUpdate(reactContext)
                promise.resolve(true)
            } catch (e: Exception) {
                promise.reject("CLEAR_ERROR", e.localizedMessage ?: "Unknown error", e)
            }
        }.start()
    }

    @ReactMethod
    fun requestWidgetUpdate(promise: Promise) {
        try {
            SpinWheelWidgetReceiver.requestUpdate(reactContext)
            promise.resolve(true)
        } catch (e: Exception) {
            promise.reject("UPDATE_ERROR", e.localizedMessage ?: "Unknown error", e)
        }
    }
}
