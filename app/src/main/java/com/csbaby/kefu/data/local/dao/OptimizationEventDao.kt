package com.csbaby.kefu.data.local.dao

import androidx.room.*
import com.csbaby.kefu.data.local.entity.OptimizationEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OptimizationEventDao {
    @Query("SELECT * FROM optimization_events WHERE featureKey = :featureKey ORDER BY createdAt DESC")
    fun getByFeatureKey(featureKey: String): Flow<List<OptimizationEventEntity>>

    @Query("SELECT * FROM optimization_events ORDER BY createdAt DESC")
    fun getAll(): Flow<List<OptimizationEventEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: OptimizationEventEntity): Long

    @Query("DELETE FROM optimization_events WHERE id = :id")
    suspend fun deleteById(id: Long)
}
