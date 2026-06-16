package com.example.spinwheel

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * [GlanceAppWidgetReceiver] that connects [SpinWheelGlanceWidget] to the
 * Android home-screen launcher.
 *
 * ## Fully self-contained — no app launch required
 *
 * Both [onUpdate] (triggered by the system on [android.appwidget.AppWidgetProviderInfo]
 * `updatePeriodMillis` cycles) and [onEnabled] (widget first pinned to home screen)
 * automatically execute a full Firebase Remote Config → asset-download → widget-render
 * pipeline on a background thread:
 *
 * ```
 * 1. RemoteConfigFetcher.fetchWheelConfigJson()
 *      → Firebase RC key "wheel_config" → raw JSON string
 *
 * 2. WidgetSyncService.fetchAndCacheFromJson(json)
 *      → parse JSON → host + file IDs → 4 Drive download URLs
 *      → OkHttp download → re-encode as PNG → cache in filesDir
 *      → invalidates per-asset cache when Drive file IDs change
 *
 * 3. SpinWheelGlanceWidget().update()
 *      → provideGlance() reads bitmaps from filesDir
 *      → Glance renders 4-layer widget via RemoteViews
 * ```
 *
 * Firebase initialization ([com.google.firebase.provider.FirebaseInitProvider]) runs as
 * a [android.content.ContentProvider] when the **process** starts — even if it is started
 * only to service a [android.content.BroadcastReceiver]. So [com.google.firebase.Firebase]
 * is available here without the user opening the host activity.
 */
class SpinWheelWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = SpinWheelGlanceWidget()

    /**
     * Called when this widget is added to the home screen for the first time.
     *
     * Immediately triggers a background Firebase RC fetch + asset download so
     * the widget displays real content as soon as possible.
     */
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        fetchAndSync(context)
    }

    /**
     * Called by the system on every [android.appwidget.AppWidgetProviderInfo.updatePeriodMillis]
     * cycle (set to 12 h in `spin_wheel_widget_info.xml`).
     *
     * [super.onUpdate] runs [SpinWheelGlanceWidget.provideGlance] immediately to
     * render the currently cached state (no flicker). Then [fetchAndSync] fetches
     * fresh config + downloads any changed assets in the background, and triggers
     * another render when done.
     */
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds) // render cached state now
        fetchAndSync(context)                                   // refresh in background
    }

    // ─────────────────────────────────────────────────────────────────────── //
    //  Background sync                                                        //
    // ─────────────────────────────────────────────────────────────────────── //

    /**
     * Fetches the `wheel_config` JSON from Firebase Remote Config, downloads
     * any updated assets, then refreshes the Glance widget.
     *
     * Uses a process-level [CoroutineScope] so the work outlives the
     * [android.content.BroadcastReceiver] callback window (10 s). This is
     * intentional — the download may take longer than 10 s on a slow network.
     */
    private fun fetchAndSync(context: Context) {
        scope.launch {
            val configJson = RemoteConfigFetcher.fetchWheelConfigJson()

            if (configJson != null) {
                // Full sync: parse JSON → derive URLs → download → cache
                val success = WidgetSyncService(context).fetchAndCacheFromJson(configJson)
                if (success) {
                    requestUpdate(context)
                } else {
                    // Download failed — refresh widget with cached bitmaps (if any)
                    requestUpdate(context)
                }
            } else {
                // Firebase RC unavailable — refresh widget with whatever is in cache
                requestUpdate(context)
            }
        }
    }

    companion object {
        /**
         * Process-level scope. Not bound to any Activity/Fragment lifecycle —
         * intentional so long-running downloads complete even after
         * [onReceive] returns.
         */
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        /**
         * Refreshes every pinned Spin Wheel widget instance by calling
         * [SpinWheelGlanceWidget.update] on each Glance ID.
         *
         * Call this after a sync completes so the widget redraws with new bitmaps.
         */
        fun requestUpdate(context: Context) {
            scope.launch {
                val manager   = GlanceAppWidgetManager(context)
                val glanceIds = manager.getGlanceIds(SpinWheelGlanceWidget::class.java)
                glanceIds.forEach { SpinWheelGlanceWidget().update(context, it) }
            }
        }
    }
}
