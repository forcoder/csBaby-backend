import json
import time
from typing import Optional

import jwt


class AuthService:
    def __init__(self, jwt_secret: str, jwt_expire_days: int = 30):
        self._secret = jwt_secret
        self._expire_days = jwt_expire_days

    def generate_token(self, device_id: str) -> str:
        payload = {
            "device_id": device_id,
            "exp": int(time.time()) + self._expire_days * 86400,
            "iat": int(time.time()),
        }
        return jwt.encode(payload, self._secret, algorithm="HS256")

    def verify_token(self, token: str) -> Optional[str]:
        try:
            payload = jwt.decode(token, self._secret, algorithms=["HS256"])
            return payload.get("device_id")
        except (jwt.ExpiredSignatureError, jwt.InvalidTokenError):
            return None
