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
    version = 7, // 版本号升级以反映索引变更
    exportSchema = true // 开启schema导出便于性能分析
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

        /**
         * 创建数据库索引以提高查询性能
         */
        fun createIndexes(database: SupportSQLiteDatabase) {
            // reply_history表索引
            database.execSQL("CREATE INDEX IF NOT EXISTS idx_reply_history_send_time ON reply_history(sendTime)")
            database.execSQL("CREATE INDEX IF NOT EXISTS idx_reply_history_source_app ON reply_history(sourceApp)")

            // optimization_metrics表索引
            database.execSQL("CREATE INDEX IF NOT EXISTS idx_optimization_metrics_feature_date ON optimization_metrics(featureKey, date)")
            database.execSQL("CREATE INDEX IF NOT EXISTS idx_optimization_metrics_variant_date ON optimization_metrics(variantId, date)")

            // app_configs表索引
            database.execSQL("CREATE INDEX IF NOT EXISTS idx_app_configs_last_used ON app_configs(lastUsed)")
            database.execSQL("CREATE INDEX IF NOT EXISTS idx_app_configs_monitored ON app_configs(isMonitored)")

            // keyword_rules表索引
            database.execSQL("CREATE INDEX IF NOT EXISTS idx_keyword_rules_enabled ON keyword_rules(enabled)")
            database.execSQL("CREATE INDEX IF NOT EXISTS idx_keyword_rules_category ON keyword_rules(category)")

            // llm_features表索引
            database.execSQL("CREATE INDEX IF NOT EXISTS idx_llm_features_enabled ON llm_features(isEnabled)")
            database.execSQL("CREATE INDEX IF NOT EXISTS idx_feature_variants_feature_id ON feature_variants(featureId)")
        }

        /**
         * 清理过时的索引（用于数据迁移）
         */
        fun dropObsoleteIndexes(database: SupportSQLiteDatabase) {
            // 这里可以添加清理旧索引的逻辑
            // 目前先保留所有索引以确保兼容性
        }
    }
}
