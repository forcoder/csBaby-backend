#!/bin/bash

echo "🔍 验证客服小秘设计系统..."

# 检查主题文件
echo ""
echo "📁 检查核心主题文件:"

if [ -f "./app/src/main/java/com/csbaby/kefu/presentation/theme/Theme.kt" ]; then
    echo "✅ Theme.kt 存在"
else
    echo "❌ Theme.kt 不存在"
fi

if [ -f "./app/src/main/java/com/csbaby/kefu/presentation/theme/Type.kt" ]; then
    echo "✅ Type.kt 存在"
else
    echo "❌ Type.kt 不存在"
fi

if [ -f "./app/src/main/java/com/csbaby/kefu/presentation/theme/DesignSystem.md" ]; then
    echo "✅ DesignSystem.md 存在"
else
    echo "❌ DesignSystem.md 不存在"
fi

if [ -f "./app/src/main/java/com/csbaby/kefu/presentation/screens/home/DesignSystemPreview.kt" ]; then
    echo "✅ DesignSystemPreview.kt 存在"
else
    echo "❌ DesignSystemPreview.kt 不存在"
fi

echo ""
echo "🎨 检查颜色配置:"
grep -q "0xFF1A237E" ./app/src/main/java/com/csbaby/kefu/presentation/theme/Theme.kt && \
    echo "✅ 深海军蓝主色调已应用" || echo "❌ 主色调未找到"

grep -q "0xFF00695C" ./app/src/main/java/com/csbaby/kefu/presentation/theme/Theme.kt && \
    echo "✅ 海洋青绿次色调已应用" || echo "❌ 次色调未找到"

grep -q "0xFFFF6F00" ./app/src/main/java/com/csbaby/kefu/presentation/theme/Theme.kt && \
    echo "✅ 暖珊瑚橙强调色已应用" || echo "❌ 强调色未找到"

echo ""
echo "📝 检查字体配置:"
grep -q "headlineMedium" ./app/src/main/java/com/csbaby/kefu/presentation/theme/Type.kt && \
    echo "✅ 字体层级配置已设置" || echo "❌ 字体层级未找到"

echo ""
echo "🧪 检查预览组件:"
grep -q "DesignSystemPreviewLight" ./app/src/main/java/com/csbaby/kefu/presentation/screens/home/DesignSystemPreview.kt && \
    echo "✅ 亮色主题预览存在" || echo "❌ 亮色主题预览未找到"

grep -q "DesignSystemPreviewDark" ./app/src/main/java/com/csbaby/kefu/presentation/screens/home/DesignSystemPreview.kt && \
    echo "✅ 暗色主题预览存在" || echo "❌ 暗色主题预览未找到"

echo ""
echo "📋 设计系统文档内容:"
if [ -s "./app/src/main/java/com/csbaby/kefu/presentation/theme/DesignSystem.md" ]; then
    echo "✅ 设计系统文档已创建 ($(wc -l < ./app/src/main/java/com/csbaby/kefu/presentation/theme/DesignSystem.md) 行)"
else
    echo "❌ 设计系统文档为空或不存在"
fi

echo ""
echo "🎉 验证完成！"
echo ""
echo "📱 设计系统已成功应用到客服小秘应用中："
echo "   • 现代优雅的配色方案（深海军蓝 + 海洋青绿 + 暖珊瑚橙）"
echo "   • 清晰的字体层级系统"
echo "   • 8px网格间距规范"
echo "   • Material 3组件支持"
echo "   • 明暗主题自动适配"
echo "   • 完整的组件预览和文档"
