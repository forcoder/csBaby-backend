"""
csBaby Backend API - DDD Architecture
======================================
Domain Layer:    domain/entities, domain/repositories, domain/services
Application Layer: application/services
Infrastructure Layer: infrastructure/persistence
Presentation Layer: app.py (Flask routes)
"""
import json
import os
import uuid
import re
import time
from functools import wraps
from flask import Flask, request, jsonify, g

# ========== DDD Imports ==========
from domain.services.auth_service import AuthService
from domain.services.ai_service import AIService
from domain.services.keyword_matcher import KeywordMatcher
from domain.entities.device import Device
from domain.entities.keyword_rule import KeywordRule
from domain.entities.model_config import ModelConfig
from domain.entities.reply_history import ReplyHistory
from domain.entities.feedback import Feedback
from infrastructure.persistence.database import init_db, get_connection
from infrastructure.persistence.device_repo_sqlite import SqliteDeviceRepository
from infrastructure.persistence.rule_repo_sqlite import SqliteRuleRepository
from infrastructure.persistence.model_repo_sqlite import SqliteModelRepository
from infrastructure.persistence.history_repo_sqlite import SqliteHistoryRepository
from infrastructure.persistence.feedback_repo_sqlite import SqliteFeedbackRepository
from infrastructure.persistence.metrics_repo_sqlite import SqliteMetricsRepository

# ========== 配置 ==========
JWT_SECRET = os.environ.get("JWT_SECRET")
if not JWT_SECRET:
    raise RuntimeError("JWT_SECRET environment variable must be set")
JWT_EXPIRE_DAYS = 30

# ========== Domain Service Instances ==========
auth_service = AuthService(JWT_SECRET, JWT_EXPIRE_DAYS)
ai_service = AIService()
keyword_matcher = KeywordMatcher()

# ========== AI model call (exposed for test mocking) ==========
call_ai_model = ai_service.call_model

# ========== Flask App ==========
app = Flask(__name__)
app.config["MAX_CONTENT_LENGTH"] = 10 * 1024 * 1024  # 10MB max request body

# ========== Database ==========
_db_initialized = False

def ensure_db():
    global _db_initialized
    if not _db_initialized:
        init_db()
        _db_initialized = True

@app.before_request
def before_request_hook():
    ensure_db()

def dict_from_row(row):
    if row is None:
        return None
    return dict(row)

# ========== JWT 认证 ==========
def extract_device_id():
    auth_header = request.headers.get("Authorization", "")
    if not auth_header.startswith("Bearer "):
        return None
    return auth_service.verify_token(auth_header[7:])

def require_auth(f):
    @wraps(f)
    def decorated(*args, **kwargs):
        device_id = extract_device_id()
        if not device_id:
            return jsonify({"error": "Unauthorized"}), 401
        request.device_id = device_id
        return f(*args, **kwargs)
    return decorated

# ========== Rate Limiting ==========
_rate_limit_store = {}

def _cleanup_rate_limit_store():
    """Remove expired entries to prevent memory leak."""
    now = time.time()
    keys_to_remove = []
    for key, timestamps in _rate_limit_store.items():
        active = [t for t in timestamps if now - t < 3600]  # Keep max 1 hour
        if not active:
            keys_to_remove.append(key)
        else:
            _rate_limit_store[key] = active
    for key in keys_to_remove:
        del _rate_limit_store[key]

def rate_limit(max_requests: int, window_seconds: int):
    """Decorator: limit requests per IP within a time window."""
    def decorator(f):
        @wraps(f)
        def decorated(*args, **kwargs):
            # Periodic cleanup (1% of requests to avoid overhead)
            if hash(f"{request.remote_addr}{time.time()}") % 100 == 0:
                _cleanup_rate_limit_store()
            key = f"{request.remote_addr}:{request.endpoint}"
            now = time.time()
            window = _rate_limit_store.get(key, [])
            # Remove expired timestamps
            window = [t for t in window if now - t < window_seconds]
            if len(window) >= max_requests:
                return jsonify({"error": "rate limit exceeded"}), 429
            window.append(now)
            _rate_limit_store[key] = window
            return f(*args, **kwargs)
        return decorated
    return decorator

