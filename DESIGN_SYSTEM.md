# 客服小秘 - UI设计系统

## 1. 设计原则

### 1.1 核心理念
- **简洁直观**：界面设计保持简约，确保用户能够快速理解和操作
- **智能高效**：突出AI助手的核心价值，减少不必要的操作步骤
- **情感化设计**：通过微动画和视觉反馈增强用户体验
- **无障碍访问**：遵循Material 3的无障碍设计指南

### 1.2 设计价值观
- **功能优先**：所有设计决策围绕核心功能展开
- **一致性**：组件和行为模式在整个应用中保持一致
- **可扩展性**：设计系统支持未来功能的扩展
- **性能导向**：优化资源使用，确保流畅的用户体验

## 2. Material 3 主题系统

### 2.1 色彩系统

#### 浅色主题调色板
```kotlin
// 主色调（Primary）
primary = #1A237E (深蓝色)
onPrimary = #FFFFFF (白色)
primaryContainer = #EFF6FF (浅蓝色背景)
onPrimaryContainer = #1E3A8A (中蓝色)

// 次要色调（Secondary）
secondary = #00695C (深绿色)
onSecondary = #FFFFFF (白色)
secondaryContainer = #D1FAE5 (浅绿色背景)
onSecondaryContainer = #065F46 (中绿色)

// 强调色（Tertiary）
tertiary = #FF6F00 (橙色)
onTertiary = #FFFFFF (白色)

// 背景和表面
background = #F8F9FA (浅灰白)
onBackground = #2C3E50 (深灰色)
surface = #FFFFFF (纯白)
onSurface = #1F2937 (中灰色)
surfaceVariant = #F3F4F6 (浅灰背景)
onSurfaceVariant = #4B5563 (灰黑色)

// 状态色
error = #E11D48 (红色)
onError = #FFFFFF (白色)
```

#### 深色主题调色板
```kotlin
// 主色调（Primary）
primary = #93C5FD (浅蓝色)
onPrimary = #1E3A8A (深蓝色)
primaryContainer = #1E40AF (中蓝色)
onPrimaryContainer = #EFF6FF (浅蓝背景)

// 次要色调（Secondary）
secondary = #34D399 (浅绿色)
onSecondary = #065F46 (深绿色)
secondaryContainer = #065F46 (深绿色)
onSecondaryContainer = #D1FAE5 (浅绿背景)

// 强调色（Tertiary）
tertiary = #FCD34D (黄色)
onTertiary = #7C2D12 (深红色)

// 背景和表面
background = #111827 (深黑)
onBackground = #F9FAFB (浅灰白)
surface = #1F2937 (深灰)
onSurface = #F9FAFB (浅灰白)
surfaceVariant = #374151 (中灰)
onSurfaceVariant = #D1D5DB (浅灰)

// 状态色
error = #F87171 (浅红)
onError = #450A0A (深红)
```

### 2.2 字体系统

#### Typography Scale
```kotlin
// Material 3 标准字体层级
displayLarge = 57sp / 64sp line height / SemiBold
displayMedium = 45sp / 52sp line height / Regular
displaySmall = 36sp / 44sp line height / Regular

headlineLarge = 32sp / 40sp line height / SemiBold
headlineMedium = 28sp / 36sp line height / SemiBold
headlineSmall = 24sp / 32sp line height / SemiBold

titleLarge = 22sp / 28sp line height / SemiBold
titleMedium = 16sp / 24sp line height / Medium
titleSmall = 14sp / 20sp line height / Medium

bodyLarge = 16sp / 24sp line height / Regular
bodyMedium = 14sp / 20sp line height / Regular
bodySmall = 12sp / 16sp line height / Regular

labelLarge = 14sp / 20sp line height / Medium
labelMedium = 12sp / 16sp line height / Medium
labelSmall = 11sp / 16sp line height / Medium
```

#### 中文字体配置
- **主要字体**: System UI (Android 默认)
- **Fallback字体**: Noto Sans CJK SC, Source Han Sans SC
- **英文字体**: Roboto, system sans-serif
- **数字字体**: Roboto Mono

### 2.3 间距系统

#### Spacing Scale
```kotlin
// 基础间距单位: 4dp
spacing = {
    xs = 4dp   // 微小间距
    sm = 8dp   // 小间距
    md = 16dp  // 中等间距
    lg = 24dp  // 大间距
    xl = 32dp  // 超大间距
    xxl = 48dp // 巨大间距
}

// 常用组合
componentPadding = md (16dp)
sectionSpacing = lg (24dp)
screenPadding = md (16dp)
cardElevation = 4dp
buttonHeight = 48dp
iconSize = 24dp
```

