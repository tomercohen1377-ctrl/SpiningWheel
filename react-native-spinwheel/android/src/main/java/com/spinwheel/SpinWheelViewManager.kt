package com.spinwheel

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import com.example.spinwheel.SpinWheelAssetUrls
import com.example.spinwheel.SpinWheelScreen
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.uimanager.SimpleViewManager
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.uimanager.annotations.ReactProp
import com.facebook.react.uimanager.events.RCTEventEmitter
import java.util.WeakHashMap

/**
 * React Native ViewManager for the Spin Wheel native view.
 *
 * ## JS Props
 * | Prop          | Type   | Description                                      |
 * |---------------|--------|--------------------------------------------------|
 * | `configUrl`   | string | Remote JSON config URL (spin settings)           |
 * | `assetUrls`   | object | `{ background, wheel, frame, spinButton }` URLs  |
 * | `onSpinEnd`   | event  | Fired with `{ segment: number }` after each spin |
 *
 * Both `configUrl` **and** `assetUrls` must be set before the wheel loads.
 * Registered native view name: `"SpinWheelView"`.
 */
class SpinWheelViewManager(
    private val reactContext: ReactApplicationContext
) : SimpleViewManager<ComposeView>() {

    override fun getName(): String = "SpinWheelView"

    // Per-view state (WeakHashMap avoids memory leaks when views are destroyed)
    private data class ViewState(
        val configUrl: MutableState<String> = mutableStateOf(""),
        val assetUrls: MutableState<SpinWheelAssetUrls?> = mutableStateOf(null)
    )

    private val viewStates = WeakHashMap<ComposeView, ViewState>()

    override fun createViewInstance(context: ThemedReactContext): ComposeView {
        val state = ViewState()
        return ComposeView(context).apply {
            viewStates[this] = state
            setContent {
                val configUrl by state.configUrl
                val assets    by state.assetUrls
                if (configUrl.isNotBlank() && assets != null) {
                    SpinWheelScreen(
                        configUrl = configUrl,
                        assetUrls = assets!!,
                        modifier  = Modifier.fillMaxSize()
                    ) { segmentIndex ->
                        emitSpinEndEvent(this@apply, context, segmentIndex)
                    }
                }
            }
        }
    }

    @ReactProp(name = "configUrl")
    fun setConfigUrl(view: ComposeView, url: String?) {
        viewStates[view]?.configUrl?.value = url ?: ""
    }

    /**
     * Receives an object with keys `background`, `wheel`, `frame`, `spinButton`.
     * All values must be HTTPS URLs (Google Drive direct-download links work).
     *
     * ```js
     * assetUrls={{
     *   background: "https://drive.google.com/uc?export=download&id=BG_ID",
     *   wheel:      "https://drive.google.com/uc?export=download&id=WHEEL_ID",
     *   frame:      "https://drive.google.com/uc?export=download&id=FRAME_ID",
     *   spinButton: "https://drive.google.com/uc?export=download&id=SPIN_ID",
     * }}
     * ```
     */
    @ReactProp(name = "assetUrls")
    fun setAssetUrls(view: ComposeView, map: ReadableMap?) {
        if (map == null) return
        viewStates[view]?.assetUrls?.value = SpinWheelAssetUrls(
            background = map.getString("background") ?: return,
            wheel      = map.getString("wheel")      ?: return,
            frame      = map.getString("frame")      ?: return,
            spinButton = map.getString("spinButton") ?: return
        )
    }

    // ------------------------------------------------------------------ //
    //  Event emission                                                      //
    // ------------------------------------------------------------------ //

    override fun getExportedCustomDirectEventTypeConstants(): Map<String, Any> =
        mapOf("onSpinEnd" to mapOf("registrationName" to "onSpinEnd"))

    private fun emitSpinEndEvent(
        view: ComposeView,
        context: ThemedReactContext,
        segment: Int
    ) {
        context
            .getJSModule(RCTEventEmitter::class.java)
            ?.receiveEvent(
                view.id,
                "onSpinEnd",
                Arguments.createMap().apply { putInt("segment", segment) }
            )
    }
}
