package com.csbaby.kefu.infrastructure.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.csbaby.kefu.infrastructure.window.FloatingWindowService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // 简化实现：开机后显示悬浮图标（如果之前开启了）
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    FloatingWindowService.showIconOnly(context)
                } catch (e: Exception) {
                    // 忽略错误
                }
            }
        }
    }
}
