package com.csbaby.kefu.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.TimeUnit

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val syncManager: SyncManager
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Timber.i("Periodic sync worker started")
        try {
            val syncResult = syncManager.fullSync()
            if (syncResult.success) {
                Timber.i("Periodic sync completed successfully")
                Result.success()
            } else {
                Timber.w("Periodic sync completed with errors: ${syncResult.errors}")
                if (syncResult.errors.isEmpty()) {
                    Result.success()
                } else {
                    Result.retry()
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Periodic sync failed")
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "csbaby_periodic_sync"

        fun enqueue(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<SyncWorker>(
                15, TimeUnit.MINUTES,
                5, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )

            Timber.i("Periodic sync worker enqueued (15 min interval)")
        }
    }
}
