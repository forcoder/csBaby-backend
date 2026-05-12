package com.csbaby.kefu.infrastructure.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.csbaby.kefu.data.local.PreferencesManager
import com.csbaby.kefu.infrastructure.window.FloatingWindowService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import timber.log.Timber


@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var preferencesManager: PreferencesManager

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val pendingResult = goAsync()
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                try {
                    val preferences = preferencesManager.userPreferencesFlow.first()
                    if (preferences.floatingIconEnabled) {
                        withContext(Dispatchers.Main) {
                            FloatingWindowService.showIconOnly(context.applicationContext)
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "BootReceiver: 显示悬浮图标失败")
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
