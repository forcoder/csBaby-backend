"""
Persistence layer with a sqlite3-compatible facade.

Supports two backends, selected at runtime by the DATABASE_URL scheme:
* sqlite:///path/to/file.db    -> uses Python's stdlib sqlite3
* postgresql://... or postgres://...  -> uses psycopg2 with a connection pool

The application code (app.py and the *_repo_sqlite.py modules) was written
against the sqlite3 API: positional `?` placeholders, `row["col"]` access via
a dict-like Row, `cursor.fetchone() / .fetchall()`, `cursor.lastrowid`, and
explicit `commit()` / `close()`. The facades in this file preserve that
surface so callers do not need to change.

Translation rules performed by the facade (PostgreSQL only):
* `?` placeholders in SQL are rewritten to `%s` (psycopg2's positional style).
* `INSERT INTO ...` statements (when the target table has an `id` column) are
  automatically extended with `RETURNING id` so `cursor.lastrowid` keeps
  working. The facade's _RETURNING_TABLES set controls which tables get this
  treatment.
* psycopg2's RealDictCursor provides dict rows; sqlite3.Row is already
  dict-like. Both support `row["col"]` access.
"""

import os
import re
import sqlite3
import threading
from typing import Any, Iterable, Mapping, Optional


_BACKEND: Optional[str] = None
_lock = threading.Lock()

# psycopg2-specific state
_pg_pool = None
_pg_factory = None

# sqlite3-specific state
_sqlite_path: Optional[str] = None


def _detect_backend() -> str:
    url = os.environ.get("DATABASE_URL", "").strip()
    if not url:
        # Default to local sqlite file for backwards compatibility.
        return "sqlite"
    if url.startswith("postgres://") or url.startswith("postgresql://"):
        return "postgres"
    if url.startswith("sqlite://") or url.startswith("sqlite:///"):
        return "sqlite"
    raise RuntimeError(f"Unsupported DATABASE_URL scheme: {url!r}")


def _backend() -> str:
    global _BACKEND
    if _BACKEND is None:
        with _lock:
            if _BACKEND is None:
                _BACKEND = _detect_backend()
    return _BACKEND


def _resolve_sqlite_path() -> str:
    global _sqlite_path
    if _sqlite_path is not None:
        return _sqlite_path
    url = os.environ.get("DATABASE_URL", "")
    if url.startswith("sqlite:///"):
        path = url[len("sqlite:///"):]
    elif url.startswith("sqlite://"):
        path = url[len("sqlite://"):]
    else:
        # Fall back to legacy DATABASE_PATH for any old call sites.
        path = os.environ.get("DATABASE_PATH", "csBaby.db")
    _sqlite_path = path
    return path


def _get_pg_pool():
    global _pg_pool, _pg_factory
    if _pg_pool is None:
        with _lock:
            if _pg_pool is None:
                import psycopg2
                from psycopg2 import pool as pg_pool
                from psycopg2 import extras as pg_extras

                dsn = os.environ.get("DATABASE_URL", "")
                if dsn.startswith("postgres://"):
                    dsn = dsn.replace("postgres://", "postgresql://", 1)
                _pg_pool = pg_pool.SimpleConnectionPool(minconn=1, maxconn=10, dsn=dsn)
                _pg_factory = pg_extras.RealDictCursor
    return _pg_pool


# ---------------------------------------------------------------------------
# sqlite3 backend — thin pass-through (no translation needed)
# ---------------------------------------------------------------------------

def _sqlite_get_connection() -> sqlite3.Connection:
    path = _resolve_sqlite_path()
    conn = sqlite3.connect(path, timeout=10)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA journal_mode=WAL")
    conn.execute("PRAGMA foreign_keys=ON")
    conn.execute("PRAGMA busy_timeout=3000")
    return conn


# ---------------------------------------------------------------------------
# psycopg2 backend — facade over a pooled connection
# ---------------------------------------------------------------------------

