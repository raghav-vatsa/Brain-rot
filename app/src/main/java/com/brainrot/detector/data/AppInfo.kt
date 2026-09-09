package com.brainrot.detector.data

import android.graphics.Bitmap

/** One launchable app installed on the phone. */
data class AppInfo(
    val packageName: String,
    val label: String,
    val icon: Bitmap?,
    val isSystem: Boolean,
)
