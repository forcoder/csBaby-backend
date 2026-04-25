# csBaby 项目全面测试点清单

> 基于实际源代码分析，覆盖正常功能、边界条件和异常情况。
> 标注说明：✅ 正常功能 | ⚠️ 边界条件 | ❌ 异常情况

---

## 一、KeywordMatcher（关键词匹配引擎）

### ✅ 正常功能测试

| 编号 | 测试点 | 说明 |
|------|--------|------|
| KM-001 | 精确匹配（EXACT）基本匹配 | 关键词"价格"匹配消息"请问价格是多少" |
| KM-002 | 包含匹配（CONTAINS）基本匹配 | 关键词"价格"包含匹配消息中的"价格" |
| KM-003 | 正则匹配（REGEX）基本匹配 | 正则`\d+元`匹配"房间100元" |
| KM-004 | 多关键词逗号分隔匹配 | 关键词"价格,多少钱,费用"中任一匹配即可 |
| KM-005 | 多关键词中文逗号分隔 | 关键词"价格，多少钱，费用" |
| KM-006 | 多关键词竖线分隔 | 关键词"价格\|多少钱\|费用" |
| KM-007 | 多关键词换行分隔 | 关键词"价格\n多少钱\n费用" |
| KM-008 | 优先级排序 | 高优先级规则的置信度获得加成 |
| KM-009 | 置信度计算-精确匹配类型加分 | EXACT类型额外+0.3f |
| KM-010 | 置信度计算-包含匹配类型加分 | CONTAINS类型额外+0.2f |
| KM-011 | 置信度计算-正则匹配类型加分 | REGEX类型额外+0.25f |
| KM-012 | 置信度计算-长度比贡献 | 匹配文本越长，置信度越高 |
| KM-013 | 置信度计算-优先级加成 | priority 10 的规则比 priority 0 多 +0.2f |
| KM-014 | 置信度范围限制在[0,1] | 使用 coerceIn(0f, 1f) 确保范围 |
| KM-015 | findMatches返回按置信度降序排列 | 高置信度结果在前 |
| KM-016 | findBestMatch返回最高置信度结果 | 仅返回一条最佳匹配 |
| KM-017 | 缓存命中返回缓存结果 | 相同消息第二次匹配走缓存 |
| KM-018 | clearCache清空缓存 | 清空后重新匹配 |
| KM-019 | initialize重置规则和缓存 | 重新初始化后旧缓存被清除 |
| KM-020 | 禁用规则不参与匹配 | enabled=false 的规则被过滤 |
| KM-021 | 模板变量替换 | applyTemplate("{price}元", {"price":"100"}) → "100元" |
| KM-022 | 模板变量忽略大小写替换 | {Price} 和 {price} 都能被替换 |
| KM-023 | 多位置匹配同一规则 | 消息中多处匹配同一关键词 |
| KM-024 | 同规则多关键词别名取最佳置信度 | groupBy后取maxBy置信度 |
| KM-025 | 匹配结果去重 | 同一规则只返回一条最佳结果 |

### ⚠️ 边界条件测试

| 编号 | 测试点 | 说明 |
|------|--------|------|
| KM-B01 | 空规则列表匹配 | initialize(emptyList()) 后匹配返回空 |
| KM-B02 | 空消息匹配 | 对空字符串消息进行匹配 |
| KM-B03 | 单字符关键词匹配 | 关键词为单个字符"好" |
| KM-B04 | 超长关键词匹配 | 关键词长度 > 100 字符 |
| KM-B05 | 超长消息匹配 | 消息长度 > 10000 字符 |
| KM-B06 | 消息与关键词完全相同 | message == keyword |
| KM-B07 | 置信度上限为1.0 | 所有加分项叠加不超过1.0 |
| KM-B08 | 置信度下限为0.0 | 最低置信度为0.0 |
| KM-B09 | 缓存达到上限1000条 | 超过CACHE_SIZE后不再缓存 |
| KM-B10 | 关键词别名去重 | "价格,价格"去重后只保留一个 |
| KM-B11 | 关键词前后空格trim | " 价格 "匹配时trim为"价格" |
| KM-B12 | 空别名过滤 | 关键词",,"拆分后过滤空字符串 |
| KM-B13 | 大小写不敏感匹配 | Trie树使用 lowerChar 存储和匹配 |
| KM-B14 | 优先级范围限制在[0,10] | priority 超过10按10计算 |
| KM-B15 | 规则ID不存在时过滤 | trieMatch中找不到对应rule则跳过 |
| KM-B16 | 模板无变量时原样返回 | applyTemplate无变量时不改变 |
| KM-B17 | 模板变量不存在时保留原样 | {notExist} 不被替换 |
| KM-B18 | 正则匹配大小写忽略 | RegexOption.IGNORE_CASE |

### ❌ 异常情况测试

| 编号 | 测试点 | 说明 |
|------|--------|------|
| KM-E01 | 无效正则表达式不崩溃 | 正则语法错误时catch Exception跳过 |
| KM-E02 | 未初始化直接匹配 | 未调用initialize时ensureTrieBuilt自动构建 |
| KM-E03 | 规则列表为null的防护 | （Kotlin类型系统保证） |
| KM-E04 | 并发匹配线程安全 | ConcurrentHashMap保证缓存安全 |
| KM-E05 | 特殊正则元字符 | 关键词含 `.*+?^${}[]\|()` 等字符 |

