package com.csbaby.kefu.presentation.screens.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.csbaby.kefu.domain.model.AIModelConfig
import com.csbaby.kefu.domain.repository.AIModelRepository
import com.csbaby.kefu.infrastructure.ai.AIService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ModelUiState(
    val models: List<AIModelConfig> = emptyList(),
    val isLoading: Boolean = false,
    val testResults: Map<Long, Boolean> = emptyMap(),
    val testErrorMessages: Map<Long, String> = emptyMap(),
    // 对话框内直接测试状态
    val dialogTestState: DialogTestState = DialogTestState.Idle
)

/** 对话框内测试连接的状态 */
sealed class DialogTestState {
    data object Idle : DialogTestState()
    data object Testing : DialogTestState()
    data class Success(val message: String) : DialogTestState()
    data class Error(val message: String) : DialogTestState()
}

@HiltViewModel
class ModelViewModel @Inject constructor(
    private val aiModelRepository: AIModelRepository,
    private val aiService: AIService
) : ViewModel() {

    private val _uiState = MutableStateFlow(ModelUiState())
    val uiState: StateFlow<ModelUiState> = _uiState.asStateFlow()

    init {
        loadModels()
    }

    private fun loadModels() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            aiModelRepository.getAllModels().collect { models ->
                _uiState.update { it.copy(models = models, isLoading = false) }
            }
        }
    }

    fun saveModel(model: AIModelConfig) {
        viewModelScope.launch {
            if (model.id == 0L) {
                aiModelRepository.insertModel(model)
            } else {
                aiModelRepository.updateModel(model)
            }
        }
    }

    fun deleteModel(id: Long) {
        viewModelScope.launch {
            aiModelRepository.deleteModel(id)
        }
    }

    fun setDefaultModel(id: Long) {
        viewModelScope.launch {
            aiModelRepository.setDefaultModel(id)
        }
    }

    /** 测试已保存模型的连接（列表页） */
    fun testConnection(modelId: Long) {
        viewModelScope.launch {
            val result = aiService.testModelConnection(modelId)
            if (result.isSuccess) {
                val success = result.getOrDefault(false)
                _uiState.update {
                    it.copy(
                        testResults = it.testResults + (modelId to success),
                        testErrorMessages = it.testErrorMessages - modelId
                    )
                }
            } else {
                val errorMessage = result.exceptionOrNull()?.message ?: "测试失败"
                _uiState.update {
                    it.copy(
                        testResults = it.testResults + (modelId to false),
                        testErrorMessages = it.testErrorMessages + (modelId to errorMessage)
                    )
                }
            }
        }
    }

    /** 用配置直接测试连接（对话框内，无需先保存） */
    fun testConnectionWithConfig(config: AIModelConfig) {
        viewModelScope.launch {
            _uiState.update { it.copy(dialogTestState = DialogTestState.Testing) }
            val result = aiService.testConnection(config)
            if (result.isSuccess) {
                val success = result.getOrDefault(false)
                if (success) {
                    _uiState.update {
                        it.copy(dialogTestState = DialogTestState.Success("连接成功，模型可用"))
                    }
                } else {
                    _uiState.update {
                        it.copy(dialogTestState = DialogTestState.Error("连接失败，模型返回空响应"))
                    }
                }
            } else {
                val errorMessage = result.exceptionOrNull()?.message ?: "测试失败"
                _uiState.update {
                    it.copy(dialogTestState = DialogTestState.Error(errorMessage))
                }
            }
        }
    }

    /** 重置对话框内测试状态 */
    fun resetDialogTestState() {
        _uiState.update { it.copy(dialogTestState = DialogTestState.Idle) }
    }
}
