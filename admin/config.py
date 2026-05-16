import os

API_BASE_URL = os.environ.get("API_BASE_URL", "http://127.0.0.1:8080")
SESSION_SECRET = os.environ.get("SESSION_SECRET") or os.environ.get("JWT_SECRET")
if not SESSION_SECRET:
    raise RuntimeError(
        "SESSION_SECRET or JWT_SECRET environment variable must be set. "
        "Do not start the admin panel without a secret key."
    )
