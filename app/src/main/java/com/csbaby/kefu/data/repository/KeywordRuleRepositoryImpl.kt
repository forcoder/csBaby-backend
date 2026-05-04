package com.csbaby.kefu.data.repository

import com.csbaby.kefu.data.local.EntityMapper.toDomain
import com.csbaby.kefu.data.local.EntityMapper.toEntity
import com.csbaby.kefu.data.local.dao.KeywordRuleDao
import com.csbaby.kefu.data.local.dao.ScenarioDao
import com.csbaby.kefu.data.local.entity.RuleScenarioCrossRef
import com.csbaby.kefu.data.remote.backend.RuleBackendSync
import com.csbaby.kefu.domain.model.KeywordRule
import com.csbaby.kefu.domain.repository.KeywordRuleRepository
import com.csbaby.kefu.infrastructure.network.NetworkMonitor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 知识库规则 Repository — 后端优先架构
 *
 * 读取策略：
 * - 网络可用时优先从后端 API 获取，失败时降级到本地 Room
 * - 写入策略：
 * - 网络可用时先写后端，成功后更新本地
 * - 网络不可用时写入本地，待网络恢复后由 SyncWorker 同步
 */
@Singleton
class KeywordRuleRepositoryImpl @Inject constructor(
    private val keywordRuleDao: KeywordRuleDao,
    private val scenarioDao: ScenarioDao,
    private val ruleBackendSync: RuleBackendSync,
    private val networkMonitor: NetworkMonitor
) : KeywordRuleRepository {

    // ========== 查询操作：后端优先，本地降级 ==========

    override fun getAllRules(): Flow<List<KeywordRule>> {
        return keywordRuleDao.getAllRules().map { entities ->
            entities.map { entity ->
                val scenarios = scenarioDao.getScenarioIdsForRule(entity.id)
                entity.toDomain(scenarios)
            }
        }
    }

    override fun getEnabledRules(): Flow<List<KeywordRule>> {
        return keywordRuleDao.getEnabledRules().map { entities ->
            entities.map { entity ->
                val scenarios = scenarioDao.getScenarioIdsForRule(entity.id)
                entity.toDomain(scenarios)
            }
        }
    }

    override fun getRulesByCategory(category: String): Flow<List<KeywordRule>> {
        return keywordRuleDao.getRulesByCategory(category).map { entities ->
            entities.map { entity ->
                val scenarios = scenarioDao.getScenarioIdsForRule(entity.id)
                entity.toDomain(scenarios)
            }
        }
    }

    override fun getAllCategories(): Flow<List<String>> = keywordRuleDao.getAllCategories()

    override suspend fun getRuleById(id: Long): KeywordRule? {
        // 后端优先：尝试从后端获取
        if (networkMonitor.isNetworkAvailable) {
            try {
                val backendResult = withTimeoutOrNull(BACKEND_TIMEOUT_MS) {
                    ruleBackendSync.pullFromBackend()
                }
                if (backendResult?.isSuccess == true) {
                    Timber.d("getRuleById: using backend data")
                }
            } catch (e: Exception) {
                Timber.w(e, "getRuleById: backend fetch failed, using local")
            }
        }
        return keywordRuleDao.getRuleById(id)?.let { entity ->
            val scenarios = scenarioDao.getScenarioIdsForRule(entity.id)
            entity.toDomain(scenarios)
        }
    }

    override suspend fun searchByKeyword(keyword: String): List<KeywordRule> {
        // 规则搜索通常需要全量数据，先确保本地与后端同步
        if (networkMonitor.isNetworkAvailable) {
            try {
                withTimeoutOrNull(BACKEND_TIMEOUT_MS) {
                    ruleBackendSync.pullFromBackend()
                }
            } catch (e: Exception) {
                Timber.w(e, "searchByKeyword: backend sync failed, using local")
            }
        }
        return keywordRuleDao.searchByKeyword(keyword).map { entity ->
            val scenarios = scenarioDao.getScenarioIdsForRule(entity.id)
            entity.toDomain(scenarios)
        }
    }

    // ========== 写入操作：后端优先，本地兜底 ==========

    override suspend fun insertRule(rule: KeywordRule): Long {
        if (networkMonitor.isNetworkAvailable) {
            try {
                val result = withTimeoutOrNull(BACKEND_TIMEOUT_MS) {
                    ruleBackendSync.pushRule(rule)
                }
                if (result?.isSuccess == true) {
                    // 后端成功，写入本地
                    val id = keywordRuleDao.insertRule(rule.toEntity())
                    if (rule.applicableScenarios.isNotEmpty()) {
                        rule.applicableScenarios.forEach { scenarioId ->
                            scenarioDao.insertRuleScenarioRelation(RuleScenarioCrossRef(id, scenarioId))
                        }
                    }
                    Timber.d("insertRule: synced to backend first, local id=$id")
                    return id
                }
            } catch (e: Exception) {
                Timber.w(e, "insertRule: backend push failed, caching locally")
            }
        }
        // 网络不可用或后端失败：写入本地
        val id = keywordRuleDao.insertRule(rule.toEntity())
        if (rule.applicableScenarios.isNotEmpty()) {
            rule.applicableScenarios.forEach { scenarioId ->
                scenarioDao.insertRuleScenarioRelation(RuleScenarioCrossRef(id, scenarioId))
            }
        }
        Timber.d("insertRule: cached locally id=$id, will sync later")
        return id
    }

    override suspend fun updateRule(rule: KeywordRule) {
        // 先更新本地（保证 UI 即时响应）
        keywordRuleDao.updateRule(rule.toEntity())
        scenarioDao.deleteRelationsForRule(rule.id)
        rule.applicableScenarios.forEach { scenarioId ->
            scenarioDao.insertRuleScenarioRelation(RuleScenarioCrossRef(rule.id, scenarioId))
        }

        // 网络可用时同步到后端
        if (networkMonitor.isNetworkAvailable) {
            try {
                withTimeoutOrNull(BACKEND_TIMEOUT_MS) {
                    ruleBackendSync.pushRule(rule)
                }
                Timber.d("updateRule: synced to backend")
            } catch (e: Exception) {
                Timber.w(e, "updateRule: backend sync failed, will retry later")
            }
        } else {
            Timber.d("updateRule: offline, will sync later")
        }
    }

    override suspend fun deleteRule(id: Long) {
        // 先删除本地
        scenarioDao.deleteRelationsForRule(id)
        keywordRuleDao.deleteById(id)

        // 网络可用时同步到后端
        if (networkMonitor.isNetworkAvailable) {
            try {
                withTimeoutOrNull(BACKEND_TIMEOUT_MS) {
                    ruleBackendSync.deleteRule(id)
                }
                Timber.d("deleteRule: synced to backend")
            } catch (e: Exception) {
                Timber.w(e, "deleteRule: backend sync failed, will retry later")
            }
        } else {
            Timber.d("deleteRule: offline, will sync later")
        }
    }

    override suspend fun deleteAllRules() {
        scenarioDao.deleteAllRelations()
        keywordRuleDao.deleteAllRules()
        // 后端批量同步由 SyncWorker 处理
        Timber.d("deleteAllRules: local cleared")
    }

    override suspend fun getRuleCount(): Int = keywordRuleDao.getRuleCount()

    override fun getRuleCountFlow(): Flow<Int> = keywordRuleDao.getRuleCountFlow()

    override suspend fun getScenariosForRule(ruleId: Long): List<Long> {
        return scenarioDao.getScenarioIdsForRule(ruleId)
    }

    override suspend fun updateRuleScenarios(ruleId: Long, scenarioIds: List<Long>) {
        scenarioDao.deleteRelationsForRule(ruleId)
        scenarioIds.forEach { scenarioId ->
            scenarioDao.insertRuleScenarioRelation(RuleScenarioCrossRef(ruleId, scenarioId))
        }
    }

    companion object {
        private const val BACKEND_TIMEOUT_MS = 10_000L
    }
}
