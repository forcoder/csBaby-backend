package com.csbaby.kefu.data.local.dao

import androidx.room.*
import com.csbaby.kefu.data.local.entity.MessageBlacklistEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageBlacklistDao {

    @Query("SELECT * FROM message_blacklist WHERE isEnabled = 1 AND tenantId = :tenantId ORDER BY createdAt DESC")
    fun getAllEnabledFlow(tenantId: String): Flow<List<MessageBlacklistEntity>>

    @Query("SELECT * FROM message_blacklist WHERE tenantId = :tenantId ORDER BY createdAt DESC")
    fun getAllFlow(tenantId: String): Flow<List<MessageBlacklistEntity>>

    @Query("SELECT * FROM message_blacklist WHERE type = :type AND isEnabled = 1 AND tenantId = :tenantId ORDER BY createdAt DESC")
    fun getByTypeFlow(type: String, tenantId: String): Flow<List<MessageBlacklistEntity>>

    @Query("SELECT * FROM message_blacklist WHERE packageName = :packageName AND isEnabled = 1 AND tenantId = :tenantId ORDER BY createdAt DESC")
    fun getByPackageFlow(packageName: String, tenantId: String): Flow<List<MessageBlacklistEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(blacklist: MessageBlacklistEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(blacklists: List<MessageBlacklistEntity>)

    @Update
    suspend fun update(blacklist: MessageBlacklistEntity)

    @Delete
    suspend fun delete(blacklist: MessageBlacklistEntity)

    @Query("DELETE FROM message_blacklist WHERE id = :id AND tenantId = :tenantId")
    suspend fun deleteById(id: Long, tenantId: String)

    @Query("DELETE FROM message_blacklist WHERE tenantId = :tenantId")
    suspend fun deleteAll(tenantId: String)

    @Query("SELECT COUNT(*) FROM message_blacklist WHERE isEnabled = 1 AND tenantId = :tenantId")
    suspend fun getEnabledCount(tenantId: String): Int

    @Query("SELECT EXISTS(SELECT 1 FROM message_blacklist WHERE value = :value AND isEnabled = 1 AND tenantId = :tenantId)")
    suspend fun isBlacklisted(value: String, tenantId: String): Boolean

    @Query("SELECT * FROM message_blacklist WHERE tenantId = :tenantId ORDER BY createdAt DESC")
    suspend fun getAllBlacklist(tenantId: String): List<MessageBlacklistEntity>
}
