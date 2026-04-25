package com.csbaby.kefu

import com.csbaby.kefu.infrastructure.notification.MessageMonitor
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class MessageMonitorTest {

    private lateinit var monitor: MessageMonitor

    @Before
    fun setup() {
        monitor = MessageMonitor()
    }

    private fun createMessage(
        content: String = "测试消息",
        packageName: String = "com.test.app"
    ) = MessageMonitor.MonitoredMessage(
        packageName = packageName,
        appName = "测试应用",
        title = "测试标题",
        content = content
    )

    // ==================== ✅ 正常功能测试 ====================

    // MM-001: emitMessage发送到Flow
    @Test
    fun `MM-001 emitMessage sends to flow`() = runBlocking {
        val received = mutableListOf<MessageMonitor.MonitoredMessage>()
        val job = kotlinx.coroutines.GlobalScope.launch {
            monitor.messageFlow.take(1).toList(received)
        }

        monitor.emitMessage(createMessage("你好"))
        job.join()

        assertEquals(1, received.size)
        assertEquals("你好", received[0].content)
    }

    // MM-002: emitMessage通知所有监听器
    @Test
    fun `MM-002 emitMessage notifies all listeners`() {
        val count = AtomicInteger(0)
        val listener1 = object : MessageMonitor.MessageListener {
            override fun onNewMessage(message: MessageMonitor.MonitoredMessage) {
                count.incrementAndGet()
            }
        }
        val listener2 = object : MessageMonitor.MessageListener {
            override fun onNewMessage(message: MessageMonitor.MonitoredMessage) {
                count.incrementAndGet()
            }
        }

        monitor.addListener(listener1)
        monitor.addListener(listener2)

        runBlocking {
            monitor.emitMessage(createMessage())
        }

        assertEquals("Both listeners should be notified", 2, count.get())
    }

    // MM-003: addListener添加监听器
    @Test
    fun `MM-003 addListener adds listener`() {
        val count = AtomicInteger(0)
        val listener = object : MessageMonitor.MessageListener {
            override fun onNewMessage(message: MessageMonitor.MonitoredMessage) {
                count.incrementAndGet()
            }
        }

        monitor.addListener(listener)
        runBlocking { monitor.emitMessage(createMessage()) }

        assertEquals(1, count.get())
    }

    // MM-004: removeListener移除监听器
    @Test
    fun `MM-004 removeListener removes listener`() {
        val count = AtomicInteger(0)
        val listener = object : MessageMonitor.MessageListener {
            override fun onNewMessage(message: MessageMonitor.MonitoredMessage) {
                count.incrementAndGet()
            }
        }

        monitor.addListener(listener)
        monitor.removeListener(listener)
        runBlocking { monitor.emitMessage(createMessage()) }

        assertEquals("Removed listener should not be notified", 0, count.get())
    }

    // MM-005: 不重复添加同一监听器
    @Test
    fun `MM-005 no duplicate listener`() {
        val count = AtomicInteger(0)
        val listener = object : MessageMonitor.MessageListener {
            override fun onNewMessage(message: MessageMonitor.MonitoredMessage) {
                count.incrementAndGet()
            }
        }

        monitor.addListener(listener)
        monitor.addListener(listener)
        runBlocking { monitor.emitMessage(createMessage()) }

        assertEquals("Should only notify once", 1, count.get())
    }

    // MM-006: MonitoredMessage数据完整性
    @Test
    fun `MM-006 MonitoredMessage data integrity`() {
        val msg = MessageMonitor.MonitoredMessage(
            packageName = "com.test",
            appName = "TestApp",
            title = "Title",
            content = "Content",
            conversationTitle = "ConvTitle",
            isGroupConversation = true,
            timestamp = 12345L
        )

        assertEquals("com.test", msg.packageName)
        assertEquals("TestApp", msg.appName)
        assertEquals("Title", msg.title)
        assertEquals("Content", msg.content)
        assertEquals("ConvTitle", msg.conversationTitle)
        assertEquals(true, msg.isGroupConversation)
        assertEquals(12345L, msg.timestamp)
    }

    // MM-007: 默认时间戳为当前时间
    @Test
    fun `MM-007 default timestamp is current time`() {
        val before = System.currentTimeMillis()
        val msg = createMessage()
        val after = System.currentTimeMillis()

        assertTrue("Timestamp should be within range", msg.timestamp in before..after)
    }

    // ==================== ⚠️ 边界条件测试 ====================

    // MM-B01: 空消息内容
    @Test
    fun `MM-B01 empty content`() {
        val msg = createMessage(content = "")
        assertEquals("", msg.content)
    }

    // MM-B02: 超长消息内容
    @Test
    fun `MM-B02 very long content`() {
        val longContent = "a".repeat(10000)
        val msg = createMessage(content = longContent)
        assertEquals(10000, msg.content.length)
    }

    // MM-B03: 特殊字符消息
    @Test
    fun `MM-B03 special characters`() {
        val msg = createMessage(content = "你好😊\n\t\r特殊字符")
        assertTrue("Should contain emoji", msg.content.contains("😊"))
        assertTrue("Should contain newline", msg.content.contains("\n"))
    }

    // MM-B04: conversationTitle为null
    @Test
    fun `MM-B04 conversationTitle null by default`() {
        val msg = MessageMonitor.MonitoredMessage(
            packageName = "com.test",
            appName = "Test",
            title = "Title",
            content = "Content"
        )
        assertNull(msg.conversationTitle)
    }

    // MM-B05: isGroupConversation为null
    @Test
    fun `MM-B05 isGroupConversation null by default`() {
        val msg = MessageMonitor.MonitoredMessage(
            packageName = "com.test",
            appName = "Test",
            title = "Title",
            content = "Content"
        )
        assertNull(msg.isGroupConversation)
    }

    // MM-B06: 多个监听器同时接收
    @Test
    fun `MM-B06 multiple listeners receive`() {
        val results = mutableListOf<Int>()
        val latch = CountDownLatch(3)

        repeat(3) { index ->
            monitor.addListener(object : MessageMonitor.MessageListener {
                override fun onNewMessage(message: MessageMonitor.MonitoredMessage) {
                    synchronized(results) { results.add(index) }
                    latch.countDown()
                }
            })
        }

        runBlocking { monitor.emitMessage(createMessage()) }

        assertTrue("All listeners should receive", latch.await(3, TimeUnit.SECONDS))
        assertEquals(3, results.size)
    }

    // MM-B07: 监听器列表为空时emit
    @Test
    fun `MM-B07 emit with empty listeners`() {
        // Should not crash
        runBlocking { monitor.emitMessage(createMessage()) }
        assertTrue("Should not crash", true)
    }

    // ==================== ❌ 异常情况测试 ====================

    // MM-E01: 某个监听器抛出异常不影响其他
    @Test
    fun `MM-E01 listener exception does not affect others`() {
        val goodCount = AtomicInteger(0)

        monitor.addListener(object : MessageMonitor.MessageListener {
            override fun onNewMessage(message: MessageMonitor.MonitoredMessage) {
                throw RuntimeException("测试异常")
            }
        })
        monitor.addListener(object : MessageMonitor.MessageListener {
            override fun onNewMessage(message: MessageMonitor.MonitoredMessage) {
                goodCount.incrementAndGet()
            }
        })

        runBlocking { monitor.emitMessage(createMessage()) }

        assertEquals("Good listener should still receive", 1, goodCount.get())
    }

    // MM-E03: 快速连续emit消息
    @Test
    fun `MM-E03 rapid consecutive emits`() {
        val count = AtomicInteger(0)
        monitor.addListener(object : MessageMonitor.MessageListener {
            override fun onNewMessage(message: MessageMonitor.MonitoredMessage) {
                count.incrementAndGet()
            }
        })

        runBlocking {
            repeat(100) {
                monitor.emitMessage(createMessage("消息$it"))
            }
        }

        assertEquals("All messages should be received", 100, count.get())
    }
}
