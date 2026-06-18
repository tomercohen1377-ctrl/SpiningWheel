package com.example.spinwheel

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.Preferences
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.example.spinwheel.data.local.AssetKey
import com.example.spinwheel.di.SpinWheelGraph
import com.example.spinwheel.domain.SpinWheelConfig
import com.example.spinwheel.ui.SpinWheelGlanceWidget
import com.example.spinwheel.ui.SpinWheelWidgetReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking

private const val TAG = "WidgetSyncService"

/**
 * Synchronous orchestrator the JS bridge calls into.
 *
 * The bridge (`SpinWheelModule.syncWidgetConfiguration`) invokes
 * [fetchAndCache] from a background `Thread`; this method must therefore
 * be safe to call from any thread and must return when the download + disk
 * persist step is complete (so the JS `Promise` can resolve).
 *
 * ## Asset source-of-truth
 *
 * The 4 image URLs the bridge passes in [SpinWheelAssets] are treated as
 * authoritative. Earlier versions of this SDK tried to resolve downloads
 * via the JSON's `network.assets.host` field + `DriveFileResolver`, but
 * the upstream JSON schema does not always populate that field, so that
 * path would silently fail with the host's value being blank. We now
 * always download directly from `assets.bgUrl/wheelUrl/frameUrl/spinUrl`.
 *
 * The JSON is still parsed (best-effort) to extract optional animation
 * parameters (`wheel.rotation.duration`, `minimumSpins`, `maximumSpins`).
 * On parse failure we fall back to the canonical defaults that match the
 * original Android Studio project's hard-coded values.
 *
 * @return `true` if all four images were downloaded + cached successfully.
 */
object WidgetSyncService {

    fun fetchAndCache(
        context: Context,
        configUrl: String,
        assets: SpinWheelAssets,
    ): Boolean = runBlocking {
        try {
            val graph = SpinWheelGraph.get(context)
            val appCtx = context.applicationContext

            // 1. Persist the URL the JS sent so the widget can retry on tap.
            graph.local.saveConfigUrl(configUrl)

            // 2. Best-effort parse the JSON for animation params. We do NOT
            //    use it for image downloads — those come from `assets.X`.
            val config: SpinWheelConfig? = graph.getWheelConfigJsonUseCase(configUrl)
            val duration = config?.spinDurationMs ?: DEFAULT_SPIN_DURATION_MS
            val minSpins = config?.minSpins     ?: DEFAULT_MIN_SPINS
            val maxSpins = config?.maxSpins     ?: DEFAULT_MAX_SPINS

            // 3. Persist the explicit URLs the bridge passed us so the
            //    widget can use them too — even after we restart.
            graph.repository.saveImageUrls(
                mapOf(
                    AssetKey.BG    to assets.bgUrl,
                    AssetKey.WHEEL to assets.wheelUrl,
                    AssetKey.FRAME to assets.frameUrl,
                    AssetKey.SPIN  to assets.spinUrl,
                )
            )

            // 4. Download all 4 images in parallel directly from the bridge URLs.
            val keys = listOf(
                AssetKey.BG    to assets.bgUrl,
                AssetKey.WHEEL to assets.wheelUrl,
                AssetKey.FRAME to assets.frameUrl,
                AssetKey.SPIN  to assets.spinUrl,
            )

            val results: List<Pair<AssetKey, ByteArray?>> = coroutineScope {
                keys.map { (key, url) ->
                    async(Dispatchers.IO) {
                        Log.d(TAG, "fetching $key from $url …")
                        val data: ByteArray? = try {
                            graph.remote.fetchImage(url)
                        } catch (e: Exception) {
                            Log.e(TAG, "$key failed: ${e.message}", e)
                            null
                        }
                        Log.d(TAG, "$key done — ${data?.size ?: "FAILED"} bytes")
                        key to data
                    }
                }.awaitAll()
            }

            var allOk = true
            results.forEach { (key, data) ->
                if (data != null) graph.local.saveImageBytes(key, data) else allOk = false
            }

            if (allOk) graph.local.setLastSync(System.currentTimeMillis())

            if (allOk) {
                // Push the animation params (or defaults) to every pinned widget so
                // the first tap on the spin button uses JSON-driven durations.
                runAnimationParamsUpdate(appCtx, duration, minSpins, maxSpins)

                // Refresh every pinned widget so it picks up the new images from disk.
                SpinWheelWidgetReceiver.requestUpdate(appCtx)
            }

            Log.d(TAG, "fetchAndCache complete — allOk=$allOk")
            allOk
        } catch (e: Exception) {
            Log.e(TAG, "fetchAndCache failed: ${e.message}", e)
            false
        }
    }

    private fun runAnimationParamsUpdate(
        context: Context,
        durationMs: Long,
        minSpins: Int,
        maxSpins: Int,
    ) {
        runBlocking {
            try {
                androidx.glance.appwidget.GlanceAppWidgetManager(context)
                    .getGlanceIds(com.example.spinwheel.ui.SpinWheelGlanceWidget::class.java)
                    .forEach { glanceId ->
                        updateAppWidgetState(
                            context,
                            PreferencesGlanceStateDefinition as GlanceStateDefinition<Preferences>,
                            glanceId,
                        ) { prefs ->
                            val mutable = prefs.toMutablePreferences()
                            mutable[SpinWheelGlanceWidget.SPIN_DURATION_MS] = durationMs
                            mutable[SpinWheelGlanceWidget.MIN_SPINS] = minSpins
                            mutable[SpinWheelGlanceWidget.MAX_SPINS] = maxSpins
                            mutable.toPreferences()
                        }
                    }
            } catch (e: Throwable) {
                Log.w(TAG, "runAnimationParamsUpdate skipped: ${e.message}")
            }
        }
    }

    /**
     * Last-sync timestamp helper used by `SpinWheelModule.getLastFetchTime()`.
     */
    fun lastFetchEpoch(context: Context): Long = runBlocking {
        SpinWheelGraph.get(context).repository.getLastSync()
    }

    private const val DEFAULT_SPIN_DURATION_MS = 3_000L
    private const val DEFAULT_MIN_SPINS = 5
    private const val DEFAULT_MAX_SPINS = 8
}
