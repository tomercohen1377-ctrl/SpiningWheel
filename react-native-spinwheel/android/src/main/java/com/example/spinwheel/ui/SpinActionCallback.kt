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
import kotlinx.coroutines.delay
import kotlin.math.pow

/**
 * Glance [ActionCallback] invoked when the user taps the spin button on
 * the wheel.
 *
 * Glance widgets render via RemoteViews; standard Compose animation APIs
 * are unavailable so the animation is a flip-book: each frame writes a new
 * angle to DataStore Preferences and pushes a fresh RemoteViews.
 *
 * Animation parameters come from the cached JSON config. Defaults match
 * the canonical values so the spin still feels right before the first sync:
 *   - duration    = 3 000 ms
 *   - minSpins    = 5
 *   - maxSpins    = 8
 *
 * Easing is quintic (1 minus the 5th power of 1 minus t) — fast start, very
 * slow finish, mimicking a physical wheel slowing down.
 */
class SpinActionCallback : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val prefs = getAppWidgetState<Preferences>(
            context, PreferencesGlanceStateDefinition, glanceId
        )
        val baseAngle = prefs[SpinWheelGlanceWidget.ROTATION_KEY] ?: 0f

        val durationMs = prefs[SpinWheelGlanceWidget.SPIN_DURATION_MS] ?: DEFAULT_SPIN_DURATION_MS
        val minSpins   = prefs[SpinWheelGlanceWidget.MIN_SPINS] ?: DEFAULT_MIN_SPINS
        val maxSpins   = prefs[SpinWheelGlanceWidget.MAX_SPINS] ?: DEFAULT_MAX_SPINS

        val frameCount    = framesFromDuration(durationMs)
        val fullRotations = (minSpins..maxSpins).random()
        val partialDeg    = (0..MAX_PARTIAL_DEG).random().toFloat()
        val totalDelta    = fullRotations * 360f + partialDeg

        Log.d(
            TAG,
            "spin start — duration=${durationMs}ms frames=$frameCount rotations=$fullRotations",
        )

        for (frame in 1..frameCount) {
            val t = frame.toFloat() / frameCount
            val eased = 1f - (1f - t).pow(QUINTIC_EXPONENT)

            val current = (baseAngle + totalDelta * eased) % 360f

            updateAppWidgetState(context, glanceId) { p ->
                p[SpinWheelGlanceWidget.ROTATION_KEY] = current
            }
            SpinWheelGlanceWidget().update(context, glanceId)

            delay(FRAME_DELAY_MS)
        }

        Log.d(TAG, "spin complete — final angle=${(baseAngle + totalDelta) % 360f} degrees")
    }

    private fun framesFromDuration(durationMs: Long): Int {
        val raw = (durationMs / FRAME_DELAY_MS).toInt()
        return raw.coerceIn(MIN_FRAME_COUNT, MAX_FRAME_COUNT)
    }

    companion object {
        private const val TAG = "SpinActionCallback"

        private const val FRAME_DELAY_MS = 60L
        private const val QUINTIC_EXPONENT = 5
        private const val MIN_FRAME_COUNT = 20
        private const val MAX_FRAME_COUNT = 200
        private const val MAX_PARTIAL_DEG = 359

        private const val DEFAULT_SPIN_DURATION_MS = 3_000L
        private const val DEFAULT_MIN_SPINS = 5
        private const val DEFAULT_MAX_SPINS = 8
    }
}
