package com.example.spinwheel

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import kotlinx.coroutines.delay

/**
 * Glance [ActionCallback] invoked when the user taps the spin button.
 *
 * ## Spin animation in a home-screen widget
 * Standard Compose animation APIs (`Animatable`, `animateFloatAsState`) cannot
 * be used in Glance because Glance runs in the Android system process — outside
 * the app's Compose runtime. Instead we simulate animation as a "flip-book":
 *
 * 1. Pick a random rotation delta (3–5 full turns + random partial).
 * 2. Apply a **quadratic ease-out** curve across [ANIMATION_FRAMES] steps.
 * 3. Each step: write the new angle to DataStore, call `widget.update()`.
 * 4. Glance recomposes, rotates the wheel bitmap via `Matrix`, and pushes
 *    fresh `RemoteViews` to every pinned widget instance.
 *
 * The result is a visually decelerating spin that takes ≈ [ANIMATION_FRAMES] ×
 * [FRAME_DELAY_MS] ms ≈ 1.6 seconds to complete.
 */
class SpinActionCallback : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        // ── 1. Read current wheel angle from DataStore ────────────────────── //
        val currentPrefs = getAppWidgetState<Preferences>(
            context, PreferencesGlanceStateDefinition, glanceId
        )
        val baseAngle = currentPrefs[SpinWheelGlanceWidget.ROTATION_KEY] ?: 0f

        // ── 2. Calculate target rotation delta ───────────────────────────── //
        // 3–5 full rotations (1 080–1 800°) + a random partial stop angle
        val fullRotations = (3..5).random()
        val partialDeg    = (0..359).random().toFloat()
        val totalDelta    = fullRotations * 360f + partialDeg

        // ── 3. Animate using ease-out flip-book ──────────────────────────── //
        for (frame in 1..ANIMATION_FRAMES) {
            val t       = frame.toFloat() / ANIMATION_FRAMES
            val eased   = 1f - (1f - t) * (1f - t)        // quadratic ease-out
            val current = (baseAngle + totalDelta * eased) % 360f

            // Write angle to DataStore Preferences
            updateAppWidgetState(context, glanceId) { prefs ->
                prefs[SpinWheelGlanceWidget.ROTATION_KEY] = current
            }

            // Re-compose the widget with the updated angle
            SpinWheelGlanceWidget().update(context, glanceId)

            // Pause between frames
            delay(FRAME_DELAY_MS)
        }
    }

    companion object {
        /** Number of animation frames for the spin. */
        private const val ANIMATION_FRAMES = 20

        /** Delay between frames in ms — total animation ≈ 20 × 80 = 1 600 ms. */
        private const val FRAME_DELAY_MS   = 80L
    }
}
