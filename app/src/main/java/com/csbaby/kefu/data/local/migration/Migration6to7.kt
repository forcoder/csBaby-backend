package com.csbaby.kefu.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration from version 6 to 7:
 * - Add performance indexes for query optimization
 * - Update database schema for better performance
 */
class Migration6to7 : Migration(6, 7) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Create indexes for reply_history table
        database.execSQL("CREATE INDEX IF NOT EXISTS idx_reply_history_send_time ON reply_history(sendTime)")
        database.execSQL("CREATE INDEX IF NOT EXISTS idx_reply_history_source_app ON reply_history(sourceApp)")

        // Create indexes for optimization_metrics table
        database.execSQL("CREATE INDEX IF NOT EXISTS idx_optimization_metrics_feature_date ON optimization_metrics(featureKey, date)")
        database.execSQL("CREATE INDEX IF NOT EXISTS idx_optimization_metrics_variant_date ON optimization_metrics(variantId, date)")

        // Create indexes for app_configs table
        database.execSQL("CREATE INDEX IF NOT EXISTS idx_app_configs_last_used ON app_configs(lastUsed)")
        database.execSQL("CREATE INDEX IF NOT EXISTS idx_app_configs_monitored ON app_configs(isMonitored)")

        // Create indexes for keyword_rules table
        database.execSQL("CREATE INDEX IF NOT EXISTS idx_keyword_rules_enabled ON keyword_rules(enabled)")
        database.execSQL("CREATE INDEX IF NOT EXISTS idx_keyword_rules_category ON keyword_rules(category)")

        // Create indexes for llm_features and feature_variants tables
        database.execSQL("CREATE INDEX IF NOT EXISTS idx_llm_features_enabled ON llm_features(isEnabled)")
        database.execSQL("CREATE INDEX IF NOT EXISTS idx_feature_variants_feature_id ON feature_variants(featureId)")

        // Log migration completion
        database.execSQL("PRAGMA user_version = 7")
    }
}