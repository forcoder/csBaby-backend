# 首页数字点击跳转 + 知识库归零设计

## 需求

1. 首页 QuickStatsCard 中的"总回复"、"今日"、"知识库"三个数字点击后跳转到对应页面
2. "总回复"和"今日"跳转到新建的历史记录页面（按应用分组展示全部历史）
3. "知识库"跳转到知识库页面
4. 知识库清空后，首页数字应显示 0

## 架构

```
HomeScreen (QuickStatsCard)
  ├─ 总回复 → 点击 → navController.navigate("history")
  ├─ 今日   → 点击 → navController.navigate("history")
  └─ 知识库 → 点击 → navController.navigate("knowledge")

AppNavigation
  └─ composable("history") { HistoryScreen() }
```

## 新增文件

### HistoryViewModel
- 使用 `combine` 合并 4 个应用的 `getRepliesByApp()` Flow
- 过滤空分组，生成 `List<AppHistoryGroup>`
- 通过 Hilt 注入 `ReplyHistoryRepository`

### HistoryScreen
- TopAppBar 标题"历史记录"，带返回按钮
- LazyColumn 展示按应用分组的历史记录
- 每组：应用名（蓝色粗体）+ 该应用的历史回复卡片
- 空状态显示"暂无历史记录"
- 加载中显示 CircularProgressIndicator

## 修改文件

### QuickStatsCard / StatItem
- 新增 `onTotalRepliesClick`、`onTodayRepliesClick`、`onKnowledgeBaseClick` 回调参数
- StatItem 加 `clickable` 修饰符

### HomeScreen
- 新增 `onNavigateToHistory`、`onNavigateToKnowledge` 回调参数
- 传入 QuickStatsCard 的点击回调

### AppNavigation
- 新增 `composable("history")` 路由
- HomeScreen 传入导航回调（history 用 navigate，knowledge 用底部导航方式跳转）

### ReplyHistoryDao
- 新增 `getTodayCount(startOfDay: Long): Int` 查询

### ReplyHistoryRepository / ReplyHistoryRepositoryImpl
- 新增 `getTodayCount(startOfDay: Long): Int` 方法

### HomeViewModel
- 用 `replyHistoryRepository.getTodayCount(startOfDay)` 替代原来基于 recentReplies 的估算
- 新增 `getStartOfDay()` 辅助方法

## 知识库归零机制

已有机制：`HomeViewModel` 通过 `keywordRuleRepository.getRuleCountFlow()` 实时收集数量
上一轮修复：`deleteAllRules()` 先删后端再删本地 → Room DB 清空 → Flow emit 0 → UI 自动更新

## 编译验证

- BUILD SUCCESSFUL
- 已安装到手机
