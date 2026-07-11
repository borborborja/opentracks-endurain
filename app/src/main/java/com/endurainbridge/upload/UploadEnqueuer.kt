package com.endurainbridge.upload

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.io.File
import java.util.concurrent.TimeUnit

object UploadEnqueuer {

    /**
     * Enqueues a network-constrained, retriable upload of [gpxFile]. Uses unique work keyed on the
     * track UUID so re-triggering the same track does not run concurrent duplicate uploads.
     */
    fun enqueue(context: Context, gpxFile: File, displayName: String, trackUuid: String) {
        val data = Data.Builder()
            .putString(UploadWorker.KEY_FILE_PATH, gpxFile.absolutePath)
            .putString(UploadWorker.KEY_DISPLAY_NAME, displayName)
            .putString(UploadWorker.KEY_TRACK_UUID, trackUuid)
            .build()

        val request = OneTimeWorkRequestBuilder<UploadWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .setInputData(data)
            .build()

        val uniqueName = if (trackUuid.isNotBlank()) "upload-$trackUuid" else "upload-${gpxFile.name}"
        WorkManager.getInstance(context)
            .enqueueUniqueWork(uniqueName, ExistingWorkPolicy.KEEP, request)
    }
}
