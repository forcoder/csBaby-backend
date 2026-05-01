# Render 部署指南

## 步骤

1. 打开 https://dashboard.render.com
2. 点击 "New +" -> "Web Service"
3. 选择 "Build and deploy from a Git repository"
4. 连接 GitHub 仓库：`forcoder/csBaby-backend`
5. 配置：
   - **Name**: `csbaby-api`
   - **Runtime**: Python 3
   - **Build Command**: `pip install -r requirements.txt`
   - **Start Command**: `python app.py`
   - **Plan**: Free
6. 添加环境变量：
   - `DATABASE_PATH`: `/var/data/csBaby.db`
   - `JWT_SECRET`: （点击 Generate）
7. 添加 Disk：
   - **Name**: `data`
   - **Mount Path**: `/var/data`
   - **Size**: 1 GB
8. 点击 "Create Web Service"

## 部署后

服务 URL 类似：`https://csbaby-api.onrender.com`

更新 Android 端 `NetworkModule.kt` 中的 `BACKEND_BASE_URL`：
```kotlin
private const val BACKEND_BASE_URL = "https://你的服务名.onrender.com/"
```
