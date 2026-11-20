package com.lonx.lyrico.worker.processor

import com.lonx.lyrico.data.model.batch.BatchTaskType

class BatchTaskProcessorFactory(
    private val processors: Map<BatchTaskType, BatchTaskProcessor>
) {
    fun create(type: BatchTaskType): BatchTaskProcessor {
        return processors[type]
            ?: throw IllegalArgumentException("No processor registered for $type")
    }
}
