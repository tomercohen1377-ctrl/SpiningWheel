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
 * Shown on first add — before any images are fetched.
 * Tapping the button triggers [LoadWheelActionCallback] which runs
 * the full download pipeline inside Glance's managed goAsync scope.
 * The [glanceId] is passed directly so there is no DataStore race.
 */
@SuppressLint("RestrictedApi")
@Composable
fun InitialContent() {
    Box(
        modifier = GlanceModifier.fillMaxSize()
            .background(ColorProvider(Color(0xFF1A1A2E))),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "🎡", style = TextStyle(fontSize = 40.sp))
            Text(
                text = "Spin Wheel",
                style = TextStyle(
                    color = ColorProvider(Color.White),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Text(
                text = "Tap to load",
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