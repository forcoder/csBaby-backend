package com.csbaby.kefu.data.local.entity

import androidx.room.ColumnInfo

/**
 * 变体性能数据类
 */
data class VariantPerformance(
    @ColumnInfo(name = "variantId") val variantId: Long = 0,
    @ColumnInfo(name = "avgAccuracy") val avgAccuracy: Double = 0.0,
    @ColumnInfo(name = "avgResponseTime") val avgResponseTimeMs: Double = 0.0,
    @ColumnInfo(name = "recordCount") val recordCount: Int = 0
)