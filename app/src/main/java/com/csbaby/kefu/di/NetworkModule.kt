package com.csbaby.kefu.di

import android.content.Context
import com.csbaby.kefu.data.local.dao.KeywordRuleDao
import com.csbaby.kefu.data.remote.AIClient
import com.csbaby.kefu.data.remote.AIClientImpl
import com.csbaby.kefu.data.remote.backend.BackendApi
import com.csbaby.kefu.data.remote.backend.BackendClient
import com.csbaby.kefu.data.remote.backend.BackendSyncManager
import com.csbaby.kefu.data.remote.backend.RuleBackendSync
import com.csbaby.kefu.data.remote.backend.TokenInterceptor
import com.csbaby.kefu.infrastructure.error.ErrorHandler
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class BackendHttpClient

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    /**
     * 后端 API 基础 URL
     * Render 部署: https://csbaby-api2.onrender.com
     */
    private const val BACKEND_BASE_URL = "https://csbaby-api2.onrender.com/"

    /** 默认 OkHttpClient，用于 AI 客户端和 Retrofit */
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://api.openai.com/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideAIClient(okHttpClient: OkHttpClient): AIClient {
        return AIClientImpl(okHttpClient)
    }

    // ========== 后端 API ==========

    @Provides
    @Singleton
    fun provideTokenInterceptor(@ApplicationContext context: Context): TokenInterceptor {
        return TokenInterceptor(context)
    }

    /** 后端专用 OkHttpClient，带 Token 认证 */
    @Provides
    @Singleton
    @BackendHttpClient
    fun provideBackendOkHttpClient(
        tokenInterceptor: TokenInterceptor
    ): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        return OkHttpClient.Builder()
            .addInterceptor(tokenInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideBackendApi(@BackendHttpClient okHttpClient: OkHttpClient): BackendApi {
        return Retrofit.Builder()
            .baseUrl(BACKEND_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(BackendApi::class.java)
    }

    @Provides
    @Singleton
    fun provideBackendClient(
        api: BackendApi,
        @ApplicationContext context: Context
    ): BackendClient {
        return BackendClient(api, context)
    }

    @Provides
    @Singleton
    fun provideBackendSyncManager(
        backendClient: BackendClient,
        @ApplicationContext context: Context
    ): BackendSyncManager {
        return BackendSyncManager(backendClient, context)
    }

    @Provides
    @Singleton
    fun provideRuleBackendSync(
        backendClient: BackendClient,
        keywordRuleDao: KeywordRuleDao
    ): RuleBackendSync {
        return RuleBackendSync(backendClient, keywordRuleDao)
    }

    @Provides
    @Singleton
    fun provideErrorHandler(@ApplicationContext context: Context): ErrorHandler {
        return ErrorHandler(context)
    }
}
