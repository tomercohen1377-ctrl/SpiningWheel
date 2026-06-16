package com.example.spinwheel

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

private const val TAG = "DriveFileResolver"

/**
 * Resolves filenames inside a **public** Google Drive folder to direct download URLs.
 *
 * ## Why this is needed
 *
 * The `wheel_config` JSON stores:
 * ```json
 * "network": { "assets": { "host": "https://drive.google.com/drive/u/0/folders/FOLDER_ID" } },
 * "wheel":   { "assets": { "bg": "bg.jpeg", "wheel": "wheel.png", ... } }
 * ```
 *
 * There is no public URL of the form `FOLDER_URL/filename.png` — Drive folders
 * don't work like a web server. To construct a working download URL we need the
 * **file ID** of each file.
 *
 * ## How it works (no API key required)
 *
 * 1. Fetch the folder's public HTML page (`drive.google.com/drive/folders/ID`)
 * 2. Extract all embedded Drive file IDs using `data-id` and `ssk` attribute patterns
 * 3. Send a HEAD request for each candidate ID to get the `Content-Disposition: filename` header
 * 4. Build a `filename → downloadUrl` mapping
 *
 * Results are in-memory cached per folder URL to avoid repeating the resolution
 * on every widget refresh (the [WidgetSyncService] provides disk-level caching).
 *
 * ## Filename matching
 *
 * Matching is done in order:
 *  1. Exact match  (`bg.jpeg` → `bg.jpeg`)
 *  2. Case-insensitive match
 *  3. **Stem match** (`bg.jpeg` → `bg.png`) — handles schema/extension mismatches
 *
 * The stem match is needed because the actual file on Drive is `bg.png` but the
 * JSON config specifies `bg.jpeg`.
 */
object DriveFileResolver {

    /** In-memory cache: folderUrl → (filename → downloadUrl) */
    private val cache = mutableMapOf<String, Map<String, String>>()

    /**
     * Returns the direct download URL for [filename] inside the Drive folder
     * identified by [folderUrl].
     *
     * Falls back to `folderUrl + filename` when [folderUrl] is not a Drive
     * folder URL, so this method is safe to call for any host.
     *
     * Must be called from a background thread (does synchronous OkHttp calls).
     *
     * @throws IOException if the folder page cannot be fetched or [filename]
     *                     cannot be resolved to a file ID.
     */
    fun resolve(client: OkHttpClient, folderUrl: String, filename: String): String {
        val folderId = extractFolderId(folderUrl)
        if (folderId == null) {
            // Not a Drive folder URL — use host + filename directly (CDN / custom server)
            Log.d(TAG, "Not a Drive folder URL — using host+filename for $filename")
            return folderUrl.trimEnd('/') + "/" + filename
        }

        // Check in-memory cache
        val cachedMap = cache[folderUrl]
        if (cachedMap != null) {
            Log.d(TAG, "Cache hit for folder $folderId")
            return lookupFilename(cachedMap, filename)
                ?: throw IOException("File '$filename' not found in cached map for folder $folderId")
        }

        Log.d(TAG, "Resolving files in Drive folder: $folderId")

        // Step 1 — fetch folder HTML and extract all file IDs
        val fileIds = fetchFileIds(client, folderId)
        Log.d(TAG, "Found ${fileIds.size} file IDs in folder: $fileIds")

        if (fileIds.isEmpty()) {
            throw IOException(
                "No file IDs found in Drive folder $folderId. " +
                "Make sure the folder is shared with 'Anyone with the link'."
            )
        }

        // Step 2 — HEAD each file ID to get its Content-Disposition filename.
        //
        // IMPORTANT: We use two different URLs per file:
        //  • headUrl  = uc?export=download  — only for the HEAD request to get the filename
        //  • lh3Url   = lh3.googleusercontent.com/d/ID  — the URL we store and use for downloads
        //
        // WHY lh3?  The uc?export=download URL serves the ORIGINAL file format, which can be
        // AVIF (the bg image is AVIF).  Android's ImageDecoder fails to decode this specific AVIF
        // with "getPixels failed with error invalid input".
        // lh3.googleusercontent.com/d/ID is Google's image CDN — it transparently converts to
        // JPEG or PNG regardless of the source format, so BitmapFactory can decode it on any
        // Android version with zero special handling.
        val filenameToUrl = mutableMapOf<String, String>()
        for (id in fileIds) {
            val headUrl = "https://drive.google.com/uc?export=download&id=$id"
            val lh3Url  = "https://lh3.googleusercontent.com/d/$id"
            val serverFilename = getFilenameFromDrive(client, headUrl)
            if (serverFilename != null) {
                filenameToUrl[serverFilename] = lh3Url   // ← lh3 URL, not uc URL
                Log.d(TAG, "  $id → $serverFilename  (download via lh3)")
            }
        }

        Log.d(TAG, "Resolved ${filenameToUrl.size} filenames: ${filenameToUrl.keys}")

        // Cache result
        cache[folderUrl] = filenameToUrl

        return lookupFilename(filenameToUrl, filename)
            ?: throw IOException(
                "File '$filename' not found in Drive folder $folderId. " +
                "Available files: ${filenameToUrl.keys}. " +
                "Check filename spelling in the Firebase RC JSON."
            )
    }

