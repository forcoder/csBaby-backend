import os
import json
import uuid
import datetime
from flask import Flask, request, jsonify, g
from database import init_db, get_db, dict_from_row
from auth import (
    hash_password, verify_password,
    generate_token, verify_token, extract_device_id,
    generate_user_token, extract_user_info,
)
from config import HOST, PORT

app = Flask(__name__)


def get_db_conn():
    """获取当前请求的数据库连接"""
    if "db" not in g:
        g.db = get_db()
    return g.db


@app.teardown_appcontext
def close_db(exception):
    """请求结束时关闭数据库连接"""
    db = g.pop("db", None)
    if db is not None:
        db.close()


# ========== CORS ==========

@app.after_request
def add_cors_headers(response):
    response.headers["Access-Control-Allow-Origin"] = "*"
    response.headers["Access-Control-Allow-Methods"] = "GET, POST, PUT, DELETE, OPTIONS"
    response.headers["Access-Control-Allow-Headers"] = "Content-Type, Authorization"
    return response


@app.before_request
def handle_options():
    if request.method == "OPTIONS":
        return "", 204


# ========== Health ==========


@app.route("/health", methods=["GET"])
def health_check():
    return jsonify({"status": "ok", "service": "csBaby-api"})


# ========== Auth ==========


@app.route("/api/auth/register", methods=["POST"])
def auth_register():
    data = request.get_json(force=True)
    phone = data.get("phone", "").strip()
    password = data.get("password", "")

    if not phone or len(phone) != 11 or not phone.startswith("1"):
        return jsonify({"error": "请输入正确的手机号"}), 400
    if not password or len(password) < 6:
        return jsonify({"error": "密码至少6位"}), 400

    db = get_db_conn()
    existing = db.execute("SELECT id FROM users WHERE phone = ?", (phone,)).fetchone()
    if existing:
        db.close()
        return jsonify({"error": "该手机号已注册"}), 400

    tenant_id = str(uuid.uuid4())
    password_hash = hash_password(password)

    admin_phone = os.environ.get("ADMIN_PHONE", "15558181817")
    admin_password = os.environ.get("ADMIN_PASSWORD", "Rd@202605")
    is_admin = 1 if (phone == admin_phone and password == admin_password) else 0

    db.execute(
        "INSERT INTO users (phone, password_hash, tenant_id, is_admin) VALUES (?, ?, ?, ?)",
        (phone, password_hash, tenant_id, is_admin),
    )

    # 自动复制默认模型配置
    default_model = db.execute(
        "SELECT * FROM tenant_default_models WHERE tenant_id = '_global' AND enabled = 1"
    ).fetchone()
    if default_model:
        dm = dict(default_model)
        db.execute(
            """INSERT INTO model_configs
               (device_id, name, model_type, model, api_key, api_endpoint, temperature, max_tokens, is_default, enabled)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1, 1)""",
            (
                tenant_id, dm.get("name", ""), dm.get("model_type", "OPENAI"),
                dm.get("model", ""), dm.get("api_key", ""),
                dm.get("api_endpoint", ""), dm.get("temperature", 0.7),
                dm.get("max_tokens", 2000),
            ),
        )

    db.commit()

    token = generate_user_token(tenant_id, tenant_id, is_admin)

    return jsonify({
        "token": token,
        "tenant_id": tenant_id,
        "phone": phone,
        "expires_in": 30 * 86400,
    })


@app.route("/api/auth/login", methods=["POST"])
def auth_login():
    data = request.get_json(force=True)
    phone = data.get("phone", "").strip()
    password = data.get("password", "")

    if not phone or not password:
        return jsonify({"error": "请输入手机号和密码"}), 400

    db = get_db_conn()
    user = db.execute(
        "SELECT id, password_hash, tenant_id, is_admin, is_active FROM users WHERE phone = ?",
        (phone,),
    ).fetchone()

    if not user:
        return jsonify({"error": "手机号或密码错误"}), 401

    user_id, password_hash, tenant_id, is_admin, is_active = user

    if not is_active:
        return jsonify({"error": "账号已被禁用"}), 401
    if not verify_password(password, password_hash):
        return jsonify({"error": "手机号或密码错误"}), 401

    token = generate_user_token(tenant_id, tenant_id, is_admin)

    return jsonify({
        "token": token,
        "tenant_id": tenant_id,
        "phone": phone,
        "expires_in": 30 * 86400,
    })


