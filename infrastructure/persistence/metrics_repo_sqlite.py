from typing import List
from datetime import datetime
from domain.entities.optimization_metrics import OptimizationMetrics
from domain.repositories.metrics_repo import MetricsRepository
from infrastructure.persistence.database import get_connection


class SqliteMetricsRepository(MetricsRepository):
    def get_by_device_and_date_range(self, device_id: str, days: int) -> List[OptimizationMetrics]:
        db = get_connection()
        rows = db.execute(
            "SELECT * FROM optimization_metrics WHERE device_id=? AND date >= date('now', ?) ORDER BY date DESC",
            (device_id, f"-{days} days"),
        ).fetchall()
        db.close()
        return [OptimizationMetrics(
            id=r["id"], device_id=r["device_id"], date=r["date"],
            total_generated=r["total_generated"], total_accepted=r["total_accepted"],
            total_modified=r["total_modified"], total_rejected=r["total_rejected"],
        ) for r in rows]

    def increment_metric(self, device_id: str, action: str) -> None:
        today = datetime.now().strftime("%Y-%m-%d")
        db = get_connection()
        existing = db.execute(
            "SELECT * FROM optimization_metrics WHERE device_id=? AND date=?", (device_id, today)
        ).fetchone()
        if not existing:
            db.execute(
                "INSERT INTO optimization_metrics (device_id, date) VALUES (?, ?)",
                (device_id, today),
            )
        field_map = {
            "generated": "total_generated", "accepted": "total_accepted",
            "modified": "total_modified", "rejected": "total_rejected",
        }
        field = field_map.get(action)
        if field:
            query = f"UPDATE optimization_metrics SET {field} = {field} + 1 WHERE device_id=? AND date=?"
            db.execute(query, (device_id, today))
        db.commit()
        db.close()
