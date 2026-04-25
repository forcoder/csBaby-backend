package com.csbaby.kefu.data.local

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.csbaby.kefu.BuildConfig
import com.google.gson.Gson
import com.google.gson.JsonObject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

private val Context.backupDataStore by preferencesDataStore(name = "kefu_settings_backup")

/**
 * 数据备份管理器
 * 负责完整备份和恢复应用数据，包括：
 * - Room 数据库文件（kefu_database）
 * - DataStore 设置（kefu_settings）
 *
 * 备份格式为 ZIP 压缩包，包含元数据（版本号、备份时间、应用版本）
 */
@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "BackupManager"
        const val BACKUP_FILE_PREFIX = "csbaby_backup_"
        const val BACKUP_FILE_EXTENSION = ".zip"
        const val BACKUP_METADATA_FILE = "backup_metadata.json"
        const val DATABASE_FILE_NAME = "kefu_database"
        const val DATABASE_WAL_FILE_NAME = "kefu_database-wal"
        const val DATABASE_SHM_FILE_NAME = "kefu_database-shm"
        const val DATASTORE_FILE_NAME = "kefu_settings.preferences_pb"
        const val DATASTORE_OLD_FILE_NAME = "kefu_settings.preferences"
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
     * 恢复结果
     */
    sealed class RestoreResult {
        data class Success(
            val metadata: BackupMetadata,
            val databaseRestored: Boolean,
            val settingsRestored: Boolean
        ) : RestoreResult()

        data class Error(val message: String, val exception: Throwable? = null) : RestoreResult()
    }

    /**
     * 执行完整备份
     * @param outputUri 备份文件输出位置（用户选择的路径）
     * @param progressCallback 进度回调 (0.0 - 1.0)
     */
    suspend fun performBackup(
        outputUri: Uri,
        progressCallback: ((Float, String) -> Unit)? = null
    ): BackupResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "开始备份数据到: $outputUri")

            progressCallback?.invoke(0.1f, "准备备份...")

            val databaseFile = context.getDatabasePath(KefuDatabase.DATABASE_NAME)
            val databaseExists = databaseFile.exists()
            val databaseSize = if (databaseExists) databaseFile.length() else 0L

            // DataStore 文件路径
            val dataStoreDir = File(context.filesDir.parent!!, "datastore")
            val dataStoreFile = File(dataStoreDir, DATASTORE_FILE_NAME)
            val dataStoreOldFile = File(dataStoreDir, DATASTORE_OLD_FILE_NAME)
            val actualDataStoreFile = when {
                dataStoreFile.exists() -> dataStoreFile
                dataStoreOldFile.exists() -> dataStoreOldFile
                else -> null
            }
            val settingsSize = actualDataStoreFile?.length() ?: 0L

            Log.d(TAG, "数据库路径: ${databaseFile.absolutePath}, 存在: $databaseExists, 大小: $databaseSize")
            Log.d(TAG, "DataStore路径: ${dataStoreDir.absolutePath}, 存在: ${actualDataStoreFile != null}, 大小: $settingsSize")

            progressCallback?.invoke(0.3f, "创建备份文件...")

            val metadata = BackupMetadata(
                databaseIncluded = databaseExists,
                settingsIncluded = actualDataStoreFile != null
            )

            context.contentResolver.openOutputStream(outputUri)?.use { outputStream ->
                ZipOutputStream(BufferedOutputStream(outputStream)).use { zipOut ->

                    // 1. 写入元数据
                    progressCallback?.invoke(0.4f, "写入元数据...")
                    val metadataJson = gson.toJson(metadata).toByteArray()
                    zipOut.putNextEntry(ZipEntry(BACKUP_METADATA_FILE))
                    zipOut.write(metadataJson)
                    zipOut.closeEntry()

                    // 2. 写入数据库文件
                    if (databaseExists) {
                        progressCallback?.invoke(0.5f, "备份数据库...")
                        zipOut.putNextEntry(ZipEntry(DATABASE_FILE_NAME))
                        databaseFile.inputStream().use { input ->
                            input.copyTo(zipOut)
                        }
                        zipOut.closeEntry()

                        // 写入 WAL 文件（如果存在）
                        val walFile = File(databaseFile.parent, DATABASE_WAL_FILE_NAME)
                        if (walFile.exists()) {
                            progressCallback?.invoke(0.6f, "备份数据库日志...")
                            zipOut.putNextEntry(ZipEntry(DATABASE_WAL_FILE_NAME))
                            walFile.inputStream().use { input ->
                                input.copyTo(zipOut)
                            }
                            zipOut.closeEntry()
                        }

                        // 写入 SHM 文件（如果存在）
                        val shmFile = File(databaseFile.parent, DATABASE_SHM_FILE_NAME)
                        if (shmFile.exists()) {
                            zipOut.putNextEntry(ZipEntry(DATABASE_SHM_FILE_NAME))
                            shmFile.inputStream().use { input ->
                                input.copyTo(zipOut)
                            }
                            zipOut.closeEntry()
                        }
                    }

                    // 3. 写入 DataStore 设置文件
                    if (actualDataStoreFile != null) {
                        progressCallback?.invoke(0.8f, "备份应用设置...")
                        zipOut.putNextEntry(ZipEntry(DATASTORE_FILE_NAME))
                        actualDataStoreFile.inputStream().use { input ->
                            input.copyTo(zipOut)
                        }
                        zipOut.closeEntry()
                    }

                    progressCallback?.invoke(0.95f, "完成备份...")
                    zipOut.finish()
                }
            } ?: return@withContext BackupResult.Error("无法打开输出流")

            progressCallback?.invoke(1.0f, "备份完成")

            Log.d(TAG, "备份完成: 数据库大小=$databaseSize, 设置大小=$settingsSize")

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
     * 从备份文件恢复数据
     * @param inputUri 备份文件路径
     * @param progressCallback 进度回调 (0.0 - 1.0)
     */
    suspend fun performRestore(
        inputUri: Uri,
        progressCallback: ((Float, String) -> Unit)? = null
    ): RestoreResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "开始从备份恢复: $inputUri")

            progressCallback?.invoke(0.1f, "读取备份文件...")

            val metadata: BackupMetadata
            val databaseBytes: ByteArray?
            val settingsBytes: ByteArray?

            context.contentResolver.openInputStream(inputUri)?.use { inputStream ->
                ZipInputStream(BufferedInputStream(inputStream)).use { zipIn ->
                    var entry = zipIn.nextEntry
                    metadata = null
                    var dbBytes: ByteArray? = null
                    var setBytes: ByteArray? = null
                    var meta: BackupMetadata? = null

                    while (entry != null) {
                        when (entry.name) {
                            BACKUP_METADATA_FILE -> {
                                progressCallback?.invoke(0.2f, "解析备份元数据...")
                                val metadataJson = zipIn.bufferedReader().readText()
                                meta = gson.fromJson(metadataJson, BackupMetadata::class.java)
                            }
                            DATABASE_FILE_NAME -> {
                                progressCallback?.invoke(0.3f, "读取数据库备份...")
                                dbBytes = zipIn.readBytes()
                            }
                            DATASTORE_FILE_NAME -> {
                                progressCallback?.invoke(0.5f, "读取设置备份...")
                                setBytes = zipIn.readBytes()
                            }
                            // 跳过 WAL 和 SHM 文件（我们只需要主数据库文件）
                            DATABASE_WAL_FILE_NAME,
                            DATABASE_SHM_FILE_NAME -> {
                                // 跳过，恢复后系统会自动重建
                            }
                        }
                        zipIn.closeEntry()
                        entry = zipIn.nextEntry
                    }

                    metadata = meta
                    databaseBytes = dbBytes
                    settingsBytes = setBytes
                }
            } ?: return@withContext RestoreResult.Error("无法打开备份文件")

            if (metadata == null) {
                return@withContext RestoreResult.Error("备份文件缺少元数据，无法恢复")
            }

            // 验证备份版本
            if (metadata.backupVersion > BACKUP_VERSION) {
                return@withContext RestoreResult.Error(
                    "备份文件版本 (${metadata.backupVersion}) 高于当前支持的版本 ($BACKUP_VERSION)，请升级应用后再恢复"
                )
            }

            Log.d(TAG, "备份元数据: 版本=${metadata.appVersionName}, 时间=${metadata.backupTimeFormatted}")

            var databaseRestored = false
            var settingsRestored = false

            // 恢复数据库
            if (databaseBytes != null && metadata.databaseIncluded) {
                progressCallback?.invoke(0.6f, "恢复数据库...")

                // 先关闭数据库连接
                // 注意：这里假设调用方已经关闭了数据库
                val databaseFile = context.getDatabasePath(KefuDatabase.DATABASE_NAME)

                // 备份当前数据库（以防万一）
                if (databaseFile.exists()) {
                    val backupDb = File(databaseFile.parent, "${KefuDatabase.DATABASE_NAME}.restore_backup")
                    databaseFile.copyTo(backupDb, overwrite = true)
                    Log.d(TAG, "已备份当前数据库到: ${backupDb.absolutePath}")
                }

                try {
                    // 写入新的数据库文件
                    databaseFile.parentFile?.mkdirs()
                    FileOutputStream(databaseFile).use { output ->
                        output.write(databaseBytes)
                    }

                    // 删除可能存在的 WAL 和 SHM 文件（让系统重建）
                    File(databaseFile.parent, DATABASE_WAL_FILE_NAME).delete()
                    File(databaseFile.parent, DATABASE_SHM_FILE_NAME).delete()

                    databaseRestored = true
                    Log.d(TAG, "数据库恢复成功: ${databaseFile.absolutePath}")
                } catch (e: Exception) {
                    Log.e(TAG, "数据库恢复失败", e)
                    // 尝试回滚
                    val backupDb = File(databaseFile.parent, "${KefuDatabase.DATABASE_NAME}.restore_backup")
                    if (backupDb.exists()) {
                        backupDb.copyTo(databaseFile, overwrite = true)
                        Log.d(TAG, "已回滚数据库")
                    }
                    return@withContext RestoreResult.Error("数据库恢复失败: ${e.message}", e)
                }
            }

            // 恢复 DataStore 设置
            if (settingsBytes != null && metadata.settingsIncluded) {
                progressCallback?.invoke(0.8f, "恢复应用设置...")

                val dataStoreDir = File(context.filesDir.parent!!, "datastore")
                val dataStoreFile = File(dataStoreDir, DATASTORE_FILE_NAME)

                // 备份当前设置
                if (dataStoreFile.exists()) {
                    val backupSet = File(dataStoreDir, "kefu_settings.restore_backup")
                    dataStoreFile.copyTo(backupSet, overwrite = true)
                    Log.d(TAG, "已备份当前设置到: ${backupSet.absolutePath}")
                }

                try {
                    dataStoreDir.mkdirs()
                    FileOutputStream(dataStoreFile).use { output ->
                        output.write(settingsBytes)
                    }

                    settingsRestored = true
                    Log.d(TAG, "设置恢复成功: ${dataStoreFile.absolutePath}")
                } catch (e: Exception) {
                    Log.e(TAG, "设置恢复失败", e)
                    // 尝试回滚
                    val backupSet = File(dataStoreDir, "kefu_settings.restore_backup")
                    if (backupSet.exists()) {
                        backupSet.copyTo(dataStoreFile, overwrite = true)
                        Log.d(TAG, "已回滚设置")
                    }
                    return@withContext RestoreResult.Error("设置恢复失败: ${e.message}", e)
                }
            }

            progressCallback?.invoke(1.0f, "恢复完成")

            Log.d(TAG, "恢复完成: 数据库=$databaseRestored, 设置=$settingsRestored")

            RestoreResult.Success(
                metadata = metadata,
                databaseRestored = databaseRestored,
                settingsRestored = settingsRestored
            )
        } catch (e: Exception) {
            Log.e(TAG, "恢复失败", e)
            RestoreResult.Error("恢复失败: ${e.message}", e)
        }
    }

    /**
     * 解析备份文件元数据（不恢复数据）
     */
    suspend fun readBackupMetadata(inputUri: Uri): BackupMetadata? = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(inputUri)?.use { inputStream ->
                ZipInputStream(BufferedInputStream(inputStream)).use { zipIn ->
                    var entry = zipIn.nextEntry
                    while (entry != null) {
                        if (entry.name == BACKUP_METADATA_FILE) {
                            val metadataJson = zipIn.bufferedReader().readText()
                            return@withContext gson.fromJson(metadataJson, BackupMetadata::class.java)
                        }
                        zipIn.closeEntry()
                        entry = zipIn.nextEntry
                    }
                }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "读取备份元数据失败", e)
            null
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
