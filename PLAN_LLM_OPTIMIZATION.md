# 大模型功能管理与建议回复自优化迭代系统

## 目标

构建一个完整的"大模型功能管理 + 建议回复自优化迭代"系统，覆盖三个核心能力：

1. **大模型功能管理** — 对 LLM 的各项能力（回复生成、风格分析、意图识别等）进行精细化配置、A/B 测试和版本管理
2. **自优化迭代循环** — 基于用户对建议回复的修改行为和反馈（采纳/拒绝/修改），自动调整 prompt、模型参数和知识库规则，形成闭环优化
3. **数据驱动的洞察面板** — 提供优化效果的可视化仪表盘

---

## 1. 核心概念

### 1.1 LLM Feature（大模型功能）
每个大模型功能是一个独立的能力单元，例如：
- `reply_generation` — 建议回复生成
- `style_analysis` — 用户风格分析
- `intent_detection` — 客户意图识别
- `sentiment_analysis` — 情感分析
- `knowledge_summary` — 知识库摘要
- `keyword_extraction` — 关键词提取

### 1.2 Feature Variant（功能变体）
每个功能可以有多个变体（variant），用于 A/B 测试：
- **Prompt Variant** — 不同的 system prompt / user prompt 模板
- **Model Variant** — 不同的模型/参数配置（temperature, max_tokens）
- **Strategy Variant** — 不同的处理策略（比如知识库优先 vs AI 直接生成）

### 1.3 Optimization Loop（优化循环）
```
用户请求 → 生成回复建议 → 用户采纳/修改 → 记录反馈 → 分析效果 → 自动优化
                                                                       ↓
            知识库规则更新 ← 效果达标 ← A/B测试 ← 生成新变体 ←←
```

---

## 2. 数据模型

### 2.1 新数据库表

#### `llm_features` — 大模型功能定义
| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long(PK) | 主键 |
| feature_key | String | 功能标识符（唯一） |
| display_name | String | 展示名称 |
| description | String | 功能描述 |
| is_enabled | Boolean | 是否启用 |
| default_variant_id | Long? | 默认变体ID |
| created_at | Long | 创建时间 |
| updated_at | Long | 更新时间 |

#### `feature_variants` — 功能变体
| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long(PK) | 主键 |
| feature_id | Long(FK) | 所属功能ID |
| variant_name | String | 变体名称 |
| variant_type | String | PROMPT / MODEL / STRATEGY |
| system_prompt | String | System prompt 模板 |
| user_prompt_template | String | User prompt 模板（带占位符） |
| model_id | Long? | 关联的模型配置ID |
| temperature | Float | 温度参数（覆盖） |
| max_tokens | Int | 最大token数（覆盖） |
| strategy_config | String | JSON策略配置 |
| is_active | Boolean | 是否在A/B测试中 |
| traffic_percentage | Int | 流量分配百分比(0-100) |
| created_at | Long | 创建时间 |

#### `optimization_metrics` — 优化指标记录
| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long(PK) | 主键 |
| feature_key | String | 功能标识符 |
| variant_id | Long | 变体ID |
| date | String | 统计日期（YYYY-MM-DD） |
| total_generated | Int | 总生成次数 |
| total_accepted | Int | 总采纳次数（用户直接发送） |
| total_modified | Int | 总修改次数 |
| total_rejected | Int | 总拒绝次数（用户关闭/忽略） |
| avg_confidence | Float | 平均置信度 |
| avg_response_time_ms | Long | 平均响应时间 |
| accuracy_score | Float | AI评估的准确率分数 |

#### `optimization_events` — 优化事件日志
| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long(PK) | 主键 |
| feature_key | String | 功能标识符 |
| event_type | String | AUTO_OPTIMIZE / MANUAL_TUNE / A_B_TEST |
| old_config | String | JSON，旧配置 |
| new_config | String | JSON，新配置 |
| reason | String | 优化原因 |
| triggered_by | String | SYSTEM / USER |
| created_at | Long | 创建时间 |

#### `reply_feedback` — 回复级别的细粒度反馈
| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long(PK) | 主键 |
| reply_history_id | Long(FK->reply_history) | 关联的回复历史 |
| variant_id | Long? | 生成时使用的变体ID |
| user_action | String | ACCEPTED / MODIFIED / REJECTED |
| modified_part | String? | 用户修改的部分内容 |
| user_rating | Int? | 用户评分(1-5) |
| feedback_text | String? | 用户反馈文本 |
| created_at | Long | 创建时间 |

### 2.2 扩展现有表

