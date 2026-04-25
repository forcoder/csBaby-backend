package com.csbaby.kefu.data.local

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.csbaby.kefu.BuildConfig
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

private val Context.backupDataStore by preferencesDataStore(name = "kefu_settings")

/**
 * 数据备份管理器
 * 负责完整备份和恢复应用数据
 */
@Singleton
class BackupManager @Inject constructor(
    private val context: Context
) {
    companion object {
        private const val TAG = "BackupManager"
        const val DATABASE_FILE_NAME = "kefu_database"
        const val DATASTORE_FILE_NAME = "kefu_settings.preferences_pb"
        const val BACKUP_VERSION = 1
    }

    private val gson = Gson()

    /**
     * 备份元数据
     */
    data class BackupMetadata(
        val backupVersion: Int = BACKUP_VERSION,
        val appVersionName: String = BuildConfig.VERSION_NAME,
        val appVersionCode: Int = BuildConfig.VERSION_CODE,
        val backupTime: Long = System.currentTimeMillis(),
        val backupTimeFormatted: String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            .format(Date()),
        val databaseIncluded: Boolean = true,
        val settingsIncluded: Boolean = true,
        val platform: String = "Android"
    )

    /**
     * 备份结果
     */
    sealed class BackupResult {
        data class Success(
            val uri: Uri,
            val metadata: BackupMetadata,
            val databaseSize: Long,
            val settingsSize: Long
        ) : BackupResult()

        data class Error(val message: String, val exception: Throwable? = null) : BackupResult()
    }

    /**
     * 执行完整备份
     */
    suspend fun performBackup(outputUri: Uri): BackupResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "开始备份到: $outputUri")

            val databaseFile = context.getDatabasePath(KefuDatabase.DATABASE_NAME)
            val databaseExists = databaseFile.exists()
            val databaseSize = if (databaseExists) databaseFile.length() else 0L

            // DataStore 文件路径
            val dataStoreDir = File(context.filesDir.parent!!, "datastore")
            val dataStoreFile = File(dataStoreDir, DATASTORE_FILE_NAME)
            val actualDataStoreFile = if (dataStoreFile.exists()) dataStoreFile else null
            val settingsSize = actualDataStoreFile?.length() ?: 0L

            Log.d(TAG, "数据库存在: $databaseExists, 大小: $databaseSize")
            Log.d(TAG, "DataStore存在: ${actualDataStoreFile != null}, 大小: $settingsSize")

            context.contentResolver.openOutputStream(outputUri)?.use { outputStream ->
                ZipOutputStream(BufferedOutputStream(outputStream)).use { zipOut ->

                    // 写入元数据
                    val metadata = BackupMetadata(
                        databaseIncluded = databaseExists,
                        settingsIncluded = actualDataStoreFile != null
                    )
                    val metadataJson = gson.toJson(metadata).toByteArray()
                    zipOut.putNextEntry(ZipEntry("backup_metadata.json"))
                    zipOut.write(metadataJson)
                    zipOut.closeEntry()

                    // 写入数据库
                    if (databaseExists) {
                        zipOut.putNextEntry(ZipEntry(DATABASE_FILE_NAME))
                        databaseFile.inputStream().use { input ->
                            input.copyTo(zipOut)
                        }
                        zipOut.closeEntry()
                    }

                    // 写入 DataStore 设置文件
                    if (actualDataStoreFile != null) {
                        zipOut.putNextEntry(ZipEntry(DATASTORE_FILE_NAME))
                        actualDataStoreFile.inputStream().use { input ->
                            input.copyTo(zipOut)
                        }
                        zipOut.closeEntry()
                    }

                    zipOut.finish()
                }
            } ?: return@withContext BackupResult.Error("无法打开输出流")

            Log.d(TAG, "备份完成: 数据库=$databaseSize, 设置=$settingsSize")

            BackupResult.Success(
                uri = outputUri,
                metadata = metadata,
                databaseSize = databaseSize,
                settingsSize = settingsSize
            )
        } catch (e: Exception) {
            Log.e(TAG, "备份失败", e)
            BackupResult.Error("备份失败: ${e.message}", e)
        }
    }

    /**
     * 格式化文件大小
     */
    fun formatFileSize(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 * 1024 -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
            bytes >= 1024 * 1024 -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
            bytes >= 1024 -> String.format("%.2f KB", bytes / 1024.0)
            else -> "$bytes B"
        }
    }
}