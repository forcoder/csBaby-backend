package com.csbaby.kefu.data.remote

import com.csbaby.kefu.data.local.PreferencesManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 自动附加 JWT Token 到请求头的拦截器
 * 从 DataStore 读取 token，添加到 Authorization: Bearer <token>
 * 收到 401 时清除本地 token 触发重新注册
 */
@Singleton
class AuthInterceptor @Inject constructor(
    private val preferencesManager: PreferencesManager
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val token = runBlocking { preferencesManager.authTokenFlow.first() }

        val request = if (token.isNotBlank()) {
            chain.request().newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build()
        } else {
            chain.request()
        }

        val response = chain.proceed(request)

        if (response.code == 401) {
            runBlocking { preferencesManager.clearAuthToken() }
        }

        return response
    }
}
