package com.csbaby.kefu.data.remote.backend

import android.content.Context
import android.content.SharedPreferences
import okhttp3.Interceptor
import okhttp3.Response

/**
 * 自动为请求添加 Authorization Token
 * 跳过注册和心跳请求
 */
class TokenInterceptor(context: Context) : Interceptor {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("backend_auth", Context.MODE_PRIVATE)

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        // 注册和心跳不需要 token
        val path = request.url.encodedPath
        if (path.endsWith("/api/auth/register") || path.endsWith("/api/auth/heartbeat") || path == "/health") {
            return chain.proceed(request)
        }

        val token = prefs.getString("token", null)
        if (token != null) {
            val newRequest = request.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
            return chain.proceed(newRequest)
        }

        return chain.proceed(request)
    }

    companion object {
        fun saveCredentials(context: Context, deviceId: String, token: String) {
            context.getSharedPreferences("backend_auth", Context.MODE_PRIVATE)
                .edit()
                .putString("device_id", deviceId)
                .putString("token", token)
                .putLong("saved_at", System.currentTimeMillis())
                .apply()
        }

        fun getDeviceId(context: Context): String? {
            return context.getSharedPreferences("backend_auth", Context.MODE_PRIVATE)
                .getString("device_id", null)
        }

        fun getToken(context: Context): String? {
            return context.getSharedPreferences("backend_auth", Context.MODE_PRIVATE)
                .getString("token", null)
        }

        fun clearCredentials(context: Context) {
            context.getSharedPreferences("backend_auth", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .apply()
        }

        fun isRegistered(context: Context): Boolean {
            return getDeviceId(context) != null && getToken(context) != null
        }
    }
}