_INSERT_RE = re.compile(r"^\s*INSERT\s+INTO\s+", re.IGNORECASE)
_RETURNING_RE = re.compile(r"\bRETURNING\b", re.IGNORECASE)
_INSERT_OR_REPLACE_RE = re.compile(r"^\s*INSERT\s+OR\s+REPLACE\s+INTO\s+", re.IGNORECASE)
_COLS_RE = re.compile(r"\(([^)]+)\)\s*VALUES\s*\(([^)]+)\)", re.IGNORECASE | re.DOTALL)
# Tables whose PRIMARY KEY is `id` and benefit from auto-RETURNING. ota_versions
# uses version_code as PK so it is excluded; init_db seeds it without needing
# the inserted id.
_RETURNING_TABLES = frozenset({
    "devices", "users", "keyword_rules", "model_configs", "reply_history",
    "feedback", "optimization_metrics", "agent_skills", "sessions",
    "tenant_style_config", "tenant_app_config",
})
# PK column per table. Used to translate INSERT OR REPLACE into PG's
# ON CONFLICT (pk) DO UPDATE form.
_PK_COLUMNS = {
    "ai_model_configs": "id",
    "app_configs": "id",
    "scenarios": "id",
    "message_blacklist": "id",
    "user_style_profiles": "user_id",
    "routing_config": "key",
    "agent_status": "phone",
    "tenant_style_config": "device_id",
    "tenant_app_config": "device_id",
}


def _translate_insert_or_replace(sql: str) -> str:
    """
    Rewrite `INSERT OR REPLACE INTO table (cols) VALUES (...)` into a
    PostgreSQL-compatible form: `INSERT INTO table (cols) VALUES (...)
    ON CONFLICT (pk) DO UPDATE SET col=EXCLUDED.col, ...`.
    Falls back to a plain INSERT (no upsert) if the table is unknown.
    """
    m = _INSERT_OR_REPLACE_RE.match(sql)
    if not m:
        return sql
    table_m = re.match(r"^\s*INSERT\s+OR\s+REPLACE\s+INTO\s+([A-Za-z_][\w\.\"]*)", sql, re.IGNORECASE)
    if not table_m:
        return sql
    table = table_m.group(1).strip('"').split(".")[-1].lower()
    pk = _PK_COLUMNS.get(table)
    if not pk:
        return sql  # Unknown table; let PG emit its own error.
    cols_m = _COLS_RE.search(sql)
    if not cols_m:
        return sql
    cols = [c.strip().strip('"').lower() for c in cols_m.group(1).split(",")]
    update_cols = [c for c in cols if c != pk]
    if not update_cols:
        # PK only; equivalent to plain INSERT, but PG still needs a no-op.
        return re.sub(r"^\s*INSERT\s+OR\s+REPLACE\s+INTO", "INSERT INTO", sql, flags=re.IGNORECASE).rstrip().rstrip(";")
    updates = ", ".join(f"{c}=EXCLUDED.{c}" for c in update_cols)
    head = re.sub(r"^\s*INSERT\s+OR\s+REPLACE\s+INTO", "INSERT INTO", sql, count=1, flags=re.IGNORECASE)
    head = head.rstrip().rstrip(";")
    return f"{head} ON CONFLICT ({pk}) DO UPDATE SET {updates}"


def _translate_sql(sql: str) -> str:
    s = _translate_insert_or_replace(sql)
    s = s.replace("?", "%s")
    if not _INSERT_RE.match(s) or _RETURNING_RE.search(s):
        return s
    m = re.match(r"^\s*INSERT\s+INTO\s+([A-Za-z_][\w\.\"]*)", s)
    if not m:
        return s
    table = m.group(1).strip('"').split(".")[-1].lower()
    if table in _RETURNING_TABLES:
        s = s.rstrip().rstrip(";") + " RETURNING id"
    return s


class _PgCursorFacade:
    def __init__(self, cursor, rowcount: int):
        self._cursor = cursor
        self._all_rows: Optional[list[dict]] = None
        self._index = 0
        self.lastrowid: Optional[int] = None
        self.rowcount = rowcount
        self.description = cursor.description

    @property
    def _rows(self) -> list[dict]:
        if self._all_rows is None:
            self._all_rows = self._cursor.fetchall() or []
        return self._all_rows

    def fetchone(self):
        rows = self._rows
        if self._index >= len(rows):
            return None
        row = rows[self._index]
        self._index += 1
        return row

    def fetchall(self):
        rows = self._rows
        remaining = rows[self._index:]
        self._index = len(rows)
        return remaining

    def close(self):
        try:
            self._cursor.close()
        except Exception:
            pass


