package com.csbaby.kefu.data.local.entity

/**
 * 变体性能数据类
 */
data class VariantPerformance(
    val variantId: String = "",
    val avgAccuracy: Double = 0.0,
    val avgResponseTimeMs: Double = 0.0,
    val recordCount: Int = 0
)