# 多租户用户体系 + Web 管理后台实施计划

> **For agentic workers:** REQUIRED SUBSKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 csBaby 后端添加用户账号体系（手机号+密码）、Admin API，并构建独立部署的 Web 管理后台。

**Architecture:** 后端改造（database.py + auth.py + auth_api.py + admin_api.py + admin_config.py），管理后台独立项目（admin/ 目录，web.py + Jinja2），移动端新增 RegisterScreen。

**Tech Stack:** Python web.py, Jinja2, bcrypt, SQLite (后端); Kotlin + Jetpack Compose (移动端)

---

## 文件结构

### 后端变更（backend/）

| 文件 | 操作 | 说明 |
|------|------|------|
| `database.py` | 修改 | 新增 users 表、devices.user_id 字段、数据迁移逻辑 |
| `auth.py` | 修改 | 新增 hash_password/verify_password，token payload 加 tenant_id + is_admin |
| `api/auth_api.py` | 修改 | 注册改为手机号+密码，新增 login/change_password |
| `api/admin_api.py` | 新建 | Admin API（login, tenants 列表/详情/状态, stats） |
| `admin_config.py` | 新建 | 管理员凭据配置（gitignore） |
| `admin_config.example.py` | 新建 | 示例配置文件 |
| `.gitignore` | 新建 | 忽略 admin_config.py |
| `app.py` | 修改 | 注册 admin_api 路由 |
| `requirements.txt` | 修改 | 加 bcrypt |
| `config.py` | 修改 | 加 ADMIN_PHONE/ADMIN_PASSWORD 环境变量读取 |

### 管理后台新建（admin/）

| 文件 | 操作 | 说明 |
|------|------|------|
| `admin/app.py` | 新建 | 入口 + URL 路由 |
| `admin/config.py` | 新建 | API_BASE_URL, SESSION_SECRET |
| `admin/requirements.txt` | 新建 | web.py, jinja2, requests |
| `admin/render.yaml` | 新建 | Render 部署配置 |
| `admin/templates/layout.html` | 新建 | 基础布局 |
| `admin/templates/login.html` | 新建 | 登录页 |
| `admin/templates/dashboard.html` | 新建 | 总览 |
| `admin/templates/tenants.html` | 新建 | 租户列表 |
| `admin/templates/tenant_detail.html` | 新建 | 租户详情 |
| `admin/templates/profile.html` | 新建 | 修改密码 |
| `admin/static/style.css` | 新建 | 样式 |

### 移动端变更

| 文件 | 操作 | 说明 |
|------|------|------|
| `presentation/screens/auth/RegisterScreen.kt` | 新建 | 注册页面 |
| `presentation/screens/auth/RegisterViewModel.kt` | 新建 | 注册 ViewModel |
| `presentation/navigation/AppNavigation.kt` | 修改 | 加 register 路由 |

---

## Task 1: 后端 — 数据库变更 + admin_config

**Files:**
- Modify: `backend/database.py`
- Create: `backend/admin_config.py`
- Create: `backend/admin_config.example.py`
- Create: `backend/.gitignore`

- [ ] **Step 1: 修改 database.py — 新增 users 表**

在 `init_db()` 的 `db.executescript` 中，在现有建表语句之后加：

```sql
CREATE TABLE IF NOT EXISTS users (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    phone TEXT UNIQUE NOT NULL,
    password_hash TEXT NOT NULL,
    tenant_id TEXT UNIQUE NOT NULL,
    is_admin INTEGER DEFAULT 0,
    is_active INTEGER DEFAULT 1,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_users_phone ON users(phone);
CREATE INDEX IF NOT EXISTS idx_users_tenant ON users(tenant_id);
```

在 `devices` 表定义后加：
```sql
ALTER TABLE devices ADD COLUMN user_id INTEGER REFERENCES users(id);
```

然后在 `init_db()` 末尾（`db.commit()` 之前）加数据迁移逻辑：

```python
    # 数据迁移：为已有 devices 创建对应的 user 记录
    existing_migration = db.execute(
        "SELECT name FROM sqlite_master WHERE type='table' AND name='users'"
    ).fetchone()
    if existing_migration:
        devices = db.execute("SELECT id FROM devices WHERE user_id IS NULL").fetchall()
        for dev in devices:
            dev_id = dev[0]
            # 检查是否已有对应 user
            existing_user = db.execute(
                "SELECT id FROM users WHERE tenant_id = ?", (dev_id,)
            ).fetchone()
            if not existing_user:
                import uuid
                tenant_id = str(uuid.uuid4())
                # 随机密码哈希（用户需通过手机号找回）
                random_hash = hashlib.sha256(tenant_id.encode()).hexdigest()
                db.execute(
                    "INSERT INTO users (phone, password_hash, tenant_id, is_admin) VALUES (?, ?, ?, 0)",
                    (f"dev_{dev_id[:8]}", random_hash, tenant_id)
                )
                user_id = db.execute("SELECT id FROM users WHERE tenant_id = ?", (tenant_id,)).fetchone()[0]
                db.execute("UPDATE devices SET user_id = ? WHERE id = ?", (user_id, dev_id))
```

注意：需要在 `database.py` 顶部加 `import hashlib`（如果还没有）。检查一下 — 已有 `import os`，加 `import hashlib` 和 `import uuid`。

- [ ] **Step 2: 创建 admin_config.py**

```python
import os

ADMIN_PHONE = os.environ.get("ADMIN_PHONE", "15558181817")
ADMIN_PASSWORD = os.environ.get("ADMIN_PASSWORD", "Rd@202605")
```

- [ ] **Step 3: 创建 admin_config.example.py**

```python
import os

# 复制此文件为 admin_config.py 并填入实际值
ADMIN_PHONE = os.environ.get("ADMIN_PHONE", "your_phone_here")
ADMIN_PASSWORD = os.environ.get("ADMIN_PASSWORD", "your_password_here")
```

- [ ] **Step 4: 创建 .gitignore**

```
admin_config.py
__pycache__/
*.pyc
*.db
```

- [ ] **Step 5: 提交**

```bash
git add backend/database.py backend/admin_config.py backend/admin_config.example.py backend/.gitignore
git commit -m "feat: add users table + admin config"
```

---

## Task 2: 后端 — auth.py 改造

**Files:**
- Modify: `backend/auth.py`
- Modify: `backend/requirements.txt`

- [ ] **Step 1: 修改 requirements.txt**

