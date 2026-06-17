package com.example.spinwheel.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.size
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider


private const val TAG = "SpinWheelWidget"


class SpinWheelGlanceWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        Log.d(TAG, "provideGlance invoked")
        provideContent {
            WidgetContent(context)
        }
    }

    companion object {
        val ROTATION_KEY = floatPreferencesKey("wheel_rotation_angle")

        /** Set to `true` while [LoadWheelActionCallback] is downloading assets. */
        val IS_LOADING_KEY = booleanPreferencesKey("is_loading")

        /** Non-null when the last load attempt failed. Cleared on retry. */
        val ERROR_MESSAGE_KEY = stringPreferencesKey("error_message")

        /** Total spin duration in ms — comes from Firebase RC `wheel.rotation.duration`. */
        val SPIN_DURATION_MS = longPreferencesKey("spin_duration_ms")

        /** Minimum full rotations per spin — comes from `wheel.rotation.minimumSpins`. */
        val MIN_SPINS = intPreferencesKey("min_spins")

        /** Maximum full rotations per spin — comes from `wheel.rotation.maximumSpins`. */
        val MAX_SPINS = intPreferencesKey("max_spins")
    }
}
