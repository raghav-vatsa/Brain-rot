package com.brainrot.detector.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.brainrot.detector.data.Permissions
import com.brainrot.detector.ui.theme.BrainRotTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            viewModel.refreshPermissions()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            BrainRotTheme {
                MainScreen(
                    viewModel = viewModel,
                    onGrantUsageAccess = { startActivity(Permissions.usageAccessIntent()) },
                    onGrantOverlay = { startActivity(Permissions.overlayIntent(this)) },
                    onGrantNotifications = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Usage access and overlay are granted in Settings, so the only reliable
        // moment to notice is when we come back.
        viewModel.refreshPermissions()
    }
}