```
web.py==0.62
gunicorn==21.2.0
bcrypt==4.1.2
```

- [ ] **Step 2: 改造 auth.py**

在文件顶部加 `import bcrypt`，然后在现有函数之后追加：

```python
def hash_password(password: str) -> str:
    """bcrypt 密码哈希"""
    return bcrypt.hashpw(password.encode("utf-8"), bcrypt.gensalt()).decode("utf-8")


def verify_password(password: str, hash_str: str) -> bool:
    """bcrypt 密码校验"""
    try:
        return bcrypt.checkpw(password.encode("utf-8"), hash_str.encode("utf-8"))
    except Exception:
        return False


def generate_user_token(device_id: str, tenant_id: str, is_admin: int = 0) -> str:
    """Generate token with tenant_id and is_admin."""
    header = base64.urlsafe_b64encode(json.dumps({"alg": "HS256", "typ": "JWT"}).encode()).rstrip(b"=").decode()
    payload_data = {
        "device_id": device_id,
        "tenant_id": tenant_id,
        "is_admin": is_admin,
        "exp": int(time.time()) + JWT_EXPIRE_DAYS * 86400,
        "iat": int(time.time())
    }
    payload = base64.urlsafe_b64encode(json.dumps(payload_data).encode()).rstrip(b"=").decode()
    signature = base64.urlsafe_b64encode(
        hmac.new(JWT_SECRET.encode(), f"{header}.{payload}".encode(), hashlib.sha256).digest()
    ).rstrip(b"=").decode()
    return f"{header}.{payload}.{signature}"


def extract_user_info():
    """从请求头提取 device_id, tenant_id, is_admin。返回 dict 或 None。"""
    auth_header = web.ctx.env.get("HTTP_AUTHORIZATION", "")
    if not auth_header.startswith("Bearer "):
        return None
    token = auth_header[7:]
    device_id = verify_token(token)
    if not device_id:
        return None
    # 解析 payload 获取 tenant_id 和 is_admin
    try:
        parts = token.split(".")
        payload = parts[1]
        payload += "=" * (4 - len(payload) % 4) if len(payload) % 4 else ""
        payload_data = json.loads(base64.urlsafe_b64decode(payload.encode()))
        return {
            "device_id": device_id,
            "tenant_id": payload_data.get("tenant_id", device_id),
            "is_admin": payload_data.get("is_admin", 0)
        }
    except Exception:
        return {"device_id": device_id, "tenant_id": device_id, "is_admin": 0}
```

- [ ] **Step 3: 提交**

```bash
git add backend/auth.py backend/requirements.txt
git commit -m "feat: add password hashing + user token with tenant_id/is_admin"
```

---

## Task 3: 后端 — 认证 API 改造

**Files:**
- Modify: `backend/api/auth_api.py`

- [ ] **Step 1: 改造 AuthRegister — 手机号+密码注册**

替换整个 `AuthRegister` 类：

```python
import json
import uuid
import web
from database import get_db
from auth import generate_token, extract_device_id, hash_password, verify_password, generate_user_info


class AuthRegister:
    """POST /api/auth/register — 手机号+密码注册"""

    def POST(self):
        data = json.loads(web.data().decode())
        phone = data.get("phone", "").strip()
        password = data.get("password", "")

        # 校验
        if not phone or len(phone) != 11 or not phone.startswith("1"):
            web.header("Content-Type", "application/json")
            raise web.badrequest(json.dumps({"error": "请输入正确的手机号"}))
        if not password or len(password) < 6:
            web.header("Content-Type", "application/json")
            raise web.badrequest(json.dumps({"error": "密码至少6位"}))

        db = get_db()
        # 检查手机号是否已存在
        existing = db.execute("SELECT id FROM users WHERE phone = ?", (phone,)).fetchone()
        if existing:
            db.close()
            web.header("Content-Type", "application/json")
            raise web.badrequest(json.dumps({"error": "该手机号已注册"}))

        # 创建用户
        tenant_id = str(uuid.uuid4())
        password_hash = hash_password(password)

        # 检查是否为管理员手机号
        from admin_config import ADMIN_PHONE, ADMIN_PASSWORD
        is_admin = 1 if (phone == ADMIN_PHONE and password == ADMIN_PASSWORD) else 0

        db.execute(
            "INSERT INTO users (phone, password_hash, tenant_id, is_admin) VALUES (?, ?, ?, ?)",
            (phone, password_hash, tenant_id, is_admin)
        )
        db.commit()

        # 生成 token（用 tenant_id 作为 device_id）
        token = generate_user_token(tenant_id, tenant_id, is_admin)

        db.close()

        web.header("Content-Type", "application/json")
        return json.dumps({
            "token": token,
            "tenant_id": tenant_id,
            "phone": phone,
            "expires_in": 30 * 86400
        })
```

- [ ] **Step 2: 新增 AuthLogin**

在 `auth_api.py` 末尾追加：

```python
class AuthLogin:
    """POST /api/auth/login — 手机号+密码登录"""

    def POST(self):
        data = json.loads(web.data().decode())
        phone = data.get("phone", "").strip()
        password = data.get("password", "")

        if not phone or not password:
            web.header("Content-Type", "application/json")
            raise web.badrequest(json.dumps({"error": "请输入手机号和密码"}))

        db = get_db()
        user = db.execute(
            "SELECT id, password_hash, tenant_id, is_admin, is_active FROM users WHERE phone = ?",
            (phone,)
        ).fetchone()
        db.close()

        if not user:
            web.header("Content-Type", "application/json")
            raise web.unauthorized(json.dumps({"error": "手机号或密码错误"}))

        user_id, password_hash, tenant_id, is_admin, is_active = user

        if not is_active:
            web.header("Content-Type", "application/json")
            raise web.unauthorized(json.dumps({"error": "账号已被禁用"}))

        if not verify_password(password, password_hash):
            web.header("Content-Type", "application/json")
            raise web.unauthorized(json.dumps({"error": "手机号或密码错误"}))

        token = generate_user_token(tenant_id, tenant_id, is_admin)

        web.header("Content-Type", "application/json")
        return json.dumps({
            "token": token,
            "tenant_id": tenant_id,
            "phone": phone,
            "expires_in": 30 * 86400
        })
```

- [ ] **Step 3: 新增 AuthChangePassword**

在 `auth_api.py` 末尾追加：

