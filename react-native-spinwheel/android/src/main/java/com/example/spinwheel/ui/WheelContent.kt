package com.example.spinwheel.ui

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.size

/**
 * The fully-loaded wheel: background + rotating wheel + frame overlay +
 * clickable spin button.
 *
 * Reads the rotation angle from the widget's per-instance preference so
 * each pinned widget can spin independently.
 */
@Composable
fun WheelContent(assets: WheelAssets) {
    val prefs = currentState<Preferences>()
    val rotationAngle = prefs[SpinWheelGlanceWidget.ROTATION_KEY] ?: 0f

    Box(
        modifier = GlanceModifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(assets.bg),
            contentDescription = "Background",
            modifier = GlanceModifier.fillMaxSize(),
        )

        val rotatedWheel =
            if (rotationAngle != 0f) rotateBitmap(assets.wheel, rotationAngle)
            else assets.wheel
        Image(
            provider = ImageProvider(rotatedWheel),
            contentDescription = "Spinning Wheel",
            modifier = GlanceModifier.size(220.dp),
        )

        Image(
            provider = ImageProvider(assets.frame),
            contentDescription = "Wheel Frame",
            modifier = GlanceModifier.size(240.dp),
        )

        Image(
            provider = ImageProvider(assets.spin),
            contentDescription = "Tap to Spin",
            modifier = GlanceModifier
                .size(72.dp)
                .clickable(onClick = actionRunCallback<SpinActionCallback>()),
        )
    }
}

private fun rotateBitmap(source: Bitmap, degrees: Float): Bitmap {
    val matrix = Matrix().apply { postRotate(degrees) }
    return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
}

data class WheelAssets(
    val bg: Bitmap,
    val wheel: Bitmap,
    val frame: Bitmap,
    val spin: Bitmap,
)
