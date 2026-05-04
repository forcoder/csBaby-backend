package com.csbaby.kefu.presentation.screens.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.csbaby.kefu.domain.model.ReplyHistory
import com.csbaby.kefu.domain.repository.ReplyHistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AppHistoryGroup(
    val appName: String,
    val replies: List<ReplyHistory>
)

data class HistoryUiState(
    val groups: List<AppHistoryGroup> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val replyHistoryRepository: ReplyHistoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    private val appConfigs = listOf(
        "com.tencent.mm" to "微信",
        "com.baijuyi" to "百居易",
        "com.meituan.ms" to "美团民宿",
        "com.tujia.ms" to "途家民宿"
    )

    init {
        loadHistory()
    }

    private fun loadHistory() {
        viewModelScope.launch {
            val flows = appConfigs.map { (pkg, _) ->
                replyHistoryRepository.getRepliesByApp(pkg, 100)
            }
            combine(flows) { arrays ->
                appConfigs.mapIndexed { index, (pkg, name) ->
                    AppHistoryGroup(
                        appName = name,
                        replies = arrays[index].toList()
                    )
                }.filter { it.replies.isNotEmpty() }
            }.collect { groups ->
                _uiState.value = HistoryUiState(groups = groups, isLoading = false)
            }
        }
    }
}
