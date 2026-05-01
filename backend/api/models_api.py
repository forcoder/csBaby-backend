import json
import web
from database import get_db, dict_from_row
from auth import extract_device_id
from services.ai_service import ai_service


class ModelsAPI:
    """GET/POST /api/models - 获取所有模型配置 / 创建模型配置"""

    def GET(self):
        device_id = extract_device_id()
        if not device_id:
            raise web.unauthorized(json.dumps({"error": "Unauthorized"}))

        db = get_db()
        rows = db.execute(
            "SELECT * FROM model_configs WHERE device_id = ? ORDER BY is_default DESC, id ASC",
            (device_id,)
        ).fetchall()
        db.close()

        web.header("Content-Type", "application/json")
        return json.dumps([dict_from_row(r) for r in rows])

    def POST(self):
        device_id = extract_device_id()
        if not device_id:
            raise web.unauthorized(json.dumps({"error": "Unauthorized"}))

        data = json.loads(web.data().decode())
        db = get_db()

        # 如果设为默认，先把其他模型取消默认
        if data.get("is_default"):
            db.execute("UPDATE model_configs SET is_default = 0 WHERE device_id = ?", (device_id,))

        cursor = db.execute(
            """INSERT INTO model_configs
            (device_id, name, model_type, model, api_key, api_endpoint, temperature, max_tokens, is_default, enabled)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
            (
                device_id,
                data.get("name", ""),
                data.get("model_type", "OPENAI"),
                data.get("model", "gpt-4o"),
                data.get("api_key", ""),
                data.get("api_endpoint", ""),
                data.get("temperature", 0.7),
                data.get("max_tokens", 2000),
                data.get("is_default", 0),
                data.get("enabled", 1)
            )
        )
        model_id = cursor.lastrowid
        db.commit()

        row = db.execute("SELECT * FROM model_configs WHERE id = ?", (model_id,)).fetchone()
        db.close()

        web.header("Content-Type", "application/json")
        return json.dumps(dict_from_row(row))


class ModelDetailAPI:
    """GET/PUT/DELETE /api/models/{id}"""

    def GET(self, model_id):
        device_id = extract_device_id()
        if not device_id:
            raise web.unauthorized(json.dumps({"error": "Unauthorized"}))

        db = get_db()
        row = db.execute(
            "SELECT * FROM model_configs WHERE id = ? AND device_id = ?",
            (model_id, device_id)
        ).fetchone()
        db.close()

        if not row:
            raise web.notfound(json.dumps({"error": "Model not found"}))

        web.header("Content-Type", "application/json")
        return json.dumps(dict_from_row(row))

    def PUT(self, model_id):
        device_id = extract_device_id()
        if not device_id:
            raise web.unauthorized(json.dumps({"error": "Unauthorized"}))

        data = json.loads(web.data().decode())
        db = get_db()

        if data.get("is_default"):
            db.execute("UPDATE model_configs SET is_default = 0 WHERE device_id = ?", (device_id,))

        db.execute(
            """UPDATE model_configs SET
            name = ?, model_type = ?, model = ?, api_key = ?, api_endpoint = ?,
            temperature = ?, max_tokens = ?, is_default = ?, enabled = ?,
            updated_at = CURRENT_TIMESTAMP
            WHERE id = ? AND device_id = ?""",
            (
                data.get("name", ""),
                data.get("model_type", "OPENAI"),
                data.get("model", ""),
                data.get("api_key", ""),
                data.get("api_endpoint", ""),
                data.get("temperature", 0.7),
                data.get("max_tokens", 2000),
                data.get("is_default", 0),
                data.get("enabled", 1),
                model_id, device_id
            )
        )
        db.commit()

        row = db.execute("SELECT * FROM model_configs WHERE id = ?", (model_id,)).fetchone()
        db.close()

        web.header("Content-Type", "application/json")
        return json.dumps(dict_from_row(row))

    def DELETE(self, model_id):
        device_id = extract_device_id()
        if not device_id:
            raise web.unauthorized(json.dumps({"error": "Unauthorized"}))

        db = get_db()
        db.execute("DELETE FROM model_configs WHERE id = ? AND device_id = ?", (model_id, device_id))
        db.commit()
        db.close()

        web.header("Content-Type", "application/json")
        return json.dumps({"status": "deleted", "id": int(model_id)})


class ModelTestAPI:
    """POST /api/models/{id}/test - 测试模型连接"""

    def POST(self, model_id):
        device_id = extract_device_id()
        if not device_id:
            raise web.unauthorized(json.dumps({"error": "Unauthorized"}))

        db = get_db()
        row = db.execute(
            "SELECT * FROM model_configs WHERE id = ? AND device_id = ?",
            (model_id, device_id)
        ).fetchone()
        db.close()

        if not row:
            raise web.notfound(json.dumps({"error": "Model not found"}))

        config = dict_from_row(row)
        result = ai_service.test_connection(config)

        web.header("Content-Type", "application/json")
        return json.dumps(result)
