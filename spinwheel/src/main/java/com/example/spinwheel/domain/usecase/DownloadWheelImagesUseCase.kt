package com.example.spinwheel.domain.usecase

import android.util.Log
import com.example.spinwheel.data.local.AssetKey
import com.example.spinwheel.data.local.LocalDataSource
import com.example.spinwheel.data.remote.DriveFileResolver
import com.example.spinwheel.data.remote.RemoteDataSource
import com.example.spinwheel.domain.SpinWheelConfig
import com.example.spinwheel.domain.SpinWheelRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

private const val TAG = "DownloadImagesUseCase"

/**
 * Single-purpose use case for **step 2** of the worker pipeline.
 *
 * Awaits all four image downloads **in parallel** with `awaitAll`,
 * then saves each one's bytes to the local data source.
 *
 * ## URL resolution
 *
 * The upstream `wheel_config` JSON ships the upstream Drive schema:
 * ```json
 * "host" = "https://drive.google.com/drive/folders/FOLDER_ID"
 * "assets" = { "bg": "bg.png", "wheel": "wheel.png", ... }
 * ```
 *
 * Drive doesn't serve `FOLDER_URL/filename` directly. We use
 * [DriveFileResolver] (the proven pre-migration scraper) to walk the
 * folder HTML, extract every file ID, HEAD each for its filename, and
 * build `filename → lh3.googleusercontent.com/d/ID` mappings.
 *
 * lh3 **transcodes AVIF to JPEG/PNG** so `BitmapFactory.decodeByteArray`
 * works on every Android version with no special handling.
 *
 * For non-Drive hosts (e.g. a CDN prefix that already takes filenames), the
 * resolver falls back to `host + filename`.
 */
class DownloadWheelImagesUseCase(
    private val remote: RemoteDataSource,
    private val local: LocalDataSource,
    private val repository: SpinWheelRepository,
) {

    suspend operator fun invoke(config: SpinWheelConfig): Boolean = coroutineScope {
        // Invalidate Drive's resolver cache if the folder URL changed
        // (the SpinWheelRepository tracks the last host we resolved against)
        val host = config.host.trim()

        Log.d(TAG, "Resolving asset URLs for host='${host}'")

        val resolved: Map<AssetKey, String> = config.assetIds
            .mapValues { (key, filename) ->
                withContext(Dispatchers.IO) {
                    DriveFileResolver.resolveFolderUrl(host, filename)
                }
            }.also {
                Log.d(TAG, "→ resolved ${it.size} URLs")
                it.forEach { (k, u) -> Log.d(TAG, "   $k → $u") }
            }

        // Persist the resolved URLs so the widget sees them
        repository.saveImageUrls(resolved)

        // Download all 4 in parallel
        val bytes: List<Pair<AssetKey, ByteArray?>> = resolved.entries.map { (key, url) ->
            async(Dispatchers.IO) {
                Log.d(TAG, "fetching $key from $url …")
                val result = try {
                    remote.fetchImage(url)
                } catch (e: Exception) {
                    Log.e(TAG, "$key failed: ${e.message}", e)
                    null
                }
                Log.d(TAG, "$key done — ${result?.size ?: "FAILED"} bytes")
                key to result
            }
        }.awaitAll()

        var allOk = true
        bytes.forEach { (key, data) ->
            if (data != null) local.saveImageBytes(key, data) else allOk = false
        }

        if (allOk) {
            withContext(Dispatchers.IO) {
                local.setLastSync(System.currentTimeMillis())
            }
        }

        Log.d(TAG, "DownloadWheelImages complete — allOk=$allOk")
        allOk
    }
}
