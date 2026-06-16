package com.example.spinwheel

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.example.spinwheel.data.WheelConfigResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

private const val TAG = "WidgetSyncService"

/** SharedPreferences file shared between the widget, host app, and RN module. */
const val PREFS_NAME          = "SpinWheelPrefs"
const val KEY_LAST_FETCH_TIME = "last_config_fetch_time"
const val KEY_CONFIG_URL      = "config_url"

// Persisted asset URL keys
const val KEY_URL_BG    = "asset_url_bg"
const val KEY_URL_WHEEL = "asset_url_wheel"
const val KEY_URL_FRAME = "asset_url_frame"
const val KEY_URL_SPIN  = "asset_url_spin"

// Cached file names (inside Context.filesDir)
const val FILE_BG    = "sw_bg.png"
const val FILE_WHEEL = "sw_wheel.png"
const val FILE_FRAME = "sw_frame.png"
const val FILE_SPIN  = "sw_spin.png"

/**
 * Explicit asset URLs used by the React Native [SpinWheelModule].
 */
data class SpinWheelAssets(
    val bgUrl:    String,
    val wheelUrl: String,
    val frameUrl: String,
    val spinUrl:  String
)

/**
 * Handles downloading and disk-caching the four spin wheel image assets.
 *
 * All network and file I/O methods are `suspend` functions that dispatch
 * to [Dispatchers.IO], so they are safe to call directly from a
 * [kotlinx.coroutines.CoroutineScope] or [androidx.work.CoroutineWorker]
 * without manually switching dispatchers at the call site.
 *
 * URL construction: `host.trimEnd('/') + '/' + fileId`
 *
 * The Firebase RC JSON should provide Drive **file IDs** (not filenames) as
 * asset values, paired with the lh3 host:
 * ```json
 * "network": { "assets": { "host": "https://lh3.googleusercontent.com/d/" } },
 * "wheel":   { "assets": { "bg": "FILE_ID", "wheel": "FILE_ID", ... } }
 * ```
 * `lh3.googleusercontent.com/d/FILE_ID` serves JPEG/PNG regardless of the
 * original format, so BitmapFactory can decode it on all Android versions.
 */
