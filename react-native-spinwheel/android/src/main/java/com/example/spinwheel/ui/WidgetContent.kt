package com.example.spinwheel.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.datastore.preferences.core.Preferences
import androidx.glance.currentState
import com.example.spinwheel.data.local.AssetKey
import com.example.spinwheel.di.SpinWheelGraph
import com.example.spinwheel.ui.SpinWheelGlanceWidget.Companion.ERROR_MESSAGE_KEY
import com.example.spinwheel.ui.SpinWheelGlanceWidget.Companion.IS_LOADING_KEY

/**
 * Top-level Composable deciding which sub-layout to render.
 *
 * Order of precedence (later wins when conditions overlap):
 * 1. All 4 images cached on disk → [WheelContent]
 * 2. `is_loading` flag set                  → [LoadingContent]
 * 3. `error_message` set                   → [ErrorContent]
 * 4. Otherwise                              → [InitialContent]
 */
@Composable
fun WidgetContent(context: Context) {
    val prefs = currentState<Preferences>()
    val isLoading = prefs[IS_LOADING_KEY] ?: false
    val errorMessage = prefs[ERROR_MESSAGE_KEY]
    val assets = getAssets(context)

    when {
        assets != null      -> WheelContent(assets)
        isLoading           -> LoadingContent()
        errorMessage != null -> ErrorContent(message = errorMessage)
        else                -> InitialContent()
    }
}

/**
 * Loads the four cached images from disk through the shared
 * [SpinWheelGraph] and returns them as a [WheelAssets] or `null` if any
 * image is missing / corrupt.
 */
fun getAssets(context: Context): WheelAssets? {
    val graph = SpinWheelGraph.get(context)
    val bgBitmap = decodeSafe(graph.repository.getImageBytes(AssetKey.BG))
    val wheelBitmap = decodeSafe(
        graph.repository.getImageBytes(AssetKey.WHEEL),
        context,
        scaleDown = true,
    )
    val frameBitmap = decodeSafe(graph.repository.getImageBytes(AssetKey.FRAME))
    val spinBitmap = decodeSafe(graph.repository.getImageBytes(AssetKey.SPIN))
    return if (bgBitmap == null || wheelBitmap == null || frameBitmap == null || spinBitmap == null)
        null
    else WheelAssets(
        bg = bgBitmap,
        wheel = wheelBitmap,
        frame = frameBitmap,
        spin = spinBitmap,
    )
}

/**
 * Safely decodes a [ByteArray] to a [Bitmap].
 *
 * Returns `null` on any failure (null/empty bytes, decoder failure,
 * out-of-memory, unsupported format, etc.). This guarantees that
 * `provideGlance` never throws — a thrown exception inside
 * `provideGlance` is what causes the "Can't load widget" / "Problem
 * loading widget" error shown by Android's widget host.
 */
private fun decodeSafe(
    bytes: ByteArray?,
    context: Context? = null,
    scaleDown: Boolean = false,
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
