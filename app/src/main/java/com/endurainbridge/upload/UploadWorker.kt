package com.endurainbridge.upload

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.endurainbridge.data.Settings
import com.endurainbridge.data.UploadHistory
import com.endurainbridge.dedup.UploadLedger
import com.endurainbridge.endurain.EndurainClient
import com.endurainbridge.endurain.UploadResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Uploads a cached GPX file to Endurain. Retriable failures (network / 5xx) return [Result.retry]
 * so WorkManager retries with exponential backoff; permanent failures (auth / bad format) stop and
 * notify. On success the track UUID is recorded in the [UploadLedger] and the cache file deleted.
 */
class UploadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val filePath = inputData.getString(KEY_FILE_PATH)
            ?: return@withContext Result.failure()
        val displayName = inputData.getString(KEY_DISPLAY_NAME) ?: "activity.gpx"
        val trackUuid = inputData.getString(KEY_TRACK_UUID).orEmpty()
        val historyId = inputData.getString(KEY_HISTORY_ID).orEmpty()
        val history = UploadHistory(applicationContext)

        val file = File(filePath)
        if (!file.exists()) {
            if (historyId.isNotBlank()) history.markFailed(historyId, "Fichero no encontrado")
            return@withContext Result.failure()
        }

        val settings = Settings.get(applicationContext)
        val client = EndurainClient(settings)

        when (val result = client.uploadGpx(file, displayName)) {
            is UploadResult.Success -> {
                if (trackUuid.isNotBlank()) UploadLedger(applicationContext).markUploaded(trackUuid)
                if (historyId.isNotBlank()) history.markSuccess(historyId, result.endurainId)
                file.delete()
                Notifications.showResult(
                    applicationContext,
                    applicationContext.getString(com.endurainbridge.R.string.notif_uploaded),
                    displayName,
                )
                Result.success()
            }

            is UploadResult.ServerError, is UploadResult.NetworkError -> {
                if (runAttemptCount >= MAX_ATTEMPTS) {
                    file.delete()
                    if (historyId.isNotBlank()) history.markFailed(historyId, describe(result))
                    Notifications.showResult(
                        applicationContext,
                        applicationContext.getString(com.endurainbridge.R.string.notif_upload_failed),
                        describe(result),
                    )
                    Result.failure()
                } else {
                    Result.retry()
                }
            }

            else -> {
                // AuthError / RejectedError / ConfigError — retrying won't help.
                file.delete()
                if (historyId.isNotBlank()) history.markFailed(historyId, describe(result))
                Notifications.showResult(
                    applicationContext,
                    applicationContext.getString(com.endurainbridge.R.string.notif_upload_failed),
                    describe(result),
                )
                Result.failure()
            }
        }
    }

    private fun describe(result: UploadResult): String = when (result) {
        is UploadResult.Success -> "OK"
        is UploadResult.ServerError -> "Servidor (HTTP ${result.httpCode})"
        is UploadResult.NetworkError -> "Red: ${result.detail}"
        is UploadResult.AuthError -> "API key rechazada (HTTP ${result.httpCode})"
        is UploadResult.RejectedError -> "Rechazado (HTTP ${result.httpCode})"
        is UploadResult.ConfigError -> result.detail
    }

    companion object {
        const val KEY_FILE_PATH = "file_path"
        const val KEY_DISPLAY_NAME = "display_name"
        const val KEY_TRACK_UUID = "track_uuid"
        const val KEY_HISTORY_ID = "history_id"
        private const val MAX_ATTEMPTS = 5
    }
}
