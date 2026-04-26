package com.csbaby.kefu.presentation.screens.model

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.csbaby.kefu.R
import com.csbaby.kefu.domain.model.AIModelConfig
import com.csbaby.kefu.domain.model.ModelType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelScreen(
    viewModel: ModelViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var editingModel by remember { mutableStateOf<AIModelConfig?>(null) }
    var showImportModeDialog by remember { mutableStateOf(false) }
    var pendingImportMode by remember { mutableStateOf(ModelImportMode.APPEND) }
    val snackbarHostState = remember { SnackbarHostState() }

    // 文件选择器
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.importModels(uri, pendingImportMode)
        }
    }

    // 监听导入状态，显示 Snackbar
    LaunchedEffect(uiState.importStatus) {
        when (val status = uiState.importStatus) {
            is ImportStatus.Success -> {
                snackbarHostState.showSnackbar(status.message)
                viewModel.resetImportStatus()
            }
            is ImportStatus.Error -> {
                snackbarHostState.showSnackbar(status.message)
                viewModel.resetImportStatus()
            }
            else -> { /* 不处理 */ }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.model_config)) },
                actions = {
                    IconButton(
                        onClick = { showImportModeDialog = true },
                        enabled = uiState.importStatus !is ImportStatus.IMPORTING
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileUpload,
                            contentDescription = "导入配置"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true }
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Model")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(uiState.models) { model ->
                ModelItem(
                    model = model,
                    testResult = uiState.testResults[model.id],
                    testErrorMessage = uiState.testErrorMessages[model.id],
                    onEdit = { editingModel = model },
                    onDelete = { viewModel.deleteModel(model.id) },
                    onSetDefault = { viewModel.setDefaultModel(model.id) },
                    onTest = { viewModel.testConnection(model.id) }
                )
            }
        }
    }

    // Add/Edit Dialog
    if (showAddDialog || editingModel != null) {
        ModelEditDialog(
            model = editingModel,
            dialogTestState = uiState.dialogTestState,
            onDismiss = {
                showAddDialog = false
                editingModel = null
                viewModel.resetDialogTestState()
            },
            onSave = { model ->
                viewModel.saveModel(model)
                showAddDialog = false
                editingModel = null
            },
            onTest = { config ->
                viewModel.testConnectionWithConfig(config)
            }
        )
    }

    // Import Mode Dialog
    if (showImportModeDialog) {
        ModelImportModeDialog(
            currentModelCount = uiState.models.size,
            isImporting = uiState.importStatus is ImportStatus.IMPORTING,
            onDismiss = { showImportModeDialog = false },
            onSelectMode = { mode ->
                showImportModeDialog = false
                pendingImportMode = mode
                importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
            }
        )
    }
}