---

## 二、AIService（AI服务）

### ✅ 正常功能测试

| 编号 | 测试点 | 说明 |
|------|--------|------|
| AI-001 | 默认模型生成回复 | getDefaultModel() 获取默认模型并调用 |
| AI-002 | 指定模型生成回复 | generateCompletionWithModel(modelId) |
| AI-003 | 缓存命中直接返回 | 相同(prompt, systemPrompt, modelId)命中缓存 |
| AI-004 | 缓存未命中走API调用 | 新请求正常调用AI接口 |
| AI-005 | 重试机制-第2次成功 | 第1次失败后重试第2次成功 |
| AI-006 | 重试机制-第3次成功 | 连续失败后第3次成功 |
| AI-007 | 重试延迟指数退避 | delay = RETRY_DELAY_MS * attempt |
| AI-008 | 模型故障转移 | 默认模型失败后按lastUsed排序切换备用模型 |
| AI-009 | 故障转移成功后设为默认 | 备用模型成功后 setDefaultModel |
| AI-010 | 成本估算-OPENAI模型 | ModelType.OPENAI → $0.002/1k tokens |
| AI-011 | 成本估算-CLAUDE模型 | ModelType.CLAUDE → $0.001/1k tokens |
| AI-012 | 成本估算-智谱模型 | ModelType.ZHIPU → $0.001/1k tokens |
| AI-013 | 成本估算-通义千问模型 | ModelType.TONGYI → $0.001/1k tokens |
| AI-014 | 成本估算-自定义模型 | ModelType.CUSTOM → $0.0/1k tokens |
| AI-015 | 月成本超限检查 | monthlyCost >= $10 视为超限 |
| AI-016 | 超限模型跳过 | hasReachedUsageLimit=true 的模型不参与 |
| AI-017 | testModelConnection成功 | 返回 Result.success(true) |
| AI-018 | testModelConnection模型不存在 | 返回 "Model not found" |
| AI-019 | analyzeTextStyle返回解析结果 | 解析JSON返回 TextStyleAnalysis |
| AI-020 | adjustStyle调用成功 | 根据风格档案调整文本 |
| AI-021 | buildMessages包含system和user | systemPrompt + user prompt |
| AI-022 | buildMessages仅有user | systemPrompt为null时只有user消息 |
| AI-023 | 成功响应后更新lastUsed | updateLastUsed(modelId) |
| AI-024 | 成功响应后累加成本 | addCost(modelId, estimatedCost) |
| AI-025 | 缓存淘汰-移除最旧条目 | 超过500条时移除最旧的 |

### ⚠️ 边界条件测试

| 编号 | 测试点 | 说明 |
|------|--------|------|
| AI-B01 | 空prompt | 空字符串作为prompt |
| AI-B02 | 仅空格的systemPrompt | systemPrompt为"   " |
| AI-B03 | temperature为0 | 最低温度 |
| AI-B04 | temperature为1 | 最高温度 |
| AI-B05 | maxTokens为1 | 最小token数 |
| AI-B06 | 缓存刚好500条 | 达到上限边界 |
| AI-B07 | 缓存过期1小时 | CACHE_EXPIRY_MS = 3600000L |
| AI-B08 | 缓存过期后重新获取 | 过期后remove并重新请求 |
| AI-B09 | 月成本刚好$10 | monthlyCost == 10.0 的边界 |
| AI-B10 | 所有模型都超限 | 返回 "No enabled models available" |
| AI-B11 | 无默认模型但有其他模型 | 跳过默认直接遍历其他模型 |
| AI-B12 | 无默认模型且无其他模型 | 返回 "No enabled models available" |
| AI-B13 | styleProfile为null时不调整 | adjustStyle中profile为null |
| AI-B14 | 风格等级描述-formality | <0.3 casual, <0.5 semi-formal, <0.7 formal, else very formal |
| AI-B15 | 风格等级描述-enthusiasm | <0.3 reserved, <0.5 neutral, <0.7 friendly, else warm |
| AI-B16 | 风格等级描述-professionalism | <0.3 approachable, <0.5 knowledgeable, <0.7 professional, else expert |
| AI-B17 | analyzeTextStyle返回markdown代码块 | 响应包含```json...```时正确解析 |
| AI-B18 | analyzeTextStyle返回纯JSON | 响应为纯JSON时正确解析 |
| AI-B19 | 置信度coerceIn(0,1) | 风格分析值超出[0,1]时被截断 |

### ❌ 异常情况测试

| 编号 | 测试点 | 说明 |
|------|--------|------|
| AI-E01 | 3次重试全部失败 | 返回最后一次的异常 |
| AI-E02 | 网络超时 | SocketTimeoutException |
| AI-E03 | 所有模型都失败 | 返回 "All models failed" |
| AI-E04 | analyzeTextStyle JSON解析失败 | 返回默认分析值(0.5, 0.5, 0.5, 15) |
| AI-E05 | 无效JSON响应 | extractContentFromResponse 兜底提取 |
| AI-E06 | 模型ID不存在 | getModelById返回null |
| AI-E07 | 缓存操作并发安全 | ConcurrentHashMap保证 |

---

## 三、AIClient（AI网络客户端）

### ✅ 正常功能测试

