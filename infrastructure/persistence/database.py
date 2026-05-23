"""
Database abstraction layer - supports SQLite (local) and PostgreSQL (production).
"""
import os
import logging

logger = logging.getLogger(__name__)

# Try to import psycopg2 for PostgreSQL support
PSYCOPG2_AVAILABLE = False
try:
    import psycopg2
    from psycopg2 import pool as pg_pool_module
    PSYCOPG2_AVAILABLE = True
except ImportError:
    psycopg2 = None
    pg_pool_module = None
    logger.warning("psycopg2 not available - PostgreSQL features disabled")


# Flask app reference for logging (set by app.py)
_flask_app = None

def set_flask_app(app):
    global _flask_app
    _flask_app = app


DATABASE_URL = os.environ.get("DATABASE_URL", "")
DB_SCHEMA = os.environ.get("DB_SCHEMA", "public")
DATABASE_PATH = os.environ.get("DATABASE_PATH", "csBaby.db")


def _log(msg):
    """Log to Flask app logger or standard logger."""
    if _flask_app:
        _flask_app.logger.info(msg)
    else:
        logger.info(msg)


def _log_error(msg):
    """Log error to Flask app logger or standard logger."""
    if _flask_app:
        _flask_app.logger.error(msg)
    else:
        logger.error(msg)


# =============================================================================
# PostgreSQL Support (Production on Render)
# =============================================================================
class PostgresqlConnectionPool:
    _pool = None

    @classmethod
    def get_pool(cls):
        if not PSYCOPG2_AVAILABLE:
            raise RuntimeError("psycopg2 not installed")
        if cls._pool is None:
            cls._pool = pg_pool_module.ThreadedConnectionPool(
                minconn=1,
                maxconn=10,
                dsn=DATABASE_URL,
                options=f"-c search_path={DB_SCHEMA},public"
            )
        return cls._pool

    @classmethod
    def get_connection(cls):
        return cls.get_pool().getconn()

    @classmethod
    def return_connection(cls, conn):
        cls.get_pool().putconn(conn)


def _pg_get_connection():
    """Get PostgreSQL connection."""
    if not PSYCOPG2_AVAILABLE:
        raise RuntimeError("PostgreSQL not available - psycopg2 not installed")
    conn = PostgresqlConnectionPool.get_connection()
    conn.row_factory = psycopg2.row.DictRow
    return conn


