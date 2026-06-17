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

    /**
     * Called when the first instance of the widget is pinned.
     *
     * No automatic download here — the user sees the [SpinWheelGlanceWidget.InitialContent]
     * screen with a "Tap to load" button. Tapping it triggers [LoadWheelActionCallback]
     * which runs the full download pipeline inside Glance's goAsync scope (no
     * WorkManager race conditions, glanceId passed directly).
     */
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        Log.d(TAG, "onEnabled — showing initial screen, waiting for user tap")
    }
}