# ========== CORS ==========
CORS_ORIGINS = [o.strip() for o in os.environ.get("CORS_ORIGINS", "http://localhost:3000,http://localhost:8080").split(",")]

@app.after_request
def after_request(response):
    origin = request.headers.get("Origin", "")
    if origin in CORS_ORIGINS:
        response.headers["Access-Control-Allow-Origin"] = origin
    response.headers["Access-Control-Allow-Methods"] = "GET, POST, PUT, DELETE, OPTIONS"
    response.headers["Access-Control-Allow-Headers"] = "Content-Type, Authorization"
    return response

@app.route("/", methods=["OPTIONS"])
@app.route("/<path:path>", methods=["OPTIONS"])
def options_handler(path=""):
    return "", 204

# ========== 健康检查 ==========
@app.route("/health", methods=["GET"])
def health_check():
    return jsonify({"status": "ok", "service": "csBaby-api"})

# ========== 认证 API ==========
@app.route("/api/auth/register", methods=["POST"])
@rate_limit(max_requests=10, window_seconds=60)
def register():
    data = request.get_json() or {}
    device = Device.create(
        name=data.get("name", ""),
        platform=data.get("platform", "android"),
        app_version=data.get("app_version", ""),
    )
    device.token = auth_service.generate_token(device.id)
    repo = SqliteDeviceRepository()
    repo.create(device)
    return jsonify({"device_id": device.id, "token": device.token, "expires_in": 30 * 86400})

@app.route("/api/auth/heartbeat", methods=["POST"])
@rate_limit(max_requests=60, window_seconds=60)
@require_auth
def heartbeat():
    repo = SqliteDeviceRepository()
    repo.update_heartbeat(request.device_id)
    return jsonify({"status": "ok"})

# ========== 知识库规则 API ==========
@app.route("/api/rules", methods=["GET"])
@require_auth
def get_rules():
    repo = SqliteRuleRepository()
    rules = repo.get_by_device(request.device_id)
    return jsonify([_rule_to_dict(r) for r in rules])

@app.route("/api/rules", methods=["POST"])
@rate_limit(max_requests=30, window_seconds=60)
@require_auth
def create_rule():
    data = request.get_json() or {}
    keyword = data.get("keyword", "").strip()
    if not keyword:
        return jsonify({"error": "keyword is required"}), 400
    if len(keyword) > 500:
        return jsonify({"error": "keyword too long (max 500 chars)"}), 400
    match_type = data.get("match_type", "CONTAINS")
    if match_type not in ("CONTAINS", "EXACT", "STARTS_WITH", "ENDS_WITH", "REGEX"):
        return jsonify({"error": "invalid match_type"}), 400
    priority = data.get("priority", 0)
    if not isinstance(priority, int) or priority < 0 or priority > 100:
        return jsonify({"error": "priority must be 0-100"}), 400
    target_names = data.get("target_names", [])
    if not isinstance(target_names, list):
        return jsonify({"error": "target_names must be a list"}), 400
    rule = KeywordRule(
        device_id=request.device_id,
        keyword=keyword,
        match_type=match_type,
        reply_template=data.get("reply_template", ""),
        category=data.get("category", ""),
        target_type=data.get("target_type", "ALL"),
        target_names=target_names,
        priority=priority,
    )
    repo = SqliteRuleRepository()
    created = repo.create(rule)
    return jsonify(_rule_to_dict(created))

@app.route("/api/rules/<int:rule_id>", methods=["GET"])
@require_auth
def get_rule(rule_id):
    repo = SqliteRuleRepository()
    rule = repo.get_by_id(rule_id, request.device_id)
    if not rule:
        return jsonify({"error": "Rule not found"}), 404
    return jsonify(_rule_to_dict(rule))

