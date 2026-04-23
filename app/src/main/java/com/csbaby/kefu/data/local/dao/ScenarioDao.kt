package com.csbaby.kefu.data.local.dao

import androidx.room.*
import com.csbaby.kefu.data.local.entity.RuleScenarioCrossRef
import com.csbaby.kefu.data.local.entity.ScenarioEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScenarioDao {
    @Query("SELECT * FROM scenarios ORDER BY createdAt DESC")
    fun getAllScenarios(): Flow<List<ScenarioEntity>>

    @Query("SELECT * FROM scenarios WHERE id = :id")
    suspend fun getScenarioById(id: Long): ScenarioEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScenario(scenario: ScenarioEntity): Long

    @Update
    suspend fun updateScenario(scenario: ScenarioEntity)

    @Delete
    suspend fun deleteScenario(scenario: ScenarioEntity)

    // Cross reference operations
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRuleScenarioRelation(crossRef: RuleScenarioCrossRef)

    @Query("SELECT scenarioId FROM rule_scenario_relation WHERE ruleId = :ruleId")
    suspend fun getScenarioIdsForRule(ruleId: Long): List<Long>

    @Query("SELECT ruleId FROM rule_scenario_relation WHERE scenarioId = :scenarioId")
    suspend fun getRuleIdsForScenario(scenarioId: Long): List<Long>

    @Delete
    suspend fun deleteRuleScenarioRelation(crossRef: RuleScenarioCrossRef)

    @Query("DELETE FROM rule_scenario_relation WHERE ruleId = :ruleId")
    suspend fun deleteRelationsForRule(ruleId: Long)

    @Query("DELETE FROM rule_scenario_relation")
    suspend fun deleteAllRelations()
}

