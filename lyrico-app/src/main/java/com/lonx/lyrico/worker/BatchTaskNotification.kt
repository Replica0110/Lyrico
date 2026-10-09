package com.lonx.lyrico.worker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.lonx.lyrico.MainActivity
import com.lonx.lyrico.R

object BatchTaskNotification {

    const val CHANNEL_ID = "batch_tasks"
    const val NOTIFICATION_ID = 1001
    const val ACTION_CANCEL = "com.lonx.lyrico.ACTION_CANCEL_BATCH_TASK"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Batch Tasks",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    setShowBadge(false)
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    fun buildRunningNotification(
        context: Context,
        taskId: String,
        title: String,
        currentFile: String?,
        current: Int,
        total: Int
    ): Notification {
        ensureChannel(context)
        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            0,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val cancelIntent = Intent(context, BatchTaskCancelReceiver::class.java).apply {
            action = ACTION_CANCEL
            putExtra("task_id", taskId)
        }
        val cancelPendingIntent = PendingIntent.getBroadcast(
            context,
            taskId.hashCode(),
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentText = if (currentFile != null) {
            "$current / $total: $currentFile"
        } else {
            "$current / $total"
        }

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setOngoing(true)
            .setProgress(total, current, false)
            .setContentIntent(contentPendingIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                context.getString(R.string.action_close),
                cancelPendingIntent
            )
            .build()
    }

    fun buildFinishedNotification(
        context: Context,
        title: String,
        successCount: Int,
        failureCount: Int
    ): Notification {
        ensureChannel(context)
        val contentText = if (failureCount > 0) {
            "Done: $successCount succeeded, $failureCount failed"
        } else {
            "Done: $successCount succeeded"
        }
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setAutoCancel(true)
            .build()
    }
}
