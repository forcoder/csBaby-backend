package com.csbaby.kefu.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration from version 5 to 6:
 * - Add featureKey and variantId columns to reply_history table
 * - Create missing index on feature_variants.featureId
 * - Handle case where feature_variants table doesn't exist (legacy v4 databases)
 */
class Migration5to6 : Migration(5, 6) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Add featureKey column to reply_history table
        database.execSQL("ALTER TABLE reply_history ADD COLUMN featureKey TEXT")

        // Add variantId column to reply_history table
        database.execSQL("ALTER TABLE reply_history ADD COLUMN variantId INTEGER")

        // Create llm_features table if it doesn't exist (legacy v4 databases)
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS llm_features (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                featureKey TEXT NOT NULL UNIQUE,
                displayName TEXT NOT NULL,
                description TEXT NOT NULL,
                isEnabled INTEGER NOT NULL DEFAULT 1,
                defaultVariantId INTEGER,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL
            )
        """.trimIndent())

        // Create feature_variants table if it doesn't exist (legacy v4 databases)
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS feature_variants (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                featureId INTEGER NOT NULL,
                variantName TEXT NOT NULL,
                variantType TEXT NOT NULL,
                systemPrompt TEXT NOT NULL DEFAULT '',
                userPromptTemplate TEXT NOT NULL DEFAULT '',
                modelId INTEGER,
                temperature REAL,
                maxTokens INTEGER,
                strategyConfig TEXT NOT NULL DEFAULT '{}',
                isActive INTEGER NOT NULL DEFAULT 0,
                trafficPercentage INTEGER NOT NULL DEFAULT 0,
                createdAt INTEGER NOT NULL,
                FOREIGN KEY (featureId) REFERENCES llm_features (id) ON DELETE CASCADE
            )
        """.trimIndent())

        // Create optimization_metrics table if it doesn't exist
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS optimization_metrics (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                featureKey TEXT NOT NULL,
                variantId INTEGER NOT NULL,
                date TEXT NOT NULL,
                totalGenerated INTEGER NOT NULL DEFAULT 0,
                totalAccepted INTEGER NOT NULL DEFAULT 0,
                totalModified INTEGER NOT NULL DEFAULT 0,
                totalRejected INTEGER NOT NULL DEFAULT 0,
                avgConfidence REAL NOT NULL DEFAULT 0.0,
                avgResponseTimeMs INTEGER NOT NULL DEFAULT 0,
                accuracyScore REAL NOT NULL DEFAULT 0.0,
                UNIQUE(featureKey, variantId, date)
            )
        """.trimIndent())

        // Create optimization_events table if it doesn't exist
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS optimization_events (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                featureKey TEXT NOT NULL,
                eventType TEXT NOT NULL,
                oldConfig TEXT NOT NULL DEFAULT '',
                newConfig TEXT NOT NULL DEFAULT '',
                reason TEXT NOT NULL,
                triggeredBy TEXT NOT NULL,
                createdAt INTEGER NOT NULL
            )
        """.trimIndent())

        // Create reply_feedback table if it doesn't exist
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS reply_feedback (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                replyHistoryId INTEGER NOT NULL,
                variantId INTEGER,
                userAction TEXT NOT NULL,
                modifiedPart TEXT,
                userRating INTEGER,
                feedbackText TEXT,
                createdAt INTEGER NOT NULL,
                FOREIGN KEY (replyHistoryId) REFERENCES reply_history (id) ON DELETE CASCADE
            )
        """.trimIndent())
    }
}