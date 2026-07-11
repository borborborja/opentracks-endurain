package com.endurainbridge.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.content.IntentCompat
import androidx.lifecycle.lifecycleScope
import com.endurainbridge.R
import com.endurainbridge.data.Settings
import com.endurainbridge.opentracks.OpenTracksContract
import com.endurainbridge.upload.Notifications
import com.endurainbridge.upload.RecordingWatchService
import com.endurainbridge.upload.TrackUploadPreparer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Invisible (transparent) activity registered as an OpenTracks "dashboard". OpenTracks launches it
 * (via startActivity, on a user tap) with a ParcelableArrayList<Uri> payload [track, trackpoints,
 * markers] plus a temporary read grant (FLAG_GRANT_READ_URI_PERMISSION).
 *
 * - Finished track (is_recording == false): read + enqueue the upload right away.
 * - Active recording (is_recording == true): forward the URIs (and the read grant) to
 *   [RecordingWatchService], a foreground service that watches until recording stops and then
 *   uploads — so the user never has to re-open the dashboard after stopping.
 *
 * The URI grant dies when this activity finishes and is not persistable, so all reading/forwarding
 * happens before finish().
 */
class DashboardReceiverActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null || intent.action != OpenTracksContract.ACTION_DASHBOARD) {
            finish(); return
        }
        if (!Settings.get(this).isConfigured) {
            toast(getString(R.string.toast_configure_first)); finish(); return
        }

        val uris = extractUris(intent)
        if (uris == null || uris.size <= OpenTracksContract.URI_INDEX_TRACKPOINTS) {
            toast(getString(R.string.toast_no_data)); finish(); return
        }

        val trackUri = uris[OpenTracksContract.URI_INDEX_TRACK]
        val trackPointsUri = uris[OpenTracksContract.URI_INDEX_TRACKPOINTS]
        val isRecording = intent.getBooleanExtra(OpenTracksContract.EXTRA_IS_RECORDING, false)

        if (isRecording) {
            // Hand off to the foreground service; it retains the read grant and uploads on stop.
            RecordingWatchService.start(this, trackUri, trackPointsUri)
            toast(getString(R.string.toast_watching))
            finish()
            return
        }

        // Finished track: read + enqueue now, then finish.
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                TrackUploadPreparer.prepareAndEnqueue(this@DashboardReceiverActivity, trackUri, trackPointsUri)
            }
            when (result) {
                is TrackUploadPreparer.Result.Duplicate ->
                    Notifications.showResult(
                        this@DashboardReceiverActivity,
                        getString(R.string.notif_skipped_duplicate),
                        result.displayName,
                    )
                is TrackUploadPreparer.Result.NoData ->
                    toast(getString(R.string.toast_read_failed))
                is TrackUploadPreparer.Result.NotConfigured ->
                    toast(getString(R.string.toast_configure_first))
                is TrackUploadPreparer.Result.Enqueued -> Unit // worker will notify on completion
            }
            finish()
        }
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

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}