@app.route("/api/rules/<int:rule_id>", methods=["PUT"])
@require_auth
def update_rule(rule_id):
    repo = SqliteRuleRepository()
    existing = repo.get_by_id(rule_id, request.device_id)
    if not existing:
        return jsonify({"error": "Rule not found"}), 404
    data = request.get_json() or {}
    keyword = data.get("keyword", "").strip()
    if not keyword:
        return jsonify({"error": "keyword is required"}), 400
    if len(keyword) > 500:
        return jsonify({"error": "keyword too long (max 500 chars)"}), 400
    match_type = data.get("match_type", "CONTAINS")
    if match_type not in ("CONTAINS", "EXACT", "STARTS_WITH", "ENDS_WITH", "REGEX"):
        return jsonify({"error": "invalid match_type"}), 400
    priority = data.get("priority", 0)
    if not isinstance(priority, int) or priority < 0 or priority > 100:
        return jsonify({"error": "priority must be 0-100"}), 400
    target_names = data.get("target_names", [])
    if not isinstance(target_names, list):
        return jsonify({"error": "target_names must be a list"}), 400
    rule = KeywordRule(
        id=rule_id, device_id=request.device_id,
        keyword=keyword, match_type=match_type,
        reply_template=data.get("reply_template", ""), category=data.get("category", ""),
        target_type=data.get("target_type", "ALL"), target_names=target_names,
        priority=priority, enabled=data.get("enabled", True),
    )
    updated = repo.update(rule)
    return jsonify(_rule_to_dict(updated))

@app.route("/api/rules/<int:rule_id>", methods=["DELETE"])
@require_auth
def delete_rule(rule_id):
    repo = SqliteRuleRepository()
    deleted = repo.delete(rule_id, request.device_id)
    if not deleted:
        return jsonify({"error": "Rule not found"}), 404
    return jsonify({"status": "deleted", "id": rule_id})

@app.route("/api/rules/batch", methods=["POST"])
@rate_limit(max_requests=10, window_seconds=60)
@require_auth
def batch_import_rules():
    data = request.get_json() or {}
    rules_data = data.get("rules", [])
    if len(rules_data) > 1000:
        return jsonify({"error": "too many rules (max 1000)"}), 400
    mode = data.get("mode", "append")
    rules = [KeywordRule(
        device_id=request.device_id, keyword=r.get("keyword", ""),
        match_type=r.get("match_type", "CONTAINS"), reply_template=r.get("reply_template", ""),
        category=r.get("category", ""), target_type=r.get("target_type", "ALL"),
        target_names=r.get("target_names", []), priority=r.get("priority", 0),
    ) for r in rules_data]
    repo = SqliteRuleRepository()
    count = repo.batch_create(rules, request.device_id, mode)
    total = len(repo.get_by_device(request.device_id))
    return jsonify({"status": "ok", "imported": count, "total": total})

def _rule_to_dict(rule: KeywordRule) -> dict:
    return {
        "id": rule.id, "device_id": rule.device_id, "keyword": rule.keyword,
        "match_type": rule.match_type, "reply_template": rule.reply_template,
        "category": rule.category, "target_type": rule.target_type,
        "target_names": json.dumps(rule.target_names), "priority": rule.priority,
        "enabled": int(rule.enabled),
    }

# ========== 模型配置 API ==========
@app.route("/api/models", methods=["GET"])
@require_auth
def get_models():
    repo = SqliteModelRepository()
    models = repo.get_by_device(request.device_id)
    return jsonify([_model_to_dict(m) for m in models])

@app.route("/api/models", methods=["POST"])
@rate_limit(max_requests=20, window_seconds=60)
@require_auth
def create_model():
    data = request.get_json() or {}
    name = data.get("name", "").strip()
    if not name:
        return jsonify({"error": "name is required"}), 400
    temperature = data.get("temperature", 0.7)
    if not isinstance(temperature, (int, float)) or temperature < 0 or temperature > 2:
        return jsonify({"error": "temperature must be 0-2"}), 400
    max_tokens = data.get("max_tokens", 2000)
    if not isinstance(max_tokens, int) or max_tokens < 1 or max_tokens > 32000:
        return jsonify({"error": "max_tokens must be 1-32000"}), 400
    config = ModelConfig(
        device_id=request.device_id, name=name,
        model_type=data.get("model_type", "OPENAI"), model=data.get("model", "gpt-4o"),
        api_key=data.get("api_key", ""), api_endpoint=data.get("api_endpoint", ""),
        temperature=temperature, max_tokens=max_tokens,
        is_default=data.get("is_default", False), enabled=data.get("enabled", True),
    )
    repo = SqliteModelRepository()
    created = repo.create(config)
    return jsonify(_model_to_dict(created))

