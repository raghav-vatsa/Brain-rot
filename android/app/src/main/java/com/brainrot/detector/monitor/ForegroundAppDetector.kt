package com.brainrot.detector.monitor

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import java.util.concurrent.TimeUnit

/**
 * Answers "which app is the user looking at right now?".
 *
 * There is no callback for this on Android, so we replay the usage-event stream
 * since the previous poll and keep the package of the most recent resume.
 */
class ForegroundAppDetector(context: Context) {

    private val usageStats = context.getSystemService(UsageStatsManager::class.java)

    private var lastQueryEnd = System.currentTimeMillis() - INITIAL_LOOKBACK_MS
    private var foreground: String? = null

    /** @param nowWallClock must be [System.currentTimeMillis]; usage events are wall-clock stamped. */
    fun poll(nowWallClock: Long): String? {
        val stats = usageStats ?: return null
        val events: UsageEvents = try {
            stats.queryEvents(lastQueryEnd, nowWallClock)
        } catch (e: SecurityException) {
            // Usage access was revoked while we were running.
            return null
        }

        val event = UsageEvents.Event()
        while (events.getNextEvent(event)) {
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> foreground = event.packageName
                // Only clears if nothing has resumed since: switching apps emits the
                // new app's resume before the old app's stop.
                UsageEvents.Event.ACTIVITY_STOPPED ->
                    if (foreground == event.packageName) foreground = null
            }
        }
        lastQueryEnd = nowWallClock
        return foreground
    }

    /** The screen went off; nothing is in the foreground until something resumes. */
    fun clear() {
        foreground = null
    }

    private companion object {
        val INITIAL_LOOKBACK_MS: Long = TimeUnit.MINUTES.toMillis(2)
    }
}