class _PgConnectionFacade:
    def __init__(self):
        self._pg_conn = _get_pg_pool().getconn()
        self._closed = False

    def execute(self, sql: str, params: Optional[Iterable[Any] | Mapping[str, Any]] = None):
        translated = _translate_sql(sql)
        added_returning = (
            _INSERT_RE.match(translated)
            and not _RETURNING_RE.search(sql)  # original, not translated
        )
        cur = self._pg_conn.cursor(cursor_factory=_pg_factory)
        try:
            if params is None:
                cur.execute(translated)
            else:
                cur.execute(translated, params)
        except Exception:
            cur.close()
            raise
        rowcount = cur.rowcount
        facade = _PgCursorFacade(cur, rowcount)
        if added_returning:
            row = cur.fetchone()
            if row is not None and "id" in row:
                facade.lastrowid = row["id"]
        return facade

    def executemany(self, sql: str, seq_of_params):
        translated = _translate_sql(sql)
        cur = self._pg_conn.cursor(cursor_factory=_pg_factory)
        try:
            cur.executemany(translated, seq_of_params)
        finally:
            cur.close()

    def commit(self):
        if not self._closed:
            self._pg_conn.commit()

    def rollback(self):
        if not self._closed:
            self._pg_conn.rollback()

    def close(self):
        if not self._closed:
            try:
                _get_pg_pool().putconn(self._pg_conn)
            finally:
                self._closed = True


# ---------------------------------------------------------------------------
# Public entry points
# ---------------------------------------------------------------------------

def get_connection():
    if _backend() == "postgres":
        return _PgConnectionFacade()
    return _sqlite_get_connection()


# ---------------------------------------------------------------------------
# Schema bootstrap
# ---------------------------------------------------------------------------

# Schema definitions are written for PostgreSQL. When running on sqlite, we
# rewrite the few statements that contain PostgreSQL-only syntax to their
# sqlite equivalents. This is only used by tests; production uses init_db()
# verbatim against PG.

_SQLITE_DDL_OVERRIDES: dict[str, str] = {
    "BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY":
        "INTEGER PRIMARY KEY AUTOINCREMENT",
    "BIGINT NOT NULL DEFAULT 0": "INTEGER NOT NULL DEFAULT 0",
    "BIGINT NOT NULL": "INTEGER NOT NULL",
    "DOUBLE PRECISION": "REAL",
    "BOOLEAN DEFAULT TRUE": "INTEGER DEFAULT 1",
    "BOOLEAN DEFAULT FALSE": "INTEGER DEFAULT 0",
    "BOOLEAN NOT NULL DEFAULT FALSE": "INTEGER NOT NULL DEFAULT 0",
    "BOOLEAN NOT NULL DEFAULT TRUE": "INTEGER NOT NULL DEFAULT 1",
    "BOOLEAN": "INTEGER",
    "TIMESTAMP": "DATETIME",
}


def _adapt_ddl_for_sqlite(stmt: str) -> str:
    s = stmt
    for pg, lite in _SQLITE_DDL_OVERRIDES.items():
        s = s.replace(pg, lite)
    # PG partial unique indexes have no sqlite equivalent; drop the WHERE clause.
    s = re.sub(
        r"CREATE UNIQUE INDEX IF NOT EXISTS (\w+) ON \S+\s+WHERE\s+[^;]+;",
        r"CREATE UNIQUE INDEX IF NOT EXISTS \1 ON \S+;",
        s,
        flags=re.IGNORECASE,
    )
    return s


