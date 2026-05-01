package com.csbaby.kefu.data.repository

import com.csbaby.kefu.data.local.EntityMapper.toDomain
import com.csbaby.kefu.data.local.EntityMapper.toEntity
import com.csbaby.kefu.data.local.dao.KeywordRuleDao
import com.csbaby.kefu.data.local.dao.ScenarioDao
import com.csbaby.kefu.data.local.entity.RuleScenarioCrossRef
import com.csbaby.kefu.data.remote.backend.RuleBackendSync
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
    private val ruleBackendSync: RuleBackendSync
) : KeywordRuleRepository {

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
        val id = keywordRuleDao.insertRule(rule.toEntity())
        if (rule.applicableScenarios.isNotEmpty()) {
            rule.applicableScenarios.forEach { scenarioId ->
                scenarioDao.insertRuleScenarioRelation(RuleScenarioCrossRef(id, scenarioId))
            }
        }
        // 同步到后端（不阻塞本地操作）
        try {
            ruleBackendSync.pushRule(rule.copy(id = id))
        } catch (e: Exception) {
            Timber.w(e, "Failed to sync new rule to backend")
        }
        return id
    }

    override suspend fun updateRule(rule: KeywordRule) {
        keywordRuleDao.updateRule(rule.toEntity())
        scenarioDao.deleteRelationsForRule(rule.id)
        rule.applicableScenarios.forEach { scenarioId ->
            scenarioDao.insertRuleScenarioRelation(RuleScenarioCrossRef(rule.id, scenarioId))
        }
        // 同步到后端
        try {
            ruleBackendSync.pushRule(rule)
        } catch (e: Exception) {
            Timber.w(e, "Failed to sync updated rule to backend")
        }
    }

    override suspend fun deleteRule(id: Long) {
        scenarioDao.deleteRelationsForRule(id)
        keywordRuleDao.deleteById(id)
        // 同步到后端
        try {
            ruleBackendSync.deleteRule(id)
        } catch (e: Exception) {
            Timber.w(e, "Failed to sync rule deletion to backend")
        }
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
}
