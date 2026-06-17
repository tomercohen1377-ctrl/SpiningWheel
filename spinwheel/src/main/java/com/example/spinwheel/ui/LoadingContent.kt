package com.example.spinwheel.ui

import android.annotation.SuppressLint
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider

/** Shown while images are being fetched from Firebase + Drive. */
@SuppressLint("RestrictedApi")
@Composable
fun LoadingContent() {
    Box(
        modifier = GlanceModifier.fillMaxSize()
            .background(ColorProvider(Color(0xFF1A1A2E))),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "🎡", style = TextStyle(fontSize = 32.sp))
            Text(
                text = "Spin Wheel",
                style = TextStyle(
                    color = ColorProvider(Color.White),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Text(
                text = "Downloading assets…",
                style = TextStyle(
                    color = ColorProvider(Color(0xFF8888AA)),
                    fontSize = 11.sp,
                ),
            )
        }
    }
}