| 编号 | 测试点 | 说明 |
|------|--------|------|
| AC-001 | OpenAI格式请求构建 | model + messages + temperature + max_tokens |
| AC-002 | Claude格式请求构建 | model + max_messages + system分离 + anthropic-version头 |
| AC-003 | 智谱格式请求构建 | 兼容OpenAI格式 |
| AC-004 | 通义千问格式请求构建 | 复用buildOpenAIRequest |
| AC-005 | 自定义模型请求构建 | 默认使用OpenAI格式 |
| AC-006 | NVIDIA API请求构建 | 识别nvidia.com域名 |
| AC-007 | OpenAI响应解析 | choices[0].message.content |
| AC-008 | Claude响应解析 | content[0].text |
| AC-009 | 自定义模型响应解析 | 先尝试OpenAI格式，失败尝试Claude格式 |
| AC-010 | 速率限制-允许请求 | 60秒内 < 60 次请求 |
| AC-011 | testConnection验证API密钥为空 | 返回 "API 密钥不能为空" |
| AC-012 | testConnection验证API地址为空 | 返回 "API 地址不能为空" |
| AC-013 | testConnection验证模型名称为空 | 返回 "模型名称不能为空" |
| AC-014 | testConnection成功但返回空内容 | 返回 "连接成功但模型返回空响应" |
| AC-015 | makeRawRequest构建完整URL | baseUrl + endpoint 拼接 |
| AC-016 | makeRawRequest识别绝对URL | endpoint以http开头直接使用 |
| AC-017 | 读取超时30秒 | READ_TIMEOUT_SECONDS = 30L |
| AC-018 | 连接超时10秒 | CONNECT_TIMEOUT_SECONDS = 10L |

### ⚠️ 边界条件测试

| 编号 | 测试点 | 说明 |
|------|--------|------|
| AC-B01 | 速率限制刚好60次/分钟 | 第61次请求被拒绝 |
| AC-B02 | 速率限制窗口滑动 | 超过60秒后旧时间戳被清除 |
| AC-B03 | API响应为空body | response.body?.string() 为null |
| AC-B04 | 模型名称为空时使用默认值 | OPENAI→gpt-3.5-turbo, CLAUDE→claude-3-haiku等 |
| AC-B05 | 响应同时包含choices和content | 优先使用choices解析（NVIDIA兼容） |
| AC-B06 | 友好错误信息-UnknownHost | "无法连接到 API 服务器" |
| AC-B07 | 友好错误信息-Connection refused | "连接被拒绝" |
| AC-B08 | 友好错误信息-401 | "API 密钥无效" |
| AC-B09 | 友好错误信息-404 | "API 地址或模型不存在" |
| AC-B10 | 友好错误信息-429 | "API 请求频率超限" |
| AC-B11 | 友好错误信息-500 | "API 服务器内部错误" |
| AC-B12 | 友好错误信息-SSL | "SSL 证书验证失败" |
| AC-B13 | 响应字段降级提取 | response → text → output → content → choices |
| AC-B14 | content为JSONArray时取第一个 | content[0].text |
| AC-B15 | content为String时直接返回 | content as String |

### ❌ 异常情况测试

| 编号 | 测试点 | 说明 |
|------|--------|------|
| AC-E01 | 网络不可达 | UnknownHostException |
| AC-E02 | 连接超时 | SocketTimeoutException |
| AC-E03 | SSL握手失败 | SSLException |
| AC-E04 | API返回非200状态码 | response.isSuccessful == false |
| AC-E05 | 速率超限返回友好提示 | "Rate limit exceeded" |
| AC-E06 | JSON解析完全失败 | extractContentFromResponse 返回原始responseBody |
| AC-E07 | 并发请求速率限制 | 多线程同时请求的计数准确性 |

---

## 四、MessageMonitor（消息监控器）

### ✅ 正常功能测试

| 编号 | 测试点 | 说明 |
|------|--------|------|
| MM-001 | emitMessage发送到Flow | messageFlow.collect能收到消息 |
| MM-002 | emitMessage通知所有监听器 | 所有listener.onNewMessage被调用 |
| MM-003 | addListener添加监听器 | 监听器被成功添加 |
| MM-004 | removeListener移除监听器 | 移除后不再收到通知 |
| MM-005 | 不重复添加同一监听器 | contains检查防止重复 |
| MM-006 | MonitoredMessage数据完整性 | packageName/appName/title/content等字段 |
| MM-007 | 默认时间戳为当前时间 | timestamp = System.currentTimeMillis() |
| MM-008 | SharedFlow缓冲64条 | extraBufferCapacity = 64 |

### ⚠️ 边界条件测试

| 编号 | 测试点 | 说明 |
|------|--------|------|
| MM-B01 | 空消息内容 | content为空字符串 |
| MM-B02 | 超长消息内容 | content > 10000字符 |
| MM-B03 | 特殊字符消息 | 包含emoji/换行/制表符 |
| MM-B04 | conversationTitle为null | 默认值null |
| MM-B05 | isGroupConversation为null | 默认值null |
| MM-B06 | 多个监听器同时接收 | 广播到所有监听器 |
| MM-B07 | 监听器列表为空时emit | 不崩溃 |

### ❌ 异常情况测试

