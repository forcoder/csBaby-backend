package com.csbaby.kefu.infrastructure.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.csbaby.kefu.data.local.PreferencesManager
import com.csbaby.kefu.infrastructure.window.FloatingWindowService
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val app = context.applicationContext
            val entryPoint = EntryPointAccessors.fromApplication(app, PreferencesManagerEntryPoint::class.java)
            val preferencesManager = entryPoint.preferencesManager()

            CoroutineScope(Dispatchers.IO).launch {
                val preferences = preferencesManager.userPreferencesFlow.first()
                if (preferences.floatingIconEnabled) {
                    FloatingWindowService.showIconOnly(context)
                }
            }
        }
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface PreferencesManagerEntryPoint {
        fun preferencesManager(): PreferencesManager
    }
}
