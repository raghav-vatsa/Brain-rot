package com.brainrot.detector.data

import java.util.concurrent.TimeUnit

/** Immutable snapshot of everything the monitor needs to do its job. */
data class WatchSettings(
    val monitoring: Boolean = false,
    val watched: Set<String> = emptySet(),
    val perAppLimitMs: Map<String, Long> = emptyMap(),
    val defaultLimitMs: Long = DEFAULT_LIMIT_MS,
    val snoozeMs: Long = DEFAULT_SNOOZE_MS,
    val showSystemApps: Boolean = false,
) {
    /** The limit that applies to [packageName]: its own override, else the default. */
    fun limitFor(packageName: String): Long = perAppLimitMs[packageName] ?: defaultLimitMs

    fun isWatched(packageName: String): Boolean = packageName in watched

    companion object {
        val DEFAULT_LIMIT_MS: Long = TimeUnit.MINUTES.toMillis(10)
        val DEFAULT_SNOOZE_MS: Long = TimeUnit.MINUTES.toMillis(5)

        /** Choices offered in the UI, in minutes. */
        val LIMIT_CHOICES_MIN = listOf(1, 2, 5, 10, 15, 20, 30, 45, 60, 90, 120)
        val SNOOZE_CHOICES_MIN = listOf(1, 2, 5, 10, 15, 30)
    }
}
