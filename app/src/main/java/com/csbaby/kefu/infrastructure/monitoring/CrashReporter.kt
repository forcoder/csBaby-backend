package com.csbaby.kefu.infrastructure.monitoring

import android.content.Context
import android.os.Build
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * 崩溃报告器
 * 负责收集和分析应用崩溃和性能警告
 */
@Singleton
class CrashReporter @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val scope = CoroutineScope(Dispatchers.IO)
    private val metricsStore = ConcurrentHashMap<String, Double>()
    private val warningsStore = ConcurrentHashMap<String, Double>()

    /**
     * 记录性能指标
     */
    fun recordMetric(metricName: String, value: Double) {
        scope.launch {
            try {
                metricsStore[metricName] = value
                Timber.v("Performance metric recorded: $metricName = $value")

                // 保存到本地存储
                saveMetricsToStorage()

            } catch (e: Exception) {
                Timber.e(e, "Failed to record metric: $metricName")
            }
        }
    }

    /**
     * 记录性能警告
     */
    fun recordWarning(warningType: String, value: Double) {
        scope.launch {
            try {
                warningsStore[warningType] = value
                Timber.w("Performance warning recorded: $warningType = $value")

                // 发送警告通知（如果配置了）
                sendWarningNotification(warningType, value)

                // 保存到本地存储
                saveWarningsToStorage()

            } catch (e: Exception) {
                Timber.e(e, "Failed to record warning: $warningType")
            }
        }
    }

    /**
     * 记录自定义异常
     */
    fun recordException(exception: Throwable, context: String = "") {
        scope.launch {
            try {
                val exceptionData = ExceptionData(
                    message = exception.message ?: "Unknown error",
                    stackTrace = getStackTraceString(exception),
                    context = context,
                    timestamp = System.currentTimeMillis(),
                    deviceInfo = getDeviceInfo()
                )

                saveExceptionLocally(exceptionData)
                sendExceptionToRemote(exceptionData)

                Timber.e(exception, "Exception recorded: ${exception.message}")

            } catch (e: Exception) {
                Timber.e(e, "Failed to record exception")
            }
        }
    }

    /**
     * 获取当前性能指标摘要
     */
    fun getMetricsSummary(): MetricsSummary {
        return MetricsSummary(
            timestamp = System.currentTimeMillis(),
            metrics = metricsStore.toMap(),
            warnings = warningsStore.toMap(),
            deviceInfo = getDeviceInfo()
        )
    }

    /**
     * 清除所有存储的数据
     */
    fun clearStoredData() {
        metricsStore.clear()
        warningsStore.clear()

        // 清除本地存储
        context.getSharedPreferences("crash_reporter_prefs", Context.MODE_PRIVATE).edit {
            clear()
            apply()
        }

        // 删除文件存储的数据
        try {
            val crashDir = File(context.filesDir, "crash_reports")
            if (crashDir.exists()) {
                deleteDirectory(crashDir)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to clear crash report files")
        }
    }

    /**
     * 保存指标到本地存储
     */
    private suspend fun saveMetricsToStorage() {
        try {
            val prefs = context.getSharedPreferences("crash_reporter_prefs", Context.MODE_PRIVATE)
            val metricsJson = metricsStore.entries.joinToString(",") { "${it.key}=${it.value}" }
            prefs.edit {
                putString("metrics", metricsJson)
                apply()
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to save metrics to storage")
        }
    }

    /**
     * 保存警告到本地存储
     */
    private suspend fun saveWarningsToStorage() {
        try {
            val prefs = context.getSharedPreferences("crash_reporter_prefs", Context.MODE_PRIVATE)
            val warningsJson = warningsStore.entries.joinToString(",") { "${it.key}=${it.value}" }
            prefs.edit {
                putString("warnings", warningsJson)
                apply()
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to save warnings to storage")
        }
    }

    /**
     * 保存异常到本地存储
     */
    private suspend fun saveExceptionLocally(exceptionData: ExceptionData) {
        try {
            val crashDir = File(context.filesDir, "crash_reports")
            if (!crashDir.exists()) {
                crashDir.mkdirs()
            }

            val file = File(crashDir, "exception_${System.currentTimeMillis()}.json")
            file.writeText(exceptionData.toJsonString())

            // 限制本地存储的文件数量
            limitCrashReportFiles(crashDir)

        } catch (e: Exception) {
            Timber.e(e, "Failed to save exception locally")
        }
    }

    /**
     * 发送警告通知
     */
    private suspend fun sendWarningNotification(warningType: String, value: Double) {
        // TODO: 实现通知逻辑（如果配置了）
        // 例如：显示通知、发送到服务器等

        Timber.i("Warning notification sent: $warningType = $value")
    }

    /**
     * 发送异常到远程服务
     */
    private suspend fun sendExceptionToRemote(exceptionData: ExceptionData) {
        // TODO: 实现远程异常报告集成
        // 例如：Firebase Crashlytics, Sentry, 自定义后端等

        Timber.d("Exception sent to remote service: ${exceptionData.message}")
    }

    /**
     * 获取设备信息
     */
    private fun getDeviceInfo(): DeviceInfo {
        return DeviceInfo(
            model = Build.MODEL,
            manufacturer = Build.MANUFACTURER,
            brand = Build.BRAND,
            product = Build.PRODUCT,
            device = Build.DEVICE,
            hardware = Build.HARDWARE,
            androidVersion = Build.VERSION.RELEASE,
            sdkInt = Build.VERSION.SDK_INT,
            buildId = Build.ID,
            fingerprint = Build.FINGERPRINT
        )
    }

    /**
     * 获取堆栈跟踪字符串
     */
    private fun getStackTraceString(exception: Throwable): String {
        return buildString {
            appendLine("Exception: ${exception.javaClass.simpleName}")
            appendLine("Message: ${exception.message}")
            appendLine("Stack Trace:")
            exception.stackTrace.forEach { element ->
                appendLine("  at $element")
            }
            exception.cause?.let { cause ->
                appendLine("\nCaused by: $cause")
            }
        }
    }

    /**
     * 限制崩溃报告文件数量
     */
    private fun limitCrashReportFiles(directory: File) {
        try {
            val files = directory.listFiles { file -> file.extension == "json" } ?: return
            if (files.size > MAX_CRASH_REPORTS) {
                files.sortedBy { it.lastModified() } // 按修改时间排序
                    .take(files.size - MAX_CRASH_REPORTS) // 保留最新的文件
                    .forEach { it.delete() }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to limit crash report files")
        }
    }

    /**
     * 递归删除目录
     */
    private fun deleteDirectory(directory: File): Boolean {
        return try {
            directory.listFiles()?.forEach { file ->
                if (file.isDirectory) {
                    deleteDirectory(file)
                } else {
                    file.delete()
                }
            }
            directory.delete()
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 数据类：设备信息
     */
    data class DeviceInfo(
        val model: String,
        val manufacturer: String,
        val brand: String,
        val product: String,
        val device: String,
        val hardware: String,
        val androidVersion: String,
        val sdkInt: Int,
        val buildId: String,
        val fingerprint: String
    ) {
        fun toMap(): Map<String, Any> {
            return mapOf(
                "model" to model,
                "manufacturer" to manufacturer,
                "brand" to brand,
                "product" to product,
                "device" to device,
                "hardware" to hardware,
                "android_version" to androidVersion,
                "sdk_int" to sdkInt,
                "build_id" to buildId,
                "fingerprint" to fingerprint
            )
        }
    }

    /**
     * 数据类：异常数据
     */
    data class ExceptionData(
        val message: String,
        val stackTrace: String,
        val context: String,
        val timestamp: Long,
        val deviceInfo: DeviceInfo
    ) {
        companion object {
            fun fromJsonString(jsonString: String): ExceptionData? {
                return try {
                    // 简化的JSON解析（实际应该使用Gson或Moshi）
                    null
                } catch (e: Exception) {
                    null
                }
            }

            fun ExceptionData.toJsonString(): String {
                return """{
                    |  "message": "$message",
                    |  "stackTrace": "$stackTrace",
                    |  "context": "$context",
                    |  "timestamp": $timestamp,
                    |  "deviceInfo": ${deviceInfo.toMap()}
                    |}""".trimMargin().replace("\n", "").replace(" ", "")
            }
        }
    }

    /**
     * 数据类：指标摘要
     */
    data class MetricsSummary(
        val timestamp: Long,
        val metrics: Map<String, Double>,
        val warnings: Map<String, Double>,
        val deviceInfo: DeviceInfo
    )

    companion object {
        private const val MAX_CRASH_REPORTS = 50
    }
}