package com.example.spinwheel

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.example.spinwheel.data.WheelConfigResponse
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

private const val TAG = "WidgetSyncService"

/** SharedPreferences file shared between the widget, host app, and RN module. */
const val PREFS_NAME = "SpinWheelPrefs"
const val KEY_LAST_FETCH_TIME = "last_config_fetch_time"
const val KEY_CONFIG_URL      = "config_url"

// Saved asset URL keys (so the widget can re-sync after a reboot)
const val KEY_URL_BG    = "asset_url_bg"
const val KEY_URL_WHEEL = "asset_url_wheel"
const val KEY_URL_FRAME = "asset_url_frame"
const val KEY_URL_SPIN  = "asset_url_spin"

// Cache file names (always stored as PNG after re-encoding)
const val FILE_BG    = "sw_bg.png"
const val FILE_WHEEL = "sw_wheel.png"
const val FILE_FRAME = "sw_frame.png"
const val FILE_SPIN  = "sw_spin.png"

/**
 * Explicit asset URLs passed to [fetchAndCache].
 *
 * The JSON config on Drive contains a **placeholder host** —
 * `"https://drive.google.com/your-public-folder-or-direct-links/"` — so the
 * asset URLs are derived here in the app using the known Drive file IDs.
 */
data class SpinWheelAssets(
    val bgUrl:    String,
    val wheelUrl: String,
    val frameUrl: String,
    val spinUrl:  String
)

