package com.lonx.lyrico.platform.player

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.provider.MediaStore
import android.graphics.drawable.Drawable
import com.lonx.lyrico.BuildConfig

data class AudioPlayerApp(val component: String, val label: String, val icon: Drawable?)

object AudioPlayerApps {
    fun label(context: Context, component: String): String? {
        val packageName = ComponentName.unflattenFromString(component)?.packageName ?: return null
        return try {
            context.packageManager.getApplicationInfo(packageName, 0).loadLabel(context.packageManager).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }

    fun query(context: Context): List<AudioPlayerApp> {
        val releasePackage = BuildConfig.APPLICATION_ID.removeSuffix(".debug")
        val excludedPackages = setOf(context.packageName, releasePackage, "$releasePackage.debug")
        // Only resolve handlers; no audio is opened while choosing a default in settings.
        val intent = PlayerIntentFactory.buildPlayIntent(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI)
        return context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            .filter { it.activityInfo.exported && it.activityInfo.packageName !in excludedPackages }
            .map {
                AudioPlayerApp(
                    ComponentName(it.activityInfo.packageName, it.activityInfo.name).flattenToString(),
                    it.activityInfo.applicationInfo.loadLabel(context.packageManager).toString(),
                    runCatching { it.activityInfo.applicationInfo.loadIcon(context.packageManager) }.getOrNull()
                )
            }
            .distinctBy { it.component.substringBefore('/') }
            .sortedBy { it.label.lowercase(java.util.Locale.getDefault()) }
    }
}
