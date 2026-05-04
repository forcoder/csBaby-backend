import json
import time
import hashlib
import hmac
import base64
import web
from passlib.hash import pbkdf2_sha256
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


def hash_password(password: str) -> str:
    """PBKDF2-SHA256 密码哈希"""
    return pbkdf2_sha256.hash(password)


def verify_password(password: str, hash_str: str) -> bool:
    """PBKDF2-SHA256 密码校验"""
    try:
        return pbkdf2_sha256.verify(password, hash_str)
    except Exception:
        return False


def generate_user_token(device_id: str, tenant_id: str, is_admin: int = 0) -> str:
    """Generate token with tenant_id and is_admin."""
    header = base64.urlsafe_b64encode(json.dumps({"alg": "HS256", "typ": "JWT"}).encode()).rstrip(b"=").decode()
    payload_data = {
        "device_id": device_id,
        "tenant_id": tenant_id,
        "is_admin": is_admin,
        "exp": int(time.time()) + JWT_EXPIRE_DAYS * 86400,
        "iat": int(time.time())
    }
    payload = base64.urlsafe_b64encode(json.dumps(payload_data).encode()).rstrip(b"=").decode()
    signature = base64.urlsafe_b64encode(
        hmac.new(JWT_SECRET.encode(), f"{header}.{payload}".encode(), hashlib.sha256).digest()
    ).rstrip(b"=").decode()
    return f"{header}.{payload}.{signature}"


def extract_user_info():
    """从请求头提取 device_id, tenant_id, is_admin。返回 dict 或 None。"""
    auth_header = web.ctx.env.get("HTTP_AUTHORIZATION", "")
    if not auth_header.startswith("Bearer "):
        return None
    token = auth_header[7:]
    device_id = verify_token(token)
    if not device_id:
        return None
    # 解析 payload 获取 tenant_id 和 is_admin
    try:
        parts = token.split(".")
        payload = parts[1]
        payload += "=" * (4 - len(payload) % 4) if len(payload) % 4 else ""
        payload_data = json.loads(base64.urlsafe_b64decode(payload.encode()))
        return {
            "device_id": device_id,
            "tenant_id": payload_data.get("tenant_id", device_id),
            "is_admin": payload_data.get("is_admin", 0)
        }
    except Exception:
        return {"device_id": device_id, "tenant_id": device_id, "is_admin": 0}
