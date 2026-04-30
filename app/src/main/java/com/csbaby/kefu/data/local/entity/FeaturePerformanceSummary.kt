package com.csbaby.kefu.data.local.entity

/**
 * 功能性能摘要数据类
 */
data class FeaturePerformanceSummary(
    val featureKey: String = "",
    val avgAccuracy: Double = 0.0,
    val avgResponseTimeMs: Double = 0.0,
    val recordCount: Int = 0,
    val totalAccepted: Int = 0,
    val totalModified: Int = 0,
    val totalRejected: Int = 0
)