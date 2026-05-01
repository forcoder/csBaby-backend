# 前后端分离架构设计

## 1. 概述

将 csBaby 应用从纯本地架构拆分为前后端分离架构：
- **前端（Android）**：保留 UI、通知监听、悬浮窗、本地配置，通过 REST API 与后端通信
- **后端（Python web.py + SQLite）**：托管数据存储、知识库管理、大模型调用、优化引擎，部署到 Render

## 2. 架构图

```
┌─────────────────────────────────────────────────────────────┐
│                    Android App (前端)                       │
│                                                             │
│  ┌──────────┐  ┌──────────────┐  ┌───────────────────────┐ │
│  │ UI层     │  │ 通知监听服务  │  │ 悬浮窗服务            │ │
│  │ Compose  │  │ Notification │  │ FloatingWindow        │ │
│  └──────────┘  │ Listener     │  └───────────────────────┘ │
│       │        └──────────────┘           │                 │
│       ▼                                    ▼                 │
│  ┌──────────────────────────────────────────────────────┐   │
│  │              ReplyOrchestrator (本地协调器)           │   │
│  └──────────────────────────┬───────────────────────────┘   │
│                             │                               │
│  ┌──────────────────────────▼───────────────────────────┐   │
│  │              ApiClient (Retrofit + OkHttp)            │   │
│  │   - 注册设备 / 心跳                                   │   │
│  │   - 知识库 CRUD                                      │   │
│  │   - 请求 AI 回复                                     │   │
│  │   - 模型配置管理                                     │   │
│  │   - 反馈上报                                         │   │
│  └──────────────────────────┬───────────────────────────┘   │
└─────────────────────────────┼───────────────────────────────┘
                              │ HTTPS REST API
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                  Python Backend (Render)                    │
│                                                             │
│  ┌──────────────────────────────────────────────────────┐   │
│  │                  web.py Application                   │   │
│  │                                                      │   │
│  │  ┌────────────┐ ┌────────────┐ ┌──────────────────┐ │   │
│  │  │ /api/auth  │ │/api/rules  │ │ /api/ai          │ │   │
│  │  │ 设备注册   │ │ 知识库CRUD │ │ 生成回复         │ │   │
│  │  │ 心跳检测   │ │ 批量操作   │ │ 测试连接         │ │   │
│  │  └────────────┘ └────────────┘ └──────────────────┘ │   │
│  │  ┌────────────┐ ┌────────────┐ ┌──────────────────┐ │   │
│  │  │/api/models │ │/api/feedback│ │ /api/optimize   │ │   │
│  │  │ 模型配置   │ │ 反馈上报   │ │ 优化分析         │ │   │
│  │  └────────────┘ └────────────┘ └──────────────────┘ │   │
│  │  ┌────────────┐ ┌────────────┐                       │   │
│  │  │/api/history│ │/api/backup │                       │   │
│  │  │ 回复历史   │ │ 备份恢复   │                       │   │
│  │  └────────────┘ └────────────┘                       │   │
│  └──────────────────────────────────────────────────────┘   │
│                                                             │
│  ┌─────────────────────┐  ┌─────────────────────────────┐   │
│  │   SQLite Database   │  │   External AI APIs          │   │
│  │   - devices         │  │   - OpenAI                  │   │
│  │   - keyword_rules   │  │   - Claude                  │   │
│  │   - model_configs   │  │   - Zhipu                   │   │
│  │   - reply_history   │  │   - Tongyi                  │   │
│  │   - feedback        │  │   - Custom                  │   │
│  │   - optimization    │  │                             │   │
│  └─────────────────────┘  └─────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

## 3. 后端 API 设计

### 3.1 认证方式
- 设备首次启动生成唯一 `device_id`（UUID）
- 注册后返回 `api_token`（JWT），后续请求携带 `Authorization: Bearer <token>`
- Token 过期时间：30天

### 3.2 API 端点

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/auth/register` | 设备注册 |
| POST | `/api/auth/heartbeat` | 心跳保活 |
| GET | `/api/rules` | 获取所有知识库规则 |
| POST | `/api/rules` | 创建规则 |
| PUT | `/api/rules/{id}` | 更新规则 |
| DELETE | `/api/rules/{id}` | 删除规则 |
| POST | `/api/rules/batch` | 批量导入规则 |
| GET | `/api/rules/export` | 导出规则（JSON/CSV） |
| GET | `/api/models` | 获取模型配置列表 |
| POST | `/api/models` | 创建模型配置 |
| PUT | `/api/models/{id}` | 更新模型配置 |
| DELETE | `/api/models/{id}` | 删除模型配置 |
| POST | `/api/models/{id}/test` | 测试模型连接 |
| POST | `/api/ai/generate` | 生成 AI 回复 |
| POST | `/api/ai/chat` | 多轮对话 |
| GET | `/api/history` | 获取回复历史 |
| POST | `/api/history` | 记录回复历史 |
| GET | `/api/feedback` | 获取反馈列表 |
| POST | `/api/feedback` | 提交用户反馈 |
| GET | `/api/optimize/metrics` | 获取优化指标 |
| POST | `/api/optimize/analyze` | 触发优化分析 |
| GET | `/api/backup` | 导出全量备份 |
| POST | `/api/backup/restore` | 恢复备份 |

