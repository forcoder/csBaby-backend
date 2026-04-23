package com.csbaby.kefu

import com.csbaby.kefu.infrastructure.monitoring.PerformanceMonitor
import com.csbaby.kefu.infrastructure.ota.OtaScheduler
import com.csbaby.kefu.infrastructure.reply.ReplyOrchestrator
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Application-level EntryPoint for accessing Hilt-managed dependencies in
 * KefuApplication.onCreate() (which is not a Hilt-managed component).
 * All modules are installed into SingletonComponent, so we access them here.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface AppEntryPoint {
    fun replyOrchestrator(): ReplyOrchestrator
    fun otaScheduler(): OtaScheduler
    fun performanceMonitor(): PerformanceMonitor
}
