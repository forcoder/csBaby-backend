package com.csbaby.kefu.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "llm_features")
data class LLMFeatureEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val featureKey: String,
    val displayName: String,
    val description: String,
    @ColumnInfo(defaultValue = "1")
    val isEnabled: Boolean = true,
    val defaultVariantId: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
