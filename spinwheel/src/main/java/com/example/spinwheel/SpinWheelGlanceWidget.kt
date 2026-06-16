package com.example.spinwheel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.wrapContentSize
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider

/**
 * Jetpack Glance home-screen widget for the Spin Wheel.
 *
 * Glance uses `@Composable` syntax and translates the layout into Android
 * `RemoteViews` — no XML required. The widget has four layers:
 *
 * 1. **Background** — full-bleed image (bg)
 * 2. **Wheel** — rotating bitmap, angle stored in DataStore Preferences
 * 3. **Frame** — static decorative overlay
 * 4. **Spin button** — tappable; fires [SpinActionCallback]
 *
 * If assets have not been downloaded yet, a dark loading card is shown
 * with a prompt to open the app and tap "Sync Assets from Drive".
 */
class SpinWheelGlanceWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Load cached bitmaps (file I/O) before entering composable scope
        val sync = WidgetSyncService(context)
        val bgBitmap    = sync.getCachedBitmap(FILE_BG)
        val wheelBitmap = sync.getCachedBitmap(FILE_WHEEL)
        val frameBitmap = sync.getCachedBitmap(FILE_FRAME)
        val spinBitmap  = sync.getCachedBitmap(FILE_SPIN)

        provideContent {
            val assetsReady = bgBitmap != null && wheelBitmap != null

            if (!assetsReady) {
                LoadingContent()
            } else {
                WheelContent(
                    bg    = bgBitmap!!,
                    wheel = wheelBitmap!!,
                    frame = frameBitmap,
                    spin  = spinBitmap
                )
            }
        }
    }

    // ───────────────��─────────────────────────────────────────────────────── //
    //  Loading state                                                          //
    // ─────────────────────────────────────────────────────────────────────── //

    @Composable
    private fun LoadingContent() {
        Box(
            modifier         = GlanceModifier
                .fillMaxSize()
                .background(ColorProvider(Color(0xFF1A1A2E))),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text  = "🎡",
                    style = TextStyle(fontSize = 32.sp)
                )
                Text(
                    text  = "Spin Wheel",
                    style = TextStyle(
                        color      = ColorProvider(Color.White),
                        fontSize   = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    text  = "Loading from Firebase…",
                    style = TextStyle(
                        color    = ColorProvider(Color(0xFF8888AA)),
                        fontSize = 11.sp
                    )
                )
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────── //
    //  Main widget UI                                                         //
    // ─────────────────────────────────────────────────────────────────────── //

    @Composable
    private fun WheelContent(
        bg:    Bitmap,
        wheel: Bitmap,
        frame: Bitmap?,
        spin:  Bitmap?
    ) {
        val prefs         = currentState<Preferences>()
        val rotationAngle = prefs[ROTATION_KEY] ?: 0f

        Box(
            modifier         = GlanceModifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            // Layer 1 — Background
            Image(
                provider           = ImageProvider(bg),
                contentDescription = "Background",
                modifier           = GlanceModifier.fillMaxSize()
            )

            // Layer 2 — Spinning wheel (angle applied via Matrix on the bitmap)
            val rotatedWheel = if (rotationAngle != 0f) rotateBitmap(wheel, rotationAngle) else wheel
            Image(
                provider           = ImageProvider(rotatedWheel),
                contentDescription = "Spinning Wheel",
                modifier           = GlanceModifier.size(220.dp)
            )

            // Layer 3 — Static frame overlay
            frame?.let {
                Image(
                    provider           = ImageProvider(it),
                    contentDescription = "Wheel Frame",
                    modifier           = GlanceModifier.size(240.dp)
                )
            }

            // Layer 4 — Tappable spin button
            spin?.let {
                Image(
                    provider           = ImageProvider(it),
                    contentDescription = "Tap to Spin",
                    modifier           = GlanceModifier
                        .size(72.dp)
                        .clickable(onClick = actionRunCallback<SpinActionCallback>())
                )
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────── //
    //  Helpers                                                                //
    // ─────────────────────────────────────────────────────────────────────── //

    private fun rotateBitmap(source: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    companion object {
        val ROTATION_KEY = floatPreferencesKey("wheel_rotation_angle")
    }
}
