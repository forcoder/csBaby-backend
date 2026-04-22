package com.csbaby.kefu.di

import android.content.Context
import com.csbaby.kefu.data.remote.AIClient
import com.csbaby.kefu.data.remote.AIClientImpl
import com.csbaby.kefu.data.remote.OtaApiService
import com.csbaby.kefu.data.remote.ShzlApiService
import com.csbaby.kefu.infrastructure.error.ErrorHandler
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

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
            .addInterceptor {
                val request = it.request()
                val response = it.proceed(request)
                response
            }
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://api.openai.com/") // Default base URL
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideAIClient(okHttpClient: OkHttpClient): AIClient {
        return AIClientImpl(okHttpClient)
    }
    
    @Provides
    @Singleton
    fun provideErrorHandler(@ApplicationContext context: Context): ErrorHandler {
        return ErrorHandler(context)
    }
    
    @Provides
    @Singleton
    fun providePerformanceMonitor(@ApplicationContext context: Context): com.csbaby.kefu.infrastructure.monitoring.PerformanceMonitor {
        return com.csbaby.kefu.infrastructure.monitoring.PerformanceMonitor(context)
    }
}
