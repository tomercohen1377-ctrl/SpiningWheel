package com.spinwheel

import com.facebook.react.ReactPackage
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.uimanager.ViewManager

/**
 * ReactPackage that registers [SpinWheelModule] with the React Native bridge.
 *
 * The home-screen `SpinWheelWidgetReceiver` is registered automatically via
 * the library's `AndroidManifest.xml` (manifest merger pulls it into the
 * host app's merged manifest at app-build time). No extra host-app code
 * needed.
 *
 * Register in your host app's `MainApplication`:
 * ```kotlin
 * override fun getPackages(): List<ReactPackage> = listOf(SpinWheelPackage())
 * ```
 */
class SpinWheelPackage : ReactPackage {
    override fun createNativeModules(reactContext: ReactApplicationContext): List<NativeModule> =
        listOf(SpinWheelModule(reactContext))

    // The widget is a remote `AppWidgetProvider` — no React-side views.
    override fun createViewManagers(reactContext: ReactApplicationContext): List<ViewManager<*, *>> =
        emptyList()
}
