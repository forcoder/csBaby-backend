package com.csbaby.kefu.infrastructure.backup

import android.content.Context
import android.util.Log
import androidx.core.content.FileProvider
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.csbaby.kefu.data.local.BackupManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * 自动备份 Worker
 * 定期在后台执行数据备份，备份文件保存到应用外部存储的备份目录
 */
@HiltWorker
class AutoBackupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val backupManager: BackupManager
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "AutoBackupWorker"
        const val WORK_NAME = "auto_backup"
        const val KEY_BACKUP_URI = "backup_uri"
        const val KEY_RESULT_SUCCESS = "result_success"
        const val KEY_RESULT_MESSAGE = "result_message"

        /**
         * 调度定期自动备份
         * @param intervalHours 备份间隔（小时），默认 24 小时
         */
        fun schedule(context: Context, intervalHours: Long = 24) {
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true) // 电量不低时执行
                .setRequiresStorageNotLow(true) // 存储空间充足时执行
                .build()

            val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(
                intervalHours, TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    10, TimeUnit.MINUTES
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP, // 保留已有的，不重复创建
                request
            )

            Log.d(TAG, "自动备份已调度，间隔: ${intervalHours}小时")
        }

        /**
         * 取消自动备份
         */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.d(TAG, "自动备份已取消")
        }

        /**
         * 检查自动备份是否已启用
         */
        fun isScheduled(context: Context): Boolean {
            val workInfos = WorkManager.getInstance(context)
                .getWorkInfosForUniqueWork(WORK_NAME)
                .get()
            return workInfos.any { !it.state.isFinished }
        }

        /**
         * 执行一次性备份（立即执行）
         */
        fun runOnce(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build()

            val request = OneTimeWorkRequestBuilder<AutoBackupWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueue(request)
            Log.d(TAG, "一次性备份已调度")
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "开始自动备份...")

            // 创建备份文件路径
            val backupDir = getBackupDirectory()
            if (backupDir == null) {
                Log.e(TAG, "无法创建备份目录")
                return@withContext Result.failure(
                    workDataOf(
                        KEY_RESULT_SUCCESS to false,
                        KEY_RESULT_MESSAGE to "无法创建备份目录"
                    )
                )
            }

            // 生成备份文件名
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                .format(Date())
            val backupFile = File(backupDir, "csbaby_backup_$timestamp.zip")

            // 使用 content URI 备份
            val uri = androidx.core.content.FileProvider.getUriForFile(
                applicationContext,
                "${applicationContext.packageName}.fileprovider",
                backupFile
            )

            // 执行备份
            val result = backupManager.performBackup(uri) { progress, message ->
                Log.d(TAG, "备份进度: ${(progress * 100).toInt()}% - $message")
            }

            when (result) {
                is BackupManager.BackupResult.Success -> {
                    Log.d(TAG, "自动备份成功: ${result.metadata.backupTimeFormatted}")

                    // 清理旧的备份文件（保留最近 5 个）
                    cleanupOldBackups(backupDir, keepCount = 5)

                    Result.success(
                        workDataOf(
                            KEY_RESULT_SUCCESS to true,
                            KEY_RESULT_MESSAGE to "自动备份成功: ${result.metadata.backupTimeFormatted}"
                        )
                    )
                }
                is BackupManager.BackupResult.Error -> {
                    Log.e(TAG, "自动备份失败: ${result.message}")
                    Result.failure(
                        workDataOf(
                            KEY_RESULT_SUCCESS to false,
                            KEY_RESULT_MESSAGE to "自动备份失败: ${result.message}"
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "自动备份异常", e)
            Result.failure(
                workDataOf(
                    KEY_RESULT_SUCCESS to false,
                    KEY_RESULT_MESSAGE to "自动备份异常: ${e.message}"
                )
            )
        }
    }

    /**
     * 获取备份目录
     */
    private fun getBackupDirectory(): File? {
        // 优先使用外部存储的备份目录
        val externalDir = applicationContext.getExternalFilesDir("backups")
        if (externalDir != null) {
            if (!externalDir.exists()) {
                externalDir.mkdirs()
            }
            return externalDir
        }

        // 回退到内部存储
        val internalDir = File(applicationContext.filesDir, "backups")
        if (!internalDir.exists()) {
            internalDir.mkdirs()
        }
        return internalDir
    }

    /**
     * 清理旧的备份文件
     */
    private fun cleanupOldBackups(backupDir: File, keepCount: Int) {
        try {
            val backupFiles = backupDir.listFiles { file ->
                file.name.startsWith("csbaby_backup_") && file.name.endsWith(".zip")
            } ?: return

            if (backupFiles.size > keepCount) {
                // 按修改时间排序，删除最旧的文件
                val sortedFiles = backupFiles.sortedBy { it.lastModified() }
                val filesToDelete = sortedFiles.take(sortedFiles.size - keepCount)

                filesToDelete.forEach { file ->
                    if (file.delete()) {
                        Log.d(TAG, "已删除旧备份: ${file.name}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "清理旧备份文件失败", e)
        }
    }
}
