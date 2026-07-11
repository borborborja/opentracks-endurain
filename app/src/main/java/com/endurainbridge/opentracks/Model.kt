package com.endurainbridge.opentracks

/** Track summary read from the OpenTracks `tracks` content URI. */
data class TrackSummary(
    val uuid: String,
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