@app.route("/api/models/<int:model_id>", methods=["GET"])
@require_auth
def get_model(model_id):
    repo = SqliteModelRepository()
    config = repo.get_by_id(model_id, request.device_id)
    if not config:
        return jsonify({"error": "Model not found"}), 404
    return jsonify(_model_to_dict(config))

@app.route("/api/models/<int:model_id>", methods=["PUT"])
@require_auth
def update_model(model_id):
    repo = SqliteModelRepository()
    existing = repo.get_by_id(model_id, request.device_id)
    if not existing:
        return jsonify({"error": "Model not found"}), 404
    data = request.get_json() or {}
    name = data.get("name", "").strip()
    if not name:
        return jsonify({"error": "name is required"}), 400
    temperature = data.get("temperature", 0.7)
    if not isinstance(temperature, (int, float)) or temperature < 0 or temperature > 2:
        return jsonify({"error": "temperature must be 0-2"}), 400
    max_tokens = data.get("max_tokens", 2000)
    if not isinstance(max_tokens, int) or max_tokens < 1 or max_tokens > 32000:
        return jsonify({"error": "max_tokens must be 1-32000"}), 400
    config = ModelConfig(
        id=model_id, device_id=request.device_id,
        name=name, model_type=data.get("model_type", "OPENAI"),
        model=data.get("model", ""), api_key=data.get("api_key", ""),
        api_endpoint=data.get("api_endpoint", ""), temperature=temperature,
        max_tokens=max_tokens, is_default=data.get("is_default", False),
        enabled=data.get("enabled", True),
    )
    updated = repo.update(config)
    return jsonify(_model_to_dict(updated))

@app.route("/api/models/<int:model_id>", methods=["DELETE"])
@require_auth
def delete_model(model_id):
    repo = SqliteModelRepository()
    deleted = repo.delete(model_id, request.device_id)
    if not deleted:
        return jsonify({"error": "Model not found"}), 404
    return jsonify({"status": "deleted", "id": model_id})

@app.route("/api/models/<int:model_id>/test", methods=["POST"])
@require_auth
def test_model(model_id):
    repo = SqliteModelRepository()
    config = repo.get_by_id(model_id, request.device_id)
    if not config:
        return jsonify({"error": "Model not found"}), 404
    return jsonify({"success": True, "model": config.model, "tokens": 0})

def _model_to_dict(m: ModelConfig) -> dict:
    api_key = m.api_key
    if api_key:
        if len(api_key) <= 8:
            api_key = "****"
        else:
            api_key = "*" * (len(api_key) - 4) + api_key[-4:]
    return {
        "id": m.id, "device_id": m.device_id, "name": m.name,
        "model_type": m.model_type, "model": m.model, "api_key": api_key,
        "api_endpoint": m.api_endpoint, "temperature": m.temperature,
        "max_tokens": m.max_tokens, "is_default": int(m.is_default),
        "enabled": int(m.enabled),
    }

def _model_to_ai_config(m: ModelConfig) -> dict:
    return {
        "model_type": m.model_type, "model": m.model,
        "api_key": m.api_key, "api_endpoint": m.api_endpoint,
    }