@app.route("/api/auth/change_password", methods=["POST"])
def auth_change_password():
    info = extract_user_info()
    if not info:
        return jsonify({"error": "Unauthorized"}), 401

    data = request.get_json(force=True)
    old_password = data.get("old_password", "")
    new_password = data.get("new_password", "")

    if not old_password or not new_password:
        return jsonify({"error": "请填写完整"}), 400
    if len(new_password) < 6:
        return jsonify({"error": "新密码至少6位"}), 400

    db = get_db_conn()
    user = db.execute(
        "SELECT password_hash FROM users WHERE tenant_id = ?", (info["tenant_id"],)
    ).fetchone()

    if not user or not verify_password(old_password, user[0]):
        return jsonify({"error": "原密码错误"}), 400

    new_hash = hash_password(new_password)
    db.execute("UPDATE users SET password_hash = ? WHERE tenant_id = ?", (new_hash, info["tenant_id"]))
    db.commit()

    return jsonify({"status": "ok"})


@app.route("/api/auth/heartbeat", methods=["POST"])
def auth_heartbeat():
    info = extract_user_info()
    if not info:
        return jsonify({"error": "Unauthorized"}), 401

    db = get_db_conn()
    db.execute(
        "UPDATE devices SET last_heartbeat = CURRENT_TIMESTAMP WHERE id = ?",
        (info["device_id"],),
    )
    db.commit()

    return jsonify({"status": "ok"})


# ========== Admin ==========


def require_admin():
    """验证管理员权限，返回 user_info 或抛出异常"""
    info = extract_user_info()
    if not info:
        return None, (jsonify({"error": "Unauthorized"}), 401)
    if not info.get("is_admin"):
        return None, (jsonify({"error": "需要管理员权限"}), 403)
    return info, None


@app.route("/api/admin/login", methods=["POST"])
def admin_login():
    data = request.get_json(force=True)
    phone = data.get("phone", "").strip()
    password = data.get("password", "")

    db = get_db_conn()
    user = db.execute(
        "SELECT id, password_hash, tenant_id, is_admin, is_active FROM users WHERE phone = ?",
        (phone,),
    ).fetchone()

    if not user:
        return jsonify({"error": "手机号或密码错误"}), 401

    user_id, password_hash, tenant_id, is_admin, is_active = user

    if not is_admin:
        return jsonify({"error": "需要管理员权限"}), 403
    if not is_active:
        return jsonify({"error": "账号已被禁用"}), 401
    if not verify_password(password, password_hash):
        return jsonify({"error": "手机号或密码错误"}), 401

    token = generate_user_token(tenant_id, tenant_id, is_admin)

    return jsonify({
        "token": token,
        "tenant_id": tenant_id,
        "phone": phone,
        "is_admin": is_admin,
        "expires_in": 30 * 86400,
    })


