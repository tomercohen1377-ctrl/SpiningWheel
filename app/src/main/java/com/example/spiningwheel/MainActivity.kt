package com.example.spiningwheel

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import com.example.spinwheel.DriveFileResolver
import com.example.spinwheel.RemoteConfigFetcher
import com.example.spinwheel.SpinWheelWidgetReceiver
import com.example.spinwheel.WidgetSyncService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Host activity — management console for the Spin Wheel home-screen widget.
 *
 * The activity provides a "Sync Now" button that:
 *  1. Fetches the `wheel_config` JSON from Firebase Remote Config
 *  2. Parses the JSON → derives Drive download URLs (host + file IDs)
 *  3. Downloads and caches the 4 image assets via [WidgetSyncService]
 *  4. Refreshes the Glance widget on the home screen
 *
 * NOTE: The widget is **fully self-contained** — it performs the same pipeline
 * automatically (via [SpinWheelWidgetReceiver.onUpdate] / [SpinWheelWidgetReceiver.onEnabled])
 * every 12 hours and when first pinned, **without this activity needing to be open**.
 *
 * The Firebase Console Remote Config key is:
 *   key   → "wheel_config"
 *   value → the full wheel config JSON string (see [RemoteConfigFetcher] KDoc)
 */
class MainActivity : Activity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private lateinit var statusText:  TextView
    private lateinit var syncButton:  Button
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText  = findViewById(R.id.tv_status)
        syncButton  = findViewById(R.id.btn_sync)
        progressBar = findViewById(R.id.progress_bar)

        updateStatusText()
        syncButton.setOnClickListener { triggerSync(forceRefresh = true) }

        // Auto-sync on every launch. forceRefresh=false skips files already cached.
        triggerSync(forceRefresh = false)
    }

    // ─────────────────────────────────────────────────────────────────────── //
    //  Sync pipeline                                                          //
    // ─────────────────────────────────────────────────────────────────────── //

    private fun triggerSync(forceRefresh: Boolean = false) {
        progressBar.visibility = View.VISIBLE
        syncButton.isEnabled   = false
        statusText.text        = "Fetching config from Firebase Remote Config…"

        scope.launch {
            if (forceRefresh) {
                // Clear all cached files + resolved URL mapping so everything
                // is re-downloaded fresh (important after a decode bug fix)
                withContext(Dispatchers.IO) {
                    WidgetSyncService(applicationContext).clearCache()
                    DriveFileResolver.clearCache()
                }
            }

            // Step 1 — Fetch wheel_config JSON from Firebase RC
            val configJson = withContext(Dispatchers.IO) {
                RemoteConfigFetcher.fetchWheelConfigJson()
            }

            if (configJson == null) {
                progressBar.visibility = View.GONE
                syncButton.isEnabled   = true
                statusText.text        = "Firebase Remote Config unavailable.\n" +
                        "Check that the 'wheel_config' key is set in the Firebase Console."
                Toast.makeText(
                    this@MainActivity,
                    "Remote Config unavailable",
                    Toast.LENGTH_LONG
                ).show()
                return@launch
            }

            statusText.text = "Downloading assets from Google Drive…"

            // Step 2 — Parse JSON → derive URLs → download + cache assets
            val success = withContext(Dispatchers.IO) {
                WidgetSyncService(applicationContext).fetchAndCacheFromJson(configJson)
            }

            progressBar.visibility = View.GONE
            syncButton.isEnabled   = true

            if (success) {
                updateStatusText()
                // Step 3 — Refresh the Glance widget with the new bitmaps
                SpinWheelWidgetReceiver.requestUpdate(applicationContext)
                Toast.makeText(this@MainActivity, "Widget synced ✓", Toast.LENGTH_SHORT).show()
            } else {
                statusText.text = "Sync failed — check your internet connection."
                Toast.makeText(this@MainActivity, "Sync failed", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun updateStatusText() {
        val ts = WidgetSyncService(this).getLastFetchTime()
        statusText.text = if (ts == 0L) {
            "Not synced yet."
        } else {
            val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            "Last sync: ${fmt.format(Date(ts))}"
        }
    }
}
