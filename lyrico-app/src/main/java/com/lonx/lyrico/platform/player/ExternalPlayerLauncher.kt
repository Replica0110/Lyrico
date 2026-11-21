package com.lonx.lyrico.platform.player

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.annotation.StringRes
import com.lonx.lyrico.R
import com.lonx.lyrico.data.log.AppLog
import com.lonx.lyrico.data.log.AppLogLevel
import com.lonx.lyrico.data.log.AppLogType
import com.lonx.lyrico.data.settings.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/** 在用户选择的播放器中打开歌曲，未设置或启动失败时退回系统选择器。 */
class ExternalPlayerLauncher(
    private val settingsRepository: SettingsRepository,
) {

    suspend fun launch(context: Context, uri: Uri) {
        val savedComponent = readDefaultPlayerComponent()
        val component = savedComponent?.let(ComponentName::unflattenFromString)
        if (component != null) {
            val intent = PlayerIntentFactory.buildPlayIntent(uri).setComponent(component)
            if (startSafely(context, intent)) return
            toast(context, R.string.player_open_failed)
        }
        openSystemChooser(context, uri)
    }

    private suspend fun readDefaultPlayerComponent(): String? {
        return try {
            settingsRepository.defaultPlayerComponent.first()
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            AppLog.record(
                AppLogType.APP, "Playback", "Unable to read default player",
                detail = error.message.orEmpty(), level = AppLogLevel.WARNING, error = error
            )
            null
        }
    }

    private fun openSystemChooser(context: Context, uri: Uri): Boolean {
        val intent = PlayerIntentFactory.buildChooserIntent(
            uri = uri,
            title = context.getString(R.string.choose_player)
        )
        return startSafely(context, intent).also { launched ->
            if (!launched) toast(context, R.string.no_player_found)
        }
    }

    private fun startSafely(context: Context, intent: Intent): Boolean {
        return try {
            context.startActivity(intent)
            AppLog.record(
                AppLogType.APP, "Playback", "Player launch requested",
                "uri=${intent.data}\naction=${intent.action}\ncomponent=${intent.component}\nflags=${intent.flags}",
                intent.data?.toString()
            )
            true
        } catch (error: ActivityNotFoundException) {
            recordFailure(intent, error)
            false
        } catch (error: SecurityException) {
            recordFailure(intent, error)
            false
        } catch (error: Exception) {
            recordFailure(intent, error)
            toast(context, R.string.unknown_error, error.message)
            false
        }
    }

    private fun recordFailure(intent: Intent, error: Exception) = AppLog.record(
        AppLogType.APP, "Playback", "Unable to launch player",
        "uri=${intent.data}\naction=${intent.action}\ncomponent=${intent.component}\nflags=${intent.flags}",
        intent.data?.toString(), AppLogLevel.WARNING, error
    )

    private fun toast(context: Context, @StringRes messageRes: Int, vararg formatArgs: Any?) {
        val message = if (formatArgs.isEmpty()) {
            context.getString(messageRes)
        } else {
            context.getString(messageRes, *formatArgs)
        }
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
}
