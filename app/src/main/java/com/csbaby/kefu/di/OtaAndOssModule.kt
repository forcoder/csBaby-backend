package com.csbaby.kefu.di

import android.content.Context
import androidx.work.WorkerParameters
import com.csbaby.kefu.data.remote.OtaApiService
import com.csbaby.kefu.data.remote.OssOtaApiService
import com.csbaby.kefu.data.remote.ShzlApiService
import com.csbaby.kefu.data.repository.OtaRepository
import com.csbaby.kefu.data.repository.OtaRepositoryImpl
import com.csbaby.kefu.infrastructure.ota.OtaManager
import com.csbaby.kefu.infrastructure.ota.OtaScheduler
import com.csbaby.kefu.infrastructure.ota.OtaUpdateWorker
import com.csbaby.kefu.infrastructure.oss.AliyunOssManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Singleton

/**
 * OTA和OSS统一模块
 * 包含OTA更新和阿里云OSS版本管理的依赖注入
 */
@Module
@InstallIn(SingletonComponent::class)
object OtaAndOssModule {
    
    // ========== OTA功能 ==========
    
    /**
     * 提供OTA API服务（模拟）
     */
    @Provides
    @Singleton
    fun provideOtaApiService(): OtaApiService {
        return com.csbaby.kefu.data.remote.MockOtaApiService()
    }
    
    /**
     * 提供OSS OTA API服务
     */
    @Provides
    @Singleton
    fun provideOssOtaApiService(okHttpClient: OkHttpClient): OssOtaApiService {
        return Retrofit.Builder()
            .baseUrl("https://your-oss-api-server.com/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OssOtaApiService::class.java)
    }
    
    /**
     * 提供 shz.al API 服务
     */
    @Provides
    @Singleton
    fun provideShzlApiService(okHttpClient: OkHttpClient): ShzlApiService {
        return Retrofit.Builder()
            .baseUrl("https://shz.al/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ShzlApiService::class.java)
    }
    
    /**
     * 提供OTA仓库
     */
    @Provides
    @Singleton
    fun provideOtaRepository(
        @ApplicationContext context: Context,
        shzlApiService: ShzlApiService
    ): OtaRepository {
        return OtaRepositoryImpl(context, shzlApiService)
    }
    
    /**
     * 提供OTA管理器
     */
    @Provides
    @Singleton
    fun provideOtaManager(
        @ApplicationContext context: Context,
        repository: OtaRepository
    ): OtaManager {
        return OtaManager(context, repository)
    }
    
    /**
     * 提供OTA调度器
     */
    @Provides
    @Singleton
    fun provideOtaScheduler(
        @ApplicationContext context: Context
    ): OtaScheduler {
        return OtaScheduler(context)
    }
    
    /**
     * 提供OTA Worker工厂
     * WorkManager使用AssistedInject，这里提供工厂
     */
    @Provides
    @Singleton
    fun provideOtaUpdateWorkerFactory(
        repository: OtaRepository
    ): OtaUpdateWorker.Factory {
        return object : OtaUpdateWorker.Factory {
            override fun create(
                context: Context,
                params: WorkerParameters
            ): OtaUpdateWorker {
                return OtaUpdateWorker(context, params, repository)
            }
        }
    }
    
    // ========== 阿里云OSS功能 ==========
    
    /**
     * 提供阿里云OSS管理器
     */
    @Provides
    @Singleton
    fun provideAliyunOssManager(
        @ApplicationContext context: Context
    ): AliyunOssManager {
        return AliyunOssManager(context)
    }
    
    /**
     * 提供直接OSS版本检查器
     */
    @Provides
    @Singleton
    fun provideDirectOssVersionChecker(
        ossManager: AliyunOssManager
    ): com.csbaby.kefu.data.remote.DirectOssVersionChecker {
        return com.csbaby.kefu.data.remote.DirectOssVersionChecker(ossManager)
    }
    
    /**
     * 提供OSS OTA仓库
     */
    @Provides
    @Singleton
    fun provideOssOtaRepository(
        ossManager: AliyunOssManager,
        directChecker: com.csbaby.kefu.data.remote.DirectOssVersionChecker
    ): com.csbaby.kefu.data.remote.OssOtaRepository {
        return com.csbaby.kefu.data.remote.OssOtaRepository(ossManager, directChecker)
    }
}