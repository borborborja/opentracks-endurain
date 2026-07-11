package com.endurainbridge.opentracks

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri

/**
 * Reads a Track summary and its TrackPoints from the read-only content URIs handed over by
 * OpenTracks in the dashboard intent. The URI read grant is revoked once the receiving Activity
 * finishes, so callers must read eagerly (into memory / a file) before finishing.
 */
class OpenTracksReader(private val resolver: ContentResolver) {

    fun readTrack(trackUri: Uri): TrackSummary? {
        resolver.query(trackUri, null, null, null, null)?.use { c ->
            if (!c.moveToFirst()) return null
            return TrackSummary(
                uuid = c.stringOrNull(OpenTracksContract.Track.UUID) ?: return null,
                name = c.stringOrNull(OpenTracksContract.Track.NAME).orEmpty(),
                description = c.stringOrNull(OpenTracksContract.Track.DESCRIPTION).orEmpty(),
                activityType = c.stringOrNull(OpenTracksContract.Track.ACTIVITY_TYPE).orEmpty(),
                startTimeEpochMs = c.longOrNull(OpenTracksContract.Track.START_TIME) ?: 0L,
            )
        }
        return null
    }

    fun readTrackPoints(trackPointsUri: Uri): List<TrackPointData> {
        val points = ArrayList<TrackPointData>()
        resolver.query(trackPointsUri, null, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                points.add(
                    TrackPointData(
                        // OpenTracks stores lat/lon as microdegrees (int * 1E6); NULL when the point
                        // has no location (segment marker / pause). See ContentProviderUtils.fillTrackPoint.
                        latitude = c.microdegreesOrNull(OpenTracksContract.TrackPoint.LATITUDE),
                        longitude = c.microdegreesOrNull(OpenTracksContract.TrackPoint.LONGITUDE),
                        timeEpochMs = c.longOrNull(OpenTracksContract.TrackPoint.TIME),
                        altitude = c.doubleOrNull(OpenTracksContract.TrackPoint.ALTITUDE),
                        speed = c.doubleOrNull(OpenTracksContract.TrackPoint.SPEED),
                        heartRate = c.floatOrNull(OpenTracksContract.TrackPoint.HEART_RATE),
                        cadence = c.floatOrNull(OpenTracksContract.TrackPoint.CADENCE),
                        power = c.floatOrNull(OpenTracksContract.TrackPoint.POWER),
                        temperature = c.floatOrNull(OpenTracksContract.TrackPoint.TEMPERATURE),
                    )
                )
            }
        }
        return points
    }

    // --- null-safe cursor helpers (a missing column returns index -1) ---

    private fun Cursor.stringOrNull(name: String): String? {
        val i = getColumnIndex(name)
        return if (i < 0 || isNull(i)) null else getString(i)
    }

    private fun Cursor.longOrNull(name: String): Long? {
        val i = getColumnIndex(name)
        return if (i < 0 || isNull(i)) null else getLong(i)
    }

    private fun Cursor.doubleOrNull(name: String): Double? {
        val i = getColumnIndex(name)
        return if (i < 0 || isNull(i)) null else getDouble(i)
    }

    /** Reads a microdegree-scaled coordinate column (int * 1E6) and returns degrees. */
    private fun Cursor.microdegreesOrNull(name: String): Double? {
        val i = getColumnIndex(name)
        return if (i < 0 || isNull(i)) null else getInt(i) / 1_000_000.0
    }

    private fun Cursor.floatOrNull(name: String): Float? {
        val i = getColumnIndex(name)
        return if (i < 0 || isNull(i)) null else getFloat(i)
    }
}
