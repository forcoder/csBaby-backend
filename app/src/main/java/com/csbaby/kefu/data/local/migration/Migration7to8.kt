package com.csbaby.kefu.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration from version 7 to 8:
 * Add tenantId column to all entity tables for multi-tenant data isolation.
 */
class Migration7to8 : Migration(7, 8) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Add tenantId to all entity tables
        database.execSQL("ALTER TABLE app_configs ADD COLUMN tenantId TEXT NOT NULL DEFAULT ''")
        database.execSQL("ALTER TABLE keyword_rules ADD COLUMN tenantId TEXT NOT NULL DEFAULT ''")
        database.execSQL("ALTER TABLE scenarios ADD COLUMN tenantId TEXT NOT NULL DEFAULT ''")
        database.execSQL("ALTER TABLE ai_model_configs ADD COLUMN tenantId TEXT NOT NULL DEFAULT ''")
        database.execSQL("ALTER TABLE user_style_profiles ADD COLUMN tenantId TEXT NOT NULL DEFAULT ''")
        database.execSQL("ALTER TABLE reply_history ADD COLUMN tenantId TEXT NOT NULL DEFAULT ''")
        database.execSQL("ALTER TABLE message_blacklist ADD COLUMN tenantId TEXT NOT NULL DEFAULT ''")
        database.execSQL("ALTER TABLE llm_features ADD COLUMN tenantId TEXT NOT NULL DEFAULT ''")
        database.execSQL("ALTER TABLE feature_variants ADD COLUMN tenantId TEXT NOT NULL DEFAULT ''")
        database.execSQL("ALTER TABLE optimization_metrics ADD COLUMN tenantId TEXT NOT NULL DEFAULT ''")
        database.execSQL("ALTER TABLE optimization_events ADD COLUMN tenantId TEXT NOT NULL DEFAULT ''")
        database.execSQL("ALTER TABLE reply_feedback ADD COLUMN tenantId TEXT NOT NULL DEFAULT ''")

        // Create indexes on tenantId for query performance
        database.execSQL("CREATE INDEX IF NOT EXISTS index_app_configs_tenantId ON app_configs(tenantId)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_keyword_rules_tenantId ON keyword_rules(tenantId)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_scenarios_tenantId ON scenarios(tenantId)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_ai_model_configs_tenantId ON ai_model_configs(tenantId)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_user_style_profiles_tenantId ON user_style_profiles(tenantId)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_reply_history_tenantId ON reply_history(tenantId)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_message_blacklist_tenantId ON message_blacklist(tenantId)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_llm_features_tenantId ON llm_features(tenantId)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_feature_variants_tenantId ON feature_variants(tenantId)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_optimization_metrics_tenantId ON optimization_metrics(tenantId)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_optimization_events_tenantId ON optimization_events(tenantId)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_reply_feedback_tenantId ON reply_feedback(tenantId)")
    }
}
