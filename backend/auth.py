import json
import time
import hashlib
import hmac
import base64
import web
from config import JWT_SECRET, JWT_EXPIRE_DAYS


def generate_token(device_id):
    """Generate a simple JWT-like token."""
    header = base64.urlsafe_b64encode(json.dumps({"alg": "HS256", "typ": "JWT"}).encode()).rstrip(b"=").decode()
    payload_data = {
        "device_id": device_id,
        "exp": int(time.time()) + JWT_EXPIRE_DAYS * 86400,
        "iat": int(time.time())
    }
    payload = base64.urlsafe_b64encode(json.dumps(payload_data).encode()).rstrip(b"=").decode()
    signature = base64.urlsafe_b64encode(
        hmac.new(JWT_SECRET.encode(), f"{header}.{payload}".encode(), hashlib.sha256).digest()
    ).rstrip(b"=").decode()
    return f"{header}.{payload}.{signature}"


def verify_token(token):
    """Verify token, return device_id or None."""
    try:
        parts = token.split(".")
        if len(parts) != 3:
            return None
        header, payload, signature = parts
        expected_sig = base64.urlsafe_b64encode(
            hmac.new(JWT_SECRET.encode(), f"{header}.{payload}".encode(), hashlib.sha256).digest()
        ).rstrip(b"=").decode()
        if not hmac.compare_digest(signature, expected_sig):
            return None
        payload += "=" * (4 - len(payload) % 4) if len(payload) % 4 else ""
        payload_data = json.loads(base64.urlsafe_b64decode(payload.encode()))
        if payload_data.get("exp", 0) < time.time():
            return None
        return payload_data.get("device_id")
    except Exception:
        return None


def extract_device_id():
    """从请求头中提取并验证 device_id."""
    auth_header = web.ctx.env.get("HTTP_AUTHORIZATION", "")
    if not auth_header.startswith("Bearer "):
        return None
    return verify_token(auth_header[7:])
