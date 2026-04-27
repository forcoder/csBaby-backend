package com.csbaby.kefu.data.local

import android.content.Context
import android.net.Uri
import androidx.sqlite.db.SimpleSQLiteQuery
import com.csbaby.kefu.data.local.dao.*
import com.csbaby.kefu.data.local.entity.*
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
 * 支持两种格式：
 * 1. ZIP 压缩包（旧格式，整体备份/恢复）
 * 2. Excel 文件（新格式，按功能模块导出/恢复）
 */
@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appConfigDao: AppConfigDao,
    private val keywordRuleDao: KeywordRuleDao,
    private val scenarioDao: ScenarioDao,
    private val aiModelConfigDao: AIModelConfigDao,
    private val userStyleProfileDao: UserStyleProfileDao,
    private val replyHistoryDao: ReplyHistoryDao,
    private val messageBlacklistDao: MessageBlacklistDao
) {
    companion object {
        private const val TAG = "BackupManager"
        private const val BACKUP_FILE_NAME = "kefu_backup.zip"
        private const val DATASTORE_DIR = "datastore"
        private const val DATABASE_DIR = "databases"
        private const val MIN_BACKUP_SIZE = 50L // 最小备份大小 50 bytes
        private const val META_FILE = ".backup_meta"
        private const val META_SIGNATURE = "CSBABY_BACKUP_V1"

        // 功能模块标识
        const val MODULE_APP_CONFIG = "app_config"
        const val MODULE_KEYWORD_RULES = "keyword_rules"
        const val MODULE_SCENARIOS = "scenarios"
        const val MODULE_AI_MODELS = "ai_models"
        const val MODULE_STYLE_PROFILES = "style_profiles"
        const val MODULE_REPLY_HISTORY = "reply_history"
        const val MODULE_MESSAGE_BLACKLIST = "message_blacklist"

        val ALL_MODULES = listOf(
            MODULE_APP_CONFIG, MODULE_KEYWORD_RULES, MODULE_SCENARIOS,
            MODULE_AI_MODELS, MODULE_STYLE_PROFILES, MODULE_REPLY_HISTORY,
            MODULE_MESSAGE_BLACKLIST
        )
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

    // ==================== Excel 导出/导入 ====================

    data class ExcelModuleResult(
        val success: Boolean,
        val message: String,
        val rowCount: Int = 0
    )

    /**
     * 将指定功能模块导出为 Excel 文件
     * @param module 功能模块标识
     * @param outputUri 输出文件 URI
     */
    suspend fun exportModuleToExcel(module: String, outputUri: Uri): ExcelModuleResult = withContext(Dispatchers.IO) {
        try {
            val sheets = when (module) {
                MODULE_APP_CONFIG -> listOf(buildAppConfigSheet())
                MODULE_KEYWORD_RULES -> listOf(buildKeywordRulesSheet())
                MODULE_SCENARIOS -> buildScenariosSheets()
                MODULE_AI_MODELS -> listOf(buildAIModelsSheet())
                MODULE_STYLE_PROFILES -> listOf(buildStyleProfilesSheet())
                MODULE_REPLY_HISTORY -> listOf(buildReplyHistorySheet())
                MODULE_MESSAGE_BLACKLIST -> listOf(buildMessageBlacklistSheet())
                else -> return@withContext ExcelModuleResult(false, "未知的功能模块: $module")
            }

            val totalRows = sheets.sumOf { it.rows.size - 1 } // 减去表头行

            context.contentResolver.openOutputStream(outputUri)?.use { outputStream ->
                ExcelUtils.writeXlsx(sheets, outputStream)
            } ?: return@withContext ExcelModuleResult(false, "无法创建文件，请检查存储权限")

            Timber.i("导出模块 $module 完成，共 $totalRows 行数据")
            ExcelModuleResult(true, "导出完成：共 $totalRows 行数据", totalRows)
        } catch (e: Exception) {
            Timber.e(e, "导出模块 $module 失败")
            ExcelModuleResult(false, "导出失败：${e.message}")
        }
    }

    /**
     * 将所有功能模块导出到单个 xlsx 文件（每个模块一个 Sheet）
     * @param outputUri 输出文件 URI
     */
    suspend fun exportAllToExcel(outputUri: Uri): ExcelModuleResult = withContext(Dispatchers.IO) {
        try {
            val allSheets = mutableListOf<ExcelUtils.ExcelSheet>()
            allSheets.add(buildAppConfigSheet())
            allSheets.add(buildKeywordRulesSheet())
            allSheets.addAll(buildScenariosSheets())
            allSheets.add(buildAIModelsSheet())
            allSheets.add(buildStyleProfilesSheet())
            allSheets.add(buildReplyHistorySheet())
            allSheets.add(buildMessageBlacklistSheet())

            val totalRows = allSheets.sumOf { (it.rows.size - 1).coerceAtLeast(0) }

            context.contentResolver.openOutputStream(outputUri)?.use { outputStream ->
                ExcelUtils.writeXlsx(allSheets, outputStream)
            } ?: return@withContext ExcelModuleResult(false, "无法创建文件，请检查存储权限")

            Timber.i("全部导出完成：${allSheets.size} 个 Sheet，共 $totalRows 行")
            ExcelModuleResult(true, "导出完成：${allSheets.size} 个功能模块，共 $totalRows 行数据", totalRows)
        } catch (e: Exception) {
            Timber.e(e, "全部导出失败")
            ExcelModuleResult(false, "导出失败：${e.message}")
        }
    }

    /**
     * 从 Excel 文件恢复指定功能模块
     * @param module 功能模块标识
     * @param inputUri 输入文件 URI
     */
    suspend fun importModuleFromExcel(module: String, inputUri: Uri): ExcelModuleResult = withContext(Dispatchers.IO) {
        try {
            val data = context.contentResolver.openInputStream(inputUri)?.use { inputStream ->
                parseExcelSheets(inputStream)
            } ?: return@withContext ExcelModuleResult(false, "无法读取文件")

            if (data.isEmpty() || data[0].isEmpty()) {
                return@withContext ExcelModuleResult(false, "Excel 文件为空")
            }

            val rows = data[0] // 取第一个 Sheet
            val result = when (module) {
                MODULE_APP_CONFIG -> importAppConfigs(rows)
                MODULE_KEYWORD_RULES -> importKeywordRules(rows)
                MODULE_SCENARIOS -> importScenarios(rows)
                MODULE_AI_MODELS -> importAIModels(rows)
                MODULE_STYLE_PROFILES -> importStyleProfiles(rows)
                MODULE_REPLY_HISTORY -> importReplyHistory(rows)
                MODULE_MESSAGE_BLACKLIST -> importMessageBlacklist(rows)
                else -> return@withContext ExcelModuleResult(false, "未知的功能模块: $module")
            }

            Timber.i("导入模块 $module 完成：${result.message}")
            result
        } catch (e: Exception) {
            Timber.e(e, "导入模块 $module 失败")
            ExcelModuleResult(false, "导入失败：${e.message}")
        }
    }

    // ==================== 导出 Sheet 构建 ====================

    private suspend fun buildAppConfigSheet(): ExcelUtils.ExcelSheet {
        val apps = appConfigDao.getAllAppsList()
        val header = listOf("packageName", "appName", "iconUri", "isMonitored", "createdAt", "lastUsed")
        val rows = mutableListOf(header)
        apps.forEach { app ->
            rows.add(listOf(
                app.packageName, app.appName, app.iconUri ?: "",
                app.isMonitored.toString(), app.createdAt.toString(), app.lastUsed.toString()
            ))
        }
        return ExcelUtils.ExcelSheet("应用配置", rows)
    }

    private suspend fun buildKeywordRulesSheet(): ExcelUtils.ExcelSheet {
        val rules = keywordRuleDao.getAllRulesList()
        val header = listOf("id", "keyword", "matchType", "replyTemplate", "category",
            "targetType", "targetNamesJson", "priority", "enabled", "createdAt", "updatedAt")
        val rows = mutableListOf(header)
        rules.forEach { rule ->
            rows.add(listOf(
                rule.id.toString(), rule.keyword, rule.matchType, rule.replyTemplate,
                rule.category, rule.targetType, rule.targetNamesJson, rule.priority.toString(),
                rule.enabled.toString(), rule.createdAt.toString(), rule.updatedAt.toString()
            ))
        }
        return ExcelUtils.ExcelSheet("关键词规则", rows)
    }

    private suspend fun buildScenariosSheets(): List<ExcelUtils.ExcelSheet> {
        val scenarios = scenarioDao.getAllScenariosList()
        val scenarioHeader = listOf("id", "name", "type", "targetId", "description", "createdAt")
        val scenarioRows = mutableListOf(scenarioHeader)
        val relationRows = mutableListOf(listOf("ruleId", "scenarioId"))

        scenarios.forEach { s ->
            scenarioRows.add(listOf(
                s.id.toString(), s.name, s.type, s.targetId ?: "",
                s.description ?: "", s.createdAt.toString()
            ))
            // 获取关联的规则
            val ruleIds = scenarioDao.getRuleIdsForScenario(s.id)
            ruleIds.forEach { ruleId ->
                relationRows.add(listOf(ruleId.toString(), s.id.toString()))
            }
        }

        return listOf(
            ExcelUtils.ExcelSheet("场景配置", scenarioRows),
            ExcelUtils.ExcelSheet("场景规则关联", relationRows)
        )
    }

    private suspend fun buildAIModelsSheet(): ExcelUtils.ExcelSheet {
        val models = aiModelConfigDao.getAllModelsList()
        val header = listOf("id", "modelType", "modelName", "model", "apiKey", "apiEndpoint",
            "temperature", "maxTokens", "isDefault", "isEnabled", "monthlyCost", "lastUsed", "createdAt")
        val rows = mutableListOf(header)
        models.forEach { m ->
            rows.add(listOf(
                m.id.toString(), m.modelType, m.modelName, m.model, m.apiKey,
                m.apiEndpoint, m.temperature.toString(), m.maxTokens.toString(),
                m.isDefault.toString(), m.isEnabled.toString(), m.monthlyCost.toString(),
                m.lastUsed.toString(), m.createdAt.toString()
            ))
        }
        return ExcelUtils.ExcelSheet("AI模型配置", rows)
    }

    private suspend fun buildStyleProfilesSheet(): ExcelUtils.ExcelSheet {
        val profiles = userStyleProfileDao.getAllProfiles()
        // Flow-based, collect the first emission
        var result: ExcelUtils.ExcelSheet? = null
        profiles.collect { list ->
            if (result == null) {
                val header = listOf("userId", "formalityLevel", "enthusiasmLevel", "professionalismLevel",
                    "wordCountPreference", "commonPhrases", "avoidPhrases", "learningSamples",
                    "accuracyScore", "lastTrained", "createdAt")
                val rows = mutableListOf(header)
                list.forEach { p ->
                    rows.add(listOf(
                        p.userId, p.formalityLevel.toString(), p.enthusiasmLevel.toString(),
                        p.professionalismLevel.toString(), p.wordCountPreference.toString(),
                        p.commonPhrases, p.avoidPhrases, p.learningSamples.toString(),
                        p.accuracyScore.toString(), p.lastTrained.toString(), p.createdAt.toString()
                    ))
                }
                result = ExcelUtils.ExcelSheet("风格画像", rows)
            }
        }
        return result ?: ExcelUtils.ExcelSheet("风格画像", listOf(
            listOf("userId", "formalityLevel", "enthusiasmLevel", "professionalismLevel",
                "wordCountPreference", "commonPhrases", "avoidPhrases", "learningSamples",
                "accuracyScore", "lastTrained", "createdAt")
        ))
    }

    private suspend fun buildReplyHistorySheet(): ExcelUtils.ExcelSheet {
        val replies = replyHistoryDao.getAllReplies()
        val header = listOf("id", "sourceApp", "originalMessage", "generatedReply", "finalReply",
            "ruleMatchedId", "modelUsedId", "styleApplied", "sendTime", "modified", "featureKey", "variantId")
        val rows = mutableListOf(header)
        replies.forEach { r ->
            rows.add(listOf(
                r.id.toString(), r.sourceApp, r.originalMessage, r.generatedReply,
                r.finalReply, r.ruleMatchedId?.toString() ?: "", r.modelUsedId?.toString() ?: "",
                r.styleApplied.toString(), r.sendTime.toString(), r.modified.toString(),
                r.featureKey ?: "", r.variantId?.toString() ?: ""
            ))
        }
        return ExcelUtils.ExcelSheet("聊天记录", rows)
    }

    private suspend fun buildMessageBlacklistSheet(): ExcelUtils.ExcelSheet {
        val items = messageBlacklistDao.getAllBlacklist()
        val header = listOf("id", "type", "value", "description", "packageName", "createdAt", "isEnabled")
        val rows = mutableListOf(header)
        items.forEach { item ->
            rows.add(listOf(
                item.id.toString(), item.type, item.value, item.description,
                item.packageName ?: "", item.createdAt.toString(), item.isEnabled.toString()
            ))
        }
        return ExcelUtils.ExcelSheet("消息黑名单", rows)
    }

    // ==================== Excel 导入解析 ====================

    /**
     * 解析 Excel 文件的所有 Sheet（复用 KnowledgeBaseManager 中的解析思路）
     */
    private fun parseExcelSheets(inputStream: InputStream): List<List<List<String>>> {
        return try {
            val sheetRows = mutableListOf<List<List<String>>>()
            var sharedStrings = emptyList<String>()
            val worksheetBytes = mutableListOf<ByteArray>()

            ZipInputStream(BufferedInputStream(inputStream)).use { zipIn ->
                var entry = zipIn.nextEntry
                while (entry != null) {
                    val bytes = zipIn.readBytes()
                    when {
                        entry.name == "xl/sharedStrings.xml" -> {
                            sharedStrings = parseSharedStrings(bytes)
                        }
                        entry.name.startsWith("xl/worksheets/") && entry.name.endsWith(".xml") -> {
                            worksheetBytes.add(bytes)
                        }
                    }
                    zipIn.closeEntry()
                    entry = zipIn.nextEntry
                }
            }

            worksheetBytes.forEach { bytes ->
                val rows = parseWorksheetRows(bytes, sharedStrings)
                if (rows.isNotEmpty()) sheetRows.add(rows)
            }
            sheetRows
        } catch (e: Exception) {
            Timber.e(e, "解析 Excel 失败")
            emptyList()
        }
    }

    private fun parseSharedStrings(bytes: ByteArray): List<String> {
        val parser = android.util.Xml.newPullParser().apply {
            setInput(ByteArrayInputStream(bytes), Charsets.UTF_8.name())
        }
        val result = mutableListOf<String>()
        var currentText: StringBuilder? = null
        var eventType = parser.eventType
        while (eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                org.xmlpull.v1.XmlPullParser.START_TAG -> {
                    if (parser.name == "si") currentText = StringBuilder()
                }
                org.xmlpull.v1.XmlPullParser.TEXT -> {
                    currentText?.append(parser.text.orEmpty())
                }
                org.xmlpull.v1.XmlPullParser.END_TAG -> {
                    if (parser.name == "si") {
                        result += currentText?.toString().orEmpty()
                        currentText = null
                    }
                }
            }
            eventType = parser.next()
        }
        return result
    }

    private fun parseWorksheetRows(bytes: ByteArray, sharedStrings: List<String>): List<List<String>> {
        val parser = android.util.Xml.newPullParser().apply {
            setInput(ByteArrayInputStream(bytes), Charsets.UTF_8.name())
        }
        val rows = mutableListOf<List<String>>()
        var currentRow = linkedMapOf<Int, String>()
        var currentCellColumn = -1
        var currentCellType: String? = null
        var currentCellValue = StringBuilder()
        var readingValueTag = false

        var eventType = parser.eventType
        while (eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                org.xmlpull.v1.XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "row" -> currentRow = linkedMapOf()
                        "c" -> {
                            currentCellColumn = columnToIndex(parser.getAttributeValue(null, "r"))
                            currentCellType = parser.getAttributeValue(null, "t")
                            currentCellValue = StringBuilder()
                        }
                        "v" -> readingValueTag = true
                        "t" -> if (currentCellType == "inlineStr") readingValueTag = true
                    }
                }
                org.xmlpull.v1.XmlPullParser.TEXT -> {
                    if (readingValueTag) currentCellValue.append(parser.text.orEmpty())
                }
                org.xmlpull.v1.XmlPullParser.END_TAG -> {
                    when (parser.name) {
                        "v", "t" -> readingValueTag = false
                        "c" -> {
                            val rawValue = currentCellValue.toString()
                            val value = when (currentCellType) {
                                "s" -> sharedStrings.getOrNull(rawValue.toIntOrNull() ?: -1).orEmpty()
                                "b" -> if (rawValue == "1") "true" else "false"
                                else -> rawValue
                            }
                            if (currentCellColumn >= 0) currentRow[currentCellColumn] = value
                            currentCellColumn = -1
                            currentCellType = null
                        }
                        "row" -> {
                            if (currentRow.isNotEmpty()) {
                                val maxCol = currentRow.keys.maxOrNull() ?: 0
                                val row = (0..maxCol).map { currentRow[it].orEmpty().trim() }
                                rows.add(row)
                            }
                        }
                    }
                }
            }
            eventType = parser.next()
        }
        return rows
    }

    private fun columnToIndex(cellRef: String?): Int {
        val col = cellRef.orEmpty().takeWhile { it.isLetter() }.uppercase()
        if (col.isBlank()) return -1
        var result = 0
        col.forEach { result = result * 26 + (it - 'A' + 1) }
        return result - 1
    }

    private fun getCell(row: List<String>, index: Int): String =
        if (index < row.size) row[index] else ""

    // ==================== 导入数据到数据库 ====================

    private suspend fun importAppConfigs(rows: List<List<String>>): ExcelModuleResult {
        if (rows.size <= 1) return ExcelModuleResult(true, "无数据", 0)
        var count = 0
        for (i in 1 until rows.size) {
            val row = rows[i]
            if (row.isEmpty()) continue
            try {
                appConfigDao.insertApp(
                    AppConfigEntity(
                        packageName = getCell(row, 0),
                        appName = getCell(row, 1),
                        iconUri = getCell(row, 2).ifEmpty { null },
                        isMonitored = getCell(row, 3).toBooleanStrictOrNull() ?: false,
                        createdAt = getCell(row, 4).toLongOrNull() ?: System.currentTimeMillis(),
                        lastUsed = getCell(row, 5).toLongOrNull() ?: System.currentTimeMillis()
                    )
                )
                count++
            } catch (e: Exception) {
                Timber.w(e, "导入应用配置第 $i 行失败")
            }
        }
        return ExcelModuleResult(true, "导入完成：$count 条应用配置", count)
    }

    private suspend fun importKeywordRules(rows: List<List<String>>): ExcelModuleResult {
        if (rows.size <= 1) return ExcelModuleResult(true, "无数据", 0)
        var count = 0
        for (i in 1 until rows.size) {
            val row = rows[i]
            if (row.isEmpty()) continue
            try {
                keywordRuleDao.insertRule(
                    KeywordRuleEntity(
                        id = getCell(row, 0).toLongOrNull() ?: 0,
                        keyword = getCell(row, 1),
                        matchType = getCell(row, 2),
                        replyTemplate = getCell(row, 3),
                        category = getCell(row, 4),
                        targetType = getCell(row, 5).ifEmpty { "ALL" },
                        targetNamesJson = getCell(row, 6).ifEmpty { "[]" },
                        priority = getCell(row, 7).toIntOrNull() ?: 0,
                        enabled = getCell(row, 8).toBooleanStrictOrNull() ?: true,
                        createdAt = getCell(row, 9).toLongOrNull() ?: System.currentTimeMillis(),
                        updatedAt = getCell(row, 10).toLongOrNull() ?: System.currentTimeMillis()
                    )
                )
                count++
            } catch (e: Exception) {
                Timber.w(e, "导入关键词规则第 $i 行失败")
            }
        }
        return ExcelModuleResult(true, "导入完成：$count 条关键词规则", count)
    }

    private suspend fun importScenarios(rows: List<List<String>>): ExcelModuleResult {
        if (rows.size <= 1) return ExcelModuleResult(true, "无数据", 0)
        var count = 0
        for (i in 1 until rows.size) {
            val row = rows[i]
            if (row.isEmpty()) continue
            try {
                scenarioDao.insertScenario(
                    ScenarioEntity(
                        id = getCell(row, 0).toLongOrNull() ?: 0,
                        name = getCell(row, 1),
                        type = getCell(row, 2),
                        targetId = getCell(row, 3).ifEmpty { null },
                        description = getCell(row, 4).ifEmpty { null },
                        createdAt = getCell(row, 5).toLongOrNull() ?: System.currentTimeMillis()
                    )
                )
                count++
            } catch (e: Exception) {
                Timber.w(e, "导入场景配置第 $i 行失败")
            }
        }
        return ExcelModuleResult(true, "导入完成：$count 条场景配置", count)
    }

    private suspend fun importAIModels(rows: List<List<String>>): ExcelModuleResult {
        if (rows.size <= 1) return ExcelModuleResult(true, "无数据", 0)
        var count = 0
        for (i in 1 until rows.size) {
            val row = rows[i]
            if (row.isEmpty()) continue
            try {
                aiModelConfigDao.insertModel(
                    AIModelConfigEntity(
                        id = getCell(row, 0).toLongOrNull() ?: 0,
                        modelType = getCell(row, 1),
                        modelName = getCell(row, 2),
                        model = getCell(row, 3),
                        apiKey = getCell(row, 4),
                        apiEndpoint = getCell(row, 5),
                        temperature = getCell(row, 6).toFloatOrNull() ?: 0.7f,
                        maxTokens = getCell(row, 7).toIntOrNull() ?: 1000,
                        isDefault = getCell(row, 8).toBooleanStrictOrNull() ?: false,
                        isEnabled = getCell(row, 9).toBooleanStrictOrNull() ?: true,
                        monthlyCost = getCell(row, 10).toDoubleOrNull() ?: 0.0,
                        lastUsed = getCell(row, 11).toLongOrNull() ?: System.currentTimeMillis(),
                        createdAt = getCell(row, 12).toLongOrNull() ?: System.currentTimeMillis()
                    )
                )
                count++
            } catch (e: Exception) {
                Timber.w(e, "导入AI模型第 $i 行失败")
            }
        }
        return ExcelModuleResult(true, "导入完成：$count 条AI模型配置", count)
    }

    private suspend fun importStyleProfiles(rows: List<List<String>>): ExcelModuleResult {
        if (rows.size <= 1) return ExcelModuleResult(true, "无数据", 0)
        var count = 0
        for (i in 1 until rows.size) {
            val row = rows[i]
            if (row.isEmpty()) continue
            try {
                userStyleProfileDao.insertProfile(
                    UserStyleProfileEntity(
                        userId = getCell(row, 0),
                        formalityLevel = getCell(row, 1).toFloatOrNull() ?: 0.5f,
                        enthusiasmLevel = getCell(row, 2).toFloatOrNull() ?: 0.5f,
                        professionalismLevel = getCell(row, 3).toFloatOrNull() ?: 0.5f,
                        wordCountPreference = getCell(row, 4).toIntOrNull() ?: 50,
                        commonPhrases = getCell(row, 5),
                        avoidPhrases = getCell(row, 6),
                        learningSamples = getCell(row, 7).toIntOrNull() ?: 0,
                        accuracyScore = getCell(row, 8).toFloatOrNull() ?: 0.0f,
                        lastTrained = getCell(row, 9).toLongOrNull() ?: System.currentTimeMillis(),
                        createdAt = getCell(row, 10).toLongOrNull() ?: System.currentTimeMillis()
                    )
                )
                count++
            } catch (e: Exception) {
                Timber.w(e, "导入风格画像第 $i 行失败")
            }
        }
        return ExcelModuleResult(true, "导入完成：$count 条风格画像", count)
    }

    private suspend fun importReplyHistory(rows: List<List<String>>): ExcelModuleResult {
        if (rows.size <= 1) return ExcelModuleResult(true, "无数据", 0)
        var count = 0
        for (i in 1 until rows.size) {
            val row = rows[i]
            if (row.isEmpty()) continue
            try {
                replyHistoryDao.insertReply(
                    ReplyHistoryEntity(
                        id = getCell(row, 0).toLongOrNull() ?: 0,
                        sourceApp = getCell(row, 1),
                        originalMessage = getCell(row, 2),
                        generatedReply = getCell(row, 3),
                        finalReply = getCell(row, 4),
                        ruleMatchedId = getCell(row, 5).ifEmpty { null }?.toLongOrNull(),
                        modelUsedId = getCell(row, 6).ifEmpty { null }?.toLongOrNull(),
                        styleApplied = getCell(row, 7).toBooleanStrictOrNull() ?: false,
                        sendTime = getCell(row, 8).toLongOrNull() ?: System.currentTimeMillis(),
                        modified = getCell(row, 9).toBooleanStrictOrNull() ?: false,
                        featureKey = getCell(row, 10).ifEmpty { null },
                        variantId = getCell(row, 11).ifEmpty { null }?.toLongOrNull()
                    )
                )
                count++
            } catch (e: Exception) {
                Timber.w(e, "导入聊天记录第 $i 行失败")
            }
        }
        return ExcelModuleResult(true, "导入完成：$count 条聊天记录", count)
    }

    private suspend fun importMessageBlacklist(rows: List<List<String>>): ExcelModuleResult {
        if (rows.size <= 1) return ExcelModuleResult(true, "无数据", 0)
        var count = 0
        for (i in 1 until rows.size) {
            val row = rows[i]
            if (row.isEmpty()) continue
            try {
                messageBlacklistDao.insert(
                    MessageBlacklistEntity(
                        id = getCell(row, 0).toLongOrNull() ?: 0,
                        type = getCell(row, 1),
                        value = getCell(row, 2),
                        description = getCell(row, 3),
                        packageName = getCell(row, 4).ifEmpty { null },
                        createdAt = getCell(row, 5).toLongOrNull() ?: System.currentTimeMillis(),
                        isEnabled = getCell(row, 6).toBooleanStrictOrNull() ?: true
                    )
                )
                count++
            } catch (e: Exception) {
                Timber.w(e, "导入消息黑名单第 $i 行失败")
            }
        }
        return ExcelModuleResult(true, "导入完成：$count 条消息黑名单", count)
    }
}
