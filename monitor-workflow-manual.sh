#!/bin/bash

echo "=== GitHub Actions 工作流监控 ==="
echo "开始时间: $(date)"
echo ""

# 等待工作流启动
echo "等待工作流启动..."
sleep 60

# 循环检查工作流状态
for i in {1..15}; do
    echo "第 $i 次检查 - $(date)"

    # 尝试获取最新工作流运行
    if gh run list --limit 1 --branch main 2>/dev/null; then
        echo "✅ 成功连接到 GitHub API"
        break
    else
        echo "⚠️  网络连接问题，重试中..."
    fi

    sleep 60
done

echo ""
echo "监控结束。请手动检查:"
echo "1. https://github.com/forcoder/msg-monitor/actions"
echo "2. 查看最新的 CI/CD 工作流状态"