package com.csbaby.kefu.infrastructure.ota

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.csbaby.kefu.BuildConfig
import com.csbaby.kefu.data.model.OtaUpdate
import com.csbaby.kefu.data.model.UpdateStatus
import com.csbaby.kefu.data.repository.OtaRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OTA更新管理器
 */
@Singleton
class OtaManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: OtaRepository
) {
    
    private val _updateStatus = MutableStateFlow<UpdateStatus>(UpdateStatus.IDLE)
    val updateStatus: StateFlow<UpdateStatus> = _updateStatus.asStateFlow()
    
    private val _availableUpdate = MutableStateFlow<OtaUpdate?>(null)
    val availableUpdate: StateFlow<OtaUpdate?> = _availableUpdate.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    private val _downloadProgress = MutableStateFlow(0f)
    val downloadProgress: StateFlow<Float> = _downloadProgress.asStateFlow()
    
    private var downloadId: Long = -1
    private var downloadManager: DownloadManager? = null
    private var downloadReceiver: BroadcastReceiver? = null
    private var progressUpdateJob: kotlinx.coroutines.Job? = null
    private var pendingApkFile: File? = null  // 保存APK路径，供权限授权后重试
    
    companion object {
        private const val TAG = "OtaManager"
    }
    
    /**
     * 检查更新
     */
    suspend fun checkForUpdate(): Boolean {
        _updateStatus.value = UpdateStatus.CHECKING
        _errorMessage.value = null
        
        return try {
            val result = repository.checkForUpdate(BuildConfig.VERSION_CODE)
            
            if (result.isSuccess) {
                val update = result.getOrNull()
                
                if (update != null && update.needsUpdate(BuildConfig.VERSION_CODE)) {
                    _availableUpdate.value = update
                    _updateStatus.value = UpdateStatus.UPDATE_AVAILABLE
                    true
                } else {
                    _updateStatus.value = UpdateStatus.IDLE
                    false
                }
            } else {
                _errorMessage.value = result.exceptionOrNull()?.message
                _updateStatus.value = UpdateStatus.FAILED
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "检查更新失败", e)
            _errorMessage.value = "检查更新失败: ${e.message}"
            _updateStatus.value = UpdateStatus.FAILED
            false
        }
    }
    
    /**
     * 获取下载目录（应用专属外部目录，不需要存储权限）
     */
    private fun getDownloadDir(): File {
        // 使用应用专属外部目录，不受Scoped Storage限制
        // 路径: /storage/emulated/0/Android/data/com.csbaby.kefu/files/Updates
        val dir = File(context.getExternalFilesDir(null), "Updates")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * 获取APK文件引用
     */
    private fun getApkFile(update: OtaUpdate): File {
        return File(getDownloadDir(), "kefu_v${update.versionName}_${update.versionCode}.apk")
    }

    /**
     * 开始下载更新
     */
    fun startDownload(update: OtaUpdate): Boolean {
        _updateStatus.value = UpdateStatus.DOWNLOADING
        _errorMessage.value = null
        _availableUpdate.value = update  // 确保下载完成时能正确获取文件路径
        
        try {
            downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            
            val downloadFile = getApkFile(update)
            
            // 如果文件已存在，先删除
            if (downloadFile.exists()) {
                downloadFile.delete()
            }
            
            val request = DownloadManager.Request(Uri.parse(update.downloadUrl))
                .setTitle("客服助手更新 v${update.versionName}")
                .setDescription("正在下载更新...")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalFilesDir(context, null, "Updates/${downloadFile.name}")
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                request.setRequiresCharging(false)
            }
            
            downloadId = downloadManager!!.enqueue(request)
            
            // 注册下载完成广播接收器
            registerDownloadReceiver()
            
            // 启动下载进度监控
            startProgressMonitoring()
            
            return true
        } catch (e: Exception) {
            Log.e(TAG, "开始下载失败", e)
            _errorMessage.value = "开始下载失败: ${e.message}"
            _updateStatus.value = UpdateStatus.FAILED
            return false
        }
    }
    
    /**
     * 注册下载完成广播接收器
     */
    private fun registerDownloadReceiver() {
        downloadReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                
                if (id == downloadId) {
                    val query = DownloadManager.Query()
                    query.setFilterById(id)
                    
                    val cursor = downloadManager?.query(query)
                    
                    if (cursor?.moveToFirst() == true) {
                        val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                        if (statusIndex >= 0) {
                            val status = cursor.getInt(statusIndex)
                            
                            when (status) {
                                DownloadManager.STATUS_SUCCESSFUL -> {
                                    // 使用应用专属下载目录获取文件（最可靠，不依赖废弃API）
                                    val update = _availableUpdate.value
                                    val apkFile: File? = update?.let { getApkFile(it) }?.takeIf { it.exists() && it.length() > 0 }

                                    if (apkFile != null) {
                                        pendingApkFile = apkFile
                                        Log.d(TAG, "下载完成，APK路径: ${apkFile.absolutePath}，大小: ${apkFile.length()} bytes")
                                        // 只设置下载完成状态，不自动安装，等用户点击"安装"按钮
                                        _updateStatus.value = UpdateStatus.DOWNLOADED
                                        _downloadProgress.value = 1f
                                    } else {
                                        _errorMessage.value = "无法找到下载文件"
                                        _updateStatus.value = UpdateStatus.FAILED
                                    }
                                }
                                
                                DownloadManager.STATUS_FAILED -> {
                                    val reasonIndex = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
                                    if (reasonIndex >= 0) {
                                        val reason = cursor.getInt(reasonIndex)
                                        _errorMessage.value = "下载失败: ${getDownloadErrorReason(reason)}"
                                    } else {
                                        _errorMessage.value = "下载失败: 未知原因"
                                    }
                                    _updateStatus.value = UpdateStatus.FAILED
                                }
                            }
                        }
                    }
                    
                    cursor?.close()
                }
            }
        }
        
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        ContextCompat.registerReceiver(context, downloadReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }
    
    /**
     * 触发安装（用于"点击安装"按钮）
     */
    fun triggerInstall() {
        val apk = pendingApkFile
        if (apk != null && apk.exists() && apk.length() > 0) {
            Log.d(TAG, "开始安装，APK路径: ${apk.absolutePath}，大小: ${apk.length()} bytes")
            _errorMessage.value = null
            installApk(apk)
        } else {
            // 找不到APK，重新查找
            val update = _availableUpdate.value
            if (update != null) {
                val file = getApkFile(update)
                if (file.exists() && file.length() > 0) {
                    pendingApkFile = file
                    _errorMessage.value = null
                    installApk(file)
                } else {
                    _errorMessage.value = "APK文件未找到或已损坏，请重新下载"
                    _updateStatus.value = UpdateStatus.FAILED
                }
            } else {
                _errorMessage.value = "无待安装的更新"
                _updateStatus.value = UpdateStatus.FAILED
            }
        }
    }
    
    /**
     * 安装APK
     */
    private fun installApk(apkFile: File) {
        try {
            // 检查文件是否存在且可读
            if (!apkFile.exists() || !apkFile.canRead()) {
                throw Exception("APK文件不存在或不可读: ${apkFile.absolutePath}")
            }
            
            // 检查文件大小（APK至少要有1KB）
            if (apkFile.length() < 1024) {
                throw Exception("APK文件已损坏（文件过小）: ${apkFile.absolutePath}，大小: ${apkFile.length()} bytes")
            }
            
            // Android 8.0+ 先检查安装权限
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val hasInstallPermission = context.packageManager.canRequestPackageInstalls()
                if (!hasInstallPermission) {
                    // 没有权限，跳转到设置页面
                    // 回退到DOWNLOADED状态，授权后用户可以再次点击"安装"
                    _updateStatus.value = UpdateStatus.DOWNLOADED
                    _errorMessage.value = "请先授予安装权限，然后返回点击\"安装\"按钮"
                    Log.w(TAG, "缺少安装未知来源应用权限，跳转到设置页面")
                    val settingsIntent = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                    settingsIntent.data = Uri.parse("package:${context.packageName}")
                    settingsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(settingsIntent)
                    return
                }
            }
            
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                // Android 7.0+ 使用FileProvider
                FileProvider.getUriForFile(
                    context,
                    "${BuildConfig.APPLICATION_ID}.fileprovider",
                    apkFile
                )
            } else {
                Uri.fromFile(apkFile)
            }
            
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
            
            // 检查是否有应用可以处理这个意图
            if (installIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(installIntent)
                Log.d(TAG, "已触发系统安装页面")
            } else {
                throw Exception("没有应用可以处理安装请求")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "安装失败", e)
            _errorMessage.value = "安装失败: ${e.message}"
            // 回退到DOWNLOADED状态，允许用户重试
            _updateStatus.value = UpdateStatus.DOWNLOADED
        }
    }
    
    /**
     * 获取下载错误原因
     */
    private fun getDownloadErrorReason(reason: Int): String {
        return when (reason) {
            DownloadManager.ERROR_CANNOT_RESUME -> "无法恢复下载"
            DownloadManager.ERROR_DEVICE_NOT_FOUND -> "设备未找到"
            DownloadManager.ERROR_FILE_ALREADY_EXISTS -> "文件已存在"
            DownloadManager.ERROR_FILE_ERROR -> "文件错误"
            DownloadManager.ERROR_HTTP_DATA_ERROR -> "HTTP数据错误"
            DownloadManager.ERROR_INSUFFICIENT_SPACE -> "存储空间不足"
            DownloadManager.ERROR_TOO_MANY_REDIRECTS -> "重定向过多"
            DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "未处理的HTTP代码"
            DownloadManager.ERROR_UNKNOWN -> "未知错误"
            else -> "错误代码: $reason"
        }
    }
    
    /**
     * 取消下载
     */
    fun cancelDownload() {
        if (downloadId != -1L) {
            downloadManager?.remove(downloadId)
            downloadId = -1
        }
        
        // 停止进度监控
        progressUpdateJob?.cancel()
        progressUpdateJob = null
        
        downloadReceiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (e: Exception) {
                // 忽略取消注册错误
            }
            downloadReceiver = null
        }
        
        _updateStatus.value = UpdateStatus.IDLE
        _errorMessage.value = null
        _downloadProgress.value = 0f
        pendingApkFile = null
    }
    
    /**
     * 启动下载进度监控
     */
    private fun startProgressMonitoring() {
        progressUpdateJob = kotlinx.coroutines.GlobalScope.launch {
            var lastDownloadedBytes = 0L
            var stuckCount = 0
            while (downloadId != -1L) {
                try {
                    val query = DownloadManager.Query()
                    query.setFilterById(downloadId)

                    val cursor = downloadManager?.query(query)
                    if (cursor?.moveToFirst() == true) {
                        val statusCol = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                        val status = if (statusCol >= 0) cursor.getInt(statusCol) else -1

                        if (status == DownloadManager.STATUS_SUCCESSFUL || status == DownloadManager.STATUS_FAILED) {
                            break
                        }

                        val totalCol = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                        val downloadedCol = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                        val totalBytes = if (totalCol >= 0) cursor.getLong(totalCol) else -1L
                        val downloadedBytes = if (downloadedCol >= 0) cursor.getLong(downloadedCol) else 0L

                        when {
                            // 服务器返回了正确的总大小，直接计算百分比
                            totalBytes > 0 -> {
                                val progress = downloadedBytes.toFloat() / totalBytes
                                _downloadProgress.value = progress.coerceIn(0f, 0.99f)
                                stuckCount = 0
                            }
                            // 服务器未返回总大小（-1），用已下载字节数做增量估算
                            downloadedBytes > 0 -> {
                                // 检测进度是否卡住（连续3次字节数不变）
                                if (downloadedBytes == lastDownloadedBytes) {
                                    stuckCount++
                                } else {
                                    stuckCount = 0
                                }
                                lastDownloadedBytes = downloadedBytes
                                // 如果卡住超过5秒，说明可能下载速度很慢或停滞，保持当前进度
                                // 如果还在缓慢下载，做一个微增让进度条动起来
                                if (stuckCount > 5 && _downloadProgress.value < 0.95f) {
                                    // 缓慢微增，给用户反馈
                                    _downloadProgress.value = (_downloadProgress.value + 0.01f).coerceAtMost(0.95f)
                                }
                            }
                        }
                    }

                    cursor?.close()
                    kotlinx.coroutines.delay(1000)
                } catch (e: Exception) {
                    Log.e(TAG, "监控下载进度失败", e)
                    break
                }
            }
        }
    }
    
    /**
     * 清理状态
     */
    fun cleanup() {
        cancelDownload()
        _updateStatus.value = UpdateStatus.IDLE
        _availableUpdate.value = null
        _errorMessage.value = null
        _downloadProgress.value = 0f
        pendingApkFile = null
    }
}