| 编号 | 测试点 | 说明 |
|------|--------|------|
| MM-E01 | 某个监听器抛出异常 | 不影响其他监听器执行 |
| MM-E02 | 并发add/remove监听器 | synchronized保证线程安全 |
| MM-E03 | 快速连续emit消息 | 不丢失消息 |

---

## 五、ReplyOrchestrator（回复协调器）

### ✅ 正常功能测试

| 编号 | 测试点 | 说明 |
|------|--------|------|
| RO-001 | start启动消息收集 | collectorJob开始收集messageFlow |
| RO-002 | stop停止所有任务 | currentJob/collectorJob/matcherJob/iconObserverJob全部取消 |
| RO-003 | 重复start不重复启动collector | isActive==true时跳过 |
| RO-004 | 占位消息过滤-"给你发送了新消息" | 被shouldSkipMessage过滤 |
| RO-005 | 占位消息过滤-"向你发送了一条消息" | 被shouldSkipMessage过滤 |
| RO-006 | 占位消息过滤-"[图片]" | 精确匹配过滤 |
| RO-007 | 占位消息过滤-"[语音]" | 精确匹配过滤 |
| RO-008 | 占位消息过滤-"[视频]" | 精确匹配过滤 |
| RO-009 | 占位消息过滤-"[文件]" | 精确匹配过滤 |
| RO-010 | 占位消息过滤-"[表情]" | 精确匹配过滤 |
| RO-011 | 占位消息过滤-仅有媒体占位符 | "发送了 [图片] " 去掉媒体后无实质文字 |
| RO-012 | 内容长度<2的消息被过滤 | content.length < 2 |
| RO-013 | 黑名单过滤 | shouldFilterMessage返回true时跳过 |
| RO-014 | 监控未启用时跳过 | monitoringEnabled=false |
| RO-015 | 应用不在选中列表时跳过 | packageName not in selectedApps |
| RO-016 | 新消息取消旧任务 | currentJob?.cancel() |
| RO-017 | 构建ReplyContext | appPackage/conversationTitle/propertyName/isGroupConversation |
| RO-018 | 百居易消息提取propertyName | BAIJUYI_PACKAGE时从title提取 |
| RO-019 | 悬浮窗显示 | FloatingWindowService.show |
| RO-020 | 悬浮窗关闭时跳过显示 | floatingWindowEnabled=false |
| RO-021 | generateReplyForMessage手动触发 | 指定消息和appPackage |
| RO-022 | recordFinalReply记录用户回复 | 保存原始/生成/最终回复 |
| RO-023 | generateSuggestions生成多条建议 | 默认count=3 |
| RO-024 | iconObserverJob监听悬浮图标开关 | floatingIconEnabled变化时响应 |
| RO-025 | matcherJob初始化知识库 | knowledgeBaseManager.initializeMatcher() |

### ⚠️ 边界条件测试

| 编号 | 测试点 | 说明 |
|------|--------|------|
| RO-B01 | 消息内容刚好2字符 | content.length == 2 不过滤 |
| RO-B02 | 消息含媒体占位符但有实质文字 | "请看[图片]这个" 不过滤 |
| RO-B03 | conversationTitle为null时使用title | 提取propertyName时fallback |
| RO-B04 | conversationTitle和title都为空 | propertyName为null |
| RO-B05 | generateSuggestions count=0 | 返回空列表 |
| RO-B06 | generateSuggestions count=1 | 只返回1条 |
| RO-B07 | searchKnowledgeRules AUTO模式 | 根据preferences.searchMode决定 |
| RO-B08 | searchKnowledgeRules KEYWORD模式 | 强制关键词搜索 |
| RO-B09 | searchKnowledgeRules SEMANTIC模式 | 强制语义搜索 |
| RO-B10 | searchKnowledgeRules HYBRID模式 | 强制混合搜索 |
| RO-B11 | 搜索结果去重 | distinctBy rule.id |
| RO-B12 | 搜索结果上限15条 | take(15) |
| RO-B13 | conversationTitle预览截断 | 超过120字符截断为117+"..." |

### ❌ 异常情况测试

| 编号 | 测试点 | 说明 |
|------|--------|------|
| RO-E01 | handleNewMessage内部异常 | catch后打印日志不崩溃 |
| RO-E02 | collect消息时异常 | catch后打印日志 |
| RO-E03 | 回复生成取消 | CancellationException 被捕获 |
| RO-E04 | 回复生成异常 | catch后打印日志 |
| RO-E05 | start()本身异常 | try-catch保护 |
| RO-E06 | 语义搜索失败回退 | catch后返回空列表，不影响关键词结果 |
| RO-E07 | 模板搜索失败 | catch后返回空列表 |
| RO-E08 | 并发start/stop调用 | @Synchronized保护 |

---

## 六、ReplyGenerator（回复生成器）

### ✅ 正常功能测试

