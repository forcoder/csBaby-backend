package com.csbaby.kefu.data.local.dao

import androidx.room.*
import com.csbaby.kefu.data.local.entity.OptimizationEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OptimizationEventDao {
    @Query("SELECT * FROM optimization_events WHERE featureKey = :featureKey AND tenantId = :tenantId ORDER BY createdAt DESC")
    fun getByFeatureKey(featureKey: String, tenantId: String): Flow<List<OptimizationEventEntity>>

    @Query("SELECT * FROM optimization_events WHERE tenantId = :tenantId ORDER BY createdAt DESC")
    fun getAll(tenantId: String): Flow<List<OptimizationEventEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: OptimizationEventEntity): Long

    @Query("DELETE FROM optimization_events WHERE id = :id AND tenantId = :tenantId")
    suspend fun deleteById(id: Long, tenantId: String)
}
