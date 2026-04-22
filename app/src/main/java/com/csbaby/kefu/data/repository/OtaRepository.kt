package com.csbaby.kefu.data.repository

import android.content.Context
import com.csbaby.kefu.data.model.OtaUpdate
import com.csbaby.kefu.data.remote.MockOtaApiService
import com.csbaby.kefu.data.remote.OtaApiService
import com.csbaby.kefu.data.remote.ShzlApiService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OTA更新仓库
 */
interface OtaRepository {
    /**
     * 检查更新
     */
    suspend fun checkForUpdate(currentVersionCode: Int): Result<OtaUpdate?>
    
    /**
     * 获取下载进度
     */
    val downloadProgress: Flow<Float>
    
    /**
     * 获取下载状态
     */
    val downloadStatus: Flow<String>
    
    /**
     * 下载APK
     */
    suspend fun downloadApk(update: OtaUpdate): Result<String>
    
    /**
     * 获取本地已下载的APK路径
     */
    suspend fun getDownloadedApkPath(versionCode: Int): String?
    
    /**
     * 清理旧版本APK文件
     */
    suspend fun cleanupOldVersions()
}

/**
 * OTA更新仓库实现
 */
@Singleton
class OtaRepositoryImpl @Inject constructor(
    private val context: Context,
    private val shzlApiService: ShzlApiService
) : OtaRepository {
    
    private val _downloadProgress = MutableStateFlow(0f)
    override val downloadProgress: StateFlow<Float> = _downloadProgress
    
    private val _downloadStatus = MutableStateFlow("")
    override val downloadStatus: StateFlow<String> = _downloadStatus
    
    override suspend fun checkForUpdate(currentVersionCode: Int): Result<OtaUpdate?> {
        return try {
            // 直接从 shz.al 获取版本信息
            val versionInfo = shzlApiService.getVersionInfo()
            
            // 比较版本号
            if (versionInfo.versionCode > currentVersionCode) {
                // 有新版本
                Result.success(versionInfo.toOtaUpdate())
            } else {
                // 已是最新版本
                Result.success(null)
            }
        } catch (e: Exception) {
            // shz.al 访问失败，尝试回退到模拟服务
            try {
                val mockService = MockOtaApiService()
                val response = mockService.checkForUpdate(currentVersionCode)
                
                if (response.isSuccess && response.data != null) {
                    Result.success(response.data)
                } else if (response.isNoUpdate) {
                    Result.success(null)
                } else {
                    Result.failure(Exception("检查更新失败: ${response.message}"))
                }
            } catch (fallbackError: Exception) {
                Result.failure(Exception("检查更新失败: ${e.message}", fallbackError))
            }
        }
    }

    override suspend fun downloadApk(update: OtaUpdate): Result<String> {
        return try {
            _downloadStatus.value = "开始下载..."
            _downloadProgress.value = 0f
            
            // 创建保存目录
            val otaDir = context.getExternalFilesDir("ota_updates")
            if (otaDir == null) {
                return Result.failure(Exception("无法访问存储目录"))
            }
            
            if (!otaDir.exists()) {
                otaDir.mkdirs()
            }
            
            // 创建目标文件
            val fileName = "app-update-v${update.versionCode}.apk"
            val targetFile = otaDir.resolve(fileName)
            
            // 使用 shz.al API 下载 APK
            val response = shzlApiService.downloadApk()
            
            // 流式写入文件
            response.byteStream().use { input ->
                targetFile.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var totalBytesRead = 0L
                    var bytesRead: Int
                    
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead
                        
                        // 更新进度
                        if (update.fileSize > 0) {
                            val progress = totalBytesRead.toFloat() / update.fileSize
                            _downloadProgress.value = progress
                            _downloadStatus.value = "下载中... ${(progress * 100).toInt()}%"
                        }
                    }
                }
            }
            
            _downloadProgress.value = 1f
            _downloadStatus.value = "下载完成"
            
            Result.success(targetFile.absolutePath)
        } catch (e: Exception) {
            _downloadStatus.value = "下载失败: ${e.message}"
            _downloadProgress.value = 0f
            Result.failure(Exception("下载失败: ${e.message}", e))
        }
    }
    
    override suspend fun getDownloadedApkPath(versionCode: Int): String? {
        val fileName = "app-update-v$versionCode.apk"
        val file = context.getExternalFilesDir("ota_updates")?.resolve(fileName)
        return file?.takeIf { it.exists() }?.absolutePath
    }
    
    override suspend fun cleanupOldVersions() {
        try {
            val otaDir = context.getExternalFilesDir("ota_updates")
            otaDir?.listFiles()?.forEach { file ->
                if (file.isFile && file.name.endsWith(".apk")) {
                    file.delete()
                }
            }
        } catch (e: Exception) {
            // 忽略清理错误
        }
    }
    
    /**
     * 模拟下载进度更新（用于测试）
     */
    private suspend fun simulateDownloadProgress() {
        for (progress in 0..100 step 5) {
            _downloadProgress.value = progress / 100f
            _downloadStatus.value = "下载中... $progress%"
            kotlinx.coroutines.delay(100)
        }
        _downloadStatus.value = "下载完成"
    }
}