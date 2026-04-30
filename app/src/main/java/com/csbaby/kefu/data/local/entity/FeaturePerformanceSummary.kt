package com.csbaby.kefu.data.local.entity

import androidx.room.ColumnInfo

/**
 * 功能性能摘要数据类
 */
data class FeaturePerformanceSummary(
    @ColumnInfo(name = "featureKey") val featureKey: String = "",
    @ColumnInfo(name = "avgAccuracy") val avgAccuracy: Double = 0.0,
    @ColumnInfo(name = "avgResponseTime") val avgResponseTimeMs: Double = 0.0,
    @ColumnInfo(name = "totalGenerated") val totalGenerated: Int = 0,
    @ColumnInfo(name = "totalAccepted") val totalAccepted: Int = 0,
    @ColumnInfo(name = "totalModified") val totalModified: Int = 0,
    @ColumnInfo(name = "totalRejected") val totalRejected: Int = 0
)