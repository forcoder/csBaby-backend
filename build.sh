#!/bin/bash
echo "========================================"
echo "客服小秘 - 编译 + 版本升级 + 上传"
echo "========================================"
echo

# 设置环境变量
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export ANDROID_HOME=/mnt/d/Android/SDK
export ANDROID_SDK_ROOT=/mnt/d/Android/SDK
export PATH=$JAVA_HOME/bin:$PATH
export GRADLE_OPTS="-Xmx4096m"

cd /mnt/d/workspace/workbuddy/house

# 1. 编译 APK（Gradle 会自动执行 incrementVersion 递增版本号）
echo "[1/2] 编译 APK（自动递增版本号）..."
./gradlew assembleDebug --no-daemon \
  -PskipVersionIncrement -PskipUpload \
  -Pkotlin.incremental=false \
  -Pksp.incremental=false \
  -Dorg.gradle.jvmargs="-Xmx2048m -Xms512m -XX:+HeapDumpOnOutOfMemoryError" \
  -x lintDebug \
  -x testDebugUnitTest \
  -x testReleaseUnitTest \
  --stacktrace

if [ $? -ne 0 ]; then
    echo
    echo "[错误] 编译失败"
    read -p "按回车键退出..."
    exit 1
fi

echo
echo "[成功] 编译完成"

# 2. 上传到 shz.al
echo
echo "[2/2] 上传到 shz.al..."
python3 upload-to-shzl.py

if [ $? -ne 0 ]; then
    echo
    echo "[错误] 上传失败"
    read -p "按回车键退出..."
    exit 1
fi

echo
echo "========================================"
echo "全部完成！"
echo "========================================"
if [ -f "version-info.json" ]; then
    echo
    echo "最新版本信息："
    python3 -c "
import json
with open('version-info.json', 'r') as f:
    v = json.load(f)
print(f'  版本: v{v[\"versionName\"]} ({v[\"versionCode\"]})')
print(f'  大小: {round(v[\"fileSize\"/(1024*1024), 2)} MB')
print(f'  MD5: {v[\"md5\"]}')
print(f'  下载: {v[\"downloadUrl\"]}')
"
fi
echo