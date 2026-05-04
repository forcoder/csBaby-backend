package com.csbaby.kefu.data.remote.backend

import com.csbaby.kefu.data.local.AuthManager
import okhttp3.Interceptor
import okhttp3.Response

/**
 * 多租户认证拦截器
 * 为所有 API 请求注入 Bearer Token
 * 跳过注册、心跳、登录、健康检查等接口
 */
class AuthInterceptor(
    private val authManager: AuthManager
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        // 不需要 token 的接口
        val path = request.url.encodedPath
        if (path.endsWith("/api/auth/register") ||
            path.endsWith("/api/auth/login") ||
            path.endsWith("/api/auth/heartbeat") ||
            path == "/health"
        ) {
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
}
