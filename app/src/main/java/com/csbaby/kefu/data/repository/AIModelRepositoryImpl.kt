package com.csbaby.kefu.data.repository

import com.csbaby.kefu.data.local.EntityMapper.toDomain
import com.csbaby.kefu.data.local.EntityMapper.toEntity
import com.csbaby.kefu.data.local.dao.AIModelConfigDao
import com.csbaby.kefu.data.remote.backend.ModelBackendSync
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
    private val modelBackendSync: ModelBackendSync
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
        if (model.isDefault) {
            aiModelConfigDao.clearDefaultModel()
        }
        val id = aiModelConfigDao.insertModel(model.toEntity())
        try {
            modelBackendSync.pushModel(model.copy(id = id))
        } catch (e: Exception) {
            Timber.w(e, "Failed to sync new model to backend")
        }
        return id
    }

    override suspend fun updateModel(model: AIModelConfig) {
        if (model.isDefault) {
            aiModelConfigDao.clearDefaultModel()
        }
        aiModelConfigDao.updateModel(model.toEntity())
        try {
            modelBackendSync.pushModel(model)
        } catch (e: Exception) {
            Timber.w(e, "Failed to sync updated model to backend")
        }
    }

    override suspend fun deleteModel(id: Long) {
        aiModelConfigDao.deleteById(id)
        try {
            modelBackendSync.deleteModel(id)
        } catch (e: Exception) {
            Timber.w(e, "Failed to sync model deletion to backend")
        }
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
}
