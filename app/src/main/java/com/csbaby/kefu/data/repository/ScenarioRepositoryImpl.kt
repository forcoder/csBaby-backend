package com.csbaby.kefu.data.repository

import com.csbaby.kefu.data.local.AuthManager
import com.csbaby.kefu.data.local.EntityMapper.toDomain
import com.csbaby.kefu.data.local.EntityMapper.toEntity
import com.csbaby.kefu.data.local.dao.ScenarioDao
import com.csbaby.kefu.domain.model.Scenario
import com.csbaby.kefu.domain.repository.ScenarioRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScenarioRepositoryImpl @Inject constructor(
    private val scenarioDao: ScenarioDao,
    private val authManager: AuthManager
) : ScenarioRepository {

    override fun getAllScenarios(): Flow<List<Scenario>> {
        val tenantId = authManager.getTenantId() ?: ""
        return scenarioDao.getAllScenarios(tenantId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getScenarioById(id: Long): Scenario? {
        val tenantId = authManager.getTenantId() ?: ""
        return scenarioDao.getScenarioById(id, tenantId)?.toDomain()
    }

    override suspend fun insertScenario(scenario: Scenario): Long {
        return scenarioDao.insertScenario(scenario.toEntity())
    }

    override suspend fun updateScenario(scenario: Scenario) {
        scenarioDao.updateScenario(scenario.toEntity())
    }

    override suspend fun deleteScenario(id: Long) {
        val tenantId = authManager.getTenantId() ?: ""
        scenarioDao.deleteScenario(scenarioDao.getScenarioById(id, tenantId)!!)
    }
}
