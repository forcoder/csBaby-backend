package com.csbaby.kefu.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "feature_variants",
    foreignKeys = [
        ForeignKey(
            entity = LLMFeatureEntity::class,
            parentColumns = ["id"],
            childColumns = ["featureId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class FeatureVariantEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val featureId: Long,
    val variantName: String,
    val variantType: String, // PROMPT / MODEL / STRATEGY
    @ColumnInfo(defaultValue = "''")
    val systemPrompt: String = "",
    @ColumnInfo(defaultValue = "''")
    val userPromptTemplate: String = "",
    val modelId: Long? = null,
    val temperature: Float? = null,
    val maxTokens: Int? = null,
    @ColumnInfo(defaultValue = "'{}'")
    val strategyConfig: String = "{}",
    @ColumnInfo(defaultValue = "0")
    val isActive: Boolean = false,
    @ColumnInfo(defaultValue = "0")
    val trafficPercentage: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)
