package com.endurainbridge.opentracks

/**
 * Constants for the OpenTracks Dashboard/Data API and its content-provider columns.
 * Confirmed from OpenTracks source (branch main):
 *  - publicapi/DataProvider.java              (intent action, extras, URI ordering)
 *  - data/tables/TracksColumns.java           (track columns)
 *  - data/tables/TrackPointsColumns.java      (trackpoint columns)
 */
object OpenTracksContract {

    // --- Dashboard intent (DataProvider.java) ---
    const val ACTION_DASHBOARD = "Intent.OpenTracks-Dashboard"
    // Key of the ParcelableArrayList<Uri> payload (= ACTION_DASHBOARD + ".Payload").
    const val EXTRA_PAYLOAD_URIS = "Intent.OpenTracks-Dashboard.Payload"
    const val EXTRA_PROTOCOL_VERSION = "PROTOCOL_VERSION"
    const val EXTRA_IS_RECORDING = "EXTRAS_OPENTRACKS_IS_RECORDING_THIS_TRACK"
    const val SUPPORTED_PROTOCOL_VERSION = 2

    // URIs arrive as a ParcelableArrayList<Uri> at these fixed indices.
    const val URI_INDEX_TRACK = 0
    const val URI_INDEX_TRACKPOINTS = 1
    const val URI_INDEX_MARKERS = 2

    // --- tracks table columns ---
    object Track {
        const val ID = "_id"
        const val UUID = "uuid"
        const val NAME = "name"
        const val DESCRIPTION = "description"
        const val ACTIVITY_TYPE = "activity_type"
        const val ACTIVITY_TYPE_LOCALIZED = "activity_type_localized"
        const val START_TIME = "time_start"
        const val STOP_TIME = "time_stop"
        const val TOTAL_DISTANCE = "distance"
        const val TOTAL_TIME = "duration_total"
    }

    // TrackPoint.Type integer codes (TrackPoint.java). A trailing SEGMENT_END_MANUAL marks that
    // recording has stopped (there is no pause feature, so it is unambiguous). IDLE is NOT a stop.
    object TrackPointType {
        const val SEGMENT_START_MANUAL = -2
        const val SEGMENT_START_AUTOMATIC = -1
        const val TRACKPOINT = 0
        const val SEGMENT_END_MANUAL = 1
        const val SENSORPOINT = 2
        const val IDLE = 3
    }

    // --- trackpoints table columns ---
    object TrackPoint {
        const val ID = "_id"
        const val TRACK_ID = "trackid"
        const val TYPE = "type"
        const val LATITUDE = "latitude"
        const val LONGITUDE = "longitude"
        const val TIME = "time"
        const val ALTITUDE = "elevation"
        const val ACCURACY = "accuracy"
        const val SPEED = "speed"
        const val BEARING = "bearing"
        const val HEART_RATE = "sensor_heartrate"
        const val CADENCE = "sensor_cadence"
        const val POWER = "sensor_power"
        const val TEMPERATURE = "sensor_temperature"
    }
}
