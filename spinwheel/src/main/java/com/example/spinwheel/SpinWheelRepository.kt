package com.example.spinwheel

import android.content.Context
import android.graphics.Bitmap
import android.util.Log

private const val TAG = "SpinWheelRepository"

/**
 * Single source of truth for widget data.
 *
 * Orchestrates the full sync pipeline:
 * ```
 * syncAssets()
 *   1. RemoteConfigFetcher.fetchWheelConfigJson()   ← Firebase RC (suspend)
 *   2. WidgetSyncService.fetchAndCacheFromJson()    ← OkHttp downloads (suspend)
 * ```
 *
 * Both steps are `suspend` functions. The caller (e.g. [WheelWidgetWorker])
 * awaits [syncAssets] — the widget must only be updated AFTER it returns.
 *
 * Also exposes read-only helpers ([getCachedBitmap], [getLastFetchTime]) used
 * by [SpinWheelGlanceWidget] when it reads bitmaps to render the widget.
 */
class SpinWheelRepository(private val context: Context) {

    private val syncService = WidgetSyncService(context)

    /**
     * Fetches the latest `wheel_config` from Firebase Remote Config, then
     * downloads and caches all four image assets.
     *
     * This is a **suspend function** — it does not return until both the RC
     * fetch and all file downloads have completed (or failed).
     *
     * @return `true` if all assets are now on disk; `false` on any failure.
     */
    suspend fun syncAssets(): Boolean {
        Log.d(TAG, "syncAssets() — fetching config from Firebase RC…")

        val configJson = RemoteConfigFetcher.fetchWheelConfigJson()
        if (configJson == null) {
            Log.w(TAG, "Firebase RC returned null — widget will render cached assets (if any)")
            return false
        }

        Log.d(TAG, "Config received (${configJson.length} chars) — downloading assets…")
        val success = syncService.fetchAndCacheFromJson(configJson)

        if (success) {
            Log.d(TAG, "syncAssets() complete ✓")
        } else {
            Log.e(TAG, "syncAssets() — asset download failed")
        }

        return success
    }

    /**
     * Returns a decoded [Bitmap] for [fileName] from the local cache,
     * or `null` if not yet downloaded.
     */
    fun getCachedBitmap(fileName: String): Bitmap? =
        syncService.getCachedBitmap(fileName)

    /** Epoch-ms of the last successful [syncAssets] call, or 0 if never synced. */
    fun getLastFetchTime(): Long = syncService.getLastFetchTime()

    /** Deletes all cached image files and resets the sync timestamp. */
    fun clearCache() = syncService.clearCache()
}
