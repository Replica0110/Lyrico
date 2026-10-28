package com.lonx.lyrico.data.repository

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.lonx.lyrico.data.model.log.AppLog
import com.lonx.lyrico.data.model.log.AppLogType
import com.lonx.lyrico.data.model.log.AppLogLevel
import com.lonx.lyrico.R
import com.lonx.lyrico.platform.player.PlayerIntentFactory

class PlaybackRepositoryImpl : PlaybackRepository {
    override fun play(context: Context, uri: Uri) {
        openSystemChooser(context, uri)
    }

    override fun openWithPackage(context: Context, uri: Uri, packageName: String): Boolean {
        val intent = PlayerIntentFactory.buildPlayIntentForPackage(
            uri = uri,
            packageName = packageName
        )

        return startSafely(context, intent)
    }

    override fun openSystemChooser(context: Context, uri: Uri): Boolean {
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

    override fun openDefaultApp(context: Context, uri: Uri): Boolean {
        val intent = PlayerIntentFactory.buildPlayIntent(uri)

        return startSafely(context, intent)
    }

    private fun startSafely(context: Context, intent: Intent): Boolean {
        return try {
            context.startActivity(intent)
            AppLog.record(AppLogType.APP, "Playback", "Player launch requested",
                "uri=${intent.data}\naction=${intent.action}\npackage=${intent.`package`}\nflags=${intent.flags}", intent.data?.toString())
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
        "uri=${intent.data}\naction=${intent.action}\npackage=${intent.`package`}\nflags=${intent.flags}",
        intent.data?.toString(), AppLogLevel.WARNING, error)
}
