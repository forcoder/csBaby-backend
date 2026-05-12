from typing import List, Tuple
from domain.entities.reply_history import ReplyHistory
from domain.repositories.history_repo import HistoryRepository
from infrastructure.persistence.database import get_connection


class SqliteHistoryRepository(HistoryRepository):
    def create(self, entry: ReplyHistory) -> ReplyHistory:
        db = get_connection()
        cursor = db.execute(
            """INSERT INTO reply_history
            (device_id, original_message, reply_content, source, model_used, confidence,
             response_time_ms, platform, customer_name, house_name)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
            (entry.device_id, entry.original_message, entry.reply_content, entry.source,
             entry.model_used, entry.confidence, entry.response_time_ms,
             entry.platform, entry.customer_name, entry.house_name),
        )
        entry.id = cursor.lastrowid
        db.commit()
        db.close()
        return entry

    def get_by_device(self, device_id: str, limit: int, offset: int) -> Tuple[List[ReplyHistory], int]:
        db = get_connection()
        rows = db.execute(
            "SELECT * FROM reply_history WHERE device_id=? ORDER BY created_at DESC LIMIT ? OFFSET ?",
            (device_id, limit, offset),
        ).fetchall()
        total = db.execute(
            "SELECT COUNT(*) FROM reply_history WHERE device_id=?", (device_id,)
        ).fetchone()[0]
        db.close()
        items = [ReplyHistory(id=r["id"], device_id=r["device_id"],
                              original_message=r["original_message"], reply_content=r["reply_content"])
                 for r in rows]
        return items, total
