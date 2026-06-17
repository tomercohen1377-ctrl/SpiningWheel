package com.example.spinwheel.data.remote

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.remoteconfig.remoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

private const val TAG = "RemoteDataSourceImpl"

/** Firebase Remote Config key holding the wheel configuration JSON. */
const val RC_KEY_WHEEL_CONFIG = "wheel_config"

/**
 * OkHttp + Firebase RC implementation of [RemoteDataSource].
 *
 * For Hilt, inject the Firebase + OkHttp dependencies via `@Singleton` and
 * mark the constructor `@Inject`. Manual wiring here uses a default
 * constructor and the Firebase bootstrap automatically.
 */
class RemoteDataSourceImpl(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : RemoteDataSource {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    override suspend fun fetchConfigJson(): String? = withContext(ioDispatcher) {
        try {
            val rc = Firebase.remoteConfig
            // setConfigSettingsAsync + fetchAndActivate are Firebase Tasks;
            // kotlinx-coroutines-play-services' .await() is dispatcher-safe,
            // but we wrap the whole block in IO so no blocking work leaks to
            // the caller's dispatcher (which may be Default or even Main).
            rc.setConfigSettingsAsync(
                remoteConfigSettings { minimumFetchIntervalInSeconds = 3_600L }
            ).await()
            rc.fetchAndActivate().await()
            val json = rc.getString(RC_KEY_WHEEL_CONFIG)
            if (json.isBlank()) {
                Log.w(TAG, "RC key '$RC_KEY_WHEEL_CONFIG' is empty — set it in Firebase Console")
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

    override suspend fun fetchImage(url: String): ByteArray = withContext(ioDispatcher) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Android; SpinWheel)")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code} for $url")
            }
            val bytes = response.body?.bytes()
                ?: throw IOException("Empty body for $url")
            if (bytes.size < 100) {
                throw IOException("Response too small (${bytes.size} B) for $url")
            }
            Log.d(TAG, "Downloaded $url (${bytes.size} bytes)")
            bytes
        }
    }
}
