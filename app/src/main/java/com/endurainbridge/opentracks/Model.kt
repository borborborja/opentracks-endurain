package com.endurainbridge.opentracks

/**
 * Track summary read from the OpenTracks `tracks` dashboard content URI. Note the dashboard
 * projection does NOT expose the track `uuid`, so [dedupKey] is derived from the start time
 * (stable per activity) instead.
 */
data class TrackSummary(
    val dedupKey: String,
    val name: String,
    val description: String,
    val activityType: String,
    val startTimeEpochMs: Long,
)

/**
 * A single recorded point. [latitude]/[longitude] are null for non-location rows (segment
 * markers, sensor-only points); those start a new GPX segment.
 */
data class TrackPointData(
    val type: Int?,
    val latitude: Double?,
    val longitude: Double?,
    val timeEpochMs: Long?,
    val altitude: Double?,
    val speed: Double?,
    val heartRate: Float?,
    val cadence: Float?,
    val power: Float?,
    val temperature: Float?,
) {
    val hasLocation: Boolean
        get() {
            val lat = latitude
            val lon = longitude
            return lat != null && lon != null &&
                lat in -90.0..90.0 && lon in -180.0..180.0
        }
}
