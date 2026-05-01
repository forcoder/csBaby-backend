package com.csbaby.kefu.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration from version 6 to 7:
 * Schema-only version bump to resolve Room identity hash mismatch
 * after @Entity indices were removed. No structural changes.
 */
class Migration6to7 : Migration(6, 7) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // No-op: entity schema changed (indices removed) without structural DB changes.
        // This migration exists solely to satisfy Room's identity check when upgrading from v6 to v7.
    }
}