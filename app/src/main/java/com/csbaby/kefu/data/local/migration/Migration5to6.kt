package com.csbaby.kefu.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration from version 5 to 6:
 * - Add featureKey and variantId columns to reply_history table
 * - Create missing index on feature_variants.featureId
 */
class Migration5to6 : Migration(5, 6) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Add featureKey column to reply_history table
        database.execSQL("ALTER TABLE reply_history ADD COLUMN feature_key TEXT DEFAULT NULL")

        // Add variantId column to reply_history table
        database.execSQL("ALTER TABLE reply_history ADD COLUMN variant_id INTEGER DEFAULT NULL")

        // Create missing index on feature_variants.featureId
        database.execSQL("CREATE INDEX IF NOT EXISTS index_feature_variants_featureId ON feature_variants(featureId)")
    }
}