package com.csbaby.kefu.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.csbaby.kefu.data.local.KefuDatabase
import com.csbaby.kefu.data.local.PreferencesManager
import com.csbaby.kefu.data.local.dao.*
import com.csbaby.kefu.data.local.migration.Migration5to6

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): KefuDatabase {
        return Room.databaseBuilder(
            context,
            KefuDatabase::class.java,
            KefuDatabase.DATABASE_NAME
        )
            .addMigrations(
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_4_6,
                Migration5to6(),
                Migration6to7() // 添加新版本迁移
            )
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    // 数据库创建时初始化索引
                    KefuDatabase.createIndexes(db)
                }

                override fun onOpen(db: SupportSQLiteDatabase) {
                    super.onOpen(db)
                    // 数据库打开时确保索引存在
                    KefuDatabase.createIndexes(db)
                }
            })
            .enableMultiInstanceInvalidation() // 支持多实例并发访问
            .setJournalMode(RoomDatabase.JournalMode.WAL) // 使用WAL模式提高并发性能
            .build()
    }


    @Provides
    @Singleton
    fun provideAppConfigDao(database: KefuDatabase): AppConfigDao {
        return database.appConfigDao()
    }

    @Provides
    @Singleton
    fun provideKeywordRuleDao(database: KefuDatabase): KeywordRuleDao {
        return database.keywordRuleDao()
    }

    @Provides
    @Singleton
    fun provideScenarioDao(database: KefuDatabase): ScenarioDao {
        return database.scenarioDao()
    }

    @Provides
    @Singleton
    fun provideAIModelConfigDao(database: KefuDatabase): AIModelConfigDao {
        return database.aiModelConfigDao()
    }

    @Provides
    @Singleton
    fun provideUserStyleProfileDao(database: KefuDatabase): UserStyleProfileDao {
        return database.userStyleProfileDao()
    }

    @Provides
    @Singleton
    fun provideReplyHistoryDao(database: KefuDatabase): ReplyHistoryDao {
        return database.replyHistoryDao()
    }

    @Provides
    @Singleton
    fun provideMessageBlacklistDao(database: KefuDatabase): MessageBlacklistDao {
        return database.messageBlacklistDao()
    }

    @Provides
    @Singleton
    fun provideLLMFeatureDao(database: KefuDatabase): LLMFeatureDao {
        return database.llmFeatureDao()
    }

    @Provides
    @Singleton
    fun provideFeatureVariantDao(database: KefuDatabase): FeatureVariantDao {
        return database.featureVariantDao()
    }

    @Provides
    @Singleton
    fun provideOptimizationMetricsDao(database: KefuDatabase): OptimizationMetricsDao {
        return database.optimizationMetricsDao()
    }

    @Provides
    @Singleton
    fun provideOptimizationEventDao(database: KefuDatabase): OptimizationEventDao {
        return database.optimizationEventDao()
    }

    @Provides
    @Singleton
    fun provideReplyFeedbackDao(database: KefuDatabase): ReplyFeedbackDao {
        return database.replyFeedbackDao()
    }

    @Provides
    @Singleton
    fun providePreferencesManager(@ApplicationContext context: Context): PreferencesManager {
        return PreferencesManager(context)
    }

    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                "ALTER TABLE keyword_rules ADD COLUMN targetType TEXT NOT NULL DEFAULT 'ALL'"
            )
            database.execSQL(
                "ALTER TABLE keyword_rules ADD COLUMN targetNamesJson TEXT NOT NULL DEFAULT '[]'"
            )
        }
    }

    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS message_blacklist (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    type TEXT NOT NULL,
                    value TEXT NOT NULL,
                    description TEXT NOT NULL,
                    packageName TEXT,
                    createdAt INTEGER NOT NULL,
                    isEnabled INTEGER NOT NULL DEFAULT 1
                )
                """.trimIndent()
            )
        }
    }

    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                "ALTER TABLE ai_model_configs ADD COLUMN model TEXT NOT NULL DEFAULT ''"
            )
        }
    }

    private val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Add new tables for LLM feature management and optimization
            database.execSQL(
                """
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
                """.trimIndent()
            )

            database.execSQL(
                """
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
                """.trimIndent()
            )

            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS optimization_metrics (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    featureKey TEXT NOT NULL DEFAULT '',
                    variantId INTEGER NOT NULL DEFAULT 0,
                    date TEXT NOT NULL DEFAULT '',
                    totalGenerated INTEGER NOT NULL DEFAULT 0,
                    totalAccepted INTEGER NOT NULL DEFAULT 0,
                    totalModified INTEGER NOT NULL DEFAULT 0,
                    totalRejected INTEGER NOT NULL DEFAULT 0,
                    avgConfidence REAL NOT NULL DEFAULT 0.0,
                    avgResponseTimeMs INTEGER NOT NULL DEFAULT 0,
                    accuracyScore REAL NOT NULL DEFAULT 0.0,
                    UNIQUE(featureKey, variantId, date)
                )
                """.trimIndent()
            )

            database.execSQL(
                """
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
                """.trimIndent()
            )

            database.execSQL(
                """
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
                """.trimIndent()
            )

            // Add new columns to reply_history
            database.execSQL(
                "ALTER TABLE reply_history ADD COLUMN featureKey TEXT"
            )
            database.execSQL(
                "ALTER TABLE reply_history ADD COLUMN variantId INTEGER"
            )
        }
    }

    private val MIGRATION_4_6 = object : Migration(4, 6) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // === Combined MIGRATION_4_5 + Migration5to6 ===

            // Create llm_features table
            database.execSQL(
                """
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
                """.trimIndent()
            )

            // Create feature_variants table
            database.execSQL(
                """
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
                """.trimIndent()
            )

            // Create optimization_metrics table
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS optimization_metrics (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    featureKey TEXT NOT NULL DEFAULT '',
                    variantId INTEGER NOT NULL DEFAULT 0,
                    date TEXT NOT NULL DEFAULT '',
                    totalGenerated INTEGER NOT NULL DEFAULT 0,
                    totalAccepted INTEGER NOT NULL DEFAULT 0,
                    totalModified INTEGER NOT NULL DEFAULT 0,
                    totalRejected INTEGER NOT NULL DEFAULT 0,
                    avgConfidence REAL NOT NULL DEFAULT 0.0,
                    avgResponseTimeMs INTEGER NOT NULL DEFAULT 0,
                    accuracyScore REAL NOT NULL DEFAULT 0.0,
                    UNIQUE(featureKey, variantId, date)
                )
                """.trimIndent()
            )

            // Create optimization_events table
            database.execSQL(
                """
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
                """.trimIndent()
            )

            // Create reply_feedback table
            database.execSQL(
                """
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
                """.trimIndent()
            )

            // Add new columns to reply_history
            database.execSQL("ALTER TABLE reply_history ADD COLUMN featureKey TEXT")
            database.execSQL("ALTER TABLE reply_history ADD COLUMN variantId INTEGER")

            // Create index on feature_variants.featureId
            database.execSQL("CREATE INDEX IF NOT EXISTS index_feature_variants_featureId ON feature_variants(featureId)")
        }
    }
}

