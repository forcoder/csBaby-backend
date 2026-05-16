package com.csbaby.kefu.infrastructure.ota

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.csbaby.kefu.BuildConfig
import com.csbaby.kefu.data.repository.OtaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

class OtaUpdateWorker(
    context: Context,
    params: WorkerParameters,
    private val repository: OtaRepository
) : CoroutineWorker(context, params) {

    companion object {
        const val TAG = "OtaUpdateWorker"
        const val WORK_NAME = "ota_update_check"
    }

    override suspend fun doWork(): Result {
        return try {
            withContext(Dispatchers.IO) {
                Timber.d("开始后台检查更新...")

                val result = repository.checkForUpdate(BuildConfig.VERSION_CODE)

                if (result.isSuccess) {
                    val update = result.getOrNull()

                    if (update != null && update.needsUpdate(BuildConfig.VERSION_CODE)) {
                        Timber.d("发现新版本: ${update.versionName} (${update.versionCode})")
                    } else {
                        Timber.d("当前已是最新版本")
                    }

                    Result.success()
                } else {
                    Timber.e("检查更新失败: ${result.exceptionOrNull()?.message}")
                    Result.failure()
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "OTA更新检查Worker执行失败")
            Result.failure()
        }
    }

    class Factory(
        private val repository: OtaRepository
    ) : WorkerFactory() {
        override fun createWorker(
            appContext: Context,
            workerClassName: String,
            workerParameters: WorkerParameters
        ): ListenableWorker? {
            return when (workerClassName) {
                OtaUpdateWorker::class.java.name -> OtaUpdateWorker(appContext, workerParameters, repository)
                else -> null
            }
        }
    }
}
