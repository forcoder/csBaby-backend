import sqlite3
import os


DATABASE_PATH = os.environ.get("DATABASE_PATH", "csBaby.db")


def get_connection() -> sqlite3.Connection:
    db = sqlite3.connect(DATABASE_PATH, timeout=10)
    db.row_factory = sqlite3.Row
    db.execute("PRAGMA journal_mode=WAL")
    db.execute("PRAGMA foreign_keys=ON")
    db.execute("PRAGMA busy_timeout=3000")
    return db


def init_db():
    db = get_connection()
    db.executescript("""
        CREATE TABLE IF NOT EXISTS devices (
            id TEXT PRIMARY KEY,
            token TEXT NOT NULL,
            name TEXT,
            platform TEXT DEFAULT 'android',
            app_version TEXT,
            last_heartbeat DATETIME,
            is_active INTEGER DEFAULT 1,
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
            FOREIGN KEY (device_id) REFERENCES devices(id) ON DELETE CASCADE
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
            FOREIGN KEY (device_id) REFERENCES devices(id) ON DELETE CASCADE
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
            FOREIGN KEY (device_id) REFERENCES devices(id) ON DELETE CASCADE
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
            FOREIGN KEY (device_id) REFERENCES devices(id) ON DELETE CASCADE,
            FOREIGN KEY (reply_history_id) REFERENCES reply_history(id) ON DELETE CASCADE
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
            FOREIGN KEY (device_id) REFERENCES devices(id) ON DELETE CASCADE
        );

        CREATE INDEX IF NOT EXISTS idx_rules_device ON keyword_rules(device_id);
        CREATE INDEX IF NOT EXISTS idx_rules_keyword ON keyword_rules(keyword);
        CREATE INDEX IF NOT EXISTS idx_history_device_created ON reply_history(device_id, created_at DESC);
        CREATE INDEX IF NOT EXISTS idx_feedback_device ON feedback(device_id);
        CREATE INDEX IF NOT EXISTS idx_feedback_history ON feedback(reply_history_id);
        CREATE INDEX IF NOT EXISTS idx_metrics_device_date ON optimization_metrics(device_id, date);
        CREATE INDEX IF NOT EXISTS idx_models_device_enabled ON model_configs(device_id, enabled);
        CREATE INDEX IF NOT EXISTS idx_devices_heartbeat ON devices(last_heartbeat DESC);
        CREATE INDEX IF NOT EXISTS idx_history_source ON reply_history(source);
        CREATE INDEX IF NOT EXISTS idx_feedback_action ON feedback(action);

        CREATE TABLE IF NOT EXISTS agent_status (
            phone TEXT PRIMARY KEY,
            agent_name TEXT DEFAULT '',
            status TEXT DEFAULT 'online',
            current_load INTEGER DEFAULT 0,
            max_concurrent INTEGER DEFAULT 5,
            tenant_id TEXT DEFAULT '',
            updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
        );

        CREATE TABLE IF NOT EXISTS agent_skills (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            agent_phone TEXT NOT NULL,
            skill_tag TEXT NOT NULL,
            proficiency INTEGER DEFAULT 5,
            FOREIGN KEY (agent_phone) REFERENCES agent_status(phone) ON DELETE CASCADE
        );

        CREATE TABLE IF NOT EXISTS routing_config (
            key TEXT PRIMARY KEY,
            value TEXT NOT NULL,
            updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
        );

        CREATE TABLE IF NOT EXISTS sessions (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            tenant_id TEXT NOT NULL,
            customer_name TEXT DEFAULT '',
            customer_phone TEXT DEFAULT '',
            platform TEXT DEFAULT '',
            assigned_agent_phone TEXT DEFAULT '',
            status TEXT DEFAULT 'pending',
            priority INTEGER DEFAULT 0,
            skill_required TEXT DEFAULT '',
            created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
            updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
            closed_at DATETIME DEFAULT NULL
        );

        CREATE TABLE IF NOT EXISTS tenant_style_config (
            device_id TEXT PRIMARY KEY,
            theme TEXT DEFAULT 'light',
            primary_color TEXT DEFAULT '#1976D2',
            accent_color TEXT default '#FF4081',
            font_size TEXT DEFAULT 'medium',
            bubble_style TEXT DEFAULT 'rounded',
            avatar_enabled INTEGER DEFAULT 1,
            show_timestamp INTEGER DEFAULT 1,
            send_sound INTEGER DEFAULT 1,
            custom_css TEXT DEFAULT '',
            updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
            FOREIGN KEY (device_id) REFERENCES devices(id) ON DELETE CASCADE
        );

        CREATE TABLE IF NOT EXISTS tenant_app_config (
            device_id TEXT PRIMARY KEY,
            app_name TEXT DEFAULT '客服小秘',
            welcome_message TEXT DEFAULT '您好，请问有什么可以帮您？',
            offline_message TEXT DEFAULT '当前无客服在线，请稍后再试。',
            auto_reply_enabled INTEGER DEFAULT 1,
            notification_enabled INTEGER DEFAULT 1,
            voice_enabled INTEGER DEFAULT 0,
            language TEXT DEFAULT 'zh-CN',
            session_timeout INTEGER DEFAULT 300,
            max_queue_size INTEGER DEFAULT 50,
            file_upload_enabled INTEGER DEFAULT 1,
            updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
            FOREIGN KEY (device_id) REFERENCES devices(id) ON DELETE CASCADE
        );

        CREATE INDEX IF NOT EXISTS idx_agent_skills_phone ON agent_skills(agent_phone);
        CREATE INDEX IF NOT EXISTS idx_sessions_tenant ON sessions(tenant_id);
        CREATE INDEX IF NOT EXISTS idx_sessions_status ON sessions(status);

        CREATE TABLE IF NOT EXISTS admin_sessions (
            token TEXT PRIMARY KEY,
            phone TEXT NOT NULL,
            created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
            expires_at DATETIME NOT NULL
        );
        CREATE INDEX IF NOT EXISTS idx_admin_sessions_phone ON admin_sessions(phone);
        CREATE INDEX IF NOT EXISTS idx_admin_sessions_expires ON admin_sessions(expires_at);
    """)
    db.commit()
    db.close()
