package com.csbaby.kefu.data.remote.backend

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.csbaby.kefu.data.local.AuthManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * 后端同步 Worker
 * 定期执行：心跳保活 + 从后端拉取最新数据
 */
@HiltWorker
class BackendSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val syncManager: BackendSyncManager,
    private val ruleBackendSync: RuleBackendSync,
    private val modelBackendSync: ModelBackendSync,
    private val historyBackendSync: HistoryBackendSync,
    private val authManager: AuthManager
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Timber.d("BackendSyncWorker: starting periodic sync")

        // 0. 检查登录状态
        if (!authManager.isLoggedIn) {
            Timber.w("BackendSyncWorker: user not logged in, skipping sync")
            return Result.retry()
        }

        // 1. 确保设备已注册
        if (!syncManager.registerIfNeeded()) {
            Timber.w("BackendSyncWorker: device not registered, skipping sync")
            return Result.retry()
        }

        // 2. 发送心跳
        val heartbeatOk = syncManager.heartbeat()
        Timber.d("BackendSyncWorker: heartbeat=${heartbeatOk}")

        // 3. 从后端拉取最新数据（不阻塞，各自独立）
        try {
            val rulesResult = ruleBackendSync.pullFromBackend()
            rulesResult.onSuccess { count ->
                if (count > 0) Timber.i("BackendSyncWorker: pulled $count rules from backend")
            }
        } catch (e: Exception) {
            Timber.w(e, "BackendSyncWorker: failed to pull rules")
        }

        try {
            val modelsResult = modelBackendSync.pullFromBackend()
            modelsResult.onSuccess { count ->
                if (count > 0) Timber.i("BackendSyncWorker: pulled $count models from backend")
            }
        } catch (e: Exception) {
            Timber.w(e, "BackendSyncWorker: failed to pull models")
        }

        try {
            val historyResult = historyBackendSync.pullFromBackend(limit = 50)
            historyResult.onSuccess { count ->
                if (count > 0) Timber.i("BackendSyncWorker: pulled $count history records from backend")
            }
        } catch (e: Exception) {
            Timber.w(e, "BackendSyncWorker: failed to pull history")
        }

        Timber.d("BackendSyncWorker: sync complete")
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "backend_periodic_sync"

        /** 注册定期同步任务（每 15 分钟） */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<BackendSyncWorker>(
                15, TimeUnit.MINUTES,
                5, TimeUnit.MINUTES  // 弹性间隔
            ).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
            Timber.i("BackendSyncWorker: scheduled periodic sync (every 15 min)")
        }
    }
}
