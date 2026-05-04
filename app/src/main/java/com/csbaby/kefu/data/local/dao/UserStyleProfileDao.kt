package com.csbaby.kefu.data.local.dao

import androidx.room.*
import com.csbaby.kefu.data.local.entity.UserStyleProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserStyleProfileDao {
    @Query("SELECT * FROM user_style_profiles WHERE userId = :userId AND tenantId = :tenantId")
    fun getProfileByUserId(userId: String, tenantId: String): Flow<UserStyleProfileEntity?>

    @Query("SELECT * FROM user_style_profiles WHERE userId = :userId AND tenantId = :tenantId")
    suspend fun getProfileByUserIdSync(userId: String, tenantId: String): UserStyleProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: UserStyleProfileEntity)

    @Update
    suspend fun updateProfile(profile: UserStyleProfileEntity)

    @Query("UPDATE user_style_profiles SET formalityLevel = :formality WHERE userId = :userId AND tenantId = :tenantId")
    suspend fun updateFormalityLevel(userId: String, tenantId: String, formality: Float)

    @Query("UPDATE user_style_profiles SET enthusiasmLevel = :enthusiasm WHERE userId = :userId AND tenantId = :tenantId")
    suspend fun updateEnthusiasmLevel(userId: String, tenantId: String, enthusiasm: Float)

    @Query("UPDATE user_style_profiles SET professionalismLevel = :professionalism WHERE userId = :userId AND tenantId = :tenantId")
    suspend fun updateProfessionalismLevel(userId: String, tenantId: String, professionalism: Float)

    @Query("UPDATE user_style_profiles SET learningSamples = learningSamples + 1, lastTrained = :timestamp WHERE userId = :userId AND tenantId = :tenantId")
    suspend fun incrementLearningSamples(userId: String, tenantId: String, timestamp: Long)

    @Query("UPDATE user_style_profiles SET accuracyScore = :score WHERE userId = :userId AND tenantId = :tenantId")
    suspend fun updateAccuracyScore(userId: String, tenantId: String, score: Float)

    @Delete
    suspend fun deleteProfile(profile: UserStyleProfileEntity)

    @Query("SELECT * FROM user_style_profiles WHERE tenantId = :tenantId ORDER BY lastTrained DESC")
    fun getAllProfiles(tenantId: String): Flow<List<UserStyleProfileEntity>>
}
