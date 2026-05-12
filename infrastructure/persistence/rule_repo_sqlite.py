import json
from typing import List, Optional
from domain.entities.keyword_rule import KeywordRule
from domain.repositories.rule_repo import RuleRepository
from infrastructure.persistence.database import get_connection


class SqliteRuleRepository(RuleRepository):
    def create(self, rule: KeywordRule) -> KeywordRule:
        db = get_connection()
        cursor = db.execute(
            """INSERT INTO keyword_rules
            (device_id, keyword, match_type, reply_template, category, target_type, target_names, priority)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)""",
            (rule.device_id, rule.keyword, rule.match_type, rule.reply_template,
             rule.category, rule.target_type, json.dumps(rule.target_names), rule.priority),
        )
        rule.id = cursor.lastrowid
        db.commit()
        db.close()
        return rule

    def get_by_device(self, device_id: str) -> List[KeywordRule]:
        db = get_connection()
        rows = db.execute(
            "SELECT * FROM keyword_rules WHERE device_id = ? ORDER BY priority DESC, id DESC",
            (device_id,),
        ).fetchall()
        db.close()
        return [KeywordRule(id=r["id"], device_id=r["device_id"], keyword=r["keyword"],
                            match_type=r["match_type"], reply_template=r["reply_template"],
                            category=r["category"], target_type=r["target_type"],
                            target_names=json.loads(r["target_names"]), priority=r["priority"],
                            enabled=bool(r["enabled"]))
                for r in rows]

    def get_by_id(self, rule_id: int, device_id: str) -> Optional[KeywordRule]:
        db = get_connection()
        row = db.execute(
            "SELECT * FROM keyword_rules WHERE id = ? AND device_id = ?", (rule_id, device_id)
        ).fetchone()
        db.close()
        if not row:
            return None
        return KeywordRule(
            id=row["id"], device_id=row["device_id"], keyword=row["keyword"],
            match_type=row["match_type"], reply_template=row["reply_template"],
            category=row["category"], target_type=row["target_type"],
            target_names=json.loads(row["target_names"]), priority=row["priority"],
            enabled=bool(row["enabled"]),
        )

    def update(self, rule: KeywordRule) -> KeywordRule:
        db = get_connection()
        db.execute(
            """UPDATE keyword_rules SET keyword=?, match_type=?, reply_template=?, category=?,
            target_type=?, target_names=?, priority=?, enabled=?, updated_at=CURRENT_TIMESTAMP
            WHERE id=? AND device_id=?""",
            (rule.keyword, rule.match_type, rule.reply_template, rule.category,
             rule.target_type, json.dumps(rule.target_names), rule.priority,
             int(rule.enabled), rule.id, rule.device_id),
        )
        db.commit()
        db.close()
        return rule

    def delete(self, rule_id: int, device_id: str) -> bool:
        db = get_connection()
        cursor = db.execute("DELETE FROM keyword_rules WHERE id=? AND device_id=?", (rule_id, device_id))
        db.commit()
        db.close()
        return cursor.rowcount > 0

    def batch_create(self, rules: List[KeywordRule], device_id: str, mode: str) -> int:
        db = get_connection()
        try:
            db.execute("BEGIN TRANSACTION")
            if mode == "override":
                db.execute("DELETE FROM keyword_rules WHERE device_id=?", (device_id,))
            for rule in rules:
                db.execute(
                    """INSERT INTO keyword_rules
                    (device_id, keyword, match_type, reply_template, category, target_type, target_names, priority)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)""",
                    (device_id, rule.keyword, rule.match_type, rule.reply_template,
                     rule.category, rule.target_type, json.dumps(rule.target_names), rule.priority),
                )
            db.commit()
        except Exception:
            db.rollback()
            raise
        finally:
            db.close()
        return len(rules)
