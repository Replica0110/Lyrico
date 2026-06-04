package com.lonx.lyrico.platform.player

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager

data class InstalledAppInfo(
    val packageName: String,
    val displayName: String
)

class InstalledAppChecker(
    private val context: Context
) {
    fun findInstalledApp(packageNames: List<String>): InstalledAppInfo? {
        return packageNames.firstNotNullOfOrNull { packageName ->
            getInstalledAppInfo(packageName)
        }
    }

    private fun getInstalledAppInfo(packageName: String): InstalledAppInfo? {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(packageName, 0)
            val applicationInfo = packageInfo.applicationInfo
            InstalledAppInfo(
                packageName = packageName,
                displayName = applicationInfo.displayName(context.packageManager)
            )
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }
}

private fun ApplicationInfo?.displayName(packageManager: PackageManager): String {
    return this?.loadLabel(packageManager)?.toString().orEmpty()
}
