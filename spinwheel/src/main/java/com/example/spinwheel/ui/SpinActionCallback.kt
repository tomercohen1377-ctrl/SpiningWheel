package com.example.spinwheel.ui

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import kotlinx.coroutines.delay
import kotlin.math.pow

/**
 * Glance [ActionCallback] invoked when the user taps the spin button.
 *
 * ## Animation design
 *
 * Glance widgets run via `RemoteViews` — standard Compose animation APIs
 * (`Animatable`, `graphicsLayer`) are unavailable.  The animation is a
 * "flip-book": each frame writes a new angle to DataStore Preferences,
 * then calls `widget.update()` to push a fresh `RemoteViews`.
 *
 * ### Easing: quintic ease-out
 * ```
 * eased = 1 - (1 - t)^5
 * ```
 * vs. the old quadratic `1 - (1 - t)²`:
 *
 * ```
 *  1 │         ╭────────  quintic  (this)
 *    │      ╭──╯
 *    │    ╭─╯              quadratic (old)
 *    │  ╭─╯
 *    │ ╭╯
 *  0 └──────────────────────> t
 *    0                        1
 * ```
 * Quintic starts much faster and decelerates far more gradually —
 * this mimics the feel of a physical spinning wheel slowing down
 * rather than a smooth mechanical deceleration.
 *
 * ### Parameters
 * | Parameter        | Old  | New  | Effect                         |
 * |------------------|------|------|--------------------------------|
 * | ANIMATION_FRAMES | 20   | 50   | ~2.5× more frames → smoother  |
 * | FRAME_DELAY_MS   | 80   | 60   | faster frame rate              |
 * | Total duration   | 1.6s | 3.0s | more satisfying spin           |
 * | Full rotations   | 3–5  | 5–8  | wheel travels further          |
 */
class SpinActionCallback : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        // ── 1. Read current wheel angle ──────────────────────────────────── //
        val currentPrefs = getAppWidgetState<Preferences>(
            context, PreferencesGlanceStateDefinition, glanceId
        )
        val baseAngle = currentPrefs[SpinWheelGlanceWidget.ROTATION_KEY] ?: 0f

        // ── 2. Calculate total rotation delta ────────────────────────────── //
        // 5–8 full rotations + random landing angle
        val fullRotations = (5..8).random()
        val partialDeg = (0..359).random().toFloat()
        val totalDelta = fullRotations * 360f + partialDeg

        // ── 3. Animate: quintic ease-out flip-book ───────────────────────── //
        for (frame in 1..ANIMATION_FRAMES) {
            val t = frame.toFloat() / ANIMATION_FRAMES

            // Quintic ease-out — fast start, very gradual deceleration at end
            val eased = 1f - (1f - t).pow(5)

            val current = (baseAngle + totalDelta * eased) % 360f

            updateAppWidgetState(context, glanceId) { prefs ->
                prefs[SpinWheelGlanceWidget.ROTATION_KEY] = current
            }
            SpinWheelGlanceWidget().update(context, glanceId)

            delay(FRAME_DELAY_MS)
        }
    }

    companion object {
        /** Frames in the spin animation. More frames = smoother motion. */
        private const val ANIMATION_FRAMES = 50

        /** Delay between frames.  50 × 60 ms = 3 000 ms total duration. */
        private const val FRAME_DELAY_MS = 60L
    }
}
