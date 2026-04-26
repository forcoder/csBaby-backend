package com.csbaby.kefu.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
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
    ],
    indices = [Index(value = ["featureId"])]
)
data class FeatureVariantEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val featureId: Long,
    val variantName: String,
    val variantType: String, // PROMPT / MODEL / STRATEGY
    val systemPrompt: String = "",
    val userPromptTemplate: String = "",
    val modelId: Long? = null,
    val temperature: Float? = null,
    val maxTokens: Int? = null,
    val strategyConfig: String = "{}",
    val isActive: Boolean = false,
    val trafficPercentage: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)
