package com.csbaby.kefu.data.local.entity

import androidx.room.Entity

@Entity(
    tableName = "rule_scenario_relation",
    primaryKeys = ["ruleId", "scenarioId"]
)
data class RuleScenarioCrossRef(
    val ruleId: Long,
    val scenarioId: Long
)
