package com.csbaby.kefu.presentation.screens.profile

import android.net.Uri
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.csbaby.kefu.data.local.BackupManager
import com.csbaby.kefu.data.local.KefuDatabase
import com.csbaby.kefu.data.local.PreferencesManager
import com.csbaby.kefu.domain.repository.UserStyleRepository
import com.csbaby.kefu.infrastructure.oss.AliyunOssManager
import com.csbaby.kefu.infrastructure.ota.OtaManager
import com.csbaby.kefu.infrastructure.style.StyleLearningEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.*

/**
 * ProfileViewModel 备份/恢复功能单元测试
 *
 * 测试覆盖:
 * - 正常功能: 备份调用、恢复调用、状态更新
 * - 边界条件: 空 URI、重复操作
 * - 异常条件: 备份失败、恢复失败、数据库关闭失败
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelBackupTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var preferencesManager: PreferencesManager
    private lateinit var userStyleRepository: UserStyleRepository
    private lateinit var styleLearningEngine: StyleLearningEngine
    private lateinit var otaManager: OtaManager
    private lateinit var ossManager: AliyunOssManager
    private lateinit var backupManager: BackupManager
    private lateinit var database: KefuDatabase

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        // 创建 mock 对象
        preferencesManager = mock {
            on { userPreferencesFlow } doReturn flowOf(PreferencesManager.UserPreferences())
        }
        userStyleRepository = mock {
            on { getProfile(any()) } doReturn flowOf(null)
        }
        styleLearningEngine = mock()
        otaManager = mock {
            on { updateStatus } doReturn flowOf()
            on { availableUpdate } doReturn flowOf(null)
            on { errorMessage } doReturn flowOf(null)
            on { downloadProgress } doReturn flowOf(0f)
        }
        ossManager = mock()
        backupManager = mock()
        database = mock()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * 创建 ViewModel 实例
     */
    private fun createViewModel(): ProfileViewModel {
        return ProfileViewModel(
            preferencesManager = preferencesManager,
            userStyleRepository = userStyleRepository,
            styleLearningEngine = styleLearningEngine,
            otaManager = otaManager,
            ossManager = ossManager,
            backupManager = backupManager,
            database = database
        )
    }

    // ==================== 正常功能测试 ====================

    @Test
    fun `test initial state has correct backup values`() {
        // 测试初始状态中备份相关的值正确
        val viewModel = createViewModel()
        val state = viewModel.uiState.value

        assertFalse("初始 isBackingUp 应该为 false", state.isBackingUp)
        assertFalse("初始 isRestoring 应该为 false", state.isRestoring)
        assertEquals("初始 backupProgress 应该为 0", 0f, state.backupProgress)
        assertEquals("初始 backupStatusMessage 应该为空", "", state.backupStatusMessage)
        assertNull("初始 lastBackupTime 应该为 null", state.lastBackupTime)
        assertNull("初始 restoreMetadata 应该为 null", state.restoreMetadata)
        assertNull("初始 backupErrorMessage 应该为 null", state.backupErrorMessage)
    }

    @Test
    fun `test performBackup delegates to BackupManager`() = runTest {
        // 测试备份委托给 BackupManager
        val viewModel = createViewModel()
        val testUri = Uri.parse("content://test/backup.zip")

        doReturn(BackupManager.BackupResult.Success(
            uri = testUri,
            metadata = BackupManager.BackupMetadata(),
            databaseSize = 1024L,
            settingsSize = 512L
        )).whenever(backupManager).performBackup(eq(testUri), any())

        viewModel.performBackup(testUri)
        advanceUntilIdle()

        verify(backupManager).performBackup(eq(testUri), any())
    }

    @Test
    fun `test performBackup updates state on success`() = runTest {
        // 测试备份成功时状态正确更新
        val viewModel = createViewModel()
        val testUri = Uri.parse("content://test/backup.zip")
        val metadata = BackupManager.BackupMetadata(
            appVersionName = "1.0.0",
            appVersionCode = 1,
            backupTimeFormatted = "2024-01-01 10:00:00"
        )

        doReturn(BackupManager.BackupResult.Success(
            uri = testUri,
            metadata = metadata,
            databaseSize = 1024L,
            settingsSize = 512L
        )).whenever(backupManager).performBackup(eq(testUri), any())

        viewModel.performBackup(testUri)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse("备份完成后 isBackingUp 应该为 false", state.isBackingUp)
        assertEquals("进度应该为 1.0", 1f, state.backupProgress)
        assertEquals("状态消息应该为备份完成", "备份完成", state.backupStatusMessage)
        assertEquals("上次备份时间应该设置", "2024-01-01 10:00:00", state.lastBackupTime)
        assertNull("错误消息应该为空", state.backupErrorMessage)
    }

    @Test
    fun `test performBackup updates state on error`() = runTest {
        // 测试备份失败时状态正确更新
        val viewModel = createViewModel()
        val testUri = Uri.parse("content://test/backup.zip")

        doReturn(BackupManager.BackupResult.Error("备份失败: 存储空间不足"))
            .whenever(backupManager).performBackup(eq(testUri), any())

        viewModel.performBackup(testUri)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse("备份失败后 isBackingUp 应该为 false", state.isBackingUp)
        assertEquals("进度应该为 0", 0f, state.backupProgress)
        assertEquals("状态消息应该为备份失败", "备份失败", state.backupStatusMessage)
        assertNotNull("错误消息应该不为空", state.backupErrorMessage)
        assertTrue("错误消息应该包含原因", state.backupErrorMessage!!.contains("存储空间不足"))
    }

    @Test
    fun `test performRestore delegates to BackupManager`() = runTest {
        // 测试恢复委托给 BackupManager
        val viewModel = createViewModel()
        val testUri = Uri.parse("content://test/backup.zip")

        doReturn(BackupManager.BackupMetadata(appVersionName = "1.0.0"))
            .whenever(backupManager).readBackupMetadata(testUri)
        doReturn(BackupManager.RestoreResult.Success(
            metadata = BackupManager.BackupMetadata(),
            databaseRestored = true,
            settingsRestored = true
        )).whenever(backupManager).performRestore(eq(testUri), any())

        viewModel.performRestore(testUri)
        advanceUntilIdle()

        verify(backupManager).readBackupMetadata(testUri)
        verify(backupManager).performRestore(eq(testUri), any())
        verify(database).close()
    }

    @Test
    fun `test performRestore updates state on success`() = runTest {
        // 测试恢复成功时状态正确更新
        val viewModel = createViewModel()
        val testUri = Uri.parse("content://test/backup.zip")
        val metadata = BackupManager.BackupMetadata(appVersionName = "1.0.0")

        doReturn(metadata).whenever(backupManager).readBackupMetadata(testUri)
        doReturn(BackupManager.RestoreResult.Success(
            metadata = metadata,
            databaseRestored = true,
            settingsRestored = true
        )).whenever(backupManager).performRestore(eq(testUri), any())

        viewModel.performRestore(testUri)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse("恢复完成后 isRestoring 应该为 false", state.isRestoring)
        assertEquals("进度应该为 1.0", 1f, state.backupProgress)
        assertTrue("状态消息应该包含恢复完成", state.backupStatusMessage.contains("恢复完成"))
        assertNull("错误消息应该为空", state.backupErrorMessage)
    }

    @Test
    fun `test performRestore updates state on error`() = runTest {
        // 测试恢复失败时状态正确更新
        val viewModel = createViewModel()
        val testUri = Uri.parse("content://test/backup.zip")

        doReturn(null).whenever(backupManager).readBackupMetadata(testUri)
        doReturn(BackupManager.RestoreResult.Error("恢复失败: 文件损坏"))
            .whenever(backupManager).performRestore(eq(testUri), any())

        viewModel.performRestore(testUri)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse("恢复失败后 isRestoring 应该为 false", state.isRestoring)
        assertEquals("进度应该为 0", 0f, state.backupProgress)
        assertEquals("状态消息应该为恢复失败", "恢复失败", state.backupStatusMessage)
        assertNotNull("错误消息应该不为空", state.backupErrorMessage)
        assertTrue("错误消息应该包含文件损坏", state.backupErrorMessage!!.contains("文件损坏"))
    }

    @Test
    fun `test clearBackupStatus clears messages`() = runTest {
        // 测试清除备份状态
        val viewModel = createViewModel()
        val testUri = Uri.parse("content://test/backup.zip")

        doReturn(BackupManager.BackupResult.Error("错误"))
            .whenever(backupManager).performBackup(eq(testUri), any())

        viewModel.performBackup(testUri)
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.backupErrorMessage)

        viewModel.clearBackupStatus()

        val state = viewModel.uiState.value
        assertEquals("状态消息应该为空", "", state.backupStatusMessage)
        assertNull("错误消息应该为空", state.backupErrorMessage)
    }

    // ==================== 边界条件测试 ====================

    @Test
    fun `test performRestore reads metadata before restore`() = runTest {
        // 测试恢复前先读取元数据
        val viewModel = createViewModel()
        val testUri = Uri.parse("content://test/backup.zip")
        val metadata = BackupManager.BackupMetadata(appVersionName = "1.0.0")

        doReturn(metadata).whenever(backupManager).readBackupMetadata(testUri)
        doReturn(BackupManager.RestoreResult.Success(
            metadata = metadata,
            databaseRestored = true,
            settingsRestored = true
        )).whenever(backupManager).performRestore(eq(testUri), any())

        viewModel.performRestore(testUri)
        advanceUntilIdle()

        // 验证元数据被设置
        assertEquals("元数据应该被设置", metadata, viewModel.uiState.value.restoreMetadata)
    }

    @Test
    fun `test formatBackupSize delegates to BackupManager`() {
        // 测试文件大小格式化委托给 BackupManager
        val viewModel = createViewModel()

        doReturn("1.50 MB").whenever(backupManager).formatFileSize(1572864L)

        val result = viewModel.formatBackupSize(1572864L)

        assertEquals("应该返回 BackupManager 的格式化结果", "1.50 MB", result)
        verify(backupManager).formatFileSize(1572864L)
    }

    // ==================== 异常条件测试 ====================

    @Test
    fun `test performBackup handles unexpected exception`() = runTest {
        // 测试备份处理意外异常
        val viewModel = createViewModel()
        val testUri = Uri.parse("content://test/backup.zip")

        doThrow(RuntimeException("意外错误"))
            .whenever(backupManager).performBackup(eq(testUri), any())

        viewModel.performBackup(testUri)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse("异常后备份应该结束", state.isBackingUp)
        assertNotNull("应该有错误消息", state.backupErrorMessage)
    }

    @Test
    fun `test performRestore handles database close failure`() = runTest {
        // 测试恢复时数据库关闭失败
        val viewModel = createViewModel()
        val testUri = Uri.parse("content://test/backup.zip")

        doThrow(RuntimeException("数据库关闭失败")).whenever(database).close()
        doReturn(null).whenever(backupManager).readBackupMetadata(testUri)
        doReturn(BackupManager.RestoreResult.Success(
            metadata = BackupManager.BackupMetadata(),
            databaseRestored = true,
            settingsRestored = true
        )).whenever(backupManager).performRestore(eq(testUri), any())

        // 恢复应该继续执行（数据库关闭失败不应阻止恢复）
        viewModel.performRestore(testUri)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse("恢复应该完成", state.isRestoring)
    }

    @Test
    fun `test performRestore handles unexpected exception`() = runTest {
        // 测试恢复处理意外异常
        val viewModel = createViewModel()
        val testUri = Uri.parse("content://test/backup.zip")

        doThrow(RuntimeException("意外错误")).whenever(backupManager).readBackupMetadata(testUri)

        viewModel.performRestore(testUri)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse("异常后恢复应该结束", state.isRestoring)
        assertNotNull("应该有错误消息", state.backupErrorMessage)
    }

    @Test
    fun `test performRestore with null metadata`() = runTest {
        // 测试恢复时元数据为 null
        val viewModel = createViewModel()
        val testUri = Uri.parse("content://test/backup.zip")

        doReturn(null).whenever(backupManager).readBackupMetadata(testUri)
        doReturn(BackupManager.RestoreResult.Success(
            metadata = BackupManager.BackupMetadata(),
            databaseRestored = true,
            settingsRestored = true
        )).whenever(backupManager).performRestore(eq(testUri), any())

        viewModel.performRestore(testUri)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse("恢复应该完成", state.isRestoring)
        assertNull("元数据应该为 null", state.restoreMetadata)
    }

    // ==================== 状态转换测试 ====================

    @Test
    fun `test backup state transition sequence`() = runTest {
        // 测试备份状态转换序列
        val viewModel = createViewModel()
        val testUri = Uri.parse("content://test/backup.zip")

        // 使用 Answer 模拟耗时操作
        doAnswer {
            // 模拟备份正在进行中
            val callback = it.getArgument<(Float, String) -> Unit>(1)
            callback.invoke(0.5f, "正在备份...")

            BackupManager.BackupResult.Success(
                uri = testUri,
                metadata = BackupManager.BackupMetadata(),
                databaseSize = 100L,
                settingsSize = 50L
            )
        }.whenever(backupManager).performBackup(eq(testUri), any())

        viewModel.performBackup(testUri)

        // 在备份进行中检查状态
        runCurrent()
        val backingUpState = viewModel.uiState.value
        assertTrue("备份进行中 isBackingUp 应该为 true", backingUpState.isBackingUp)

        advanceUntilIdle()

        val completedState = viewModel.uiState.value
        assertFalse("备份完成后 isBackingUp 应该为 false", completedState.isBackingUp)
    }

    // ==================== 多次操作测试 ====================

    @Test
    fun `test multiple backup operations update lastBackupTime`() = runTest {
        // 测试多次备份操作更新上次备份时间
        val viewModel = createViewModel()
        val testUri1 = Uri.parse("content://test/backup1.zip")
        val testUri2 = Uri.parse("content://test/backup2.zip")

        val metadata1 = BackupManager.BackupMetadata(
            backupTime = 1000L,
            backupTimeFormatted = "2024-01-01 10:00:00"
        )
        val metadata2 = BackupManager.BackupMetadata(
            backupTime = 2000L,
            backupTimeFormatted = "2024-01-02 11:00:00"
        )

        doReturn(BackupManager.BackupResult.Success(
            uri = testUri1, metadata = metadata1, databaseSize = 100L, settingsSize = 50L
        )).whenever(backupManager).performBackup(eq(testUri1), any())

        doReturn(BackupManager.BackupResult.Success(
            uri = testUri2, metadata = metadata2, databaseSize = 100L, settingsSize = 50L
        )).whenever(backupManager).performBackup(eq(testUri2), any())

        // 第一次备份
        viewModel.performBackup(testUri1)
        advanceUntilIdle()
        assertEquals("第一次备份时间", "2024-01-01 10:00:00", viewModel.uiState.value.lastBackupTime)

        // 第二次备份
        viewModel.performBackup(testUri2)
        advanceUntilIdle()
        assertEquals("第二次备份时间应该更新", "2024-01-02 11:00:00", viewModel.uiState.value.lastBackupTime)
    }

    @Test
    fun `test backup followed by restore`() = runTest {
        // 测试先备份后恢复
        val viewModel = createViewModel()
        val backupUri = Uri.parse("content://test/backup.zip")
        val restoreUri = Uri.parse("content://test/restore.zip")

        doReturn(BackupManager.BackupResult.Success(
            uri = backupUri,
            metadata = BackupManager.BackupMetadata(),
            databaseSize = 100L,
            settingsSize = 50L
        )).whenever(backupManager).performBackup(eq(backupUri), any())

        doReturn(BackupManager.BackupMetadata())
            .whenever(backupManager).readBackupMetadata(restoreUri)
        doReturn(BackupManager.RestoreResult.Success(
            metadata = BackupManager.BackupMetadata(),
            databaseRestored = true,
            settingsRestored = true
        )).whenever(backupManager).performRestore(eq(restoreUri), any())

        // 先备份
        viewModel.performBackup(backupUri)
        advanceUntilIdle()
        assertFalse("备份应该完成", viewModel.uiState.value.isBackingUp)

        // 后恢复
        viewModel.performRestore(restoreUri)
        advanceUntilIdle()
        assertFalse("恢复应该完成", viewModel.uiState.value.isRestoring)
    }
}
