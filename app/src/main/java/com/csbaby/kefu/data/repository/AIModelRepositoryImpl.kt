package com.csbaby.kefu.data.repository

import com.csbaby.kefu.data.local.AuthManager
import com.csbaby.kefu.data.local.EntityMapper.toDomain
import com.csbaby.kefu.data.local.EntityMapper.toEntity
import com.csbaby.kefu.data.local.dao.AIModelConfigDao
import com.csbaby.kefu.data.remote.backend.ModelBackendSync
import com.csbaby.kefu.domain.model.AIModelConfig
import com.csbaby.kefu.domain.repository.AIModelRepository
import com.csbaby.kefu.infrastructure.network.NetworkMonitor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AI 模型 Repository — 后端优先架构
 *
 * 读取策略：网络可用时优先从后端获取最新配置，失败时降级到本地
 * 写入策略：网络可用时先写后端成功后更新本地，离线时写本地待同步
 */
@Singleton
class AIModelRepositoryImpl @Inject constructor(
    private val aiModelConfigDao: AIModelConfigDao,
    private val modelBackendSync: ModelBackendSync,
    private val networkMonitor: NetworkMonitor,
    private val authManager: AuthManager
) : AIModelRepository {

    override fun getAllModels(): Flow<List<AIModelConfig>> {
        val tenantId = authManager.getTenantId() ?: ""
        return aiModelConfigDao.getAllModels(tenantId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getEnabledModels(): Flow<List<AIModelConfig>> {
        val tenantId = authManager.getTenantId() ?: ""
        return aiModelConfigDao.getEnabledModels(tenantId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getDefaultModel(): AIModelConfig? {
        // 后端优先：尝试从后端拉取最新配置
        fetchFromBackendIfNeeded()
        val tenantId = authManager.getTenantId() ?: ""
        return aiModelConfigDao.getDefaultModel(tenantId)?.toDomain()
    }

    override suspend fun getModelById(id: Long): AIModelConfig? {
        fetchFromBackendIfNeeded()
        val tenantId = authManager.getTenantId() ?: ""
        return aiModelConfigDao.getModelById(id, tenantId)?.toDomain()
    }

    override suspend fun insertModel(model: AIModelConfig): Long {
        val tenantId = authManager.getTenantId() ?: ""
        if (model.isDefault) {
            aiModelConfigDao.clearDefaultModel(tenantId)
        }

        if (networkMonitor.isNetworkAvailable) {
            try {
                val result = withTimeoutOrNull(BACKEND_TIMEOUT_MS) {
                    modelBackendSync.pushModel(model)
                }
                if (result?.isSuccess == true) {
                    val id = aiModelConfigDao.insertModel(model.toEntity())
                    Timber.d("insertModel: synced to backend first, local id=$id")
                    return id
                }
            } catch (e: Exception) {
                Timber.w(e, "insertModel: backend push failed, caching locally")
            }
        }

        val id = aiModelConfigDao.insertModel(model.toEntity())
        Timber.d("insertModel: cached locally id=$id, will sync later")
        return id
    }

    override suspend fun updateModel(model: AIModelConfig) {
        val tenantId = authManager.getTenantId() ?: ""
        if (model.isDefault) {
            aiModelConfigDao.clearDefaultModel(tenantId)
        }
        aiModelConfigDao.updateModel(model.toEntity())

        if (networkMonitor.isNetworkAvailable) {
            try {
                withTimeoutOrNull(BACKEND_TIMEOUT_MS) {
                    modelBackendSync.pushModel(model)
                }
                Timber.d("updateModel: synced to backend")
            } catch (e: Exception) {
                Timber.w(e, "updateModel: backend sync failed, will retry later")
            }
        } else {
            Timber.d("updateModel: offline, will sync later")
        }
    }

    override suspend fun deleteModel(id: Long) {
        val tenantId = authManager.getTenantId() ?: ""
        aiModelConfigDao.deleteById(id, tenantId)

        if (networkMonitor.isNetworkAvailable) {
            try {
                withTimeoutOrNull(BACKEND_TIMEOUT_MS) {
                    modelBackendSync.deleteModel(id)
                }
                Timber.d("deleteModel: synced to backend")
            } catch (e: Exception) {
                Timber.w(e, "deleteModel: backend sync failed, will retry later")
            }
        } else {
            Timber.d("deleteModel: offline, will sync later")
        }
    }

    override suspend fun setDefaultModel(id: Long) {
        val tenantId = authManager.getTenantId() ?: ""
        aiModelConfigDao.clearDefaultModel(tenantId)
        aiModelConfigDao.setDefaultModel(id, tenantId)
        // 后端同步由 SyncWorker 处理
    }

    override suspend fun updateLastUsed(id: Long) {
        val tenantId = authManager.getTenantId() ?: ""
        aiModelConfigDao.updateLastUsed(id, tenantId, System.currentTimeMillis())
    }

    override suspend fun addCost(id: Long, cost: Double) {
        val tenantId = authManager.getTenantId() ?: ""
        aiModelConfigDao.addCost(id, tenantId, cost)
    }

    /**
     * 网络可用时从后端拉取最新模型配置并更新本地
     */
    private suspend fun fetchFromBackendIfNeeded() {
        if (!networkMonitor.isNetworkAvailable) return
        try {
            val result = withTimeoutOrNull(BACKEND_TIMEOUT_MS) {
                modelBackendSync.pullFromBackend()
            }
            if (result?.isSuccess == true) {
                Timber.d("fetchFromBackend: updated local models from backend")
            }
        } catch (e: Exception) {
            Timber.w(e, "fetchFromBackend: failed, using local cache")
        }
    }

    companion object {
        private const val BACKEND_TIMEOUT_MS = 10_000L
    }
}
