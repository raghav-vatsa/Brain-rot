package com.brainrot.detector.monitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.brainrot.detector.data.Permissions
import com.brainrot.detector.data.SettingsStore

/** Brings the watcher back after a reboot or an app update, if it was on. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }
        val settings = SettingsStore.get(context)
        if (settings.current.monitoring && Permissions.allGranted(context)) {
            UsageMonitorService.start(context)
        }
    }
}
