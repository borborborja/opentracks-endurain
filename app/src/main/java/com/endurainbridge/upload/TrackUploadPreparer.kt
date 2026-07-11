package com.endurainbridge.upload

import android.content.Context
import android.net.Uri
import com.endurainbridge.data.Settings
import com.endurainbridge.dedup.UploadLedger
import com.endurainbridge.opentracks.GpxWriter
import com.endurainbridge.opentracks.OpenTracksContract
import com.endurainbridge.opentracks.OpenTracksReader
import com.endurainbridge.opentracks.TrackPointData
import java.io.File

/**
 * Shared pipeline used by both the dashboard receiver (finished track) and the recording watch
 * service (recording just stopped): read the OpenTracks track over the granted content URIs,
 * dedup by track UUID, serialize to a private GPX cache file, and enqueue the upload.
 *
 * Must be called while the caller still holds the URI read grant (queries the ContentResolver).
 */
object TrackUploadPreparer {

    sealed interface Result {
        data class Enqueued(val displayName: String) : Result
        data class Duplicate(val displayName: String) : Result
        data object NoData : Result
        data object NotConfigured : Result
    }

    fun prepareAndEnqueue(context: Context, trackUri: Uri, trackPointsUri: Uri): Result {
        if (!Settings.get(context).isConfigured) return Result.NotConfigured

        val reader = OpenTracksReader(context.contentResolver)
        val track = reader.readTrack(trackUri) ?: return Result.NoData

        val ledger = UploadLedger(context)
        val displayName = track.name.ifBlank { track.uuid }
        if (ledger.isUploaded(track.uuid)) return Result.Duplicate(displayName)

        val points = reader.readTrackPoints(trackPointsUri)
        if (points.none { it.hasLocation }) return Result.NoData

        val fileName = safeFileName(track.name.ifBlank { "activity-${track.uuid}" }) + ".gpx"
        val outFile = File(context.cacheDir, "ot-${track.uuid}.gpx")
        GpxWriter.writeToFile(track, points, outFile)

        UploadEnqueuer.enqueue(context, outFile, fileName, track.uuid)
        return Result.Enqueued(displayName)
    }

    /** A track is finished when its last point is a manual segment end (there is no pause feature). */
    fun isRecordingStopped(points: List<TrackPointData>): Boolean =
        points.lastOrNull()?.type == OpenTracksContract.TrackPointType.SEGMENT_END_MANUAL

    private fun safeFileName(name: String): String =
        name.replace(Regex("[^A-Za-z0-9-_ ]"), "_").trim().take(80).ifBlank { "activity" }
}
