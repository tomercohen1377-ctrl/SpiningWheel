package com.example.spinwheel.ui

import android.annotation.SuppressLint
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider

/**
 * Shown when the config fetch or image download failed.
 * Tapping "Retry" clears the error state and runs the pipeline again.
 */
@SuppressLint("RestrictedApi")
@Composable
fun ErrorContent(message: String) {
    Box(
        modifier = GlanceModifier.fillMaxSize()
            .background(ColorProvider(Color(0xFF1A1A2E))),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "⚠️", style = TextStyle(fontSize = 32.sp))
            Text(
                text = "Failed to load",
                style = TextStyle(
                    color = ColorProvider(Color(0xFFFF6B6B)),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Text(
                text = message,
                style = TextStyle(
                    color = ColorProvider(Color(0xFF8888AA)),
                    fontSize = 10.sp,
                ),
            )
            Text(
                text = "Tap to retry",
                style = TextStyle(
                    color = ColorProvider(Color(0xFFFFD700)),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                ),
                modifier = GlanceModifier
                    .clickable(onClick = actionRunCallback<LoadWheelActionCallback>()),
            )
        }
    }
}