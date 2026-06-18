package com.example.spinwheel.ui

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.state.PreferencesGlanceStateDefinition

private const val TAG = "SpinWheelWidget"

/**
 * The home-screen Spin Wheel widget (Glance-based RemoteViews).
 *
 * State is held in a [PreferencesGlanceStateDefinition] keyed by widget ID
 * so each widget instance can be animated independently (e.g. two pinned
 * widgets can spin at different angles / speeds).
 *
 * Render pipeline:
 * 1. `provideGlance` is invoked when the widget is created OR after a
 *    `SpinWheelWidget().update(context, glanceId)` call.
 * 2. The Composable [WidgetContent] inspects the current state preferences
 *    + the cached assets (via [getAssets]) to decide which sub-layout to draw:
 *    - All images present       → [WheelContent]
 *    - Loading in progress       → [LoadingContent]
 *    - Last attempt failed       → [ErrorContent]
 *    - First add (no data yet)   → [InitialContent]
 */
class SpinWheelGlanceWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        Log.d(TAG, "provideGlance invoked")
        provideContent {
            WidgetContent(context)
        }
    }

    companion object {
        /** Current rotation angle of the wheel (degrees). */
        val ROTATION_KEY = floatPreferencesKey("wheel_rotation_angle")

        /** Set to `true` while [LoadWheelActionCallback] is downloading assets. */
        val IS_LOADING_KEY = booleanPreferencesKey("is_loading")

        /** Non-null when the last load attempt failed. Cleared on retry. */
        val ERROR_MESSAGE_KEY = stringPreferencesKey("error_message")

        /** Total spin duration in ms — comes from the JSON `wheel.rotation.duration`. */
        val SPIN_DURATION_MS = longPreferencesKey("spin_duration_ms")

        /** Minimum full rotations per spin — comes from `wheel.rotation.minimumSpins`. */
        val MIN_SPINS = intPreferencesKey("min_spins")

        /** Maximum full rotations per spin — comes from `wheel.rotation.maximumSpins`. */
        val MAX_SPINS = intPreferencesKey("max_spins")
    }
}
