import json
import web
from database import get_db, dict_from_row
from auth import extract_device_id


class BackupExportAPI:
    """GET /api/backup - 导出全量备份"""

    def GET(self):
        device_id = extract_device_id()
        if not device_id:
            raise web.unauthorized(json.dumps({"error": "Unauthorized"}))

        db = get_db()

        rules = [dict_from_row(r) for r in db.execute(
            "SELECT * FROM keyword_rules WHERE device_id = ?", (device_id,)
        ).fetchall()]

        models = [dict_from_row(r) for r in db.execute(
            "SELECT * FROM model_configs WHERE device_id = ?", (device_id,)
        ).fetchall()]

        history = [dict_from_row(r) for r in db.execute(
            "SELECT * FROM reply_history WHERE device_id = ? ORDER BY created_at DESC LIMIT 1000",
            (device_id,)
        ).fetchall()]

        feedback = [dict_from_row(r) for r in db.execute(
            "SELECT * FROM feedback WHERE device_id = ? ORDER BY created_at DESC LIMIT 1000",
            (device_id,)
        ).fetchall()]

        metrics = [dict_from_row(r) for r in db.execute(
            "SELECT * FROM optimization_metrics WHERE device_id = ? ORDER BY date DESC",
            (device_id,)
        ).fetchall()]

        db.close()

        backup = {
            "version": 1,
            "device_id": device_id,
            "rules": rules,
            "models": models,
            "history": history,
            "feedback": feedback,
            "metrics": metrics
        }

        web.header("Content-Type", "application/json")
        return json.dumps(backup)


class BackupRestoreAPI:
    """POST /api/backup/restore - 恢复备份"""

    def POST(self):
        device_id = extract_device_id()
        if not device_id:
            raise web.unauthorized(json.dumps({"error": "Unauthorized"}))

        data = json.loads(web.data().decode())
        backup = data.get("backup", {})

        db = get_db()

        # 恢复规则
        rules = backup.get("rules", [])
        if rules:
            db.execute("DELETE FROM keyword_rules WHERE device_id = ?", (device_id,))
            for rule in rules:
                db.execute(
                    """INSERT INTO keyword_rules
                    (device_id, keyword, match_type, reply_template, category, target_type, target_names, priority, enabled)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                    (device_id, rule.get("keyword", ""), rule.get("match_type", "CONTAINS"),
                     rule.get("reply_template", ""), rule.get("category", ""),
                     rule.get("target_type", "ALL"), rule.get("target_names", "[]"),
                     rule.get("priority", 0), rule.get("enabled", 1))
                )

        # 恢复模型配置
        models = backup.get("models", [])
        if models:
            db.execute("DELETE FROM model_configs WHERE device_id = ?", (device_id,))
            for m in models:
                db.execute(
                    """INSERT INTO model_configs
                    (device_id, name, model_type, model, api_key, api_endpoint, temperature, max_tokens, is_default, enabled)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                    (device_id, m.get("name", ""), m.get("model_type", "OPENAI"),
                     m.get("model", ""), m.get("api_key", ""), m.get("api_endpoint", ""),
                     m.get("temperature", 0.7), m.get("max_tokens", 2000),
                     m.get("is_default", 0), m.get("enabled", 1))
                )

        db.commit()
        db.close()

        web.header("Content-Type", "application/json")
        return json.dumps({
            "status": "ok",
            "restored": {
                "rules": len(rules),
                "models": len(models)
            }
        })
