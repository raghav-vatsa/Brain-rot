package com.brainrot.detector.monitor

import java.util.concurrent.TimeUnit

/**
 * Accumulates how long the user has been sitting in each app.
 *
 * A package's counter keeps running across brief detours (checking a
 * notification, answering a message) and only resets once the app has been left
 * alone for [awayResetMs] — otherwise the limit would be trivially defeated by
 * bouncing to the home screen and back.
 */
class UsageSessionTracker(
    private val awayResetMs: Long = DEFAULT_AWAY_RESET_MS,
    private val maxTickMs: Long = DEFAULT_MAX_TICK_MS,
) {
    private val usedMs = HashMap<String, Long>()
    private val lastSeenMs = HashMap<String, Long>()

    var currentPackage: String? = null
        private set

    /** -1 until the first poll, so the first tick never counts a bogus delta. */
    private var lastTickMs = -1L

    /**
     * Folds one poll into the counters.
     *
     * @param foreground package currently in front, or null (screen off, unknown).
     * @param nowElapsed monotonic clock, i.e. [android.os.SystemClock.elapsedRealtime].
     * @return true if [foreground]'s counter was reset by this call.
     */
    fun update(foreground: String?, nowElapsed: Long): Boolean {
        val previous = currentPackage
        var didReset = false

        if (previous != null) lastSeenMs[previous] = nowElapsed

        if (foreground != null && foreground != previous) {
            val awayFor = nowElapsed - (lastSeenMs[foreground] ?: 0L)
            if (awayFor > awayResetMs) {
                usedMs.remove(foreground)
                didReset = true
            }
        }

        if (foreground != null && foreground == previous && lastTickMs >= 0L) {
            // Capped so a doze or a long sleep between polls cannot dump an hour
            // of "usage" into the counter at once.
            val delta = (nowElapsed - lastTickMs).coerceIn(0L, maxTickMs)
            usedMs[foreground] = (usedMs[foreground] ?: 0L) + delta
        }

        if (foreground != null) lastSeenMs[foreground] = nowElapsed
        currentPackage = foreground
        lastTickMs = nowElapsed
        return didReset
    }

    fun usedMs(packageName: String): Long = usedMs[packageName] ?: 0L

    fun reset(packageName: String) {
        usedMs.remove(packageName)
    }

    fun resetAll() {
        usedMs.clear()
    }

    companion object {
        val DEFAULT_AWAY_RESET_MS: Long = TimeUnit.MINUTES.toMillis(5)
        val DEFAULT_MAX_TICK_MS: Long = TimeUnit.SECONDS.toMillis(15)
    }
}
