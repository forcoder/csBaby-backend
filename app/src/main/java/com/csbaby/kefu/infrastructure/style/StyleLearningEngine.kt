package com.csbaby.kefu.infrastructure.style

import com.csbaby.kefu.domain.model.ReplyHistory
import com.csbaby.kefu.domain.model.UserStyleProfile
import com.csbaby.kefu.domain.repository.ReplyHistoryRepository
import com.csbaby.kefu.domain.repository.UserStyleRepository
import com.csbaby.kefu.infrastructure.ai.AIService
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * User style learning engine that analyzes historical replies
 * and builds a style profile for the user.
 */
@Singleton
class StyleLearningEngine @Inject constructor(
    private val replyHistoryRepository: ReplyHistoryRepository,
    private val userStyleRepository: UserStyleRepository,
    private val aiService: AIService
) {
    companion object {
        private const val MIN_SAMPLES_FOR_AI_ANALYSIS = 8
        private const val MAX_LEARNING_SAMPLES = 1000 // Increased for better learning
        private const val DEEP_ANALYSIS_INTERVAL = 10 // Perform deep analysis every 10 samples
        private const val STYLE_STABILITY_THRESHOLD = 0.05f // Minimum change to update style
        private const val FEEDBACK_WEIGHT = 0.3f // Weight of user feedback in style updates
    }

    // Style learning metrics
    data class LearningMetrics(
        var totalSamples: Int = 0,
        var aiAnalysisCount: Int = 0,
        var styleStabilityScore: Float = 0f,
        var lastUpdateTime: Long = System.currentTimeMillis(),
        var confidenceTrend: List<Float> = emptyList()
    )

    /**
     * Enhanced learning from a new reply with feedback integration.
     */
    suspend fun learnFromReply(userId: String, reply: ReplyHistory, feedback: FeedbackAction? = null) {
        val finalReply = reply.finalReply.trim()
        if (finalReply.isBlank()) return

        // Update metrics
        val metrics = learningMetrics.getOrPut(userId) { LearningMetrics() }
        metrics.totalSamples++

        val currentProfile = userStyleRepository.getProfileSync(userId)
        val styleSignals = analyzeEnhancedStyleSignals(finalReply, reply)

        // Apply feedback weighting if available
        val adjustedSignals = if (feedback != null) {
            adjustStyleSignalsForFeedback(styleSignals, feedback)
        } else {
            styleSignals
        }

        val now = System.currentTimeMillis()
        val updatedProfile = if (currentProfile == null) {
            createInitialProfile(userId, adjustedSignals, now)
        } else {
            updateExistingProfile(currentProfile, adjustedSignals, now, metrics)
        }

        // Save profile
        if (currentProfile == null) {
            userStyleRepository.saveProfile(updatedProfile)
        } else {
            userStyleRepository.updateProfile(updatedProfile)
        }

        // Enhanced deep analysis scheduling
        if (shouldPerformDeepAnalysis(updatedProfile, metrics)) {
            performEnhancedDeepAnalysis(userId, updatedProfile, metrics)
        }

        // Update metrics
        metrics.lastUpdateTime = now
        updateConfidenceTrend(metrics, updatedProfile.accuracyScore)

        Timber.d("Style learning completed for user $userId, samples: ${updatedProfile.learningSamples}")
    }

    private fun createInitialProfile(
        userId: String,
        signals: LocalStyleSignals,
        now: Long
    ): UserStyleProfile {
        return UserStyleProfile(
            userId = userId,
            formalityLevel = signals.formality,
            enthusiasmLevel = signals.enthusiasm,
            professionalismLevel = signals.professionalism,
            wordCountPreference = signals.lengthPreference,
            commonPhrases = signals.commonPhrases,
            learningSamples = 1,
            accuracyScore = calculateEnhancedAccuracyScore(1),
            createdAt = now,
            lastTrained = now
        )
    }

    private fun updateExistingProfile(
        current: UserStyleProfile,
        signals: LocalStyleSignals,
        now: Long,
        metrics: LearningMetrics
    ): UserStyleProfile {
        val newSampleCount = current.learningSamples + 1

        // Enhanced blending with stability checks
        val formalityChange = kotlin.math.abs(current.formalityLevel - signals.formality)
        val enthusiasmChange = kotlin.math.abs(current.enthusiasmLevel - signals.enthusiasm)
        val professionalismChange = kotlin.math.abs(current.professionalismLevel - signals.professionalism)

        // Only update if change is significant enough to matter
        val shouldUpdateFormality = formalityChange > STYLE_STABILITY_THRESHOLD || newSampleCount < 5
        val shouldUpdateEnthusiasm = enthusiasmChange > STYLE_STABILITY_THRESHOLD || newSampleCount < 5
        val shouldUpdateProfessionalism = professionalismChange > STYLE_STABILITY_THRESHOLD || newSampleCount < 5

        return current.copy(
            formalityLevel = if (shouldUpdateFormality) {
                blendMetric(current.formalityLevel, signals.formality, current.learningSamples)
            } else current.formalityLevel,

            enthusiasmLevel = if (shouldUpdateEnthusiasm) {
                blendMetric(current.enthusiasmLevel, signals.enthusiasm, current.learningSamples)
            } else current.enthusiasmLevel,

            professionalismLevel = if (shouldUpdateProfessionalism) {
                blendMetric(current.professionalismLevel, signals.professionalism, current.learningSamples)
            } else current.professionalismLevel,

            wordCountPreference = blendLengthPreference(
                current.wordCountPreference,
                signals.lengthPreference,
                current.learningSamples
            ),

            commonPhrases = mergeEnhancedCommonPhrases(current.commonPhrases, signals.commonPhrases),

            learningSamples = newSampleCount,
            accuracyScore = calculateEnhancedAccuracyScore(newSampleCount),
            lastTrained = now
        )
    }

    private fun shouldPerformDeepAnalysis(profile: UserStyleProfile, metrics: LearningMetrics): Boolean {
        // Perform deep analysis based on multiple criteria
        val sampleThreshold = when {
            profile.accuracyScore > 0.8f -> MIN_SAMPLES_FOR_AI_ANALYSIS / 2 // High confidence, fewer samples needed
            profile.learningSamples >= 50 -> MIN_SAMPLES_FOR_AI_ANALYSIS * 2 // Large dataset
            else -> DEEP_ANALYSIS_INTERVAL * profile.learningSamples
        }

        val timeSinceLastAnalysis = System.currentTimeMillis() - metrics.lastUpdateTime
        val hoursThreshold = when {
            profile.accuracyScore > 0.7f -> 24 // High confidence models can analyze more frequently
            else -> 48 // Default frequency
        }

        val shouldAnalyzeBySamples = profile.learningSamples % sampleThreshold == 0
        val shouldAnalyzeByTime = timeSinceLastAnalysis > hoursThreshold * 3600000L

        return shouldAnalyzeBySamples && shouldAnalyzeByTime
    }

    private fun performEnhancedDeepAnalysis(
        userId: String,
        profile: UserStyleProfile,
        metrics: LearningMetrics
    ) {
        try {
            val recentReplies = replyHistoryRepository.getRecentReplies(MAX_LEARNING_SAMPLES)
                .first()
                .filter { it.finalReply.isNotBlank() && it.styleApplied }

            if (recentReplies.size < MIN_SAMPLES_FOR_AI_ANALYSIS) {
                Timber.d("Not enough styled replies for deep analysis: ${recentReplies.size}")
                return
            }

            // Select representative samples for analysis
            val representativeSamples = selectRepresentativeSamples(recentReplies, MIN_SAMPLES_FOR_AI_ANALYSIS)

            val combinedText = representativeSamples.joinToString("\n---\n") { it.finalReply }

            val analysisResult = aiService.analyzeTextStyle(combinedText)
            analysisResult.fold(
                onSuccess = { analysis ->
                    // Weight AI analysis based on confidence
                    val aiWeight = minOf(profile.accuracyScore, 0.9f)
                    val localWeight = 1.0f - aiWeight

                    // Calculate weighted averages
                    val weightedFormality = analysis.formality * aiWeight + profile.formalityLevel * localWeight
                    val weightedEnthusiasm = analysis.enthusiasm * aiWeight + profile.enthusiasmLevel * localWeight
                    val weightedProfessionalism = analysis.professionalism * aiWeight + profile.professionalismLevel * localWeight

                    // Extract enhanced phrases
                    val aiPhrases = extractAdvancedPhrases(representativeSamples.map { it.finalReply })

                    val updatedProfile = profile.copy(
                        formalityLevel = weightedFormality.coerceIn(0f, 1f),
                        enthusiasmLevel = weightedEnthusiasm.coerceIn(0f, 1f),
                        professionalismLevel = weightedProfessionalism.coerceIn(0f, 1f),
                        commonPhrases = mergeEnhancedCommonPhrases(profile.commonPhrases, aiPhrases),
                        accuracyScore = calculateEnhancedAccuracyScore(profile.learningSamples),
                        lastTrained = System.currentTimeMillis()
                    )

                    userStyleRepository.updateProfile(updatedProfile)
                    metrics.aiAnalysisCount++

                    Timber.i("Enhanced deep analysis completed, AI weight: ${(aiWeight * 100).toInt()}%")
                },
                onFailure = { error ->
                    Timber.e("Enhanced deep analysis failed: ${error.message}")
                    // Fallback to local analysis only
                    performLocalFallbackAnalysis(userId, profile, metrics)
                }
            )
        } catch (e: Exception) {
            Timber.e(e, "Exception in enhanced deep analysis")
            performLocalFallbackAnalysis(userId, profile, metrics)
        }
    }

    private fun performLocalFallbackAnalysis(userId: String, profile: UserStyleProfile, metrics: LearningMetrics) {
        val recentReplies = replyHistoryRepository.getRecentReplies(20)
            .first()
            .filter { it.finalReply.isNotBlank() }

        if (recentReplies.isNotEmpty()) {
            val localPhrases = extractAdvancedPhrases(recentReplies.map { it.finalReply })
            val updatedProfile = profile.copy(
                commonPhrases = mergeEnhancedCommonPhrases(profile.commonPhrases, localPhrases),
                lastTrained = System.currentTimeMillis()
            )
            userStyleRepository.updateProfile(updatedProfile)
        }
    }

    private fun adjustStyleSignalsForFeedback(signals: LocalStyleSignals, feedback: FeedbackAction): LocalStyleSignals {
        val adjustmentFactor = when (feedback) {
            FeedbackAction.ACCEPTED -> 0f // No adjustment for accepted replies
            FeedbackAction.MODIFIED -> 0.1f // Slight adjustment for modified replies
            FeedbackAction.REJECTED -> 0.3f // Stronger adjustment for rejected replies
        }

        return signals.copy(
            formalityLevel = signals.formalityLevel + (adjustmentFactor * 0.1f).coerceIn(-0.2f, 0.2f),
            enthusiasmLevel = signals.enthusiasmLevel + (adjustmentFactor * 0.1f).coerceIn(-0.2f, 0.2f),
            professionalismLevel = signals.professionalismLevel + (adjustmentFactor * 0.1f).coerceIn(-0.2f, 0.2f)
        )
    }

    private fun selectRepresentativeSamples(replies: List<ReplyHistory>, count: Int): List<String> {
        // Simple selection strategy - could be enhanced with clustering
        val step = maxOf(1, replies.size / count)
        return replies.filterIndexed { index, _ -> index % step == 0 }.take(count).map { it.finalReply }
    }

    private fun extractAdvancedPhrases(texts: List<String>): List<String> {
        val phraseCounts = mutableMapOf<String, Int>()
        val stopWords = setOf("的", "了", "在", "是", "我", "有", "和", "就", "不", "人", "都", "一", "一个", "上", "也", "很", "到", "说", "要", "去", "你", "会", "着", "没有", "看", "好", "自己", "这")

        texts.forEach { text ->
            // Split by sentence boundaries and punctuation
            val sentences = text.split(Regex("[。！？；,.!?;\n]+"))
            sentences.forEach { sentence ->
                val words = sentence.trim().split(Regex("\\s+|[，,、]"))
                var phrase = ""

                words.forEach { word ->
                    val cleanWord = word.trim()
                    if (cleanWord.length in 2..8 && !stopWords.contains(cleanWord)) {
                        phrase += cleanWord
                        if (phrase.length <= 12) {
                            phraseCounts[phrase] = phraseCounts.getOrDefault(phrase, 0) + 1
                        }
                    } else {
                        // Reset phrase when we hit a stop word or long word
                        if (phrase.length >= 3 && phrase.length <= 12) {
                            phraseCounts[phrase] = phraseCounts.getOrDefault(phrase, 0) + 1
                        }
                        phrase = ""
                    }
                }

                // Handle remaining phrase
                if (phrase.length >= 3 && phrase.length <= 12) {
                    phraseCounts[phrase] = phraseCounts.getOrDefault(phrase, 0) + 1
                }
            }
        }

        return phraseCounts
            .filter { it.value >= 2 }
            .entries
            .sortedByDescending { it.value }
            .take(10)
            .map { it.key }
    }

    private fun mergeEnhancedCommonPhrases(existing: List<String>, incoming: List<String>): List<String> {
        val weights = linkedMapOf<String, Int>()

        // Weight existing phrases higher (they're proven)
        existing.forEachIndexed { index, phrase ->
            val bonus = (existing.size - index).coerceAtLeast(1) * 2
            weights[phrase] = weights.getOrDefault(phrase, 0) + bonus
        }

        // Weight new phrases lower but still give them credit
        incoming.forEach { phrase ->
            val bonus = 1
            weights[phrase] = weights.getOrDefault(phrase, 0) + bonus
        }

        return weights.entries
            .sortedByDescending { it.value }
            .map { it.key.trim() }
            .filter { it.length in 2..18 }
            .distinct()
            .take(12) // Increased from 8 to 12
    }

    private fun calculateEnhancedAccuracyScore(sampleCount: Int): Float {
        // More sophisticated accuracy calculation
        return when {
            sampleCount < 5 -> 0.1f // Very low confidence initially
            sampleCount < 20 -> (sampleCount.toFloat() / 25).coerceAtMost(0.5f) // Gradual increase
            sampleCount < 50 -> 0.5f + ((sampleCount - 20) * 0.015f).coerceAtMost(0.3f) // Steady increase
            sampleCount < 100 -> 0.8f + ((sampleCount - 50) * 0.005f).coerceAtMost(0.15f) // Diminishing returns
            else -> 0.95f // Near maximum for large datasets
        }.coerceIn(0f, 0.98f)
    }

    private fun updateConfidenceTrend(metrics: LearningMetrics, newConfidence: Float) {
        val trend = metrics.confidenceTrend.toMutableList()
        trend.add(newConfidence)

        // Keep only last 10 values for trend analysis
        if (trend.size > 10) {
            trend.removeAt(0)
        }

        metrics.confidenceTrend = trend

        // Check for confidence degradation
        if (trend.size >= 5) {
            val recentAverage = trend.takeLast(3).average().toFloat()
            val olderAverage = trend.take(trend.size - 3).average().toFloat()

            if (recentAverage < olderAverage * 0.8f) {
                Timber.w("Confidence trending downward, consider retraining style model")
            }
        }
    }

    /**
     * Batch learn from multiple replies.
     */
    suspend fun learnFromBatch(userId: String, replies: List<ReplyHistory>) {
        replies.forEach { reply ->
            learnFromReply(userId, reply)
        }
    }

    /**
     * Perform deep style analysis using AI.
     */
    private suspend fun performDeepAnalysis(userId: String, profile: UserStyleProfile) {
        val recentReplies = replyHistoryRepository.getRecentReplies(MAX_LEARNING_SAMPLES)
            .first()
            .filter { it.finalReply.isNotBlank() }
        if (recentReplies.size < MIN_SAMPLES_FOR_AI_ANALYSIS) {
            Timber.d("Not enough samples for deep analysis: ${recentReplies.size} < $MIN_SAMPLES_FOR_AI_ANALYSIS")
            return
        }

        val combinedText = recentReplies.take(10).joinToString("\n---\n") { it.finalReply }

        try {
            val analysisResult = aiService.analyzeTextStyle(combinedText)
            analysisResult.fold(
                onSuccess = { analysis ->
                    val latestProfile = userStyleRepository.getProfileSync(userId) ?: profile
                    val weightedSamples = (latestProfile.learningSamples / 2).coerceAtLeast(MIN_SAMPLES_FOR_AI_ANALYSIS)
                    val mergedPhrases = mergeCommonPhrases(
                        latestProfile.commonPhrases,
                        extractCommonPhrases(recentReplies.map { it.finalReply })
                    )
                    val updatedProfile = latestProfile.copy(
                        formalityLevel = blendMetric(latestProfile.formalityLevel, analysis.formality, weightedSamples),
                        enthusiasmLevel = blendMetric(latestProfile.enthusiasmLevel, analysis.enthusiasm, weightedSamples),
                        professionalismLevel = blendMetric(latestProfile.professionalismLevel, analysis.professionalism, weightedSamples),
                        commonPhrases = mergedPhrases,
                        accuracyScore = calculateAccuracyScore(latestProfile.learningSamples),
                        lastTrained = System.currentTimeMillis()
                    )
                    userStyleRepository.updateProfile(updatedProfile)
                    Timber.d("Deep analysis completed for user $userId, samples: ${latestProfile.learningSamples}")
                },
                onFailure = { error ->
                    Timber.e("Deep style analysis failed: ${error.message}")
                    // Even if AI analysis fails, we can still update the profile with local analysis
                    val localPhrases = extractCommonPhrases(recentReplies.map { it.finalReply })
                    val latestProfile = userStyleRepository.getProfileSync(userId) ?: profile
                    val updatedProfile = latestProfile.copy(
                        commonPhrases = mergeCommonPhrases(latestProfile.commonPhrases, localPhrases),
                        lastTrained = System.currentTimeMillis()
                    )
                    userStyleRepository.updateProfile(updatedProfile)
                }
            )
        } catch (e: Exception) {
            Timber.e(e, "Exception during deep style analysis: ${e.message}")
        }
    }

    private fun blendMetric(currentValue: Float, newValue: Float, sampleCount: Int): Float {
        val safeSamples = sampleCount.coerceAtLeast(1)
        // 随着样本数量增加，给予新值更低的权重，保持模型的稳定性
        val newWeight = 1.0f / (safeSamples + 1)
        val currentWeight = 1.0f - newWeight
        // 平滑过渡，避免突然的风格变化
        return (currentValue * currentWeight) + (newValue * newWeight)
    }

    private fun blendLengthPreference(currentValue: Int, newValue: Int, sampleCount: Int): Int {
        val safeSamples = sampleCount.coerceAtLeast(1)
        // 同样使用加权平均，保持长度偏好的稳定性
        val newWeight = 1.0f / (safeSamples + 1)
        val currentWeight = 1.0f - newWeight
        return ((currentValue * currentWeight) + (newValue * newWeight)).toInt()
    }

    /**
     * Calculate accuracy score based on number of samples.
     */
    private fun calculateAccuracyScore(sampleCount: Int): Float {
        return (sampleCount.toFloat() / (sampleCount + 12)).coerceIn(0f, 0.98f)
    }

    /**
     * Extract common phrases from replies.
     */
    private fun extractCommonPhrases(replies: List<String>): List<String> {
        val phraseCounts = mutableMapOf<String, Int>()

        replies.forEach { reply ->
            reply.split(Regex("[，。！？；：,.!?;:\n]+"))
                .map { it.trim() }
                .filter { it.length in 2..18 }
                .forEach { phrase ->
                    phraseCounts[phrase] = phraseCounts.getOrDefault(phrase, 0) + 1
                }
        }

        return phraseCounts
            .filter { it.value >= 2 }
            .entries
            .sortedByDescending { it.value }
            .take(8)
            .map { it.key }
    }

    private fun mergeCommonPhrases(existing: List<String>, incoming: List<String>): List<String> {
        val weights = linkedMapOf<String, Int>()
        existing.forEachIndexed { index, phrase ->
            val bonus = (existing.size - index).coerceAtLeast(1)
            weights[phrase] = weights.getOrDefault(phrase, 0) + bonus + 1
        }
        incoming.forEach { phrase ->
            weights[phrase] = weights.getOrDefault(phrase, 0) + 2
        }

        return weights.entries
            .sortedByDescending { it.value }
            .map { it.key.trim() }
            .filter { it.length in 2..18 }
            .distinct()
            .take(8)
    }

    private fun analyzeLocalStyleSignals(text: String): LocalStyleSignals {
        val normalizedText = text.trim()
        val visibleLength = normalizedText.filterNot { it.isWhitespace() }.length.coerceAtLeast(1)
        val lowerText = normalizedText.lowercase()

        // 扩展标记词列表，提高分析准确性
        val formalMarkers = listOf("您", "您好", "请", "感谢", "麻烦", "抱歉", "辛苦", "为您", "核实", "安排", "尊敬的", "请问", "谢谢", "不好意思", "打扰您", "请您", "能否", "是否", "敬请", "恳请")
        val casualMarkers = listOf("哈", "呀", "啦", "哦", "呢", "亲", "宝", "～", "~", "嗨", "嘿", "哇", "哟", "啊", "嗯", "哎", "喂")
        val warmMarkers = listOf("感谢", "谢谢", "好的", "没问题", "尽快", "马上", "安排", "收到", "很高兴", "乐意", "随时", "随时为您", "竭诚", "全力", "用心", "热情")
        val professionalMarkers = listOf("核实", "确认", "处理", "反馈", "订单", "系统", "平台", "售后", "方案", "安排", "流程", "规范", "标准", "专业", "服务", "质量", "效率", "优化", "改进", "提升")
        val emojiMarkers = listOf("😊", "😄", "😁", "🙏", "🌹", "❤️", "👍", "🎉", "✨", "⭐", "🔥", "💪", "🤝", "🙌")

        // 计算正式度
        val formality = (
            0.45f +
                scoreMarkers(normalizedText, formalMarkers, 0.06f) -
                scoreMarkers(normalizedText, casualMarkers, 0.05f) +
                // 考虑句子长度和标点使用
                (if (normalizedText.length > 50) 0.1f else 0f) +
                (if (normalizedText.contains("。") && normalizedText.split("。").size > 2) 0.05f else 0f)
            ).coerceIn(0.05f, 0.98f)

        // 计算热情度
        val enthusiasm = (
            0.35f +
                scoreMarkers(normalizedText, warmMarkers, 0.07f) +
                scoreMarkers(normalizedText, emojiMarkers, 0.1f) +
                normalizedText.count { it == '!' || it == '！' } * 0.08f +
                normalizedText.count { it == '~' || it == '～' } * 0.05f +
                // 考虑感叹词和重复标点
                (if (Regex("[！!]{2,}").containsMatchIn(normalizedText) || Regex("[～~]{2,}").containsMatchIn(normalizedText)) 0.1f else 0f)
            ).coerceIn(0.05f, 0.98f)

        // 计算专业度
        val professionalism = (
            0.45f +
                scoreMarkers(normalizedText, professionalMarkers, 0.07f) +
                if ("为您" in normalizedText || "麻烦您" in normalizedText) 0.08f else 0f -
                scoreMarkers(lowerText, listOf("哈哈", "hh", "宝子", "亲亲", "哈哈哈哈", "呵呵"), 0.09f) +
                // 考虑专业术语和结构化表达
                (if (Regex("[0-9]+[%\u4E00-\u9FA5]").containsMatchIn(normalizedText)) 0.05f else 0f) +
                (if (normalizedText.contains("流程") || normalizedText.contains("规范") || normalizedText.contains("标准")) 0.1f else 0f)
            ).coerceIn(0.05f, 0.98f)

        // 提取常用短语，使用更智能的算法
        val commonPhrases = extractCommonPhrases(listOf(normalizedText))

        return LocalStyleSignals(
            formality = formality,
            enthusiasm = enthusiasm,
            professionalism = professionalism,
            lengthPreference = visibleLength.coerceIn(6, 120),
            commonPhrases = commonPhrases
        )
    }

    private fun scoreMarkers(text: String, markers: List<String>, weight: Float): Float {
        val hitCount = markers.count { marker -> text.contains(marker, ignoreCase = true) }
        return hitCount * weight
    }

    private data class LocalStyleSignals(
        val formality: Float,
        val enthusiasm: Float,
        val professionalism: Float,
        val lengthPreference: Int,
        val commonPhrases: List<String>
    )

    /**
     * Get style profile for a user.
     */
    suspend fun getStyleProfile(userId: String): UserStyleProfile? {
        return userStyleRepository.getProfileSync(userId)
    }

    /**
     * Manually update style profile parameters.
     */
    suspend fun updateStyleParameters(
        userId: String,
        formality: Float? = null,
        enthusiasm: Float? = null,
        professionalism: Float? = null
    ) {
        formality?.let { userStyleRepository.updateFormalityLevel(userId, it) }
        enthusiasm?.let { userStyleRepository.updateEnthusiasmLevel(userId, it) }
        professionalism?.let { userStyleRepository.updateProfessionalismLevel(userId, it) }
    }

    /**
     * Apply style profile to generated reply.
     */
    suspend fun applyStyle(text: String, userId: String): Result<String> {
        val profile = userStyleRepository.getProfileSync(userId)
            ?: return Result.success(text)

        return aiService.adjustStyle(text, profile)
    }

    /**
     * Generate a system prompt that incorporates the user's style.
     */
    fun generateStyleSystemPrompt(profile: UserStyleProfile): String {
        val formalityDesc = when {
            profile.formalityLevel < 0.3 -> "casual and conversational"
            profile.formalityLevel < 0.5 -> "semi-formal"
            profile.formalityLevel < 0.7 -> "formal"
            else -> "very formal"
        }

        val enthusiasmDesc = when {
            profile.enthusiasmLevel < 0.3 -> "reserved"
            profile.enthusiasmLevel < 0.5 -> "neutral"
            profile.enthusiasmLevel < 0.7 -> "friendly"
            else -> "warm and enthusiastic"
        }

        val professionalismDesc = when {
            profile.professionalismLevel < 0.3 -> "approachable"
            profile.professionalismLevel < 0.5 -> "knowledgeable"
            profile.professionalismLevel < 0.7 -> "professional"
            else -> "expert"
        }

        val avgLength = profile.wordCountPreference
        val commonPhrases = if (profile.commonPhrases.isNotEmpty()) {
            "\nPreferred expressions: ${profile.commonPhrases.take(5).joinToString(", ")}"
        } else ""
        val styleConfidence = (profile.accuracyScore * 100).toInt().coerceIn(0, 98)

        return """
            You are a customer service assistant mirroring the owner's reply style.
            - Tone: $formalityDesc, $enthusiasmDesc
            - Professionalism: $professionalismDesc
            - Typical response length: around $avgLength Chinese characters
            - Style confidence: $styleConfidence%
            $commonPhrases

            Keep the core meaning accurate, but make the wording feel like it was written by the same person replying in this chat.
        """.trimIndent()
    }
}
