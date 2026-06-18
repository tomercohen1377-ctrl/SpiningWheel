package com.example.spinwheel.ui

import android.content.Context
import android.util.Log
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState
import com.example.spinwheel.SpinWheelAssets
import com.example.spinwheel.SpinWheelDefaults
import com.example.spinwheel.WidgetSyncService
import com.example.spinwheel.data.local.AssetKey
import com.example.spinwheel.di.SpinWheelGraph
import kotlinx.coroutines.runBlocking

/**
 * Glance [ActionCallback] invoked when the user taps "Tap to load" / "Tap
 * to retry" on the widget.
 *
 * Behaviour, in order:
 * 1. If all four image bytes are already on disk → just refresh the
 *    widget using the cached assets.
 * 2. Otherwise download from the bridge-written URLs (if any), falling
 *    back to the SDK's built-in [SpinWheelDefaults] when the bridge was
 *    never called (e.g. user dropped the widget before opening the app).
 */
class LoadWheelActionCallback : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        showLoading(context, glanceId)
        try {
            ensureAssets(context)
        } catch (e: Throwable) {
            Log.e(TAG, "ensureAssets threw: ${e.message}", e)
            showError(
                context,
                glanceId,
                "Could not download assets: ${e.message ?: "unknown"}",
            )
            return
        }
        clearErrorState(context, glanceId)
        SpinWheelGlanceWidget().update(context, glanceId)
    }

    /**
     * Pull the four images onto local disk if they aren't already. Uses the
     * bridge-written URLs when every one of them is present, otherwise the
     * SDK's built-in [SpinWheelDefaults] — so the widget works without ever
     * opening the RN app.
     */
    private fun ensureAssets(context: Context) {
        val graph = SpinWheelGraph.get(context)
        val hasAll = runBlocking { graph.local.hasAllImages() }
        if (hasAll) return

        // Read whatever the JS bridge may have cached, and decide whether
        // every URL slot is filled.
        val cached = runBlocking { graph.repository.getImageUrls() }
        val assetsFromBridge: SpinWheelAssets? = run {
            val bg    = cached[AssetKey.BG]
            val wheel = cached[AssetKey.WHEEL]
            val frame = cached[AssetKey.FRAME]
            val spin  = cached[AssetKey.SPIN]
            if (bg != null && wheel != null && frame != null && spin != null) {
                SpinWheelAssets(bgUrl = bg, wheelUrl = wheel, frameUrl = frame, spinUrl = spin)
            } else {
                null
            }
        }
        val configUrl = runBlocking { graph.local.getConfigUrl() }
            ?: SpinWheelDefaults.CONFIG_URL
        val assets = assetsFromBridge ?: SpinWheelDefaults.ASSETS

        Log.d(
            TAG,
            "ensureAssets — using " +
                if (assetsFromBridge != null) "bridge cache" else "DEFAULTS",
        )
        val ok = WidgetSyncService.fetchAndCache(context, configUrl, assets)
        if (!ok) {
            throw IllegalStateException(
                "asset download failed (network?). Tap again when reachable.",
            )
        }
    }

    private suspend fun showLoading(context: Context, glanceId: GlanceId) {
        updateAppWidgetState(context, glanceId) { prefs ->
            prefs[SpinWheelGlanceWidget.IS_LOADING_KEY] = true
            prefs.remove(SpinWheelGlanceWidget.ERROR_MESSAGE_KEY)
        }
        SpinWheelGlanceWidget().update(context, glanceId)
    }

    private suspend fun clearErrorState(context: Context, glanceId: GlanceId) {
        updateAppWidgetState(context, glanceId) {prefs ->
            prefs[SpinWheelGlanceWidget.IS_LOADING_KEY] = false
            prefs.remove(SpinWheelGlanceWidget.ERROR_MESSAGE_KEY)
        }
    }

    private suspend fun showError(context: Context, glanceId: GlanceId, message: String) {
        Log.e(TAG, "Load failed: $message")
        updateAppWidgetState(context, glanceId) {prefs ->
            prefs[SpinWheelGlanceWidget.IS_LOADING_KEY] = false
            prefs[SpinWheelGlanceWidget.ERROR_MESSAGE_KEY] = message
        }
        SpinWheelGlanceWidget().update(context, glanceId)
    }

    private companion object {
        const val TAG = "LoadWheelCallback"
    }
}


