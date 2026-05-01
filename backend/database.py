import sqlite3
import os
from config import DATABASE_PATH


def get_db():
    """获取数据库连接"""
    db = sqlite3.connect(DATABASE_PATH)
    db.row_factory = sqlite3.Row
    db.execute("PRAGMA journal_mode=WAL")
    db.execute("PRAGMA foreign_keys=ON")
    return db


def init_db():
    """初始化数据库表"""
    db = get_db()
    db.executescript("""
        CREATE TABLE IF NOT EXISTS devices (
            id TEXT PRIMARY KEY,
            token TEXT NOT NULL,
            name TEXT,
            platform TEXT DEFAULT 'android',
            app_version TEXT,
            last_heartbeat DATETIME,
            created_at DATETIME DEFAULT CURRENT_TIMESTAMP
        );

        CREATE TABLE IF NOT EXISTS keyword_rules (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            device_id TEXT NOT NULL,
            keyword TEXT NOT NULL,
            match_type TEXT DEFAULT 'CONTAINS',
            reply_template TEXT NOT NULL,
            category TEXT DEFAULT '',
            target_type TEXT DEFAULT 'ALL',
            target_names TEXT DEFAULT '[]',
            priority INTEGER DEFAULT 0,
            enabled INTEGER DEFAULT 1,
            created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
            updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
            FOREIGN KEY (device_id) REFERENCES devices(id)
        );

        CREATE TABLE IF NOT EXISTS model_configs (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            device_id TEXT NOT NULL,
            name TEXT NOT NULL,
            model_type TEXT NOT NULL,
            model TEXT NOT NULL,
            api_key TEXT NOT NULL,
            api_endpoint TEXT,
            temperature REAL DEFAULT 0.7,
            max_tokens INTEGER DEFAULT 2000,
            is_default INTEGER DEFAULT 0,
            enabled INTEGER DEFAULT 1,
            created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
            updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
            FOREIGN KEY (device_id) REFERENCES devices(id)
        );

        CREATE TABLE IF NOT EXISTS reply_history (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            device_id TEXT NOT NULL,
            original_message TEXT,
            reply_content TEXT,
            source TEXT DEFAULT 'ai',
            model_used TEXT,
            confidence REAL,
            response_time_ms INTEGER,
            platform TEXT,
            customer_name TEXT,
            house_name TEXT,
            created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
            FOREIGN KEY (device_id) REFERENCES devices(id)
        );

        CREATE TABLE IF NOT EXISTS feedback (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            device_id TEXT NOT NULL,
            reply_history_id INTEGER,
            action TEXT NOT NULL,
            modified_text TEXT,
            rating INTEGER,
            comment TEXT,
            created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
            FOREIGN KEY (device_id) REFERENCES devices(id)
        );

        CREATE TABLE IF NOT EXISTS optimization_metrics (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            device_id TEXT NOT NULL,
            date TEXT NOT NULL,
            total_generated INTEGER DEFAULT 0,
            total_accepted INTEGER DEFAULT 0,
            total_modified INTEGER DEFAULT 0,
            total_rejected INTEGER DEFAULT 0,
            avg_confidence REAL DEFAULT 0,
            avg_response_time_ms INTEGER DEFAULT 0,
            UNIQUE(device_id, date),
            FOREIGN KEY (device_id) REFERENCES devices(id)
        );

        CREATE INDEX IF NOT EXISTS idx_rules_device ON keyword_rules(device_id);
        CREATE INDEX IF NOT EXISTS idx_rules_keyword ON keyword_rules(keyword);
        CREATE INDEX IF NOT EXISTS idx_history_device ON reply_history(device_id);
        CREATE INDEX IF NOT EXISTS idx_history_created ON reply_history(created_at);
        CREATE INDEX IF NOT EXISTS idx_feedback_device ON feedback(device_id);
        CREATE INDEX IF NOT EXISTS idx_metrics_device_date ON optimization_metrics(device_id, date);
    """)
    db.commit()
    db.close()


def dict_from_row(row):
    """将 sqlite3.Row 转为 dict"""
    if row is None:
        return None
    return dict(row)