| 编号 | 测试点 | 说明 |
|------|--------|------|
| RG-001 | 知识库优先匹配 | KNOWLEDGE_BASE_FIRST=true |
| RG-002 | 知识库匹配成功返回 | source=RULE_MATCH, ruleId有值 |
| RG-003 | 知识库未匹配转AI生成 | tryKnowledgeBaseMatch返回null |
| RG-004 | AI生成成功返回 | source=AI_GENERATED, modelId有值 |
| RG-005 | AI失败返回兜底回复 | "感谢您的留言，我们会尽快处理您的问题。" |
| RG-006 | 风格学习启用时应用风格 | styleLearningEnabled && styleProfile!=null |
| RG-007 | 风格学习未启用时不调整 | styleLearningEnabled=false |
| RG-008 | recordUserReply记录历史 | insertReply + learnFromReply |
| RG-009 | 用户修改检测 | modified = generatedReply != finalReply |
| RG-010 | generateSuggestions合并知识库+AI | 先取知识库匹配，不足补AI |
| RG-011 | buildSystemPrompt包含基础提示词 | "professional customer service assistant" |
| RG-012 | buildSystemPrompt追加风格提示 | styleProfile!=null时追加 |
| RG-013 | buildUserPrompt包含消息和上下文 | appPackage/scenarioId |

### ⚠️ 边界条件测试

| 编号 | 测试点 | 说明 |
|------|--------|------|
| RG-B01 | 知识库匹配但置信度低于阈值 | 低于0.5f仍返回（代码中未过滤低置信度） |
| RG-B02 | styleProfile为null时系统提示词 | 仅使用basePrompt |
| RG-B03 | scenarioId为空 | 不拼接Scenario行 |
| RG-B04 | finalReply为空时不学习 | isNotBlank()检查 |
| RG-B05 | generateSuggestions知识库匹配数>=count | 不调用AI |
| RG-B06 | generateSuggestions知识库+AI总数<count | 返回已有的 |

### ❌ 异常情况测试

| 编号 | 测试点 | 说明 |
|------|--------|------|
| RG-E01 | AI生成抛出异常 | fold的onFailure返回null |
| RG-E02 | 知识库匹配异常 | tryKnowledgeBaseMatch catch |
| RG-E03 | 风格调整失败 | getOrDefault(reply) 回退原始回复 |

---

## 七、KnowledgeBaseManager（知识库管理器）

### ✅ 正常功能测试

| 编号 | 测试点 | 说明 |
|------|--------|------|
| KB-001 | createRule创建规则 | insertRule返回ID |
| KB-002 | updateRule更新规则 | 更新后数据正确 |
| KB-003 | deleteRule删除规则 | 删除成功 |
| KB-004 | getRuleById查询规则 | 返回正确规则 |
| KB-005 | getAllRules获取所有规则 | Flow<List<KeywordRule>> |
| KB-006 | getEnabledRules获取启用规则 | 仅返回enabled=true |
| KB-007 | getRulesByCategory按分类查询 | 返回指定分类 |
| KB-008 | toggleRule切换启用状态 | enabled取反 |
| KB-009 | searchRules关键词搜索 | 按关键词搜索 |
| KB-010 | getRuleCount规则计数 | 返回总数 |
| KB-011 | clearAllRules清空所有 | 返回删除数量 |
| KB-012 | initializeMatcher初始化匹配器 | keywordMatcher.initialize + hybridSearchEngine.initialize |
| KB-013 | findMatch查找最佳匹配 | keywordMatcher.findBestMatch |
| KB-014 | findAllMatches查找所有匹配 | keywordMatcher.findMatches |
| KB-015 | isRuleApplicableToContext-ALL类型始终适用 | targetType==ALL返回true |
| KB-016 | isRuleApplicableToContext-CONTACT类型 | 群聊返回false，非群聊检查targetNames匹配 |
| KB-017 | isRuleApplicableToContext-GROUP类型 | 非群聊返回false，群聊检查targetNames匹配 |
| KB-018 | isRuleApplicableToContext-PROPERTY类型 | 检查propertyName与targetNames匹配 |
| KB-019 | targetNames为空时始终适用 | targetNames.isEmpty()返回true |
| KB-020 | conversationTitle为null时 | CONTACT类型fallback: isNullOrBlank返回true |
| KB-021 | propertyName为null时PROPERTY规则 | fallback: 返回true（宽松策略） |
| KB-022 | matchesConversationTarget精确匹配 | normalizedTitle == normalizedTargetName |
| KB-023 | matchesConversationTarget包含匹配 | normalizedTitle.contains(normalizedTargetName) |
| KB-024 | normalizeForMatch去空格小写 | trim().lowercase() |
| KB-025 | propertyMatchPriority优先级排序 | PROPERTY匹配=3, 遗留PROPERTY=2, 其他target=1, ALL=0 |
| KB-026 | findAllMatches按优先级排序 | propertyMatchPriority → priority → confidence → matchedText.length |
| KB-027 | exportToJson导出JSON | 包含version/exportTime/rules |
| KB-028 | exportToCsv导出CSV | 包含表头和所有规则 |
| KB-029 | importFromJson导入JSON | 解析并插入规则 |
| KB-030 | importFromCsv导入CSV | 解析并插入规则 |
| KB-031 | importFromExcel导入xlsx | 解析多工作表并插入规则 |
| KB-032 | hybridSearch混合搜索 | keyword + semantic 合并结果 |
| KB-033 | applyTemplate模板变量替换 | keywordMatcher.applyTemplate |
| KB-034 | generateReplyFromRule生成回复 | applyTemplate(replyTemplate, variables) |
| KB-035 | getAllCategories获取所有分类 | Flow<List<String>> |

### ⚠️ 边界条件测试

