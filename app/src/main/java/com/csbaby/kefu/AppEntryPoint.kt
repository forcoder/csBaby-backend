package com.csbaby.kefu

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.csbaby.kefu.data.repository.OtaRepository
import com.csbaby.kefu.data.sync.SyncManager
import com.csbaby.kefu.data.sync.SyncWorker
import com.csbaby.kefu.infrastructure.monitoring.PerformanceMonitor
import com.csbaby.kefu.infrastructure.ota.OtaScheduler
import com.csbaby.kefu.infrastructure.ota.OtaUpdateWorker
import com.csbaby.kefu.infrastructure.reply.ReplyOrchestrator
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Application-level EntryPoint for accessing Hilt-managed dependencies in
 * KefuApplication.onCreate() (which is not a Hilt-managed component).
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface AppEntryPoint {
    fun replyOrchestrator(): ReplyOrchestrator
    fun otaScheduler(): OtaScheduler
    fun performanceMonitor(): PerformanceMonitor
    fun syncManager(): SyncManager
    fun otaRepository(): OtaRepository
    fun appWorkerFactory(): AppWorkerFactory
}

/**
 * Composite WorkerFactory that creates workers with Hilt-injected dependencies.
 * Replaces HiltWorkerFactory to avoid hilt-compiler dependency (which causes
 * Dagger SuperficialValidation ClassCastException with Room compiler-processing).
 */
@Singleton
class AppWorkerFactory @Inject constructor(
    private val syncManager: SyncManager,
    private val otaRepository: OtaRepository
) : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters
    ): ListenableWorker? {
        return when (workerClassName) {
            SyncWorker::class.java.name -> SyncWorker(appContext, workerParameters, syncManager)
            OtaUpdateWorker::class.java.name -> OtaUpdateWorker(appContext, workerParameters, otaRepository)
            else -> null
        }
    }
}
