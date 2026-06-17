package com.example.spinwheel.domain

import com.example.spinwheel.data.local.AssetKey
import com.example.spinwheel.data.local.ImageUrls

/**
 * Parsed config extracted from the raw JSON in [com.example.spinwheel.RemoteConfigFetcher].
 * Used by the use case that builds asset download URLs.
 */
data class SpinWheelConfig(
    val host: String,
    /** Total spin duration in ms — comes from `wheel.rotation.duration` in Firebase RC. */
    val spinDurationMs: Long,
    /** Minimum number of full rotations per spin — comes from `wheel.rotation.minimumSpins`. */
    val minSpins: Int,
    /** Maximum number of full rotations per spin — comes from `wheel.rotation.maximumSpins`. */
    val maxSpins: Int,
    /** File IDs for each image layer, joined with [host] to form a full URL. */
    val assetIds: Map<AssetKey, String>,
)

/**
 * Domain-level contract for the widget data pipeline.
 *
 * Holds no Android types. Implementations live in
 * `com.example.spinwheel.data.repository.SpinWheelRepositoryImpl`.
 *
 * ## Singleton
 *
 * A single repository instance is shared between the widget process (Glance
 * `provideGlance`) and the worker process (WorkManager `doWork`). Without
 * sharing, the widget re-decodes files from disk on every tick instead of
 * receiving an in-memory event.
 */
interface SpinWheelRepository {

    /**
     * Fetches the `wheel_config` JSON from Firebase via the remote data source
     * and persists it via the local data source. Returns the parsed config or
     * `null` on failure.
     */
    suspend fun fetchAndCacheConfig(): SpinWheelConfig?

    /**
     * Reads the cached config from local storage. Returns null if nothing has
     * been synced yet.
     */
    suspend fun getCachedConfig(): SpinWheelConfig?

    /**
     * Persists the resolved asset URLs to local storage.
     */
    suspend fun saveImageUrls(urls: ImageUrls)

    /**
     * Returns the asset URLs persisted after the last successful sync.
     */
    suspend fun getImageUrls(): ImageUrls

    /**
     * Reads raw image bytes from local storage. Returns null if any file is
     * missing or empty.
     */
    fun getImageBytes(key: AssetKey): ByteArray?

    /** Epoch-ms of the last successful sync, or 0 if never synced. */
    suspend fun getLastSync(): Long

    /**
     * Emits `Unit` whenever any data is written locally. The widget subscribes
     * here so it re-renders automatically on data change.
     */
    fun observeChanges(): kotlinx.coroutines.flow.Flow<Unit>

    /** Deletes all cached data and clears SharedPreferences. */
    suspend fun clear()
}
