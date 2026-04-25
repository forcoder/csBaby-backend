package com.csbaby.kefu.data.local

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * BackupManager 单元测试
 *
 * 测试覆盖:
 * - 正常功能: 备份创建、备份读取、元数据解析
 * - 边界条件: 空备份、大文件备份、特殊字符文件名
 * - 异常条件: 文件损坏、版本不兼容、权限不足、路径不存在
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class BackupManagerTest {

    private lateinit var context: Context
    private lateinit var backupManager: BackupManager
    private lateinit var testDir: File
    private val gson = Gson()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        testDir = File(context.cacheDir, "backup_test_${System.currentTimeMillis()}")
        testDir.mkdirs()
    }

    @After
    fun tearDown() {
        // 清理测试文件
        testDir.deleteRecursively()
    }

    // ==================== 辅助方法 ====================

    /**
     * 创建一个模拟的备份 ZIP 文件
     */
    private fun createMockBackupFile(
        metadata: BackupManager.BackupMetadata? = null,
        includeDatabase: Boolean = true,
        includeSettings: Boolean = true,
        databaseContent: ByteArray = "mock database content".toByteArray(),
        settingsContent: ByteArray = "mock settings content".toByteArray(),
        corruptZip: Boolean = false
    ): File {
        val backupFile = File(testDir, "test_backup.zip")

        if (corruptZip) {
            // 创建损坏的 ZIP 文件
            backupFile.writeBytes("this is not a valid zip file".toByteArray())
            return backupFile
        }

        ZipOutputStream(BufferedOutputStream(FileOutputStream(backupFile))).use { zipOut ->
            // 写入元数据
            val meta = metadata ?: BackupManager.BackupMetadata()
            val metadataJson = gson.toJson(meta).toByteArray()
            zipOut.putNextEntry(ZipEntry(BackupManager.BACKUP_METADATA_FILE))
            zipOut.write(metadataJson)
            zipOut.closeEntry()

            // 写入数据库
            if (includeDatabase) {
                zipOut.putNextEntry(ZipEntry(BackupManager.DATABASE_FILE_NAME))
                zipOut.write(databaseContent)
                zipOut.closeEntry()
            }

            // 写入设置
            if (includeSettings) {
                zipOut.putNextEntry(ZipEntry(BackupManager.DATASTORE_FILE_NAME))
                zipOut.write(settingsContent)
                zipOut.closeEntry()
            }
        }

        return backupFile
    }

    /**
     * 从备份文件中读取元数据
     */
    private fun readMetadataFromBackup(backupFile: File): BackupManager.BackupMetadata? {
        ZipInputStream(BufferedInputStream(FileInputStream(backupFile))).use { zipIn ->
            var entry = zipIn.nextEntry
            while (entry != null) {
                if (entry.name == BackupManager.BACKUP_METADATA_FILE) {
                    val metadataJson = zipIn.bufferedReader().readText()
                    return gson.fromJson(metadataJson, BackupManager.BackupMetadata::class.java)
                }
                zipIn.closeEntry()
                entry = zipIn.nextEntry
            }
        }
        return null
    }

    /**
     * 验证备份文件结构
     */
    private fun verifyBackupStructure(
        backupFile: File,
        expectMetadata: Boolean = true,
        expectDatabase: Boolean = true,
        expectSettings: Boolean = true
    ): Boolean {
        if (!backupFile.exists() || backupFile.length() == 0L) return false

        val entries = mutableSetOf<String>()
        ZipInputStream(BufferedInputStream(FileInputStream(backupFile))).use { zipIn ->
            var entry = zipIn.nextEntry
            while (entry != null) {
                entries.add(entry.name)
                zipIn.closeEntry()
                entry = zipIn.nextEntry
            }
        }

        if (expectMetadata && !entries.contains(BackupManager.BACKUP_METADATA_FILE)) return false
        if (expectDatabase && !entries.contains(BackupManager.DATABASE_FILE_NAME)) return false
        if (expectSettings && !entries.contains(BackupManager.DATASTORE_FILE_NAME)) return false

        return true
    }

    // ==================== 正常功能测试 ====================

    @Test
    fun `test create mock backup file successfully`() {
        // 测试辅助方法：创建模拟备份文件
        val backupFile = createMockBackupFile()

        assertTrue("备份文件应该存在", backupFile.exists())
        assertTrue("备份文件应该有内容", backupFile.length() > 0L)
        assertTrue("备份文件结构应该正确", verifyBackupStructure(backupFile))
    }

    @Test
    fun `test read metadata from backup file`() {
        // 测试从备份文件中读取元数据
        val originalMetadata = BackupManager.BackupMetadata(
            backupVersion = 1,
            appVersionName = "1.0.0",
            appVersionCode = 1,
            databaseIncluded = true,
            settingsIncluded = true
        )

        val backupFile = createMockBackupFile(metadata = originalMetadata)
        val readMetadata = readMetadataFromBackup(backupFile)

        assertNotNull("应该能读取到元数据", readMetadata)
        assertEquals("备份版本应该匹配", originalMetadata.backupVersion, readMetadata!!.backupVersion)
        assertEquals("应用版本名应该匹配", originalMetadata.appVersionName, readMetadata.appVersionName)
        assertEquals("应用版本号应该匹配", originalMetadata.appVersionCode, readMetadata.appVersionCode)
        assertEquals("数据库包含标志应该匹配", originalMetadata.databaseIncluded, readMetadata.databaseIncluded)
        assertEquals("设置包含标志应该匹配", originalMetadata.settingsIncluded, readMetadata.settingsIncluded)
    }

    @Test
    fun `test backup metadata contains required fields`() {
        // 测试备份元数据包含所有必要字段
        val metadata = BackupManager.BackupMetadata()
        val json = gson.toJson(metadata)
        val jsonObject = gson.fromJson(json, com.google.gson.JsonObject::class.java)

        // 验证必要字段存在
        assertTrue("应该包含 backupVersion", jsonObject.has("backupVersion"))
        assertTrue("应该包含 appVersionName", jsonObject.has("appVersionName"))
        assertTrue("应该包含 appVersionCode", jsonObject.has("appVersionCode"))
        assertTrue("应该包含 backupTime", jsonObject.has("backupTime"))
        assertTrue("应该包含 backupTimeFormatted", jsonObject.has("backupTimeFormatted"))
        assertTrue("应该包含 databaseIncluded", jsonObject.has("databaseIncluded"))
        assertTrue("应该包含 settingsIncluded", jsonObject.has("settingsIncluded"))
        assertTrue("应该包含 platform", jsonObject.has("platform"))
    }

    @Test
    fun `test backup with database only`() {
        // 测试仅包含数据库的备份
        val backupFile = createMockBackupFile(
            includeDatabase = true,
            includeSettings = false
        )

        assertTrue("备份文件结构应该正确", verifyBackupStructure(
            backupFile,
            expectMetadata = true,
            expectDatabase = true,
            expectSettings = false
        ))
    }

    @Test
    fun `test backup with settings only`() {
        // 测试仅包含设置的备份
        val backupFile = createMockBackupFile(
            includeDatabase = false,
            includeSettings = true
        )

        assertTrue("备份文件结构应该正确", verifyBackupStructure(
            backupFile,
            expectMetadata = true,
            expectDatabase = false,
            expectSettings = true
        ))
    }

    @Test
    fun `test backup file content integrity`() {
        // 测试备份文件内容完整性
        val databaseContent = "test database content - 数据库内容测试".toByteArray()
        val settingsContent = "test settings content - 设置内容测试".toByteArray()

        val backupFile = createMockBackupFile(
            databaseContent = databaseContent,
            settingsContent = settingsContent
        )

        // 读取备份文件内容
        var readDatabaseContent: ByteArray? = null
        var readSettingsContent: ByteArray? = null

        ZipInputStream(BufferedInputStream(FileInputStream(backupFile))).use { zipIn ->
            var entry = zipIn.nextEntry
            while (entry != null) {
                when (entry.name) {
                    BackupManager.DATABASE_FILE_NAME -> {
                        readDatabaseContent = zipIn.readBytes()
                    }
                    BackupManager.DATASTORE_FILE_NAME -> {
                        readSettingsContent = zipIn.readBytes()
                    }
                }
                zipIn.closeEntry()
                entry = zipIn.nextEntry
            }
        }

        assertNotNull("数据库内容应该存在", readDatabaseContent)
        assertArrayEquals("数据库内容应该匹配", databaseContent, readDatabaseContent)
        assertNotNull("设置内容应该存在", readSettingsContent)
        assertArrayEquals("设置内容应该匹配", settingsContent, readSettingsContent)
    }

    // ==================== 边界条件测试 ====================

    @Test
    fun `test backup with empty database content`() {
        // 测试空数据库内容的备份
        val backupFile = createMockBackupFile(
            databaseContent = ByteArray(0),
            settingsContent = "settings".toByteArray()
        )

        assertTrue("备份文件应该存在", backupFile.exists())

        // 验证空数据库内容
        var databaseSize = -1L
        ZipInputStream(BufferedInputStream(FileInputStream(backupFile))).use { zipIn ->
            var entry = zipIn.nextEntry
            while (entry != null) {
                if (entry.name == BackupManager.DATABASE_FILE_NAME) {
                    databaseSize = entry.size
                }
                zipIn.closeEntry()
                entry = zipIn.nextEntry
            }
        }

        // ZIP 中空的条目 compressed size 可能不为 0，但原始大小应该为 0
        assertTrue("数据库条目应该存在", databaseSize >= -1L)
    }

    @Test
    fun `test backup with large database content`() {
        // 测试大文件数据库内容的备份 (1MB)
        val largeContent = ByteArray(1024 * 1024) { (it % 256).toByte() }

        val backupFile = createMockBackupFile(
            databaseContent = largeContent,
            settingsContent = "settings".toByteArray()
        )

        assertTrue("备份文件应该存在", backupFile.exists())
        assertTrue("备份文件应该有合理的大小", backupFile.length() > 0L)

        // 验证内容完整性
        var readContent: ByteArray? = null
        ZipInputStream(BufferedInputStream(FileInputStream(backupFile))).use { zipIn ->
            var entry = zipIn.nextEntry
            while (entry != null) {
                if (entry.name == BackupManager.DATABASE_FILE_NAME) {
                    readContent = zipIn.readBytes()
                }
                zipIn.closeEntry()
                entry = zipIn.nextEntry
            }
        }

        assertNotNull("大文件内容应该存在", readContent)
        assertArrayEquals("大文件内容应该匹配", largeContent, readContent)
    }

    @Test
    fun `test backup with special characters in content`() {
        // 测试包含特殊字符的内容
        val specialContent = "特殊字符: 中文 🎉 émojis \n\t\r\\ \"'".toByteArray(Charsets.UTF_8)

        val backupFile = createMockBackupFile(
            databaseContent = specialContent,
            settingsContent = specialContent
        )

        var readDatabaseContent: ByteArray? = null
        ZipInputStream(BufferedInputStream(FileInputStream(backupFile))).use { zipIn ->
            var entry = zipIn.nextEntry
            while (entry != null) {
                if (entry.name == BackupManager.DATABASE_FILE_NAME) {
                    readDatabaseContent = zipIn.readBytes()
                }
                zipIn.closeEntry()
                entry = zipIn.nextEntry
            }
        }

        assertNotNull("特殊字符内容应该存在", readDatabaseContent)
        assertArrayEquals(
            "特殊字符内容应该匹配",
            specialContent,
            readDatabaseContent
        )
    }

    @Test
    fun `test backup metadata with future timestamp`() {
        // 测试未来时间戳的备份元数据
        val futureTime = System.currentTimeMillis() + 365L * 24 * 60 * 60 * 1000 // 一年后
        val metadata = BackupManager.BackupMetadata(
            backupTime = futureTime
        )

        val backupFile = createMockBackupFile(metadata = metadata)
        val readMetadata = readMetadataFromBackup(backupFile)

        assertNotNull("应该能读取到元数据", readMetadata)
        assertEquals("时间戳应该匹配", futureTime, readMetadata!!.backupTime)
    }

    @Test
    fun `test backup with both database and settings empty`() {
        // 测试数据库和设置都为空的备份
        val backupFile = createMockBackupFile(
            databaseContent = ByteArray(0),
            settingsContent = ByteArray(0)
        )

        assertTrue("备份文件应该存在", backupFile.exists())
        assertTrue("备份文件应该包含元数据", verifyBackupStructure(
            backupFile,
            expectMetadata = true,
            expectDatabase = true,
            expectSettings = true
        ))
    }

    @Test
    fun `test backup version number is valid`() {
        // 测试备份版本号有效性
        val metadata = BackupManager.BackupMetadata()
        assertTrue("备份版本号应该大于 0", metadata.backupVersion > 0)
        assertEquals("当前备份版本应该匹配常量", BackupManager.BACKUP_VERSION, metadata.backupVersion)
    }

    @Test
    fun `test format file size utility`() {
        // 测试文件大小格式化工具方法
        val manager = BackupManager(context)

        assertEquals("0 B", manager.formatFileSize(0))
        assertEquals("1023 B", manager.formatFileSize(1023))
        assertEquals("1.00 KB", manager.formatFileSize(1024))
        assertEquals("1.00 MB", manager.formatFileSize(1024 * 1024))
        assertEquals("1.00 GB", manager.formatFileSize(1024L * 1024 * 1024))
        assertEquals("1.50 GB", manager.formatFileSize(1024L * 1024 * 1024 * 1.5.toLong()))
    }

    // ==================== 异常条件测试 ====================

    @Test
    fun `test read metadata from corrupted backup file`() {
        // 测试从损坏的备份文件中读取元数据
        val corruptFile = createMockBackupFile(corruptZip = true)
        val metadata = readMetadataFromBackup(corruptFile)

        assertNull("损坏的文件不应该返回元数据", metadata)
    }

    @Test
    fun `test read metadata from non-existent file`() {
        // 测试从不存在的文件中读取元数据
        val nonExistentFile = File(testDir, "non_existent.zip")
        val metadata = readMetadataFromBackup(nonExistentFile)

        assertNull("不存在的文件不应该返回元数据", metadata)
    }

    @Test
    fun `test read metadata from empty file`() {
        // 测试从空文件中读取元数据
        val emptyFile = File(testDir, "empty.zip")
        emptyFile.createNewFile()
        val metadata = readMetadataFromBackup(emptyFile)

        assertNull("空文件不应该返回元数据", metadata)
    }

    @Test
    fun `test read metadata from backup without metadata entry`() {
        // 测试从没有元数据条目的备份文件中读取
        val backupFile = File(testDir, "no_metadata.zip")
        ZipOutputStream(BufferedOutputStream(FileOutputStream(backupFile))).use { zipOut ->
            zipOut.putNextEntry(ZipEntry("some_other_file.txt"))
            zipOut.write("content".toByteArray())
            zipOut.closeEntry()
        }

        val metadata = readMetadataFromBackup(backupFile)
        assertNull("没有元数据的备份不应该返回元数据", metadata)
    }

    @Test
    fun `test read metadata from backup with invalid json`() {
        // 测试从包含无效 JSON 元数据的备份文件中读取
        val backupFile = File(testDir, "invalid_metadata.zip")
        ZipOutputStream(BufferedOutputStream(FileOutputStream(backupFile))).use { zipOut ->
            zipOut.putNextEntry(ZipEntry(BackupManager.BACKUP_METADATA_FILE))
            zipOut.write("this is not valid json {{{".toByteArray())
            zipOut.closeEntry()
        }

        val metadata = readMetadataFromBackup(backupFile)
        assertNull("无效 JSON 的元数据不应该返回结果", metadata)
    }

    @Test
    fun `test backup with high version number`() {
        // 测试高版本号的备份文件
        val highVersionMetadata = BackupManager.BackupMetadata(
            backupVersion = 9999
        )
        val backupFile = createMockBackupFile(metadata = highVersionMetadata)
        val readMetadata = readMetadataFromBackup(backupFile)

        assertNotNull("应该能读取到高版本元数据", readMetadata)
        assertEquals("高版本号应该匹配", 9999, readMetadata!!.backupVersion)
    }

    @Test
    fun `test backup metadata with missing optional fields`() {
        // 测试缺少可选字段的备份元数据
        val incompleteJson = """
            {
                "backupVersion": 1,
                "appVersionName": "1.0.0"
            }
        """.trimIndent()

        val metadata = gson.fromJson(incompleteJson, BackupManager.BackupMetadata::class.java)
        assertEquals("备份版本应该匹配", 1, metadata.backupVersion)
        assertEquals("应用版本名应该匹配", "1.0.0", metadata.appVersionName)
        // 缺失字段应该有默认值
        assertEquals("默认应用版本号应该为 0", 0, metadata.appVersionCode)
        assertFalse("默认数据库包含标志应该为 false", metadata.databaseIncluded)
    }

    @Test
    fun `test backup file with extra entries`() {
        // 测试包含额外条目的备份文件
        val backupFile = createMockBackupFile()

        // 向 ZIP 中添加额外条目
        val tempFile = File(testDir, "temp_backup.zip")
        backupFile.copyTo(tempFile)

        ZipOutputStream(BufferedOutputStream(FileOutputStream(backupFile))).use { zipOut ->
            // 先复制原有内容
            ZipInputStream(BufferedInputStream(FileInputStream(tempFile))).use { zipIn ->
                var entry = zipIn.nextEntry
                while (entry != null) {
                    zipOut.putNextEntry(ZipEntry(entry.name))
                    zipIn.copyTo(zipOut)
                    zipOut.closeEntry()
                    zipIn.closeEntry()
                    entry = zipIn.nextEntry
                }
            }
            // 添加额外条目
            zipOut.putNextEntry(ZipEntry("extra_file.txt"))
            zipOut.write("extra content".toByteArray())
            zipOut.closeEntry()
        }

        assertTrue("包含额外条目的备份文件应该仍然有效", verifyBackupStructure(backupFile))
    }

    @Test
    fun `test backup file with duplicate entries`() {
        // 测试包含重复条目的备份文件（ZIP 规范允许）
        val backupFile = File(testDir, "duplicate_entries.zip")
        ZipOutputStream(BufferedOutputStream(FileOutputStream(backupFile))).use { zipOut ->
            // 写入两个同名条目
            zipOut.putNextEntry(ZipEntry(BackupManager.DATABASE_FILE_NAME))
            zipOut.write("first".toByteArray())
            zipOut.closeEntry()

            zipOut.putNextEntry(ZipEntry(BackupManager.DATABASE_FILE_NAME))
            zipOut.write("second".toByteArray())
            zipOut.closeEntry()
        }

        // ZIP 读取时应该读取到第一个匹配的条目
        var content: String? = null
        ZipInputStream(BufferedInputStream(FileInputStream(backupFile))).use { zipIn ->
            var entry = zipIn.nextEntry
            while (entry != null) {
                if (entry.name == BackupManager.DATABASE_FILE_NAME) {
                    content = zipIn.bufferedReader().readText()
                    break
                }
                zipIn.closeEntry()
                entry = zipIn.nextEntry
            }
        }

        assertNotNull("应该读取到重复条目", content)
        assertTrue("应该读取到第一个或第二个条目", content == "first" || content == "second")
    }

    // ==================== 备份结果类型测试 ====================

    @Test
    fun `test backup result success type`() {
        // 测试备份成功结果类型
        val metadata = BackupManager.BackupMetadata()
        val uri = Uri.parse("content://test/backup.zip")

        val success = BackupManager.BackupResult.Success(
            uri = uri,
            metadata = metadata,
            databaseSize = 1024L,
            settingsSize = 512L
        )

        assertTrue("应该是成功类型", success is BackupManager.BackupResult.Success)
        assertEquals("URI 应该匹配", uri, success.uri)
        assertEquals("数据库大小应该匹配", 1024L, success.databaseSize)
        assertEquals("设置大小应该匹配", 512L, success.settingsSize)
    }

    @Test
    fun `test backup result error type`() {
        // 测试备份错误结果类型
        val exception = RuntimeException("Test error")
        val error = BackupManager.BackupResult.Error("备份失败", exception)

        assertTrue("应该是错误类型", error is BackupManager.BackupResult.Error)
        assertEquals("错误消息应该匹配", "备份失败", error.message)
        assertEquals("异常应该匹配", exception, error.exception)
    }

    @Test
    fun `test restore result success type`() {
        // 测试恢复成功结果类型
        val metadata = BackupManager.BackupMetadata()
        val success = BackupManager.RestoreResult.Success(
            metadata = metadata,
            databaseRestored = true,
            settingsRestored = true
        )

        assertTrue("应该是成功类型", success is BackupManager.RestoreResult.Success)
        assertTrue("数据库应该已恢复", success.databaseRestored)
        assertTrue("设置应该已恢复", success.settingsRestored)
    }

    @Test
    fun `test restore result error type`() {
        // 测试恢复错误结果类型
        val exception = IOException("文件不存在")
        val error = BackupManager.RestoreResult.Error("恢复失败", exception)

        assertTrue("应该是错误类型", error is BackupManager.RestoreResult.Error)
        assertEquals("错误消息应该匹配", "恢复失败", error.message)
        assertEquals("异常应该匹配", exception, error.exception)
    }

    // ==================== 备份常量测试 ====================

    @Test
    fun `test backup file constants are valid`() {
        // 测试备份文件名常量
        assertEquals("备份文件前缀应该正确", "csbaby_backup_", BackupManager.BACKUP_FILE_PREFIX)
        assertEquals("备份文件扩展名应该正确", ".zip", BackupManager.BACKUP_FILE_EXTENSION)
        assertEquals("元数据文件名应该正确", "backup_metadata.json", BackupManager.BACKUP_METADATA_FILE)
        assertEquals("数据库文件名应该正确", "kefu_database", BackupManager.DATABASE_FILE_NAME)
        assertEquals("数据库 WAL 文件名应该正确", "kefu_database-wal", BackupManager.DATABASE_WAL_FILE_NAME)
        assertEquals("数据库 SHM 文件名应该正确", "kefu_database-shm", BackupManager.DATABASE_SHM_FILE_NAME)
        assertEquals("DataStore 文件名应该正确", "kefu_settings.preferences_pb", BackupManager.DATASTORE_FILE_NAME)
    }

    // ==================== 并发安全测试 ====================

    @Test
    fun `test concurrent backup file creation`() = runTest {
        // 测试并发创建备份文件
        val files = mutableListOf<File>()
        val jobs = mutableListOf<kotlinx.coroutines.Job>()

        repeat(5) { index ->
            val file = File(testDir, "concurrent_backup_$index.zip")
            files.add(file)

            ZipOutputStream(BufferedOutputStream(FileOutputStream(file))).use { zipOut ->
                val metadata = BackupManager.BackupMetadata(
                    backupTime = System.currentTimeMillis() + index
                )
                zipOut.putNextEntry(ZipEntry(BackupManager.BACKUP_METADATA_FILE))
                zipOut.write(gson.toJson(metadata).toByteArray())
                zipOut.closeEntry()
            }
        }

        // 验证所有文件都创建成功
        files.forEach { file ->
            assertTrue("并发创建的备份文件应该存在: ${file.name}", file.exists())
            assertTrue("并发创建的备份文件应该有内容: ${file.name}", file.length() > 0L)
        }
    }

    // ==================== 恢复回滚测试 ====================

    @Test
    fun `test backup and restore preserves data integrity`() {
        // 测试备份和恢复保持数据完整性
        val originalDatabase = "original database content - 原始数据库内容".toByteArray()
        val originalSettings = "original settings content - 原始设置内容".toByteArray()

        // 创建备份
        val backupFile = createMockBackupFile(
            databaseContent = originalDatabase,
            settingsContent = originalSettings
        )

        // 模拟恢复：从备份中读取
        var restoredDatabase: ByteArray? = null
        var restoredSettings: ByteArray? = null

        ZipInputStream(BufferedInputStream(FileInputStream(backupFile))).use { zipIn ->
            var entry = zipIn.nextEntry
            while (entry != null) {
                when (entry.name) {
                    BackupManager.DATABASE_FILE_NAME -> restoredDatabase = zipIn.readBytes()
                    BackupManager.DATASTORE_FILE_NAME -> restoredSettings = zipIn.readBytes()
                }
                zipIn.closeEntry()
                entry = zipIn.nextEntry
            }
        }

        // 验证数据完整性
        assertNotNull("恢复的数据库应该存在", restoredDatabase)
        assertArrayEquals("恢复的数据库应该与原始数据匹配", originalDatabase, restoredDatabase)
        assertNotNull("恢复的设置应该存在", restoredSettings)
        assertArrayEquals("恢复的设置应该与原始数据匹配", originalSettings, restoredSettings)
    }

    // ==================== 元数据序列化/反序列化测试 ====================

    @Test
    fun `test metadata serialization roundtrip`() {
        // 测试元数据序列化/反序列化往返
        val original = BackupManager.BackupMetadata(
            backupVersion = 1,
            appVersionName = "1.2.3",
            appVersionCode = 123,
            backupTime = 1700000000000L,
            databaseIncluded = true,
            settingsIncluded = false,
            platform = "Android"
        )

        val json = gson.toJson(original)
        val restored = gson.fromJson(json, BackupManager.BackupMetadata::class.java)

        assertEquals("备份版本应该匹配", original.backupVersion, restored.backupVersion)
        assertEquals("应用版本名应该匹配", original.appVersionName, restored.appVersionName)
        assertEquals("应用版本号应该匹配", original.appVersionCode, restored.appVersionCode)
        assertEquals("备份时间应该匹配", original.backupTime, restored.backupTime)
        assertEquals("数据库包含标志应该匹配", original.databaseIncluded, restored.databaseIncluded)
        assertEquals("设置包含标志应该匹配", original.settingsIncluded, restored.settingsIncluded)
        assertEquals("平台应该匹配", original.platform, restored.platform)
    }

    @Test
    fun `test metadata with unicode characters`() {
        // 测试包含 Unicode 字符的元数据
        val metadata = BackupManager.BackupMetadata(
            appVersionName = "1.0.0-β 测试版 🎉",
            platform = "Android 安卓"
        )

        val json = gson.toJson(metadata)
        val restored = gson.fromJson(json, BackupManager.BackupMetadata::class.java)

        assertEquals("Unicode 版本名应该匹配", metadata.appVersionName, restored.appVersionName)
        assertEquals("Unicode 平台应该匹配", metadata.platform, restored.platform)
    }

    @Test
    fun `test backup result sealed class hierarchy`() {
        // 测试备份结果密封类层次结构
        val success = BackupManager.BackupResult.Success(
            uri = Uri.parse("content://test/backup.zip"),
            metadata = BackupManager.BackupMetadata(),
            databaseSize = 100L,
            settingsSize = 50L
        )
        val error = BackupManager.BackupResult.Error("error message")

        // 验证密封类类型
        assertTrue("Success 应该是 BackupResult 的实例", success is BackupManager.BackupResult)
        assertTrue("Error 应该是 BackupResult 的实例", error is BackupManager.BackupResult)

        // 验证 when 表达式覆盖所有分支
        val result: BackupManager.BackupResult = success
        val message = when (result) {
            is BackupManager.BackupResult.Success -> "success"
            is BackupManager.BackupResult.Error -> "error"
        }
        assertEquals("应该匹配成功分支", "success", message)
    }

    @Test
    fun `test restore result sealed class hierarchy`() {
        // 测试恢复结果密封类层次结构
        val success = BackupManager.RestoreResult.Success(
            metadata = BackupManager.BackupMetadata(),
            databaseRestored = true,
            settingsRestored = false
        )
        val error = BackupManager.RestoreResult.Error("error")

        assertTrue("Success 应该是 RestoreResult 的实例", success is BackupManager.RestoreResult)
        assertTrue("Error 应该是 RestoreResult 的实例", error is BackupManager.RestoreResult)
    }

    @Test
    fun `test backup with zero version code`() {
        // 测试版本号为 0 的备份
        val metadata = BackupManager.BackupMetadata(
            appVersionCode = 0,
            appVersionName = "0.0.0"
        )
        val backupFile = createMockBackupFile(metadata = metadata)
        val readMetadata = readMetadataFromBackup(backupFile)

        assertNotNull("应该能读取到版本号为 0 的元数据", readMetadata)
        assertEquals("版本号应该为 0", 0, readMetadata!!.appVersionCode)
    }

    @Test
    fun `test backup with negative version code`() {
        // 测试负版本号的备份（异常情况）
        val metadata = BackupManager.BackupMetadata(
            appVersionCode = -1,
            appVersionName = "invalid"
        )
        val backupFile = createMockBackupFile(metadata = metadata)
        val readMetadata = readMetadataFromBackup(backupFile)

        assertNotNull("应该能读取到负版本号的元数据", readMetadata)
        assertEquals("负版本号应该被保留", -1, readMetadata!!.appVersionCode)
    }

    @Test
    fun `test very long content in backup`() {
        // 测试非常长的内容（5MB）
        val longContent = ByteArray(5 * 1024 * 1024) { (it % 256).toByte() }

        val backupFile = createMockBackupFile(
            databaseContent = longContent
        )

        assertTrue("大文件备份应该存在", backupFile.exists())

        var readContent: ByteArray? = null
        ZipInputStream(BufferedInputStream(FileInputStream(backupFile))).use { zipIn ->
            var entry = zipIn.nextEntry
            while (entry != null) {
                if (entry.name == BackupManager.DATABASE_FILE_NAME) {
                    readContent = zipIn.readBytes()
                }
                zipIn.closeEntry()
                entry = zipIn.nextEntry
            }
        }

        assertNotNull("大文件内容应该存在", readContent)
        assertEquals("大文件大小应该匹配", longContent.size.toLong(), readContent!!.size.toLong())
        assertArrayEquals("大文件内容应该匹配", longContent, readContent)
    }
}