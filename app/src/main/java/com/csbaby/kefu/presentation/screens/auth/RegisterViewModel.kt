package com.csbaby.kefu.presentation.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.csbaby.kefu.data.local.AuthManager
import com.csbaby.kefu.data.remote.backend.BackendClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RegisterUiState(
    val phoneNumber: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val isLoading: Boolean = false,
    val isRegistered: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val backendClient: BackendClient,
    private val authManager: AuthManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

    fun onPhoneChanged(phone: String) {
        _uiState.update { it.copy(phoneNumber = phone, errorMessage = null) }
    }

    fun onPasswordChanged(password: String) {
        _uiState.update { it.copy(password = password, errorMessage = null) }
    }

    fun onConfirmPasswordChanged(confirm: String) {
        _uiState.update { it.copy(confirmPassword = confirm, errorMessage = null) }
    }

    fun register() {
        val state = _uiState.value
        if (state.phoneNumber.isBlank()) {
            _uiState.update { it.copy(errorMessage = "请输入手机号") }
            return
        }
        if (state.password.isBlank()) {
            _uiState.update { it.copy(errorMessage = "请输入密码") }
            return
        }
        if (state.password.length < 6) {
            _uiState.update { it.copy(errorMessage = "密码至少6位") }
            return
        }
        if (state.password != state.confirmPassword) {
            _uiState.update { it.copy(errorMessage = "两次密码不一致") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = backendClient.registerUser(state.phoneNumber, state.password)
            result.fold(
                onSuccess = { response ->
                    authManager.saveAuth(
                        token = response.token,
                        tenantId = response.tenantId,
                        phoneNumber = response.phoneNumber,
                        expiresInSeconds = response.expiresIn
                    )
                    _uiState.update { it.copy(isLoading = false, isRegistered = true) }
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = e.message ?: "注册失败")
                    }
                }
            )
        }
    }
}
