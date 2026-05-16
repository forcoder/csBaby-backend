package com.csbaby.kefu.data.sync

import com.csbaby.kefu.data.local.PreferencesManager
import com.csbaby.kefu.data.repository.AIModelRepositoryImpl
import com.csbaby.kefu.data.repository.KeywordRuleRepositoryImpl
import com.csbaby.kefu.data.repository.ReplyHistoryRepositoryImpl
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

data class SyncResult(
    val success: Boolean,
    val rulesSynced: Int = 0,
    val modelsSynced: Int = 0,
    val historySynced: Int = 0,
    val errors: List<String> = emptyList()
)

@Singleton
class SyncManager @Inject constructor(
    private val ruleRepo: KeywordRuleRepositoryImpl,
    private val modelRepo: AIModelRepositoryImpl,
    private val historyRepo: ReplyHistoryRepositoryImpl,
    private val preferencesManager: PreferencesManager
) {
    /**
     * 全量同步：验证本地数据可访问，并返回各仓库的数据计数。
     * 当前仓库层暂无服务端同步接口，此处作为本地数据完整性检查。
     */
    suspend fun fullSync(): SyncResult = coroutineScope {
        Timber.i("Starting full sync")

        val rulesResult = async {
            try {
                val count = ruleRepo.getRuleCount()
                Result.success(count)
            } catch (e: Exception) {
                Result.failure<Int>(e)
            }
        }
        val modelsResult = async {
            try {
                val models = modelRepo.getAllModels().first()
                Result.success(models.size)
            } catch (e: Exception) {
                Result.failure<Int>(e)
            }
        }
        val historyResult = async {
            try {
                val count = historyRepo.getTotalCount()
                Result.success(count)
            } catch (e: Exception) {
                Result.failure<Int>(e)
            }
        }

        val rules = rulesResult.await()
        val models = modelsResult.await()
        val history = historyResult.await()

        val errors = mutableListOf<String>()
        rules.exceptionOrNull()?.let { errors.add("Rules sync failed: ${it.message}") }
        models.exceptionOrNull()?.let { errors.add("Models sync failed: ${it.message}") }
        history.exceptionOrNull()?.let { errors.add("History sync failed: ${it.message}") }

        val success = rules.isSuccess && models.isSuccess && history.isSuccess

        val result = SyncResult(
            success = success,
            rulesSynced = rules.getOrDefault(0),
            modelsSynced = models.getOrDefault(0),
            historySynced = history.getOrDefault(0),
            errors = errors
        )

        Timber.i("Full sync completed: rules=${result.rulesSynced}, models=${result.modelsSynced}, history=${result.historySynced}, errors=${result.errors.size}")
        result
    }

    /**
     * 增量同步（当前实现为全量同步，后续可扩展为基于时间戳的增量同步）
     */
    suspend fun incrementalSync(): SyncResult = fullSync()

    /**
     * 获取上次同步时间（暂返回 0，待 PreferencesManager 添加时间戳支持后扩展）
     */
    suspend fun getLastSyncTime(): Long {
        return 0L
    }
}
