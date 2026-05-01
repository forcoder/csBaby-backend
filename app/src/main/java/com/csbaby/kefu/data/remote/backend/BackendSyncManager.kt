package com.csbaby.kefu.data.remote.backend

import android.content.Context
import android.provider.Settings
import com.csbaby.kefu.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * 后端同步管理器
 * 负责设备注册、心跳保活、数据同步
 */
class BackendSyncManager(
    private val backendClient: BackendClient,
    private val context: Context
) {
    private val deviceId: String?
        get() = TokenInterceptor.getDeviceId(context)

    val isRegistered: Boolean
        get() = TokenInterceptor.isRegistered(context)

    /**
     * 注册设备（如果尚未注册）
     */
    suspend fun registerIfNeeded(): Boolean {
        if (isRegistered) {
            Timber.d("Device already registered: $deviceId")
            return true
        }
        return register()
    }

    /**
     * 强制重新注册设备
     */
    suspend fun register(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val result = backendClient.register(BuildConfig.VERSION_NAME)
                result.fold(
                    onSuccess = { auth ->
                        Timber.i("Device registered: deviceId=${auth.deviceId}")
                        true
                    },
                    onFailure = { e ->
                        Timber.e(e, "Device registration failed")
                        false
                    }
                )
            } catch (e: Exception) {
                Timber.e(e, "Device registration error")
                false
            }
        }
    }

    /**
     * 发送心跳
     */
    suspend fun heartbeat(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val result = backendClient.heartbeat()
                result.getOrDefault(false)
            } catch (e: Exception) {
                Timber.w(e, "Heartbeat failed")
                false
            }
        }
    }

    /**
     * 健康检查
     */
    suspend fun healthCheck(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                backendClient.healthCheck().getOrDefault(false)
            } catch (e: Exception) {
                false
            }
        }
    }
}
