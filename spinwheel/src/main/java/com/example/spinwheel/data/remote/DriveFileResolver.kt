package com.example.spinwheel.data.remote

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

private const val TAG = "DriveFileResolver"

/**
 * Resolves filenames inside a **public** Google Drive folder to direct download URLs.
 *
 * This is the proven implementation revived from the pre-migration
 * `WidgetSyncService` era — the only piece of code that correctly turns
 * Drive folder data into working image URLs for this widget.
 *
 * ## Why this is needed
 *
 * The upstream `wheel_config` JSON on Drive uses this schema:
 * ```json
 * "host": "https://drive.google.com/drive/folders/FOLDER_ID",
 * "assets": { "bg": "bg.png", "wheel": "wheel.png", ... }
 * ```
 *
 * There is **no** public URL of the form `FOLDER_URL/filename.png` — Drive
 * folders don't work like a web server. To build a working download URL we
 * must resolve each `filename` to its **Drive file ID** first.
 *
 * ## How it works (no Drive API key required)
 * 1. Fetch the folder's public HTML page (`drive.google.com/drive/folders/ID`)
 * 2. Extract every embedded file ID via `data-id` and `ssk` regex patterns
 * 3. HEAD each file ID → read `Content-Disposition: filename="..."`
 * 4. Map `filename → lh3.googleusercontent.com/d/ID`
 *
 * The download URL is **lh3**, not `uc?export=download`, because lh3 serves JPEG
 * or PNG transcoded from the original format (including AVIF). `BitmapFactory`
 * can decode these directly with zero special handling.
 */
object DriveFileResolver {

    /** In-memory cache: folderUrl → (filename → downloadUrl). */
    private val cache = mutableMapOf<String, Map<String, String>>()

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    /**
     * Returns the direct download URL for [filename] in the Drive folder
     * identified by [folderUrl].
     *
     * **Must be called from a background thread** (does synchronous OkHttp calls).
     *
     * - If [folderUrl] is a Drive folder URL → scrapes, resolves, caches
     * - If [folderUrl] is not a folder URL → returns `folderUrl + filename` (CDN)
     *
     * @throws IOException if [filename] cannot be resolved in the folder.
     */
    fun resolveFolderUrl(folderUrl: String, filename: String): String {
        val folderId = extractFolderId(folderUrl)
        if (folderId == null) {
            Log.d(TAG, "Not a Drive folder URL — using host+filename for $filename")
            return folderUrl.trimEnd('/') + "/" + filename
        }

        // Check in-memory cache first
        val cachedMap = cache[folderUrl]
        if (cachedMap != null) {
            return lookupFilename(cachedMap, filename) ?: throw IOException(
                "File '$filename' not found in cached map for folder $folderId"
            )
        }

        Log.d(TAG, "Resolving files in Drive folder: $folderId")

        val fileIds = fetchFileIds(folderId)
        Log.d(TAG, "Found ${fileIds.size} file IDs in folder: $fileIds")

        if (fileIds.isEmpty()) {
            throw IOException(
                "No file IDs found in Drive folder $folderId. " +
                "Make sure the folder is shared with 'Anyone with the link'."
            )
        }

        // Build filename → URL map. lh3 is the download URL.
        val filenameToUrl = mutableMapOf<String, String>()
        for (id in fileIds) {
            val headUrl    = "https://drive.google.com/uc?export=download&id=$id"
            val lh3Url     = "https://lh3.googleusercontent.com/d/$id"
            val serverName = getFilenameFromDrive(headUrl)
            if (serverName != null) {
                filenameToUrl[serverName] = lh3Url
                Log.d(TAG, "  $id → $serverName  (download via lh3)")
            }
        }
        Log.d(TAG, "Resolved ${filenameToUrl.size} filenames: ${filenameToUrl.keys}")

        cache[folderUrl] = filenameToUrl

        return lookupFilename(filenameToUrl, filename) ?: throw IOException(
            "File '$filename' not found in Drive folder $folderId. " +
            "Available files: ${filenameToUrl.keys}."
        )
    }

    /** Drops the in-memory cache; call when the folder URL changes. */
    fun clearCache() = cache.clear()

    // ───────────────────────────────────────────────────────────────────── //
    //  Private helpers                                                       //
    // ───────────────────────────────────────────────────────────────────── //

    /**
     * Three-pass filename matching so Drive folder results align with the JSON:
     * 1. Exact (`bg.png` ↔ `bg.png`)
     * 2. Case-insensitive
     * 3. Stem (`bg.jpeg` ↔ `bg.png`) — handles extension mismatch
     */
    private fun lookupFilename(map: Map<String, String>, filename: String): String? {
        map[filename]?.let { return it }
        map.entries.firstOrNull { it.key.equals(filename, ignoreCase = true) }?.let { return it.value }
        val stem = filename.substringBeforeLast('.')
        return map.entries.firstOrNull {
            it.key.substringBeforeLast('.').equals(stem, ignoreCase = true)
        }?.value
    }

    /**
     * Extracts the folder ID from a Google Drive folder URL.
     * Handles /folders/ID and /u/N/folders/ID.
     */
    private fun extractFolderId(url: String): String? =
        Regex("""/folders/([0-9a-zA-Z_-]{20,})""").find(url)?.groupValues?.get(1)

    /**
     * Fetches the folder's public HTML page and extracts all embedded file IDs.
     * Two patterns cover all rendering states: `data-id=` for visible rows,
     * `ssk='...'` for storage-size tooltips (covers every file).
     */
    private fun fetchFileIds(folderId: String): Set<String> {
        val request = Request.Builder()
            .url("https://drive.google.com/drive/folders/$folderId")
            .header("User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36")
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "Folder page fetch failed: HTTP ${response.code}")
                    return emptySet()
                }
                val html = response.body?.string() ?: return emptySet()
                extractIdsFromHtml(html)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch folder HTML: ${e.message}", e)
            emptySet()
        }
    }

    /** Extracts Drive file IDs from folder HTML (data-id + ssk patterns). */
    internal fun extractIdsFromHtml(html: String): Set<String> {
        val ids = mutableSetOf<String>()

        // Pattern 1: data-id attribute (visible rows)
        Regex("""data-id="([0-9a-zA-Z_-]{20,45})"""")
            .findAll(html).forEach { ids.add(it.groupValues[1]) }

        // Pattern 2: ssk storage tooltip (covers every file)
        Regex("""ssk='[^:]+:[^:]+:([0-9a-zA-Z_-]{20,}?)-0-\d+'""")
            .findAll(html).forEach { ids.add(it.groupValues[1]) }

        return ids
    }

    /** HEAD request → parses filename from Content-Disposition header. */
    private fun getFilenameFromDrive(url: String): String? {
        val request = Request.Builder().url(url).head().build()
        return try {
            client.newCall(request).execute().use { response ->
                val cd = response.header("Content-Disposition") ?: return null
                Regex("""filename="([^"]+)"""").find(cd)?.groupValues?.get(1)
            }
        } catch (e: Exception) {
            Log.w(TAG, "HEAD failed for $url: ${e.message}")
            null
        }
    }
}