_DDL_STATEMENTS: tuple[str, ...] = (
    """
    CREATE TABLE IF NOT EXISTS devices (
        id TEXT PRIMARY KEY,
        token TEXT NOT NULL,
        name TEXT,
        platform TEXT DEFAULT 'android',
        app_version TEXT,
        last_heartbeat DATETIME,
        is_active INTEGER DEFAULT 1,
        created_at DATETIME DEFAULT CURRENT_TIMESTAMP
    )
    """,
    """
    CREATE TABLE IF NOT EXISTS users (
        id TEXT PRIMARY KEY,
        email TEXT UNIQUE NOT NULL,
        password_hash TEXT NOT NULL,
        salt TEXT NOT NULL,
        display_name TEXT NOT NULL DEFAULT '',
        tenant_id TEXT NOT NULL,
        created_at INTEGER NOT NULL
    )
    """,
    "CREATE INDEX IF NOT EXISTS idx_users_email ON users(email)",
    """
    CREATE TABLE IF NOT EXISTS keyword_rules (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        device_id TEXT NOT NULL,
        user_id TEXT,
        keyword TEXT NOT NULL,
        match_type TEXT DEFAULT 'CONTAINS',
        reply_template TEXT NOT NULL,
        category TEXT DEFAULT '',
        target_type TEXT DEFAULT 'ALL',
        target_names TEXT DEFAULT '[]',
        priority INTEGER DEFAULT 0,
        enabled INTEGER DEFAULT 1,
        created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
        updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
    )
    """,
    """
    CREATE TABLE IF NOT EXISTS model_configs (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        device_id TEXT NOT NULL,
        user_id TEXT,
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
        updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
    )
    """,
    """
    CREATE TABLE IF NOT EXISTS reply_history (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        device_id TEXT NOT NULL,
        user_id TEXT,
        original_message TEXT,
        reply_content TEXT,
        source TEXT DEFAULT 'ai',
        model_used TEXT,
        confidence REAL,
        response_time_ms INTEGER,
        platform TEXT,
        customer_name TEXT,
        house_name TEXT,
        created_at DATETIME DEFAULT CURRENT_TIMESTAMP
    )
    """,
    """
    CREATE TABLE IF NOT EXISTS feedback (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        device_id TEXT NOT NULL,
        user_id TEXT,
        reply_history_id INTEGER,
        action TEXT NOT NULL,
        modified_text TEXT,
        rating INTEGER,
        comment TEXT,
        created_at DATETIME DEFAULT CURRENT_TIMESTAMP
    )
    """,
    """
    CREATE TABLE IF NOT EXISTS optimization_metrics (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        device_id TEXT NOT NULL,
        user_id TEXT,
        date TEXT NOT NULL,
        total_generated INTEGER DEFAULT 0,
        total_accepted INTEGER DEFAULT 0,
        total_modified INTEGER DEFAULT 0,
        total_rejected INTEGER DEFAULT 0,
        avg_confidence REAL DEFAULT 0,
        avg_response_time_ms INTEGER DEFAULT 0
    )
    """,
    "CREATE INDEX IF NOT EXISTS idx_rules_device ON keyword_rules(device_id)",
    "CREATE INDEX IF NOT EXISTS idx_rules_user ON keyword_rules(user_id)",
    "CREATE INDEX IF NOT EXISTS idx_rules_keyword ON keyword_rules(keyword)",
    "CREATE INDEX IF NOT EXISTS idx_history_device_created ON reply_history(device_id, created_at DESC)",
    "CREATE INDEX IF NOT EXISTS idx_history_user_created ON reply_history(user_id, created_at DESC)",
    "CREATE INDEX IF NOT EXISTS idx_feedback_device ON feedback(device_id)",
    "CREATE INDEX IF NOT EXISTS idx_feedback_user ON feedback(user_id)",
    "CREATE INDEX IF NOT EXISTS idx_feedback_history ON feedback(reply_history_id)",
    "CREATE INDEX IF NOT EXISTS idx_metrics_device_date ON optimization_metrics(device_id, date)",
    # Both PG and sqlite get the plain unique index; PG allows multiple NULLs.
    "CREATE UNIQUE INDEX IF NOT EXISTS uq_metrics_user_date ON optimization_metrics(user_id, date)",
    "CREATE UNIQUE INDEX IF NOT EXISTS uq_metrics_device_date ON optimization_metrics(device_id, date)",
    "CREATE INDEX IF NOT EXISTS idx_models_device_enabled ON model_configs(device_id, enabled)",
    "CREATE INDEX IF NOT EXISTS idx_models_user_enabled ON model_configs(user_id, enabled)",
    "CREATE INDEX IF NOT EXISTS idx_devices_heartbeat ON devices(last_heartbeat DESC)",
    "CREATE INDEX IF NOT EXISTS idx_history_source ON reply_history(source)",
    "CREATE INDEX IF NOT EXISTS idx_feedback_action ON feedback(action)",
    """
    CREATE TABLE IF NOT EXISTS agent_status (
        phone TEXT PRIMARY KEY,
        agent_name TEXT DEFAULT '',
        status TEXT DEFAULT 'online',
        current_load INTEGER DEFAULT 0,
        max_concurrent INTEGER DEFAULT 5,
        tenant_id TEXT DEFAULT '',
        updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
    )
    """,
    """
    CREATE TABLE IF NOT EXISTS agent_skills (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        agent_phone TEXT NOT NULL,
        skill_tag TEXT NOT NULL,
        proficiency INTEGER DEFAULT 5
    )
    """,
    """
    CREATE TABLE IF NOT EXISTS routing_config (
        key TEXT PRIMARY KEY,
        value TEXT NOT NULL,
        updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
    )
    """,
    """
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
        closed_at DATETIME
    )
    """,
    """
    CREATE TABLE IF NOT EXISTS tenant_style_config (
        device_id TEXT PRIMARY KEY,
        theme TEXT DEFAULT 'light',
        primary_color TEXT DEFAULT '#1976D2',
        accent_color TEXT DEFAULT '#FF4081',
        font_size TEXT DEFAULT 'medium',
        bubble_style TEXT DEFAULT 'rounded',
        avatar_enabled INTEGER DEFAULT 1,
        show_timestamp INTEGER DEFAULT 1,
        send_sound INTEGER DEFAULT 1,
        custom_css TEXT DEFAULT '',
        updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
    )
    """,
    """
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
        updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
    )
    """,
    "CREATE INDEX IF NOT EXISTS idx_agent_skills_phone ON agent_skills(agent_phone)",
    "CREATE INDEX IF NOT EXISTS idx_sessions_tenant ON sessions(tenant_id)",
    "CREATE INDEX IF NOT EXISTS idx_sessions_status ON sessions(status)",
    """
    CREATE TABLE IF NOT EXISTS admin_sessions (
        token TEXT PRIMARY KEY,
        phone TEXT NOT NULL,
        created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
        expires_at DATETIME NOT NULL
    )
    """,
    "CREATE INDEX IF NOT EXISTS idx_admin_sessions_phone ON admin_sessions(phone)",
    "CREATE INDEX IF NOT EXISTS idx_admin_sessions_expires ON admin_sessions(expires_at)",
    """
    CREATE TABLE IF NOT EXISTS ota_versions (
        version_code INTEGER PRIMARY KEY,
        version_name TEXT NOT NULL,
        download_url TEXT NOT NULL,
        file_size INTEGER NOT NULL DEFAULT 0,
        md5 TEXT NOT NULL DEFAULT '',
        release_notes TEXT NOT NULL DEFAULT '',
        channel TEXT NOT NULL DEFAULT 'default',
        is_force_update INTEGER NOT NULL DEFAULT 0,
        min_required_version INTEGER NOT NULL DEFAULT 1,
        is_published INTEGER NOT NULL DEFAULT 1,
        release_date INTEGER,
        created_at INTEGER NOT NULL
    )
    """,
    # ---- Sync-related tables (app.py uses these in _upsert_* helpers) ----
    """
    CREATE TABLE IF NOT EXISTS ai_model_configs (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        tenant_id TEXT NOT NULL,
        model_type TEXT DEFAULT '',
        model_name TEXT DEFAULT '',
        api_key TEXT DEFAULT '',
        api_endpoint TEXT DEFAULT '',
        temperature REAL DEFAULT 0.7,
        max_tokens INTEGER DEFAULT 1000,
        is_default INTEGER DEFAULT 0,
        is_enabled INTEGER DEFAULT 1,
        monthly_cost REAL DEFAULT 0,
        last_used INTEGER,
        created_at INTEGER,
        sync_version INTEGER DEFAULT 0,
        deleted INTEGER DEFAULT 0
    )
    """,
    "CREATE INDEX IF NOT EXISTS idx_aimc_tenant ON ai_model_configs(tenant_id)",
    """
    CREATE TABLE IF NOT EXISTS user_style_profiles (
        user_id TEXT PRIMARY KEY,
        tenant_id TEXT NOT NULL,
        formality_level REAL DEFAULT 0.5,
        enthusiasm_level REAL DEFAULT 0.5,
        professionalism_level REAL DEFAULT 0.5,
        word_count_preference INTEGER DEFAULT 50,
        common_phrases TEXT DEFAULT '',
        avoid_phrases TEXT DEFAULT '',
        learning_samples INTEGER DEFAULT 0,
        accuracy_score REAL DEFAULT 0,
        last_trained INTEGER,
        created_at INTEGER,
        sync_version INTEGER DEFAULT 0,
        deleted INTEGER DEFAULT 0
    )
    """,
    "CREATE INDEX IF NOT EXISTS idx_usp_tenant ON user_style_profiles(tenant_id)",
    """
    CREATE TABLE IF NOT EXISTS app_configs (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        tenant_id TEXT NOT NULL,
        package_name TEXT NOT NULL,
        app_name TEXT DEFAULT '',
        icon_uri TEXT,
        is_monitored INTEGER DEFAULT 0,
        created_at INTEGER,
        last_used INTEGER,
        sync_version INTEGER DEFAULT 0,
        deleted INTEGER DEFAULT 0
    )
    """,
    "CREATE INDEX IF NOT EXISTS idx_appc_tenant ON app_configs(tenant_id)",
    """
    CREATE TABLE IF NOT EXISTS scenarios (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        tenant_id TEXT NOT NULL,
        name TEXT NOT NULL,
        type TEXT DEFAULT 'ALL_PROPERTIES',
        target_id TEXT,
        description TEXT,
        created_at INTEGER,
        sync_version INTEGER DEFAULT 0,
        deleted INTEGER DEFAULT 0
    )
    """,
    "CREATE INDEX IF NOT EXISTS idx_scenarios_tenant ON scenarios(tenant_id)",
    """
    CREATE TABLE IF NOT EXISTS message_blacklist (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        tenant_id TEXT NOT NULL,
        type TEXT DEFAULT 'KEYWORD',
        value TEXT NOT NULL,
        description TEXT DEFAULT '',
        package_name TEXT,
        is_enabled INTEGER DEFAULT 1,
        created_at INTEGER,
        sync_version INTEGER DEFAULT 0,
        deleted INTEGER DEFAULT 0
    )
    """,
    "CREATE INDEX IF NOT EXISTS idx_mbl_tenant ON message_blacklist(tenant_id)",
    # ---- Admin tables (also defined lazily in app.py) ----
    """
    CREATE TABLE IF NOT EXISTS admin_accounts (
        phone TEXT PRIMARY KEY,
        password_hash TEXT NOT NULL,
        is_active INTEGER DEFAULT 1,
        created_at DATETIME DEFAULT CURRENT_TIMESTAMP
    )
    """,
    """
    CREATE TABLE IF NOT EXISTS audit_log (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        admin_phone TEXT NOT NULL,
        action TEXT NOT NULL,
        target_type TEXT DEFAULT '',
        target_id TEXT DEFAULT '',
        detail TEXT DEFAULT '',
        created_at DATETIME DEFAULT CURRENT_TIMESTAMP
    )
    """,
    "CREATE INDEX IF NOT EXISTS idx_audit_created ON audit_log(created_at DESC)",
)


