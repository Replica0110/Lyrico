package com.lonx.lyrico.utils

import android.content.Context
import android.os.Build
import com.hjq.device.compat.DeviceMarketName
import com.hjq.device.compat.DeviceOs
import com.lonx.lyrico.BuildConfig

/** Shared environment header for ordinary logs and reproduction reports. */
internal object DiagnosticEnvironment {
    fun describe(context: Context): String = buildString {
        appendLine("Lyrico diagnostic info")
        appendLine("App version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        val model = DeviceMarketName.getMarketName(context).trimmedOrNull()
            ?: listOf(Build.MANUFACTURER, Build.MODEL).filter { it.isNotBlank() }
                .joinToString(" ").ifBlank { Build.DEVICE }
        val system = listOfNotNull(DeviceOs.getOsName().trimmedOrNull(), DeviceOs.getOsVersionName().trimmedOrNull())
            .joinToString(" ").ifBlank { "Android ${Build.VERSION.RELEASE}" }
        appendLine("Device model: $model")
        appendLine("System version: $system")
        appendLine("Android version: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
    }

    private fun String?.trimmedOrNull(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
}
