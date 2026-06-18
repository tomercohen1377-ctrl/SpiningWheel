package com.example.spinwheel

/**
 * Built-in fallback asset URLs.
 *
 * The SDK ships with these so the home-screen widget is usable from the
 * very first tap — even if the user drops it on the home screen **before**
 * ever opening the RN app.
 *
 * The React Native bridge (`SpinWheelModule.syncWidgetConfiguration`) always
 * overwrites these in DataStore when it runs, so updating your CDN URLs
 * later only requires editing `App.tsx` and shipping a new app build.
 *
 * The four asset URLs below match the defaults used by the demo app's
 * `App.tsx` — change them in lockstep if you ever ship a custom theme.
 */
object SpinWheelDefaults {

    /**
     * Configurable CDN host. The widget-tier default uses Google's Drive
     * public-download endpoint with explicit file IDs — swap this when
     * promoting your own CDN.
     */
    private const val DRIVE = "https://drive.google.com/uc?export=download&id="

    /**
     * Default JSON config URL. Points to the canonical wheel_rotation
     * config — kept here only so the SDK has *something* to fetch if the
     * user taps the widget before the RN app has run.
     */
    const val CONFIG_URL: String = DRIVE + "1TCOGD961TPmtp2EQbvOj6T6wQVkZbjur"

    /** Background layer PNG. */
    const val BG_URL: String    = DRIVE + "1LQBHiIrO92sZ1lFaaqkH_yE5G7A6tK5B"

    /** Wheel layer PNG. */
    const val WHEEL_URL: String = DRIVE + "1gRxQmL7kLnxlTKRk6TKa-YaRKcf61tI9"

    /** Frame layer PNG. */
    const val FRAME_URL: String = DRIVE + "10cFF-MGK_rbEh8TnprrmS0uHBOUN7wjN"

    /** "Tap to spin" button PNG. */
    const val SPIN_URL: String  = DRIVE + "1qx0XNFz6wueMRES02D0QS27fMDfxoBAJ"

    /** Pre-built [SpinWheelAssets] snapshot using the URLs above. */
    val ASSETS: SpinWheelAssets = SpinWheelAssets(
        bgUrl    = BG_URL,
        wheelUrl = WHEEL_URL,
        frameUrl = FRAME_URL,
        spinUrl  = SPIN_URL,
    )
}
