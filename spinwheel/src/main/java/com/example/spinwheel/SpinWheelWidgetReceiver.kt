package com.example.spinwheel

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver


class SpinWheelWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = SpinWheelGlanceWidget()

    /**
     * Called when this widget is added to the home screen for the first time.
     * Enqueues a [WheelWidgetWorker] so the widget loads its images immediately
     * without the user having to open the app.
     */
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WheelWidgetWorker.enqueue(context)
    }
}
