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
import com.example.spinwheel.data.local.AssetKey
import com.example.spinwheel.di.SpinWheelGraph


private const val TAG = "SpinWheelWidget"


class SpinWheelGlanceWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        Log.d(TAG, "provideGlance invoked")


        provideContent {
            // Read Glance DataStore state — available synchronously inside provideContent
            val prefs        = currentState<Preferences>()
            val isLoading    = prefs[IS_LOADING_KEY]    ?: false
            val errorMessage = prefs[ERROR_MESSAGE_KEY]

            val graph = SpinWheelGraph.get(context)

            // Read directly from disk. Any failure ends up as null bitmaps → no throw.
            val bgBitmap    = decodeSafe(graph.repository.getImageBytes(AssetKey.BG))
            val wheelBitmap = decodeSafe(
                graph.repository.getImageBytes(AssetKey.WHEEL),
                context,
                scaleDown = true,
            )
            val frameBitmap = decodeSafe(graph.repository.getImageBytes(AssetKey.FRAME))
            val spinBitmap  = decodeSafe(graph.repository.getImageBytes(AssetKey.SPIN))

            when {
                // Images already on disk — show the wheel
                bgBitmap != null && wheelBitmap != null -> WheelContent(
                    bg    = bgBitmap,
                    wheel = wheelBitmap,
                    frame = frameBitmap,
                    spin  = spinBitmap,
                )
                // Something went wrong — show error with retry button
                errorMessage != null -> ErrorContent(message = errorMessage)
                // Download in progress — show a spinner text
                isLoading -> LoadingContent()
                // Nothing yet — show the "Load Wheel" button
                else -> InitialContent()
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────── //
    //  UI                                                                  //
    // ──────────────────────────────────────────────────────────────────── //

    /** Shown while images are being fetched from Firebase + Drive. */
    @SuppressLint("RestrictedApi")
    @Composable
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
                    text  = "Downloading assets…",
                    style = TextStyle(
                        color    = ColorProvider(Color(0xFF8888AA)),
                        fontSize = 11.sp,
                    ),
                )
            }
        }
    }

    /**
     * Shown on first add — before any images are fetched.
     * Tapping the button triggers [LoadWheelActionCallback] which runs
     * the full download pipeline inside Glance's managed goAsync scope.
     * The [glanceId] is passed directly so there is no DataStore race.
     */
    @SuppressLint("RestrictedApi")
    @Composable
    private fun InitialContent() {
        Box(
            modifier         = GlanceModifier.fillMaxSize()
                .background(ColorProvider(Color(0xFF1A1A2E))),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "🎡", style = TextStyle(fontSize = 40.sp))
                Text(
                    text  = "Spin Wheel",
                    style = TextStyle(
                        color      = ColorProvider(Color.White),
                        fontSize   = 16.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
                Text(
                    text  = "Tap to load",
                    style = TextStyle(
                        color      = ColorProvider(Color(0xFFFFD700)),
                        fontSize   = 13.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    modifier = GlanceModifier
                        .clickable(onClick = actionRunCallback<LoadWheelActionCallback>()),
                )
            }
        }
    }

    /**
     * Shown when the config fetch or image download failed.
     * Tapping "Retry" clears the error state and runs the pipeline again.
     */
    @SuppressLint("RestrictedApi")
    @Composable
    private fun ErrorContent(message: String) {
        Box(
            modifier         = GlanceModifier.fillMaxSize()
                .background(ColorProvider(Color(0xFF1A1A2E))),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "⚠️", style = TextStyle(fontSize = 32.sp))
                Text(
                    text  = "Failed to load",
                    style = TextStyle(
                        color      = ColorProvider(Color(0xFFFF6B6B)),
                        fontSize   = 14.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
                Text(
                    text  = message,
                    style = TextStyle(
                        color    = ColorProvider(Color(0xFF8888AA)),
                        fontSize = 10.sp,
                    ),
                )
                Text(
                    text  = "Tap to retry",
                    style = TextStyle(
                        color      = ColorProvider(Color(0xFFFFD700)),
                        fontSize   = 13.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    modifier = GlanceModifier
                        .clickable(onClick = actionRunCallback<LoadWheelActionCallback>()),
                )
            }
        }
    }

    @Composable
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
        val ROTATION_KEY    = floatPreferencesKey("wheel_rotation_angle")
        /** Set to `true` while [LoadWheelActionCallback] is downloading assets. */
        val IS_LOADING_KEY  = booleanPreferencesKey("is_loading")
        /** Non-null when the last load attempt failed. Cleared on retry. */
        val ERROR_MESSAGE_KEY = stringPreferencesKey("error_message")
    }
}