| 编号 | 测试点 | 说明 |
|------|--------|------|
| KB-B01 | clearAllRules空库 | count=0时不执行删除 |
| KB-B02 | importFromJson空JSON | 返回 "JSON 中没有可导入的规则" |
| KB-B03 | importFromCsv空CSV | 返回 "CSV 中没有可导入的规则" |
| KB-B04 | importFromExcel空xlsx | 返回 "Excel 中没有可读取的工作表" |
| KB-B05 | importFromJson仅BOM字符 | 去除﻿后空白 |
| KB-B06 | importFromJson单行JSON | 无rules包裹的单个对象 |
| KB-B07 | importFromJson JSON数组 | [{...},{...}] |
| KB-B08 | importFromJson JSON Lines | 每行一个JSON对象 |
| KB-B09 | importFromJson带rules字段 | {"rules":[...]} |
| KB-B10 | importFromJson触发条件含"关键字"前缀 | 去除"关键字："前缀 |
| KB-B11 | importFromJson触发条件含"关键词"前缀 | 去除"关键词："前缀 |
| KB-B12 | importFromJson带"咨询问题"前缀 | 去除"咨询问题："前缀 |
| KB-B13 | importFromJson keyword为空但triggerCondition有值 | 从triggerCondition提取keyword |
| KB-B14 | importFromJson enabled为"启用"/"禁用" | 中文状态解析 |
| KB-B15 | importFromJson enabled为"true"/"false" | 布尔字符串解析 |
| KB-B16 | importFromJson enabled为1/0 | 数字字符串解析 |
| KB-B17 | importFromJson targetType中文映射 | "房源"/"民宿"→PROPERTY |
| KB-B18 | importFromJson targetType英文映射 | "CONTACT"/"GROUP"/"PROPERTY" |
| KB-B19 | importFromJson优先级默认0 | 无priority字段时为0 |
| KB-B20 | importFromCsv带BOM字符 | 去除﻿ |
| KB-B21 | importFromCsv中文表头 | "规则标题"/"回复内容"/"适用房源"等 |
| KB-B22 | importFromCsv英文表头 | keyword/reply_template/category等 |
| KB-B23 | importFromCsv混合引号字段 | "field, with comma" |
| KB-B24 | importFromCsv双引号转义 | "" → " |
| KB-B25 | importFromCsv空行跳过 | 全空字段的行跳过 |
| KB-B26 | importFromExcel旧版xls | 返回 "暂不支持旧版 .xls" |
| KB-B27 | importFromExcel sharedStrings | 类型为"s"时使用共享字符串 |
| KB-B28 | importFromExcel布尔类型 | 类型为"b"时1=true |
| KB-B29 | importFromExcel空工作表 | 跳过全空工作表 |
| KB-B30 | importFromExcel多工作表 | 每张工作表分别解析 |
| KB-B31 | importFromExcel列号计算 | A=0, Z=25, AA=26 |
| KB-B32 | importResult成功+错误计数 | successCount/errorCount |
| KB-B33 | isCsvHeaderRow检测 | 含已知表头字段则视为表头 |
| KB-B34 | normalizeCsvHeader中文保留 | 含非ASCII字符时不做lowercase |
| KB-B35 | normalizeCsvHeader英文转换 | lowercase+去空格+连字符转下划线 |
| KB-B36 | 关键词别名去重 | distinct() |
| KB-B37 | 关键词逗号分隔+trim | ", , " → 过滤空别名 |
| KB-B38 | importFromJson applicableScenarios | 解析scenarioIds列表 |
| KB-B39 | importFromJson targetNames JSON数组 | ["name1","name2"] |
| KB-B40 | importFromJson targetNames逗号分隔 | "name1,name2" |
| KB-B41 | importFromCsv categoryColumnMeansProperty | Excel导入时category列视为房源 |
| KB-B42 | legacy CSV解析（无表头） | 按位置解析8列 |

### ❌ 异常情况测试

| 编号 | 测试点 | 说明 |
|------|--------|------|
| KB-E01 | importFromJson JSON格式错误 | catch返回错误信息 |
| KB-E02 | importFromCsv CSV格式错误 | catch返回错误信息 |
| KB-E03 | importFromExcel Excel格式错误 | catch返回错误信息 |
| KB-E04 | importFromJson单条规则插入失败 | errorCount++，继续处理 |
| KB-E05 | hybridSearchEngine初始化失败 | catch后降级为纯关键词搜索 |
| KB-E06 | hybridSearch搜索异常 | catch后返回空列表 |
| KB-E07 | conversationTitle normalize后为空 | normalizeForMatch返回"" |
| KB-E08 | Excel ZipInputStream读取异常 | catch返回错误 |
| KB-E09 | Excel sharedString索引越界 | getOrNull(-1)返回null |
| KB-E10 | importFromJson status字段映射 | status="启用"→true, status="禁用"→false |
| KB-E11 | triggerType="咨询问题" | 推断category为"售前咨询" |
| KB-E12 | triggerType=其他 | 推断category为"通用问题" |

---

## 八、StyleLearningEngine（风格学习引擎）

### ✅ 正常功能测试