```python
class AuthChangePassword:
    """POST /api/auth/change_password — 修改密码（需登录）"""

    def POST(self):
        info = extract_user_info()
        if not info:
            web.header("Content-Type", "application/json")
            raise web.unauthorized(json.dumps({"error": "Unauthorized"}))

        data = json.loads(web.data().decode())
        old_password = data.get("old_password", "")
        new_password = data.get("new_password", "")

        if not old_password or not new_password:
            raise web.badrequest(json.dumps({"error": "请填写完整"}))
        if len(new_password) < 6:
            raise web.badrequest(json.dumps({"error": "新密码至少6位"}))

        db = get_db()
        user = db.execute(
            "SELECT password_hash FROM users WHERE tenant_id = ?", (info["tenant_id"],)
        ).fetchone()

        if not user or not verify_password(old_password, user[0]):
            db.close()
            raise web.badrequest(json.dumps({"error": "原密码错误"}))

        new_hash = hash_password(new_password)
        db.execute("UPDATE users SET password_hash = ? WHERE tenant_id = ?", (new_hash, info["tenant_id"]))
        db.commit()
        db.close()

        web.header("Content-Type", "application/json")
        return json.dumps({"status": "ok"})
```

- [ ] **Step 4: 改造 AuthHeartbeat**

改造为从 tenant_id 心跳：

```python
class AuthHeartbeat:
    """POST /api/auth/heartbeat"""

    def POST(self):
        info = extract_user_info()
        if not info:
            web.header("Content-Type", "application/json")
            raise web.unauthorized(json.dumps({"error": "Unauthorized"}))

        db = get_db()
        db.execute(
            "UPDATE devices SET last_heartbeat = CURRENT_TIMESTAMP WHERE id = ?",
            (info["device_id"],)
        )
        db.commit()
        db.close()

        web.header("Content-Type", "application/json")
        return json.dumps({"status": "ok"})
```

- [ ] **Step 4: 提交**

```bash
git add backend/api/auth_api.py
git commit -m "feat: add phone+password register/login API"
```

---

## Task 4: 后端 — Admin API

**Files:**
- Create: `backend/api/admin_api.py`
- Modify: `backend/app.py`

- [ ] **Step 1: 创建 admin_api.py**

```python
import json
import web
from database import get_db, dict_from_row
from auth import extract_user_info, verify_password, hash_password, generate_user_token


def require_admin():
    """验证管理员权限，返回 user_info 或抛出 401/403"""
    info = extract_user_info()
    if not info:
        raise web.unauthorized(json.dumps({"error": "Unauthorized"}))
    if not info.get("is_admin"):
        raise web.forbidden(json.dumps({"error": "需要管理员权限"}))
    return info


class AdminLogin:
    """POST /api/admin/login — 管理员登录（校验 is_admin=1）"""

    def POST(self):
        data = json.loads(web.data().decode())
        phone = data.get("phone", "").strip()
        password = data.get("password", "")

        db = get_db()
        user = db.execute(
            "SELECT id, password_hash, tenant_id, is_admin, is_active FROM users WHERE phone = ?",
            (phone,)
        ).fetchone()
        db.close()

        if not user:
            raise web.unauthorized(json.dumps({"error": "手机号或密码错误"}))

        user_id, password_hash, tenant_id, is_admin, is_active = user

        if not is_admin:
            raise web.forbidden(json.dumps({"error": "需要管理员权限"}))
        if not is_active:
            raise web.unauthorized(json.dumps({"error": "账号已被禁用"}))
        if not verify_password(password, password_hash):
            raise web.unauthorized(json.dumps({"error": "手机号或密码错误"}))

        token = generate_user_token(tenant_id, tenant_id, is_admin)

        web.header("Content-Type", "application/json")
        return json.dumps({
            "token": token,
            "tenant_id": tenant_id,
            "phone": phone,
            "is_admin": is_admin,
            "expires_in": 30 * 86400
        })


class AdminTenants:
    """GET /api/admin/tenants — 租户列表"""

    def GET(self):
        require_admin()

        # 解析分页参数
        params = web.input(page="1", page_size="20", search="")
        page = int(params.page)
        page_size = int(params.page_size)
        search = params.search.strip()
        offset = (page - 1) * page_size

        db = get_db()

        # 构建查询
        where_clause = ""
        query_params = []
        if search:
            where_clause = "WHERE phone LIKE ?"
            query_params.append(f"%{search}%")

        # 总数
        total_row = db.execute(
            f"SELECT COUNT(*) FROM users {where_clause}", query_params
        ).fetchone()
        total = total_row[0]

        # 列表
        rows = db.execute(
            f"""SELECT u.id, u.phone, u.tenant_id, u.is_admin, u.is_active, u.created_at,
                (SELECT COUNT(*) FROM keyword_rules WHERE device_id = u.tenant_id) as rule_count,
                (SELECT COUNT(*) FROM reply_history WHERE device_id = u.tenant_id) as history_count,
                (SELECT MAX(created_at) FROM reply_history WHERE device_id = u.tenant_id) as last_active
                FROM users u {where_clause}
                ORDER BY u.created_at DESC LIMIT ? OFFSET ?""",
            query_params + [page_size, offset]
        ).fetchall()
        db.close()

        web.header("Content-Type", "application/json")
        return json.dumps({
            "total": total,
            "page": page,
            "page_size": page_size,
            "items": [dict_from_row(r) for r in rows]
        })


class AdminTenantDetail:
    """GET/PUT /api/admin/tenants/{tenant_id}"""

    def GET(self, tenant_id):
        require_admin()

        db = get_db()
        user = db.execute(
            "SELECT id, phone, tenant_id, is_admin, is_active, created_at FROM users WHERE tenant_id = ?",
            (tenant_id,)
        ).fetchone()

        if not user:
            db.close()
            raise web.notfound(json.dumps({"error": "Tenant not found"}))

        rule_count = db.execute(
            "SELECT COUNT(*) FROM keyword_rules WHERE device_id = ?", (tenant_id,)
        ).fetchone()[0]
        model_count = db.execute(
            "SELECT COUNT(*) FROM model_configs WHERE device_id = ?", (tenant_id,)
        ).fetchone()[0]
        history_count = db.execute(
            "SELECT COUNT(*) FROM reply_history WHERE device_id = ?", (tenant_id,)
        ).fetchone()[0]
        feedback_count = db.execute(
            "SELECT COUNT(*) FROM feedback WHERE device_id = ?", (tenant_id,)
        ).fetchone()[0]
        last_active = db.execute(
            "SELECT MAX(created_at) FROM reply_history WHERE device_id = ?", (tenant_id,)
        ).fetchone()[0]
        db.close()

        result = dict_from_row(user)
        result["rule_count"] = rule_count
        result["model_count"] = model_count
        result["history_count"] = history_count
        result["feedback_count"] = feedback_count
        result["last_active"] = last_active

        web.header("Content-Type", "application/json")
        return json.dumps(result)

    def PUT(self, tenant_id):
        require_admin()

        data = json.loads(web.data().decode())
        is_active = data.get("is_active")

        db = get_db()
        user = db.execute("SELECT id FROM users WHERE tenant_id = ?", (tenant_id,)).fetchone()
        if not user:
            db.close()
            raise web.notfound(json.dumps({"error": "Tenant not found"}))

        if is_active is not None:
            db.execute("UPDATE users SET is_active = ? WHERE tenant_id = ?", (is_active, tenant_id))

        db.commit()
        db.close()

        web.header("Content-Type", "application/json")
        return json.dumps({"status": "ok", "tenant_id": tenant_id, "is_active": is_active})


class AdminStats:
    """GET /api/admin/stats — 全局统计"""

    def GET(self):
        require_admin()

        db = get_db()
        total_tenants = db.execute("SELECT COUNT(*) FROM users").fetchone()[0]
        active_tenants = db.execute("SELECT COUNT(*) FROM users WHERE is_active = 1").fetchone()[0]
        total_rules = db.execute("SELECT COUNT(*) FROM keyword_rules").fetchone()[0]
        total_history = db.execute("SELECT COUNT(*) FROM reply_history").fetchone()[0]

        # 今日
        import datetime
        today = datetime.date.today().strftime("%Y-%m-%d")
        today_row = db.execute(
            "SELECT COUNT(*) FROM reply_history WHERE DATE(created_at) = ?", (today,)
        ).fetchone()
        today_history = today_row[0] if today_row else 0
        db.close()

        web.header("Content-Type", "application/json")
        return json.dumps({
            "total_tenants": total_tenants,
            "active_tenants": active_tenants,
            "total_rules": total_rules,
            "total_history": total_history,
            "today_history": today_history
        })
```

