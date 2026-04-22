package com.csbaby.kefu.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.csbaby.kefu.data.local.KefuDatabase
import com.csbaby.kefu.data.local.PreferencesManager
import com.csbaby.kefu.data.local.dao.*

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
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
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
}

