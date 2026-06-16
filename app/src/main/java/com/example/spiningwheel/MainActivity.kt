package com.example.spiningwheel

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import com.example.spinwheel.SpinWheelRepository
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
 * Provides a "Sync Now" button that calls [SpinWheelRepository.syncAssets],
 * then refreshes the Glance widget. The widget is **fully self-contained** and
 * runs the same pipeline automatically via [SpinWheelWidgetReceiver] every
 * 12 hours and on first pin — no app launch required.
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

        // Auto-sync on launch — skips already-cached files
        triggerSync(forceRefresh = false)
    }

    // ─────────────────────────────────────────────────────────────────────── //

    private fun triggerSync(forceRefresh: Boolean = false) {
        progressBar.visibility = View.VISIBLE
        syncButton.isEnabled   = false
        statusText.text        = "Fetching config from Firebase Remote Config…"

        scope.launch {
            if (forceRefresh) {
                withContext(Dispatchers.IO) {
                    WidgetSyncService(applicationContext).clearCache()
                }
            }

            statusText.text = "Downloading assets from Google Drive…"

            // Repository awaits both the Firebase RC fetch AND all downloads
            val success = withContext(Dispatchers.IO) {
                SpinWheelRepository(applicationContext).syncAssets()
            }

            progressBar.visibility = View.GONE
            syncButton.isEnabled   = true

            if (success) {
                updateStatusText()
                SpinWheelWidgetReceiver.requestUpdate(applicationContext)
                Toast.makeText(this@MainActivity, "Widget synced ✓", Toast.LENGTH_SHORT).show()
            } else {
                statusText.text = "Sync failed — check your internet connection\n" +
                        "or verify the Firebase RC 'wheel_config' key is set."
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
