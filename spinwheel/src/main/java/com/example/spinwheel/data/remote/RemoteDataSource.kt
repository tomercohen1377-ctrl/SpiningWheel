package com.example.spinwheel.data.remote

/**
 * Network-facing data source.
 *
 * Hand-implemented rather than injected by Hilt — see the migration guide for
 * the Hilt equivalent. Constructor-injected manually inside the repository.
 *
 * All operations are **suspend** functions and dispatch their blocking I/O to
 * an IO dispatcher internally, so callers can `await` them without manual
 * `withContext` plumbing.
 */
interface RemoteDataSource {

    /**
     * Fetches the `wheel_config` JSON string from Firebase Remote Config.
     *
     * @return the raw JSON, or `null` on any failure (network, missing key,
     *         Firebase not initialised).
     */
    suspend fun fetchConfigJson(): String?

    /**
     * Downloads the raw bytes of a single image URL.
     *
     * @param url HTTPS URL of the image.
     * @return the response bytes; never null on a successful response.
     * @throws java.io.IOException on non-2xx response or empty body.
     */
    suspend fun fetchImage(url: String): ByteArray
}
