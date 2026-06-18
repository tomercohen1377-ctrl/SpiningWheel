package com.example.spinwheel.data.remote

import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

private const val TAG = "RemoteDataSourceImpl"

/**
 * OkHttp implementation of [RemoteDataSource].
 *
 * The SDK deliberately avoids Firebase Remote Config / Google Services so
 * that any RN project can consume it without a Firebase project. The
 * config JSON URL is passed in at runtime from the JS bridge via
 * [com.example.spinwheel.WidgetSyncService].
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

    override suspend fun fetchConfigJson(configUrl: String): String? = withContext(ioDispatcher) {
        try {
            val request = Request.Builder()
                .url(configUrl)
                .header("User-Agent", "Mozilla/5.0 (Android; SpinWheel)")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "Config fetch failed: HTTP ${response.code} for $configUrl")
                    return@withContext null
                }
                val body = response.body?.string()
                if (body.isNullOrBlank()) {
                    Log.w(TAG, "Config fetch returned empty body for $configUrl")
                    null
                } else {
                    Log.d(TAG, "Fetched config ✓ (${body.length} chars)")
                    body
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Config fetch failed: ${e.message}", e)
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
