package com.csbaby.kefu.data.remote

import android.provider.Settings
import com.csbaby.kefu.data.local.PreferencesManager
import com.csbaby.kefu.data.remote.dto.RegisterRequest
import com.csbaby.kefu.data.remote.dto.RegisterResponse
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 设备注册与 Token 管理
 * 首次启动自动注册，Token 过期自动重新注册
 */
@Singleton
class DeviceManager @Inject constructor(
    private val apiService: CsbabyApiService,
    private val preferencesManager: PreferencesManager
) {
    private val mutex = Mutex()

    /**
     * 确保设备已注册，返回有效的 token
     * 如果本地已有 token 则直接返回，否则自动注册
     */
    suspend fun ensureRegistered(): String {
        val existingToken = preferencesManager.authTokenFlow.first()
        if (existingToken.isNotBlank()) {
            return existingToken
        }
        return mutex.withLock {
            // Double-check after acquiring lock
            val token = preferencesManager.authTokenFlow.first()
            if (token.isNotBlank()) return@withLock token
            registerDevice()
        }
    }

    /**
     * 强制重新注册设备
     */
    suspend fun reRegister(): String {
        return mutex.withLock {
            preferencesManager.clearAuthToken()
            registerDevice()
        }
    }

    /**
     * 发送心跳
     */
    suspend fun heartbeat() {
        try {
            apiService.heartbeat()
        } catch (e: Exception) {
            Timber.w(e, "Heartbeat failed")
        }
    }

    private suspend fun registerDevice(): String {
        val request = RegisterRequest(
            platform = "android",
            appVersion = getAppVersion(),
            name = getDeviceName()
        )
        try {
            val response: RegisterResponse = apiService.register(request)
            preferencesManager.saveAuthToken(response.token)
            preferencesManager.saveDeviceId(response.deviceId)
            Timber.i("Device registered: ${response.deviceId}")
            return response.token
        } catch (e: Exception) {
            Timber.e(e, "Device registration failed")
            throw e
        }
    }

    private fun getAppVersion(): String {
        return try {
            val packageInfo = com.csbaby.kefu.BuildConfig.VERSION_NAME
            packageInfo ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
    }

    private fun getDeviceName(): String {
        return try {
            "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"
        } catch (e: Exception) {
            "Android Device"
        }
    }
}
