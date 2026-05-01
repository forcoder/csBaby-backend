import re
from database import get_db, dict_from_row


class RuleEngine:
    """知识库规则匹配引擎"""

    @staticmethod
    def match_rules(device_id, message):
        """
        匹配消息对应的规则。
        返回匹配的规则列表（按优先级降序）。
        """
        db = get_db()
        rows = db.execute(
            "SELECT * FROM keyword_rules WHERE device_id = ? AND enabled = 1 ORDER BY priority DESC",
            (device_id,)
        ).fetchall()
        db.close()

        matched = []
        for row in rows:
            rule = dict_from_row(row)
            if RuleEngine._match_rule(rule, message):
                matched.append(rule)
        return matched

    @staticmethod
    def _match_rule(rule, message):
        """单条规则匹配"""
        keyword = rule["keyword"]
        match_type = rule.get("match_type", "CONTAINS")
        message_lower = message.lower()

        if match_type == "EXACT":
            return message_lower == keyword.lower()
        elif match_type == "STARTS_WITH":
            return message_lower.startswith(keyword.lower())
        elif match_type == "ENDS_WITH":
            return message_lower.endswith(keyword.lower())
        elif match_type == "REGEX":
            try:
                return bool(re.search(keyword, message, re.IGNORECASE))
            except re.error:
                return False
        else:  # CONTAINS (default)
            return keyword.lower() in message_lower

    @staticmethod
    def apply_template(template, context):
        """
        应用模板变量替换。
        context: dict, e.g. {"customer_name": "张三", "house_name": "海景套房"}
        """
        result = template
        if context:
            for key, value in context.items():
                result = result.replace(f"{{{key}}}", str(value))
        return result


# 单例
rule_engine = RuleEngine()
