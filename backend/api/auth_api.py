import json
import os
import uuid
import web
from database import get_db
from auth import generate_token, extract_device_id, hash_password, verify_password, generate_user_token, extract_user_info


class AuthRegister:
    """POST /api/auth/register — 手机号+密码注册"""

    def POST(self):
        data = json.loads(web.data().decode())
        phone = data.get("phone", "").strip()
        password = data.get("password", "")

        if not phone or len(phone) != 11 or not phone.startswith("1"):
            web.header("Content-Type", "application/json")
            raise web.badrequest(json.dumps({"error": "请输入正确的手机号"}))
        if not password or len(password) < 6:
            web.header("Content-Type", "application/json")
            raise web.badrequest(json.dumps({"error": "密码至少6位"}))

        db = get_db()
        existing = db.execute("SELECT id FROM users WHERE phone = ?", (phone,)).fetchone()
        if existing:
            db.close()
            web.header("Content-Type", "application/json")
            raise web.badrequest(json.dumps({"error": "该手机号已注册"}))

        tenant_id = str(uuid.uuid4())
        password_hash = hash_password(password)

        admin_phone = os.environ.get("ADMIN_PHONE", "15558181817")
        admin_password = os.environ.get("ADMIN_PASSWORD", "Rd@202605")
        is_admin = 1 if (phone == admin_phone and password == admin_password) else 0

        db.execute(
            "INSERT INTO users (phone, password_hash, tenant_id, is_admin) VALUES (?, ?, ?, ?)",
            (phone, password_hash, tenant_id, is_admin)
        )
        db.commit()

        token = generate_user_token(tenant_id, tenant_id, is_admin)

        db.close()

        web.header("Content-Type", "application/json")
        return json.dumps({
            "token": token,
            "tenant_id": tenant_id,
            "phone": phone,
            "expires_in": 30 * 86400
        })


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
