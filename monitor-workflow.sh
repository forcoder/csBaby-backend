#!/bin/bash

echo "=== 监控 GitHub Actions 工作流状态 ==="
echo "提交哈希: $(git rev-parse --short HEAD)"
echo "当前分支: $(git branch --show-current)"
echo ""

# 等待一段时间让工作流开始运行
echo "等待工作流启动..."
sleep 30

# 尝试获取工作流运行信息
echo "正在检查最新工作流状态..."

# 使用简单的循环检查（在实际环境中可能需要更复杂的逻辑）
for i in {1..20}; do
    echo "检查第 $i 次..."

    # 尝试不同的方式来获取工作流状态
    if command -v gh &> /dev/null; then
        gh run list --workflow=ci.yml --limit 1 2>/dev/null | head -5 || echo "GitHub CLI 不可用"
    fi

    # 检查是否有新的构建产物或日志文件
    if [ -d "app/build" ] && [ -f "app/build/outputs/apk/debug/app-debug.apk" ]; then
        echo "✅ APK 构建成功!"
        break
    fi

    sleep 60
done

echo "监控完成。请检查 GitHub Actions 页面查看最新状态。"