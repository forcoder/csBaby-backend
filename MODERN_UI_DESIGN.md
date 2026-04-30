# 客服小秘 - 现代化UI界面设计

## 1. 设计理念更新

### 1.1 设计目标
- **视觉现代化**：采用更简洁、现代的Material 3设计语言
- **用户体验优化**：减少认知负荷，提高操作效率
- **情感化交互**：通过微动画和视觉反馈增强用户参与感
- **性能优化**：确保流畅的60fps动画和即时响应

### 1.2 设计原则
- **简约而不简单**：去除冗余元素，突出核心功能
- **智能引导**：通过视觉层次引导用户完成关键操作
- **一致性**：跨模块保持统一的设计语言和交互模式
- **可访问性**：支持色盲用户和辅助技术

## 2. 主屏幕现代化重设计

### 2.1 新布局结构

```
[顶部应用栏]
┌─────────────────────────────────┐
│ 状态卡片 (监控状态 + 快速操作)  │
├─────────────────────────────────┤
│ 数据概览 (统计信息网格)          │
├─────────────────────────────────┤
│ 最近回复列表                    │
│ ┌─────────────┐ ┌─────────────┐ │
│ │ 客户消息    │ │ AI回复      │ │
│ └─────────────┘ └─────────────┘ │
└─────────────────────────────────┘
```

### 2.2 核心组件重新设计

#### 2.2.1 状态卡片组件
```kotlin
@Composable
fun MonitoringStatusCard(
    isMonitoring: Boolean,
    monitoringMode: MonitoringMode = MonitoringMode.AUTO,
    onToggle: () -> Unit,
    onSettings: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isMonitoring) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        elevation = CardDefaults.cardElevation(elevation = 4.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 头部区域
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "AI助手状态",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = when {
                            isMonitoring -> "正在监听新消息"
                            else -> "已暂停监听"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // 状态指示器
                AnimatedContainer(
                    targetState = isMonitoring,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                ) { monitoring ->
                    Box(
                        modifier = Modifier
                            .size(if (monitoring) 16.dp else 8.dp)
                            .background(
                                color = if (monitoring) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outline
                                },
                                shape = CircleShape
                            )
                    )
                }
            }

            // 控制按钮组
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onToggle,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isMonitoring) {
                            MaterialTheme.colorScheme.errorContainer
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    )
                ) {
                    Icon(
                        imageVector = if (isMonitoring) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isMonitoring) "停止" else "开始")
                }

                OutlinedButton(
                    onClick = onSettings,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("设置")
                }
            }
        }
    }
}
```

#### 2.2.2 数据概览网格
```kotlin
@Composable
fun QuickStatsGrid(
    totalReplies: Int,
    todayReplies: Int,
    knowledgeBaseCount: Int,
    weeklyGrowth: Float
) {
    val stats = listOf(
        StatItem(
            title = "总回复数",
            value = totalReplies.toString(),
            subtitle = "累计服务",
            icon = Icons.Default.Assignment,
            color = MaterialTheme.colorScheme.primary
        ),
        StatItem(
            title = "今日回复",
            value = todayReplies.toString(),
            subtitle = "今日活跃",
            icon = Icons.Default.Today,
            color = MaterialTheme.colorScheme.secondary
        ),
        StatItem(
            title = "知识条目",
            value = knowledgeBaseCount.toString(),
            subtitle = "规则数量",
            icon = Icons.Default.LibraryBooks,
            color = MaterialTheme.colorScheme.tertiary
        ),
        StatItem(
            title = "周增长",
            value = "${(weeklyGrowth * 100).toInt()}%",
            subtitle = "较上周",
            icon = Icons.Default.TrendingUp,
            color = if (weeklyGrowth >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        )
    )

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.height(180.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(stats) { stat ->
            StatCard(stat = stat)
        }
    }
}

@Composable
fun StatCard(stat: StatItem) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { /* 查看详情 */ },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = stat.icon,
                    contentDescription = null,
                    tint = stat.color,
                    modifier = Modifier.size(24.dp)
                )

                if (stat.subtitle.isNotEmpty()) {
                    Text(
                        text = "↑",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (stat.color == MaterialTheme.colorScheme.primary) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        }
                    )
                }
            }

            Text(
                text = stat.value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Column {
                Text(
                    text = stat.title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (stat.subtitle.isNotEmpty()) {
                    Text(
                        text = stat.subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
```

