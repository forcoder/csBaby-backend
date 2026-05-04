package com.csbaby.kefu.data.repository

import com.csbaby.kefu.data.local.AuthManager
import com.csbaby.kefu.data.local.EntityMapper.toDomain
import com.csbaby.kefu.data.local.EntityMapper.toEntity
import com.csbaby.kefu.data.local.dao.UserStyleProfileDao
import com.csbaby.kefu.domain.model.UserStyleProfile
import com.csbaby.kefu.domain.repository.UserStyleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserStyleRepositoryImpl @Inject constructor(
    private val userStyleProfileDao: UserStyleProfileDao,
    private val authManager: AuthManager
) : UserStyleRepository {

    override fun getProfile(userId: String): Flow<UserStyleProfile?> {
        val tenantId = authManager.getTenantId() ?: ""
        return userStyleProfileDao.getProfileByUserId(userId, tenantId).map { it?.toDomain() }
    }

    override suspend fun getProfileSync(userId: String): UserStyleProfile? {
        val tenantId = authManager.getTenantId() ?: ""
        return userStyleProfileDao.getProfileByUserIdSync(userId, tenantId)?.toDomain()
    }

    override suspend fun saveProfile(profile: UserStyleProfile) {
        userStyleProfileDao.insertProfile(profile.toEntity())
    }

    override suspend fun updateProfile(profile: UserStyleProfile) {
        userStyleProfileDao.updateProfile(profile.toEntity())
    }

    override suspend fun updateFormalityLevel(userId: String, formality: Float) {
        val tenantId = authManager.getTenantId() ?: ""
        userStyleProfileDao.updateFormalityLevel(userId, tenantId, formality.coerceIn(0f, 1f))
    }

    override suspend fun updateEnthusiasmLevel(userId: String, enthusiasm: Float) {
        val tenantId = authManager.getTenantId() ?: ""
        userStyleProfileDao.updateEnthusiasmLevel(userId, tenantId, enthusiasm.coerceIn(0f, 1f))
    }

    override suspend fun updateProfessionalismLevel(userId: String, professionalism: Float) {
        val tenantId = authManager.getTenantId() ?: ""
        userStyleProfileDao.updateProfessionalismLevel(userId, tenantId, professionalism.coerceIn(0f, 1f))
    }

    override suspend fun incrementLearningSamples(userId: String) {
        val tenantId = authManager.getTenantId() ?: ""
        userStyleProfileDao.incrementLearningSamples(userId, tenantId, System.currentTimeMillis())
    }

    override suspend fun updateAccuracyScore(userId: String, score: Float) {
        val tenantId = authManager.getTenantId() ?: ""
        userStyleProfileDao.updateAccuracyScore(userId, tenantId, score.coerceIn(0f, 1f))
    }
}
