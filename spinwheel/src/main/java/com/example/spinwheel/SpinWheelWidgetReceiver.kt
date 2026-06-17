package com.example.spinwheel

import android.appwidget.AppWidgetManager
import android.content.Context
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

private const val TAG = "SpinWheelWidgetReceiver"

/**
 * Home-screen widget receiver.
 *
 * Both [onEnabled] (first-add) and [onUpdate] (periodic 12-hour tick,
 * reboot, re-add) enqueue the background sync worker.  This ensures the
 * worker runs regardless of whether the widget was just placed for the
 * first time or refreshed by the system.
 */
class SpinWheelWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = SpinWheelGlanceWidget()

    /** Called once when the FIRST widget of this type is placed. */
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        Log.d(TAG, "onEnabled — enqueueing worker")
        WheelWidgetWorker.enqueue(context)
    }

    /**
     * Called on every system update (right after first-add, on periodic
     * 12-hour ticks, and after a reboot).  `super.onUpdate` runs Glance's
     * bootstrap (registers the receiver+provider in Glance's DataStore and
     * renders the initial Loading state).  We then re-enqueue the worker so
     * fresh images are fetched if needed.
     */
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        Log.d(TAG, "onUpdate — ids=${appWidgetIds.toList()} — enqueueing worker")
        WheelWidgetWorker.enqueue(context)
    }
}
