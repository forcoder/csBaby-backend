import json
import web
from database import get_db, dict_from_row
from auth import extract_device_id


class HistoryAPI:
    """GET/POST /api/history"""

    def GET(self):
        device_id = extract_device_id()
        if not device_id:
            raise web.unauthorized(json.dumps({"error": "Unauthorized"}))

        limit = int(web.input(limit=50).limit)
        offset = int(web.input(offset=0).offset)

        db = get_db()
        rows = db.execute(
            "SELECT * FROM reply_history WHERE device_id = ? ORDER BY created_at DESC LIMIT ? OFFSET ?",
            (device_id, limit, offset)
        ).fetchall()

        total = db.execute(
            "SELECT COUNT(*) FROM reply_history WHERE device_id = ?",
            (device_id,)
        ).fetchone()[0]
        db.close()

        web.header("Content-Type", "application/json")
        return json.dumps({
            "items": [dict_from_row(r) for r in rows],
            "total": total,
            "limit": limit,
            "offset": offset
        })

    def POST(self):
        device_id = extract_device_id()
        if not device_id:
            raise web.unauthorized(json.dumps({"error": "Unauthorized"}))

        data = json.loads(web.data().decode())
        db = get_db()
        cursor = db.execute(
            """INSERT INTO reply_history
            (device_id, original_message, reply_content, source, model_used, confidence, response_time_ms, platform, customer_name, house_name)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
            (
                device_id,
                data.get("original_message", ""),
                data.get("reply_content", ""),
                data.get("source", "ai"),
                data.get("model_used", ""),
                data.get("confidence", 0),
                data.get("response_time_ms", 0),
                data.get("platform", ""),
                data.get("customer_name", ""),
                data.get("house_name", "")
            )
        )
        history_id = cursor.lastrowid
        db.commit()

        row = db.execute("SELECT * FROM reply_history WHERE id = ?", (history_id,)).fetchone()
        db.close()

        web.header("Content-Type", "application/json")
        return json.dumps(dict_from_row(row))
