package com.csbaby.kefu.infrastructure.monitoring

import android.content.Context
import androidx.annotation.VisibleForTesting
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 性能监控器提供者
 * 提供单例的性能监控器实例
 */
object AppPerformanceMonitorProvider {

    @Volatile
    private var instance: AppPerformanceMonitor? = null

    /**
     * 获取性能监控器实例
     */
    fun get(context: Context): AppPerformanceMonitor {
        return instance ?: synchronized(this) {
            instance ?: createInstance(context).also { instance = it }
        }
    }

    /**
     * 创建新的性能监控器实例（用于测试）
     */
    internal fun createInstance(context: Context): AppPerformanceMonitor {
        val analyticsTracker = AnalyticsTracker(context)
        val crashReporter = CrashReporter(context)
        return AppPerformanceMonitorModule.provideAppPerformanceMonitor(analyticsTracker, crashReporter)
    }

    /**
     * 设置自定义实例（主要用于测试）
     */
    @VisibleForTesting
    internal fun setInstance(monitor: AppPerformanceMonitor?) {
        instance = monitor
    }

    /**
     * 清理实例（主要用于测试）
     */
    @VisibleForTesting
    internal fun clearInstance() {
        instance = null
    }
}