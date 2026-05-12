import pytest


class TestRegister:
    def test_register_returns_device_and_token(self, client):
        resp = client.post("/api/auth/register", json={
            "name": "test-device",
            "platform": "android",
            "app_version": "1.0.0"
        })
        assert resp.status_code == 200
        data = resp.get_json()
        assert "device_id" in data
        assert "token" in data
        assert "expires_in" in data
        assert data["expires_in"] == 30 * 86400

    def test_register_defaults(self, client):
        resp = client.post("/api/auth/register", json={})
        assert resp.status_code == 200
        data = resp.get_json()
        assert "device_id" in data
        assert "token" in data

    def test_register_custom_platform(self, client):
        resp = client.post("/api/auth/register", json={"platform": "ios"})
        assert resp.status_code == 200


class TestHeartbeat:
    def test_heartbeat_ok(self, client, auth_headers):
        resp = client.post("/api/auth/heartbeat", headers=auth_headers)
        assert resp.status_code == 200
        assert resp.get_json()["status"] == "ok"

    def test_heartbeat_unauthorized(self, client):
        resp = client.post("/api/auth/heartbeat")
        assert resp.status_code == 401

    def test_heartbeat_bad_token(self, client):
        resp = client.post("/api/auth/heartbeat",
                           headers={"Authorization": "Bearer invalid.token.here"})
        assert resp.status_code == 401


class TestHealthCheck:
    def test_health(self, client):
        resp = client.get("/health")
        assert resp.status_code == 200
        data = resp.get_json()
        assert data["status"] == "ok"
        assert data["service"] == "csBaby-api"


class TestCORS:
    def test_cors_headers(self, client):
        resp = client.get("/health", headers={"Origin": "http://localhost:3000"})
        assert "Access-Control-Allow-Origin" in resp.headers
        assert resp.headers["Access-Control-Allow-Origin"] == "http://localhost:3000"

    def test_cors_rejects_unknown_origin(self, client):
        resp = client.get("/health", headers={"Origin": "http://evil.com"})
        assert "Access-Control-Allow-Origin" not in resp.headers

    def test_cors_missing_origin_no_header(self, client):
        resp = client.get("/health")
        # Without Origin header, CORS header is not set
        assert "Access-Control-Allow-Origin" not in resp.headers

    def test_options_preflight(self, client):
        resp = client.options("/api/rules")
        # OPTIONS handler returns empty string; status may be 200 or 204
        assert resp.status_code in (200, 204)