| 编号 | 测试点 | 说明 |
|------|--------|------|
| SL-001 | learnFromReply首次学习 | 创建新的UserStyleProfile |
| SL-02 | learnFromReply增量学习 | 更新已有profile的blendMetric |
| SL-003 | learnFromReply空回复跳过 | finalReply.isBlank()直接return |
| SL-004 | blendMetric加权平均 | newWeight = 1/(sampleCount+1) |
| SL-005 | blendMetric样本越多新值权重越低 | 稳定性递增 |
| SL-006 | blendLengthPreference长度偏好 | 加权平均后取整 |
| SL-007 | calculateAccuracyScore公式 | sampleCount/(sampleCount+12) |
| SL-008 | accuracyScore上限0.98 | coerceIn(0f, 0.98f) |
| SL-009 | extractCommonPhrases提取短语 | 2-18字，频率≥2 |
| SL-010 | extractCommonPhrases最多8条 | take(8) |
| SL-011 | mergeCommonPhrases合并短语 | 权重排序+去重+take(8) |
| SL-012 | 每10个样本触发深度分析 | learningSamples % 10 == 0 |
| SL-013 | performDeepAnalysis AI分析 | analyzeTextStyle(combinedText) |
| SL-014 | 深度分析最少5个样本 | MIN_SAMPLES_FOR_AI_ANALYSIS = 5 |
| SL-015 | 深度分析最多取500条 | MAX_LEARNING_SAMPLES = 500 |
| SL-016 | 深度分析取最近10条合并 | take(10) |
| SL-017 | analyzeLocalStyleSignals正式度 | 正式标记词+句子长度+标点 |
| SL-018 | analyzeLocalStyleSignals热情度 | 温暖标记+emoji+感叹号+波浪号 |
| SL-019 | analyzeLocalStyleSignals专业度 | 专业术语+结构化表达 |
| SL-020 | 正式度下限0.05 | coerceIn(0.05f, 0.98f) |
| SL-021 | 正式度上限0.98 | coerceIn(0.05f, 0.98f) |
| SL-022 | applyStyle应用风格 | aiService.adjustStyle |
| SL-023 | getStyleProfile获取档案 | userStyleRepository.getProfileSync |
| SL-024 | updateStyleParameters更新参数 | 分别更新formality/enthusiasm/professionalism |
| SL-025 | generateStyleSystemPrompt生成提示词 | 包含tone/professionalism/length/phrases |
| SL-026 | learnFromBatch批量学习 | 逐条调用learnFromReply |

### ⚠️ 边界条件测试

| 编号 | 测试点 | 说明 |
|------|--------|------|
| SL-B01 | 首个样本的blendMetric | sampleCount=0时safeSamples=1 |
| SL-B02 | 大量样本后accuracyScore趋近0.98 | sampleCount=500时 |
| SL-B03 | 正式度-长文本加成 | length>50时+0.1f |
| SL-B04 | 正式度-多句号加成 | 包含2+句号+0.05f |
| SL-B05 | 正式度-正式标记词 | 每个+0.06f |
| SL-B06 | 正式度-随意标记词 | 每个-0.05f |
| SL-B07 | 热情度-emoji加成 | 每个+0.1f |
| SL-B08 | 热情度-感叹号加成 | 每个+0.08f |
| SL-B09 | 热情度-波浪号加成 | 每个+0.05f |
| SL-B10 | 热情度-连续感叹号 | !!或！！+0.1f |
| SL-B11 | 专业度-专业术语 | 每个+0.07f |
| SL-B12 | 专业度-"为您"/"麻烦您" | +0.08f |
| SL-B13 | 专业度-随意词减分 | "哈哈"/"hh"等-0.09f |
| SL-B14 | 专业度-数字+中文 | 正则匹配+0.05f |
| SL-B15 | 短语长度范围[2,18] | 过滤超出范围的短语 |
| SL-B16 | 短语频率≥2才保留 | filter value>=2 |
| SL-B17 | 短语去重 | distinct() |
| SL-B18 | 深度分析AI结果合并 | weightedSamples = learningSamples/2 |
| SL-B19 | 风格提示词-formality分级 | <0.3 casual, <0.5 semi-formal, <0.7 formal, else very formal |
| SL-B20 | 风格提示词-enthusiasm分级 | <0.3 reserved, <0.5 neutral, <0.7 friendly, else warm |
| SL-B21 | 风格提示词-professionalism分级 | <0.3 approachable, <0.5 knowledgeable, <0.7 professional, else expert |
| SL-B22 | 风格提示词-常用短语 | 最多5条 |
| SL-B23 | 风格提示词-置信度显示 | accuracyScore * 100, coerceIn(0, 98) |
| SL-B24 | 可见字符长度计算 | filterNot isWhitespace |
| SL-B25 | 长度偏好范围[6,120] | coerceIn(6, 120) |

### ❌ 异常情况测试

| 编号 | 测试点 | 说明 |
|------|--------|------|
| SL-E01 | AI分析失败 | analysisResult.onSuccess不执行 |
| SL-E02 | applyStyle失败 | Result.success(text) 回退原文 |
| SL-E03 | getProfileSync返回null | applyStyle返回原文 |
| SL-E04 | 深度分析getProfileSync返回null | 使用传入的profile |
| SL-E05 | 短语提取空列表 | 无满足条件的短语 |

---

## 九、OtaManager（OTA更新管理器）

### ✅ 正常功能测试

