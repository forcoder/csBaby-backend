package com.csbaby.kefu.data.repository

import com.csbaby.kefu.data.local.EntityMapper.toDomain
import com.csbaby.kefu.data.local.EntityMapper.toEntity
import com.csbaby.kefu.data.local.dao.AIModelConfigDao
import com.csbaby.kefu.data.remote.CsbabyApiService
import com.csbaby.kefu.data.remote.DeviceManager
import com.csbaby.kefu.data.remote.dto.toDomain
import com.csbaby.kefu.data.remote.dto.toDto
import com.csbaby.kefu.domain.model.AIModelConfig
import com.csbaby.kefu.domain.repository.AIModelRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AIModelRepositoryImpl @Inject constructor(
    private val aiModelConfigDao: AIModelConfigDao,
    private val apiService: CsbabyApiService,
    private val deviceManager: DeviceManager
) : AIModelRepository {

    override fun getAllModels(): Flow<List<AIModelConfig>> {
        return aiModelConfigDao.getAllModels().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getEnabledModels(): Flow<List<AIModelConfig>> {
        return aiModelConfigDao.getEnabledModels().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getDefaultModel(): AIModelConfig? {
        return aiModelConfigDao.getDefaultModel()?.toDomain()
    }

    override suspend fun getModelById(id: Long): AIModelConfig? {
        return aiModelConfigDao.getModelById(id)?.toDomain()
    }

    override suspend fun insertModel(model: AIModelConfig): Long {
        return try {
            deviceManager.ensureRegistered()
            val dto = model.copy(id = 0).toDto()
            val created = apiService.createModel(dto)
            val modelWithServerId = model.copy(id = created.id.toLong())
            if (modelWithServerId.isDefault) {
                aiModelConfigDao.clearDefaultModel()
            }
            aiModelConfigDao.insertModel(modelWithServerId.toEntity())
        } catch (e: Exception) {
            Timber.w(e, "Failed to sync model to server, saving locally only")
            if (model.isDefault) {
                aiModelConfigDao.clearDefaultModel()
            }
            aiModelConfigDao.insertModel(model.toEntity())
        }
    }

    override suspend fun updateModel(model: AIModelConfig) {
        try {
            deviceManager.ensureRegistered()
            apiService.updateModel(model.id.toInt(), model.toDto())
        } catch (e: Exception) {
            Timber.w(e, "Failed to sync model update to server")
        }
        if (model.isDefault) {
            aiModelConfigDao.clearDefaultModel()
        }
        aiModelConfigDao.updateModel(model.toEntity())
    }

    override suspend fun deleteModel(id: Long) {
        try {
            deviceManager.ensureRegistered()
            apiService.deleteModel(id.toInt())
        } catch (e: Exception) {
            Timber.w(e, "Failed to sync model delete to server")
        }
        aiModelConfigDao.deleteById(id)
    }

    override suspend fun setDefaultModel(id: Long) {
        aiModelConfigDao.clearDefaultModel()
        aiModelConfigDao.setDefaultModel(id)
    }

    override suspend fun updateLastUsed(id: Long) {
        aiModelConfigDao.updateLastUsed(id, System.currentTimeMillis())
    }

    override suspend fun addCost(id: Long, cost: Double) {
        aiModelConfigDao.addCost(id, cost)
    }

    /**
     * 从服务器同步所有模型配置到本地缓存
     */
    suspend fun syncFromServer(): Result<Int> {
        return try {
            deviceManager.ensureRegistered()
            val serverModels = apiService.getModels()

            aiModelConfigDao.deleteAllModels()

            var count = 0
            serverModels.forEach { dto ->
                try {
                    val domain = dto.toDomain()
                    aiModelConfigDao.insertModel(domain.toEntity())
                    count++
                } catch (e: Exception) {
                    Timber.e(e, "Failed to import model from server: ${dto.name}")
                }
            }
            Timber.i("Synced $count models from server")
            Result.success(count)
        } catch (e: Exception) {
            Timber.e(e, "Failed to sync models from server")
            Result.failure(e)
        }
    }

    suspend fun forceRefresh(): Result<Int> = syncFromServer()
}
