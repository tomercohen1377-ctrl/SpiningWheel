package com.example.spinwheel

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.glance.appwidget.AppWidgetId
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.spinwheel.di.SpinWheelGraph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

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

            // ── Step 3: push the widget update synchronously ──────────────── //
            //
            // WHY NOT sendBroadcast:
            //   sendBroadcast() is fire-and-forget. The worker returns
            //   Result.success() immediately after, WorkManager releases the
            //   wakelock, and the process can die before the broadcast is
            //   delivered to the receiver. provideGlance never gets called.
            //
            // WHY NOT GlanceAppWidgetManager.getGlanceIds():
            //   Glance's DataStore is populated asynchronously by
            //   GlanceAppWidgetReceiver.super.onUpdate() via goAsync. On first
            //   widget add, the worker can start before that async block writes
            //   to the DataStore → getGlanceIds returns empty → no update.
            //
            // THE FIX:
            //   Read widget IDs from the system AppWidgetManager (always
            //   accurate, no async bootstrap required). Then call
            //   SpinWheelGlanceWidget().update(context, AppWidgetId(rawId))
            //   as a direct suspend call inside doWork(). This is synchronous —
            //   the wakelock is held until provideGlance() finishes executing.
            val awm = AppWidgetManager.getInstance(applicationContext)
            val receiverComponent =
                ComponentName(applicationContext, SpinWheelWidgetReceiver::class.java)
            val widgetIds = awm.getAppWidgetIds(receiverComponent)

            Log.d(TAG, "AppWidgetManager IDs = ${widgetIds.toList()}")

            if (widgetIds.isEmpty()) {
                Log.w(TAG, "No widget instances found — widget may not have been added yet")
            }

            // ── Why withContext(Main) + delay:
            //   Glance's update() uses a frame dispatcher (Choreographer-based)
            //   internally. The actual Compose recomposition + RemoteViews push
            //   is posted to the MAIN thread message queue. If the worker returns
            //   immediately after update(), WorkManager releases the wakelock and
            //   the process can die before the main thread picks up that work.
            //
            //   Switching to Main and yielding lets any pending main-thread work
            //   (including Glance's frame) run before we exit. The extra delay
            //   covers any subsequent vsync-frame scheduling.
            withContext(Dispatchers.Main) {
                widgetIds.forEach { rawId ->
                    Log.d(TAG, "Calling update() for widget ID $rawId")
                    SpinWheelGlanceWidget().update(applicationContext, AppWidgetId(rawId))
                    Log.d(TAG, "update() returned for $rawId")
                }
                // Give Glance's frame dispatcher time to push RemoteViews to the
                // launcher (one vsync = 16 ms; we wait 4 s to be safe).
                Log.d(TAG, "Waiting for Glance to commit RemoteViews…")
                delay(4_000L)
                Log.d(TAG, "Wait complete — wakelock release imminent")
            }

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
