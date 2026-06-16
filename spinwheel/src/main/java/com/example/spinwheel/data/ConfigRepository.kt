package com.example.spinwheel.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

private const val TAG = "ConfigRepository"
private const val PREFS_NAME = "spinwheel_prefs"
private const val KEY_LAST_FETCH_TIME = "spinwheel_last_fetch_time"
private const val KEY_CACHED_CONFIG = "spinwheel_cached_config"
private const val IMAGE_CACHE_DIR = "spinwheel"

/**
 * Repository responsible for:
 * - Fetching the remote JSON config via OkHttp
 * - Caching it in [SharedPreferences] with a TTL driven by `cacheExpiration` in the config
 *   (defaults to 1 hour when no config has been loaded yet)
 * - Downloading and disk-caching image assets into [Context.getCacheDir]/spinwheel/
 */
class ConfigRepository(
    private val context: Context,
    private val client: OkHttpClient = defaultClient()
) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    // ------------------------------------------------------------------ //
    //  Public API                                                          //
    // ------------------------------------------------------------------ //

    /**
     * Returns the first [WheelConfigItem] from the remote JSON response.
     *
     * Uses a cached copy (stored in [SharedPreferences]) if it is still within
     * the TTL set by [NetworkAttributes.cacheExpiration]. Falls back to the
     * network on first run or after cache expiry.
     *
     * @throws IOException on network failure when no valid cache exists.
     */
    suspend fun getConfig(url: String): WheelConfigItem {
        val cachedJson = prefs.getString(KEY_CACHED_CONFIG, null)
        val lastFetch = prefs.getLong(KEY_LAST_FETCH_TIME, 0L)

        if (cachedJson != null) {
            runCatching {
                val cached = json.decodeFromString(WheelConfigResponse.serializer(), cachedJson)
                val ttlMs = (cached.data.firstOrNull()?.network?.attributes?.cacheExpiration ?: 3600L) * 1_000L
                val age = System.currentTimeMillis() - lastFetch
                if (age < ttlMs) {
                    Log.d(TAG, "Returning config from cache (age=${age}ms, ttl=${ttlMs}ms)")
                    return cached.data.first()
                }
            }
        }

        Log.d(TAG, "Fetching fresh config from $url")
        val rawJson = fetchString(url)
        val response = json.decodeFromString(WheelConfigResponse.serializer(), rawJson)
        val item = response.data.firstOrNull()
            ?: throw IOException("Config response contained an empty data array")

        prefs.edit()
            .putString(KEY_CACHED_CONFIG, rawJson)
            .putLong(KEY_LAST_FETCH_TIME, System.currentTimeMillis())
            .apply()

        return item
    }

    /**
     * Returns a local [File] for the given [url].
     * Downloads and caches the file on the first request; subsequent calls
     * return the cached file without a network round-trip.
     *
     * @param url      Full HTTPS URL of the image.
     * @param filename Desired filename inside the image cache directory.
     */
    suspend fun getImageFile(url: String, filename: String): File {
        val cacheDir = File(context.cacheDir, IMAGE_CACHE_DIR).also { it.mkdirs() }
        val file = File(cacheDir, filename)

        if (file.exists() && file.length() > 0) {
            Log.d(TAG, "Cache hit for $filename")
            return file
        }

        Log.d(TAG, "Downloading $filename from $url")
        fetchBytes(url).also { bytes -> file.writeBytes(bytes) }
        return file
    }

    /**
     * Clears all cached data: SharedPreferences entries and all files under
     * the image cache directory.
     */
    fun clearCache() {
        prefs.edit()
            .remove(KEY_CACHED_CONFIG)
            .remove(KEY_LAST_FETCH_TIME)
            .apply()
        File(context.cacheDir, IMAGE_CACHE_DIR).deleteRecursively()
        Log.d(TAG, "Cache cleared")
    }

    // ------------------------------------------------------------------ //
    //  Private helpers                                                    //
    // ------------------------------------------------------------------ //

    private fun fetchString(url: String): String {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code} for $url")
            }
            return response.body?.string() ?: throw IOException("Empty body for $url")
        }
    }

    private fun fetchBytes(url: String): ByteArray {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code} for $url")
            }
            return response.body?.bytes() ?: throw IOException("Empty body for $url")
        }
    }

    companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)   // Required for Google Drive direct-download redirects
            .build()
    }
}
