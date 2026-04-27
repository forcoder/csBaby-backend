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
    @ColumnInfo(defaultValue = "''")
    val featureKey: String,
    @ColumnInfo(defaultValue = "0")
    val variantId: Long,
    @ColumnInfo(defaultValue = "''")
    val date: String, // YYYY-MM-DD format
    @ColumnInfo(defaultValue = "0")
    val totalGenerated: Int = 0,
    @ColumnInfo(defaultValue = "0")
    val totalAccepted: Int = 0,
    @ColumnInfo(defaultValue = "0")
    val totalModified: Int = 0,
    @ColumnInfo(defaultValue = "0")
    val totalRejected: Int = 0,
    @ColumnInfo(defaultValue = "0.0")
    val avgConfidence: Float = 0f,
    @ColumnInfo(defaultValue = "0")
    val avgResponseTimeMs: Long = 0L,
    @ColumnInfo(defaultValue = "0.0")
    val accuracyScore: Float = 0f
)
