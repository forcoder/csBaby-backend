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
        private const val MIN_BACKUP_SIZE = 50L // 最小备份大小 50 bytes
        private const val META_FILE = ".backup_meta"
        private const val META_SIGNATURE = "CSBABY_BACKUP_V1"
    }

    data class BackupResult(
        val success: Boolean,
        val message: String,
        val backupPath: String? = null,
        val fileCount: Int = 0,
        val totalSize: Long = 0
    )

    data class RestoreResult(
        val success: Boolean,
        val message: String,
        val restoredFileCount: Int = 0
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

            var totalBytesWritten = 0L
            var fileCount = 0

            context.contentResolver.openOutputStream(outputUri)?.use { outputStream ->
                BufferedOutputStream(outputStream).use { buffered ->
                    ZipOutputStream(buffered).use { zipOut ->
                        // 写入元数据标识
                        val metaEntry = ZipEntry(META_FILE)
                        zipOut.putNextEntry(metaEntry)
                        zipOut.write(META_SIGNATURE.toByteArray(Charsets.UTF_8))
                        zipOut.closeEntry()

                        backupFiles.forEach { (relativePath, file) ->
                            if (file.exists() && file.length() > 0) {
                                val entry = ZipEntry(relativePath)
                                zipOut.putNextEntry(entry)
                                file.inputStream().use { input ->
                                    input.copyTo(zipOut)
                                }
                                zipOut.closeEntry()
                                totalBytesWritten += file.length()
                                fileCount++
                                Timber.d("已备份: $relativePath (${file.length()} bytes)")
                            }
                        }
                    }
                }
            } ?: return@withContext BackupResult(false, "无法创建备份文件，请检查存储权限")

            if (totalBytesWritten < MIN_BACKUP_SIZE) {
                return@withContext BackupResult(false, "备份文件不完整，请重试")
            }

            val formattedSize = formatFileSize(totalBytesWritten)
            Timber.i("备份完成: $fileCount 个文件, $formattedSize")
            BackupResult(
                success = true,
                message = "备份完成：共 $fileCount 个文件，$formattedSize",
                fileCount = fileCount,
                totalSize = totalBytesWritten
            )
        } catch (e: SecurityException) {
            Timber.e(e, "备份失败: 存储权限不足")
            BackupResult(false, "备份失败：无法访问存储，请在设置中授予存储权限")
        } catch (e: FileNotFoundException) {
            Timber.e(e, "备份失败: 文件未找到")
            BackupResult(false, "备份失败：无法创建备份文件，请检查存储空间是否充足")
        } catch (e: IOException) {
            Timber.e(e, "备份失败: IO异常")
            BackupResult(false, "备份失败：读写错误，请检查存储空间并重试")
        } catch (e: Exception) {
            Timber.e(e, "备份失败: 未知异常")
            BackupResult(false, "备份失败：${e.message}")
        }
    }

    /**
     * 从备份文件恢复数据
     * @param inputUri 用户选择的备份文件
     */
    suspend fun restoreData(inputUri: Uri): RestoreResult = withContext(Dispatchers.IO) {
        try {
            Timber.d("开始从备份恢复数据: $inputUri")

            // 验证输入文件大小
            val fileSize = getFileSize(inputUri)
            if (fileSize != null && fileSize < MIN_BACKUP_SIZE) {
                return@withContext RestoreResult(false, "备份文件无效或已损坏（文件过小）")
            }

            val tempDir = File(context.cacheDir, "restore_temp")
            if (tempDir.exists()) {
                tempDir.deleteRecursively()
            }
            tempDir.mkdirs()

            // 1. 解压备份文件到临时目录
            val extractedFiles = mutableListOf<File>()
            var magicVerified = false

            context.contentResolver.openInputStream(inputUri)?.use { inputStream ->
                BufferedInputStream(inputStream).use { buffered ->
                    ZipInputStream(buffered).use { zipIn ->
                        var entry: ZipEntry? = zipIn.nextEntry
                        while (entry != null) {
                            // 检查元数据标识
                            if (entry.name == META_FILE) {
                                val metaBytes = zipIn.readBytes()
                                val meta = String(metaBytes, Charsets.UTF_8)
                                if (META_SIGNATURE == meta) {
                                    magicVerified = true
                                }
                                zipIn.closeEntry()
                                entry = zipIn.nextEntry
                                continue
                            }

                            // 防止ZIP炸弹：限制单个文件大小
                            val maxEntrySize = 100 * 1024 * 1024L // 100MB
                            if (entry.size > maxEntrySize) {
                                zipIn.closeEntry()
                                entry = zipIn.nextEntry
                                continue
                            }

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
                }
            } ?: return@withContext RestoreResult(false, "无法读取备份文件，文件可能已被移动或删除")

            if (extractedFiles.isEmpty()) {
                return@withContext RestoreResult(false, "备份文件为空或格式不正确")
            }

            // 若不是本应用生成的备份，给出警告但仍继续恢复
            if (!magicVerified) {
                Timber.w("备份文件缺少应用标识，可能不是由本应用生成")
            }

            // 2. 恢复 DataStore 文件
            val datastoreDir = File(context.filesDir.parentFile, DATASTORE_DIR)
            val datastoreFiles = extractedFiles.filter {
                it.absolutePath.replace("\\", "/").contains(DATASTORE_DIR)
            }
            var restoredCount = 0
            datastoreFiles.forEach { file ->
                val relativePath = file.absolutePath
                    .replace("\\", "/")
                    .substringAfter("$DATASTORE_DIR/")
                val targetFile = File(datastoreDir, relativePath)
                targetFile.parentFile?.mkdirs()
                file.copyTo(targetFile, overwrite = true)
                restoredCount++
                Timber.d("已恢复 DataStore: $relativePath")
            }

            // 3. 恢复数据库文件
            val databaseDir = File(context.filesDir.parentFile, DATABASE_DIR)
            val dbFiles = extractedFiles.filter {
                it.absolutePath.replace("\\", "/").contains(DATABASE_DIR)
            }
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

            if (restoredCount == 0) {
                return@withContext RestoreResult(
                    false,
                    "备份文件中未找到可恢复的数据文件"
                )
            }

            Timber.i("恢复完成: $restoredCount 个文件")
            RestoreResult(
                success = true,
                message = "恢复完成：共恢复 $restoredCount 个文件。建议重启应用以生效。",
                restoredFileCount = restoredCount
            )
        } catch (e: SecurityException) {
            Timber.e(e, "恢复失败: 权限不足")
            RestoreResult(false, "恢复失败：无法读取备份文件，请检查文件权限")
        } catch (e: FileNotFoundException) {
            Timber.e(e, "恢复失败: 文件未找到")
            RestoreResult(false, "恢复失败：备份文件未找到，文件可能已被移动或删除")
        } catch (e: IOException) {
            Timber.e(e, "恢复失败: IO异常")
            RestoreResult(false, "恢复失败：读取备份文件时出错，文件可能已损坏")
        } catch (e: Exception) {
            Timber.e(e, "恢复失败: 未知异常")
            RestoreResult(false, "恢复失败：${e.message}")
        }
    }

    /**
     * 检查备份文件是否有效（不实际恢复数据）
     * @param backupUri 备份文件URI
     */
    suspend fun validateBackupFile(backupUri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(backupUri)?.use { inputStream ->
                BufferedInputStream(inputStream).use { buffered ->
                    ZipInputStream(buffered).use { zipIn ->
                        var entryCount = 0
                        var hasMeta = false
                        var entry: ZipEntry? = zipIn.nextEntry
                        while (entry != null) {
                            if (entry.name == META_FILE) {
                                val metaBytes = zipIn.readBytes()
                                val meta = String(metaBytes, Charsets.UTF_8)
                                if (META_SIGNATURE == meta) hasMeta = true
                            } else {
                                entryCount++
                            }
                            zipIn.closeEntry()
                            entry = zipIn.nextEntry
                        }
                        return@use hasMeta || entryCount > 0
                    }
                }
            } ?: false
        } catch (e: Exception) {
            Timber.e(e, "备份文件验证失败")
            false
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

    /**
     * 获取URI对应文件的大小
     */
    private fun getFileSize(uri: Uri): Long? {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val sizeIndex = cursor.getColumnIndex(
                        android.provider.OpenableColumns.SIZE
                    )
                    if (sizeIndex >= 0) cursor.getLong(sizeIndex) else null
                } else null
            }
        } catch (e: Exception) {
            Timber.w(e, "无法获取文件大小")
            null
        }
    }

    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
            else -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        }
    }
}
