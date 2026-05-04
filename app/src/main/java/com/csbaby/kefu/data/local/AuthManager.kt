package com.csbaby.kefu.data.local

import android.content.Context
import android.content.SharedPreferences
import timber.log.Timber

/**
 * 多租户认证管理器
 * 管理登录状态、token 存储、tenant_id 解析
 */
class AuthManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("auth_manager", Context.MODE_PRIVATE)

    /**
     * 检查是否已登录（有有效 token）
     */
    val isLoggedIn: Boolean
        get() {
            val token = getToken()
            val expiresAt = getExpiresAt()
            if (token.isNullOrBlank()) return false
            if (expiresAt > 0 && System.currentTimeMillis() > expiresAt) {
                Timber.w("Token expired")
                return false
            }
            return true
        }

    /**
     * 获取当前 token
     */
    fun getToken(): String? = prefs.getString(KEY_TOKEN, null)

    /**
     * 获取当前租户 ID
     */
    fun getTenantId(): String? = prefs.getString(KEY_TENANT_ID, null)

    /**
     * 获取当前用户手机号
     */
    fun getPhoneNumber(): String? = prefs.getString(KEY_PHONE, null)

    /**
     * 获取过期时间
     */
    private fun getExpiresAt(): Long = prefs.getLong(KEY_EXPIRES_AT, 0)

    /**
     * 保存登录凭证
     */
    fun saveAuth(token: String, tenantId: String, phoneNumber: String, expiresInSeconds: Long = 0) {
        val expiresAt = if (expiresInSeconds > 0) {
            System.currentTimeMillis() + expiresInSeconds * 1000
        } else 0L

        prefs.edit()
            .putString(KEY_TOKEN, token)
            .putString(KEY_TENANT_ID, tenantId)
            .putString(KEY_PHONE, phoneNumber)
            .putLong(KEY_EXPIRES_AT, expiresAt)
            .apply()

        Timber.d("Auth saved: phone=$phoneNumber, tenantId=$tenantId")
    }

    /**
     * 清除所有认证信息（登出）
     */
    fun clearAuth() {
        prefs.edit().clear().apply()
        Timber.d("Auth cleared")
    }

    companion object {
        private const val KEY_TOKEN = "auth_token"
        private const val KEY_TENANT_ID = "tenant_id"
        private const val KEY_PHONE = "phone_number"
        private const val KEY_EXPIRES_AT = "expires_at"
    }
}
