package com.csbaby.kefu.infrastructure.monitoring

import android.app.Application
import android.os.Build
import android.os.Debug
import android.util.Log
import androidx.annotation.RequiresApi
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.system.measureTimeMillis

/**
 * 应用性能监控器
 * 负责收集和分析应用的性能指标
 */
@Singleton
class AppPerformanceMonitor @Inject constructor(
    private val analyticsTracker: AnalyticsTracker,
    private val crashReporter: CrashReporter
) {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    /**
     * 跟踪应用启动时间
     */
    fun trackStartup(startTime: Long, appName: String = "csbaby") {
        scope.launch {
            try {
                val duration = System.currentTimeMillis() - startTime
                Timber.d("App startup tracked: ${duration}ms for $appName")

                analyticsTracker.trackEvent(
                    eventName = "app_startup",
                    properties = mapOf(
                        "duration_ms" to duration,
                        "app_name" to appName,
                        "build_type" to BuildConfig.BUILD_TYPE,
                        "debuggable" to BuildConfig.DEBUG.toString()
                    )
                )

                // 记录性能指标到崩溃报告系统
                crashReporter.recordMetric("startup_time", duration.toDouble())

            } catch (e: Exception) {
                Timber.e(e, "Failed to track startup performance")
            }
        }
    }

    /**
     * 跟踪内存使用情况
     */
    fun trackMemoryUsage(tag: String = "general") {
        scope.launch {
            try {
                val runtime = Runtime.getRuntime()

                val totalMemory = runtime.totalMemory()
                val freeMemory = runtime.freeMemory()
                val usedMemory = totalMemory - freeMemory
                val maxMemory = runtime.maxMemory()

                val memoryInfo = MemoryInfo(
                    totalMB = totalMemory / 1024 / 1024,
                    usedMB = usedMemory / 1024 / 1024,
                    freeMB = freeMemory / 1024 / 1024,
                    maxMB = maxMemory / 1024 / 1024,
                    usagePercent = (usedMemory * 100 / totalMemory).toFloat()
                )

                Timber.v("Memory usage [$tag]: ${memoryInfo.usedMB}MB/${memoryInfo.maxMB}MB (${memoryInfo.usagePercent}%)")

                analyticsTracker.trackEvent(
                    eventName = "memory_usage",
                    properties = mapOf(
                        "tag" to tag,
                        "total_mb" to memoryInfo.totalMB,
                        "used_mb" to memoryInfo.usedMB,
                        "free_mb" to memoryInfo.freeMB,
                        "max_mb" to memoryInfo.maxMB,
                        "usage_percent" to memoryInfo.usagePercent
                    )
                )

                // 检查是否需要警告
                if (memoryInfo.usagePercent > 80f) {
                    Timber.w("High memory usage detected: ${memoryInfo.usagePercent}%")
                    crashReporter.recordWarning("high_memory_usage", memoryInfo.usagePercent)
                }

            } catch (e: Exception) {
                Timber.e(e, "Failed to track memory usage")
            }
        }
    }

    /**
     * 跟踪网络请求性能
     */
    suspend fun trackNetworkRequest(
        url: String,
        method: String,
        responseCode: Int?,
        durationMs: Long,
        sizeBytes: Long = 0L
    ) {
        try {
            analyticsTracker.trackEvent(
                eventName = "network_request",
                properties = mapOf(
                    "url" to url,
                    "method" to method,
                    "response_code" to (responseCode ?: -1),
                    "duration_ms" to durationMs,
                    "size_bytes" to sizeBytes,
                    "success" to (responseCode in 200..299).toString()
                )
            )

            // 记录性能指标
            crashReporter.recordMetric("network_response_time", durationMs.toDouble())
            crashReporter.recordMetric("network_response_size", sizeBytes.toDouble())

            // 检查慢请求
            if (durationMs > 3000) { // 超过3秒
                Timber.w("Slow network request: ${durationMs}ms for $url")
                crashReporter.recordWarning("slow_network_request", durationMs.toDouble())
            }

        } catch (e: Exception) {
            Timber.e(e, "Failed to track network request performance")
        }
    }

    /**
     * 跟踪UI渲染性能
     */
    fun trackUIRendering(componentName: String, renderTimeMs: Long) {
        scope.launch {
            try {
                analyticsTracker.trackEvent(
                    eventName = "ui_render",
                    properties = mapOf(
                        "component" to componentName,
                        "render_time_ms" to renderTimeMs,
                        "fps" to calculateFPS(renderTimeMs)
                    )
                )

                // 检查帧率
                if (renderTimeMs > 16) { // 低于60FPS
                    Timber.w("Low FPS detected: ${calculateFPS(renderTimeMs)} FPS for $componentName")
                    crashReporter.recordWarning("low_fps", calculateFPS(renderTimeMs).toDouble())
                }

            } catch (e: Exception) {
                Timber.e(e, "Failed to track UI rendering performance")
            }
        }
    }

    /**
     * 跟踪数据库操作性能
     */
    fun trackDatabaseOperation(operation: String, durationMs: Long, affectedRows: Int = 0) {
        scope.launch {
            try {
                analyticsTracker.trackEvent(
                    eventName = "database_operation",
                    properties = mapOf(
                        "operation" to operation,
                        "duration_ms" to durationMs,
                        "affected_rows" to affectedRows
                    )
                )

                // 记录性能指标
                crashReporter.recordMetric("db_operation_time", durationMs.toDouble())

                // 检查慢查询
                if (durationMs > 100) { // 超过100ms
                    Timber.w("Slow database operation: ${durationMs}ms for $operation")
                    crashReporter.recordWarning("slow_db_query", durationMs.toDouble())
                }

            } catch (e: Exception) {
                Timber.e(e, "Failed to track database operation performance")
            }
        }
    }

    /**
     * 跟踪用户交互事件
     */
    fun trackUserInteraction(event: String, screen: String, durationMs: Long = 0) {
        scope.launch {
            try {
                analyticsTracker.trackEvent(
                    eventName = "user_interaction",
                    properties = mapOf(
                        "event" to event,
                        "screen" to screen,
                        "duration_ms" to durationMs,
                        "timestamp" to System.currentTimeMillis()
                    )
                )

            } catch (e: Exception) {
                Timber.e(e, "Failed to track user interaction")
            }
        }
    }

    /**
     * 计算帧率
     */
    private fun calculateFPS(renderTimeMs: Long): Double {
        return if (renderTimeMs > 0) {
            1000.0 / renderTimeMs
        } else {
            0.0
        }
    }

    /**
     * 获取当前性能状态摘要
     */
    fun getPerformanceSummary(): PerformanceSummary {
        return try {
            val runtime = Runtime.getRuntime()
            val usedMemory = runtime.totalMemory() - runtime.freeMemory()
            val maxMemory = runtime.maxMemory()

            PerformanceSummary(
                timestamp = System.currentTimeMillis(),
                memoryUsedMB = usedMemory / 1024 / 1024,
                memoryMaxMB = maxMemory / 1024 / 1024,
                memoryUsagePercent = (usedMemory * 100 / maxMemory).toFloat(),
                availableProcessors = Runtime.getRuntime().availableProcessors(),
                deviceModel = Build.MODEL,
                apiLevel = Build.VERSION.SDK_INT
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to generate performance summary")
            PerformanceSummary.empty()
        }
    }

    /**
     * 数据类：内存信息
     */
    data class MemoryInfo(
        val totalMB: Long,
        val usedMB: Long,
        val freeMB: Long,
        val maxMB: Long,
        val usagePercent: Float
    )

    /**
     * 数据类：性能摘要
     */
    data class PerformanceSummary(
        val timestamp: Long,
        val memoryUsedMB: Long,
        val memoryMaxMB: Long,
        val memoryUsagePercent: Float,
        val availableProcessors: Int,
        val deviceModel: String,
        val apiLevel: Int
    ) {
        companion object {
            fun empty() = PerformanceSummary(
                timestamp = 0L,
                memoryUsedMB = 0L,
                memoryMaxMB = 0L,
                memoryUsagePercent = 0f,
                availableProcessors = 0,
                deviceModel = "",
                apiLevel = 0
            )
        }
    }
}