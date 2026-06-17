package com.example.spinwheel.data.local

/**
 * Identifiers for the four image layers of the widget.
 */
enum class AssetKey { BG, WHEEL, FRAME, SPIN }

/**
 * Persisted asset metadata: the URL that was used to download each image.
 * Used to detect when the Firebase RC value has changed and a re-download is needed.
 */
typealias ImageUrls = Map<AssetKey, String>

/**
 * Disk + SharedPreferences data source.
 *
 * All methods are `suspend` (they do disk I/O internally) and dispatch to IO.
 * Observers can subscribe to [updates] to react when any data changes; the
 * widget uses this to redraw whenever a new image/file becomes available.
 *
 * This is what the widget UI observes — the repository writes here.
 */
interface LocalDataSource {

    // ─── Config (JSON) ──────────────────────────────────────────────────── //

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
     * Returns true if all four image bytes are present on disk.
     * Used by the widget to decide between "Loading" and "Wheel" UI.
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
