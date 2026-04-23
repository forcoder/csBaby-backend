package com.csbaby.kefu.domain.repository

import com.csbaby.kefu.data.local.dao.MessageBlacklistDao
import com.csbaby.kefu.data.local.entity.MessageBlacklistEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageBlacklistRepository @Inject constructor(
    private val blacklistDao: MessageBlacklistDao
) {
    fun getAllEnabled(): Flow<List<MessageBlacklistEntity>> {
        return blacklistDao.getAllEnabledFlow()
    }

    fun getAll(): Flow<List<MessageBlacklistEntity>> {
        return blacklistDao.getAllFlow()
    }

    suspend fun addToBlacklist(
        type: String,
        value: String,
        description: String = "",
        packageName: String? = null
    ): Long {
        return blacklistDao.insert(
            MessageBlacklistEntity(
                type = type,
                value = value,
                description = description,
                packageName = packageName
            )
        )
    }

    suspend fun removeFromBlacklist(id: Long) {
        blacklistDao.deleteById(id)
    }

    suspend fun updateBlacklist(blacklist: MessageBlacklistEntity) {
        blacklistDao.update(blacklist)
    }

    suspend fun toggleBlacklist(id: Long, isEnabled: Boolean) {
        val blacklist = blacklistDao.getAllFlow().first().find { it.id == id }
        blacklist?.let {
            blacklistDao.update(it.copy(isEnabled = isEnabled))
        }
    }

    suspend fun clearAll() {
        blacklistDao.deleteAll()
    }

    suspend fun importBlacklist(items: List<MessageBlacklistEntity>) {
        blacklistDao.insertAll(items)
    }

    suspend fun isBlacklisted(value: String): Boolean {
        return blacklistDao.isBlacklisted(value)
    }

    /**
     * 检查消息是否应该被过滤
     */
    suspend fun shouldFilterMessage(
        content: String,
        sender: String? = null,
        packageName: String? = null
    ): Boolean {
        // 检查内容关键词
        val blacklists = blacklistDao.getAllEnabledFlow().first()
        
        for (blacklist in blacklists) {
            // 如果指定了包名且不匹配，跳过
            if (blacklist.packageName != null && blacklist.packageName != packageName) {
                continue
            }
            
            when (blacklist.type) {
                MessageBlacklistEntity.TYPE_KEYWORD -> {
                    // 关键词匹配（支持模糊匹配）
                    if (content.contains(blacklist.value, ignoreCase = true)) {
                        return true
                    }
                }
                MessageBlacklistEntity.TYPE_SENDER -> {
                    // 发送者匹配
                    if (sender != null && sender.contains(blacklist.value, ignoreCase = true)) {
                        return true
                    }
                }
                MessageBlacklistEntity.TYPE_CONTENT -> {
                    // 完整内容匹配
                    if (content.trim() == blacklist.value.trim()) {
                        return true
                    }
                }
            }
        }
        
        return false
    }
}
