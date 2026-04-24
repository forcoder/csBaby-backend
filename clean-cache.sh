#!/bin/bash

echo "========================================"
echo "清理编译缓存"
echo "========================================"
echo

echo "1. 清理Gradle构建缓存..."
if [ -d "build" ]; then
    rm -rf build
fi
if [ -d "app/build" ]; then
    rm -rf app/build
fi

echo "2. 清理Gradle包装器缓存..."
if [ -d ".gradle" ]; then
    rm -rf .gradle
fi

echo "3. 运行Gradle清理..."
./gradlew clean --no-daemon

echo
echo "清理完成！"