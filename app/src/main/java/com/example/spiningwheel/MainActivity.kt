package com.example.spiningwheel

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import com.example.spinwheel.WheelWidgetWorker
import com.example.spinwheel.di.SpinWheelGraph
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
 * Calls the use cases it needs via the singleton [SpinWheelGraph]:
 * 1. [GetWheelConfigJsonUseCase] — fetch + persist JSON
 * 2. [DownloadWheelImagesUseCase] — 4 parallel image downloads
 *
 * After success it enqueues the [WheelWidgetWorker] so the widget redraws
 * with the new data immediately (the worker also runs on its own when the
 * widget process starts, so this is redundant but harmless).
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

        // Auto-sync on launch — skips files already cached
        triggerSync(forceRefresh = false)
    }

    private fun triggerSync(forceRefresh: Boolean = false) {
        progressBar.visibility = View.VISIBLE
        syncButton.isEnabled   = false
        statusText.text        = "Fetching config from Firebase Remote Config…"

        scope.launch {
            val graph = SpinWheelGraph.get(applicationContext)

            if (forceRefresh) {
                withContext(Dispatchers.IO) { graph.repository.clear() }
            }

            statusText.text = "Downloading assets from Google Drive…"

            // Step 1 — Config JSON
            val config = graph.getWheelConfigJsonUseCase()
            if (config == null) {
                progressBar.visibility = View.GONE
                syncButton.isEnabled   = true
                statusText.text        = "Firebase RC unavailable.\n" +
                        "Verify the 'wheel_config' key is set in the Firebase Console."
                Toast.makeText(this@MainActivity, "Remote Config unavailable", Toast.LENGTH_LONG).show()
                return@launch
            }

            // Step 2 — Image downloads (4 in parallel, awaited by the use case)
            val ok = withContext(Dispatchers.IO) {
                graph.downloadWheelImagesUseCase(config)
            }

            progressBar.visibility = View.GONE
            syncButton.isEnabled   = true

            if (ok) {
                updateStatusText()
                WheelWidgetWorker.enqueue(applicationContext)
                Toast.makeText(this@MainActivity, "Widget synced ✓", Toast.LENGTH_SHORT).show()
            } else {
                statusText.text = "Sync failed — check your internet connection."
                Toast.makeText(this@MainActivity, "Sync failed", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun updateStatusText() {
        val graph   = SpinWheelGraph.get(applicationContext)
        val ts      = runCatching {
            kotlinx.coroutines.runBlocking { graph.repository.getLastSync() }
        }.getOrDefault(0L)

        statusText.text = if (ts == 0L) {
            "Not synced yet."
        } else {
            val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            "Last sync: ${fmt.format(Date(ts))}"
        }
    }
}
