package com.brainrot.detector.monitor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class UsageSessionTrackerTest {

    private val away = TimeUnit.MINUTES.toMillis(5)
    private val poll = 2_000L

    private fun tracker() = UsageSessionTracker(awayResetMs = away, maxTickMs = 15_000L)

    @Test
    fun `time in one app accumulates across polls`() {
        val tracker = tracker()
        var now = 0L
        repeat(6) {
            tracker.update("com.example.social", now)
            now += poll
        }
        assertEquals(5 * poll, tracker.usedMs("com.example.social"))
    }

    @Test
    fun `a short detour does not reset the counter`() {
        val tracker = tracker()
        var now = 0L
        repeat(5) { tracker.update("com.example.social", now); now += poll }
        val before = tracker.usedMs("com.example.social")

        // Thirty seconds in another app, then back.
        repeat(15) { tracker.update("com.example.launcher", now); now += poll }
        tracker.update("com.example.social", now)

        assertEquals(before, tracker.usedMs("com.example.social"))
    }

    @Test
    fun `counter resets after staying away past the reset window`() {
        val tracker = tracker()
        var now = 0L
        repeat(5) { tracker.update("com.example.social", now); now += poll }
        assertTrue(tracker.usedMs("com.example.social") > 0)

        // Off in another app for longer than the reset window.
        tracker.update("com.example.launcher", now)
        now += away + 1_000
        tracker.update("com.example.launcher", now)

        val didReset = tracker.update("com.example.social", now)

        assertTrue(didReset)
        assertEquals(0L, tracker.usedMs("com.example.social"))
    }

    @Test
    fun `a long gap between polls is capped`() {
        val tracker = tracker()
        tracker.update("com.example.social", 0L)
        // Device dozed for an hour with the app still in front.
        tracker.update("com.example.social", TimeUnit.HOURS.toMillis(1))
        assertEquals(15_000L, tracker.usedMs("com.example.social"))
    }

    @Test
    fun `screen off stops accumulation`() {
        val tracker = tracker()
        var now = 0L
        repeat(3) { tracker.update("com.example.social", now); now += poll }
        val before = tracker.usedMs("com.example.social")

        repeat(3) { tracker.update(null, now); now += poll }

        assertEquals(before, tracker.usedMs("com.example.social"))
        assertFalse(tracker.currentPackage == "com.example.social")
    }
}