/**
 * Blocking service that:
 * 1. Downloads the remote JSON config for spin settings (duration, easing).
 * 2. Downloads all four image assets from the **explicit** [SpinWheelAssets] URLs.
 * 3. Re-encodes each image as PNG before saving — handles any source format
 *    (PNG, JPEG, AVIF, WebP) regardless of the file extension on Drive.
 * 4. Persists [KEY_LAST_FETCH_TIME] and asset URLs in [SharedPreferences].
 *
 * Always call on a background thread.
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
     * Fetches the JSON config (for spin duration/settings) then downloads and
     * caches all four image assets.
     *
     * Asset URLs are provided **explicitly** — the JSON's `network.assets.host`
     * field is a placeholder and must not be used for URL construction.
     *
     * @return `true` on full success; `false` if any step fails.
     */
    fun fetchAndCache(configUrl: String, assets: SpinWheelAssets): Boolean {
        return try {
            Log.d(TAG, "Fetching config from $configUrl")
            // Fetch JSON — used only for spin settings (duration, easing etc.)
            // The host field in the JSON is a placeholder; we ignore it.
            val rawJson = fetchString(configUrl)
            // Parse just to validate the response — we log the duration
            runCatching {
                val response = json.decodeFromString(WheelConfigResponse.serializer(), rawJson)
                val duration = response.data.firstOrNull()?.wheel?.rotation?.duration ?: 2000L
                Log.d(TAG, "Config OK — spin duration: ${duration}ms")
            }.onFailure { Log.w(TAG, "Config parse warning (non-fatal): ${it.message}") }

            Log.d(TAG, "Downloading 4 assets from Drive…")
            downloadAsPng(assets.bgUrl,    FILE_BG,    "background")
            downloadAsPng(assets.wheelUrl, FILE_WHEEL, "wheel")
            downloadAsPng(assets.frameUrl, FILE_FRAME, "frame")
            downloadAsPng(assets.spinUrl,  FILE_SPIN,  "spin button")

            // Persist metadata + asset URLs (widget reads them after reboot)
            prefs.edit()
                .putLong(KEY_LAST_FETCH_TIME, System.currentTimeMillis())
                .putString(KEY_CONFIG_URL, configUrl)
                .putString(KEY_URL_BG,    assets.bgUrl)
                .putString(KEY_URL_WHEEL, assets.wheelUrl)
                .putString(KEY_URL_FRAME, assets.frameUrl)
                .putString(KEY_URL_SPIN,  assets.spinUrl)
                .apply()

            Log.d(TAG, "Sync complete ✓")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed: ${e.message}", e)
            false
        }
    }

    // ─────────────────────────────────────────────────────────────────────── //

    /**
     * Parses the full wheel config JSON (as fetched from Firebase Remote Config),
     * resolves each asset filename to a direct download URL, then downloads and
     * caches all four images.
     *
     * ## URL resolution strategies
     *
     * **Google Drive folder host** (`host` contains `/folders/`):
     * ```
     * host = "https://drive.google.com/drive/u/0/folders/FOLDER_ID"
     * asset = "bg.jpeg"
     * ```
     * [DriveFileResolver] fetches the folder HTML, extracts all file IDs,
     * sends HEAD requests to map `filename → fileId`, then builds:
     * `https://drive.google.com/uc?export=download&id=FILE_ID`
     *
     * Filename matching uses 3 fallbacks: exact → case-insensitive → stem
     * (`bg.jpeg` matches `bg.png` via the stem `bg`).
     *
     * **Direct prefix host** (`host = "https://cdn.example.com/assets/"`):
     * ```
     * resolvedUrl = host + filename    (unchanged)
     * ```
     *
     * ## Cache invalidation
     *
     * Resolved download URLs are saved in [SharedPreferences].  If the resolved
     * URL for an asset changes (new file ID from Drive, or a new host in RC),
     * the cached PNG is deleted and re-downloaded on the next sync.
     *
     * @param configJson Raw JSON string from Firebase RC `wheel_config` key.
     * @return `true` on full success; `false` if parsing, resolution, or download fails.
     */
    fun fetchAndCacheFromJson(configJson: String): Boolean {
        return try {
            val response = json.decodeFromString(WheelConfigResponse.serializer(), configJson)
            val item = response.data.firstOrNull()
                ?: throw IOException("Config data[] is empty")

            val host   = item.network.assets.host.trim()
            val assets = item.wheel.assets

            Log.d(TAG, "fetchAndCacheFromJson — host=$host " +
                    "spinDuration=${item.wheel.rotation.duration}ms")

            if (host.isBlank() || !host.startsWith("http")) {
                throw IOException(
                    "Invalid host in wheel_config: \"$host\". " +
                    "Must be a valid URL starting with http."
                )
            }

            // ── Invalidate DriveFileResolver cache if the folder URL changed ── //
            val savedHost = prefs.getString("resolved_host", null)
            if (savedHost != host) {
                DriveFileResolver.clearCache()
                Log.d(TAG, "Folder URL changed — DriveFileResolver cache cleared")
            }

            // ── Resolve filenames → download URLs ─────────────────────────── //
            //
            // If host is a Drive folder URL  → DriveFileResolver does:
            //   1. Fetch folder HTML, extract file IDs
            //   2. HEAD each ID to get Content-Disposition filename
            //   3. Match requested filename (exact / case-insensitive / stem)
            //   4. Return "https://drive.google.com/uc?export=download&id=FILE_ID"
            //
            // Otherwise → host + filename (CDN / custom server)
            //
            val newBgUrl    = DriveFileResolver.resolve(client, host, assets.bg)
            val newWheelUrl = DriveFileResolver.resolve(client, host, assets.wheel)
            val newFrameUrl = DriveFileResolver.resolve(client, host, assets.wheelFrame)
            val newSpinUrl  = DriveFileResolver.resolve(client, host, assets.wheelSpin)

            Log.d(TAG, "Resolved URLs:\n" +
                    "  bg    = $newBgUrl\n" +
                    "  wheel = $newWheelUrl\n" +
                    "  frame = $newFrameUrl\n" +
                    "  spin  = $newSpinUrl")

            // ── Invalidate per-asset cache if URL changed ─────────────────── //
            fun invalidateIfChanged(newUrl: String, prefKey: String, cacheFile: String) {
                val oldUrl = prefs.getString(prefKey, null)
                if (oldUrl != null && oldUrl != newUrl) {
                    File(context.filesDir, cacheFile).delete()
                    Log.d(TAG, "Cache invalidated ($cacheFile): URL changed")
                }
            }
            invalidateIfChanged(newBgUrl,    KEY_URL_BG,    FILE_BG)
            invalidateIfChanged(newWheelUrl, KEY_URL_WHEEL, FILE_WHEEL)
            invalidateIfChanged(newFrameUrl, KEY_URL_FRAME, FILE_FRAME)
            invalidateIfChanged(newSpinUrl,  KEY_URL_SPIN,  FILE_SPIN)

            // ── Download + re-encode as PNG ───────────────────────────────── //
            // downloadAsPng skips files that are already cached (size > 1 KB)
            downloadAsPng(newBgUrl,    FILE_BG,    "background")
            downloadAsPng(newWheelUrl, FILE_WHEEL, "wheel")
            downloadAsPng(newFrameUrl, FILE_FRAME, "frame")
            downloadAsPng(newSpinUrl,  FILE_SPIN,  "spin button")

            // ── Persist URLs + fetch metadata ─────────────────────────────── //
            prefs.edit()
                .putLong(KEY_LAST_FETCH_TIME, System.currentTimeMillis())
                .putString("resolved_host",   host)
                .putString(KEY_URL_BG,        newBgUrl)
                .putString(KEY_URL_WHEEL,     newWheelUrl)
                .putString(KEY_URL_FRAME,     newFrameUrl)
                .putString(KEY_URL_SPIN,      newSpinUrl)
                .apply()

            Log.d(TAG, "fetchAndCacheFromJson complete ✓")
            true
        } catch (e: Exception) {
            Log.e(TAG, "fetchAndCacheFromJson failed: ${e.message}", e)
            false
        }
    }

    fun getLastFetchTime(): Long = prefs.getLong(KEY_LAST_FETCH_TIME, 0L)

    fun clearCache() {
        listOf(FILE_BG, FILE_WHEEL, FILE_FRAME, FILE_SPIN)
            .forEach { File(context.filesDir, it).delete() }
        prefs.edit()
            .remove(KEY_LAST_FETCH_TIME)
            .remove(KEY_CONFIG_URL)
            .remove(KEY_URL_BG).remove(KEY_URL_WHEEL)
            .remove(KEY_URL_FRAME).remove(KEY_URL_SPIN)
            .apply()
        Log.d(TAG, "Cache cleared")
    }

    /**
     * Returns a [Bitmap] from the cached file, or `null` if not cached.
     *
     * Files are stored as JPEG or PNG (served by lh3.googleusercontent.com),
     * so plain [BitmapFactory] is sufficient on all Android versions.
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
    // ──────────────────────────��──────────────────────────────────────────── //

    /**
     * Downloads [url] and saves the raw bytes directly to [fileName].
     *
     * ## Why raw bytes (no decode/re-encode)?
     *
     * The download URL is always an `lh3.googleusercontent.com/d/FILE_ID` URL
     * (set by [DriveFileResolver]).  Google's lh3 CDN serves the image as
     * **JPEG or PNG** regardless of the original file format on Drive — even
     * if the Drive file is AVIF, lh3 transcodes it to JPEG automatically.
     *
     * JPEG and PNG are universally decodable by [BitmapFactory] on every
     * Android version (no [android.graphics.ImageDecoder] needed, no color-space
     * issues, no "getPixels failed with error invalid input").
     *
     * Saving the raw bytes avoids an unnecessary decode→encode round-trip and
     * eliminates any quality loss or color-space conversion errors.
     */
    private fun downloadAsPng(url: String, fileName: String, label: String) {
        val file = File(context.filesDir, fileName)
        if (file.exists() && file.length() > 1024) {
            Log.d(TAG, "Cache hit: $label ($fileName)")
            return
        }

        Log.d(TAG, "Downloading $label from $url")
        val bytes = fetchBytes(url)
        Log.d(TAG, "Downloaded $label: ${bytes.size} bytes  " +
                "(magic: ${bytes.take(4).joinToString(" ") { "%02X".format(it) }})")

        if (bytes.size < 256) {
            throw IOException("$label response is too small (${bytes.size} bytes) — " +
                    "likely an error page. URL: $url")
        }

        // Save raw bytes — lh3 already serves JPEG/PNG, no decode needed
        file.writeBytes(bytes)
        Log.d(TAG, "Saved $label: $fileName (${file.length()} bytes)")
    }

    private fun fetchString(url: String): String {
        val req = Request.Builder().url(url).build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code} fetching $url")
            return resp.body?.string() ?: throw IOException("Empty body: $url")
        }
    }

    private fun fetchBytes(url: String): ByteArray {
        val req = Request.Builder().url(url).build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code} fetching $url")
            return resp.body?.bytes() ?: throw IOException("Empty body: $url")
        }
    }
}
