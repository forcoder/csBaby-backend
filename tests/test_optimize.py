import pytest


class TestGetMetrics:
    def test_get_metrics_empty(self, client, auth_headers):
        resp = client.get("/api/optimize/metrics", headers=auth_headers)
        assert resp.status_code == 200
        assert resp.get_json() == []

    def test_get_metrics_with_data(self, client, auth_headers):
        # Submit feedback which triggers metric recording
        client.post("/api/feedback", json={
            "action": "generated"
        }, headers=auth_headers)
        resp = client.get("/api/optimize/metrics?days=1", headers=auth_headers)
        data = resp.get_json()
        assert len(data) >= 1
        assert data[0]["total_generated"] >= 1


class TestAnalyze:
    def test_analyze_no_data(self, client, auth_headers):
        resp = client.post("/api/optimize/analyze", headers=auth_headers)
        assert resp.status_code == 200
        data = resp.get_json()
        assert data["status"] == "no_data"

    def test_analyze_with_data(self, client, auth_headers):
        for action in ["generated", "generated", "generated", "accepted", "modified", "rejected"]:
            client.post("/api/feedback", json={"action": action}, headers=auth_headers)
        resp = client.post("/api/optimize/analyze", headers=auth_headers)
        assert resp.status_code == 200
        data = resp.get_json()
        assert data["status"] == "ok"
        assert data["total_generated"] >= 3
        assert "accept_rate" in data
        assert "suggestions" in data
