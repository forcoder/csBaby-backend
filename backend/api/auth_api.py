import json
import uuid
import web
from database import get_db
from auth import generate_token, extract_device_id


class AuthRegister:
    """POST /api/auth/register"""

    def POST(self):
        data = json.loads(web.data().decode())
        device_id = str(uuid.uuid4())
        name = data.get("name", "")
        platform = data.get("platform", "android")
        app_version = data.get("app_version", "")

        token = generate_token(device_id)

        db = get_db()
        db.execute(
            "INSERT INTO devices (id, token, name, platform, app_version) VALUES (?, ?, ?, ?, ?)",
            (device_id, token, name, platform, app_version)
        )
        db.commit()
        db.close()

        web.header("Content-Type", "application/json")
        return json.dumps({
            "device_id": device_id,
            "token": token,
            "expires_in": 30 * 86400
        })


class AuthHeartbeat:
    """POST /api/auth/heartbeat"""

    def POST(self):
        device_id = extract_device_id()
        if not device_id:
            web.header("Content-Type", "application/json")
            raise web.unauthorized(json.dumps({"error": "Unauthorized"}))

        db = get_db()
        db.execute(
            "UPDATE devices SET last_heartbeat = CURRENT_TIMESTAMP WHERE id = ?",
            (device_id,)
        )
        db.commit()
        db.close()

        web.header("Content-Type", "application/json")
        return json.dumps({"status": "ok"})
