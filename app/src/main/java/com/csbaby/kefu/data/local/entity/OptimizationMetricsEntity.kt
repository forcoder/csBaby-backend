package com.csbaby.kefu.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "optimization_metrics",
    indices = [
        Index(value = ["featureKey", "variantId", "date"], unique = true)
    ]
)
data class OptimizationMetricsEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val featureKey: String,
    val variantId: Long,
    val date: String, // YYYY-MM-DD format
    val totalGenerated: Int = 0,
    val totalAccepted: Int = 0,
    val totalModified: Int = 0,
    val totalRejected: Int = 0,
    val avgConfidence: Float = 0f,
    val avgResponseTimeMs: Long = 0L,
    val accuracyScore: Float = 0f
)
