import os

# 数据库路径
DATABASE_PATH = os.environ.get("DATABASE_PATH", "csBaby.db")

# JWT 配置
JWT_SECRET = os.environ.get("JWT_SECRET", "csbaby-secret-key-change-in-production")
JWT_EXPIRE_DAYS = 30

# AI API Keys（可选，用于服务端统一管理）
OPENAI_API_KEY = os.environ.get("OPENAI_API_KEY", "")
CLAUDE_API_KEY = os.environ.get("CLAUDE_API_KEY", "")
ZHIPU_API_KEY = os.environ.get("ZHIPU_API_KEY", "")
TONGYI_API_KEY = os.environ.get("TONGYI_API_KEY", "")

# 服务器配置
HOST = "0.0.0.0"
PORT = int(os.environ.get("PORT", 8080))
DEBUG = os.environ.get("DEBUG", "false").lower() == "true"
