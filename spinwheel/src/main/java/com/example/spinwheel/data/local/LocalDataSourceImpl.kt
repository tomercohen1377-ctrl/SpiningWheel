package com.example.spinwheel.data.local

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "LocalDataSourceImpl"
private const val PREFS_NAME = "spinwheel_prefs"

/** Pref keys for the JSON config + asset URLs + last-sync timestamp. */
private const val KEY_CONFIG_JSON = "config_json"
private const val KEY_LAST_SYNC   = "last_sync_epoch_ms"
private const val KEY_URL_BG      = "url_bg"
private const val KEY_URL_WHEEL   = "url_wheel"
private const val KEY_URL_FRAME   = "url_frame"
private const val KEY_URL_SPIN    = "url_spin"

/** Disk file extension for cached image bytes. */
private const val FILE_EXT = ".bin"

/**
 * SharedPreferences + `filesDir` implementation of [LocalDataSource].
 *
 * Hold a reference to this as a **singleton** at the application level (or wire
 * through Hilt later) so all components see the same write events via [updates].
 */
class LocalDataSourceImpl(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : LocalDataSource {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val writes = MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 16)
    override val updates: Flow<Unit> = writes.asSharedFlow()

    // ─── Config JSON ────────────────────────────────────────────────────── //

    override suspend fun saveConfigJson(json: String): Unit = withContext(ioDispatcher) {
        prefs.edit().putString(KEY_CONFIG_JSON, json).apply()
        writes.tryEmit(Unit)
        Log.d(TAG, "Config JSON saved (${json.length} chars)")
    }

    override suspend fun getConfigJson(): String? = withContext(ioDispatcher) {
        prefs.getString(KEY_CONFIG_JSON, null)
    }

    // ─── Image URLs ─────────────────────────────────────────────────────── //

    override suspend fun saveImageUrls(urls: ImageUrls): Unit = withContext(ioDispatcher) {
        prefs.edit().apply {
            urls[AssetKey.BG]?.let    { putString(KEY_URL_BG, it) }
            urls[AssetKey.WHEEL]?.let { putString(KEY_URL_WHEEL, it) }
            urls[AssetKey.FRAME]?.let { putString(KEY_URL_FRAME, it) }
            urls[AssetKey.SPIN]?.let  { putString(KEY_URL_SPIN, it) }
        }.apply()
        writes.tryEmit(Unit)
    }

    override suspend fun getImageUrls(): ImageUrls = withContext(ioDispatcher) {
        val out = mutableMapOf<AssetKey, String>()
        prefs.getString(KEY_URL_BG, null)?.let    { out[AssetKey.BG] = it }
        prefs.getString(KEY_URL_WHEEL, null)?.let { out[AssetKey.WHEEL] = it }
        prefs.getString(KEY_URL_FRAME, null)?.let { out[AssetKey.FRAME] = it }
        prefs.getString(KEY_URL_SPIN, null)?.let  { out[AssetKey.SPIN] = it }
        out
    }

    // ─── Image bytes ────────────────────────────────────────────────────── //

    override suspend fun saveImageBytes(key: AssetKey, bytes: ByteArray): Unit =
        withContext(ioDispatcher) {
            val file = fileFor(key)
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeBytes(bytes)
            if (tmp.renameTo(file)) {
                writes.tryEmit(Unit)
                Log.d(TAG, "Saved ${file.name} (${bytes.size} bytes)")
            } else {
                file.writeBytes(bytes)   // fallback: direct write
                writes.tryEmit(Unit)
            }
        }

    override fun getImageBytes(key: AssetKey): ByteArray? {
            val file = fileFor(key)
            return if (file.exists() && file.length() > 0) file.readBytes() else null
        }

    // ─── Timestamps ─────────────────────────────────────────────────────── //

    override suspend fun setLastSync(epochMillis: Long): Unit = withContext(ioDispatcher) {
        prefs.edit().putLong(KEY_LAST_SYNC, epochMillis).apply()
        Log.d(TAG, "Last sync = $epochMillis")
    }

    override suspend fun getLastSync(): Long = prefs.getLong(KEY_LAST_SYNC, 0L)

    override suspend fun hasAllImages(): Boolean = withContext(ioDispatcher) {
        AssetKey.entries.all { fileFor(it).exists() && fileFor(it).length() > 0 }
    }

    override suspend fun clear(): Unit = withContext(ioDispatcher) {
        prefs.edit().clear().apply()
        AssetKey.entries.forEach { fileFor(it).delete() }
        writes.tryEmit(Unit)
        Log.d(TAG, "Cache cleared")
    }

    // ─── Internals ──────────────────────────────────────────────────────── //

    private fun fileFor(key: AssetKey): File =
        File(context.filesDir, key.name.lowercase() + FILE_EXT)
}
