package com.example.spinwheel

import android.content.Context
import android.content.Intent
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback

/**
 * Glance [ActionCallback] invoked when the user taps the spin button on the widget.
 *
 * ## Animation strategy
 *
 * Glance widgets run in the system process via `RemoteViews` — standard Compose
 * animation APIs (`Animatable`, `animateFloatAsState`, `graphicsLayer`) are
 * unavailable.  The old flip-book approach (20 frames × 80 ms via DataStore
 * writes + `widget.update()`) produced ~12 fps and heavy bitmap allocation.
 *
 * **New approach**: launch [SpinActivity] (in the host `:app` module) via an
 * implicit Intent.  `SpinActivity` is a full-screen Compose activity that:
 * - Plays a buttery 60 fps spin using `Animatable` + `graphicsLayer { rotationZ }`
 * - Shows a golden glow ring that pulses during the spin
 * - Writes the final wheel angle back to every widget instance via DataStore
 * - Closes itself automatically after a brief pause
 *
 * The library communicates with the app-level activity through the intent action
 * string [ACTION_SPIN] so there is no compile-time dependency between modules.
 */
class SpinActionCallback : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val intent = Intent(ACTION_SPIN)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        context.startActivity(intent)
    }

    companion object {
        /** Intent action that [SpinActivity] registers an intent-filter for. */
        const val ACTION_SPIN = "com.example.spinwheel.ACTION_SPIN"
    }
}
