package com.brainrot.detector.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.TimeUnit

/**
 * SharedPreferences-backed settings, shared by the UI and the monitoring
 * service through a process-wide singleton so both always see the same state.
 */
class SettingsStore private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(read())

    /** Current settings; emits again on every change, from any component. */
    val state: StateFlow<WatchSettings> = _state.asStateFlow()

    val current: WatchSettings get() = _state.value

    private fun read(): WatchSettings {
        val perApp = prefs.all.entries
            .filter { it.key.startsWith(KEY_LIMIT_PREFIX) }
            .mapNotNull { entry ->
                val ms = entry.value as? Long ?: return@mapNotNull null
                entry.key.removePrefix(KEY_LIMIT_PREFIX) to ms
            }
            .toMap()

        return WatchSettings(
            monitoring = prefs.getBoolean(KEY_MONITORING, false),
            watched = prefs.getStringSet(KEY_WATCHED, emptySet())!!.toSet(),
            perAppLimitMs = perApp,
            defaultLimitMs = prefs.getLong(KEY_DEFAULT_LIMIT, WatchSettings.DEFAULT_LIMIT_MS),
            snoozeMs = prefs.getLong(KEY_SNOOZE, WatchSettings.DEFAULT_SNOOZE_MS),
            showSystemApps = prefs.getBoolean(KEY_SHOW_SYSTEM, false),
        )
    }

    private fun edit(block: SharedPreferences.Editor.() -> Unit) {
        prefs.edit().apply(block).apply()
        _state.value = read()
    }

    fun setMonitoring(enabled: Boolean) = edit { putBoolean(KEY_MONITORING, enabled) }

    fun setShowSystemApps(show: Boolean) = edit { putBoolean(KEY_SHOW_SYSTEM, show) }

    fun setWatched(packageName: String, watched: Boolean) = edit {
        val next = current.watched.toMutableSet()
        if (watched) next.add(packageName) else next.remove(packageName)
        putStringSet(KEY_WATCHED, next)
    }

    fun setDefaultLimitMinutes(minutes: Int) = edit {
        putLong(KEY_DEFAULT_LIMIT, TimeUnit.MINUTES.toMillis(minutes.toLong()))
    }

    fun setSnoozeMinutes(minutes: Int) = edit {
        putLong(KEY_SNOOZE, TimeUnit.MINUTES.toMillis(minutes.toLong()))
    }

    /** Passing null clears the override so the app falls back to the default limit. */
    fun setAppLimitMinutes(packageName: String, minutes: Int?) = edit {
        val key = KEY_LIMIT_PREFIX + packageName
        if (minutes == null) remove(key) else putLong(key, TimeUnit.MINUTES.toMillis(minutes.toLong()))
    }

    companion object {
        private const val PREFS = "brain_rot_settings"
        private const val KEY_MONITORING = "monitoring"
        private const val KEY_WATCHED = "watched_packages"
        private const val KEY_DEFAULT_LIMIT = "default_limit_ms"
        private const val KEY_SNOOZE = "snooze_ms"
        private const val KEY_SHOW_SYSTEM = "show_system_apps"
        private const val KEY_LIMIT_PREFIX = "limit:"

        @Volatile
        private var instance: SettingsStore? = null

        fun get(context: Context): SettingsStore =
            instance ?: synchronized(this) {
                instance ?: SettingsStore(context).also { instance = it }
            }
    }
}
