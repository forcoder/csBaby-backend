package com.csbaby.kefu.data.local.dao

import androidx.room.*
import com.csbaby.kefu.data.local.entity.AppConfigEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AppConfigDao {
    @Query("SELECT * FROM app_configs WHERE tenantId = :tenantId ORDER BY lastUsed DESC")
    fun getAllApps(tenantId: String): Flow<List<AppConfigEntity>>

    @Query("SELECT * FROM app_configs WHERE isMonitored = 1 AND tenantId = :tenantId")
    fun getMonitoredApps(tenantId: String): Flow<List<AppConfigEntity>>

    @Query("SELECT * FROM app_configs WHERE packageName = :packageName AND tenantId = :tenantId")
    suspend fun getAppByPackage(packageName: String, tenantId: String): AppConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertApp(app: AppConfigEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertApps(apps: List<AppConfigEntity>)

    @Update
    suspend fun updateApp(app: AppConfigEntity)

    @Query("UPDATE app_configs SET isMonitored = :isMonitored WHERE packageName = :packageName AND tenantId = :tenantId")
    suspend fun updateMonitorStatus(packageName: String, tenantId: String, isMonitored: Boolean)

    @Query("UPDATE app_configs SET lastUsed = :timestamp WHERE packageName = :packageName AND tenantId = :tenantId")
    suspend fun updateLastUsed(packageName: String, tenantId: String, timestamp: Long)

    @Delete
    suspend fun deleteApp(app: AppConfigEntity)

    @Query("DELETE FROM app_configs WHERE packageName = :packageName AND tenantId = :tenantId")
    suspend fun deleteByPackage(packageName: String, tenantId: String)

    @Query("SELECT * FROM app_configs WHERE tenantId = :tenantId ORDER BY lastUsed DESC")
    suspend fun getAllAppsList(tenantId: String): List<AppConfigEntity>
}
