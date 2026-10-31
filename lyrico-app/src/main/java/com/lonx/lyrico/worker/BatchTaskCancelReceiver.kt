package com.lonx.lyrico.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import com.lonx.lyrico.data.repository.BatchTaskRepository

class BatchTaskCancelReceiver : BroadcastReceiver(), KoinComponent {

    private val batchTaskRepository: BatchTaskRepository by inject()
    private val applicationScope: CoroutineScope by inject()

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == BatchTaskNotification.ACTION_CANCEL) {
            val taskId = intent.getStringExtra("task_id") ?: return
            WorkManager.getInstance(context).cancelAllWorkByTag("batch_task:$taskId")
            val pendingResult = goAsync()
            applicationScope.launch(Dispatchers.IO) {
                try {
                    batchTaskRepository.markCancelled(taskId)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    android.util.Log.e("BatchTaskCancelReceiver", "Failed to record task cancellation", e)
                }
            }.invokeOnCompletion { pendingResult.finish() }
        }
    }
}