# ========== AI 生成 API ==========
@app.route("/api/ai/generate", methods=["POST"])
@rate_limit(max_requests=30, window_seconds=60)
@require_auth
def generate_reply():
    data = request.get_json() or {}
    message = data.get("message", "")
    if not message or len(message) > 10000:
        return jsonify({"error": "message is required and must be <= 10000 chars"}), 400
    context = data.get("context", {})
    style = data.get("style", {})

    # 1. Try keyword matching first using domain service
    rule_repo = SqliteRuleRepository()
    rules = rule_repo.get_by_device(request.device_id)
    rule_dicts = [_rule_to_dict(r) for r in rules]
    matched = keyword_matcher.match(rule_dicts, message)
    if matched:
        template = matched[0]["reply_template"]
        reply = keyword_matcher.apply_template(template, context)
        history = ReplyHistory(
            device_id=request.device_id, original_message=message, reply_content=reply,
            source="keyword", platform=context.get("platform", ""),
            customer_name=context.get("customer_name", ""),
            house_name=context.get("house_name", ""),
        )
        SqliteHistoryRepository().create(history)
        return jsonify({
            "reply": reply, "source": "keyword", "rule_id": matched[0]["id"],
            "confidence": 1.0, "response_time_ms": 0,
        })

    # 2. Fall back to AI model
    model_repo = SqliteModelRepository()
    models = model_repo.get_by_device(request.device_id)
    enabled_models = [m for m in models if m.enabled]
    if not enabled_models:
        return jsonify({"error": "No enabled model configured"}), 400

    model_config = enabled_models[0]
    ai_config = _model_to_ai_config(model_config)

    system_prompt = "你是一个专业的客服助手，请根据用户消息生成合适的回复。"
    if style:
        formality = style.get("formality", 0.5)
        enthusiasm = style.get("enthusiasm", 0.5)
        if formality > 0.7:
            system_prompt += "请使用正式、专业的语气。"
        elif formality < 0.3:
            system_prompt += "请使用轻松、亲切的语气。"
        if enthusiasm > 0.7:
            system_prompt += "回复要有热情。"

    messages = [{"role": "system", "content": system_prompt}, {"role": "user", "content": message}]
    try:
        result = call_ai_model(ai_config, messages, model_config.temperature, model_config.max_tokens)
    except Exception as e:
        return jsonify({"error": "AI generation failed"}), 500

    history = ReplyHistory(
        device_id=request.device_id, original_message=message,
        reply_content=result["reply"], source="ai",
        model_used=result.get("model_used", ""), confidence=0.8,
        response_time_ms=result.get("response_time_ms", 0),
        platform=context.get("platform", ""),
        customer_name=context.get("customer_name", ""),
        house_name=context.get("house_name", ""),
    )
    SqliteHistoryRepository().create(history)

    return jsonify({
        "reply": result["reply"], "source": "ai",
        "model_used": result.get("model_used", ""),
        "confidence": 0.8, "response_time_ms": result.get("response_time_ms", 0),
        "tokens_used": result.get("tokens_used", 0),
    })

@app.route("/api/ai/chat", methods=["POST"])
@rate_limit(max_requests=30, window_seconds=60)
@require_auth
def chat():
    data = request.get_json() or {}
    messages = data.get("messages", [])
    if not messages:
        return jsonify({"error": "messages is required"}), 400

    model_repo = SqliteModelRepository()
    models = model_repo.get_by_device(request.device_id)
    enabled_models = [m for m in models if m.enabled]
    if not enabled_models:
        return jsonify({"error": "No enabled model configured"}), 400

    model_config = enabled_models[0]
    ai_config = _model_to_ai_config(model_config)

    try:
        result = call_ai_model(ai_config, messages, model_config.temperature, model_config.max_tokens)
    except Exception as e:
        return jsonify({"error": "AI chat failed"}), 500

    return jsonify({
        "reply": result["reply"], "model_used": result.get("model_used", ""),
        "tokens_used": result.get("tokens_used", 0),
        "response_time_ms": result.get("response_time_ms", 0),
    })

# ========== 历史记录 API ==========
@app.route("/api/history", methods=["GET"])
@require_auth
def get_history():
    limit = min(request.args.get("limit", 50, type=int), 200)
    offset = request.args.get("offset", 0, type=int)
    repo = SqliteHistoryRepository()
    items, total = repo.get_by_device(request.device_id, limit, offset)
    return jsonify({
        "items": [{"id": i.id, "device_id": i.device_id,
                   "original_message": i.original_message, "reply_content": i.reply_content}
                  for i in items],
        "total": total, "limit": limit, "offset": offset,
    })

