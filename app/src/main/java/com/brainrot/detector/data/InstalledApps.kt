package com.brainrot.detector.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.os.Build
import androidx.core.graphics.drawable.toBitmap

/**
 * Enumerates the apps on the phone. Only apps with a launcher entry are listed:
 * those are the ones a person can actually sit in and lose an evening to, and
 * listing them needs the `<queries>` block in the manifest rather than the
 * QUERY_ALL_PACKAGES permission.
 */
object InstalledApps {

    private const val ICON_PX = 128

    fun load(context: Context): List<AppInfo> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved: List<ResolveInfo> =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(intent, 0)
            }

        return resolved
            .asSequence()
            .map { it.activityInfo.applicationInfo }
            .distinctBy { it.packageName }
            .filter { it.packageName != context.packageName }
            .map { appInfo ->
                AppInfo(
                    packageName = appInfo.packageName,
                    label = pm.getApplicationLabel(appInfo).toString(),
                    icon = safeIcon(pm, appInfo.packageName),
                    isSystem = appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM != 0,
                )
            }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    /** Label for a package, falling back to the package name for uninstalled apps. */
    fun labelFor(context: Context, packageName: String): String = runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    }.getOrDefault(packageName)

    private fun safeIcon(pm: PackageManager, packageName: String): Bitmap? = runCatching {
        pm.getApplicationIcon(packageName).toBitmap(ICON_PX, ICON_PX)
    }.getOrNull()
}