@app.route("/api/admin/tenants", methods=["GET"])
def admin_tenants():
    info, err = require_admin()
    if err:
        return err

    page = request.args.get("page", 1, type=int)
    page_size = request.args.get("page_size", 20, type=int)
    search = request.args.get("search", "").strip()
    offset = (page - 1) * page_size

    db = get_db_conn()

    where_clause = ""
    query_params = []
    if search:
        where_clause = "WHERE phone LIKE ?"
        query_params.append(f"%{search}%")

    total_row = db.execute(
        f"SELECT COUNT(*) FROM users {where_clause}", query_params
    ).fetchone()
    total = total_row[0]

    rows = db.execute(
        f"""SELECT u.id, u.phone, u.tenant_id, u.is_admin, u.is_active, u.created_at,
            (SELECT COUNT(*) FROM keyword_rules WHERE device_id = u.tenant_id) as rule_count,
            (SELECT COUNT(*) FROM reply_history WHERE device_id = u.tenant_id) as history_count,
            (SELECT MAX(created_at) FROM reply_history WHERE device_id = u.tenant_id) as last_active
            FROM users u {where_clause}
            ORDER BY u.created_at DESC LIMIT ? OFFSET ?""",
        query_params + [page_size, offset],
    ).fetchall()

    return jsonify({
        "total": total,
        "page": page,
        "page_size": page_size,
        "items": [dict_from_row(r) for r in rows],
    })


@app.route("/api/admin/tenants/<tenant_id>", methods=["GET", "PUT"])
def admin_tenant_detail(tenant_id):
    info, err = require_admin()
    if err:
        return err

    db = get_db_conn()

    if request.method == "GET":
        user = db.execute(
            "SELECT id, phone, tenant_id, is_admin, is_active, created_at FROM users WHERE tenant_id = ?",
            (tenant_id,),
        ).fetchone()

        if not user:
            return jsonify({"error": "Tenant not found"}), 404

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

        result = dict_from_row(user)
        result["rule_count"] = rule_count
        result["model_count"] = model_count
        result["history_count"] = history_count
        result["feedback_count"] = feedback_count
        result["last_active"] = last_active

        return jsonify(result)

    elif request.method == "PUT":
        data = request.get_json(force=True)
        is_active = data.get("is_active")

        user = db.execute("SELECT id FROM users WHERE tenant_id = ?", (tenant_id,)).fetchone()
        if not user:
            return jsonify({"error": "Tenant not found"}), 404

        if is_active is not None:
            db.execute("UPDATE users SET is_active = ? WHERE tenant_id = ?", (is_active, tenant_id))

        db.commit()
        return jsonify({"status": "ok", "tenant_id": tenant_id, "is_active": is_active})


