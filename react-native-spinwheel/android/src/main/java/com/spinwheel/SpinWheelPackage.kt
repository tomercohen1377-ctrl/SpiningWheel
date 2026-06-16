package com.spinwheel

import com.facebook.react.ReactPackage
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.uimanager.ViewManager

/**
 * ReactPackage that registers [SpinWheelModule] with the React Native bridge.
 *
 * Register in your host app's `MainApplication`:
 * ```kotlin
 * override fun getPackages(): List<ReactPackage> = listOf(SpinWheelPackage())
 * ```
 */
class SpinWheelPackage : ReactPackage {
    override fun createNativeModules(reactContext: ReactApplicationContext): List<NativeModule> =
        listOf(SpinWheelModule(reactContext))

    // No custom views — the widget lives in the Android home-screen process
    override fun createViewManagers(reactContext: ReactApplicationContext): List<ViewManager<*, *>> =
        emptyList()
}