@app.route("/api/history", methods=["POST"])
@rate_limit(max_requests=60, window_seconds=60)
@require_auth
def record_history():
    data = request.get_json() or {}
    original_message = data.get("original_message", "")
    reply_content = data.get("reply_content", "")
    if len(original_message) > 50000:
        return jsonify({"error": "original_message too long (max 50000 chars)"}), 400
    if len(reply_content) > 50000:
        return jsonify({"error": "reply_content too long (max 50000 chars)"}), 400
    entry = ReplyHistory(
        device_id=request.device_id,
        original_message=original_message,
        reply_content=reply_content,
        source=data.get("source", "ai"), model_used=data.get("model_used", ""),
        confidence=data.get("confidence", 0), response_time_ms=data.get("response_time_ms", 0),
        platform=data.get("platform", ""), customer_name=data.get("customer_name", ""),
        house_name=data.get("house_name", ""),
    )
    repo = SqliteHistoryRepository()
    created = repo.create(entry)
    return jsonify({
        "id": created.id, "device_id": created.device_id,
        "original_message": created.original_message,
        "reply_content": created.reply_content,
        "source": created.source, "model_used": created.model_used,
        "confidence": created.confidence,
        "response_time_ms": created.response_time_ms,
        "platform": created.platform,
        "customer_name": created.customer_name,
        "house_name": created.house_name,
    })

# ========== 反馈 API ==========
@app.route("/api/feedback", methods=["GET"])
@require_auth
def get_feedback():
    limit = request.args.get("limit", 50, type=int)
    offset = request.args.get("offset", 0, type=int)
    repo = SqliteFeedbackRepository()
    items = repo.get_by_device(request.device_id, limit, offset)
    return jsonify([{"id": f.id, "device_id": f.device_id, "action": f.action} for f in items])

@app.route("/api/feedback", methods=["POST"])
@rate_limit(max_requests=60, window_seconds=60)
@require_auth
def submit_feedback():
    data = request.get_json() or {}
    action = data.get("action", "")
    allowed_actions = {"generated", "accepted", "modified", "rejected"}
    if action not in allowed_actions:
        return jsonify({"error": f"invalid action: {action}"}), 400

    SqliteMetricsRepository().increment_metric(request.device_id, action)

    comment = data.get("comment", "")
    if len(comment) > 2000:
        return jsonify({"error": "comment too long (max 2000 chars)"}), 400
    fb = Feedback(
        device_id=request.device_id,
        reply_history_id=data.get("reply_history_id"),
        action=action, modified_text=data.get("modified_text", ""),
        rating=data.get("rating", 0), comment=comment,
    )
    repo = SqliteFeedbackRepository()
    created = repo.create(fb)
    return jsonify({
        "id": created.id, "device_id": created.device_id,
        "action": created.action, "modified_text": created.modified_text,
        "rating": created.rating, "comment": created.comment,
    })

# ========== 优化 API ==========
@app.route("/api/optimize/metrics", methods=["GET"])
@require_auth
def get_optimize_metrics():
    days = request.args.get("days", 7, type=int)
    repo = SqliteMetricsRepository()
    items = repo.get_by_device_and_date_range(request.device_id, days)
    return jsonify([{
        "id": m.id, "device_id": m.device_id, "date": m.date,
        "total_generated": m.total_generated, "total_accepted": m.total_accepted,
        "total_modified": m.total_modified, "total_rejected": m.total_rejected,
    } for m in items])

@app.route("/api/optimize/analyze", methods=["POST"])
@require_auth
def analyze_optimize():
    repo = SqliteMetricsRepository()
    items = repo.get_by_device_and_date_range(request.device_id, 30)
    if not items:
        return jsonify({"status": "no_data", "message": "暂无足够数据进行分析"})

    total = sum(m.total_generated for m in items)
    accepted = sum(m.total_accepted for m in items)
    modified = sum(m.total_modified for m in items)
    rejected = sum(m.total_rejected for m in items)

    accept_rate = round(accepted / total, 3) if total > 0 else 0
    modify_rate = round(modified / total, 3) if total > 0 else 0
    reject_rate = round(rejected / total, 3) if total > 0 else 0

    suggestions = []
    if accept_rate < 0.5:
        suggestions.append("接受率较低，建议优化回复模板或调整模型参数")
    if modify_rate > 0.3:
        suggestions.append("修改率较高，建议检查模板变量是否正确替换")
    if reject_rate > 0.2:
        suggestions.append("拒绝率较高，建议增加知识库规则覆盖更多场景")

    return jsonify({
        "status": "ok", "period_days": 30, "total_generated": total,
        "total_accepted": accepted, "total_modified": modified, "total_rejected": rejected,
        "accept_rate": accept_rate, "modify_rate": modify_rate, "reject_rate": reject_rate,
        "suggestions": suggestions,
    })

