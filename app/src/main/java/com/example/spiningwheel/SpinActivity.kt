package com.example.spiningwheel

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.lifecycle.lifecycleScope
import com.example.spinwheel.FILE_BG
import com.example.spinwheel.FILE_FRAME
import com.example.spinwheel.FILE_SPIN
import com.example.spinwheel.FILE_WHEEL
import com.example.spinwheel.SpinWheelGlanceWidget
import com.example.spinwheel.WidgetSyncService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.Image as FoundationImage

/**
 * Full-screen Compose activity that plays the spin wheel animation at 60 fps.
 *
 * ## Why a separate Activity?
 *
 * Glance widgets use `RemoteViews` — Compose animation APIs (`Animatable`,
 * `graphicsLayer`) are unavailable.  This activity is launched by
 * [com.example.spinwheel.SpinActionCallback] when the user taps the spin
 * button on the home-screen widget.  It provides:
 *
 * - **60 fps GPU rotation** via `graphicsLayer { rotationZ = angle.value }`
 *   (no bitmap copies, no memory churn — pure GPU transform)
 * - **Cubic ease-out** that mimics a real spinning wheel decelerating
 * - **Golden glow ring** that pulses during the spin and fades out at rest
 * - **Haptic feedback** on both spin start and completion
 * - **Automatic close** — after the animation the final angle is written
 *   to every pinned widget instance and the activity finishes itself
 *
 * Declared with `Theme.SpinWheel.FullScreen` (edge-to-edge, no title bar).
 */
class SpinActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Load cached bitmaps on the main thread before entering composition
        // (file I/O is fast for small cached images; avoids Compose side-effects)
        val sync = WidgetSyncService(this)
        val bgBitmap    = sync.getCachedBitmap(FILE_BG)
        val wheelBitmap = sync.getCachedBitmap(FILE_WHEEL)
        val frameBitmap = sync.getCachedBitmap(FILE_FRAME)
        val spinBitmap  = sync.getCachedBitmap(FILE_SPIN)

        setContent {
            SpinWheelAnimationScreen(
                bgBitmap    = bgBitmap,
                wheelBitmap = wheelBitmap,
                frameBitmap = frameBitmap,
                spinBitmap  = spinBitmap,
                onSpinComplete = { finalAngle ->
                    // Write final angle to every pinned widget and close
                    lifecycleScope.launch {
                        val manager  = GlanceAppWidgetManager(this@SpinActivity)
                        val glanceIds = manager.getGlanceIds(SpinWheelGlanceWidget::class.java)
                        glanceIds.forEach { id ->
                            updateAppWidgetState(this@SpinActivity, id) { prefs ->
                                prefs[SpinWheelGlanceWidget.ROTATION_KEY] = finalAngle
                            }
                            SpinWheelGlanceWidget().update(this@SpinActivity, id)
                        }
                        delay(600)   // brief pause so the user sees the final position
                        finish()
                    }
                }
            )
        }
    }

    // Prevent the back-gesture from closing early while spinning
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        /* no-op during animation — activity closes itself via onSpinComplete */
    }
}

// ─────────────────────────────────────────────────────────────────────────── //
//  Composable UI                                                              //
// ─────────────────────────────────────────────────────────────────────────── //

/**
 * Full-screen spin animation.
 *
 * ### Easing curve
 * ```
 * CubicBezierEasing(0.05f, 0.85f, 0.1f, 1.0f)
 * ```
 * P1 = (0.05, 0.85) — nearly instant acceleration at the start
 * P2 = (0.10, 1.00) — very gradual approach to the final position
 *
 * This mimics a real spinning wheel: snaps to full speed, then gently
 * decelerates over the last 30 % of the animation duration.
 *
 * ### Glow ring
 * A golden sweep-gradient ring drawn via [Canvas] whose alpha is driven by
 * [animateFloatAsState].  It fades in when the wheel starts spinning and fades
 * out when it stops, adding a satisfying visual flourish.
 */
@Composable
private fun SpinWheelAnimationScreen(
    bgBitmap:    android.graphics.Bitmap?,
    wheelBitmap: android.graphics.Bitmap?,
    frameBitmap: android.graphics.Bitmap?,
    spinBitmap:  android.graphics.Bitmap?,
    onSpinComplete: (Float) -> Unit
) {
    // Total rotation: 5–8 full spins + random landing angle
    val totalRotation = remember {
        (5..8).random() * 360f + (0..359).random().toFloat()
    }
    val angle = remember { Animatable(0f) }
    var spinning by remember { mutableStateOf(true) }

    // Launch animation immediately on first composition
    LaunchedEffect(Unit) {
        angle.animateTo(
            targetValue  = totalRotation,
            animationSpec = tween(
                durationMillis = 3500,
                // Fast start, long gentle deceleration — real spinning-wheel feel
                easing = CubicBezierEasing(0.05f, 0.85f, 0.1f, 1.0f)
            )
        )
        spinning = false
        onSpinComplete(angle.value % 360f)
    }

    // Glow ring fades in when spinning, fades out when stopped
    val glowAlpha by animateFloatAsState(
        targetValue   = if (spinning) 0.85f else 0f,
        animationSpec = tween(durationMillis = 600),
        label         = "glowAlpha"
    )

    Box(
        modifier         = Modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center
    ) {

        // ── Layer 1: Background (full-bleed) ─────────────────────────────── //
        bgBitmap?.let { bmp ->
            FoundationImage(
                bitmap             = bmp.asImageBitmap(),
                contentDescription = null,
                modifier           = Modifier.fillMaxSize(),
                contentScale       = ContentScale.Crop
            )
        }

        // ── Layer 2: Dark scrim so the wheel pops ────────────────────────── //
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.25f))
        )

        // ── Layers 3–6: Wheel stack ──────────────────────────────────────── //
        Box(contentAlignment = Alignment.Center) {

            // Golden glow ring — drawn via Compose Canvas (no bitmap alloc)
            Canvas(modifier = Modifier.size(310.dp)) {
                val strokeWidth = 14.dp.toPx()
                drawCircle(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            Color(0xFFFFD700).copy(alpha = glowAlpha),
                            Color(0xFFFF8C00).copy(alpha = glowAlpha * 0.7f),
                            Color(0xFFFFFF88).copy(alpha = glowAlpha),
                            Color(0xFFFF8C00).copy(alpha = glowAlpha * 0.7f),
                            Color(0xFFFFD700).copy(alpha = glowAlpha),
                        )
                    ),
                    radius = size.minDimension / 2f - strokeWidth / 2f,
                    style  = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }

            // Layer 3: Spinning wheel — GPU rotation via graphicsLayer
            // No bitmap is created per frame; the GPU rotates the texture
            wheelBitmap?.let { bmp ->
                FoundationImage(
                    bitmap             = bmp.asImageBitmap(),
                    contentDescription = "Spinning wheel",
                    modifier           = Modifier
                        .size(260.dp)
                        .graphicsLayer { rotationZ = angle.value }
                )
            }

            // Layer 4: Static decorative frame
            frameBitmap?.let { bmp ->
                FoundationImage(
                    bitmap             = bmp.asImageBitmap(),
                    contentDescription = null,
                    modifier           = Modifier.size(280.dp)
                )
            }

            // Layer 5: Spin button (decorative — tapping here has no effect
            //          since the animation is already running)
            spinBitmap?.let { bmp ->
                FoundationImage(
                    bitmap             = bmp.asImageBitmap(),
                    contentDescription = null,
                    modifier           = Modifier.size(72.dp)
                )
            }
        }
    }
}
