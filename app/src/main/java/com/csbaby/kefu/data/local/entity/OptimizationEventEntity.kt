package com.csbaby.kefu.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "optimization_events",
    indices = [
        Index(value = ["featureKey"])
    ]
)
data class OptimizationEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val featureKey: String,
    val eventType: String, // AUTO_OPTIMIZE / MANUAL_TUNE / A_B_TEST
    val oldConfig: String = "",
    val newConfig: String = "",
    val reason: String,
    val triggeredBy: String, // SYSTEM / USER
    val createdAt: Long = System.currentTimeMillis()
)