@app.route("/api/admin/tenants/<tenant_id>/default-model", methods=["GET", "POST", "DELETE"])
def admin_tenant_default_model(tenant_id):
    info, err = require_admin()
    if err:
        return err

    db = get_db_conn()

    if request.method == "GET":
        row = db.execute(
            "SELECT * FROM tenant_default_models WHERE tenant_id = ?", (tenant_id,)
        ).fetchone()
        if not row:
            return jsonify({"error": "未配置默认模型"}), 404
        return jsonify(dict_from_row(row))

    elif request.method == "POST":
        data = request.get_json(force=True)
        existing = db.execute(
            "SELECT id FROM tenant_default_models WHERE tenant_id = ?", (tenant_id,)
        ).fetchone()

        if existing:
            db.execute(
                """UPDATE tenant_default_models SET
                   name=?, model_type=?, model=?, api_key=?,
                   api_endpoint=?, temperature=?, max_tokens=?, enabled=?,
                   updated_at=CURRENT_TIMESTAMP
                   WHERE tenant_id=?""",
                (
                    data.get("name", ""), data.get("model_type", "OPENAI"),
                    data.get("model", ""), data.get("api_key", ""),
                    data.get("api_endpoint", ""), data.get("temperature", 0.7),
                    data.get("max_tokens", 2000), data.get("enabled", 1),
                    tenant_id,
                ),
            )
        else:
            db.execute(
                """INSERT INTO tenant_default_models
                   (tenant_id, name, model_type, model, api_key, api_endpoint, temperature, max_tokens, enabled)
                   VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                (
                    tenant_id, data.get("name", ""), data.get("model_type", "OPENAI"),
                    data.get("model", ""), data.get("api_key", ""),
                    data.get("api_endpoint", ""), data.get("temperature", 0.7),
                    data.get("max_tokens", 2000), data.get("enabled", 1),
                ),
            )
        db.commit()

        row = db.execute(
            "SELECT * FROM tenant_default_models WHERE tenant_id = ?", (tenant_id,)
        ).fetchone()
        return jsonify(dict_from_row(row))

    elif request.method == "DELETE":
        db.execute("DELETE FROM tenant_default_models WHERE tenant_id = ?", (tenant_id,))
        db.commit()
        return jsonify({"status": "ok"})


@app.route("/api/admin/stats", methods=["GET"])
def admin_stats():
    info, err = require_admin()
    if err:
        return err

    db = get_db_conn()
    total_tenants = db.execute("SELECT COUNT(*) FROM users").fetchone()[0]
    active_tenants = db.execute("SELECT COUNT(*) FROM users WHERE is_active = 1").fetchone()[0]
    total_rules = db.execute("SELECT COUNT(*) FROM keyword_rules").fetchone()[0]
    total_history = db.execute("SELECT COUNT(*) FROM reply_history").fetchone()[0]

    today = datetime.date.today().strftime("%Y-%m-%d")
    today_row = db.execute(
        "SELECT COUNT(*) FROM reply_history WHERE DATE(created_at) = ?", (today,)
    ).fetchone()
    today_history = today_row[0] if today_row else 0

    return jsonify({
        "total_tenants": total_tenants,
        "active_tenants": active_tenants,
        "total_rules": total_rules,
        "total_history": total_history,
        "today_history": today_history,
    })


# ========== Admin Rules (知识库管理) ==========


@app.route("/api/admin/tenants/<tenant_id>/rules", methods=["GET"])
def admin_rules_list(tenant_id):
    """管理员获取指定租户的知识库规则列表"""
    info, err = require_admin()
    if err:
        return err

    db = get_db_conn()
    rows = db.execute(
        "SELECT * FROM keyword_rules WHERE device_id = ? ORDER BY priority DESC, id DESC",
        (tenant_id,),
    ).fetchall()
    return jsonify([dict_from_row(r) for r in rows])


@app.route("/api/admin/tenants/<tenant_id>/rules", methods=["POST"])
def admin_rules_create(tenant_id):
    """管理员为指定租户创建知识库规则"""
    info, err = require_admin()
    if err:
        return err

    data = request.get_json(force=True)
    db = get_db_conn()
    db.execute(
        """INSERT INTO keyword_rules
           (device_id, keyword, match_type, reply_template, category, target_type, target_names, priority, enabled)
           VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""",
        (
            tenant_id, data.get("keyword", ""), data.get("match_type", "CONTAINS"),
            data.get("reply_template", ""), data.get("category", ""),
            data.get("target_type", "ALL"), data.get("target_names", "[]"),
            data.get("priority", 0), data.get("enabled", 1),
        ),
    )
    db.commit()
    return jsonify({"status": "ok", "id": db.execute("SELECT last_insert_rowid()").fetchone()[0]})


@app.route("/api/admin/tenants/<tenant_id>/rules/<int:rule_id>", methods=["GET", "PUT", "DELETE"])
def admin_rules_detail(tenant_id, rule_id):
    """管理员获取/更新/删除指定租户的某条规则"""
    info, err = require_admin()
    if err:
        return err

    db = get_db_conn()

    if request.method == "GET":
        row = db.execute(
            "SELECT * FROM keyword_rules WHERE id = ? AND device_id = ?", (rule_id, tenant_id)
        ).fetchone()
        if not row:
            return jsonify({"error": "Not found"}), 404
        return jsonify(dict_from_row(row))

    elif request.method == "PUT":
        data = request.get_json(force=True)
        db.execute(
            """UPDATE keyword_rules SET keyword=?, match_type=?, reply_template=?,
               category=?, target_type=?, target_names=?, priority=?, enabled=?
               WHERE id=? AND device_id=?""",
            (
                data.get("keyword", ""), data.get("match_type", "CONTAINS"),
                data.get("reply_template", ""), data.get("category", ""),
                data.get("target_type", "ALL"), data.get("target_names", "[]"),
                data.get("priority", 0), data.get("enabled", 1),
                rule_id, tenant_id,
            ),
        )
        db.commit()
        return jsonify({"status": "ok"})

    elif request.method == "DELETE":
        db.execute("DELETE FROM keyword_rules WHERE id = ? AND device_id = ?", (rule_id, tenant_id))
        db.commit()
        return jsonify({"status": "ok"})


@app.route("/api/admin/tenants/<tenant_id>/rules/batch", methods=["POST"])
def admin_rules_batch(tenant_id):
    """管理员批量导入规则（覆盖模式：先删除再插入）"""
    info, err = require_admin()
    if err:
        return err

    data = request.get_json(force=True)
    rules = data.get("rules", [])
    db = get_db_conn()

    db.execute("DELETE FROM keyword_rules WHERE device_id = ?", (tenant_id,))
    for rule in rules:
        db.execute(
            """INSERT INTO keyword_rules
               (device_id, keyword, match_type, reply_template, category, target_type, target_names, priority, enabled)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""",
            (
                tenant_id, rule.get("keyword", ""), rule.get("match_type", "CONTAINS"),
                rule.get("reply_template", ""), rule.get("category", ""),
                rule.get("target_type", "ALL"), rule.get("target_names", "[]"),
                rule.get("priority", 0), rule.get("enabled", 1),
            ),
        )
    db.commit()
    return jsonify({"status": "ok", "count": len(rules)})


# ========== Rules ==========


@app.route("/api/rules", methods=["GET"])
def rules_list():
    device_id = extract_device_id()
    if not device_id:
        return jsonify({"error": "Unauthorized"}), 401

    db = get_db_conn()
    rows = db.execute(
        "SELECT * FROM keyword_rules WHERE device_id = ? ORDER BY priority DESC, id DESC",
        (device_id,),
    ).fetchall()
    return jsonify([dict_from_row(r) for r in rows])


@app.route("/api/rules", methods=["POST"])
def rules_create():
    device_id = extract_device_id()
    if not device_id:
        return jsonify({"error": "Unauthorized"}), 401

    data = request.get_json(force=True)
    db = get_db_conn()
    db.execute(
        """INSERT INTO keyword_rules
           (device_id, keyword, match_type, reply_template, category, target_type, target_names, priority, enabled)
           VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""",
        (
            device_id, data.get("keyword", ""), data.get("match_type", "CONTAINS"),
            data.get("reply_template", ""), data.get("category", ""),
            data.get("target_type", "ALL"), data.get("target_names", "[]"),
            data.get("priority", 0), data.get("enabled", 1),
        ),
    )
    db.commit()
    return jsonify({"status": "ok", "id": db.execute("SELECT last_insert_rowid()").fetchone()[0]})


@app.route("/api/rules/<int:rule_id>", methods=["GET", "PUT", "DELETE"])
def rules_detail(rule_id):
    device_id = extract_device_id()
    if not device_id:
        return jsonify({"error": "Unauthorized"}), 401

    db = get_db_conn()

    if request.method == "GET":
        row = db.execute(
            "SELECT * FROM keyword_rules WHERE id = ? AND device_id = ?", (rule_id, device_id)
        ).fetchone()
        if not row:
            return jsonify({"error": "Not found"}), 404
        return jsonify(dict_from_row(row))

    elif request.method == "PUT":
        data = request.get_json(force=True)
        db.execute(
            """UPDATE keyword_rules SET keyword=?, match_type=?, reply_template=?,
               category=?, target_type=?, target_names=?, priority=?, enabled=?
               WHERE id=? AND device_id=?""",
            (
                data.get("keyword", ""), data.get("match_type", "CONTAINS"),
                data.get("reply_template", ""), data.get("category", ""),
                data.get("target_type", "ALL"), data.get("target_names", "[]"),
                data.get("priority", 0), data.get("enabled", 1),
                rule_id, device_id,
            ),
        )
        db.commit()
        return jsonify({"status": "ok"})

    elif request.method == "DELETE":
        db.execute("DELETE FROM keyword_rules WHERE id = ? AND device_id = ?", (rule_id, device_id))
        db.commit()
        return jsonify({"status": "ok"})


@app.route("/api/rules/batch", methods=["POST"])
def rules_batch():
    device_id = extract_device_id()
    if not device_id:
        return jsonify({"error": "Unauthorized"}), 401

    data = request.get_json(force=True)
    rules = data.get("rules", [])
    db = get_db_conn()

    # 先删除该设备的所有规则，再批量插入
    db.execute("DELETE FROM keyword_rules WHERE device_id = ?", (device_id,))
    for rule in rules:
        db.execute(
            """INSERT INTO keyword_rules
               (device_id, keyword, match_type, reply_template, category, target_type, target_names, priority, enabled)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""",
            (
                device_id, rule.get("keyword", ""), rule.get("match_type", "CONTAINS"),
                rule.get("reply_template", ""), rule.get("category", ""),
                rule.get("target_type", "ALL"), rule.get("target_names", "[]"),
                rule.get("priority", 0), rule.get("enabled", 1),
            ),
        )
    db.commit()
    return jsonify({"status": "ok", "count": len(rules)})


# ========== Models ==========


@app.route("/api/models", methods=["GET"])
def models_list():
    device_id = extract_device_id()
    if not device_id:
        return jsonify({"error": "Unauthorized"}), 401

    db = get_db_conn()
    rows = db.execute(
        "SELECT * FROM model_configs WHERE device_id = ?", (device_id,)
    ).fetchall()
    return jsonify([dict_from_row(r) for r in rows])


@app.route("/api/models", methods=["POST"])
def models_create():
    device_id = extract_device_id()
    if not device_id:
        return jsonify({"error": "Unauthorized"}), 401

    data = request.get_json(force=True)
    db = get_db_conn()
    db.execute(
        """INSERT INTO model_configs
           (device_id, name, model_type, model, api_key, api_endpoint, temperature, max_tokens, is_default, enabled)
           VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
        (
            device_id, data.get("name", ""), data.get("model_type", ""),
            data.get("model", ""), data.get("api_key", ""),
            data.get("api_endpoint", ""), data.get("temperature", 0.7),
            data.get("max_tokens", 2000), data.get("is_default", 0), data.get("enabled", 1),
        ),
    )
    db.commit()
    return jsonify({"status": "ok", "id": db.execute("SELECT last_insert_rowid()").fetchone()[0]})


