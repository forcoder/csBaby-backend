package com.csbaby.kefu.infrastructure.ota

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
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
     * 开始下载更新
     */
    fun startDownload(update: OtaUpdate): Boolean {
        _updateStatus.value = UpdateStatus.DOWNLOADING
        _errorMessage.value = null
        
        try {
            downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            
            // 创建下载目录
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val appDir = File(downloadsDir, "KefuUpdates")
            if (!appDir.exists()) {
                appDir.mkdirs()
            }
            
            val fileName = "kefu_v${update.versionName}_${update.versionCode}.apk"
            val downloadFile = File(appDir, fileName)
            
            // 如果文件已存在，先删除
            if (downloadFile.exists()) {
                downloadFile.delete()
            }
            
            val request = DownloadManager.Request(Uri.parse(update.downloadUrl))
                .setTitle("客服助手更新 v${update.versionName}")
                .setDescription("正在下载更新...")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationUri(Uri.fromFile(downloadFile))
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
                                    _updateStatus.value = UpdateStatus.DOWNLOADED
                                    
                                    // 获取下载文件的路径
                                    var apkFile: File? = null
                                    
                                    // 方式1: 使用COLUMN_LOCAL_FILENAME（最可靠）
                                    val localFilenameIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_FILENAME)
                                    if (localFilenameIndex != -1) {
                                        val localFilename = cursor.getString(localFilenameIndex)
                                        if (!localFilename.isNullOrEmpty()) {
                                            val file = File(localFilename)
                                            if (file.exists()) {
                                                apkFile = file
                                            }
                                        }
                                    }
                                    
                                    // 方式2: 直接使用下载目录
                                    if (apkFile == null) {
                                        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                                        val appDir = File(downloadsDir, "KefuUpdates")
                                        _availableUpdate.value?.let { update ->
                                            val fileName = "kefu_v${update.versionName}_${update.versionCode}.apk"
                                            val file = File(appDir, fileName)
                                            if (file.exists()) {
                                                apkFile = file
                                            }
                                        }
                                    }
                                    
                                    // 保存APK路径供后续安装使用
                                    apkFile?.let {
                                        pendingApkFile = it
                                        Log.d(TAG, "下载完成，APK路径: ${it.absolutePath}")
                                        prepareInstallation(it)
                                    } ?: run {
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
        context.registerReceiver(downloadReceiver, filter)
    }
    
    /**
     * 触发安装（用于"点击安装"按钮）
     */
    fun triggerInstall() {
        val apk = pendingApkFile
        if (apk != null && apk.exists()) {
            _updateStatus.value = UpdateStatus.INSTALLING
            _errorMessage.value = null
            prepareInstallation(apk)
        } else {
            // 找不到APK，重新查找
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val appDir = File(downloadsDir, "KefuUpdates")
            val update = _availableUpdate.value
            if (update != null) {
                val fileName = "kefu_v${update.versionName}_${update.versionCode}.apk"
                val file = File(appDir, fileName)
                if (file.exists()) {
                    pendingApkFile = file
                    _updateStatus.value = UpdateStatus.INSTALLING
                    _errorMessage.value = null
                    prepareInstallation(file)
                } else {
                    _errorMessage.value = "APK文件未找到，请重新下载"
                    _updateStatus.value = UpdateStatus.FAILED
                }
            } else {
                _errorMessage.value = "无待安装的更新"
                _updateStatus.value = UpdateStatus.FAILED
            }
        }
    }
    
    /**
     * 准备安装APK
     */
    private fun prepareInstallation(apkFile: File) {
        try {
            // 保存APK路径
            pendingApkFile = apkFile
            
            // 检查文件是否存在且可读
            if (!apkFile.exists() || !apkFile.canRead()) {
                throw Exception("APK文件不存在或不可读: ${apkFile.absolutePath}")
            }
            
            // 检查文件大小
            if (apkFile.length() == 0L) {
                throw Exception("APK文件为空: ${apkFile.absolutePath}")
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
                
                // 确保有安装包的权限
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val hasInstallPermission = context.packageManager.canRequestPackageInstalls()
                    if (!hasInstallPermission) {
                        // 没有权限，跳转到设置页面（保持INSTALLING状态，授权后可重试）
                        pendingApkFile = apkFile
                        val settingsIntent = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                        settingsIntent.data = Uri.parse("package:${context.packageName}")
                        settingsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(settingsIntent)
                        _errorMessage.value = "请授予安装未知来源应用的权限，授权后返回点击\"点击安装\""
                        // 不改变状态为FAILED，保持INSTALLING，用户授权后可以重新调用triggerInstall
                        return
                    }
                }
            }
            
            // 检查是否有应用可以处理这个意图
            if (installIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(installIntent)
            } else {
                throw Exception("没有应用可以处理安装请求")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "安装准备失败", e)
            _errorMessage.value = "安装准备失败: ${e.message}"
            _updateStatus.value = UpdateStatus.FAILED
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
            while (downloadId != -1L) {
                try {
                    val query = DownloadManager.Query()
                    query.setFilterById(downloadId)
                    
                    val cursor = downloadManager?.query(query)
                    if (cursor?.moveToFirst() == true) {
                        val status = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS))
                        
                        if (status == DownloadManager.STATUS_SUCCESSFUL || status == DownloadManager.STATUS_FAILED) {
                            break
                        }
                        
                        val totalBytes = cursor.getLong(cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                        val downloadedBytes = cursor.getLong(cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                        
                        if (totalBytes > 0) {
                            val progress = downloadedBytes.toFloat() / totalBytes
                            _downloadProgress.value = progress
                        }
                    }
                    
                    cursor?.close()
                    kotlinx.coroutines.delay(1000) // 每秒更新一次进度
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
