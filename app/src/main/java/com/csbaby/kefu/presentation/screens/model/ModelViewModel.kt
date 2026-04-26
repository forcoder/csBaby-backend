package com.csbaby.kefu.presentation.screens.model

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.csbaby.kefu.domain.model.AIModelConfig
import com.csbaby.kefu.domain.model.ModelType
import com.csbaby.kefu.domain.repository.AIModelRepository
import com.csbaby.kefu.infrastructure.ai.AIService
import com.google.gson.JsonParser
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import timber.log.Timber

data class ModelUiState(
    val models: List<AIModelConfig> = emptyList(),
    val isLoading: Boolean = false,
    val testResults: Map<Long, Boolean> = emptyMap(),
    val testErrorMessages: Map<Long, String> = emptyMap(),
    val dialogTestState: DialogTestState = DialogTestState.Idle,
    val importStatus: ImportStatus = ImportStatus.IDLE
)

sealed class DialogTestState {
    data object Idle : DialogTestState()
    data object Testing : DialogTestState()
    data class Success(val message: String) : DialogTestState()
    data class Error(val message: String) : DialogTestState()
}

sealed class ImportStatus {
    data object IDLE : ImportStatus()
    data object IMPORTING : ImportStatus()
    data class Success(val message: String, val count: Int) : ImportStatus()
    data class Error(val message: String) : ImportStatus()
}

enum class ModelImportMode {
    APPEND,
    OVERRIDE
}

@HiltViewModel
class ModelViewModel @Inject constructor(
    private val aiModelRepository: AIModelRepository,
    private val aiService: AIService,
    @ApplicationContext private val context: Context
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

    fun resetDialogTestState() {
        _uiState.update { it.copy(dialogTestState = DialogTestState.Idle) }
    }

    fun resetImportStatus() {
        _uiState.update { it.copy(importStatus = ImportStatus.IDLE) }
    }

    fun importModels(uri: Uri, mode: ModelImportMode) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(importStatus = ImportStatus.IMPORTING) }

                if (mode == ModelImportMode.OVERRIDE) {
                    val currentModels = _uiState.value.models
                    currentModels.forEach { model ->
                        aiModelRepository.deleteModel(model.id)
                    }
                }

                val jsonString = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    inputStream.bufferedReader().readText()
                } ?: throw Exception("无法读取文件")

                val configs = parseModelConfigs(jsonString)

                if (configs.isEmpty()) {
                    _uiState.update { it.copy(importStatus = ImportStatus.Error("文件中没有有效的模型配置")) }
                    return@launch
                }

                var successCount = 0
                configs.forEach { config ->
                    try {
                        aiModelRepository.insertModel(config)
                        successCount++
                    } catch (e: Exception) {
                        Timber.w(e, "导入模型配置失败: ${config.modelName}")
                    }
                }

                if (successCount > 0) {
                    val modeText = if (mode == ModelImportMode.OVERRIDE) "覆盖导入" else "追加导入"
                    _uiState.update {
                        it.copy(importStatus = ImportStatus.Success(
                            "$modeText 完成：成功导入 $successCount/${configs.size} 个模型配置",
                            successCount
                        ))
                    }
                } else {
                    _uiState.update { it.copy(importStatus = ImportStatus.Error("导入失败，没有成功导入任何配置")) }
                }
            } catch (e: Exception) {
                Timber.e(e, "导入模型配置失败")
                _uiState.update { it.copy(importStatus = ImportStatus.Error("导入失败: ${e.message}")) }
            }
        }
    }

    private fun parseModelConfigs(jsonString: String): List<AIModelConfig> {
        val configList = mutableListOf<AIModelConfig>()
        try {
            val element = JsonParser.parseString(jsonString)
            when {
                element.isJsonArray -> {
                    element.asJsonArray.forEach { item ->
                        parseSingleModel(item.asJsonObject)?.let { configList.add(it) }
                    }
                }
                element.isJsonObject -> {
                    val obj = element.asJsonObject
                    val arrayKey = obj.keySet().firstOrNull {
                        it in listOf("models", "configs", "data", "items")
                    }
                    if (arrayKey != null && obj.get(arrayKey).isJsonArray) {
                        obj.getAsJsonArray(arrayKey).forEach { item ->
                            parseSingleModel(item.asJsonObject)?.let { configList.add(it) }
                        }
                    } else {
                        parseSingleModel(obj)?.let { configList.add(it) }
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "解析 JSON 失败")
        }
        return configList
    }

    private fun parseSingleModel(obj: com.google.gson.JsonObject): AIModelConfig? {
        return try {
            val modelName = obj.getString("modelName")
                ?: obj.getString("name")
                ?: return null

            val apiKey = obj.getString("apiKey")
                ?: obj.getString("api_key")
                ?: ""

            val apiEndpoint = obj.getString("apiEndpoint")
                ?: obj.getString("api_endpoint")
                ?: obj.getString("endpoint")
                ?: ""

            val model = obj.getString("model") ?: ""
            val modelTypeStr = obj.getString("modelType")
                ?: obj.getString("model_type")
                ?: obj.getString("type")
                ?: "OPENAI"

            val modelType = try {
                ModelType.valueOf(modelTypeStr.uppercase())
            } catch (e: Exception) {
                ModelType.OPENAI
            }

            val temperature = obj.getFloat("temperature") ?: 0.7f
            val maxTokens = obj.getInt("maxTokens")
                ?: obj.getInt("max_tokens")
                ?: 1000
            val isDefault = obj.getBoolean("isDefault")
                ?: obj.getBoolean("is_default")
                ?: false
            val isEnabled = obj.getBoolean("isEnabled")
                ?: obj.getBoolean("is_enabled")
                ?: true

            AIModelConfig(
                id = 0,
                modelType = modelType,
                modelName = modelName,
                model = model,
                apiKey = apiKey,
                apiEndpoint = apiEndpoint,
                temperature = temperature,
                maxTokens = maxTokens,
                isDefault = isDefault,
                isEnabled = isEnabled
            )
        } catch (e: Exception) {
            Timber.w(e, "解析单个模型配置失败")
            null
        }
    }

    private fun com.google.gson.JsonObject.getString(key: String): String? {
        return try {
            if (has(key) && !get(key).isJsonNull) get(key).asString else null
        } catch (e: Exception) { null }
    }

    private fun com.google.gson.JsonObject.getInt(key: String): Int? {
        return try {
            if (has(key) && !get(key).isJsonNull) get(key).asInt else null
        } catch (e: Exception) { null }
    }

    private fun com.google.gson.JsonObject.getFloat(key: String): Float? {
        return try {
            if (has(key) && !get(key).isJsonNull) get(key).asFloat else null
        } catch (e: Exception) { null }
    }

    private fun com.google.gson.JsonObject.getBoolean(key: String): Boolean? {
        return try {
            if (has(key) && !get(key).isJsonNull) get(key).asBoolean else null
        } catch (e: Exception) { null }
    }
}
