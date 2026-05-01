import json
import web
from database import get_db, dict_from_row
from auth import extract_device_id


class RulesAPI:
    """GET/POST /api/rules - 获取所有规则 / 创建规则"""

    def GET(self):
        device_id = extract_device_id()
        if not device_id:
            raise web.unauthorized(json.dumps({"error": "Unauthorized"}))

        db = get_db()
        rows = db.execute(
            "SELECT * FROM keyword_rules WHERE device_id = ? ORDER BY priority DESC, id DESC",
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
        cursor = db.execute(
            """INSERT INTO keyword_rules
            (device_id, keyword, match_type, reply_template, category, target_type, target_names, priority)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)""",
            (
                device_id,
                data.get("keyword", ""),
                data.get("match_type", "CONTAINS"),
                data.get("reply_template", ""),
                data.get("category", ""),
                data.get("target_type", "ALL"),
                json.dumps(data.get("target_names", [])),
                data.get("priority", 0)
            )
        )
        rule_id = cursor.lastrowid
        db.commit()

        row = db.execute("SELECT * FROM keyword_rules WHERE id = ?", (rule_id,)).fetchone()
        db.close()

        web.header("Content-Type", "application/json")
        return json.dumps(dict_from_row(row))


class RuleDetailAPI:
    """GET/PUT/DELETE /api/rules/{id}"""

    def GET(self, rule_id):
        device_id = extract_device_id()
        if not device_id:
            raise web.unauthorized(json.dumps({"error": "Unauthorized"}))

        db = get_db()
        row = db.execute(
            "SELECT * FROM keyword_rules WHERE id = ? AND device_id = ?",
            (rule_id, device_id)
        ).fetchone()
        db.close()

        if not row:
            raise web.notfound(json.dumps({"error": "Rule not found"}))

        web.header("Content-Type", "application/json")
        return json.dumps(dict_from_row(row))

    def PUT(self, rule_id):
        device_id = extract_device_id()
        if not device_id:
            raise web.unauthorized(json.dumps({"error": "Unauthorized"}))

        data = json.loads(web.data().decode())
        db = get_db()
        db.execute(
            """UPDATE keyword_rules SET
            keyword = ?, match_type = ?, reply_template = ?, category = ?,
            target_type = ?, target_names = ?, priority = ?, enabled = ?,
            updated_at = CURRENT_TIMESTAMP
            WHERE id = ? AND device_id = ?""",
            (
                data.get("keyword", ""),
                data.get("match_type", "CONTAINS"),
                data.get("reply_template", ""),
                data.get("category", ""),
                data.get("target_type", "ALL"),
                json.dumps(data.get("target_names", [])),
                data.get("priority", 0),
                data.get("enabled", 1),
                rule_id, device_id
            )
        )
        db.commit()

        row = db.execute("SELECT * FROM keyword_rules WHERE id = ?", (rule_id,)).fetchone()
        db.close()

        web.header("Content-Type", "application/json")
        return json.dumps(dict_from_row(row))

    def DELETE(self, rule_id):
        device_id = extract_device_id()
        if not device_id:
            raise web.unauthorized(json.dumps({"error": "Unauthorized"}))

        db = get_db()
        db.execute("DELETE FROM keyword_rules WHERE id = ? AND device_id = ?", (rule_id, device_id))
        db.commit()
        db.close()

        web.header("Content-Type", "application/json")
        return json.dumps({"status": "deleted", "id": int(rule_id)})


class RulesBatchAPI:
    """POST /api/rules/batch - 批量导入规则"""

    def POST(self):
        device_id = extract_device_id()
        if not device_id:
            raise web.unauthorized(json.dumps({"error": "Unauthorized"}))

        data = json.loads(web.data().decode())
        rules = data.get("rules", [])
        mode = data.get("mode", "append")  # append or override

        db = get_db()
        if mode == "override":
            db.execute("DELETE FROM keyword_rules WHERE device_id = ?", (device_id,))

        for rule in rules:
            db.execute(
                """INSERT INTO keyword_rules
                (device_id, keyword, match_type, reply_template, category, target_type, target_names, priority)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)""",
                (
                    device_id,
                    rule.get("keyword", ""),
                    rule.get("match_type", "CONTAINS"),
                    rule.get("reply_template", ""),
                    rule.get("category", ""),
                    rule.get("target_type", "ALL"),
                    json.dumps(rule.get("target_names", [])),
                    rule.get("priority", 0)
                )
            )

        db.commit()
        count = db.execute("SELECT COUNT(*) FROM keyword_rules WHERE device_id = ?", (device_id,)).fetchone()[0]
        db.close()

        web.header("Content-Type", "application/json")
        return json.dumps({"status": "ok", "imported": len(rules), "total": count})
