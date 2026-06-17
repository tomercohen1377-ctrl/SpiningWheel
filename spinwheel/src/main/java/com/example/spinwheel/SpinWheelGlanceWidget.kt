package com.example.spinwheel

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.floatPreferencesKey
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
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.spinwheel.data.local.AssetKey
import com.example.spinwheel.di.SpinWheelGraph

/**
 * State-driven Glance widget.
 *
 * ## Design rules (learned the hard way)
 *
 * 1. **`provideGlance` must NEVER throw.** Any uncaught exception crashes
 *    the widget host which then shows "Can't load widget" to the user.
 *    Every BitmapFactory call is wrapped in a safe decode helper that
 *    returns `null` on failure.
 *
 * 2. **No background `launch` from inside `provideGlance`.** Using
 *    `coroutineScope { launch { … } }` inside the suspend `provideGlance`
 *    is a bug — when this block returns, the scope cancels, killing the
 *    launched collector. We learned this empirically: this is what caused
 *    the "Can't load widget" error.
 *
 * 3. **The widget is a pure renderer.** It reads whatever is in the local
 *    data source and renders it. The Receiver + Worker push `update()`
 *    calls after every successful sync to drive re-renders. This is
 *    the official Glance pattern for widgets that don't need their own
 *    long-lived collectors.
 */
private const val TAG = "SpinWheelWidget"


class SpinWheelGlanceWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        Log.d(TAG, "provideGlance invoked")


        provideContent {

            val graph = SpinWheelGraph.get(context)

            // Read directly from disk. Any failure here ends up as null bitmaps,
            // which fall back to LoadingContent — not a thrown exception.
            val bgBitmap   = decodeSafe(graph.repository.getImageBytes(AssetKey.BG))
            val wheelBitmap = decodeSafe(
                graph.repository.getImageBytes(AssetKey.WHEEL),
                context,
                scaleDown = true,
            )
            val frameBitmap = decodeSafe(graph.repository.getImageBytes(AssetKey.FRAME))
            val spinBitmap  = decodeSafe(graph.repository.getImageBytes(AssetKey.SPIN))


            if (bgBitmap == null || wheelBitmap == null) {
                LoadingContent()
            } else {
                WheelContent(
                    bg    = bgBitmap,
                    wheel = wheelBitmap,
                    frame = frameBitmap,
                    spin  = spinBitmap,
                )
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────── //
    //  UI                                                                  //
    // ──────────────────────────────────────────────────────────────────── //

    @SuppressLint("RestrictedApi")
    @androidx.compose.runtime.Composable
    private fun LoadingContent() {
        Box(
            modifier         = GlanceModifier.fillMaxSize()
                .background(ColorProvider(Color(0xFF1A1A2E))),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "🎡", style = TextStyle(fontSize = 32.sp))
                Text(
                    text  = "Spin Wheel",
                    style = TextStyle(
                        color      = ColorProvider(Color.White),
                        fontSize   = 14.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
                Text(
                    text  = "Loading from Firebase…",
                    style = TextStyle(
                        color    = ColorProvider(Color(0xFF8888AA)),
                        fontSize = 11.sp,
                    ),
                )
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun WheelContent(
        bg: Bitmap,
        wheel: Bitmap,
        frame: Bitmap?,
        spin: Bitmap?,
    ) {
        val prefs         = currentState<Preferences>()
        val rotationAngle = prefs[ROTATION_KEY] ?: 0f

        Box(
            modifier         = GlanceModifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                provider           = ImageProvider(bg),
                contentDescription = "Background",
                modifier           = GlanceModifier.fillMaxSize(),
            )

            val rotatedWheel =
                if (rotationAngle != 0f) rotateBitmap(wheel, rotationAngle) else wheel
            Image(
                provider           = ImageProvider(rotatedWheel),
                contentDescription = "Spinning Wheel",
                modifier           = GlanceModifier.size(220.dp),
            )

            frame?.let {
                Image(
                    provider           = ImageProvider(it),
                    contentDescription = "Wheel Frame",
                    modifier           = GlanceModifier.size(240.dp),
                )
            }

            spin?.let {
                Image(
                    provider           = ImageProvider(it),
                    contentDescription = "Tap to Spin",
                    modifier           = GlanceModifier
                        .size(72.dp)
                        .clickable(onClick = actionRunCallback<SpinActionCallback>()),
                )
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────── //
    //  Helpers                                                              //
    // ──────────────────────────────────────────────────────────────────── //

    /**
     * Safely decodes a [ByteArray] to a [Bitmap].
     *
     * Returns `null` on any failure (null/empty bytes, decoder failure,
     * out-of-memory, unsupported format, etc.). This guarantees that
     * [provideGlance] never throws — a thrown exception inside
     * `provideGlance` is what causes the "Can't load widget" / "Problem
     * loading widget" error shown by Android's widget host.
     */
    private fun decodeSafe(
        bytes: ByteArray?,
        context: Context?   = null,
        scaleDown: Boolean  = false,
    ): Bitmap? {
        if (bytes == null || bytes.isEmpty()) return null
        val src = try {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Throwable) {
            return null
        } ?: return null

        if (!scaleDown || context == null) return src

        val density = context.resources.displayMetrics.density
        val targetPx = (260f * density).toInt().coerceAtLeast(64)
        return if (src.width > targetPx) {
            try {
                val scaled = Bitmap.createScaledBitmap(src, targetPx, targetPx, true)
                if (scaled !== src) src.recycle()
                scaled
            } catch (e: Throwable) {
                src
            }
        } else src
    }

    private fun rotateBitmap(source: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    companion object {
        val ROTATION_KEY = floatPreferencesKey("wheel_rotation_angle")
    }
}
