package com.csbaby.kefu.infrastructure.oss

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * 阿里云OSS管理器
 * 用于管理APK文件的上传和版本管理
 */
class AliyunOssManager(private val context: Context) {

    companion object {
        const val MIME_TYPE_APK = "application/vnd.android.package-archive"
        private const val OSS_DOMAIN = "https://apk-ota.oss-cn-shenzhen.aliyuncs.com/"
    }

    /**
     * 验证OSS配置
     */
    fun validateConfig(): Boolean {
        // 简化实现：始终返回true
        return true
    }

    /**
     * 将Uri转换为文件
     */
    fun uriToFile(uri: Uri): File? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val tempFile = File(context.cacheDir, "temp_upload.apk")
            tempFile.outputStream().use { output ->
                inputStream.copyTo(output)
            }
            tempFile
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 分析APK文件信息
     */
    fun analyzeApkFile(file: File): ApkFileInfo {
        return ApkFileInfo(
            fileSize = file.length(),
            md5 = "placeholder_md5"
        )
    }

    /**
     * 生成OSS对象键
     */
    fun generateObjectKey(
        appName: String,
        versionName: String,
        versionCode: Int,
        timestamp: Long,
        fileMd5: String
    ): String {
        val dateStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            .format(java.util.Date(timestamp))
        return "apks/$appName/v${versionName}_$versionCode/$dateStr/${timestamp}_$fileMd5.apk"
    }

    /**
     * 生成上传签名
     */
    fun generatePutSignature(
        objectKey: String,
        contentType: String,
        contentMd5: String
    ): String {
        return "placeholder_signature"
    }

    /**
     * 构建直接上传URL
     */
    fun buildDirectUploadUrl(objectKey: String): String? {
        return "$OSS_DOMAIN$objectKey"
    }

    /**
     * 获取OSS域名
     */
    fun getOssDomain(): String {
        return OSS_DOMAIN
    }

    /**
     * 清理临时文件
     */
    fun cleanupTempFiles() {
        try {
            val cacheDir = context.cacheDir
            cacheDir.listFiles()?.forEach { file ->
                if (file.name.startsWith("temp_")) {
                    file.delete()
                }
            }
        } catch (e: Exception) {
            // 忽略清理错误
        }
    }
}

/**
 * APK文件信息
 */
data class ApkFileInfo(
    val fileSize: Long,
    val md5: String?
)
