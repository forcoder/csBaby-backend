import hashlib
import hmac
import base64
import json
import time
from typing import Optional


class AuthService:
    def __init__(self, jwt_secret: str, jwt_expire_days: int = 30):
        self._secret = jwt_secret
        self._expire_days = jwt_expire_days

    def generate_token(self, device_id: str) -> str:
        header = base64.urlsafe_b64encode(
            json.dumps({"alg": "HS256", "typ": "JWT"}).encode()
        ).rstrip(b"=").decode()
        payload_data = {
            "device_id": device_id,
            "exp": int(time.time()) + self._expire_days * 86400,
            "iat": int(time.time()),
        }
        payload = base64.urlsafe_b64encode(
            json.dumps(payload_data).encode()
        ).rstrip(b"=").decode()
        signature = base64.urlsafe_b64encode(
            hmac.new(self._secret.encode(), f"{header}.{payload}".encode(), hashlib.sha256).digest()
        ).rstrip(b"=").decode()
        return f"{header}.{payload}.{signature}"

    def verify_token(self, token: str) -> Optional[str]:
        try:
            parts = token.split(".")
            if len(parts) != 3:
                return None
            header, payload, signature = parts
            expected_sig = base64.urlsafe_b64encode(
                hmac.new(self._secret.encode(), f"{header}.{payload}".encode(), hashlib.sha256).digest()
            ).rstrip(b"=").decode()
            if not hmac.compare_digest(signature, expected_sig):
                return None
            payload_padded = payload + "=" * (4 - len(payload) % 4) if len(payload) % 4 else payload
            payload_data = json.loads(base64.urlsafe_b64decode(payload_padded.encode()))
            if payload_data.get("exp", 0) < time.time():
                return None
            return payload_data.get("device_id")
        except Exception:
            return None
