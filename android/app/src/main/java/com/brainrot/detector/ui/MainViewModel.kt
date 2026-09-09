package com.brainrot.detector.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.brainrot.detector.data.AppInfo
import com.brainrot.detector.data.InstalledApps
import com.brainrot.detector.data.Permissions
import com.brainrot.detector.data.SettingsStore
import com.brainrot.detector.monitor.UsageMonitorService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PermissionState(
    val usageAccess: Boolean = false,
    val overlay: Boolean = false,
    val notifications: Boolean = false,
) {
    val allGranted: Boolean get() = usageAccess && overlay && notifications
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val settings = SettingsStore.get(application)

    val settingsState = settings.state

    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    val apps: StateFlow<List<AppInfo>> = _apps.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _permissions = MutableStateFlow(PermissionState())
    val permissions: StateFlow<PermissionState> = _permissions.asStateFlow()

    init {
        refreshPermissions()
        loadApps()
    }

    fun loadApps() {
        viewModelScope.launch {
            _loading.value = true
            _apps.value = withContext(Dispatchers.IO) { InstalledApps.load(getApplication<Application>()) }
            _loading.value = false
        }
    }

    fun refreshPermissions() {
        val context = getApplication<Application>()
        _permissions.value = PermissionState(
            usageAccess = Permissions.hasUsageAccess(context),
            overlay = Permissions.canDrawOverlay(context),
            notifications = Permissions.hasNotifications(context),
        )
        // Monitoring silently stops working if a permission is taken away.
        if (!_permissions.value.allGranted && settings.current.monitoring) {
            setMonitoring(false)
        }
    }

    fun setQuery(value: String) {
        _query.value = value
    }

    fun setWatched(packageName: String, watched: Boolean) =
        settings.setWatched(packageName, watched)

    fun setAppLimitMinutes(packageName: String, minutes: Int?) =
        settings.setAppLimitMinutes(packageName, minutes)

    fun setDefaultLimitMinutes(minutes: Int) = settings.setDefaultLimitMinutes(minutes)

    fun setSnoozeMinutes(minutes: Int) = settings.setSnoozeMinutes(minutes)

    fun setShowSystemApps(show: Boolean) = settings.setShowSystemApps(show)

    fun setMonitoring(enabled: Boolean) {
        val context = getApplication<Application>()
        settings.setMonitoring(enabled)
        if (enabled) UsageMonitorService.start(context) else UsageMonitorService.stop(context)
    }

    /** Apps matching the search box and the show-system-apps preference. */
    fun visibleApps(all: List<AppInfo>, search: String, showSystem: Boolean, watched: Set<String>) =
        all.filter { app ->
            (showSystem || !app.isSystem || app.packageName in watched) &&
                (search.isBlank() ||
                    app.label.contains(search, ignoreCase = true) ||
                    app.packageName.contains(search, ignoreCase = true))
        }
}
