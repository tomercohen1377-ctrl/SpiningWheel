package com.example.spinwheel

import android.content.Context
import android.util.Log
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState
import com.example.spinwheel.di.SpinWheelGraph

private const val TAG = "LoadWheelCallback"

class LoadWheelActionCallback : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        Log.d(TAG, "onAction — starting load pipeline for glanceId=$glanceId")

        // ── Step 1: mark loading ──────────────────────────────────────────── //
        updateAppWidgetState(context, glanceId) { prefs ->
            prefs[SpinWheelGlanceWidget.IS_LOADING_KEY] = true
        }
        SpinWheelGlanceWidget().update(context, glanceId)   // shows "Downloading assets…"

        // ── Step 2: fetch config + download images ────────────────────────── //
        try {
            val graph  = SpinWheelGraph.get(context)

            val config = graph.getWheelConfigJsonUseCase()
            if (config == null) {
                Log.w(TAG, "Firebase RC returned null — aborting")
                return
            }
            Log.d(TAG, "Config ok — ${config.assetIds.size} assets, spinDuration=${config.spinDurationMs}")

            val ok = graph.downloadWheelImagesUseCase(config)
            if (!ok) {
                Log.w(TAG, "Image download failed — aborting")
                return
            }
            Log.d(TAG, "All images downloaded ✓")

        } finally {
            // ── Step 3: always clear loading flag + re-render ─────────────── //
            // Using `finally` ensures the loading spinner never gets stuck
            // on screen even if the download throws.
            updateAppWidgetState(context, glanceId) { prefs ->
                prefs[SpinWheelGlanceWidget.IS_LOADING_KEY] = false
            }
            // This update call runs inside Glance's goAsync scope — the
            // wakelock is still held, so provideGlance() is guaranteed to run.
            SpinWheelGlanceWidget().update(context, glanceId)
            Log.d(TAG, "Widget update dispatched — wheel should now be visible")
        }
    }
}
