import json
import web
import datetime
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

        params = web.input(page="1", page_size="20", search="")
        page = int(params.page)
        page_size = int(params.page_size)
        search = params.search.strip()
        offset = (page - 1) * page_size

        db = get_db()

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
