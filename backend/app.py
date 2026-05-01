import web
import json
from config import HOST, PORT
from database import init_db

# 导入 API 路由
from api.auth_api import AuthRegister, AuthHeartbeat
from api.rules_api import RulesAPI, RuleDetailAPI, RulesBatchAPI
from api.models_api import ModelsAPI, ModelDetailAPI, ModelTestAPI
from api.ai_api import AIGenerateAPI, AIChatAPI
from api.history_api import HistoryAPI
from api.feedback_api import FeedbackAPI
from api.optimize_api import OptimizeMetricsAPI, OptimizeAnalyzeAPI
from api.backup_api import BackupExportAPI, BackupRestoreAPI

# URL 路由
urls = (
    "/api/auth/register", "AuthRegister",
    "/api/auth/heartbeat", "AuthHeartbeat",
    "/api/rules/batch", "RulesBatchAPI",
    "/api/rules/(\d+)", "RuleDetailAPI",
    "/api/rules", "RulesAPI",
    "/api/models/(\d+)/test", "ModelTestAPI",
    "/api/models/(\d+)", "ModelDetailAPI",
    "/api/models", "ModelsAPI",
    "/api/ai/generate", "AIGenerateAPI",
    "/api/ai/chat", "AIChatAPI",
    "/api/history", "HistoryAPI",
    "/api/feedback", "FeedbackAPI",
    "/api/optimize/metrics", "OptimizeMetricsAPI",
    "/api/optimize/analyze", "OptimizeAnalyzeAPI",
    "/api/backup/restore", "BackupRestoreAPI",
    "/api/backup", "BackupExportAPI",
    "/health", "HealthCheck",
)


class HealthCheck:
    def GET(self):
        web.header("Content-Type", "application/json")
        return json.dumps({"status": "ok", "service": "csBaby-api"})


def json_error_hook():
    """统一 JSON 错误响应"""
    error = web.ctx.get("_error")
    if error:
        web.header("Content-Type", "application/json")
        return json.dumps({"error": str(error)})


def cors_hook():
    """CORS 头"""
    web.header("Access-Control-Allow-Origin", "*")
    web.webapi.header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS", unique=False)
    web.webapi.header("Access-Control-Allow-Headers", "Content-Type, Authorization", unique=False)


def options_handler():
    """处理 OPTIONS 预检请求"""
    if web.ctx.method == "OPTIONS":
        web.header("Access-Control-Allow-Origin", "*")
        web.header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
        web.header("Access-Control-Allow-Headers", "Content-Type, Authorization")
        web.header("Content-Length", "0")
        return ""


if __name__ == "__main__":
    init_db()
    app = web.application(urls, globals())
    app.add_processor(web.loadhook(cors_hook))
    app.add_processor(web.loadhook(options_handler))
    print(f"csBaby API server starting on {HOST}:{PORT}")
    app.run()
