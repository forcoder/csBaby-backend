package com.csbaby.kefu.data.sync

import com.csbaby.kefu.data.local.PreferencesManager
import com.csbaby.kefu.data.repository.AIModelRepositoryImpl
import com.csbaby.kefu.data.repository.KeywordRuleRepositoryImpl
import com.csbaby.kefu.data.repository.ReplyHistoryRepositoryImpl
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
     * Full sync: pull all data from server and update local cache.
     */
    suspend fun fullSync(): SyncResult = coroutineScope {
        Timber.i("Starting full sync from server")

        val rulesResult = async {
            try {
                val count = ruleRepo.syncFromServer()
                Result.success(count.getOrDefault(0))
            } catch (e: Exception) {
                Result.failure<Int>(e)
            }
        }
        val modelsResult = async {
            try {
                val count = modelRepo.syncFromServer()
                Result.success(count.getOrDefault(0))
            } catch (e: Exception) {
                Result.failure<Int>(e)
            }
        }
        val historyResult = async {
            try {
                val count = historyRepo.syncFromServer()
                Result.success(count.getOrDefault(0))
            } catch (e: Exception) {
                Result.failure<Int>(e)
            }
        }

        val rules = rulesResult.await()
        val models = modelsResult.await()
        val history = historyResult.await()

        val errors = mutableListOf<String>()
        rules.exceptionOrNull()?.let { errors.add("Rules sync failed: ${it.message}") }
        models.exceptionOrNull()?.let { models.exceptionOrNull()?.let { errors.add("Models sync failed: ${it.message}") } }
        history.exceptionOrNull()?.let { errors.add("History sync failed: ${it.message}") }

        val success = rules.isSuccess || models.isSuccess || history.isSuccess

        // Update last sync timestamp on partial or full success
        if (success) {
            preferencesManager.updateLastSyncTimestamp(System.currentTimeMillis())
        }

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
     * Incremental sync (currently same as full sync for rules/models, incremental for history).
     */
    suspend fun incrementalSync(): SyncResult = fullSync()

    /**
     * Get last sync timestamp.
     */
    suspend fun getLastSyncTime(): Long {
        return preferencesManager.getLastSyncTimestamp()
    }
}
