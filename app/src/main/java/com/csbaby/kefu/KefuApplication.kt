package com.csbaby.kefu

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.csbaby.kefu.data.remote.backend.BackendSyncManager
import com.csbaby.kefu.data.remote.backend.BackendSyncWorker
import com.csbaby.kefu.infrastructure.monitoring.AppPerformanceMonitor
import com.csbaby.kefu.infrastructure.ota.OtaScheduler
import com.csbaby.kefu.infrastructure.reply.ReplyOrchestrator
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Application class — uses standard @HiltAndroidApp for full Hilt initialization.
 * This enables @AndroidEntryPoint on Activity / Service / BroadcastReceiver components.
 */
@HiltAndroidApp
class KefuApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        Log.d(TAG, "KefuApplication.onCreate() starting")

        try {
            val entryPoint = EntryPointAccessors.fromApplication(
                this,
                AppEntryPoint::class.java
            )

            try {
                val replyOrchestrator = entryPoint.replyOrchestrator()
                replyOrchestrator.start()
                Timber.d("ReplyOrchestrator started")
                Log.d(TAG, "ReplyOrchestrator started OK")
            } catch (e: Exception) {
                Timber.e(e, "Failed to start ReplyOrchestrator")
                Log.e(TAG, "Failed to start ReplyOrchestrator", e)
            }

            try {
                val otaScheduler = entryPoint.otaScheduler()
                otaScheduler.schedulePeriodicUpdateCheck()
                Timber.d("OTA update check scheduled")
                Log.d(TAG, "OTA update check scheduled OK")
            } catch (e: Exception) {
                Timber.e(e, "Failed to schedule OTA updates")
                Log.e(TAG, "Failed to schedule OTA updates", e)
            }
            
            // 性能监控器已通过Hilt自动初始化

            // 后端设备注册 + 定期同步
            try {
                val syncManager = entryPoint.backendSyncManager()
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    val registered = syncManager.registerIfNeeded()
                    if (registered) {
                        Timber.i("Backend device registered successfully")
                    } else {
                        Timber.w("Backend device registration failed — will retry later")
                    }
                }
                // 启动定期后台同步（心跳 + 数据拉取）
                BackendSyncWorker.schedule(this@KefuApplication)
            } catch (e: Exception) {
                Timber.e(e, "Backend sync initialization failed")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Hilt EntryPoint bootstrap failed — app will run without auto-reply", e)
        }
    }

    companion object {
        private const val TAG = "KefuApp"
    }
}
