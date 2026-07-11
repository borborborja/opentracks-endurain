package com.endurainbridge.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.content.IntentCompat
import androidx.lifecycle.lifecycleScope
import com.endurainbridge.data.Settings
import com.endurainbridge.dedup.UploadLedger
import com.endurainbridge.opentracks.GpxWriter
import com.endurainbridge.opentracks.OpenTracksContract
import com.endurainbridge.opentracks.OpenTracksReader
import com.endurainbridge.upload.Notifications
import com.endurainbridge.upload.UploadEnqueuer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Invisible (transparent) activity registered as an OpenTracks "dashboard". OpenTracks sends the
 * dashboard intent with a ParcelableArrayList<Uri> payload [track, trackpoints, markers] plus a
 * temporary read grant (FLAG_GRANT_READ_URI_PERMISSION). We read the track eagerly into a private
 * cache GPX file — the URI grant is revoked once this activity finishes — then hand the file to a
 * WorkManager job for reliable upload, and finish.
 *
 * v1 scope: upload a *finished* track. While a recording is still in progress we do nothing and let
 * OpenTracks re-invoke us when the user opens the finished track's dashboard. (Live-recording
 * auto-upload needs a foreground service to hold the URI grant — deferred to M4.)
 */
class DashboardReceiverActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null || intent.action != OpenTracksContract.ACTION_DASHBOARD) {
            finish()
            return
        }

        val settings = Settings.get(this)
        if (!settings.isConfigured) {
            toast("Configura Endurain Bridge antes de usarlo como dashboard")
            finish()
            return
        }

        val uris = extractUris(intent)
        if (uris == null || uris.size <= OpenTracksContract.URI_INDEX_TRACKPOINTS) {
            toast("OpenTracks no envió datos (¿Data API desactivada?)")
            finish()
            return
        }

        val isRecording = intent.getBooleanExtra(OpenTracksContract.EXTRA_IS_RECORDING, false)
        if (isRecording) {
            // Don't upload a partial track. Wait until the user finishes and re-opens the dashboard.
            finish()
            return
        }

        val trackUri = uris[OpenTracksContract.URI_INDEX_TRACK]
        val trackPointsUri = uris[OpenTracksContract.URI_INDEX_TRACKPOINTS]

        // Read + serialize on a background thread, but keep the activity alive (holding the URI
        // grant) until the cache file is written; only then finish and enqueue.
        lifecycleScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                buildGpxFile(trackUri, trackPointsUri)
            }
            when (outcome) {
                is Outcome.Duplicate -> {
                    Notifications.showResult(
                        this@DashboardReceiverActivity,
                        getString(com.endurainbridge.R.string.notif_skipped_duplicate),
                        outcome.name,
                    )
                }
                is Outcome.Ready -> {
                    UploadEnqueuer.enqueue(
                        this@DashboardReceiverActivity,
                        outcome.file,
                        outcome.fileName,
                        outcome.trackUuid,
                    )
                }
                is Outcome.Nothing -> {
                    toast("No se pudo leer la actividad de OpenTracks")
                }
            }
            finish()
        }
    }

    private fun buildGpxFile(trackUri: Uri, trackPointsUri: Uri): Outcome {
        val reader = OpenTracksReader(contentResolver)
        val track = reader.readTrack(trackUri) ?: return Outcome.Nothing

        val ledger = UploadLedger(this)
        if (ledger.isUploaded(track.uuid)) {
            return Outcome.Duplicate(track.name.ifBlank { track.uuid })
        }

        val points = reader.readTrackPoints(trackPointsUri)
        if (points.none { it.hasLocation }) return Outcome.Nothing

        val fileName = safeFileName(track.name.ifBlank { "activity-${track.uuid}" }) + ".gpx"
        val outFile = File(cacheDir, "ot-${track.uuid}.gpx")
        GpxWriter.writeToFile(track, points, outFile)
        return Outcome.Ready(outFile, fileName, track.uuid)
    }

    private fun extractUris(intent: Intent): ArrayList<Uri>? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            IntentCompat.getParcelableArrayListExtra(
                intent, OpenTracksContract.EXTRA_PAYLOAD_URIS, Uri::class.java
            )
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra(OpenTracksContract.EXTRA_PAYLOAD_URIS)
        }
    }

    private fun safeFileName(name: String): String =
        name.replace(Regex("[^A-Za-z0-9-_ ]"), "_").trim().take(80).ifBlank { "activity" }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    private sealed interface Outcome {
        data class Ready(val file: File, val fileName: String, val trackUuid: String) : Outcome
        data class Duplicate(val name: String) : Outcome
        data object Nothing : Outcome
    }
}
