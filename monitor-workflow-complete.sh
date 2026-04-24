#!/bin/bash

echo "=== GitHub Actions 工作流监控与修复 ==="
echo "开始时间: $(date)"
echo "最新提交: ae94c77 (Room schema 配置修复)"
echo ""

# 等待工作流启动和运行
echo "等待工作流启动..."
sleep 120

echo ""
echo "开始监控工作流状态..."
echo "================================"

# 循环监控工作流
for i in {1..25}; do
    echo "第 $i 次检查 - $(date)"

    # 尝试获取工作流状态
    if gh run list --limit 3 --branch main 2>/dev/null; then
        echo "✅ 成功连接到 GitHub API"

        # 获取最新工作流运行
        latest_run=$(gh run list --limit 1 --branch main --json status,conclusion,createdAt,workflowName,url | jq '.[0]')

        if [ ! -z "$latest_run" ]; then
            status=$(echo "$latest_run" | jq -r '.status')
            conclusion=$(echo "$latest_run" | jq -r '.conclusion')
            workflow_name=$(echo "$latest_run" | jq -r '.workflowName')
            created_at=$(echo "$latest_run" | jq -r '.createdAt')
            url=$(echo "$latest_run" | jq -r '.url')

            echo "工作流: $workflow_name"
            echo "状态: $status"
            echo "结果: $conclusion"
            echo "创建时间: $created_at"
            echo "详情: $url"

            if [ "$status" = "completed" ]; then
                if [ "$conclusion" = "success" ]; then
                    echo ""
                    echo "🎉 恭喜！GitHub Actions 构建成功！"
                    echo "APK 应该已经生成并可以下载。"
                    exit 0
                else
                    echo ""
                    echo "❌ 构建失败，需要进一步分析错误日志"
                    echo "建议: 点击 URL 查看详细错误信息"
                    exit 1
                fi
            elif [ "$status" = "failed" ]; then
                echo ""
                echo "❌ 工作流运行失败"
                echo "建议: 检查构建日志中的具体错误"
                exit 1
            fi
        fi
    else
        echo "⚠️  网络连接问题，重试中..."
    fi

    sleep 60
done

echo ""
echo "监控结束。请手动检查:"
echo "https://github.com/forcoder/msg-monitor/actions"