- [ ] **Step 2: 修改 app.py — 注册 Admin 路由**

在 `app.py` 的 import 区加：
```python
from api.admin_api import AdminLogin, AdminTenants, AdminTenantDetail, AdminStats
```
同时确保 `AuthLogin` 和 `AuthChangePassword` 也在 import 区：
```python
from api.auth_api import AuthRegister, AuthHeartbeat, AuthLogin, AuthChangePassword
```

在 `urls` 元组中，在 `/health` 之前加：
```python
"/api/admin/login", "AdminLogin",
"/api/admin/tenants/(\w+)", "AdminTenantDetail",
"/api/admin/tenants", "AdminTenants",
"/api/admin/stats", "AdminStats",
```

同时把 `"/api/auth/register"` 路由改为指向改造后的 `AuthRegister`（代码不变，路由不变）。

在 `"/api/auth/register"` 后面加：
```python
"/api/auth/login", "AuthLogin",
```

- [ ] **Step 3: 提交**

```bash
git add backend/api/admin_api.py backend/app.py
git commit -m "feat: add admin API (login, tenants, stats)"
```

---

## Task 5: 管理后台 — 项目搭建

**Files:**
- Create: `admin/requirements.txt`
- Create: `admin/config.py`
- Create: `admin/app.py`

- [ ] **Step 1: 创建 admin/requirements.txt**

```
web.py==0.62
jinja2==3.1.3
requests==2.31.0
gunicorn==21.2.0
```

- [ ] **Step 2: 创建 admin/config.py**

```python
import os

API_BASE_URL = os.environ.get("API_BASE_URL", "https://csbaby-api2.onrender.com")
SESSION_SECRET = os.environ.get("SESSION_SECRET", "change-this-secret-key")
```

- [ ] **Step 3: 创建 admin/app.py**

