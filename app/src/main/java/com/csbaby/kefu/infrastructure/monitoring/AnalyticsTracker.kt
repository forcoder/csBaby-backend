package com.csbaby.kefu.infrastructure.monitoring

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * 分析跟踪器
 * 负责收集和分析用户行为数据
 */
@Singleton
class AnalyticsTracker @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val scope = CoroutineScope(Dispatchers.IO)
    private val sharedPrefs = context.getSharedPreferences("analytics_prefs", Context.MODE_PRIVATE)

    /**
     * 跟踪事件
     */
    fun trackEvent(eventName: String, properties: Map<String, Any> = emptyMap()) {
        scope.launch {
            try {
                val timestamp = System.currentTimeMillis()
                val eventData = EventData(
                    name = eventName,
                    properties = properties,
                    timestamp = timestamp,
                    sessionId = getCurrentSessionId(),
                    userId = getUserId()
                )

                // 保存到本地存储
                saveEventLocally(eventData)

                // 发送到远程分析服务
                sendToRemoteAnalytics(eventData)

                Timber.v("Analytics event tracked: $eventName")

            } catch (e: Exception) {
                Timber.e(e, "Failed to track analytics event: $eventName")
            }
        }
    }

    /**
     * 发送事件到远程分析服务
     */
    private suspend fun sendToRemoteAnalytics(event: EventData) {
        // TODO: 实现实际的远程分析服务集成
        // 例如：Firebase Analytics, Mixpanel, 自定义后端等

        // 临时实现：保存到本地文件用于后续上传
        saveEventToFile(event)
    }

    /**
     * 保存事件到本地存储
     */
    private fun saveEventLocally(event: EventData) {
        val events = getStoredEvents().toMutableList()
        events.add(event)

        // 限制本地存储的事件数量（防止占用过多空间）
        if (events.size > MAX_LOCAL_EVENTS) {
            events.removeFirst() // FIFO移除最旧的事件
        }

        // 保存回SharedPreferences（简化实现，实际应该使用数据库）
        val eventJson = events.joinToString("\n") { it.toJsonString() }
        sharedPrefs.edit {
            putString("local_events", eventJson)
            apply()
        }
    }

    /**
     * 保存事件到文件（用于调试和离线同步）
     */
    private suspend fun saveEventToFile(event: EventData) {
        try {
            val eventsFile = File(context.filesDir, "analytics_events.json")
            val existingEvents = if (eventsFile.exists()) {
                val content = eventsFile.readText()
                if (content.isNotEmpty()) {
                    content.lines().filter { it.isNotBlank() }.mapNotNull { EventData.fromJsonString(it) }
                } else {
                    emptyList()
                }
            } else {
                emptyList()
            }

            val updatedEvents = (existingEvents + event).takeLast(MAX_FILE_EVENTS)

            eventsFile.writeText(updatedEvents.joinToString("\n") { it.toJsonString() })

        } catch (e: Exception) {
            Timber.e(e, "Failed to save event to file")
        }
    }

    /**
     * 获取本地存储的事件
     */
    private fun getStoredEvents(): List<EventData> {
        return try {
            val storedEvents = sharedPrefs.getString("local_events", "") ?: ""
            storedEvents.lines()
                .filter { it.isNotBlank() }
                .mapNotNull { EventData.fromJsonString(it) }
        } catch (e: Exception) {
            Timber.e(e, "Failed to load stored events")
            emptyList()
        }
    }

    /**
     * 获取当前会话ID
     */
    private fun getCurrentSessionId(): String {
        val currentTime = System.currentTimeMillis()
        val lastSessionTime = sharedPrefs.getLong("last_session_time", 0L)

        return if (currentTime - lastSessionTime > SESSION_TIMEOUT_MS) {
            // 新会话
            val newSessionId = UUID.randomUUID().toString()
            sharedPrefs.edit {
                putString("session_id", newSessionId)
                putLong("last_session_time", currentTime)
                apply()
            }
            newSessionId
        } else {
            sharedPrefs.getString("session_id", "") ?: ""
        }
    }

    /**
     * 获取用户ID（匿名）
     */
    private fun getUserId(): String {
        return sharedPrefs.getString("user_id", null) ?: run {
            val newUserId = UUID.randomUUID().toString()
            sharedPrefs.edit {
                putString("user_id", newUserId)
                apply()
            }
            newUserId
        }
    }

    /**
     * 清除所有本地存储的数据
     */
    fun clearLocalData() {
        sharedPrefs.edit {
            remove("local_events")
            apply()
        }

        // 删除文件存储的事件
        try {
            val eventsFile = File(context.filesDir, "analytics_events.json")
            if (eventsFile.exists()) {
                eventsFile.delete()
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to delete events file")
        }
    }

    /**
     * 数据类：事件数据
     */
    data class EventData(
        val name: String,
        val properties: Map<String, Any>,
        val timestamp: Long,
        val sessionId: String,
        val userId: String
    ) {
        companion object {
            private const val MAX_JSON_LENGTH = 1024

            fun fromJsonString(jsonString: String): EventData? {
                return try {
                    // 简化的JSON解析（实际应该使用Gson或Moshi）
                    val parts = jsonString.split("|", limit = 5)
                    if (parts.size >= 5) {
                        val name = parts[0]
                        val propertiesStr = parts[1]
                        val timestamp = parts[2].toLongOrNull() ?: 0L
                        val sessionId = parts[3]
                        val userId = parts[4]

                        val properties = parseProperties(propertiesStr)

                        EventData(name, properties, timestamp, sessionId, userId)
                    } else null
                } catch (e: Exception) {
                    Timber.e(e, "Failed to parse event JSON")
                    null
                }
            }

            private fun parseProperties(propertiesStr: String): Map<String, Any> {
                return try {
                    // 简化的属性解析
                    if (propertiesStr == "{}") emptyMap()
                    else {
                        // 这里应该实现完整的JSON解析
                        emptyMap()
                    }
                } catch (e: Exception) {
                    emptyMap()
                }
            }

            fun EventData.toJsonString(): String {
                val propsStr = properties.entries.joinToString(",") { "${it.key}=${it.value}" }
                return "$name|$propsStr|$timestamp|$sessionId|$userId"
            }
        }
    }

    companion object {
        private const val SESSION_TIMEOUT_MS = 30 * 60 * 1000 // 30分钟
        private const val MAX_LOCAL_EVENTS = 1000
        private const val MAX_FILE_EVENTS = 5000
    }
}