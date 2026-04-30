# 客服小秘 - 设计系统

## 🎨 色彩系统 (Color System)

### 主色调 (Primary Colors)
- **深海军蓝** `#1A237E` - 主要品牌色、按钮、导航栏
- **海洋青绿** `#00695C` - 次要强调色、悬停状态
- **暖珊瑚橙** `#FF6F00` - 行动按钮、重要提示

### 中性色 (Neutral Colors)
- **浅灰白** `#F8F9FA` - 背景色、卡片背景
- **深炭黑** `#2C3E50` - 文字主色、高对比度内容
- **表面变体** `#E8EAED` - 次级表面元素

### 语义化颜色 (Semantic Colors)
- **错误** `#DC3545` - 错误状态、警告
- **成功** `#28A745` - 成功状态
- **信息** `#17A2B8` - 信息提示

## 📝 字体层级 (Typography Hierarchy)

### 标题层级 (Headlines)
- **Display Large**: Inter Bold 32px, line-height: 40px
- **Display Medium**: Inter Bold 28px, line-height: 36px
- **Display Small**: Inter Bold 24px, line-height: 32px

### 副标题 (Subheadings)
- **Headline Large**: Inter SemiBold 22px
- **Headline Medium**: Inter SemiBold 18px
- **Headline Small**: Inter SemiBold 16px

### 正文 (Body Text)
- **Body Large**: Inter Regular 16px, line-height: 24px
- **Body Medium**: Inter Regular 14px, line-height: 20px
- **Body Small**: Inter Regular 12px, line-height: 16px

### 标签 (Labels)
- **Label Large**: Inter Medium 14px
- **Label Medium**: Inter Medium 12px
- **Label Small**: Inter Medium 11px

## 📏 间距系统 (Spacing System)

### 基础网格 (Base Grid)
- **8px** 基础单位
- 所有间距都是8的倍数

### 组件间距 (Component Spacing)
- **Button Padding**: 16px 24px
- **Card Padding**: 20px 24px
- **Section Spacing**: 24px
- **Page Padding**: 16px

### Material Design 3 间距
- Navigation bar height: 56dp
- Bottom navigation height: 80dp
- Card elevation: 4dp

## 🧩 组件规范 (Component Specifications)

### 按钮 (Buttons)
```kotlin
// 主按钮
OutlinedButton(
    onClick = { /* TODO */ },
    modifier = Modifier.fillMaxWidth()
) {
    Text("主操作")
}

// 次按钮
OutlinedButton(
    onClick = { /* TODO */ },
    modifier = Modifier.fillMaxWidth()
) {
    Text("次要操作按钮")
}
```

### 卡片 (Cards)
```kotlin
Card(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surface
    ),
    elevation = CardDefaults.cardElevation(4.dp),
    shape = MaterialTheme.shapes.medium
) {
    Column(
        modifier = Modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Card content
    }
}
```

### 底部导航 (Bottom Navigation)
- 高度: 80dp
- 圆角: 24dp (顶部)
- 选中指示器颜色: primaryContainer
- 图标大小: 24dp
- 文字样式: labelSmall + SemiBold

## 🌙 主题支持 (Theme Support)

### 亮色主题 (Light Theme)
- 背景: #F8F9FA (浅灰白)
- 表面: #FFFFFF (白色)
- 主色: #1A237E (深海军蓝)

### 暗色主题 (Dark Theme)
- 背景: #111827 (深灰黑)
- 表面: #1F2937 (中灰)
- 主色: #93C5FD (浅蓝色)

## 📱 响应式设计 (Responsive Design)

### 断点 (Breakpoints)
- **Mobile**: ≤ 480px (默认)
- **Tablet**: > 480px and ≤ 768px
- **Desktop**: > 768px

### 容器宽度 (Container Widths)
- **Mobile**: max-width: 480px
- **Tablet**: max-width: 768px
- **Desktop**: max-width: 1200px

## 🔧 开发指南 (Development Guide)

### 添加新组件时遵循
1. 使用Material 3组件
2. 遵循间距系统
3. 使用正确的颜色语义
4. 支持明暗主题
5. 添加适当的动画过渡

### 代码示例
```kotlin
@Composable
fun CustomComponent() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp), // 使用系统间距
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp) // 使用系统间距
        ) {
            Text(
                text = "组件标题",
                style = MaterialTheme.typography.headlineSmall // 使用正确层级
            )
            Text(
                text = "组件描述文本",
                style = MaterialTheme.typography.bodyMedium // 使用正确层级
            )
        }
    }
}
```

## ✅ 检查清单 (Checklist)

- [ ] 使用正确的颜色值
- [ ] 遵循字体层级系统
- [ ] 使用8px网格间距
- [ ] 支持明暗主题切换
- [ ] 添加适当的阴影效果
- [ ] 确保足够的对比度
- [ ] 测试在不同屏幕尺寸下的显示效果