```python
import web
import json
import requests as http_requests
from config import API_BASE_URL, SESSION_SECRET

urls = (
    "/admin/login", "Login",
    "/admin/logout", "Logout",
    "/admin/dashboard", "Dashboard",
    "/admin/tenants", "Tenants",
    "/admin/tenants/(\w+)", "TenantDetail",
    "/admin/profile", "Profile",
    "/admin", "AdminIndex",
)

app = web.application(urls, globals())
web.config.session_parameters["cookie_name"] = "csbaby_admin_session"

# Session setup
if web.config.get("_session") is None:
    store = web.session.DiskStore("sessions")
    session = web.session.Session(app, store, initializer={"admin_phone": None})
    web.config._session = session
else:
    session = web.config._session

# Template rendering
import jinja2
template_env = jinja2.Environment(loader=jinja2.FileSystemLoader("templates"))


def render(template_name, **context):
    """渲染模板 + 基础布局"""
    tmpl = template_env.get_template(template_name)
    content = tmpl.render(**context)
    layout = template_env.get_template("layout.html")
    return layout.render(content=content, **context)


def require_admin():
    """检查 session 中是否有管理员"""
    if not session.get("admin_phone"):
        raise web.seeother("/admin/login")


def api_get(path, token):
    """调用 Admin API (GET)"""
    resp = http_requests.get(
        f"{API_BASE_URL}{path}",
        headers={"Authorization": f"Bearer {token}"},
        timeout=10
    )
    return resp


def api_put(path, token, data):
    """调用 Admin API (PUT)"""
    resp = http_requests.put(
        f"{API_BASE_URL}{path}",
        headers={"Authorization": f"Bearer {token}", "Content-Type": "application/json"},
        data=json.dumps(data),
        timeout=10
    )
    return resp


class Login:
    def GET(self):
        if session.get("admin_phone"):
            raise web.seeother("/admin/dashboard")
        return render("login.html", error=None)

    def POST(self):
        data = web.input()
        phone = data.get("phone", "").strip()
        password = data.get("password", "")

        try:
            resp = http_requests.post(
                f"{API_BASE_URL}/api/auth/login",
                json={"phone": phone, "password": password},
                timeout=10
            )
            if resp.status_code == 200:
                result = resp.json()
                session["admin_phone"] = result.get("phone", phone)
                session["admin_token"] = result.get("token", "")
                raise web.seeother("/admin/dashboard")
            else:
                error = resp.json().get("error", "登录失败")
        except http_requests.exceptions.RequestException:
            error = "无法连接 API 服务"

        return render("login.html", error=error)


class Logout:
    def GET(self):
        session.kill()
        raise web.seeother("/admin/login")


class AdminIndex:
    def GET(self):
        raise web.seeother("/admin/dashboard")


class Dashboard:
    def GET(self):
        require_admin()
        token = session.get("admin_token", "")

        stats = {}
        try:
            resp = api_get("/api/admin/stats", token)
            if resp.status_code == 200:
                stats = resp.json()
        except Exception:
            pass

        return render("dashboard.html", stats=stats, admin_phone=session.get("admin_phone"))


class Tenants:
    def GET(self):
        require_admin()
        token = session.get("admin_token", "")

        params = web.input(page="1", search="")
        page = int(params.page)
        search = params.search.strip()

        tenants_data = {"items": [], "total": 0, "page": page, "page_size": 20}
        try:
            query = f"/api/admin/tenants?page={page}&page_size=20"
            if search:
                query += f"&search={search}"
            resp = api_get(query, token)
            if resp.status_code == 200:
                tenants_data = resp.json()
        except Exception:
            pass

        return render(
            "tenants.html",
            tenants=tenants_data.get("items", []),
            total=tenants_data.get("total", 0),
            page=page,
            page_size=20,
            search=search,
            admin_phone=session.get("admin_phone")
        )


class TenantDetail:
    def GET(self, tenant_id):
        require_admin()
        token = session.get("admin_token", "")

        tenant = {}
        try:
            resp = api_get(f"/api/admin/tenants/{tenant_id}", token)
            if resp.status_code == 200:
                tenant = resp.json()
        except Exception:
            pass

        return render("tenant_detail.html", tenant=tenant, admin_phone=session.get("admin_phone"))

    def POST(self, tenant_id):
        require_admin()
        token = session.get("admin_token", "")

        data = web.input()
        is_active = data.get("is_active")
        if is_active is not None:
            api_put(f"/api/admin/tenants/{tenant_id}", token, {"is_active": int(is_active)})

        raise web.seeother(f"/admin/tenants/{tenant_id}")


class Profile:
    def GET(self):
        require_admin()
        return render("profile.html", admin_phone=session.get("admin_phone"), error=None, success=None)

    def POST(self):
        require_admin()
        token = session.get("admin_token", "")

        data = web.input()
        old_password = data.get("old_password", "")
        new_password = data.get("new_password", "")
        confirm_password = data.get("confirm_password", "")

        if not old_password or not new_password:
            return render("profile.html", admin_phone=session.get("admin_phone"), error="请填写完整", success=None)

        if new_password != confirm_password:
            return render("profile.html", admin_phone=session.get("admin_phone"), error="两次新密码不一致", success=None)

        if len(new_password) < 6:
            return render("profile.html", admin_phone=session.get("admin_phone"), error="新密码至少6位", success=None)

        try:
            resp = http_requests.post(
                f"{API_BASE_URL}/api/auth/change_password",
                json={"old_password": old_password, "new_password": new_password},
                headers={"Authorization": f"Bearer {token}"},
                timeout=10
            )
            if resp.status_code == 200:
                return render("profile.html", admin_phone=session.get("admin_phone"), error=None, success="密码修改成功")
            else:
                error = resp.json().get("error", "修改失败")
                return render("profile.html", admin_phone=session.get("admin_phone"), error=error, success=None)
        except Exception as e:
            return render("profile.html", admin_phone=session.get("admin_phone"), error=f"网络错误: {e}", success=None)


# WSGI
application = app.wsgifunc()

if __name__ == "__main__":
    app.run()
```

- [ ] **Step 4: 创建 admin/render.yaml**

```yaml
services:
  - type: web
    name: csbaby-admin
    runtime: python
    plan: free
    buildCommand: pip install -r requirements.txt
    startCommand: gunicorn app:application --bind 0.0.0.0:$PORT --workers 1 --timeout 120
    envVars:
      - key: API_BASE_URL
        value: https://csbaby-api2.onrender.com
      - key: SESSION_SECRET
        generateValue: true
      - key: PYTHON_VERSION
        value: "3.11"
```

- [ ] **Step 5: 提交**

```bash
git add admin/app.py admin/config.py admin/requirements.txt admin/render.yaml
git commit -m "feat: admin panel backend (app.py + config + render.yaml)"
```

---

## Task 6: 管理后台 — 模板

**Files:**
- Create: `admin/templates/layout.html`
- Create: `admin/templates/login.html`
- Create: `admin/templates/dashboard.html`
- Create: `admin/templates/tenants.html`
- Create: `admin/templates/tenant_detail.html`
- Create: `admin/templates/profile.html`
- Create: `admin/static/style.css`

- [ ] **Step 1: 创建 layout.html**

```html
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>csBaby 管理后台</title>
    <link rel="stylesheet" href="/static/style.css">
</head>
<body>
    {% if admin_phone %}
    <nav class="navbar">
        <div class="nav-brand">csBaby 管理后台</div>
        <div class="nav-links">
            <a href="/admin/dashboard">总览</a>
            <a href="/admin/tenants">租户管理</a>
            <a href="/admin/profile">修改密码</a>
            <span class="nav-user">{{ admin_phone }}</span>
            <a href="/admin/logout" class="nav-logout">退出</a>
        </div>
    </nav>
    {% endif %}
    <div class="container">
        {% block content %}{{ content }}{% endblock %}
    </div>
</body>
</html>
```

- [ ] **Step 2: 创建 login.html**

```html
{% extends "layout.html" %}
{% block content %}
<div class="login-container">
    <h2>管理员登录</h2>
    {% if error %}
    <div class="alert alert-error">{{ error }}</div>
    {% endif %}
    <form method="POST" action="/admin/login">
        <div class="form-group">
            <label>手机号</label>
            <input type="tel" name="phone" placeholder="请输入手机号" required>
        </div>
        <div class="form-group">
            <label>密码</label>
            <input type="password" name="password" placeholder="请输入密码" required>
        </div>
        <button type="submit" class="btn btn-primary">登录</button>
    </form>
</div>
{% endblock %}
```

- [ ] **Step 3: 创建 dashboard.html**

