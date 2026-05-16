package com.csbaby.kefu.data.remote.dto

import com.google.gson.annotations.SerializedName

data class ModelConfigDto(
    @SerializedName("id") val id: Int = 0,
    @SerializedName("device_id") val deviceId: String = "",
    @SerializedName("name") val name: String = "",
    @SerializedName("model_type") val modelType: String = "OPENAI",
    @SerializedName("model") val model: String = "",
    @SerializedName("api_key") val apiKey: String = "",
    @SerializedName("api_endpoint") val apiEndpoint: String = "",
    @SerializedName("temperature") val temperature: Double = 0.7,
    @SerializedName("max_tokens") val maxTokens: Int = 2000,
    @SerializedName("is_default") val isDefault: Boolean = false,
    @SerializedName("enabled") val enabled: Boolean = true,
    @SerializedName("created_at") val createdAt: String = "",
    @SerializedName("updated_at") val updatedAt: String = ""
)

data class ModelTestResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("model") val model: String = "",
    @SerializedName("tokens") val tokens: Int = 0
)
