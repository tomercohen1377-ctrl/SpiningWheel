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
 * Expected value format (set this in the Firebase Console):
 * ```json
 * {
 *   "data": [{
 *     "id": "wheel_minimal",
 *     "network": { "assets": { "host": "https://drive.google.com/uc?export=download&id=" } },
 *     "wheel": {
 *       "rotation": { "duration": 3000, "minimumSpins": 3, "maximumSpins": 5 },
 *       "assets": {
 *         "bg":         "<DRIVE_FILE_ID>",
 *         "wheel":      "<DRIVE_FILE_ID>",
 *         "wheelFrame": "<DRIVE_FILE_ID>",
 *         "wheelSpin":  "<DRIVE_FILE_ID>"
 *       }
 *     }
 *   }],
 *   "meta": { "version": 1 }
 * }
 * ```
 *
 * Full asset URL is built as: `host + fileId`
 */
const val RC_KEY_WHEEL_CONFIG = "wheel_config"

/**
 * Fetches the `wheel_config` JSON string from Firebase Remote Config.
 *
 * ## Why this works without the app being opened
 *
 * Firebase initializes itself via [com.google.firebase.provider.FirebaseInitProvider],
 * a [android.content.ContentProvider] declared in the merged manifest.
 * ContentProviders are started when the app **process** starts — even when a
 * [android.content.BroadcastReceiver] (e.g. the widget's [SpinWheelWidgetReceiver])
 * triggers the process creation. So [Firebase.remoteConfig] is available here
 * without the user ever opening the host activity.
 */
object RemoteConfigFetcher {

    /**
     * Lazy-initialized Remote Config instance.
     *
     * Minimum fetch interval is 1 hour in production.
     * Set to 0 during development: `minimumFetchIntervalInSeconds = 0L`.
     */
    private val rc by lazy {
        Firebase.remoteConfig.also { r ->
            r.setConfigSettingsAsync(
                remoteConfigSettings {
                    minimumFetchIntervalInSeconds = 3_600L   // 1 hour in production
                }
            )
        }
    }

    /**
     * Fetches and activates the latest Remote Config, then returns the raw JSON
     * string stored under [RC_KEY_WHEEL_CONFIG].
     *
     * - On success → returns the JSON string (non-empty)
     * - On failure (no network, Firebase not initialized, key missing) → returns `null`
     *
     * Must be called from a coroutine (suspend function).
     */
    suspend fun fetchWheelConfigJson(): String? {
        return try {
            rc.fetchAndActivate().await()
            val json = rc.getString(RC_KEY_WHEEL_CONFIG)
            if (json.isBlank()) {
                Log.w(TAG, "RC key '$RC_KEY_WHEEL_CONFIG' is empty — " +
                        "make sure it is configured in the Firebase Console.")
                null
            } else {
                Log.d(TAG, "Fetched '$RC_KEY_WHEEL_CONFIG' from Firebase RC ✓ " +
                        "(${json.length} chars)")
                json
            }
        } catch (e: Exception) {
            Log.e(TAG, "Firebase RC fetch failed: ${e.message}", e)
            null
        }
    }
}