```html
{% extends "layout.html" %}
{% block content %}
<h1>总览</h1>
<div class="stats-grid">
    <div class="stat-card">
        <div class="stat-value">{{ stats.get('total_tenants', 0) }}</div>
        <div class="stat-label">总租户数</div>
    </div>
    <div class="stat-card">
        <div class="stat-value">{{ stats.get('active_tenants', 0) }}</div>
        <div class="stat-label">活跃租户</div>
    </div>
    <div class="stat-card">
        <div class="stat-value">{{ stats.get('total_rules', 0) }}</div>
        <div class="stat-label">总规则数</div>
    </div>
    <div class="stat-card">
        <div class="stat-value">{{ stats.get('today_history', 0) }}</div>
        <div class="stat-label">今日回复</div>
    </div>
    <div class="stat-card">
        <div class="stat-value">{{ stats.get('total_history', 0) }}</div>
        <div class="stat-label">总回复数</div>
    </div>
</div>
{% endblock %}
```

- [ ] **Step 4: 创建 tenants.html**

```html
{% extends "layout.html" %}
{% block content %}
<h1>租户管理</h1>

<form method="GET" action="/admin/tenants" class="search-form">
    <input type="text" name="search" value="{{ search }}" placeholder="搜索手机号...">
    <button type="submit" class="btn">搜索</button>
</form>

<table class="table">
    <thead>
        <tr>
            <th>手机号</th>
            <th>规则数</th>
            <th>回复数</th>
            <th>注册时间</th>
            <th>状态</th>
            <th>操作</th>
        </tr>
    </thead>
    <tbody>
        {% for t in tenants %}
        <tr>
            <td>{{ t.phone }}</td>
            <td>{{ t.get('rule_count', 0) }}</td>
            <td>{{ t.get('history_count', 0) }}</td>
            <td>{{ t.get('created_at', '')[:10] }}</td>
            <td>
                {% if t.get('is_active', 1) %}
                <span class="badge badge-success">正常</span>
                {% else %}
                <span class="badge badge-danger">已禁用</span>
                {% endif %}
            </td>
            <td><a href="/admin/tenants/{{ t.tenant_id }}">详情</a></td>
        </tr>
        {% endfor %}
    </tbody>
</table>

{% if total > page_size %}
<div class="pagination">
    {% if page > 1 %}
    <a href="/admin/tenants?page={{ page - 1 }}&search={{ search }}">上一页</a>
    {% endif %}
    <span>第 {{ page }} 页 / 共 {{ (total + page_size - 1) // page_size }} 页</span>
    {% if page * page_size < total %}
    <a href="/admin/tenants?page={{ page + 1 }}&search={{ search }}">下一页</a>
    {% endif %}
</div>
{% endif %}
{% endblock %}
```

- [ ] **Step 5: 创建 tenant_detail.html**

```html
{% extends "layout.html" %}
{% block content %}
<h1>租户详情</h1>

<div class="detail-card">
    <div class="detail-row"><label>手机号</label><span>{{ tenant.phone }}</span></div>
    <div class="detail-row"><label>租户ID</label><span>{{ tenant.tenant_id }}</span></div>
    <div class="detail-row"><label>注册时间</label><span>{{ tenant.get('created_at', '') }}</span></div>
    <div class="detail-row">
        <label>状态</label>
        <span>
            {% if tenant.get('is_active', 1) %}
            <span class="badge badge-success">正常</span>
            {% else %}
            <span class="badge badge-danger">已禁用</span>
            {% endif %}
        </span>
    </div>
</div>

<h2>数据统计</h2>
<div class="stats-grid">
    <div class="stat-card"><div class="stat-value">{{ tenant.get('rule_count', 0) }}</div><div class="stat-label">规则数</div></div>
    <div class="stat-card"><div class="stat-value">{{ tenant.get('model_count', 0) }}</div><div class="stat-label">模型数</div></div>
    <div class="stat-card"><div class="stat-value">{{ tenant.get('history_count', 0) }}</div><div class="stat-label">回复数</div></div>
    <div class="stat-card"><div class="stat-value">{{ tenant.get('feedback_count', 0) }}</div><div class="stat-label">反馈数</div></div>
</div>

<h2>操作</h2>
<form method="POST" action="/admin/tenants/{{ tenant.tenant_id }}" class="action-form">
    <input type="hidden" name="is_active" value="{{ 0 if tenant.get('is_active', 1) else 1 }}">
    <button type="submit" class="btn {{ 'btn-danger' if tenant.get('is_active', 1) else 'btn-success' }}">
        {{ '禁用租户' if tenant.get('is_active', 1) else '启用租户' }}
    </button>
</form>

<a href="/admin/tenants" class="btn">返回列表</a>
{% endblock %}
```

- [ ] **Step 6: 创建 profile.html**

```html
{% extends "layout.html" %}
{% block content %}
<h1>修改密码</h1>

{% if error %}
<div class="alert alert-error">{{ error }}</div>
{% endif %}
{% if success %}
<div class="alert alert-success">{{ success }}</div>
{% endif %}

<form method="POST" action="/admin/profile" class="form-card">
    <div class="form-group">
        <label>当前密码</label>
        <input type="password" name="old_password" required>
    </div>
    <div class="form-group">
        <label>新密码</label>
        <input type="password" name="new_password" required minlength="6">
    </div>
    <div class="form-group">
        <label>确认新密码</label>
        <input type="password" name="confirm_password" required minlength="6">
    </div>
    <button type="submit" class="btn btn-primary">修改密码</button>
</form>
{% endblock %}
```

- [ ] **Step 7: 创建 style.css**

