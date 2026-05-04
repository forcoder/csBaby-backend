package com.csbaby.kefu.data.remote.backend

import com.csbaby.kefu.data.local.AuthManager
import com.csbaby.kefu.data.local.dao.AIModelConfigDao
import com.csbaby.kefu.data.local.entity.AIModelConfigEntity
import com.csbaby.kefu.domain.model.AIModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * AI 模型配置后端同步辅助类
 */
class ModelBackendSync(
    private val backendClient: BackendClient,
    private val aiModelConfigDao: AIModelConfigDao,
    private val authManager: AuthManager
) {
    suspend fun pullFromBackend(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val tenantId = authManager.getTenantId() ?: ""
            val result = backendClient.getModels()
            result.fold(
                onSuccess = { modelDtos ->
                    // 保存现有的 monthlyCost 和 lastUsed
                    val existingModels = aiModelConfigDao.getAllModelsList(tenantId)
                    val costMap = existingModels.associate { it.id to it.monthlyCost }
                    val lastUsedMap = existingModels.associate { it.id to it.lastUsed }

                    val entities = modelDtos.map { dto ->
                        val entity = dto.toEntity().copy(tenantId = tenantId)
                        // 保留本地累计数据
                        entity.copy(
                            monthlyCost = costMap[entity.id] ?: 0.0,
                            lastUsed = lastUsedMap[entity.id] ?: System.currentTimeMillis()
                        )
                    }
                    // 清除并重新插入
                    existingModels.forEach { aiModelConfigDao.deleteById(it.id, tenantId) }
                    entities.forEach { aiModelConfigDao.insertModel(it) }
                    Timber.d("Pulled ${entities.size} models from backend")
                    Result.success(entities.size)
                },
                onFailure = { e ->
                    Timber.w(e, "Failed to pull models from backend")
                    Result.failure(e)
                }
            )
        } catch (e: Exception) {
            Timber.e(e, "Error pulling models from backend")
            Result.failure(e)
        }
    }

    suspend fun pushModel(model: AIModelConfig): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val dto = ModelConfigDto(
                name = model.modelName,
                modelType = model.modelType.name,
                model = model.model,
                apiKey = model.apiKey,
                apiEndpoint = model.apiEndpoint,
                temperature = model.temperature,
                maxTokens = model.maxTokens,
                isDefault = if (model.isDefault) 1 else 0,
                enabled = if (model.isEnabled) 1 else 0
            )
            val result = if (model.id == 0L) {
                backendClient.createModel(dto)
            } else {
                backendClient.updateModel(model.id, dto)
            }
            result.fold(
                onSuccess = { Timber.d("Model synced to backend: ${model.modelName}") },
                onFailure = { Timber.w(it, "Failed to sync model to backend: ${model.modelName}") }
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Error pushing model to backend")
            Result.failure(e)
        }
    }

    suspend fun deleteModel(modelId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            backendClient.deleteModel(modelId)
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Error deleting model from backend")
            Result.failure(e)
        }
    }

    private fun ModelConfigDto.toEntity() = AIModelConfigEntity(
        id = id,
        modelType = modelType,
        modelName = name,
        model = model,
        apiKey = apiKey,
        apiEndpoint = apiEndpoint,
        temperature = temperature,
        maxTokens = maxTokens,
        isDefault = isDefault == 1,
        isEnabled = enabled == 1,
        monthlyCost = 0.0,
        lastUsed = System.currentTimeMillis(),
        createdAt = System.currentTimeMillis()
    )
}
