import json
from typing import List, Optional
from domain.entities.keyword_rule import KeywordRule
from domain.repositories.rule_repo import RuleRepository
from infrastructure.persistence.database import get_connection


class SqliteRuleRepository(RuleRepository):
    def create(self, rule: KeywordRule) -> KeywordRule:
        conn = get_connection()
        cursor = conn.execute(
            """INSERT INTO keyword_rules
            (user_id, keyword, match_type, reply_template, category, target_type, target_names, priority)
            VALUES (%s, %s, %s, %s, %s, %s, %s, %s)
            RETURNING id""",
            (rule.user_id, rule.keyword, rule.match_type, rule.reply_template,
             rule.category, rule.target_type, json.dumps(rule.target_names), rule.priority),
        )
        row = cursor.fetchone()
        rule.id = row[0]
        conn.commit()
        conn.close()
        return rule

    def get_by_device(self, user_id: str) -> List[KeywordRule]:
        conn = get_connection()
        cursor = conn.execute(
            "SELECT * FROM keyword_rules WHERE user_id = %s ORDER BY priority DESC, id DESC",
            (user_id,),
        )
        rows = cursor.fetchall()
        conn.close()
        rules = []
        for r in rows:
            try:
                target_names = json.loads(r["target_names"]) if r["target_names"] else []
            except (json.JSONDecodeError, TypeError):
                target_names = []
            rules.append(KeywordRule(
                id=r["id"], user_id=r["user_id"], keyword=r["keyword"],
                match_type=r["match_type"], reply_template=r["reply_template"],
                category=r["category"], target_type=r["target_type"],
                target_names=target_names, priority=r["priority"],
                enabled=bool(r["enabled"])
            ))
        return rules

    def get_by_id(self, rule_id: int, user_id: str) -> Optional[KeywordRule]:
        conn = get_connection()
        cursor = conn.execute(
            "SELECT * FROM keyword_rules WHERE id = %s AND user_id = %s",
            (rule_id, user_id)
        )
        row = cursor.fetchone()
        conn.close()
        if not row:
            return None
        try:
            target_names = json.loads(row["target_names"]) if row["target_names"] else []
        except (json.JSONDecodeError, TypeError):
            target_names = []
        return KeywordRule(
            id=row["id"], user_id=row["user_id"], keyword=row["keyword"],
            match_type=row["match_type"], reply_template=row["reply_template"],
            category=row["category"], target_type=row["target_type"],
            target_names=target_names, priority=row["priority"],
            enabled=bool(row["enabled"]),
        )

    def update(self, rule: KeywordRule) -> KeywordRule:
        conn = get_connection()
        conn.execute(
            """UPDATE keyword_rules SET keyword=%s, match_type=%s, reply_template=%s, category=%s,
            target_type=%s, target_names=%s, priority=%s, enabled=%s, updated_at=CURRENT_TIMESTAMP
            WHERE id=%s AND user_id=%s""",
            (rule.keyword, rule.match_type, rule.reply_template, rule.category,
             rule.target_type, json.dumps(rule.target_names), rule.priority,
             rule.enabled, rule.id, rule.user_id),
        )
        conn.commit()
        conn.close()
        return rule

    def delete(self, rule_id: int, user_id: str) -> bool:
        conn = get_connection()
        cursor = conn.execute(
            "DELETE FROM keyword_rules WHERE id=%s AND user_id=%s",
            (rule_id, user_id)
        )
        conn.commit()
        conn.close()
        return cursor.rowcount > 0

    def batch_create(self, rules: List[KeywordRule], user_id: str, mode: str) -> int:
        conn = get_connection()
        try:
            if mode == "override":
                conn.execute("DELETE FROM keyword_rules WHERE user_id=%s", (user_id,))
            for rule in rules:
                conn.execute(
                    """INSERT INTO keyword_rules
                    (user_id, keyword, match_type, reply_template, category, target_type, target_names, priority)
                    VALUES (%s, %s, %s, %s, %s, %s, %s, %s)""",
                    (user_id, rule.keyword, rule.match_type, rule.reply_template,
                     rule.category, rule.target_type, json.dumps(rule.target_names), rule.priority),
                )
            conn.commit()
        except Exception:
            conn.rollback()
            raise
        finally:
            conn.close()
        return len(rules)

    def upsert(self, rule: KeywordRule) -> KeywordRule:
        """Insert or update a rule using PostgreSQL upsert."""
        conn = get_connection()
        try:
            if rule.id:
                # Check if rule exists
                cursor = conn.execute(
                    "SELECT id FROM keyword_rules WHERE id=%s AND user_id=%s",
                    (rule.id, rule.user_id)
                )
                existing = cursor.fetchone()

                if existing:
                    conn.execute(
                        """UPDATE keyword_rules SET keyword=%s, match_type=%s, reply_template=%s, category=%s,
                        target_type=%s, target_names=%s, priority=%s, enabled=%s, updated_at=CURRENT_TIMESTAMP
                        WHERE id=%s AND user_id=%s""",
                        (rule.keyword, rule.match_type, rule.reply_template, rule.category,
                         rule.target_type, json.dumps(rule.target_names), rule.priority,
                         rule.enabled, rule.id, rule.user_id),
                    )
            else:
                cursor = conn.execute(
                    """INSERT INTO keyword_rules
                    (user_id, keyword, match_type, reply_template, category, target_type, target_names, priority, enabled)
                    VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s)
                    RETURNING id""",
                    (rule.user_id, rule.keyword, rule.match_type, rule.reply_template,
                     rule.category, rule.target_type, json.dumps(rule.target_names), rule.priority, rule.enabled),
                )
                row = cursor.fetchone()
                rule.id = row[0]

            conn.commit()
        except Exception:
            conn.rollback()
            raise
        finally:
            conn.close()
        return rule