### 3.3 核心请求/响应示例

**生成 AI 回复**：
```json
// POST /api/ai/generate
{
  "device_id": "uuid",
  "message": "客户消息内容",
  "context": {
    "customer_name": "张三",
    "house_name": "海景套房",
    "platform": "wechat"
  },
  "style": {
    "formality": 0.7,
    "enthusiasm": 0.5,
    "professionalism": 0.8
  }
}

// Response 200
{
  "reply": "您好张三！关于海景套房的问题...",
  "model_used": "gpt-4o",
  "confidence": 0.92,
  "response_time_ms": 1200,
  "tokens_used": 150
}
```

## 4. 数据库设计（SQLite）

```sql
-- 设备表
CREATE TABLE devices (
    id TEXT PRIMARY KEY,          -- UUID
    token TEXT NOT NULL,          -- JWT token
    name TEXT,
    platform TEXT DEFAULT 'android',
    app_version TEXT,
    last_heartbeat DATETIME,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 知识库规则表
CREATE TABLE keyword_rules (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    device_id TEXT NOT NULL,
    keyword TEXT NOT NULL,
    match_type TEXT DEFAULT 'CONTAINS',
    reply_template TEXT NOT NULL,
    category TEXT DEFAULT '',
    target_type TEXT DEFAULT 'ALL',
    target_names TEXT DEFAULT '[]',  -- JSON array
    priority INTEGER DEFAULT 0,
    enabled INTEGER DEFAULT 1,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (device_id) REFERENCES devices(id)
);

-- 模型配置表
CREATE TABLE model_configs (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    device_id TEXT NOT NULL,
    name TEXT NOT NULL,
    model_type TEXT NOT NULL,     -- OPENAI/CLAUDE/ZHIPU/TONGYI/CUSTOM
    model TEXT NOT NULL,          -- 模型标识如 gpt-4o
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

-- 回复历史表
CREATE TABLE reply_history (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    device_id TEXT NOT NULL,
    original_message TEXT,
    reply_content TEXT,
    source TEXT DEFAULT 'ai',     -- keyword/ai
    model_used TEXT,
    confidence REAL,
    response_time_ms INTEGER,
    platform TEXT,
    customer_name TEXT,
    house_name TEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (device_id) REFERENCES devices(id)
);

-- 用户反馈表
CREATE TABLE feedback (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    device_id TEXT NOT NULL,
    reply_history_id INTEGER,
    action TEXT NOT NULL,         -- accepted/modified/rejected
    modified_text TEXT,
    rating INTEGER,
    comment TEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (device_id) REFERENCES devices(id)
);

-- 优化指标表
CREATE TABLE optimization_metrics (
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

-- 索引
CREATE INDEX idx_rules_device ON keyword_rules(device_id);
CREATE INDEX idx_rules_keyword ON keyword_rules(keyword);
CREATE INDEX idx_history_device ON reply_history(device_id);
CREATE INDEX idx_history_created ON reply_history(created_at);
CREATE INDEX idx_feedback_device ON feedback(device_id);
CREATE INDEX idx_metrics_device_date ON optimization_metrics(device_id, date);
```

## 5. 后端项目结构

