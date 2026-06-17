package com.example.spinwheel.ui

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.example.spinwheel.ui.SpinActionCallback.Companion.DEFAULT_MAX_SPINS
import com.example.spinwheel.ui.SpinActionCallback.Companion.DEFAULT_MIN_SPINS
import com.example.spinwheel.ui.SpinActionCallback.Companion.DEFAULT_SPIN_DURATION_MS
import kotlinx.coroutines.delay
import kotlin.math.pow

/**
 * Glance [ActionCallback] invoked when the user taps the spin button.
 *
 * ## Animation technique
 *
 * Glance widgets run via `RemoteViews` — standard Compose animation APIs
 * (`Animatable`, `graphicsLayer`) are unavailable. The animation is a
 * "flip-book": each frame writes a new angle to DataStore Preferences
 * then calls `widget.update()` to push a fresh `RemoteViews`.
 *
 * ## Parameters sourced from Firebase Remote Config
 *
 * | RC field                          | Read from widget pref         | Fallback      |
 * |-----------------------------------|-------------------------------|---------------|
 * | `wheel.rotation.duration` (ms)    | [SpinWheelGlanceWidget.SPIN_DURATION_MS] | [DEFAULT_SPIN_DURATION_MS] |
 * | `wheel.rotation.minimumSpins`     | [SpinWheelGlanceWidget.MIN_SPINS]        | [DEFAULT_MIN_SPINS]        |
 * | `wheel.rotation.maximumSpins`     | [SpinWheelGlanceWidget.MAX_SPINS]        | [DEFAULT_MAX_SPINS]        |
 *
 * The three values are written to widget prefs once per successful load
 * (by [LoadWheelActionCallback]). The fallbacks match the values in
 * `docs/firebase_rc_wheel_config.json` so the spin still feels right if
 * the user somehow gets to a state where no load has ever succeeded.
 *
 * ## Frame-level math
 *
 * - Total duration is fixed (the Firebase-configured value, clamped).
 * - Frame count = `duration / FRAME_DELAY_MS`, clamped to a sensible range
 *   so a misconfigured Firebase (e.g. duration=1_000_000) can't lock up
 *   the worker / burn the battery.
 * - Full rotations = a random integer in `[minSpins to maxSpins]` +
 *   a random landing angle in `[0..359]`.
 *
 * ## Easing: quintic ease-out (`1 - (1 - t)^5`)
 *
 * Quintic starts much faster and decelerates far more gradually than a
 * quadratic ease — it mimics a physical wheel slowing down.
 */
class SpinActionCallback : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        // ── 1. Read current wheel angle + animation params from widget prefs ── //
        val prefs = getAppWidgetState<Preferences>(
            context, PreferencesGlanceStateDefinition, glanceId
        )
        val baseAngle = prefs[SpinWheelGlanceWidget.ROTATION_KEY] ?: 0f

        val durationMs = prefs[SpinWheelGlanceWidget.SPIN_DURATION_MS] ?: DEFAULT_SPIN_DURATION_MS
        val minSpins = prefs[SpinWheelGlanceWidget.MIN_SPINS] ?: DEFAULT_MIN_SPINS
        val maxSpins = prefs[SpinWheelGlanceWidget.MAX_SPINS] ?: DEFAULT_MAX_SPINS

        // ── 2. Compute animation parameters ───────────────────────────────── //
        val frameCount = framesFromDuration(durationMs)
        val fullRotations = (minSpins..maxSpins).random()
        val partialDeg = (0..MAX_PARTIAL_DEG).random().toFloat()
        val totalDelta = fullRotations * 360f + partialDeg

        Log.d(
            TAG,
            "spin start — duration=${durationMs}ms frames=$frameCount rotations=$fullRotations",
        )

        // ── 3. Animate: quintic ease-out flip-book ───────────────────────── //
        for (frame in 1..frameCount) {
            val t = frame.toFloat() / frameCount

            // Quintic ease-out — fast start, very gradual deceleration at end
            val eased = 1f - (1f - t).pow(QUINTIC_EXPONENT)

            val current = (baseAngle + totalDelta * eased) % 360f

            updateAppWidgetState(context, glanceId) { p ->
                p[SpinWheelGlanceWidget.ROTATION_KEY] = current
            }
            SpinWheelGlanceWidget().update(context, glanceId)

            delay(FRAME_DELAY_MS)
        }

        Log.d(TAG, "spin complete — final angle=${(baseAngle + totalDelta) % 360f}°")
    }

    /**
     * Convert the configured total duration into a frame count, clamped
     * to a sensible range so weird remote-config values can't lock up the
     * widget host or burn battery.
     */
    private fun framesFromDuration(durationMs: Long): Int {
        val raw = (durationMs / FRAME_DELAY_MS).toInt()
        return raw.coerceIn(MIN_FRAME_COUNT, MAX_FRAME_COUNT)
    }

    companion object {
        private const val TAG = "SpinActionCallback"

        /** Delay between animation frames. Smaller = smoother, more battery. */
        private const val FRAME_DELAY_MS = 60L

        /** Quintic exponent — `5` gives a physical-feel decel. */
        private const val QUINTIC_EXPONENT = 5

        /** Frame-count clamp — keeps the animation within sane bounds. */
        private const val MIN_FRAME_COUNT = 20
        private const val MAX_FRAME_COUNT = 200

        /** Inclusive upper bound of the random landing angle (0–359). */
        private const val MAX_PARTIAL_DEG = 359

        /** Defaults used when the widget hasn't been synced yet. Match the
         *  values in `docs/firebase_rc_wheel_config.json` so behaviour is
         *  identical to a fresh install with a successful load. */
        private const val DEFAULT_SPIN_DURATION_MS = 3_000L
        private const val DEFAULT_MIN_SPINS = 5
        private const val DEFAULT_MAX_SPINS = 8
    }
}
