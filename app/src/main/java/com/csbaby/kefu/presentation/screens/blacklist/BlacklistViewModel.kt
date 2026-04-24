package com.csbaby.kefu.presentation.screens.blacklist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.csbaby.kefu.data.local.entity.MessageBlacklistEntity
import com.csbaby.kefu.domain.repository.MessageBlacklistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BlacklistUiState(
    val blacklists: List<MessageBlacklistEntity> = emptyList(),
    val isLoading: Boolean = false,
    val noticeMessage: String? = null
)

@HiltViewModel
class BlacklistViewModel @Inject constructor(
    private val blacklistRepository: MessageBlacklistRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(BlacklistUiState())
    val uiState: StateFlow<BlacklistUiState> = _uiState.asStateFlow()
    
    init {
        loadBlacklists()
    }
    
    private fun loadBlacklists() {
        viewModelScope.launch {
            blacklistRepository.getAll()
                .catch { e ->
                    _uiState.update { it.copy(noticeMessage = "加载黑名单失败: ${e.message}") }
                }
                .collect { blacklists ->
                    _uiState.update { it.copy(blacklists = blacklists) }
                }
        }
    }
    
    fun addBlacklist(
        type: String,
        value: String,
        description: String = "",
        packageName: String? = null
    ) {
        if (value.isBlank()) {
            _uiState.update { it.copy(noticeMessage = "黑名单值不能为空") }
            return
        }
        
        viewModelScope.launch {
            blacklistRepository.addToBlacklist(
                type = type,
                value = value.trim(),
                description = description.trim(),
                packageName = packageName?.trim()
            )
            _uiState.update { it.copy(noticeMessage = "添加成功") }
        }
    }
    
    fun removeBlacklist(id: Long) {
        viewModelScope.launch {
            blacklistRepository.removeFromBlacklist(id)
            _uiState.update { it.copy(noticeMessage = "已删除") }
        }
    }
    
    fun toggleBlacklist(id: Long, isEnabled: Boolean) {
        viewModelScope.launch {
            blacklistRepository.toggleBlacklist(id, isEnabled)
            _uiState.update { 
                it.copy(noticeMessage = if (isEnabled) "已启用" else "已禁用") 
            }
        }
    }
    
    fun clearAll() {
        viewModelScope.launch {
            blacklistRepository.clearAll()
            _uiState.update { it.copy(noticeMessage = "已清空所有黑名单") }
        }
    }
    
    fun consumeNoticeMessage() {
        _uiState.update { it.copy(noticeMessage = null) }
    }
}