```css
* { box-sizing: border-box; margin: 0; padding: 0; }
body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif; background: #f5f5f5; color: #333; }

.navbar { background: #1a73e8; color: white; padding: 0 24px; height: 56px; display: flex; align-items: center; justify-content: space-between; }
.nav-brand { font-size: 18px; font-weight: 600; }
.nav-links a { color: rgba(255,255,255,0.85); text-decoration: none; margin-left: 20px; font-size: 14px; }
.nav-links a:hover { color: white; }
.nav-user { margin-left: 20px; font-size: 13px; opacity: 0.7; }
.nav-logout { margin-left: 16px; }

.container { max-width: 1200px; margin: 24px auto; padding: 0 24px; }

h1 { font-size: 24px; margin-bottom: 20px; }
h2 { font-size: 18px; margin: 24px 0 12px; }

.login-container { max-width: 400px; margin: 80px auto; background: white; padding: 32px; border-radius: 8px; box-shadow: 0 2px 8px rgba(0,0,0,0.1); }
.login-container h2 { text-align: center; margin-bottom: 24px; }

.form-group { margin-bottom: 16px; }
.form-group label { display: block; font-size: 14px; margin-bottom: 6px; color: #555; }
.form-group input { width: 100%; padding: 10px 12px; border: 1px solid #ddd; border-radius: 4px; font-size: 14px; }
.form-group input:focus { outline: none; border-color: #1a73e8; }

.btn { display: inline-block; padding: 10px 20px; border: none; border-radius: 4px; font-size: 14px; cursor: pointer; text-decoration: none; color: white; }
.btn-primary { background: #1a73e8; }
.btn-success { background: #34a853; }
.btn-danger { background: #ea4335; }
.btn:hover { opacity: 0.9; }

.stats-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 16px; margin-bottom: 24px; }
.stat-card { background: white; padding: 20px; border-radius: 8px; text-align: center; box-shadow: 0 1px 4px rgba(0,0,0,0.08); }
.stat-value { font-size: 32px; font-weight: 700; color: #1a73e8; }
.stat-label { font-size: 13px; color: #888; margin-top: 4px; }

.table { width: 100%; background: white; border-radius: 8px; overflow: hidden; box-shadow: 0 1px 4px rgba(0,0,0,0.08); border-collapse: collapse; }
.table th, .table td { padding: 12px 16px; text-align: left; border-bottom: 1px solid #eee; font-size: 14px; }
.table th { background: #f8f9fa; font-weight: 600; color: #555; }
.table a { color: #1a73e8; text-decoration: none; }

.badge { display: inline-block; padding: 2px 8px; border-radius: 12px; font-size: 12px; }
.badge-success { background: #e6f4ea; color: #34a853; }
.badge-danger { background: #fce8e6; color: #ea4335; }

.search-form { display: flex; gap: 8px; margin-bottom: 16px; }
.search-form input { padding: 8px 12px; border: 1px solid #ddd; border-radius: 4px; width: 260px; }

.pagination { display: flex; align-items: center; gap: 16px; margin-top: 16px; }
.pagination a { color: #1a73e8; text-decoration: none; }

.detail-card { background: white; padding: 24px; border-radius: 8px; box-shadow: 0 1px 4px rgba(0,0,0,0.08); margin-bottom: 24px; }
.detail-row { display: flex; padding: 10px 0; border-bottom: 1px solid #f0f0f0; }
.detail-row label { width: 100px; color: #888; font-size: 14px; }

.form-card { background: white; padding: 24px; border-radius: 8px; box-shadow: 0 1px 4px rgba(0,0,0,0.08); max-width: 480px; }

.alert { padding: 12px 16px; border-radius: 4px; margin-bottom: 16px; font-size: 14px; }
.alert-error { background: #fce8e6; color: #ea4335; }
.alert-success { background: #e6f4ea; color: #34a853; }

.action-form { margin: 16px 0; }
```

- [ ] **Step 8: 提交**

```bash
git add admin/templates/ admin/static/
git commit -m "feat: admin panel templates (login, dashboard, tenants, detail, profile)"
```

---

## Task 7: 后端 — API 路由注册验证

**Files:**
- Verify: `backend/app.py`

- [ ] **Step 1: 验证 app.py 路由完整**

确保 `urls` 元组包含所有路由（顺序很重要，web.py 按顺序匹配）：

```python
urls = (
    "/api/auth/register", "AuthRegister",
    "/api/auth/login", "AuthLogin",
    "/api/auth/change_password", "AuthChangePassword",
    "/api/auth/heartbeat", "AuthHeartbeat",
    "/api/admin/login", "AdminLogin",
    "/api/admin/stats", "AdminStats",
    "/api/admin/tenants/(\w+)", "AdminTenantDetail",
    "/api/admin/tenants", "AdminTenants",
    "/api/rules/batch", "RulesBatchAPI",
    "/api/rules/(\d+)", "RuleDetailAPI",
    "/api/rules", "RulesAPI",
    "/api/models/(\d+)/test", "ModelTestAPI",
    "/api/models/(\d+)", "ModelDetailAPI",
    "/api/models", "ModelsAPI",
    "/api/ai/generate", "AIGenerateAPI",
    "/api/ai/chat", "AIChatAPI",
    "/api/history", "HistoryAPI",
    "/api/feedback", "FeedbackAPI",
    "/api/optimize/metrics", "OptimizeMetricsAPI",
    "/api/optimize/analyze", "OptimizeAnalyzeAPI",
    "/api/backup/restore", "BackupRestoreAPI",
    "/api/backup", "BackupExportAPI",
    "/health", "HealthCheck",
)
```

注意：`/api/admin/tenants/(\w+)` 必须在 `/api/admin/tenants` 之前，否则具体租户ID会被列表路由拦截。

- [ ] **Step 2: 本地验证后端启动**

```bash
cd backend
pip install -r requirements.txt
python app.py
```

检查输出无报错，访问 `http://localhost:8080/health` 返回 `{"status": "ok"}`。

- [ ] **Step 3: 提交（如有修改）**

```bash
git add backend/app.py
git commit -m "chore: verify and register all API routes"
```

---

## Task 8: 移动端 — RegisterScreen

**Files:**
- Create: `app/src/main/java/com/csbaby/kefu/presentation/screens/auth/RegisterScreen.kt`
- Create: `app/src/main/java/com/csbaby/kefu/presentation/screens/auth/RegisterViewModel.kt`
- Modify: `app/src/main/java/com\csbaby\kefu\presentation\navigation\AppNavigation.kt`
- Modify: `app/src/main/java/com/csbaby/kefu/data/remote/backend/BackendClient.kt`

- [ ] **Step 1: 创建 RegisterViewModel**

```kotlin
package com.csbaby.kefu.presentation.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.csbaby.kefu.data.local.AuthManager
import com.csbaby.kefu.data.remote.backend.BackendClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RegisterUiState(
    val phoneNumber: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val isLoading: Boolean = false,
    val isRegistered: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val backendClient: BackendClient,
    private val authManager: AuthManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

    fun onPhoneChanged(phone: String) {
        _uiState.update { it.copy(phoneNumber = phone, errorMessage = null) }
    }

    fun onPasswordChanged(password: String) {
        _uiState.update { it.copy(password = password, errorMessage = null) }
    }

    fun onConfirmPasswordChanged(confirm: String) {
        _uiState.update { it.copy(confirmPassword = confirm, errorMessage = null) }
    }

    fun register() {
        val state = _uiState.value
        if (state.phoneNumber.isBlank()) {
            _uiState.update { it.copy(errorMessage = "请输入手机号") }
            return
        }
        if (state.password.isBlank()) {
            _uiState.update { it.copy(errorMessage = "请输入密码") }
            return
        }
        if (state.password.length < 6) {
            _uiState.update { it.copy(errorMessage = "密码至少6位") }
            return
        }
        if (state.password != state.confirmPassword) {
            _uiState.update { it.copy(errorMessage = "两次密码不一致") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            val result = backendClient.registerUser(state.phoneNumber, state.password)
            result.fold(
                onSuccess = { response ->
                    authManager.saveAuth(
                        token = response.token,
                        tenantId = response.tenantId,
                        phoneNumber = response.phoneNumber,
                        expiresInSeconds = response.expiresIn
                    )
                    _uiState.update { it.copy(isLoading = false, isRegistered = true) }
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = e.message ?: "注册失败")
                    }
                }
            )
        }
    }
}
```