def init_db() -> None:
    is_sqlite = _backend() == "sqlite"
    conn = get_connection()
    try:
        for stmt in _DDL_STATEMENTS:
            s = _adapt_ddl_for_sqlite(stmt) if is_sqlite else stmt
            conn.execute(s)
        if is_sqlite:
            conn.execute(
                "INSERT OR IGNORE INTO ota_versions"
                " (version_code, version_name, download_url, file_size, md5,"
                " release_notes, channel, is_force_update, min_required_version,"
                " is_published, release_date, created_at)"
                " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                (
                    1, "1.0.0", "https://example.com/csbaby-v1.0.0.apk", 0, "",
                    "初始版本", "default", False, 1, True, 1704067200000, 1704067200000,
                ),
            )
        else:
            conn.execute(
                "INSERT INTO ota_versions"
                " (version_code, version_name, download_url, file_size, md5,"
                " release_notes, channel, is_force_update, min_required_version,"
                " is_published, release_date, created_at)"
                " VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)"
                " ON CONFLICT (version_code) DO NOTHING",
                (
                    1, "1.0.0", "https://example.com/csbaby-v1.0.0.apk", 0, "",
                    "初始版本", "default", False, 1, True, 1704067200000, 1704067200000,
                ),
            )
        conn.commit()
    except Exception:
        conn.rollback()
        raise
    finally:
        conn.close()
