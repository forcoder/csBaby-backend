package com.csbaby.kefu.data.remote.backend

import com.csbaby.kefu.data.local.dao.KeywordRuleDao
import com.csbaby.kefu.data.local.entity.KeywordRuleEntity
import com.csbaby.kefu.domain.model.KeywordRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * 知识库规则后端同步辅助类
 */
class RuleBackendSync(
    private val backendClient: BackendClient,
    private val keywordRuleDao: KeywordRuleDao
) {
    /**
     * 从后端拉取所有规则并保存到本地
     */
    suspend fun pullFromBackend(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val result = backendClient.getRules()
            result.fold(
                onSuccess = { ruleDtos ->
                    val entities = ruleDtos.map { it.toKeywordRuleEntity() }
                    keywordRuleDao.deleteAllRules()
                    keywordRuleDao.insertRules(entities)
                    Timber.d("Pulled ${entities.size} rules from backend")
                    Result.success(entities.size)
                },
                onFailure = { e ->
                    Timber.w(e, "Failed to pull rules from backend")
                    Result.failure(e)
                }
            )
        } catch (e: Exception) {
            Timber.e(e, "Error pulling rules from backend")
            Result.failure(e)
        }
    }

    /**
     * 推送单条规则到后端
     */
    suspend fun pushRule(rule: KeywordRule): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val dto = RuleDto(
                keyword = rule.keyword,
                matchType = rule.matchType.name,
                replyTemplate = rule.replyTemplate,
                category = rule.category,
                targetType = rule.targetType.name,
                targetNames = rule.targetNames.joinToString(",", "[", "]"),
                priority = rule.priority,
                enabled = if (rule.enabled) 1 else 0
            )
            val result = if (rule.id == 0L) {
                backendClient.createRule(dto)
            } else {
                backendClient.updateRule(rule.id, dto)
            }
            result.fold(
                onSuccess = { Timber.d("Rule synced to backend: ${rule.keyword}") },
                onFailure = { Timber.w(it, "Failed to sync rule to backend: ${rule.keyword}") }
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Error pushing rule to backend")
            Result.failure(e)
        }
    }

    /**
     * 从后端删除规则
     */
    suspend fun deleteRule(ruleId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            backendClient.deleteRule(ruleId)
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Error deleting rule from backend")
            Result.failure(e)
        }
    }

    /**
     * 批量导入规则到后端
     */
    suspend fun batchImport(rules: List<KeywordRule>, mode: String = "override"): Result<Int> =
        withContext(Dispatchers.IO) {
            try {
                val dtos = rules.map { rule ->
                    RuleDto(
                        keyword = rule.keyword,
                        matchType = rule.matchType.name,
                        replyTemplate = rule.replyTemplate,
                        category = rule.category,
                        targetType = rule.targetType.name,
                        targetNames = rule.targetNames.joinToString(",", "[", "]"),
                        priority = rule.priority,
                        enabled = if (rule.enabled) 1 else 0
                    )
                }
                val result = backendClient.batchImportRules(dtos, mode)
                result.fold(
                    onSuccess = { resp ->
                        Timber.d("Batch imported ${resp.imported} rules to backend")
                        Result.success(resp.imported)
                    },
                    onFailure = { Result.failure(it) }
                )
            } catch (e: Exception) {
                Timber.e(e, "Error batch importing rules to backend")
                Result.failure(e)
            }
        }

    private fun RuleDto.toKeywordRuleEntity() = KeywordRuleEntity(
        id = id,
        keyword = keyword,
        matchType = matchType,
        replyTemplate = replyTemplate,
        category = category,
        targetType = targetType,
        targetNamesJson = targetNames,
        priority = priority,
        enabled = enabled == 1,
        createdAt = try { createdAt.toLong() } catch (_: Exception) { System.currentTimeMillis() },
        updatedAt = try { updatedAt.toLong() } catch (_: Exception) { System.currentTimeMillis() }
    )
}
