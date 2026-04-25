package com.csbaby.kefu.data.local

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 数据备份管理器
 * 支持备份和恢复：DataStore 偏好设置、Room 数据库文件
 * 备份格式：ZIP 压缩包
 */
@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "BackupManager"
        private const val BACKUP_FILE_NAME = "kefu_backup.zip"
        private const val DATASTORE_DIR = "datastore"
        private const val DATABASE_DIR = "databases"
    }

    data class BackupResult(
        val success: Boolean,
        val message: String,
        val backupPath: String? = null
    )

    data class RestoreResult(
        val success: Boolean,
        val message: String
    )

    /**
     * 执行备份
     * @param outputUri 用户选择的保存位置
     */
    suspend fun performBackup(outputUri: Uri): BackupResult = withContext(Dispatchers.IO) {
        try {
            Timber.d("开始备份数据到: $outputUri")

            val backupFiles = collectBackupFiles()
            if (backupFiles.isEmpty()) {
                return@withContext BackupResult(false, "没有找到需要备份的数据")
            }

            context.contentResolver.openOutputStream(outputUri)?.use { outputStream ->
                ZipOutputStream(BufferedOutputStream(outputStream)).use { zipOut ->
                    backupFiles.forEach { (relativePath, file) ->
                        if (file.exists() && file.length() > 0) {
                            val entry = ZipEntry(relativePath)
                            zipOut.putNextEntry(entry)
                            file.inputStream().use { input ->
                                input.copyTo(zipOut)
                            }
                            zipOut.closeEntry()
                            Timber.d("已备份: $relativePath (${file.length()} bytes)")
                        }
                    }
                }
            } ?: return@withContext BackupResult(false, "无法创建备份文件")

            val totalSize = backupFiles.values.sumOf { if (it.exists()) it.length() else 0 }
            val fileCount = backupFiles.count { it.value.exists() && it.value.length() > 0 }
            BackupResult(
                success = true,
                message = "备份完成：共 $fileCount 个文件，${formatFileSize(totalSize)}"
            )
        } catch (e: Exception) {
            Timber.e(e, "备份失败")
            BackupResult(false, "备份失败: ${e.message}")
        }
    }

    /**
     * 从备份文件恢复数据
     * @param inputUri 用户选择的备份文件
     */
    suspend fun restoreData(inputUri: Uri): RestoreResult = withContext(Dispatchers.IO) {
        try {
            Timber.d("开始从备份恢复数据: $inputUri")

            val tempDir = File(context.cacheDir, "restore_temp")
            if (tempDir.exists()) {
                tempDir.deleteRecursively()
            }
            tempDir.mkdirs()

            // 1. 解压备份文件到临时目录
            val extractedFiles = mutableListOf<File>()
            context.contentResolver.openInputStream(inputUri)?.use { inputStream ->
                ZipInputStream(BufferedInputStream(inputStream)).use { zipIn ->
                    var entry: ZipEntry? = zipIn.nextEntry
                    while (entry != null) {
                        val file = File(tempDir, entry.name)
                        file.parentFile?.mkdirs()

                        if (!entry.isDirectory) {
                            file.outputStream().use { output ->
                                zipIn.copyTo(output)
                            }
                            extractedFiles.add(file)
                            Timber.d("已解压: ${entry.name} (${file.length()} bytes)")
                        }
                        zipIn.closeEntry()
                        entry = zipIn.nextEntry
                    }
                }
            } ?: return@withContext RestoreResult(false, "无法读取备份文件")

            if (extractedFiles.isEmpty()) {
                return@withContext RestoreResult(false, "备份文件为空或格式不正确")
            }

            // 2. 恢复 DataStore 文件
            val datastoreDir = File(context.filesDir.parentFile, DATASTORE_DIR)
            val datastoreFiles = extractedFiles.filter { it.absolutePath.contains(DATASTORE_DIR) }
            var restoredCount = 0
            datastoreFiles.forEach { file ->
                val relativePath = file.absolutePath.substringAfter("$DATASTORE_DIR/")
                val targetFile = File(datastoreDir, relativePath)
                targetFile.parentFile?.mkdirs()
                file.copyTo(targetFile, overwrite = true)
                restoredCount++
                Timber.d("已恢复 DataStore: $relativePath")
            }

            // 3. 恢复数据库文件
            val databaseDir = File(context.filesDir.parentFile, DATABASE_DIR)
            val dbFiles = extractedFiles.filter { it.absolutePath.contains(DATABASE_DIR) }
            dbFiles.forEach { file ->
                val dbName = file.name
                val targetFile = File(databaseDir, dbName)
                targetFile.parentFile?.mkdirs()
                file.copyTo(targetFile, overwrite = true)
                restoredCount++
                Timber.d("已恢复数据库: $dbName")
            }

            // 清理临时目录
            tempDir.deleteRecursively()

            RestoreResult(
                success = true,
                message = "恢复完成：共恢复 $restoredCount 个文件。请重启应用以生效。"
            )
        } catch (e: Exception) {
            Timber.e(e, "恢复失败")
            RestoreResult(false, "恢复失败: ${e.message}")
        }
    }

    /**
     * 收集需要备份的文件
     */
    private fun collectBackupFiles(): Map<String, File> {
        val files = mutableMapOf<String, File>()

        // 1. DataStore 文件
        val datastoreDir = File(context.filesDir.parentFile, DATASTORE_DIR)
        if (datastoreDir.exists() && datastoreDir.isDirectory) {
            datastoreDir.walkTopDown().filter { it.isFile }.forEach { file ->
                val relativePath = "$DATASTORE_DIR/${file.relativeTo(datastoreDir).path}"
                files[relativePath] = file
            }
        }

        // 2. Room 数据库文件（包括 .db, .db-wal, .db-shm）
        val databaseDir = File(context.filesDir.parentFile, DATABASE_DIR)
        if (databaseDir.exists() && databaseDir.isDirectory) {
            databaseDir.listFiles()?.filter { it.isFile }?.forEach { file ->
                val relativePath = "$DATABASE_DIR/${file.name}"
                files[relativePath] = file
            }
        }

        Timber.d("收集到 ${files.size} 个备份文件")
        return files
    }

    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> "${bytes / (1024 * 1024)} MB"
        }
    }
}
