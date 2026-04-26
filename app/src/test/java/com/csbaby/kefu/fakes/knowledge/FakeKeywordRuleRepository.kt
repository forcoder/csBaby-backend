package com.csbaby.kefu.fakes.knowledge

import com.csbaby.kefu.domain.model.KeywordRule
import com.csbaby.kefu.domain.repository.KeywordRuleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeKeywordRuleRepository : KeywordRuleRepository {

    private val _rules = MutableStateFlow<List<KeywordRule>>(emptyList())
    val rules: MutableStateFlow<List<KeywordRule>> = _rules

    var insertCallCount = 0
    var updateCallCount = 0
    var deleteCallCount = 0

    var lastInsertedRule: KeywordRule? = null
    var lastUpdatedRule: KeywordRule? = null

    fun reset() {
        _rules.value = emptyList()
        insertCallCount = 0
        updateCallCount = 0
        deleteCallCount = 0
        lastInsertedRule = null
        lastUpdatedRule = null
    }

    override fun getAllRules(): Flow<List<KeywordRule>> = _rules

    override fun getEnabledRules(): Flow<List<KeywordRule>> =
        _rules.map { list -> list.filter { it.enabled } }

    override fun getAllCategories(): Flow<List<String>> =
        _rules.map { list -> list.map { it.category }.distinct().sorted() }

    override fun getRulesByCategory(category: String): Flow<List<KeywordRule>> =
        _rules.map { list -> list.filter { it.category == category && it.enabled } }

    override suspend fun getRuleById(id: Long): KeywordRule? =
        _rules.value.find { it.id == id }

    override suspend fun insertRule(rule: KeywordRule): Long {
        insertCallCount++
        val newId = (if (_rules.value.isEmpty()) 1L else _rules.value.maxOf { it.id } + 1)
        val newRule = rule.copy(id = newId)
        lastInsertedRule = newRule
        _rules.value = _rules.value + newRule
        return newId
    }

    override suspend fun updateRule(rule: KeywordRule) {
        updateCallCount++
        lastUpdatedRule = rule
        _rules.value = _rules.value.map { if (it.id == rule.id) rule else it }
    }

    override suspend fun deleteRule(id: Long) {
        deleteCallCount++
        _rules.value = _rules.value.filter { it.id != id }
    }

    override suspend fun deleteAllRules() {
        _rules.value = emptyList()
    }

    override suspend fun getRuleCount(): Int = _rules.value.size

    override suspend fun searchByKeyword(keyword: String): List<KeywordRule> =
        _rules.value.filter {
            it.enabled &&
                (it.keyword.contains(keyword, ignoreCase = true) ||
                 it.replyTemplate.contains(keyword, ignoreCase = true) ||
                 it.category.contains(keyword, ignoreCase = true))
        }

    override fun getRuleCountFlow(): Flow<Int> =
        _rules.map { list -> list.size }

    override suspend fun getScenariosForRule(ruleId: Long): List<Long> =
        _rules.value.find { it.id == ruleId }?.applicableScenarios ?: emptyList()

    override suspend fun updateRuleScenarios(ruleId: Long, scenarioIds: List<Long>) {
        _rules.value = _rules.value.map { rule ->
            if (rule.id == ruleId) rule.copy(applicableScenarios = scenarioIds)
            else rule
        }
    }

    // Helper methods

    fun setRules(rules: List<KeywordRule>) {
        _rules.value = rules
    }

    fun addRule(rule: KeywordRule) {
        _rules.value = _rules.value + rule
    }

    fun removeRule(id: Long) {
        _rules.value = _rules.value.filter { it.id != id }
    }
}