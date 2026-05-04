package com.csbaby.kefu.data.remote.backend

import android.content.Context
import com.csbaby.kefu.data.local.AuthManager
import okhttp3.Interceptor
import okhttp3.Response

/**
 * 自动为请求添加 Authorization Token
 * 跳过注册和心跳请求
 * 统一从 AuthManager 读取 token，避免双存储不一致
 */
class TokenInterceptor(context: Context) : Interceptor {

    private val authManager = AuthManager(context)

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        // 注册和心跳不需要 token
        val path = request.url.encodedPath
        if (path.endsWith("/api/auth/register") || path.endsWith("/api/auth/heartbeat") || path == "/health") {
            return chain.proceed(request)
        }

        val token = authManager.getToken()
        if (!token.isNullOrBlank()) {
            val newRequest = request.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
            return chain.proceed(newRequest)
        }

        return chain.proceed(request)
    }

    companion object {
        fun saveCredentials(context: Context, deviceId: String, token: String) {
            // 同步写入 AuthManager，保证登录后的 token 一致
            AuthManager(context).saveAuth(
                token = token,
                tenantId = "",
                phoneNumber = "",
                expiresInSeconds = 0
            )
        }

        fun getDeviceId(context: Context): String? {
            return context.getSharedPreferences("backend_auth", Context.MODE_PRIVATE)
                .getString("device_id", null)
        }

        fun getToken(context: Context): String? {
            return AuthManager(context).getToken()
        }

        fun clearCredentials(context: Context) {
            AuthManager(context).clearAuth()
        }

        fun isRegistered(context: Context): Boolean {
            return !AuthManager(context).getToken().isNullOrBlank()
        }
    }
}