@app.route("/api/models/<int:model_id>", methods=["GET", "PUT", "DELETE"])
def models_detail(model_id):
    device_id = extract_device_id()
    if not device_id:
        return jsonify({"error": "Unauthorized"}), 401

    db = get_db_conn()

    if request.method == "GET":
        row = db.execute(
            "SELECT * FROM model_configs WHERE id = ? AND device_id = ?", (model_id, device_id)
        ).fetchone()
        if not row:
            return jsonify({"error": "Not found"}), 404
        return jsonify(dict_from_row(row))

    elif request.method == "PUT":
        data = request.get_json(force=True)
        db.execute(
            """UPDATE model_configs SET name=?, model_type=?, model=?, api_key=?,
               api_endpoint=?, temperature=?, max_tokens=?, is_default=?, enabled=?
               WHERE id=? AND device_id=?""",
            (
                data.get("name", ""), data.get("model_type", ""),
                data.get("model", ""), data.get("api_key", ""),
                data.get("api_endpoint", ""), data.get("temperature", 0.7),
                data.get("max_tokens", 2000), data.get("is_default", 0), data.get("enabled", 1),
                model_id, device_id,
            ),
        )
        db.commit()
        return jsonify({"status": "ok"})

    elif request.method == "DELETE":
        db.execute("DELETE FROM model_configs WHERE id = ? AND device_id = ?", (model_id, device_id))
        db.commit()
        return jsonify({"status": "ok"})