@Composable
fun ModelImportModeDialog(
    currentModelCount: Int,
    isImporting: Boolean,
    onDismiss: () -> Unit,
    onSelectMode: (ModelImportMode) -> Unit
) {
    var selectedMode by remember { mutableStateOf(ModelImportMode.APPEND) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导入大模型配置") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "当前已有 $currentModelCount 个模型配置",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = "选择导入方式：",
                    style = MaterialTheme.typography.bodyMedium
                )

                // 追加选项
                val appendColor = if (selectedMode == ModelImportMode.APPEND)
                    MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant
                Surface(
                    onClick = { selectedMode = ModelImportMode.APPEND },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = appendColor
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedMode == ModelImportMode.APPEND,
                            onClick = { selectedMode = ModelImportMode.APPEND }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("追加导入", style = MaterialTheme.typography.titleSmall)
                            Text("保留现有配置，添加新配置", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                // 覆盖选项
                val overrideColor = if (selectedMode == ModelImportMode.OVERRIDE)
                    MaterialTheme.colorScheme.errorContainer
                else MaterialTheme.colorScheme.surfaceVariant
                Surface(
                    onClick = { selectedMode = ModelImportMode.OVERRIDE },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = overrideColor
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedMode == ModelImportMode.OVERRIDE,
                            onClick = { selectedMode = ModelImportMode.OVERRIDE }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("覆盖导入", style = MaterialTheme.typography.titleSmall)
                            Text("清空现有配置后导入", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                if (isImporting) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("正在导入...", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSelectMode(selectedMode) },
                enabled = !isImporting
            ) {
                Text("选择文件")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isImporting) {
                Text("取消")
            }
        }
    )
}

@Composable
fun ModelItem(
    model: AIModelConfig,
    testResult: Boolean?,
    testErrorMessage: String?,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSetDefault: () -> Unit,
    onTest: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = model.modelName,
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (model.isDefault) {
                            Spacer(modifier = Modifier.width(8.dp))
                            AssistChip(
                                onClick = {},
                                label = { Text("默认") }
                            )
                        }
                        // Test result indicator
                        if (testResult != null) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = if (testResult) Icons.Default.CheckCircle else Icons.Default.Error,
                                contentDescription = if (testResult) "测试成功" else "测试失败",
                                tint = if (testResult) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Text(
                        text = "${model.modelType.name} • ${model.model.take(20)}${if (model.model.length > 20) "..." else ""} • ${model.apiEndpoint.take(20)}...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (!model.isDefault) {
                    TextButton(onClick = onSetDefault) {
                        Text("设为默认")
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "温度: ${model.temperature}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "最大Token: ${model.maxTokens}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "本月费用: ¥${String.format("%.2f", model.monthlyCost)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 显示测试错误信息
            if (testErrorMessage != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "测试失败: $testErrorMessage",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onTest) {
                    Icon(Icons.Default.Settings, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (testResult == null) "测试" else "重新测试")
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelEditDialog(
    model: AIModelConfig?,
    dialogTestState: DialogTestState = DialogTestState.Idle,
    onDismiss: () -> Unit,
    onSave: (AIModelConfig) -> Unit,
    onTest: (AIModelConfig) -> Unit
) {
    var modelName by remember { mutableStateOf(model?.modelName ?: "") }
    var modelType by remember { mutableStateOf(model?.modelType ?: ModelType.OPENAI) }
    var modelValue by remember { mutableStateOf(model?.model ?: "") }
    var apiKey by remember { mutableStateOf(model?.apiKey ?: "") }
    var apiEndpoint by remember { mutableStateOf(model?.apiEndpoint ?: "") }
    var temperature by remember { mutableStateOf(model?.temperature?.toString() ?: "0.7") }
    var maxTokens by remember { mutableStateOf(model?.maxTokens?.toString() ?: "1000") }
    var isDefault by remember { mutableStateOf(model?.isDefault ?: false) }

    // 构建当前配置
    fun buildCurrentConfig(): AIModelConfig {
        return AIModelConfig(
            id = model?.id ?: 0,
            modelType = modelType,
            modelName = modelName,
            model = modelValue,
            apiKey = apiKey,
            apiEndpoint = apiEndpoint,
            temperature = temperature.toFloatOrNull() ?: 0.7f,
            maxTokens = maxTokens.toIntOrNull() ?: 1000,
            isDefault = isDefault,
            isEnabled = true
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (model == null) "添加模型" else "编辑模型") },
        text = {
            Column {
                OutlinedTextField(
                    value = modelName,
                    onValueChange = { modelName = it },
                    label = { Text("模型名称") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Model Type Dropdown
                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = modelType.name,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("模型类型") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        ModelType.entries.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type.name) },
                                onClick = {
                                    modelType = type
                                    expanded = false
                                    // Auto-fill endpoint based on type
                                    if (model == null) {
                                        apiEndpoint = getDefaultEndpoint(type)
                                    }
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = modelValue,
                    onValueChange = { modelValue = it },
                    label = { Text("模型") },
                    placeholder = { Text("如：gpt-4、claude-3-opus等") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API密钥") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = apiEndpoint,
                    onValueChange = { apiEndpoint = it },
                    label = { Text("API地址") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row {
                    OutlinedTextField(
                        value = temperature,
                        onValueChange = { temperature = it },
                        label = { Text("温度") },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedTextField(
                        value = maxTokens,
                        onValueChange = { maxTokens = it },
                        label = { Text("最大Token") },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = isDefault,
                        onCheckedChange = { isDefault = it }
                    )
                    Text("设为默认模型")
                }

                // 测试结果展示
                when (dialogTestState) {
                    is DialogTestState.Idle -> { /* 不显示 */ }
                    is DialogTestState.Testing -> {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "正在测试连接...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    is DialogTestState.Success -> {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                dialogTestState.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    is DialogTestState.Error -> {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                Icons.Default.Error,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                dialogTestState.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // 测试按钮
                OutlinedButton(
                    onClick = { onTest(buildCurrentConfig()) },
                    enabled = modelName.isNotBlank() && apiKey.isNotBlank() &&
                            apiEndpoint.isNotBlank() && dialogTestState !is DialogTestState.Testing
                ) {
                    if (dialogTestState is DialogTestState.Testing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    } else {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("测试")
                }
                // 保存按钮
                TextButton(
                    onClick = {
                        onSave(buildCurrentConfig())
                    }
                ) {
                    Text(stringResource(R.string.save))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

private fun getDefaultEndpoint(modelType: ModelType): String {
    return when (modelType) {
        ModelType.OPENAI -> "https://api.openai.com/v1/chat/completions"
        ModelType.CLAUDE -> "https://api.anthropic.com/v1/messages"
        ModelType.ZHIPU -> "https://open.bigmodel.cn/api/paas/v4/chat/completions"
        ModelType.TONGYI -> "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions"
        ModelType.CUSTOM -> ""
    }
}