### 2.4 形状系统

#### Shape Tokens
```kotlin
shapes = {
    // 圆角级别
    small = 4dp    // 小圆角
    medium = 8dp   // 中等圆角
    large = 12dp   // 大圆角
    full = 9999dp  // 完全圆形

    // 具体应用
    buttonShape = medium (8dp)
    cardShape = medium (8dp)
    dialogShape = large (12dp)
    bottomSheetShape = large (12dp)
}
```

## 3. 组件设计规范

### 3.1 按钮组件

#### Primary Button
```kotlin
Button(
    onClick = { },
    modifier = Modifier.height(48.dp),
    shape = RoundedCornerShape(8.dp)
) {
    Text(
        text = "主要操作",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onPrimary
    )
}
```

#### Secondary Button
```kotlin
OutlinedButton(
    onClick = { },
    modifier = Modifier.height(48.dp),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
) {
    Text(
        text = "次要操作",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary
    )
}
```

#### Icon Button
```kotlin
IconButton(
    onClick = { },
    modifier = Modifier.size(48.dp)
) {
    Icon(
        imageVector = Icons.Default.Add,
        contentDescription = "添加",
        modifier = Modifier.size(24.dp)
    )
}
```

### 3.2 卡片组件

#### Elevated Card
```kotlin
Card(
    modifier = Modifier.fillMaxWidth(),
    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    shape = RoundedCornerShape(8.dp)
) {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Card content
    }
}
```

#### Filled Card
```kotlin
Card(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.primaryContainer
    ),
    shape = RoundedCornerShape(8.dp)
) {
    // Content with primary container background
}
```

### 3.3 导航组件

#### Bottom Navigation
```kotlin
NavigationBar(
    containerColor = MaterialTheme.colorScheme.surface,
    tonalElevation = 8.dp
) {
    bottomNavItems.forEach { screen ->
        NavigationBarItem(
            icon = {
                Icon(
                    screen.icon,
                    contentDescription = stringResource(screen.titleResId),
                    modifier = Modifier.size(24.dp)
                )
            },
            label = {
                Text(
                    stringResource(screen.titleResId),
                    style = MaterialTheme.typography.labelSmall
                )
            },
            selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
            onClick = {
                navController.navigate(screen.route) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        )
    }
}
```

### 3.4 表单组件

#### TextField
```kotlin
OutlinedTextField(
    value = text,
    onValueChange = { text = it },
    label = { Text("标签") },
    placeholder = { Text("提示文本") },
    supportingText = { Text("辅助说明文字") },
    leadingIcon = {
        Icon(Icons.Default.Search, contentDescription = null)
    },
    trailingIcon = {
        if (text.isNotEmpty()) {
            IconButton(onClick = { text = "" }) {
                Icon(Icons.Default.Clear, contentDescription = "清除")
            }
        }
    },
    singleLine = true,
    modifier = Modifier.fillMaxWidth()
)
```

#### Dropdown Menu
```kotlin
var expanded by remember { mutableStateOf(false) }
ExposedDropdownMenuBox(
    expanded = expanded,
    onExpandedChange = { expanded = !expanded }
) {
    OutlinedTextField(
        value = selectedOption,
        onValueChange = {},
        readOnly = true,
        label = { Text("选择选项") },
        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
        modifier = Modifier.menuAnchor().fillMaxWidth()
    )

    ExposedDropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false }
    ) {
        options.forEach { option ->
            DropdownMenuItem(
                text = { Text(option) },
                onClick = {
                    selectedOption = option
                    expanded = false
                }
            )
        }
    }
}
```

## 4. 响应式设计指南

### 4.1 断点定义
```kotlin
breakpoints = {
    extraSmall = 0dp     // < 600dp (手机竖屏)
    small = 600dp        // 600dp - 840dp (手机横屏/小平板)
    medium = 840dp       // 840dp - 1200dp (大平板)
    large = 1200dp       // 1200dp - 1600dp (桌面)
    extraLarge = 1600dp  // > 1600dp (大桌面)
}
```

### 4.2 布局策略

#### 单列布局（手机）
```
[顶部栏]
[主要内容区域]
[底部导航]

内容宽度：match_parent
内边距：16dp
```

#### 双列布局（平板横屏）
```
[侧边栏 | 主内容区域]

侧边栏宽度：240dp
主内容宽度：match_parent
总内边距：24dp
```

### 4.3 自适应组件

#### 自适应网格
```kotlin
LazyVerticalGrid(
    columns = GridCells.Adaptive(minSize = 120.dp),
    modifier = Modifier.fillMaxWidth(),
    contentPadding = PaddingValues(16.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp)
) {
    items(items) { item ->
        Card(modifier = Modifier.aspectRatio(1f)) {
            // 内容
        }
    }
}
```

