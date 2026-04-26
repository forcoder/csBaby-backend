package com.csbaby.kefu.fakes.ai

import com.csbaby.kefu.domain.model.AIModelConfig
import com.csbaby.kefu.domain.repository.AIModelRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeAIModelRepository : AIModelRepository {

    private val _models = MutableStateFlow<List<AIModelConfig>>(emptyList())
    val models: MutableStateFlow<List<AIModelConfig>> = _models

    var defaultModelId: Long? = 1L

    var getDefaultModelException: Exception? = null
    var getModelByIdException: Exception? = null
    var getAllModelsException: Exception? = null

    var addCostCallCount = 0
    var updateLastUsedCallCount = 0
    var setDefaultModelCallCount = 0

    var lastAddCostModelId: Long? = null
    var lastAddCostAmount: Double? = null
    var lastUpdateLastUsedModelId: Long? = null
    var lastSetDefaultModelId: Long? = null

    fun reset() {
        _models.value = emptyList()
        defaultModelId = 1L
        getDefaultModelException = null
        getModelByIdException = null
        getAllModelsException = null
        addCostCallCount = 0
        updateLastUsedCallCount = 0
        setDefaultModelCallCount = 0
        lastAddCostModelId = null
        lastAddCostAmount = null
        lastUpdateLastUsedModelId = null
        lastSetDefaultModelId = null
    }

    override suspend fun getDefaultModel(): AIModelConfig? {
        getDefaultModelException?.let { throw it }
        return _models.value.find { it.id == defaultModelId && it.isEnabled }
    }

    override suspend fun getModelById(id: Long): AIModelConfig? {
        getModelByIdException?.let { throw it }
        return _models.value.find { it.id == id }
    }

    override fun getAllModels(): Flow<List<AIModelConfig>> {
        getAllModelsException?.let { throw it }
        return _models
    }

    override fun getEnabledModels(): Flow<List<AIModelConfig>> {
        return _models.map { list -> list.filter { it.isEnabled } }
    }

    override suspend fun addCost(modelId: Long, cost: Double) {
        addCostCallCount++
        lastAddCostModelId = modelId
        lastAddCostAmount = cost
        _models.value = _models.value.map { model ->
            if (model.id == modelId) model.copy(monthlyCost = model.monthlyCost + cost)
            else model
        }
    }

    override suspend fun updateLastUsed(modelId: Long) {
        updateLastUsedCallCount++
        lastUpdateLastUsedModelId = modelId
        _models.value = _models.value.map { model ->
            if (model.id == modelId) model.copy(lastUsed = System.currentTimeMillis())
            else model
        }
    }

    override suspend fun setDefaultModel(id: Long) {
        setDefaultModelCallCount++
        lastSetDefaultModelId = id
        defaultModelId = id
    }

    override suspend fun insertModel(model: AIModelConfig): Long {
        val newId = (if (_models.value.isEmpty()) 1L else _models.value.maxOf { it.id } + 1)
        val newModel = model.copy(id = newId)
        _models.value = _models.value + newModel
        return newId
    }

    override suspend fun updateModel(model: AIModelConfig) {
        _models.value = _models.value.map { if (it.id == model.id) model else it }
    }

    override suspend fun deleteModel(id: Long) {
        _models.value = _models.value.filter { it.id != id }
    }

    // Helper methods

    fun setModels(models: List<AIModelConfig>) {
        _models.value = models
    }

    fun addModel(model: AIModelConfig) {
        _models.value = _models.value + model
    }

    fun removeModel(id: Long) {
        _models.value = _models.value.filter { it.id != id }
    }
}