    /** Clears the in-memory cache (call when the folder URL changes). */
    fun clearCache() {
        cache.clear()
    }

    // ─────────────────────────────────────────────────────────────────────── //
    //  Private helpers                                                        //
    // ─────────────────────────────────────────────────────────────────────── //

    /**
     * Looks up [filename] in [map] using three fallback strategies:
     * 1. Exact match
     * 2. Case-insensitive match
     * 3. Stem match (`bg.jpeg` matches `bg.png`)
     */
    private fun lookupFilename(map: Map<String, String>, filename: String): String? {
        // 1. Exact
        map[filename]?.let { return it }

        // 2. Case-insensitive
        map.entries.firstOrNull { it.key.equals(filename, ignoreCase = true) }?.let { return it.value }

        // 3. Stem match — handles bg.jpeg ↔ bg.png discrepancies
        val stem = filename.substringBeforeLast('.')
        map.entries.firstOrNull { entry ->
            entry.key.substringBeforeLast('.').equals(stem, ignoreCase = true)
        }?.let { return it.value }

        return null
    }

    /**
     * Extracts the folder ID from a Google Drive folder URL.
     *
     * Handles:
     * - `https://drive.google.com/drive/folders/FOLDER_ID`
     * - `https://drive.google.com/drive/u/0/folders/FOLDER_ID`
     * - `https://drive.google.com/drive/u/1/folders/FOLDER_ID`
     *
     * Returns null for non-Drive-folder URLs.
     */
    private fun extractFolderId(url: String): String? {
        return Regex("""/folders/([0-9a-zA-Z_-]{20,})""").find(url)?.groupValues?.get(1)
    }

    /**
     * Fetches the Drive folder's HTML page and extracts all embedded file IDs.
     *
     * Two patterns are searched:
     * - `data-id="FILE_ID"` — appears on visible file rows
     * - `ssk='N:TAG:FILE_ID-0-N'` — appears in storage-size tooltips (covers all files)
     *
     * The SSK pattern is the most comprehensive — it appears for every file in the
     * folder regardless of scroll position or rendering state.
     */
    private fun fetchFileIds(client: OkHttpClient, folderId: String): Set<String> {
        val request = Request.Builder()
            .url("https://drive.google.com/drive/folders/$folderId")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
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

    /**
     * Extracts Drive file IDs from folder HTML using two regex patterns.
     *
     * Drive file IDs are 25–45 characters consisting of `[0-9a-zA-Z_-]`.
     * The `0` digit must be included (earlier bug: using `[1-9]` missed IDs
     * like `1UVcO433...` that contain `0`).
     */
    internal fun extractIdsFromHtml(html: String): Set<String> {
        val ids = mutableSetOf<String>()

        // Pattern 1: data-id attribute (standard row rendering)
        Regex("""data-id="([0-9a-zA-Z_-]{20,45})"""")
            .findAll(html)
            .forEach { ids.add(it.groupValues[1]) }

        // Pattern 2: ssk storage tooltip attribute (most comprehensive — covers all files)
        // Format: ssk='N:TAG:FILE_ID-0-N'  where FILE_ID may contain '-'
        // Use non-greedy +? to stop before the -0-N trailer
        Regex("""ssk='[^:]+:[^:]+:([0-9a-zA-Z_-]{20,}?)-0-\d+'""")
            .findAll(html)
            .forEach { ids.add(it.groupValues[1]) }

        return ids
    }

    /**
     * Sends a HEAD request for [downloadUrl] and parses the filename from the
     * `Content-Disposition: attachment; filename="..."` response header.
     *
     * Returns null if the header is absent or unparseable.
     */
    private fun getFilenameFromDrive(client: OkHttpClient, downloadUrl: String): String? {
        val request = Request.Builder()
            .url(downloadUrl)
            .head()
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                val cd = response.header("Content-Disposition") ?: return null
                Regex("""filename="([^"]+)"""").find(cd)?.groupValues?.get(1)
            }
        } catch (e: Exception) {
            Log.w(TAG, "HEAD failed for $downloadUrl: ${e.message}")
            null
        }
    }
}
