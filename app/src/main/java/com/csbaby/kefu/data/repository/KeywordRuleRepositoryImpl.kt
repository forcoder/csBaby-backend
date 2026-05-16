package com.csbaby.kefu.data.repository

import com.csbaby.kefu.data.local.EntityMapper.toDomain
import com.csbaby.kefu.data.local.EntityMapper.toEntity
import com.csbaby.kefu.data.local.dao.KeywordRuleDao
import com.csbaby.kefu.data.local.dao.ScenarioDao
import com.csbaby.kefu.data.local.entity.RuleScenarioCrossRef
import com.csbaby.kefu.data.remote.CsbabyApiService
import com.csbaby.kefu.data.remote.DeviceManager
import com.csbaby.kefu.data.remote.dto.toDomain
import com.csbaby.kefu.data.remote.dto.toDto
import com.csbaby.kefu.domain.model.KeywordRule
import com.csbaby.kefu.domain.repository.KeywordRuleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KeywordRuleRepositoryImpl @Inject constructor(
    private val keywordRuleDao: KeywordRuleDao,
    private val scenarioDao: ScenarioDao,
    private val apiService: CsbabyApiService,
    private val deviceManager: DeviceManager
) : KeywordRuleRepository {

    override fun getAllRules(): Flow<List<KeywordRule>> {
        return keywordRuleDao.getAllRules().map { entities ->
            entities.map { entity ->
                val scenarios = runBlocking { scenarioDao.getScenarioIdsForRule(entity.id) }
                entity.toDomain(scenarios)
            }
        }
    }

    override fun getEnabledRules(): Flow<List<KeywordRule>> {
        return keywordRuleDao.getEnabledRules().map { entities ->
            entities.map { entity ->
                val scenarios = runBlocking { scenarioDao.getScenarioIdsForRule(entity.id) }
                entity.toDomain(scenarios)
            }
        }
    }

    override fun getRulesByCategory(category: String): Flow<List<KeywordRule>> {
        return keywordRuleDao.getRulesByCategory(category).map { entities ->
            entities.map { entity ->
                val scenarios = runBlocking { scenarioDao.getScenarioIdsForRule(entity.id) }
                entity.toDomain(scenarios)
            }
        }
    }

    override fun getAllCategories(): Flow<List<String>> = keywordRuleDao.getAllCategories()

    override suspend fun getRuleById(id: Long): KeywordRule? {
        return keywordRuleDao.getRuleById(id)?.let { entity ->
            val scenarios = scenarioDao.getScenarioIdsForRule(entity.id)
            entity.toDomain(scenarios)
        }
    }

    override suspend fun searchByKeyword(keyword: String): List<KeywordRule> {
        return keywordRuleDao.searchByKeyword(keyword).map { entity ->
            val scenarios = scenarioDao.getScenarioIdsForRule(entity.id)
            entity.toDomain(scenarios)
        }
    }

    override suspend fun insertRule(rule: KeywordRule): Long {
        return try {
            deviceManager.ensureRegistered()
            val dto = rule.copy(id = 0).toDto()
            val created = apiService.createRule(dto)
            val ruleWithServerId = rule.copy(id = created.id.toLong())
            keywordRuleDao.insertRule(ruleWithServerId.toEntity())
        } catch (e: Exception) {
            Timber.w(e, "Failed to sync rule to server, saving locally only")
            keywordRuleDao.insertRule(rule.toEntity())
        }
    }

    override suspend fun updateRule(rule: KeywordRule) {
        try {
            deviceManager.ensureRegistered()
            apiService.updateRule(rule.id.toInt(), rule.toDto())
        } catch (e: Exception) {
            Timber.w(e, "Failed to sync rule update to server")
        }
        keywordRuleDao.updateRule(rule.toEntity())
    }

    override suspend fun deleteRule(id: Long) {
        try {
            deviceManager.ensureRegistered()
            apiService.deleteRule(id.toInt())
        } catch (e: Exception) {
            Timber.w(e, "Failed to sync rule delete to server")
        }
        scenarioDao.deleteRelationsForRule(id)
        keywordRuleDao.deleteById(id)
    }

    override suspend fun deleteAllRules() {
        scenarioDao.deleteAllRelations()
        keywordRuleDao.deleteAllRules()
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

    /**
     * 从服务器同步所有规则到本地缓存
     */
    suspend fun syncFromServer(): Result<Int> {
        return try {
            deviceManager.ensureRegistered()
            val serverRules = apiService.getRules()
            val localRules = keywordRuleDao.getAllRules()

            // Clear local and replace with server data
            scenarioDao.deleteAllRelations()
            keywordRuleDao.deleteAllRules()

            var count = 0
            serverRules.forEach { dto ->
                try {
                    val domain = dto.toDomain()
                    keywordRuleDao.insertRule(domain.toEntity())
                    count++
                } catch (e: Exception) {
                    Timber.e(e, "Failed to import rule from server: ${dto.keyword}")
                }
            }
            Timber.i("Synced $count rules from server")
            Result.success(count)
        } catch (e: Exception) {
            Timber.e(e, "Failed to sync rules from server")
            Result.failure(e)
        }
    }

    /**
     * 强制从服务器刷新
     */
    suspend fun forceRefresh(): Result<Int> = syncFromServer()
}
