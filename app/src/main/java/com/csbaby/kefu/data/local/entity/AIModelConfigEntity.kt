package com.csbaby.kefu.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ai_model_configs")
data class AIModelConfigEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val modelType: String, // OPENAI, CLAUDE, ZHIPU, TONGYI, CUSTOM
    val modelName: String,
    val model: String = "", // 模型具体名称，如gpt-4、claude-3-opus等
    val apiKey: String,
    val apiEndpoint: String,
    val temperature: Float = 0.7f,
    val maxTokens: Int = 1000,
    val isDefault: Boolean = false,
    val isEnabled: Boolean = true,
    val monthlyCost: Double = 0.0,
    val lastUsed: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis()
)