#### 2.2.3 现代化回复卡片
```kotlin
@Composable
fun ModernReplyCard(
    replyHistory: ReplyHistoryUiModel,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        onClick = onClick,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 时间戳和状态
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatTime(replyHistory.sendTime),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    color = when (replyHistory.status) {
                        ReplyStatus.SUCCESS -> MaterialTheme.colorScheme.primaryContainer
                        ReplyStatus.PENDING -> MaterialTheme.colorScheme.secondaryContainer
                        ReplyStatus.ERROR -> MaterialTheme.colorScheme.errorContainer
                    },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = when (replyHistory.status) {
                            ReplyStatus.SUCCESS -> "已发送"
                            ReplyStatus.PENDING -> "发送中"
                            ReplyStatus.ERROR -> "发送失败"
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            // 客户消息
            MessageBubble(
                message = replyHistory.originalMessage,
                isOutgoing = false,
                timestamp = ""
            )

            // AI回复
            MessageBubble(
                message = replyHistory.finalReply,
                isOutgoing = true,
                timestamp = "",
                backgroundColor = MaterialTheme.colorScheme.primaryContainer
            )

            // 操作按钮
            if (replyHistory.canRetry || replyHistory.canEdit) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    if (replyHistory.canEdit) {
                        TextButton(
                            onClick = { /* 编辑回复 */ },
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("编辑")
                        }
                    }

                    if (replyHistory.canRetry) {
                        TextButton(onClick = { /* 重试发送 */ }) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("重试")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MessageBubble(
    message: String,
    isOutgoing: Boolean,
    timestamp: String,
    backgroundColor: Color = Color.Transparent
) {
    Surface(
        color = backgroundColor.takeIf { it != Color.Transparent } ?: 
            if (isOutgoing) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(
            topStart = 16.dp,
            topEnd = 16.dp,
            bottomStart = if (isOutgoing) 16.dp else 4.dp,
            bottomEnd = if (isOutgoing) 4.dp else 16.dp
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            if (timestamp.isNotEmpty()) {
                Text(
                    text = timestamp,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = if (backgroundColor != Color.Transparent) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
        }
    }
}
```

## 3. 导航结构优化

### 3.1 底部导航改进
```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModernBottomNavigation(
    currentRoute: String,
    onNavigate: (String) -> Unit
) {
    val items = listOf(
        BottomNavItem(
            route = "home",
            label = "首页",
            icon = Icons.Default.Home,
            activeIcon = Icons.Default.HomeFilled
        ),
        BottomNavItem(
            route = "knowledge",
            label = "知识库",
            icon = Icons.Default.LibraryBooks,
            activeIcon = Icons.Default.LibraryBooks
        ),
        BottomNavItem(
            route = "models",
            label = "模型",
            icon = Icons.Default.Settings,
            activeIcon = Icons.Default.SettingsSuggest
        ),
        BottomNavItem(
            route = "profile",
            label = "我的",
            icon = Icons.Default.Person,
            activeIcon = Icons.Default.AccountCircle
        )
    )

    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp,
        modifier = Modifier.padding(horizontal = 8.dp, bottom = 8.dp)
    ) {
        items.forEach { item ->
            NavigationBarItem(
                selected = currentRoute == item.route,
                onClick = { onNavigate(item.route) },
                icon = {
                    val icon = if (currentRoute == item.route) item.activeIcon else item.icon
                    Icon(
                        imageVector = icon,
                        contentDescription = item.label,
                        modifier = Modifier.size(24.dp)
                    )
                },
                label = {
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.labelSmall
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}
```

### 3.2 侧滑导航（平板适配）
```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResponsiveNavigationLayout(
    currentRoute: String,
    onNavigate: (String) -> Unit
) {
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    if (isTablet && isLandscape) {
        // 平板横屏：侧边栏 + 主内容
        Row(modifier = Modifier.fillMaxSize()) {
            NavigationRail(
                modifier = Modifier.width(72.dp),
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ) {
                // 侧边栏项目
                items.forEach { item ->
                    NavigationRailItem(
                        selected = currentRoute == item.route,
                        onClick = { onNavigate(item.route) },
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) }
                    )
                }
            }

            Divider(
                modifier = Modifier.width(1.dp),
                color = MaterialTheme.colorScheme.outline
            )

            // 主内容区域
            Box(modifier = Modifier.weight(1f)) {
                // 主屏幕内容
            }
        }
    } else {
        // 手机或竖屏平板：底部导航
        Scaffold(
            bottomBar = {
                ModernBottomNavigation(currentRoute, onNavigate)
            }
        ) { paddingValues ->
            Box(modifier = Modifier.padding(paddingValues)) {
                // 主屏幕内容
            }
        }
    }
}
```

