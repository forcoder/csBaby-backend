import json
import web
from auth import extract_device_id
from services.optimize_service import optimize_service


class OptimizeMetricsAPI:
    """GET /api/optimize/metrics"""

    def GET(self):
        device_id = extract_device_id()
        if not device_id:
            raise web.unauthorized(json.dumps({"error": "Unauthorized"}))

        days = int(web.input(days=7).days)
        metrics = optimize_service.get_metrics(device_id, days)

        web.header("Content-Type", "application/json")
        return json.dumps(metrics)


class OptimizeAnalyzeAPI:
    """POST /api/optimize/analyze"""

    def POST(self):
        device_id = extract_device_id()
        if not device_id:
            raise web.unauthorized(json.dumps({"error": "Unauthorized"}))

        result = optimize_service.analyze(device_id)

        web.header("Content-Type", "application/json")
        return json.dumps(result)
