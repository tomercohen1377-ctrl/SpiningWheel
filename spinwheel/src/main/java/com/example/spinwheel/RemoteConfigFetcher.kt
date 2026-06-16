package com.example.spinwheel

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.remoteconfig.remoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import kotlinx.coroutines.tasks.await

private const val TAG = "RemoteConfigFetcher"

/**
 * Firebase Remote Config key that holds the full wheel configuration JSON.
 *
 * ## Required Firebase RC value format
 *
 * Set this JSON string as the value for key `wheel_config` in the Firebase Console.
 * Asset values must be **Google Drive file IDs** (not filenames).
 * The URL for each image is built as: `host + fileId`.
 *
 * ```json
 * {
 *   "data": [{
 *     "id": "wheel_minimal",
 *     "network": {
 *       "assets": {
 *         "host": "https://lh3.googleusercontent.com/d/"
 *       }
 *     },
 *     "wheel": {
 *       "rotation": { "duration": 3000, "minimumSpins": 5, "maximumSpins": 8 },
 *       "assets": {
 *         "bg":         "<DRIVE_FILE_ID_OF_BG>",
 *         "wheel":      "<DRIVE_FILE_ID_OF_WHEEL>",
 *         "wheelFrame": "<DRIVE_FILE_ID_OF_FRAME>",
 *         "wheelSpin":  "<DRIVE_FILE_ID_OF_SPIN_BTN>"
 *       }
 *     }
 *   }],
 *   "meta": { "version": 1 }
 * }
 * ```
 *
 * Why `lh3.googleusercontent.com/d/ID`?
 * Google's CDN serves JPEG/PNG regardless of the source format (handles AVIF, WebP, etc.)
 * so BitmapFactory can decode it on every Android version without special handling.
 */
const val RC_KEY_WHEEL_CONFIG = "wheel_config"

/**
 * Fetches the `wheel_config` JSON string from Firebase Remote Config.
 *
 * Firebase initializes automatically via `FirebaseInitProvider` (a ContentProvider
 * that runs at process start), so this works without the user opening the app —
 * it is safe to call from a [WheelWidgetWorker] background worker.
 */
object RemoteConfigFetcher {

    /**
     * Fetches and activates the latest Remote Config, then returns the raw JSON
     * string stored under [RC_KEY_WHEEL_CONFIG].
     *
     * - On success  → returns the non-empty JSON string
     * - On failure  → returns `null` (network error, key missing, etc.)
     *
     * The minimum fetch interval is 1 hour (production).
     * For development: temporarily lower it to 0 in the Firebase Console
     * under Remote Config → Settings → Fetch timeout.
     */
    suspend fun fetchWheelConfigJson(): String? {
        return try {
            val rc = Firebase.remoteConfig

            // Apply settings and AWAIT completion before fetching.
            // Not awaiting caused settings to be applied after fetchAndActivate
            // was called, falling back to the default 12-hour throttle window.
            rc.setConfigSettingsAsync(
                remoteConfigSettings {
                    minimumFetchIntervalInSeconds = 3_600L   // 1 hour
                }
            ).await()

            rc.fetchAndActivate().await()

            val json = rc.getString(RC_KEY_WHEEL_CONFIG)
            if (json.isBlank()) {
                Log.w(TAG, "RC key '$RC_KEY_WHEEL_CONFIG' is empty — " +
                        "set it in the Firebase Console (Remote Config).")
                null
            } else {
                Log.d(TAG, "Fetched '$RC_KEY_WHEEL_CONFIG' ✓ (${json.length} chars)")
                json
            }
        } catch (e: Exception) {
            Log.e(TAG, "Firebase RC fetch failed: ${e.message}", e)
            null
        }
    }
}
