package com.example.spinwheel.ui

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.Preferences
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import kotlinx.coroutines.runBlocking

private const val TAG = "SpinWheelReceiver"

/**
 * AppWidgetProvider that ties [SpinWheelGlanceWidget] into the launcher.
 *
 * Discovered by Android because the library's manifest declares this class
 * as a `<receiver>` with the `android.appwidget.action.APPWIDGET_UPDATE`
 * intent filter and the `@xml/spin_wheel_widget_info` metadata.
 *
 * Call [requestUpdate] from anywhere (e.g. the JS bridge after a sync) to
 * refresh every pinned widget instance with the freshly cached assets.
 */
class SpinWheelWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = SpinWheelGlanceWidget()

    companion object {
        /**
         * Refresh every pinned instance of the Spin Wheel widget by walking
         * `GlanceAppWidgetManager.getGlanceIds(SpinWheelGlanceWidget::class)`,
         * the canonical Glance API for active widget instances.
         */
        fun requestUpdate(context: Context) {
            try {
                runBlocking {
                    val widget = SpinWheelGlanceWidget()
                    var refreshed = 0
                    androidx.glance.appwidget.GlanceAppWidgetManager(context)
                        .getGlanceIds(SpinWheelGlanceWidget::class.java)
                        .forEach { glanceId ->
                            widget.update(context, glanceId)
                            refreshed++
                        }
                    if (refreshed == 0) {
                        Log.d(TAG, "No pinned Spin Wheel widgets — nothing to refresh.")
                    } else {
                        Log.d(TAG, "Refreshing $refreshed pinned widget(s)")
                    }
                }

                // Also write the per-widget preferences (spin duration,
                // min/max spins) so the first tap on the spin button uses
                // the JSON-driven values rather than the defaults.
                runBlocking {
                    val graph = com.example.spinwheel.di.SpinWheelGraph.get(context)
                    val config = graph.repository.getCachedConfig() ?: return@runBlocking
                    val mgr = androidx.glance.appwidget.GlanceAppWidgetManager(context)
                    mgr.getGlanceIds(SpinWheelGlanceWidget::class.java).forEach { glanceId ->
                        androidx.glance.appwidget.state.updateAppWidgetState(
                            context,
                            PreferencesGlanceStateDefinition as GlanceStateDefinition<Preferences>,
                            glanceId,
                        ) { prefs ->
                            val mutable = prefs.toMutablePreferences()
                            mutable[SpinWheelGlanceWidget.SPIN_DURATION_MS] = config.spinDurationMs
                            mutable[SpinWheelGlanceWidget.MIN_SPINS] = config.minSpins
                            mutable[SpinWheelGlanceWidget.MAX_SPINS] = config.maxSpins
                            mutable.toPreferences()
                        }
                    }
                }
            } catch (e: Throwable) {
                Log.e(TAG, "requestUpdate failed: ${e.message}", e)
            }
        }
    }
}