class WidgetSyncService(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // ─────────────────────────────────────────────────────────────────────── //
    //  Public API                                                             //
    // ─────────────────────────────────────────────────────────────────────── //

    /**
     * Parses [configJson] (from Firebase RC), builds asset URLs as
     * `host + fileId`, then downloads and caches all four images.
     *
     * **This is a suspend function.** All blocking OkHttp and file I/O runs
     * on [Dispatchers.IO]. The caller awaits completion — the widget must
     * only be updated AFTER this function returns `true`.
     *
     * Files already cached (size > 1 KB) are skipped unless the URL changed.
     *
     * @return `true` on full success; `false` if parsing or any download fails.
     */
    suspend fun fetchAndCacheFromJson(configJson: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val response = json.decodeFromString(WheelConfigResponse.serializer(), configJson)
                val item     = response.data.firstOrNull()
                    ?: throw IOException("Config data[] is empty")

                val host   = item.network.assets.host.trim()
                val assets = item.wheel.assets

                Log.d(TAG, "fetchAndCacheFromJson — host=$host  " +
                        "spinDuration=${item.wheel.rotation.duration}ms")

                if (host.isBlank() || !host.startsWith("http")) {
                    throw IOException(
                        "Invalid host in wheel_config: \"$host\". " +
                        "Must start with http."
                    )
                }

                val base = if (host.endsWith('/')) host else "$host/"

                val newBgUrl    = base + assets.bg
                val newWheelUrl = base + assets.wheel
                val newFrameUrl = base + assets.wheelFrame
                val newSpinUrl  = base + assets.wheelSpin

                Log.d(TAG, "Asset URLs:\n" +
                        "  bg    = $newBgUrl\n" +
                        "  wheel = $newWheelUrl\n" +
                        "  frame = $newFrameUrl\n" +
                        "  spin  = $newSpinUrl")

                // Invalidate disk cache if any URL changed
                invalidateIfChanged(newBgUrl,    KEY_URL_BG,    FILE_BG)
                invalidateIfChanged(newWheelUrl, KEY_URL_WHEEL, FILE_WHEEL)
                invalidateIfChanged(newFrameUrl, KEY_URL_FRAME, FILE_FRAME)
                invalidateIfChanged(newSpinUrl,  KEY_URL_SPIN,  FILE_SPIN)

                // Download all four assets — each call awaits its own download
                downloadFile(newBgUrl,    FILE_BG,    "background")
                downloadFile(newWheelUrl, FILE_WHEEL, "wheel")
                downloadFile(newFrameUrl, FILE_FRAME, "frame")
                downloadFile(newSpinUrl,  FILE_SPIN,  "spin button")

                // Persist URLs + timestamp AFTER all downloads succeed
                prefs.edit()
                    .putLong(KEY_LAST_FETCH_TIME, System.currentTimeMillis())
                    .putString(KEY_URL_BG,    newBgUrl)
                    .putString(KEY_URL_WHEEL, newWheelUrl)
                    .putString(KEY_URL_FRAME, newFrameUrl)
                    .putString(KEY_URL_SPIN,  newSpinUrl)
                    .apply()

                Log.d(TAG, "fetchAndCacheFromJson complete ✓ — all 4 assets cached")
                true
            } catch (e: Exception) {
                Log.e(TAG, "fetchAndCacheFromJson failed: ${e.message}", e)
                false
            }
        }

    /**
     * Downloads the given explicit [SpinWheelAssets] URLs and caches them.
     * Called by the React Native [SpinWheelModule].
     */
    suspend fun fetchAndCache(configUrl: String, assets: SpinWheelAssets): Boolean =
        withContext(Dispatchers.IO) {
            try {
                downloadFile(assets.bgUrl,    FILE_BG,    "background")
                downloadFile(assets.wheelUrl, FILE_WHEEL, "wheel")
                downloadFile(assets.frameUrl, FILE_FRAME, "frame")
                downloadFile(assets.spinUrl,  FILE_SPIN,  "spin button")

                prefs.edit()
                    .putLong(KEY_LAST_FETCH_TIME, System.currentTimeMillis())
                    .putString(KEY_CONFIG_URL, configUrl)
                    .putString(KEY_URL_BG,    assets.bgUrl)
                    .putString(KEY_URL_WHEEL, assets.wheelUrl)
                    .putString(KEY_URL_FRAME, assets.frameUrl)
                    .putString(KEY_URL_SPIN,  assets.spinUrl)
                    .apply()

                true
            } catch (e: Exception) {
                Log.e(TAG, "fetchAndCache failed: ${e.message}", e)
                false
            }
        }

    fun getLastFetchTime(): Long = prefs.getLong(KEY_LAST_FETCH_TIME, 0L)

    fun clearCache() {
        listOf(FILE_BG, FILE_WHEEL, FILE_FRAME, FILE_SPIN)
            .forEach { File(context.filesDir, it).delete() }
        prefs.edit()
            .remove(KEY_LAST_FETCH_TIME).remove(KEY_CONFIG_URL)
            .remove(KEY_URL_BG).remove(KEY_URL_WHEEL)
            .remove(KEY_URL_FRAME).remove(KEY_URL_SPIN)
            .apply()
        Log.d(TAG, "Cache cleared")
    }

    /**
     * Returns a [Bitmap] decoded from the cached file, or `null` if not cached.
     * This is a fast file-read — safe to call on any dispatcher.
     */
    fun getCachedBitmap(fileName: String): Bitmap? {
        val file = File(context.filesDir, fileName)
        if (!file.exists() || file.length() == 0L) return null
        return try {
            BitmapFactory.decodeFile(file.absolutePath)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to decode $fileName: ${e.message}")
            null
        }
    }

    // ─────────────────────────────────────────────────────────────────────── //
    //  Private helpers                                                        //
    // ─────────────────────────────────────────────────────────────────────── //

    private fun invalidateIfChanged(newUrl: String, prefKey: String, cacheFile: String) {
        val oldUrl = prefs.getString(prefKey, null)
        if (oldUrl != null && oldUrl != newUrl) {
            File(context.filesDir, cacheFile).delete()
            Log.d(TAG, "Cache invalidated for $cacheFile (URL changed)")
        }
    }

    /**
     * Downloads [url] and saves raw bytes to [fileName] inside [Context.filesDir].
     * Skips if file is already cached (size > 1 KB).
     * Writes atomically via a temp file to avoid partial writes on failure.
     *
     * Must be called from [Dispatchers.IO] (blocking OkHttp call).
     */
    private fun downloadFile(url: String, fileName: String, label: String) {
        val dest = File(context.filesDir, fileName)
        if (dest.exists() && dest.length() > 1_024L) {
            Log.d(TAG, "Cache hit — skipping $label ($fileName)")
            return
        }

        Log.d(TAG, "Downloading $label from $url")
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Android; SpinWheel)")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code} downloading $label")
            }
            val bytes = response.body?.bytes()
                ?: throw IOException("Empty body downloading $label")

            if (bytes.size < 100) {
                throw IOException(
                    "$label response too small (${bytes.size} B) — " +
                    "verify the Drive file is publicly shared."
                )
            }

            val tmp = File(context.filesDir, "$fileName.tmp")
            tmp.writeBytes(bytes)
            tmp.renameTo(dest)
            Log.d(TAG, "Saved $label → $fileName (${bytes.size} bytes)")
        }
    }
}