## 4. 动画和过渡效果

### 4.1 页面转场动画
```kotlin
@Composable
fun AnimatedScreenTransition(
    targetScreen: Screen,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    AnimatedContent(
        targetState = targetScreen,
        transitionSpec = {
            if (targetState > initialState) {
                slideInHorizontally { width -> width } + fadeIn() with
                slideOutHorizontally { width -> -width } + fadeOut()
            } else {
                slideInHorizontally { width -> -width } + fadeIn() with
                slideOutHorizontally { width -> width } + fadeOut()
            }
        },
        modifier = modifier,
        contentKey = { it.route }
    ) { screen ->
        when (screen) {
            Screen.Home -> HomeScreen()
            Screen.Knowledge -> KnowledgeScreen()
            Screen.Models -> ModelScreen()
            Screen.Profile -> ProfileScreen()
        }
    }
}
```

### 4.2 列表项入场动画
```kotlin
@Composable
fun AnimatedLazyColumn(
    items: List<Any>,
    modifier: Modifier = Modifier,
    content: LazyListScope.() -> Unit
) {
    LazyColumn(modifier = modifier) {
        itemsIndexed(items) { index, item ->
            LaunchedEffect(index) {
                delay((index * 50).milliseconds)
            }

            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateItemPlacement(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMedium
                            )
                        )
                ) {
                    content()
                }
            }
        }
    }
}
```

### 4.3 加载状态动画
```kotlin
@Composable
fun LoadingShimmer(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
) {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmer_alpha"
    )

    Canvas(modifier = modifier) {
        drawRect(
            color = color.copy(alpha = alpha),
            size = size
        )
    }
}
```

## 5. 暗色主题增强

### 5.1 动态主题切换
```kotlin
@Composable
fun DynamicThemeWrapper(
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val dynamicColor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                DynamicColorScheme.getDynamicColorScheme(context)
            } else null
            dynamicColor ?: KefuThemeColors.lightColorScheme
        }
        else -> KefuThemeColors.lightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
```

### 5.2 暗色主题优化
```kotlin
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF93C5FD),           // 更柔和的蓝色
    onPrimary = Color(0xFF1E3A8A),
    primaryContainer = Color(0xFF1E40AF),
    onPrimaryContainer = Color(0xFFEFF6FF),

    secondary = Color(0xFF34D399),         // 更明亮的绿色
    onSecondary = Color(0xFF065F46),

    tertiary = Color(0xFFFCD34D),          // 温暖的黄色
    onTertiary = Color(0xFF7C2D12),

    background = Color(0xFF0F172A),        // 更深背景
    onBackground = Color(0xFFF1F5F9),

    surface = Color(0xFF1E293B),           // 中等表面色
    onSurface = Color(0xFFF1F5F9),

    error = Color(0xFFF87171),             // 更亮的错误色
    onError = Color(0xFF450A0A),
)

// 添加对比度优化
private val HighContrastDarkColorScheme = darkColorScheme(
    primary = Color(0xFFFFFFFF),
    onPrimary = Color(0xFF000000),
    // ... 其他高对比度颜色
)
```

## 6. 实施计划

### 6.1 阶段一：基础组件更新（1-2天）
- [ ] 更新按钮组件库
- [ ] 重设计卡片组件
- [ ] 优化表单控件
- [ ] 实现新的导航组件

### 6.2 阶段二：主屏幕重设计（2-3天）
- [ ] 实现现代化状态卡片
- [ ] 创建数据概览网格
- [ ] 重设计回复历史列表
- [ ] 添加流畅的动画效果

### 6.3 阶段三：全局优化（1-2天）
- [ ] 更新暗色主题
- [ ] 实现响应式布局
- [ ] 添加无障碍支持
- [ ] 性能优化和测试

### 6.4 阶段四：质量保障（1天）
- [ ] 全面UI测试
- [ ] 无障碍审计
- [ ] 性能基准测试
- [ ] 用户验收测试

---

*设计版本：v2.0.0*
*最后更新：2026年4月30日*
*设计负责人：UI/UX设计团队*