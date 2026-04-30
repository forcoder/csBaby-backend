package com.csbaby.kefu.data.local.entity

/**
 * 功能性能数据类
 */
data class FeaturePerformance(
    val featureKey: String = "",
    val avgAccuracy: Double = 0.0,
    val avgResponseTimeMs: Double = 0.0,
    val recordCount: Int = 0
)