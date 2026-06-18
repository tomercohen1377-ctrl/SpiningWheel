package com.example.spinwheel.data.local

/**
 * Identifiers for the four image layers of the widget.
 */
enum class AssetKey { BG, WHEEL, FRAME, SPIN }

/**
 * Persisted asset metadata: the URL that was used to download each image.
 * Used to detect when the upstream URL has changed and a re-download is needed.
 */
typealias ImageUrls = Map<AssetKey, String>

/**
 * Disk + SharedPreferences data source.
 *
 * All methods are `suspend` (they do disk I/O internally) and dispatch to IO.
 * Observers can subscribe to [updates] to react when any data is written;
 * the widget UI subscribes here to re-render automatically.
 */
interface LocalDataSource {

    // ─── Config (JSON) ─────────────────────────────────────────────────── //

    /** URL of the remote JSON config that produced the cached [saveConfigJson] payload. */
    suspend fun saveConfigUrl(url: String)

    /** Returns the URL of the JSON that produced the cached config, or null if never synced. */
    suspend fun getConfigUrl(): String?

    /** Saves the raw `wheel_config` JSON to SharedPreferences. */
    suspend fun saveConfigJson(json: String)

    /** Returns the cached JSON, or null if never synced. */
    suspend fun getConfigJson(): String?

    // ─── Image URLs (metadata) ─────────────────────────────────────────── //

    /** Persists the resolved download URL of each asset. */
    suspend fun saveImageUrls(urls: ImageUrls)

    /** Returns the persisted URLs (empty if none). */
    suspend fun getImageUrls(): ImageUrls

    // ─── Image bytes (the actual files) ────────────────────────────────── //

    /** Writes image bytes atomically to `filesDir/<key>.bin`. */
    suspend fun saveImageBytes(key: AssetKey, bytes: ByteArray)

    /** Reads bytes for [key] from disk, or null if not cached. */
    fun getImageBytes(key: AssetKey): ByteArray?

    // ─── Timestamps ─────────────────────────────────────────────────────── //

    suspend fun setLastSync(epochMillis: Long)
    suspend fun getLastSync(): Long

    /**
     * Returns true if all four image bytes are present on disk and the JSON
     * config has been cached. Used by the widget to decide between
     * "Loading"/"Initial"/"Wheel" UI.
     */
    suspend fun hasAllImages(): Boolean

    /** Deletes every cached file and preference entry. */
    suspend fun clear()

    /**
     * Emits [Unit] after every successful write to any of the above methods.
     * The widget subscribes to this flow to re-render on data change.
     */
    val updates: kotlinx.coroutines.flow.Flow<Unit>
}
