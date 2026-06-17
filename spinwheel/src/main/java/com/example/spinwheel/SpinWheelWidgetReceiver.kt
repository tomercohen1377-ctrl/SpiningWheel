package com.example.spinwheel

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

private const val TAG = "SpinWheelWidgetReceiver"

/**
 * Home-screen widget receiver.
 */
class SpinWheelWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = SpinWheelGlanceWidget()

    /** Called when the widget is first pinned. Hands off to WorkManager. */
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        Log.d(TAG, "onEnabled — enqueueing worker")
        WheelWidgetWorker.enqueue(context)
    }
}
