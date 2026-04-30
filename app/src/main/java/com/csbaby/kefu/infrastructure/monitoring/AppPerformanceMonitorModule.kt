package com.csbaby.kefu.infrastructure.monitoring

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 性能监控器依赖注入模块
 */
@Module
@InstallIn(SingletonComponent::class)
object AppPerformanceMonitorModule {

    @Provides
    @Singleton
    fun provideAnalyticsTracker(@ApplicationContext context: Context): AnalyticsTracker {
        return AnalyticsTracker(context)
    }

    @Provides
    @Singleton
    fun provideCrashReporter(@ApplicationContext context: Context): CrashReporter {
        return CrashReporter(context)
    }

    @Provides
    @Singleton
    fun provideAppPerformanceMonitor(
        @ApplicationContext context: Context,
        analyticsTracker: AnalyticsTracker,
        crashReporter: CrashReporter
    ): AppPerformanceMonitor {
        return AppPerformanceMonitor(analyticsTracker, crashReporter)
    }
}