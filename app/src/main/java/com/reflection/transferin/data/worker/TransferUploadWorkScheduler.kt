package com.reflection.transferin.data.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.reflection.transferin.session.worker.TransferUploadWorker
import java.util.concurrent.TimeUnit

class TransferUploadWorkScheduler(
    private val context: Context
) {

    private val workManager: WorkManager
        get() = WorkManager.getInstance(context.applicationContext)

    fun enqueue(queueItemId: String) {
        if (queueItemId.isBlank()) return

        val inputData = Data.Builder()
            .putString(TransferUploadWorker.KEY_QUEUE_ITEM_ID, queueItemId)
            .build()

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(false)
            .setRequiresStorageNotLow(false)
            .build()

        val request = OneTimeWorkRequestBuilder<TransferUploadWorker>()
            .setInputData(inputData)
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                10,
                TimeUnit.SECONDS
            )
            .addTag(tagForQueueItem(queueItemId))
            .build()

        workManager.enqueueUniqueWork(
            uniqueWorkName = uniqueWorkName(queueItemId),
            existingWorkPolicy = ExistingWorkPolicy.KEEP,
            request = request
        )
    }

    fun cancel(queueItemId: String) {
        if (queueItemId.isBlank()) return
        workManager.cancelUniqueWork(uniqueWorkName(queueItemId))
    }

    fun uniqueWorkName(queueItemId: String): String {
        return WORK_NAME_PREFIX + queueItemId
    }

    fun tagForQueueItem(queueItemId: String): String {
        return WORK_TAG_PREFIX + queueItemId
    }

    companion object {
        private const val WORK_NAME_PREFIX = "transfer_upload_"
        private const val WORK_TAG_PREFIX = "transfer_upload_tag_"
    }
}