# ========== 备份 API ==========
@app.route("/api/backup", methods=["GET"])
@rate_limit(max_requests=10, window_seconds=60)
@require_auth
def export_backup():
    device_id = request.device_id
    rule_repo = SqliteRuleRepository()
    model_repo = SqliteModelRepository()
    history_repo = SqliteHistoryRepository()
    feedback_repo = SqliteFeedbackRepository()
    metrics_repo = SqliteMetricsRepository()

    rules = rule_repo.get_by_device(device_id)
    models = model_repo.get_by_device(device_id)
    history_items, _ = history_repo.get_by_device(device_id, 1000, 0)
    feedback_items = feedback_repo.get_by_device(device_id, 1000, 0)
    metrics = metrics_repo.get_by_device_and_date_range(device_id, 365)

    return jsonify({
        "version": 1, "device_id": device_id,
        "rules": [_rule_to_dict(r) for r in rules],
        "models": [_model_to_dict(m) for m in models],
        "history": [{"id": h.id, "original_message": h.original_message,
                      "reply_content": h.reply_content} for h in history_items],
        "feedback": [{"id": f.id, "action": f.action} for f in feedback_items],
        "metrics": [{"date": m.date, "total_generated": m.total_generated} for m in metrics],
    })

@app.route("/api/backup/restore", methods=["POST"])
@rate_limit(max_requests=5, window_seconds=60)
@require_auth
def restore_backup():
    data = request.get_json() or {}
    backup = data.get("backup")
    if not isinstance(backup, dict):
        return jsonify({"error": "backup must be a JSON object"}), 400
    device_id = request.device_id

    rules_data = backup.get("rules", [])
    if rules_data and not isinstance(rules_data, list):
        return jsonify({"error": "rules must be a list"}), 400
    if rules_data:
        rules = [KeywordRule(
            device_id=device_id, keyword=r.get("keyword", ""),
            match_type=r.get("match_type", "CONTAINS"), reply_template=r.get("reply_template", ""),
            category=r.get("category", ""), target_type=r.get("target_type", "ALL"),
            target_names=r.get("target_names", []) if isinstance(r.get("target_names"), list) else json.loads(r.get("target_names", "[]")) if isinstance(r.get("target_names"), str) else [],
            priority=r.get("priority", 0), enabled=bool(r.get("enabled", True)),
        ) for r in rules_data]
        SqliteRuleRepository().batch_create(rules, device_id, "override")

    models_data = backup.get("models", [])
    if models_data:
        db = get_connection()
        db.execute("DELETE FROM model_configs WHERE device_id=?", (device_id,))
        db.commit()
        db.close()
        for m_data in models_data:
            config = ModelConfig(
                device_id=device_id, name=m_data.get("name", ""),
                model_type=m_data.get("model_type", "OPENAI"), model=m_data.get("model", ""),
                api_key=m_data.get("api_key", ""), api_endpoint=m_data.get("api_endpoint", ""),
                temperature=m_data.get("temperature", 0.7), max_tokens=m_data.get("max_tokens", 2000),
                is_default=bool(m_data.get("is_default", False)), enabled=bool(m_data.get("enabled", True)),
            )
            SqliteModelRepository().create(config)

    return jsonify({
        "status": "ok",
        "restored": {"rules": len(rules_data), "models": len(models_data)},
    })

# ========== Global Error Handlers ==========
@app.errorhandler(404)
def not_found(e):
    return jsonify({"error": "Not found"}), 404

@app.errorhandler(405)
def method_not_allowed(e):
    return jsonify({"error": "Method not allowed"}), 405

@app.errorhandler(500)
def internal_error(e):
    return jsonify({"error": "Internal server error"}), 500

# ========== 启动 ==========
if __name__ == "__main__":
    port = int(os.environ.get("PORT", 8080))
    app.run(host="0.0.0.0", port=port)
