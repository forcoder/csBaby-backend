import pytest


class TestExportBackup:
    def test_export_empty_backup(self, client, auth_headers):
        resp = client.get("/api/backup", headers=auth_headers)
        assert resp.status_code == 200
        data = resp.get_json()
        assert data["version"] == 1
        assert "device_id" in data
        assert data["rules"] == []
        assert data["models"] == []
        assert data["history"] == []

    def test_export_with_data(self, client, auth_headers):
        client.post("/api/rules", json={
            "keyword": "test", "reply_template": "reply"
        }, headers=auth_headers)
        client.post("/api/models", json={
            "name": "M1", "model_type": "OPENAI", "model": "gpt-4", "api_key": "k"
        }, headers=auth_headers)
        resp = client.get("/api/backup", headers=auth_headers)
        data = resp.get_json()
        assert len(data["rules"]) >= 1
        assert len(data["models"]) >= 1

    def test_export_unauthorized(self, client):
        resp = client.get("/api/backup")
        assert resp.status_code == 401


class TestRestoreBackup:
    def test_restore_rules(self, client, auth_headers):
        backup = {
            "rules": [
                {"keyword": "r1", "reply_template": "t1", "match_type": "CONTAINS"},
                {"keyword": "r2", "reply_template": "t2", "match_type": "EXACT"},
            ],
            "models": []
        }
        resp = client.post("/api/backup/restore", json={
            "backup": backup
        }, headers=auth_headers)
        assert resp.status_code == 200
        data = resp.get_json()
        assert data["restored"]["rules"] == 2
        # Verify rules were restored
        rules = client.get("/api/rules", headers=auth_headers).get_json()
        assert len(rules) == 2

    def test_restore_models(self, client, auth_headers):
        backup = {
            "rules": [],
            "models": [
                {"name": "Model1", "model_type": "OPENAI", "model": "gpt-4",
                 "api_key": "k", "temperature": 0.7, "max_tokens": 2000,
                 "api_endpoint": "", "is_default": 1, "enabled": 1}
            ]
        }
        resp = client.post("/api/backup/restore", json={
            "backup": backup
        }, headers=auth_headers)
        assert resp.status_code == 200
        data = resp.get_json()
        assert data["restored"]["models"] == 1

    def test_restore_override(self, client, auth_headers):
        # Create existing data
        client.post("/api/rules", json={
            "keyword": "existing", "reply_template": "old"
        }, headers=auth_headers)
        # Restore with new data (should replace)
        backup = {
            "rules": [{"keyword": "new", "reply_template": "new_reply"}],
            "models": []
        }
        resp = client.post("/api/backup/restore", json={"backup": backup}, headers=auth_headers)
        rules = client.get("/api/rules", headers=auth_headers).get_json()
        assert len(rules) == 1
        assert rules[0]["keyword"] == "new"

    def test_restore_empty(self, client, auth_headers):
        resp = client.post("/api/backup/restore", json={
            "backup": {"rules": [], "models": []}
        }, headers=auth_headers)
        assert resp.status_code == 200

    def test_restore_unauthorized(self, client):
        resp = client.post("/api/backup/restore", json={"backup": {}})
        assert resp.status_code == 401