```
backend/
├── app.py                 # web.py 主入口
├── config.py              # 配置（数据库路径、AI API keys、JWT secret）
├── database.py            # SQLite 数据库初始化和连接管理
├── auth.py                # JWT 认证装饰器
├── models/
│   ├── __init__.py
│   ├── device.py          # 设备模型
│   ├── rule.py            # 知识库规则模型
│   ├── model_config.py    # 模型配置模型
│   ├── history.py         # 回复历史模型
│   ├── feedback.py        # 反馈模型
│   └── metrics.py         # 优化指标模型
├── services/
│   ├── __init__.py
│   ├── ai_service.py      # 大模型调用服务（支持多模型）
│   ├── rule_engine.py     # 规则匹配引擎
│   └── optimize_service.py # 优化分析服务
├── api/
│   ├── __init__.py
│   ├── auth.py            # 认证相关路由
│   ├── rules.py           # 知识库规则路由
│   ├── models.py          # 模型配置路由
│   ├── ai.py              # AI 生成路由
│   ├── history.py         # 回复历史路由
│   ├── feedback.py        # 反馈路由
│   ├── optimize.py        # 优化路由
│   └── backup.py          # 备份恢复路由
├── requirements.txt       # Python 依赖
├── render.yaml            # Render 部署配置
└── tests/
    └── test_api.py
```

## 6. Android 端改造

### 6.1 移除（迁移到后端）
- Room 数据库中的 keyword_rules、model_configs、reply_history、feedback、optimization_metrics 表及相关 DAO
- AIClient / AIService 的大模型 HTTP 调用逻辑
- KnowledgeBaseManager 中的规则匹配逻辑（保留缓存层）
- LLMFeatureManager、OptimizationEngine

### 6.2 保留（本地）
- PreferencesManager（本地配置：监控开关、主题、风格参数等）
- NotificationListenerService（通知监听）
- FloatingWindowService（悬浮窗）
- ReplyOrchestrator（改为通过 API 调用后端）
- BackupManager（改为通过 API 备份到后端）
- OTA 更新逻辑

### 6.3 新增
- `ApiClient`：Retrofit 接口，封装所有后端 API 调用
- `DeviceManager`：设备注册、Token 管理、心跳保活
- `RuleCacheManager`：本地规则缓存（LRU），减少 API 调用
- 网络状态监听：无网络时降级到本地 Room 缓存

### 6.4 Android 依赖变更
```kotlin
// 移除（不再需要）
// - Room 大部分 DAO（保留 AppConfig 等本地配置表）
// - AIClient / AIService 的 OkHttp AI 调用

// 新增
// - Retrofit 后端 API 客户端（已有 Retrofit 依赖，复用）
// - 网络状态监听
```

## 7. 部署方案（Render）

### 7.1 Render 配置
- **服务类型**：Web Service
- **Runtime**：Python 3
- **Build Command**：`pip install -r requirements.txt`
- **Start Command**：`python app.py`
- **环境变量**：
  - `DATABASE_PATH`: `/var/data/csBaby.db`（Render 持久化存储）
  - `JWT_SECRET`: 随机生成的密钥
  - `RENDER_API_KEY`: `rnd_FXR6kBx93Pt3bXUGmxo6E3FQugAv`

### 7.2 数据持久化
- Render Web Service 重启后会丢失文件系统数据
- 使用 Render 的 Persistent Disk（`/var/data`）存放 SQLite 数据库
- 或者使用 Render PostgreSQL（免费 tier）替代 SQLite

### 7.3 部署步骤
1. 创建 GitHub 仓库存放后端代码
2. 在 Render 创建 Web Service，关联该仓库
3. 配置环境变量和持久化存储
4. 自动部署

## 8. 开发顺序

1. **后端基础**：web.py 项目结构 + SQLite 数据库 + 认证
2. **后端核心 API**：知识库 CRUD + AI 生成 + 模型配置
3. **后端辅助 API**：历史记录 + 反馈 + 优化 + 备份
4. **Android ApiClient**：Retrofit 接口 + 设备注册 + Token 管理
5. **Android 改造**：替换本地数据源为 API 调用 + 离线缓存
6. **部署**：Render 部署 + 配置 + 测试
7. **联调**：端到端测试

## 9. 风险与缓解

| 风险 | 缓解措施 |
|------|----------|
| Render 免费 tier 冷启动慢 | 心跳保活 + Android 端请求重试 |
| 网络不可用 | 本地规则缓存 + 离线模式降级 |
| API Key 泄露 | 后端统一管理，Android 端不存储 AI API Key |
| 数据迁移 | 首次启动时从本地 Room 上传到后端 |