def _pg_create_tables(db):
    """Create all required tables in PostgreSQL."""
    cursor = db.cursor()

    cursor.execute("CREATE EXTENSION IF NOT EXISTS \"uuid-ossp\"")

    tables = [
        """CREATE TABLE IF NOT EXISTS users (
            id TEXT PRIMARY KEY, phone TEXT UNIQUE NOT NULL,
            password_hash TEXT NOT NULL, salt TEXT NOT NULL,
            name TEXT DEFAULT '', created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        )""",
        """CREATE TABLE IF NOT EXISTS user_devices (
            user_id TEXT NOT NULL, device_id TEXT NOT NULL,
            platform TEXT DEFAULT 'android', device_name TEXT DEFAULT '',
            registered_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            PRIMARY KEY (user_id, device_id),
            FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
        )""",
        """CREATE TABLE IF NOT EXISTS devices (
            id TEXT PRIMARY KEY, token TEXT NOT NULL, name TEXT,
            platform TEXT DEFAULT 'android', app_version TEXT,
            last_heartbeat TIMESTAMP, is_active INTEGER DEFAULT 1,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        )""",
        """CREATE TABLE IF NOT EXISTS keyword_rules (
            id SERIAL PRIMARY KEY, user_id TEXT NOT NULL, keyword TEXT NOT NULL,
            match_type TEXT DEFAULT 'CONTAINS', reply_template TEXT NOT NULL,
            category TEXT DEFAULT '', target_type TEXT DEFAULT 'ALL',
            target_names TEXT DEFAULT '[]', priority INTEGER DEFAULT 0,
            enabled BOOLEAN DEFAULT TRUE,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
        )""",
        """CREATE TABLE IF NOT EXISTS model_configs (
            id SERIAL PRIMARY KEY, user_id TEXT NOT NULL, name TEXT NOT NULL,
            model_type TEXT NOT NULL, model TEXT NOT NULL, api_key TEXT NOT NULL,
            api_endpoint TEXT, temperature REAL DEFAULT 0.7,
            max_tokens INTEGER DEFAULT 2000, is_default BOOLEAN DEFAULT FALSE,
            enabled BOOLEAN DEFAULT TRUE,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
        )""",
        """CREATE TABLE IF NOT EXISTS reply_history (
            id SERIAL PRIMARY KEY, user_id TEXT NOT NULL, original_message TEXT,
            reply_content TEXT, source TEXT DEFAULT 'ai', model_used TEXT,
            confidence REAL, response_time_ms INTEGER, platform TEXT,
            customer_name TEXT, house_name TEXT,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
        )""",
        """CREATE TABLE IF NOT EXISTS feedback (
            id SERIAL PRIMARY KEY, user_id TEXT NOT NULL, reply_history_id INTEGER,
            action TEXT NOT NULL, modified_text TEXT, rating INTEGER, comment TEXT,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
            FOREIGN KEY (reply_history_id) REFERENCES reply_history(id) ON DELETE SET NULL
        )""",
        """CREATE TABLE IF NOT EXISTS optimization_metrics (
            id SERIAL PRIMARY KEY, user_id TEXT NOT NULL, date TEXT NOT NULL,
            total_generated INTEGER DEFAULT 0, total_accepted INTEGER DEFAULT 0,
            total_modified INTEGER DEFAULT 0, total_rejected INTEGER DEFAULT 0,
            avg_confidence REAL DEFAULT 0, avg_response_time_ms INTEGER DEFAULT 0,
            UNIQUE(user_id, date),
            FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
        )""",
        """CREATE TABLE IF NOT EXISTS blacklist (
            id SERIAL PRIMARY KEY, user_id TEXT NOT NULL, type TEXT DEFAULT 'KEYWORD',
            value TEXT NOT NULL, description TEXT DEFAULT '', package_name TEXT,
            is_enabled BOOLEAN DEFAULT TRUE, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
        )""",
        """CREATE TABLE IF NOT EXISTS agent_status (
            phone TEXT PRIMARY KEY, agent_name TEXT DEFAULT '',
            status TEXT DEFAULT 'online', current_load INTEGER DEFAULT 0,
            max_concurrent INTEGER DEFAULT 5, user_id TEXT DEFAULT '',
            updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        )""",
        """CREATE TABLE IF NOT EXISTS agent_skills (
            id SERIAL PRIMARY KEY, agent_phone TEXT NOT NULL,
            skill_tag TEXT NOT NULL, proficiency INTEGER DEFAULT 5,
            FOREIGN KEY (agent_phone) REFERENCES agent_status(phone) ON DELETE CASCADE
        )""",
        """CREATE TABLE IF NOT EXISTS routing_config (
            key TEXT PRIMARY KEY, value TEXT NOT NULL,
            updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        )""",
        """CREATE TABLE IF NOT EXISTS sessions (
            id SERIAL PRIMARY KEY, user_id TEXT NOT NULL,
            customer_name TEXT DEFAULT '', customer_phone TEXT DEFAULT '',
            platform TEXT DEFAULT '', assigned_agent_phone TEXT DEFAULT '',
            status TEXT DEFAULT 'pending', priority INTEGER DEFAULT 0,
            skill_required TEXT DEFAULT '',
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, closed_at TIMESTAMP DEFAULT NULL
        )""",
        """CREATE TABLE IF NOT EXISTS tenant_style_config (
            user_id TEXT PRIMARY KEY, theme TEXT DEFAULT 'light',
            primary_color TEXT DEFAULT '#1976D2', accent_color TEXT DEFAULT '#FF4081',
            font_size TEXT DEFAULT 'medium', bubble_style TEXT DEFAULT 'rounded',
            avatar_enabled BOOLEAN DEFAULT TRUE, show_timestamp BOOLEAN DEFAULT TRUE,
            send_sound BOOLEAN DEFAULT TRUE, custom_css TEXT DEFAULT '',
            updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
        )""",
        """CREATE TABLE IF NOT EXISTS tenant_app_config (
            user_id TEXT PRIMARY KEY, app_name TEXT DEFAULT '客服小秘',
            welcome_message TEXT DEFAULT '您好，请问有什么可以帮您？',
            offline_message TEXT DEFAULT '当前无客服在线，请稍后再试。',
            auto_reply_enabled BOOLEAN DEFAULT TRUE, notification_enabled BOOLEAN DEFAULT TRUE,
            voice_enabled BOOLEAN DEFAULT FALSE, language TEXT DEFAULT 'zh-CN',
            session_timeout INTEGER DEFAULT 300, max_queue_size INTEGER DEFAULT 50,
            file_upload_enabled BOOLEAN DEFAULT TRUE,
            updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
        )""",
        """CREATE TABLE IF NOT EXISTS admin_sessions (
            token TEXT PRIMARY KEY, phone TEXT NOT NULL,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, expires_at TIMESTAMP NOT NULL
        )""",
    ]
    for sql in tables:
        cursor.execute(sql)

    indexes = [
        "CREATE INDEX IF NOT EXISTS idx_rules_user ON keyword_rules(user_id)",
        "CREATE INDEX IF NOT EXISTS idx_rules_keyword ON keyword_rules(keyword)",
        "CREATE INDEX IF NOT EXISTS idx_history_user_created ON reply_history(user_id, created_at DESC)",
        "CREATE INDEX IF NOT EXISTS idx_feedback_user ON feedback(user_id)",
        "CREATE INDEX IF NOT EXISTS idx_feedback_history ON feedback(reply_history_id)",
        "CREATE INDEX IF NOT EXISTS idx_metrics_user_date ON optimization_metrics(user_id, date)",
        "CREATE INDEX IF NOT EXISTS idx_models_user_enabled ON model_configs(user_id, enabled)",
        "CREATE INDEX IF NOT EXISTS idx_devices_heartbeat ON devices(last_heartbeat DESC)",
        "CREATE INDEX IF NOT EXISTS idx_history_source ON reply_history(source)",
        "CREATE INDEX IF NOT EXISTS idx_feedback_action ON feedback(action)",
        "CREATE INDEX IF NOT EXISTS idx_blacklist_user ON blacklist(user_id)",
        "CREATE INDEX IF NOT EXISTS idx_user_devices_user ON user_devices(user_id)",
        "CREATE INDEX IF NOT EXISTS idx_agent_skills_phone ON agent_skills(agent_phone)",
        "CREATE INDEX IF NOT EXISTS idx_sessions_user ON sessions(user_id)",
        "CREATE INDEX IF NOT EXISTS idx_sessions_status ON sessions(status)",
        "CREATE INDEX IF NOT EXISTS idx_admin_sessions_phone ON admin_sessions(phone)",
        "CREATE INDEX IF NOT EXISTS idx_admin_sessions_expires ON admin_sessions(expires_at)",
    ]
    for idx in indexes:
        cursor.execute(idx)

    db.commit()
    cursor.close()


