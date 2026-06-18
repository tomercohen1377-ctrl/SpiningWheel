package com.example.spinwheel.data.remote

/**
 * Network-facing data source.
 *
 * All operations are **suspend** functions and dispatch their blocking I/O
 * to an IO dispatcher internally, so callers can `await` them without
 * manual `withContext` plumbing.
 */
interface RemoteDataSource {

    /**
     * Fetches the `wheel_config` JSON string from the given HTTPS URL.
     * The SDK does not depend on Firebase Remote Config — the URL is
     * supplied by the host app via `SpinWheelModule.syncWidgetConfiguration(...)`.
     *
     * @return the raw JSON, or `null` on any failure (network, malformed
     *         response, missing context).
     */
    suspend fun fetchConfigJson(configUrl: String): String?

    /**
     * Downloads the raw bytes of a single image URL.
     *
     * @param url HTTPS URL of the image.
     * @return the response bytes; never null on a successful response.
     * @throws java.io.IOException on non-2xx response or empty body.
     */
    suspend fun fetchImage(url: String): ByteArray
}