- [ ] **Step 2: 创建 RegisterScreen**

```kotlin
package com.csbaby.kefu.presentation.screens.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(
    onRegisterSuccess: () -> Unit,
    onNavigateToLogin: () -> Unit,
    viewModel: RegisterViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.isRegistered) {
        if (uiState.isRegistered) {
            onRegisterSuccess()
        }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("注册账号", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
            Text("创建您的新账号", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(40.dp))

            OutlinedTextField(value = uiState.phoneNumber, onValueChange = viewModel::onPhoneChanged, label = { Text("手机号") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(value = uiState.password, onValueChange = viewModel::onPasswordChanged, label = { Text("密码") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(value = uiState.confirmPassword, onValueChange = viewModel::onConfirmPasswordChanged, label = { Text("确认密码") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(24.dp))

            if (uiState.errorMessage != null) {
                Text(text = uiState.errorMessage!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 16.dp))
            }

            Button(onClick = viewModel::register, enabled = !uiState.isLoading, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                } else { Text("注册") }
            }

            Spacer(modifier = Modifier.height(16.dp))
            TextButton(onClick = onNavigateToLogin) { Text("已有账号？去登录") }
        }
    }
}
```

- [ ] **Step 3: 修改 BackendClient + BackendDtos — 新增 registerUser**

在 `BackendClient.kt` 的 `login()` 之后加：

```kotlin
suspend fun registerUser(phone: String, password: String): Result<LoginResponse> {
    return withContext(Dispatchers.IO) {
        try {
            val response = api.register(RegisterRequest(phone = phone, password = password))
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                Result.success(LoginResponse(
                    token = body.token,
                    tenantId = body.tenantId,
                    phoneNumber = body.phone,
                    expiresIn = body.expiresIn
                ))
            } else {
                Result.failure(Exception(parseError(response)))
            }
        } catch (e: IOException) {
            Result.failure(Exception("网络连接失败，请检查网络"))
        } catch (e: Exception) { Result.failure(e) }
    }
}
```

修改 `BackendDtos.kt` 中的 `RegisterRequest`：

```kotlin
data class RegisterRequest(
    val phone: String = "",
    val password: String = ""
)
```

给 `AuthResponse` 加字段：

```kotlin
data class AuthResponse(
    @SerializedName("device_id") val deviceId: String = "",
    val token: String = "",
    @SerializedName("expires_in") val expiresIn: Long = 0,
    @SerializedName("tenant_id") val tenantId: String = "",
    @SerializedName("phone_number") val phoneNumber: String = ""
)
```

- [ ] **Step 4: 修改 AppNavigation — 加 register 路由**

在 `RootNavigation` 的 NavHost 中加：

```kotlin
composable("register") {
    RegisterScreen(
        onRegisterSuccess = {
            navController.navigate(Screen.Home.route) { popUpTo("register") { inclusive = true } }
        },
        onNavigateToLogin = {
            navController.navigate("login") { popUpTo("register") { inclusive = true } }
        }
    )
}
```

修改 `LoginScreen` 调用处，加 `onNavigateToRegister` 参数：

```kotlin
composable("login") {
    LoginScreen(
        onLoginSuccess = {
            navController.navigate(Screen.Home.route) { popUpTo("login") { inclusive = true } }
        },
        onNavigateToRegister = { navController.navigate("register") }
    )
}
```

在 `LoginScreen.kt` 中加 `onNavigateToRegister: () -> Unit` 参数，在登录按钮下方加：

```kotlin
TextButton(onClick = onNavigateToRegister) { Text("没有账号？去注册") }
```

- [ ] **Step 5: 编译验证**

```bash
./gradlew assembleDebug
```

- [ ] **Step 6: 提交**

```bash
git add app/src/main/java/com/csbaby/kefu/presentation/screens/auth/RegisterScreen.kt \
    app/src/main/java/com/csbaby/kefu/presentation/screens/auth/RegisterViewModel.kt \
    app/src/main/java/com/csbaby/kefu/presentation/navigation/AppNavigation.kt \
    app/src/main/java/com/csbaby/kefu/data/remote/backend/BackendClient.kt \
    app/src/main/java/com/csbaby/kefu/data/remote/backend/BackendDtos.kt \
    app/src/main/java/com/csbaby/kefu/presentation/screens/auth/LoginScreen.kt
git commit -m "feat: add RegisterScreen + registerUser API client"
```

---

## Task 10: 部署验证

**Files:** N/A — 部署操作

- [ ] **Step 1: 部署后端**

Push 代码到 GitHub，Render 自动重新部署。验证：

```bash
curl https://csbaby-api2.onrender.com/health
```

- [ ] **Step 2: 验证管理员注册+登录**

```bash
curl -X POST https://csbaby-api2.onrender.com/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"phone":"15558181817","password":"Rd@202605"}'

curl -X POST https://csbaby-api2.onrender.com/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"phone":"15558181817","password":"Rd@202605"}'
```

- [ ] **Step 3: 验证 Admin API**

```bash
curl https://csbaby-api2.onrender.com/api/admin/stats \
  -H "Authorization: Bearer <上一步返回的token>"
```

- [ ] **Step 4: 部署管理后台**

在 Render 创建新 Web Service，根目录 `admin/`，环境变量 `API_BASE_URL=https://csbaby-api2.onrender.com`。访问 `https://csbaby-admin.onrender.com/admin/login` 登录。

- [ ] **Step 5: 移动端安装验证**

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

启动 App → LoginScreen → "去注册" → RegisterScreen → 注册成功 → 首页。

- [ ] **Step 6: 最终提交**

```bash
git add . && git commit -m "chore: deploy + verify end-to-end"
```
