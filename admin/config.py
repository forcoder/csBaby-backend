import os

API_BASE_URL = os.environ.get("API_BASE_URL", "https://csbaby-api2.onrender.com")
SESSION_SECRET = os.environ.get("SESSION_SECRET")
if not SESSION_SECRET:
    raise RuntimeError(
        "SESSION_SECRET environment variable must be set. "
        "Do not use the default in production."
    )
