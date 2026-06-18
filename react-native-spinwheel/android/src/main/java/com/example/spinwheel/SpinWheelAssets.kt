package com.example.spinwheel

/**
 * Convenience data carrier passed from the JS bridge into
 * [com.example.spinwheel.WidgetSyncService].
 *
 * Each URL points directly to the image asset the widget's wheel layer
 * needs. URLs may be:
 * - the `lh3.googleusercontent.com/d/<id>` URL produced by
 *   [com.example.spinwheel.data.remote.DriveFileResolver], or
 * - a pre-resolved CDN URL of the form `<host>/bg.png`.
 */
data class SpinWheelAssets(
    val bgUrl: String,
    val wheelUrl: String,
    val frameUrl: String,
    val spinUrl: String,
)
