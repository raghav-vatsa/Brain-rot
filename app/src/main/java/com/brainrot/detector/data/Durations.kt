package com.brainrot.detector.data

import java.util.concurrent.TimeUnit

/** "45 sec", "12 min", "1 hr 5 min" - short enough for a chip or a nag screen. */
fun formatDuration(ms: Long): String {
    val totalMinutes = TimeUnit.MILLISECONDS.toMinutes(ms)
    if (totalMinutes < 1) return "${TimeUnit.MILLISECONDS.toSeconds(ms)} sec"
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours == 0L -> "$totalMinutes min"
        minutes == 0L -> "$hours hr"
        else -> "$hours hr $minutes min"
    }
}

fun formatMinutes(minutes: Int): String = formatDuration(TimeUnit.MINUTES.toMillis(minutes.toLong()))