#### 条件布局
```kotlin
val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

if (isLandscape) {
    Row(modifier = Modifier.fillMaxSize()) {
        Sidebar(modifier = Modifier.width(240.dp))
        MainContent(modifier = Modifier.weight(1f))
    }
} else {
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("标题") })
        MainContent(modifier = Modifier.weight(1f))
        BottomNavigation()
    }
}
```

## 5. 动效设计规范

### 5.1 转场动画

#### 页面切换
```kotlin
// 淡入淡出
AnimatedContent(targetState = targetScreen) { screen ->
    Box(
        modifier = Modifier
            .fillMaxSize()
            .animateEnterExit(
                enter = fadeIn(animationSpec = tween(300)),
                exit = fadeOut(animationSpec = tween(300))
            )
    ) {
        when (screen) {
            Screen.Home -> HomeScreen()
            Screen.Knowledge -> KnowledgeScreen()
            // ...
        }
    }
}
```

#### 列表项动画
```kotlin
LazyColumn {
    itemsIndexed(items) { index, item ->
        ItemCard(
            item = item,
            modifier = Modifier.animateItemPlacement(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
        )
    }
}
```

### 5.2 交互反馈

#### 按钮按压效果
```kotlin
Button(
    onClick = { },
    modifier = Modifier
        .height(48.dp)
        .clickable(
            interactionSource = interactionSource,
            indication = ripple(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
        ) { }
) {
    // 内容
}
```

#### 加载状态
```kotlin
var isLoading by remember { mutableStateOf(false) }

if (isLoading) {
    LinearProgressIndicator(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.primary
    )
}
```

## 6. 无障碍设计

### 6.1 可访问性检查清单

#### 颜色对比
- [ ] 文本与背景对比度 >= 4.5:1 (AA级)
- [ ] 大号文本对比度 >= 3:1 (AAA级)
- [ ] 状态指示器有额外的视觉提示

#### 触摸目标
- [ ] 最小触摸目标尺寸 >= 48dp
- [ ] 按钮之间有足够间距
- [ ] 避免重叠的点击区域

#### 屏幕阅读器
- [ ] 所有交互元素都有语义描述
- [ ] 动态内容更新通知辅助技术
- [ ] 焦点顺序符合逻辑阅读顺序

### 6.2 语义标签示例

```kotlin
IconButton(
    onClick = { },
    modifier = Modifier.semantics {
        contentDescription = "复制到剪贴板"
        role = Role.Button
    }
) {
    Icon(Icons.Default.ContentCopy, contentDescription = null)
}

// 状态指示器
val monitoringStatus = if (isMonitoring) "监控已开启" else "监控已关闭"

Text(
    text = monitoringStatus,
    modifier = Modifier.semantics {
        liveRegion = LiveRegion. polite
    }
)
```

## 7. 设计令牌管理

### 7.1 中央化配置
```kotlin
object DesignTokens {
    // 色彩
    object Colors {
        val primary = Color(0xFF1A237E)
        val secondary = Color(0xFF00695C)
        val error = Color(0xFFE11D48)
        // ...
    }

    // 间距
    object Spacing {
        val xs = 4.dp
        val sm = 8.dp
        val md = 16.dp
        val lg = 24.dp
        val xl = 32.dp
    }

    // 形状
    object Shapes {
        val small = 4.dp
        val medium = 8.dp
        val large = 12.dp
    }

    // 动画
    object Animation {
        val fast = 150.milliseconds
        val medium = 300.milliseconds
        val slow = 500.milliseconds
    }
}
```

### 7.2 主题扩展
```kotlin
@Composable
fun CustomTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = customColorScheme,
        typography = customTypography,
        shapes = customShapes,
        content = content
    )
}
```

## 8. 实施指南

### 8.1 开发流程
1. 检查现有组件是否符合设计规范
2. 创建新的设计令牌时更新中央配置文件
3. 实现时使用DesignTokens对象引用值
4. 进行无障碍测试
5. 在多种屏幕尺寸上验证响应式布局

### 8.2 质量保证
- 定期运行无障碍扫描工具
- 使用Compose Preview验证不同主题
- 在不同Android版本上进行兼容性测试
- 收集用户反馈并迭代优化

### 8.3 维护计划
- 每季度审查设计系统的一致性
- 根据Android和Material Design的更新调整规范
- 建立组件库文档和示例代码
- 为团队成员提供设计系统培训

---

*最后更新：2026年4月30日*
*版本：v1.0.0*
*维护者：UI/UX设计团队*