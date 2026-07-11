package com.endurainbridge.upload

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.endurainbridge.R
import com.endurainbridge.opentracks.OpenTracksReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Foreground service that holds the OpenTracks URI read grant (forwarded from
 * [com.endurainbridge.ui.DashboardReceiverActivity]) for the lifetime of a recording, observes the
 * trackpoints content URI, and — when a trailing SEGMENT_END_MANUAL point appears (recording
 * stopped) — reads the track, serializes it to GPX and enqueues the upload. Then it stops itself.
 *
 * Uses a `specialUse` foreground-service type because the work is "wait for an external recorder to
 * finish", which does not fit the timed `dataSync` model and could run for hours.
 */
class RecordingWatchService : LifecycleService() {

    private lateinit var trackUri: Uri
    private lateinit var trackPointsUri: Uri
    private var observer: ContentObserver? = null
    private var watchThread: HandlerThread? = null
    private var handler: Handler? = null
    @Volatile private var handled = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        // Must promote to foreground promptly after startForegroundService().
        ServiceCompat.startForeground(
            this,
            Notifications.NOTIF_ID_WATCH,
            Notifications.buildWatching(this),
            foregroundServiceTypeCompat(),
        )

        val track = intent?.let { IntentCompat.getParcelableExtra(it, EXTRA_TRACK_URI, Uri::class.java) }
        val points = intent?.let { IntentCompat.getParcelableExtra(it, EXTRA_TRACKPOINTS_URI, Uri::class.java) }
        if (track == null || points == null) {
            stop()
            return START_NOT_STICKY
        }
        trackUri = track
        trackPointsUri = points

        val thread = HandlerThread("recording-watch").also { it.start() }
        watchThread = thread
        val h = Handler(thread.looper)
        handler = h

        val obs = object : ContentObserver(h) {
            override fun onChange(selfChange: Boolean) = checkOnWatchThread()
        }
        observer = obs
        contentResolver.registerContentObserver(trackPointsUri, true, obs)

        // Give up if the recording never ends (e.g. app/OpenTracks killed mid-way).
        h.postDelayed({ giveUp() }, MAX_WATCH_MS)
        // Check immediately, in case the track already finished before the observer registered.
        h.post { checkOnWatchThread() }

        return START_NOT_STICKY
    }

    private fun checkOnWatchThread() {
        if (handled) return
        val stopped = try {
            val pts = OpenTracksReader(contentResolver).readTrackPoints(trackPointsUri)
            TrackUploadPreparer.isRecordingStopped(pts)
        } catch (e: SecurityException) {
            // URI grant lost — cannot continue.
            giveUp(); return
        }
        if (stopped) finishAndUpload()
    }

    private fun finishAndUpload() {
        if (handled) return
        handled = true
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                TrackUploadPreparer.prepareAndEnqueue(applicationContext, trackUri, trackPointsUri)
            }
            if (result is TrackUploadPreparer.Result.Duplicate) {
                Notifications.showResult(
                    applicationContext,
                    getString(R.string.notif_skipped_duplicate),
                    result.displayName,
                )
            }
            stop()
        }
    }

    private fun giveUp() {
        if (handled) return
        handled = true
        stop()
    }

    private fun stop() {
        observer?.let { contentResolver.unregisterContentObserver(it) }
        observer = null
        watchThread?.quitSafely()
        watchThread = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        observer?.let { contentResolver.unregisterContentObserver(it) }
        watchThread?.quitSafely()
        super.onDestroy()
    }

    private fun foregroundServiceTypeCompat(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }

    companion object {
        private const val EXTRA_TRACK_URI = "track_uri"
        private const val EXTRA_TRACKPOINTS_URI = "trackpoints_uri"
        private const val MAX_WATCH_MS = 18L * 60 * 60 * 1000 // 18 hours

        /**
         * Starts the service, forwarding the read grant on both URIs via ClipData +
         * FLAG_GRANT_READ_URI_PERMISSION. Must be called by a component that currently holds the grant.
         */
        fun start(context: Context, trackUri: Uri, trackPointsUri: Uri) {
            val intent = Intent(context, RecordingWatchService::class.java).apply {
                putExtra(EXTRA_TRACK_URI, trackUri)
                putExtra(EXTRA_TRACKPOINTS_URI, trackPointsUri)
                clipData = ClipData.newRawUri(null, trackUri).apply {
                    addItem(ClipData.Item(trackPointsUri))
                }
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