在 `ReplyHistoryEntity` 中添加字段：
- `feature_key: String?` — 使用的功能标识
- `variant_id: Long?` — 使用的变体ID

在 `AppConfigEntity` / PreferencesManager 中添加：
- 每个功能启用的 A/B 测试开关
- 优化循环的自动执行间隔

---

## 3. 核心组件设计

### 3.1 LMMFeatureManager（大模型功能管理器）
```
LMMFeatureManager
├── getFeature(featureKey) → 获取功能配置
├── getActiveVariant(featureKey) → 获取当前活跃变体（考虑A/B流量分配）
├── createVariant(featureKey, config) → 创建新变体
├── activateABTest(featureKey, variantIds, percentages) → 启动A/B测试
└── recordFeedback(featureKey, context, result) → 记录反馈
```

### 3.2 OptimizationEngine（自优化引擎）
```
OptimizationEngine
├── collectMetrics() → 收集各变体的效果指标
├── analyzePerformance() → 分析哪些变体表现更好
├── generateOptimization() → 基于分析生成新的优化变体
│   ├── optimizePrompt() → 用AI优化prompt模板
│   ├── tuneParameters() → 调整temperature等参数
│   └── updateKnowledgeRule() → 根据高频修改自动创建知识库规则
├── autoPromote() → 效果显著优于现有方案时自动提升
└── scheduleOptimization() → 定期触发优化循环
```

### 3.3 AutoRuleGenerator（自动规则生成器）
```
AutoRuleGenerator
├── analyzeModificationPatterns() → 分析用户修改模式
│   └── 找出"用户经常把AI生成的回复改成什么"
├── generateKeywordRules() → 自动生成关键词规则
│   └── 对于高频场景，从修改中提取关键词+回复模板
└── suggestRuleImprovements() → 建议改进现有规则
    └── 对已有关键词规则，基于采纳率给出改进建议
```

### 3.4 OptimizationDashboard（优化仪表盘）
提供 Compose UI 展示：
- 各功能变体的采纳率趋势图
- 修改率 vs 直接采纳率对比
- 各模型的性能对比
- 优化事件时间线
- 自动生成的规则建议列表

---

## 4. A/B 测试流量分配策略

### 4.1 分层分配
```
用户请求 → hash(userId + featureKey) % 100
  ├── < trafficA → Variant A
  ├── < trafficA + trafficB → Variant B
  └── else → Default Variant
```

### 4.2 确定性的用户分桶
- 基于 userId + featureKey 的哈希值做分桶
- 保证同一用户在同一功能下始终看到同一变体（一致性）
- 避免同一个对话中变体不一致导致体验割裂

---

## 5. 实施步骤

### Phase 1: 基础设施
1. 创建新的数据库实体和 DAO（llm_features, feature_variants, optimization_metrics, optimization_events, reply_feedback）
2. 更新 KefuDatabase 版本
3. 创建对应的 Repository 接口和实现
4. 添加 Hilt DI 模块

### Phase 2: 核心逻辑
1. 实现 `LMMFeatureManager` — 功能配置管理 + A/B 测试流量分配
2. 实现 `OptimizationEngine` — 指标收集 + 分析 + 自动优化
3. 实现 `AutoRuleGenerator` — 修改模式分析 + 规则生成
4. 集成到 `ReplyGenerator` 中，替换硬编码的 prompt，使用 feature variant

### Phase 3: UI
1. 功能管理界面 — 查看/创建/编辑功能变体
2. 优化仪表盘 — 指标趋势和效果对比
3. 规则建议列表 — 展示自动生成的规则建议

### Phase 4: 自动优化闭环
1. 定时优化任务（WorkManager / 协程定期任务）
2. 自动 A/B 测试结果分析和大使变体推
3. 知识库规则自动更新

---

## 6. 关键算法

### 6.1 采纳率统计
```
acceptanceRate(variantId, dateRange) = accepted / (accepted + modified + rejected)
modificationRate(variantId, dateRange) = modified / (accepted + modified + rejected)
```

### 6.2 变体提升判定
```
improvement = newVariant.acceptanceRate - baseline.acceptanceRate
if improvement > threshold(0.05) AND confidence > 0.95:
    autoPromote(newVariant)
```

### 6.3 规则自动生成
```
1. 按场景分组用户修改记录
2. 对每组提取: 原始消息→用户最终回复
3. 找出现频率 > threshold 的 (keyword, template) 对
4. 用AI验证规则质量: {keyword} → {template} 是否合理
5. 生成关键词规则并建议给用户确认
```
