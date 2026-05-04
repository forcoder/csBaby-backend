# 多租户用户体系 + Web 管理后台设计

## 一、背景

当前后端是设备注册模式（注册返回 device_id + token），没有账号密码体系。移动端 LoginScreen 调的是 POST /api/auth/login，但后端未实现。没有任何管理后台可以查看/管理租户数据。

## 二、管理员凭据

管理员账号硬编码在配置文件中，不提交到 git。

| 文件 | 说明 |
|------|------|
| `backend/admin_config.py` | 管理员凭据配置文件（gitignore） |
| `backend/admin_config.example.py` | 示例文件，提交到 git |

凭据：
- 手机号：`15558181817`
- 密码：`Rd@202605`

## 三、后端改造（csbaby-api）

### 3.1 数据库变更

新增 `users` 表：

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

`devices` 表加 `user_id` 字段（可选关联，兼容老数据）：

```sql
ALTER TABLE devices ADD COLUMN user_id INTEGER REFERENCES users(id);
```

数据迁移：启动时检查，为每个已有 device 自动创建对应的 user 记录（tenant_id = device_id，随机生成密码哈希，用户后续通过手机号找回）。

### 3.2 auth.py 改造

新增函数：

- `hash_password(password: str) -> str` — bcrypt 哈希
- `verify_password(password: str, hash: str) -> bool` — bcrypt 校验
- `generate_admin_token(phone: str) -> str` — 管理员 token（payload 加 is_admin=1）
- `extract_admin_info() -> dict` — 从 token 提取 {device_id, is_admin}

Token payload 变更：
```python
{
    "device_id": "xxx",
    "tenant_id": "yyy",    # 新增
    "is_admin": 0,         # 新增，管理员为 1
    "exp": ...,
    "iat": ...
}
```

### 3.3 认证 API 变更

#### POST /api/auth/register — 手机号+密码注册

请求：
```json
{
    "phone": "15558181817",
    "password": "Rd@202605",
    "name": "可选名称"
}
```

逻辑：
1. 校验手机号格式（11位，1开头）
2. 校验密码强度（至少6位）
3. 检查手机号是否已存在
4. bcrypt 哈希密码
5. 生成 tenant_id（UUID4）
6. 插入 users 表
7. 生成 token（含 tenant_id）

响应：
```json
{
    "token": "xxx",
    "tenant_id": "yyy",
    "phone": "15558181817",
    "expires_in": 2592000
}
```

#### POST /api/auth/login — 手机号+密码登录

请求：
```json
{
    "phone": "15558181817",
    "password": "Rd@202605"
}
```

逻辑：
1. 查找 users 表
2. bcrypt 校验密码
3. 检查 is_active
4. 生成 token（管理员携带 is_admin=1）

响应同注册。

#### POST /api/auth/change_password — 修改密码

需登录。请求：`{ "old_password": "xxx", "new_password": "yyy" }`

### 3.4 Admin API

所有 Admin API 需要管理员 token（is_admin=1）。

#### POST /api/admin/login — 管理员登录

同 /api/auth/login，但额外校验 is_admin=1。

#### GET /api/admin/tenants — 租户列表

支持查询参数：`?page=1&page_size=20&search=手机号`

响应：
```json
{
    "total": 100,
    "page": 1,
    "page_size": 20,
    "items": [
        {
            "id": 1,
            "phone": "15558181817",
            "tenant_id": "xxx",
            "is_active": 1,
            "created_at": "...",
            "rule_count": 50,
            "history_count": 200,
            "last_active": "..."
        }
    ]
}
```

#### GET /api/admin/tenants/{tenant_id} — 租户详情

该租户的完整统计数据：规则数、模型数、历史记录数、反馈数、最近活跃时间。

#### PUT /api/admin/tenants/{tenant_id}/status — 启用/禁用

请求：`{ "is_active": 0 }`

#### GET /api/admin/stats — 全局统计

```json
{
    "total_tenants": 100,
    "active_tenants": 80,
    "total_rules": 5000,
    "total_history": 100000,
    "today_history": 500
}
```

### 3.5 admin_config.py 配置

```python
import os

# 管理员账号（不提交到 git）
ADMIN_PHONE = os.environ.get("ADMIN_PHONE", "15558181817")
ADMIN_PASSWORD = os.environ.get("ADMIN_PASSWORD", "Rd@202605")
```

`backend/.gitignore` 加一行：`admin_config.py`

## 四、Web 管理后台（独立部署）

### 4.1 技术栈

- Python + web.py（与 API 服务保持一致）
- Jinja2 模板（服务端渲染）
- 无数据库，通过 HTTP 调用 API 服务的 Admin API
- Session 用 cookie 存储（web.py 内置 session）

### 4.2 项目结构

```
admin/
    app.py              # 入口 + URL 路由
    config.py           # API 服务地址、管理员 session 密钥
    templates/
        layout.html     # 基础布局
        login.html      # 登录页
        dashboard.html  # 总览
        tenants.html    # 租户列表
        tenant_detail.html  # 租户详情
        profile.html    # 修改密码
    static/
        style.css       # 极简样式
    requirements.txt    # web.py, jinja2, requests
    render.yaml         # Render 部署配置
```

### 4.3 部署

- 独立 Render Web Service
- 服务名：`csbaby-admin`
- 访问地址：`https://csbaby-admin.onrender.com`
- 环境变量：`API_BASE_URL=https://csbaby-api2.onrender.com`，`SESSION_SECRET=随机值`

### 4.4 页面设计

#### /admin/login

简单的登录表单：手机号 + 密码。提交后调 `/api/admin/login`，成功则写 session，跳转到 dashboard。

#### /admin/dashboard

卡片式布局：
- 总租户数 / 活跃租户数
- 总规则数
- 今日回复数
- 最近注册租户列表（5条）

#### /admin/tenants

表格：手机号、租户ID、注册时间、规则数、状态、操作（查看详情/禁用）。
支持手机号搜索、分页。

#### /admin/tenants/{id}

租户详情卡片：
- 基本信息（手机号、注册时间、状态）
- 统计数据（规则数、模型数、历史数、反馈数）
- 操作：启用/禁用、重置密码

#### /admin/profile

修改管理员密码表单。

### 4.5 认证中间件

每个管理页面请求前检查 session 中的 `admin_phone`，无则跳转登录页。

## 五、移动端变更

### 5.1 注册功能

新增 `RegisterScreen`：
- 手机号输入
- 密码输入
- 确认密码
- 提交调 `POST /api/auth/register`
- 成功后自动登录（保存 token + tenant_id）

### 5.2 LoginScreen 变更

当前已经正确调 `POST /api/auth/login`，后端实现后即可工作。不需要改移动端代码。

### 5.3 BackendClient.login() 响应处理

后端 `LoginResponse` 字段对齐：`token`、`tenant_id`、`phone`、`expires_in`。移动端 `AuthManager.saveAuth()` 已支持这些字段，不需要改。

## 六、实施顺序

1. 后端：数据库变更 + auth.py 改造
2. 后端：登录/注册 API
3. 后端：Admin API
4. 后端：admin_config.py + .gitignore
5. 管理后台：项目搭建 + 登录页
6. 管理后台：Dashboard + 租户列表
7. 管理后台：租户详情 + 全局统计
8. 移动端：RegisterScreen
9. 部署：API 服务更新 + 管理后台部署