| 编号 | 测试点 | 说明 |
|------|--------|------|
| OT-001 | checkForUpdate有更新 | 返回true, status=UPDATE_AVAILABLE |
| OT-002 | checkForUpdate无更新 | 返回false, status=IDLE |
| OT-003 | startDownload开始下载 | status=DOWNLOADING, 注册广播接收器 |
| OT-004 | 下载成功 | status=DOWNLOADED, progress=1.0 |
| OT-005 | 下载完成保存APK路径 | pendingApkFile设置 |
| OT-006 | triggerInstall安装APK | 触发系统安装页面 |
| OT-007 | installApk-FileProvider | Android N+使用FileProvider |
| OT-008 | installApk-直接Uri | Android N以下使用Uri.fromFile |
| OT-009 | cancelDownload取消下载 | 停止进度监控+注销广播+重置状态 |
| OT-010 | cleanup清理状态 | 取消下载+重置所有状态 |
| OT-011 | 下载进度监控 | 每秒更新progress |
| OT-012 | 下载目录创建 | getExternalFilesDir/Updates |
| OT-013 | 已存在文件先删除 | downloadFile.delete() |
| OT-014 | 状态流更新 | _updateStatus StateFlow |

### ⚠️ 边界条件测试

| 编号 | 测试点 | 说明 |
|------|--------|------|
| OT-B01 | APK文件小于1KB | 视为损坏，抛异常 |
| OT-B02 | Android O+安装权限检查 | canRequestPackageInstalls() |
| OT-B03 | 无安装权限时跳转设置 | ACTION_MANAGE_UNKNOWN_APP_SOURCES |
| OT-B04 | 无安装权限时回退DOWNLOADED | 授权后可重试 |
| OT-B05 | triggerInstall无pendingApkFile | 尝试从availableUpdate重新获取 |
| OT-B06 | triggerInstall无update | 返回 "无待安装的更新" |
| OT-B07 | triggerInstall文件不存在 | 返回 "APK文件未找到或已损坏" |
| OT-B08 | downloadId=-1时不操作 | cancelDownload中判断 |
| OT-B09 | 下载状态为SUCCESSFUL或FAILED | 停止进度监控 |
| OT-B10 | totalBytes=0时不更新进度 | 避免除零 |
| OT-B11 | 下载错误原因映射 | ERROR_CANNOT_RESUME等10种错误 |
| OT-B12 | resolveActivity为null | 抛异常 "没有应用可以处理安装请求" |

### ❌ 异常情况测试

| 编号 | 测试点 | 说明 |
|------|--------|------|
| OT-E01 | 检查更新网络异常 | catch后status=FAILED |
| OT-E02 | 下载失败广播 | STATUS_FAILED → 错误信息 |
| OT-E03 | APK文件不存在 | "无法找到下载文件" |
| OT-E04 | 安装异常 | catch后status回退DOWNLOADED |
| OT-E05 | 注销广播异常 | try-catch忽略 |
| OT-E06 | 进度监控异常 | catch后break |
| OT-E07 | checkForUpdate返回null | update==null → status=IDLE |

---

## 十、数据模型测试

### ✅ 正常功能测试

| 编号 | 测试点 | 说明 |
|------|--------|------|
| DM-001 | MatchType枚举值 | EXACT/CONTAINS/REGEX |
| DM-002 | ModelType枚举值 | OPENAI/CLAUDE/ZHIPU/TONGYI/CUSTOM |
| DM-003 | ReplySource枚举值 | RULE_MATCH/AI_GENERATED |
| DM-004 | RuleTargetType枚举值 | ALL/CONTACT/GROUP/PROPERTY |
| DM-005 | ScenarioType枚举值 | ALL_PROPERTIES/SPECIFIC_PROPERTY/SPECIFIC_PRODUCT |
| DM-006 | AIModelConfig默认值 | temperature=0.7f, maxTokens=1000 |
| DM-007 | UserStyleProfile默认值 | 三个维度均为0.5f |
| DM-008 | ReplyHistory默认值 | styleApplied=false, modified=false |
| DM-009 | MonitoredMessage默认值 | timestamp=currentTimeMillis |
| DM-010 | AppConfig默认值 | isMonitored=false |

### ⚠️ 边界条件测试

| 编号 | 测试点 | 说明 |
|------|--------|------|
| DM-B01 | AIModelConfig temperature范围 | 0.0-1.0（代码未校验，测试验证） |
| DM-B02 | UserStyleProfile 三个维度范围 | 0.0-1.0（代码未校验） |
| DM-B03 | KeywordRule priority范围 | coerceIn(0,10)仅在confidence计算时 |

---

## 统计汇总

| 模块 | ✅ 正常 | ⚠️ 边界 | ❌ 异常 | 合计 |
|------|---------|---------|---------|------|
| KeywordMatcher | 25 | 18 | 5 | 48 |
| AIService | 25 | 19 | 7 | 51 |
| AIClient | 18 | 15 | 7 | 40 |
| MessageMonitor | 8 | 7 | 3 | 18 |
| ReplyOrchestrator | 25 | 13 | 8 | 46 |
| ReplyGenerator | 13 | 6 | 3 | 22 |
| KnowledgeBaseManager | 35 | 42 | 12 | 89 |
| StyleLearningEngine | 26 | 25 | 5 | 56 |
| OtaManager | 14 | 12 | 7 | 33 |
| 数据模型 | 10 | 3 | 0 | 13 |
| **合计** | **199** | **160** | **57** | **416** |

---

> 文档生成日期：2026-04-25
> 基于实际源代码分析，覆盖 csBaby 项目全部核心模块