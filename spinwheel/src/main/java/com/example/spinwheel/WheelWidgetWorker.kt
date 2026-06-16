package com.example.spinwheel

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters

private const val TAG = "WidgetSyncWorker"

class WheelWidgetWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            // Step 1 — Fetch config + download assets.
            // Repository awaits BOTH the Firebase RC call and all file downloads.
            val repository = SpinWheelRepository(appContext)
            val synced = repository.syncAssets()

            if (!synced) {
                Log.w(TAG, "Sync incomplete — widget will show cached assets (if any)")
                // Still proceed to update: show whatever is in cache (may be a prior sync)
            }

            // Step 2 — Push updated RemoteViews to every pinned widget instance.
            // Called directly here (not fire-and-forget) so the wakelock is still
            // held until the Glance render completes.
            val manager   = GlanceAppWidgetManager(appContext)
            val glanceIds = manager.getGlanceIds(SpinWheelGlanceWidget::class.java)
            glanceIds.forEach { SpinWheelGlanceWidget().update(appContext, it) }
            Log.d(TAG, "Widget updated — ${glanceIds.size} instance(s)")

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "doWork() failed: ${e.message}", e)
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val WORK_NAME = "spinwheel_widget_sync"

        /**
         * Enqueues a one-time sync. Uses [ExistingWorkPolicy.REPLACE] so rapid
         * add/remove of the widget collapses into a single request.
         * Waits for a network connection before starting.
         */
        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<WheelWidgetWorker>()
                .setConstraints(
                    Constraints.Builder()
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
