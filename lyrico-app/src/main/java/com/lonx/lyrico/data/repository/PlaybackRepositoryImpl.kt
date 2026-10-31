package com.lonx.lyrico.data.repository

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.lonx.lyrico.data.model.log.AppLog
import com.lonx.lyrico.data.model.log.AppLogType
import com.lonx.lyrico.data.model.log.AppLogLevel
import com.lonx.lyrico.R
import com.lonx.lyrico.platform.player.PlayerIntentFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CancellationException

class PlaybackRepositoryImpl(private val settingsRepository: SettingsRepository) : PlaybackRepository {
    override suspend fun play(context: Context, uri: Uri) {
        val savedComponent = try {
            settingsRepository.defaultPlayerComponent.first()
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            AppLog.record(AppLogType.APP, "Playback", "Unable to read default player",
                detail = error.message.orEmpty(), level = AppLogLevel.WARNING, error = error)
            null
        }
        val component = savedComponent?.let(ComponentName::unflattenFromString)
        if (component != null) {
            val intent = PlayerIntentFactory.buildPlayIntent(uri).setComponent(component)
            if (startSafely(context, intent)) return
            Toast.makeText(context, context.getString(R.string.player_open_failed), Toast.LENGTH_SHORT).show()
        }
        openSystemChooser(context, uri)
    }

    private fun openSystemChooser(context: Context, uri: Uri): Boolean {
        val intent = PlayerIntentFactory.buildChooserIntent(
            uri = uri,
            title = context.getString(R.string.choose_player)
        )

        return startSafely(context, intent).also { launched ->
            if (!launched) {
                Toast.makeText(context, context.getString(R.string.no_player_found), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startSafely(context: Context, intent: Intent): Boolean {
        return try {
            context.startActivity(intent)
            AppLog.record(AppLogType.APP, "Playback", "Player launch requested",
                "uri=${intent.data}\naction=${intent.action}\ncomponent=${intent.component}\nflags=${intent.flags}", intent.data?.toString())
            true
        } catch (error: ActivityNotFoundException) {
            recordFailure(intent, error)
            false
        } catch (error: SecurityException) {
            recordFailure(intent, error)
            false
        } catch (e: Exception) {
            recordFailure(intent, e)
            Toast.makeText(context, context.getString(R.string.unknown_error, e.message), Toast.LENGTH_SHORT).show()
            false
        }
    }

    private fun recordFailure(intent: Intent, error: Exception) = AppLog.record(
        AppLogType.APP, "Playback", "Unable to launch player",
        "uri=${intent.data}\naction=${intent.action}\ncomponent=${intent.component}\nflags=${intent.flags}",
        intent.data?.toString(), AppLogLevel.WARNING, error)
}