# =============================================================================
# SQLite Support (Local Development)
# =============================================================================
def _sqlite_get_connection():
    """Get SQLite connection."""
    import sqlite3
    db = sqlite3.connect(DATABASE_PATH, timeout=10)
    db.row_factory = sqlite3.Row
    db.execute("PRAGMA journal_mode=WAL")
    db.execute("PRAGMA foreign_keys=ON")
    db.execute("PRAGMA busy_timeout=3000")
    return db


def _sqlite_init_tables(db):
    """Initialize SQLite tables (legacy schema)."""
    db.executescript("""
        CREATE TABLE IF NOT EXISTS users (id TEXT PRIMARY KEY, phone TEXT UNIQUE NOT NULL,
            password_hash TEXT NOT NULL, salt TEXT NOT NULL, name TEXT DEFAULT '',
            created_at DATETIME DEFAULT CURRENT_TIMESTAMP);
        CREATE TABLE IF NOT EXISTS user_devices (user_id TEXT NOT NULL, device_id TEXT NOT NULL,
            platform TEXT DEFAULT 'android', device_name TEXT DEFAULT '',
            registered_at DATETIME DEFAULT CURRENT_TIMESTAMP,
            PRIMARY KEY (user_id, device_id),
            FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE);
        CREATE TABLE IF NOT EXISTS devices (id TEXT PRIMARY KEY, token TEXT NOT NULL, name TEXT,
            platform TEXT DEFAULT 'android', app_version TEXT, last_heartbeat DATETIME,
            is_active INTEGER DEFAULT 1, created_at DATETIME DEFAULT CURRENT_TIMESTAMP);
        CREATE TABLE IF NOT EXISTS keyword_rules (id INTEGER PRIMARY KEY AUTOINCREMENT,
            user_id TEXT NOT NULL, keyword TEXT NOT NULL, match_type TEXT DEFAULT 'CONTAINS',
            reply_template TEXT NOT NULL, category TEXT DEFAULT '', target_type TEXT DEFAULT 'ALL',
            target_names TEXT DEFAULT '[]', priority INTEGER DEFAULT 0, enabled INTEGER DEFAULT 1,
            created_at DATETIME DEFAULT CURRENT_TIMESTAMP, updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
            FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE);
        CREATE TABLE IF NOT EXISTS model_configs (id INTEGER PRIMARY KEY AUTOINCREMENT,
            user_id TEXT NOT NULL, name TEXT NOT NULL, model_type TEXT NOT NULL, model TEXT NOT NULL,
            api_key TEXT NOT NULL, api_endpoint TEXT, temperature REAL DEFAULT 0.7,
            max_tokens INTEGER DEFAULT 2000, is_default INTEGER DEFAULT 0, enabled INTEGER DEFAULT 1,
            created_at DATETIME DEFAULT CURRENT_TIMESTAMP, updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
            FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE);
        CREATE TABLE IF NOT EXISTS reply_history (id INTEGER PRIMARY KEY AUTOINCREMENT,
            user_id TEXT NOT NULL, original_message TEXT, reply_content TEXT,
            source TEXT DEFAULT 'ai', model_used TEXT, confidence REAL, response_time_ms INTEGER,
            platform TEXT, customer_name TEXT, house_name TEXT,
            created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
            FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE);
        CREATE TABLE IF NOT EXISTS feedback (id INTEGER PRIMARY KEY AUTOINCREMENT,
            user_id TEXT NOT NULL, reply_history_id INTEGER, action TEXT NOT NULL,
            modified_text TEXT, rating INTEGER, comment TEXT, created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
            FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
            FOREIGN KEY (reply_history_id) REFERENCES reply_history(id) ON DELETE CASCADE);
        CREATE TABLE IF NOT EXISTS optimization_metrics (id INTEGER PRIMARY KEY AUTOINCREMENT,
            user_id TEXT NOT NULL, date TEXT NOT NULL, total_generated INTEGER DEFAULT 0,
            total_accepted INTEGER DEFAULT 0, total_modified INTEGER DEFAULT 0,
            total_rejected INTEGER DEFAULT 0, avg_confidence REAL DEFAULT 0,
            avg_response_time_ms INTEGER DEFAULT 0, UNIQUE(user_id, date),
            FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE);
        CREATE TABLE IF NOT EXISTS blacklist (id INTEGER PRIMARY KEY AUTOINCREMENT,
            user_id TEXT NOT NULL, type TEXT DEFAULT 'KEYWORD', value TEXT NOT NULL,
            description TEXT DEFAULT '', package_name TEXT, is_enabled INTEGER DEFAULT 1,
            created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
            FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE);
        CREATE TABLE IF NOT EXISTS agent_status (phone TEXT PRIMARY KEY, agent_name TEXT DEFAULT '',
            status TEXT DEFAULT 'online', current_load INTEGER DEFAULT 0,
            max_concurrent INTEGER DEFAULT 5, user_id TEXT DEFAULT '',
            updated_at DATETIME DEFAULT CURRENT_TIMESTAMP);
        CREATE TABLE IF NOT EXISTS agent_skills (id INTEGER PRIMARY KEY AUTOINCREMENT,
            agent_phone TEXT NOT NULL, skill_tag TEXT NOT NULL, proficiency INTEGER DEFAULT 5,
            FOREIGN KEY (agent_phone) REFERENCES agent_status(phone) ON DELETE CASCADE);
        CREATE TABLE IF NOT EXISTS routing_config (key TEXT PRIMARY KEY, value TEXT NOT NULL,
            updated_at DATETIME DEFAULT CURRENT_TIMESTAMP);
        CREATE TABLE IF NOT EXISTS sessions (id INTEGER PRIMARY KEY AUTOINCREMENT,
            user_id TEXT NOT NULL, customer_name TEXT DEFAULT '', customer_phone TEXT DEFAULT '',
            platform TEXT DEFAULT '', assigned_agent_phone TEXT DEFAULT '',
            status TEXT DEFAULT 'pending', priority INTEGER DEFAULT 0, skill_required TEXT DEFAULT '',
            created_at DATETIME DEFAULT CURRENT_TIMESTAMP, updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
            closed_at DATETIME DEFAULT NULL);
        CREATE TABLE IF NOT EXISTS tenant_style_config (user_id TEXT PRIMARY KEY,
            theme TEXT DEFAULT 'light', primary_color TEXT DEFAULT '#1976D2',
            accent_color TEXT default '#FF4081', font_size TEXT DEFAULT 'medium',
            bubble_style TEXT DEFAULT 'rounded', avatar_enabled INTEGER DEFAULT 1,
            show_timestamp INTEGER DEFAULT 1, send_sound INTEGER DEFAULT 1,
            custom_css TEXT DEFAULT '', updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
            FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE);
        CREATE TABLE IF NOT EXISTS tenant_app_config (user_id TEXT PRIMARY KEY,
            app_name TEXT DEFAULT '客服小秘', welcome_message TEXT DEFAULT '您好，请问有什么可以帮您？',
            offline_message TEXT DEFAULT '当前无客服在线，请稍后再试。',
            auto_reply_enabled INTEGER DEFAULT 1, notification_enabled INTEGER DEFAULT 1,
            voice_enabled INTEGER DEFAULT 0, language TEXT DEFAULT 'zh-CN',
            session_timeout INTEGER DEFAULT 300, max_queue_size INTEGER DEFAULT 50,
            file_upload_enabled INTEGER DEFAULT 1, updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
            FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE);
        CREATE TABLE IF NOT EXISTS admin_sessions (token TEXT PRIMARY KEY, phone TEXT NOT NULL,
            created_at DATETIME DEFAULT CURRENT_TIMESTAMP, expires_at DATETIME NOT NULL);
        CREATE INDEX IF NOT EXISTS idx_rules_user ON keyword_rules(user_id);
        CREATE INDEX IF NOT EXISTS idx_rules_keyword ON keyword_rules(keyword);
        CREATE INDEX IF NOT EXISTS idx_history_user_created ON reply_history(user_id, created_at DESC);
        CREATE INDEX IF NOT EXISTS idx_feedback_user ON feedback(user_id);
        CREATE INDEX IF NOT EXISTS idx_feedback_history ON feedback(reply_history_id);
        CREATE INDEX IF NOT EXISTS idx_metrics_user_date ON optimization_metrics(user_id, date);
        CREATE INDEX IF NOT EXISTS idx_models_user_enabled ON model_configs(user_id, enabled);
        CREATE INDEX IF NOT EXISTS idx_devices_heartbeat ON devices(last_heartbeat DESC);
        CREATE INDEX IF NOT EXISTS idx_history_source ON reply_history(source);
        CREATE INDEX IF NOT EXISTS idx_feedback_action ON feedback(action);
        CREATE INDEX IF NOT EXISTS idx_blacklist_user ON blacklist(user_id);
        CREATE INDEX IF NOT EXISTS idx_user_devices_user ON user_devices(user_id);
        CREATE INDEX IF NOT EXISTS idx_agent_skills_phone ON agent_skills(agent_phone);
        CREATE INDEX IF NOT EXISTS idx_sessions_user ON sessions(user_id);
        CREATE INDEX IF NOT EXISTS idx_sessions_status ON sessions(status);
        CREATE INDEX IF NOT EXISTS idx_admin_sessions_phone ON admin_sessions(phone);
        CREATE INDEX IF NOT EXISTS idx_admin_sessions_expires ON admin_sessions(expires_at);
    """)


# =============================================================================
# Public API - Unified get_connection() and init_db()
# =============================================================================
def get_connection():
    """Get database connection (PostgreSQL if DATABASE_URL set, else SQLite)."""
    if DATABASE_URL and PSYCOPG2_AVAILABLE:
        return _pg_get_connection()
    return _sqlite_get_connection()


def init_db():
    """Initialize database based on available backend."""
    if DATABASE_URL and PSYCOPG2_AVAILABLE:
        _log("Initializing PostgreSQL database...")
        try:
            db = _pg_get_connection()
            _pg_create_tables(db)
            PostgresqlConnectionPool.return_connection(db)
            _log("PostgreSQL tables created successfully")
        except Exception as e:
            _log_error(f"PostgreSQL init failed: {e}, falling back to SQLite")
            _init_sqlite()
    else:
        _init_sqlite()


def _init_sqlite():
    """Initialize SQLite database."""
    _log("Initializing SQLite database...")
    db = _sqlite_get_connection()
    try:
        _sqlite_init_tables(db)
        db.commit()
    finally:
        db.close()
    _log("SQLite tables created successfully")