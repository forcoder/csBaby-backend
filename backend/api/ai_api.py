import json
import web
from database import get_db, dict_from_row
from auth import extract_device_id
from services.ai_service import ai_service
from services.rule_engine import rule_engine
from services.optimize_service import optimize_service


class AIGenerateAPI:
    """POST /api/ai/generate - 生成 AI 回复"""

    def POST(self):
        device_id = extract_device_id()
        if not device_id:
            raise web.unauthorized(json.dumps({"error": "Unauthorized"}))

        data = json.loads(web.data().decode())
        message = data.get("message", "")
        context = data.get("context", {})
        style = data.get("style", {})

        # 1. 先尝试规则匹配
        matched_rules = rule_engine.match_rules(device_id, message)
        if matched_rules:
            template = matched_rules[0]["reply_template"]
            reply = rule_engine.apply_template(template, context)
            optimize_service.record_metric(device_id, "generated")

            # 记录历史
            db = get_db()
            db.execute(
                """INSERT INTO reply_history
                (device_id, original_message, reply_content, source, platform, customer_name, house_name)
                VALUES (?, ?, ?, ?, ?, ?, ?)""",
                (device_id, message, reply, "keyword",
                 context.get("platform", ""),
                 context.get("customer_name", ""),
                 context.get("house_name", ""))
            )
            db.commit()
            db.close()

            web.header("Content-Type", "application/json")
            return json.dumps({
                "reply": reply,
                "source": "keyword",
                "rule_id": matched_rules[0]["id"],
                "confidence": 1.0,
                "response_time_ms": 0
            })

        # 2. 规则未匹配，调用 AI
        db = get_db()
        model_row = db.execute(
            "SELECT * FROM model_configs WHERE device_id = ? AND enabled = 1 ORDER BY is_default DESC, id ASC LIMIT 1",
            (device_id,)
        ).fetchone()
        db.close()

        if not model_row:
            raise web.badrequest(json.dumps({"error": "No enabled model configured"}))

        model_config = dict_from_row(model_row)

        # 构建 prompt
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

        messages = [
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": message}
        ]

        try:
            result = ai_service.generate_reply(
                model_config, messages,
                temperature=model_config.get("temperature", 0.7),
                max_tokens=model_config.get("max_tokens", 2000)
            )
        except Exception as e:
            raise web.internalerror(json.dumps({"error": f"AI generation failed: {str(e)}"}))

        optimize_service.record_metric(device_id, "generated")

        # 记录历史
        db = get_db()
        db.execute(
            """INSERT INTO reply_history
            (device_id, original_message, reply_content, source, model_used, confidence, response_time_ms, platform, customer_name, house_name)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
            (device_id, message, result["reply"], "ai",
             result.get("model_used", ""),
             0.8,  # default confidence for AI
             result.get("response_time_ms", 0),
             context.get("platform", ""),
             context.get("customer_name", ""),
             context.get("house_name", ""))
        )
        db.commit()
        db.close()

        web.header("Content-Type", "application/json")
        return json.dumps({
            "reply": result["reply"],
            "source": "ai",
            "model_used": result.get("model_used", ""),
            "confidence": 0.8,
            "response_time_ms": result.get("response_time_ms", 0),
            "tokens_used": result.get("tokens_used", 0)
        })


class AIChatAPI:
    """POST /api/ai/chat - 多轮对话"""

    def POST(self):
        device_id = extract_device_id()
        if not device_id:
            raise web.unauthorized(json.dumps({"error": "Unauthorized"}))

        data = json.loads(web.data().decode())
        messages = data.get("messages", [])

        if not messages:
            raise web.badrequest(json.dumps({"error": "messages is required"}))

        db = get_db()
        model_row = db.execute(
            "SELECT * FROM model_configs WHERE device_id = ? AND enabled = 1 ORDER BY is_default DESC, id ASC LIMIT 1",
            (device_id,)
        ).fetchone()
        db.close()

        if not model_row:
            raise web.badrequest(json.dumps({"error": "No enabled model configured"}))

        model_config = dict_from_row(model_row)

        try:
            result = ai_service.generate_reply(
                model_config, messages,
                temperature=model_config.get("temperature", 0.7),
                max_tokens=model_config.get("max_tokens", 2000)
            )
        except Exception as e:
            raise web.internalerror(json.dumps({"error": f"AI chat failed: {str(e)}"}))

        web.header("Content-Type", "application/json")
        return json.dumps({
            "reply": result["reply"],
            "model_used": result.get("model_used", ""),
            "tokens_used": result.get("tokens_used", 0),
            "response_time_ms": result.get("response_time_ms", 0)
        })