@app.route("/api/models/<int:model_id>/test", methods=["POST"])
def models_test(model_id):
    device_id = extract_device_id()
    if not device_id:
        return jsonify({"error": "Unauthorized"}), 401
    return jsonify({"status": "ok", "message": "test endpoint"})


# ========== AI ==========


@app.route("/api/ai/generate", methods=["POST"])
def ai_generate():
    device_id = extract_device_id()
    if not device_id:
        return jsonify({"error": "Unauthorized"}), 401

    data = request.get_json(force=True)
    message = data.get("message", "")
    return jsonify({
        "reply": "AI 回复占位",
        "model": "default",
        "confidence": 0.9,
        "response_time_ms": 100,
    })


@app.route("/api/ai/chat", methods=["POST"])
def ai_chat():
    device_id = extract_device_id()
    if not device_id:
        return jsonify({"error": "Unauthorized"}), 401

    data = request.get_json(force=True)
    return jsonify({"reply": "AI 聊天回复占位"})


# ========== History ==========


@app.route("/api/history", methods=["GET"])
def history_list():
    device_id = extract_device_id()
    if not device_id:
        return jsonify({"error": "Unauthorized"}), 401

    db = get_db_conn()
    rows = db.execute(
        "SELECT * FROM reply_history WHERE device_id = ? ORDER BY created_at DESC LIMIT 100",
        (device_id,),
    ).fetchall()
    return jsonify([dict_from_row(r) for r in rows])


