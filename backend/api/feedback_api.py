import json
import web
from database import get_db, dict_from_row
from auth import extract_device_id
from services.optimize_service import optimize_service


class FeedbackAPI:
    """GET/POST /api/feedback"""

    def GET(self):
        device_id = extract_device_id()
        if not device_id:
            raise web.unauthorized(json.dumps({"error": "Unauthorized"}))

        limit = int(web.input(limit=50).limit)
        offset = int(web.input(offset=0).offset)

        db = get_db()
        rows = db.execute(
            "SELECT * FROM feedback WHERE device_id = ? ORDER BY created_at DESC LIMIT ? OFFSET ?",
            (device_id, limit, offset)
        ).fetchall()
        db.close()

        web.header("Content-Type", "application/json")
        return json.dumps([dict_from_row(r) for r in rows])

    def POST(self):
        device_id = extract_device_id()
        if not device_id:
            raise web.unauthorized(json.dumps({"error": "Unauthorized"}))

        data = json.loads(web.data().decode())
        action = data.get("action", "")

        # 记录优化指标
        if action in ("generated", "accepted", "modified", "rejected"):
            optimize_service.record_metric(device_id, action)

        db = get_db()
        cursor = db.execute(
            """INSERT INTO feedback
            (device_id, reply_history_id, action, modified_text, rating, comment)
            VALUES (?, ?, ?, ?, ?, ?)""",
            (
                device_id,
                data.get("reply_history_id"),
                action,
                data.get("modified_text", ""),
                data.get("rating", 0),
                data.get("comment", "")
            )
        )
        feedback_id = cursor.lastrowid
        db.commit()

        row = db.execute("SELECT * FROM feedback WHERE id = ?", (feedback_id,)).fetchone()
        db.close()

        web.header("Content-Type", "application/json")
        return json.dumps(dict_from_row(row))
