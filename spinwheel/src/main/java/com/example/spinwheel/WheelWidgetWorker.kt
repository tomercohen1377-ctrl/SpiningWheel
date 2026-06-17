package com.example.spinwheel

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.glance.appwidget.AppWidgetId
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.spinwheel.di.SpinWheelGraph

private const val TAG = "SpinWheelWidgetWorker"

/**
 * Background worker that drives the full widget sync pipeline.
 *
 * ## Pipeline (every step is awaited inside doWork — no fire-and-forget)
 * ```
 * 1. GetWheelConfigJsonUseCase        → Firebase RC + persists JSON
 * 2. DownloadWheelImagesUseCase        → 4 parallel OkHttp downloads
 * 3. GlanceAppWidgetManager.update()   → widget redraws from local disk
 * ```
 *
 * Components obtained from [SpinWheelGraph.get] which returns the **same
 * singleton** across calls — Hilt would normally do this for us, but compatible
 * Hilt + AGP 9.x is on hold in this environment.
 */
class WheelWidgetWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        Log.d(TAG, "doWork() start (attempt ${runAttemptCount + 1})")
        val ctx = applicationContext

        return try {
            val graph = SpinWheelGraph.get(ctx)

            // ── Step 1: fetch the config JSON + persist ─────────────────────── //
            val config = graph.getWheelConfigJsonUseCase()
            if (config == null) {
                Log.w(TAG, "Config unavailable — retrying")
                return if (runAttemptCount < 2) Result.retry() else Result.failure()
            }
            Log.d(
                TAG, "Config received: ${config.assetIds.size} assets, " +
                        "spinDuration=${config.spinDurationMs}"
            )

            // ── Step 2: download all 4 images in parallel ──────────────────── //
            val ok = graph.downloadWheelImagesUseCase(config)
            if (!ok) {
                Log.w(TAG, "Some images failed to download — retrying")
                return if (runAttemptCount < 2) Result.retry() else Result.failure()
            }
            Log.d(TAG, "All images downloaded ✓")

            // ── Step 3: push the widget update ────────────────────────────── //
            //
            // WHY we bypass GlanceAppWidgetManager.getGlanceIds:
            // getGlanceIds reads from Glance's internal DataStore which is
            // populated by GlanceAppWidgetReceiver.onUpdate's goAsync block via
            // updateManager(). WorkManager can wake and run the worker BEFORE
            // that async bootstrap completes → getGlanceIds returns empty →
            // no update() call → widget stays on "Loading" forever.
            //
            // Fix: query the system AppWidgetManager directly. It knows about
            // every pinned widget ID the moment the launcher places it — no
            // async bootstrap required. Then construct AppWidgetId (the Glance
            // wrapper) manually and call update() which triggers provideGlance.
            val awm = AppWidgetManager.getInstance(applicationContext)
            val receiverComponent = ComponentName(
                applicationContext,
                SpinWheelWidgetReceiver::class.java,
            )
            val rawIds = awm.getAppWidgetIds(receiverComponent)
            Log.d(TAG, "AppWidgetManager.getAppWidgetIds = ${rawIds.toList()}")

            if (rawIds.isEmpty()) {
                Log.w(TAG, "No widget IDs found — widget not pinned yet?")
            }

            rawIds.forEach { rawId ->
                val glanceId = AppWidgetId(rawId)
                Log.d(TAG, "Calling update for id=$rawId")
                SpinWheelGlanceWidget().update(applicationContext, glanceId)
            }

            Log.d(TAG, "Widget update loop done")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "doWork() failed: ${e.message}", e)
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val WORK_NAME = "spinwheel_widget_sync"

        /**
         * Enqueue a one-time sync. Uses REPLACE policy so rapid add/remove of
         * the widget collapses into a single request. Waits for connectivity.
         */
        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<WheelWidgetWorker>()
                .setConstraints(
                    Constraints.Builder()
                        // Don't require connectivity — let the worker just bail
                        // if there's no network (returns retry). This avoids the
                        // case where the worker stays queued indefinitely.
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
            Log.d(TAG, "Sync work enqueued")
        }
    }
}
