from datetime import datetime, timedelta
from database import get_db, dict_from_row


class OptimizeService:
    """优化分析服务"""

    @staticmethod
    def get_metrics(device_id, days=7):
        """获取最近 N 天的优化指标"""
        db = get_db()
        since = (datetime.now() - timedelta(days=days)).strftime("%Y-%m-%d")
        rows = db.execute(
            "SELECT * FROM optimization_metrics WHERE device_id = ? AND date >= ? ORDER BY date DESC",
            (device_id, since)
        ).fetchall()
        db.close()
        return [dict_from_row(r) for r in rows]

    @staticmethod
    def record_metric(device_id, action):
        """
        记录一次操作指标。
        action: "generated" / "accepted" / "modified" / "rejected"
        """
        today = datetime.now().strftime("%Y-%m-%d")
        db = get_db()

        # 确保当天记录存在
        existing = db.execute(
            "SELECT * FROM optimization_metrics WHERE device_id = ? AND date = ?",
            (device_id, today)
        ).fetchone()

        if not existing:
            db.execute(
                "INSERT INTO optimization_metrics (device_id, date) VALUES (?, ?)",
                (device_id, today)
            )

        # 更新计数
        field_map = {
            "generated": "total_generated",
            "accepted": "total_accepted",
            "modified": "total_modified",
            "rejected": "total_rejected"
        }
        field = field_map.get(action)
        if field:
            db.execute(
                f"UPDATE optimization_metrics SET {field} = {field} + 1 WHERE device_id = ? AND date = ?",
                (device_id, today)
            )

        db.commit()
        db.close()

    @staticmethod
    def analyze(device_id):
        """分析最近的优化指标并给出建议"""
        metrics = OptimizeService.get_metrics(device_id, days=30)
        if not metrics:
            return {"status": "no_data", "message": "暂无足够数据进行分析"}

        total = sum(m["total_generated"] for m in metrics)
        accepted = sum(m["total_accepted"] for m in metrics)
        modified = sum(m["total_modified"] for m in metrics)
        rejected = sum(m["total_rejected"] for m in metrics)

        accept_rate = accepted / total if total > 0 else 0
        modify_rate = modified / total if total > 0 else 0
        reject_rate = rejected / total if total > 0 else 0

        suggestions = []
        if accept_rate < 0.5:
            suggestions.append("接受率较低，建议优化回复模板或调整模型参数")
        if modify_rate > 0.3:
            suggestions.append("修改率较高，建议检查模板变量是否正确替换")
        if reject_rate > 0.2:
            suggestions.append("拒绝率较高，建议增加知识库规则覆盖更多场景")

        return {
            "status": "ok",
            "period_days": 30,
            "total_generated": total,
            "total_accepted": accepted,
            "total_modified": modified,
            "total_rejected": rejected,
            "accept_rate": round(accept_rate, 3),
            "modify_rate": round(modify_rate, 3),
            "reject_rate": round(reject_rate, 3),
            "suggestions": suggestions
        }


# 单例
optimize_service = OptimizeService()
