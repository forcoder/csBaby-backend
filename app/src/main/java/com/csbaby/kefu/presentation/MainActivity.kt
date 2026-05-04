package com.csbaby.kefu.presentation

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.csbaby.kefu.data.local.AuthManager
import com.csbaby.kefu.data.local.PreferencesManager
import com.csbaby.kefu.infrastructure.notification.NotificationListenerServiceImpl
import com.csbaby.kefu.presentation.navigation.RootNavigation
import com.csbaby.kefu.presentation.theme.KefuTheme
import com.csbaby.kefu.presentation.theme.ThemeMode
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var preferencesManager: PreferencesManager

    @Inject
    lateinit var authManager: AuthManager

    private var pendingOverlayPermission = false
    private var pendingNotificationPermission = false

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            requestOverlayPermission()
        } else {
            requestOverlayPermission()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val userPreferences by preferencesManager.userPreferencesFlow.collectAsState(
                initial = PreferencesManager.UserPreferences()
            )

            val themeMode = ThemeMode.fromValue(userPreferences.themeMode)

            KefuTheme(themeMode = themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    RootNavigation(authManager = authManager)
                }
            }
        }

        checkPermissions()
    }

    override fun onResume() {
        super.onResume()
        if (pendingOverlayPermission && Settings.canDrawOverlays(this)) {
            pendingOverlayPermission = false
            requestNotificationListenerPermissionIfNeeded()
        } else if (pendingNotificationPermission &&
                   NotificationListenerServiceImpl.isNotificationAccessEnabled(this)) {
            pendingNotificationPermission = false
        }
    }

    private fun checkPermissions() {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }

        requestOverlayPermission()

        if (Settings.canDrawOverlays(this)) {
            requestNotificationListenerPermissionIfNeeded()
        }
    }

    private fun requestOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            pendingOverlayPermission = true
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        } else {
            requestNotificationListenerPermissionIfNeeded()
        }
    }

    private fun requestNotificationListenerPermissionIfNeeded() {
        if (!NotificationListenerServiceImpl.isNotificationAccessEnabled(this)) {
            pendingNotificationPermission = true
            requestNotificationListenerPermission()
        }
    }

    private fun requestNotificationListenerPermission() {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        startActivity(intent)
    }
}