@app.route("/api/history", methods=["POST"])
def history_create():
    device_id = extract_device_id()
    if not device_id:
        return jsonify({"error": "Unauthorized"}), 401

    data = request.get_json(force=True)
    db = get_db_conn()
    db.execute(
        """INSERT INTO reply_history
           (device_id, original_message, reply_content, source, model_used, confidence, response_time_ms, platform, customer_name, house_name)
           VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
        (
            device_id, data.get("original_message", ""), data.get("reply_content", ""),
            data.get("source", "ai"), data.get("model_used", ""),
            data.get("confidence", 0), data.get("response_time_ms", 0),
            data.get("platform", ""), data.get("customer_name", ""), data.get("house_name", ""),
        ),
    )
    db.commit()
    return jsonify({"status": "ok", "id": db.execute("SELECT last_insert_rowid()").fetchone()[0]})


# ========== Feedback ==========


@app.route("/api/feedback", methods=["GET"])
def feedback_list():
    device_id = extract_device_id()
    if not device_id:
        return jsonify({"error": "Unauthorized"}), 401

    db = get_db_conn()
    rows = db.execute(
        "SELECT * FROM feedback WHERE device_id = ? ORDER BY created_at DESC LIMIT 100",
        (device_id,),
    ).fetchall()
    return jsonify([dict_from_row(r) for r in rows])


@app.route("/api/feedback", methods=["POST"])
def feedback_create():
    device_id = extract_device_id()
    if not device_id:
        return jsonify({"error": "Unauthorized"}), 401

    data = request.get_json(force=True)
    db = get_db_conn()
    db.execute(
        "INSERT INTO feedback (device_id, reply_history_id, action, modified_text, rating, comment) VALUES (?, ?, ?, ?, ?, ?)",
        (
            device_id, data.get("reply_history_id"), data.get("action", ""),
            data.get("modified_text", ""), data.get("rating", 0), data.get("comment", ""),
        ),
    )
    db.commit()
    return jsonify({"status": "ok", "id": db.execute("SELECT last_insert_rowid()").fetchone()[0]})


# ========== Optimize ==========


@app.route("/api/optimize/metrics", methods=["GET"])
def optimize_metrics():
    device_id = extract_device_id()
    if not device_id:
        return jsonify({"error": "Unauthorized"}), 401

    db = get_db_conn()
    rows = db.execute(
        "SELECT * FROM optimization_metrics WHERE device_id = ? ORDER BY date DESC LIMIT 30",
        (device_id,),
    ).fetchall()
    return jsonify([dict_from_row(r) for r in rows])


@app.route("/api/optimize/metrics", methods=["POST"])
def optimize_metrics_create():
    device_id = extract_device_id()
    if not device_id:
        return jsonify({"error": "Unauthorized"}), 401

    data = request.get_json(force=True)
    db = get_db_conn()
    db.execute(
        """INSERT OR REPLACE INTO optimization_metrics
           (device_id, date, total_generated, total_accepted, total_modified, total_rejected, avg_confidence, avg_response_time_ms)
           VALUES (?, ?, ?, ?, ?, ?, ?, ?)""",
        (
            device_id, data.get("date", ""), data.get("total_generated", 0),
            data.get("total_accepted", 0), data.get("total_modified", 0),
            data.get("total_rejected", 0), data.get("avg_confidence", 0),
            data.get("avg_response_time_ms", 0),
        ),
    )
    db.commit()
    return jsonify({"status": "ok"})


@app.route("/api/optimize/analyze", methods=["POST"])
def optimize_analyze():
    device_id = extract_device_id()
    if not device_id:
        return jsonify({"error": "Unauthorized"}), 401
    return jsonify({"status": "ok", "analysis": {}})


# ========== Backup ==========


@app.route("/api/backup", methods=["GET"])
def backup_export():
    device_id = extract_device_id()
    if not device_id:
        return jsonify({"error": "Unauthorized"}), 401

    db = get_db_conn()
    rules = [dict_from_row(r) for r in db.execute(
        "SELECT * FROM keyword_rules WHERE device_id = ?", (device_id,)
    ).fetchall()]
    models = [dict_from_row(r) for r in db.execute(
        "SELECT * FROM model_configs WHERE device_id = ?", (device_id,)
    ).fetchall()]

    return jsonify({
        "version": "1.0",
        "device_id": device_id,
        "exported_at": datetime.datetime.now().isoformat(),
        "rules": rules,
        "models": models,
    })


@app.route("/api/backup/restore", methods=["POST"])
def backup_restore():
    device_id = extract_device_id()
    if not device_id:
        return jsonify({"error": "Unauthorized"}), 401

    data = request.get_json(force=True)
    db = get_db_conn()

    rules = data.get("rules", [])
    if rules:
        db.execute("DELETE FROM keyword_rules WHERE device_id = ?", (device_id,))
        for rule in rules:
            db.execute(
                """INSERT INTO keyword_rules
                   (device_id, keyword, match_type, reply_template, category, target_type, target_names, priority, enabled)
                   VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                (
                    device_id, rule.get("keyword", ""), rule.get("match_type", "CONTAINS"),
                    rule.get("reply_template", ""), rule.get("category", ""),
                    rule.get("target_type", "ALL"), rule.get("target_names", "[]"),
                    rule.get("priority", 0), rule.get("enabled", 1),
                ),
            )
        db.commit()

    models = data.get("models", [])
    if models:
        db.execute("DELETE FROM model_configs WHERE device_id = ?", (device_id,))
        for model in models:
            db.execute(
                """INSERT INTO model_configs
                   (device_id, name, model_type, model, api_key, api_endpoint, temperature, max_tokens, is_default, enabled)
                   VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                (
                    device_id, model.get("name", ""), model.get("model_type", ""),
                    model.get("model", ""), model.get("api_key", ""),
                    model.get("api_endpoint", ""), model.get("temperature", 0.7),
                    model.get("max_tokens", 2000), model.get("is_default", 0), model.get("enabled", 1),
                ),
            )
        db.commit()

    return jsonify({"status": "ok", "rules_imported": len(rules), "models_imported": len(models)})


# ========== Entry Point ==========

# gunicorn 启动时初始化数据库
init_db()

if __name__ == "__main__":
    port = int(os.environ.get("PORT", PORT))
    app.run(host=HOST, port=port, debug=False)