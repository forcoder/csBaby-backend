package com.csbaby.kefu.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.csbaby.kefu.data.local.dao.*
import com.csbaby.kefu.data.local.entity.*
import com.csbaby.kefu.data.local.migration.Migration5to6

@Database(
    entities = [
        AppConfigEntity::class,
        KeywordRuleEntity::class,
        ScenarioEntity::class,
        RuleScenarioCrossRef::class,
        AIModelConfigEntity::class,
        UserStyleProfileEntity::class,
        ReplyHistoryEntity::class,
        MessageBlacklistEntity::class,
        LLMFeatureEntity::class,
        FeatureVariantEntity::class,
        OptimizationMetricsEntity::class,
        OptimizationEventEntity::class,
        ReplyFeedbackEntity::class
    ],
    version = 7,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class KefuDatabase : RoomDatabase() {
    abstract fun appConfigDao(): AppConfigDao
    abstract fun keywordRuleDao(): KeywordRuleDao
    abstract fun scenarioDao(): ScenarioDao
    abstract fun aiModelConfigDao(): AIModelConfigDao
    abstract fun userStyleProfileDao(): UserStyleProfileDao
    abstract fun replyHistoryDao(): ReplyHistoryDao
    abstract fun messageBlacklistDao(): MessageBlacklistDao
    abstract fun llmFeatureDao(): LLMFeatureDao
    abstract fun featureVariantDao(): FeatureVariantDao
    abstract fun optimizationMetricsDao(): OptimizationMetricsDao
    abstract fun optimizationEventDao(): OptimizationEventDao
    abstract fun replyFeedbackDao(): ReplyFeedbackDao

    companion object {
        const val DATABASE_NAME = "kefu_database"
    }
}
