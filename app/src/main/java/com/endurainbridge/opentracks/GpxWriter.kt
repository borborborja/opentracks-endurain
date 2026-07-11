package com.endurainbridge.opentracks

import java.io.File
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Serializes a [TrackSummary] + its [TrackPointData] list into a GPX 1.1 document.
 *
 * A new `<trkseg>` is started whenever a non-location point (segment marker / sensor-only row)
 * is encountered, which reproduces OpenTracks' pause/resume segmentation. Heart rate, cadence,
 * power and temperature are emitted using the Garmin TrackPointExtension namespace, which
 * Endurain (gpxpy-based parser) understands.
 */
object GpxWriter {

    private const val NS_GPX = "http://www.topografix.com/GPX/1/1"
    private const val NS_GPXTPX = "http://www.garmin.com/xmlschemas/TrackPointExtension/v1"

    fun writeToFile(track: TrackSummary, points: List<TrackPointData>, outFile: File): File {
        outFile.bufferedWriter(Charsets.UTF_8).use { it.write(build(track, points)) }
        return outFile
    }

    fun build(track: TrackSummary, points: List<TrackPointData>): String {
        val sb = StringBuilder(points.size * 96 + 512)
        sb.append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
        sb.append("<gpx version=\"1.1\" creator=\"Endurain Bridge\"")
            .append(" xmlns=\"").append(NS_GPX).append('"')
            .append(" xmlns:gpxtpx=\"").append(NS_GPXTPX).append("\">").append('\n')

        // <metadata>
        sb.append("  <metadata>\n")
        sb.append("    <name>").append(escape(track.name.ifBlank { "OpenTracks activity" })).append("</name>\n")
        if (track.startTimeEpochMs > 0) {
            sb.append("    <time>").append(iso(track.startTimeEpochMs)).append("</time>\n")
        }
        sb.append("  </metadata>\n")

        // <trk>
        sb.append("  <trk>\n")
        sb.append("    <name>").append(escape(track.name.ifBlank { "OpenTracks activity" })).append("</name>\n")
        if (track.description.isNotBlank()) {
            sb.append("    <desc>").append(escape(track.description)).append("</desc>\n")
        }
        // OpenTracks category string (e.g. "trail running"). Endurain derives the numeric type from
        // the file; <type> gives it a hint to map from.
        if (track.activityType.isNotBlank()) {
            sb.append("    <type>").append(escape(track.activityType)).append("</type>\n")
        }

        var segOpen = false
        for (p in points) {
            if (!p.hasLocation) {
                // segment boundary / non-GPS row: close the current segment
                if (segOpen) {
                    sb.append("    </trkseg>\n")
                    segOpen = false
                }
                continue
            }
            if (!segOpen) {
                sb.append("    <trkseg>\n")
                segOpen = true
            }
            appendTrackPoint(sb, p)
        }
        if (segOpen) sb.append("    </trkseg>\n")

        sb.append("  </trk>\n")
        sb.append("</gpx>\n")
        return sb.toString()
    }

    private fun appendTrackPoint(sb: StringBuilder, p: TrackPointData) {
        sb.append("      <trkpt lat=\"").append(num(p.latitude!!)).append("\" lon=\"")
            .append(num(p.longitude!!)).append("\">\n")
        p.altitude?.let { sb.append("        <ele>").append(num(it)).append("</ele>\n") }
        p.timeEpochMs?.let { sb.append("        <time>").append(iso(it)).append("</time>\n") }

        val hasExt = p.heartRate != null || p.cadence != null || p.power != null || p.temperature != null
        if (hasExt) {
            sb.append("        <extensions>\n")
            sb.append("          <gpxtpx:TrackPointExtension>\n")
            p.heartRate?.let { sb.append("            <gpxtpx:hr>").append(it.toInt()).append("</gpxtpx:hr>\n") }
            p.cadence?.let { sb.append("            <gpxtpx:cad>").append(it.toInt()).append("</gpxtpx:cad>\n") }
            p.temperature?.let { sb.append("            <gpxtpx:atemp>").append(num(it.toDouble())).append("</gpxtpx:atemp>\n") }
            p.power?.let { sb.append("            <gpxtpx:power>").append(it.toInt()).append("</gpxtpx:power>\n") }
            sb.append("          </gpxtpx:TrackPointExtension>\n")
            sb.append("        </extensions>\n")
        }
        sb.append("      </trkpt>\n")
    }

    private fun iso(epochMs: Long): String =
        DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(epochMs))

    private fun num(v: Double): String = String.format(Locale.US, "%.6f", v)

    private fun escape(s: String): String = buildString(s.length) {
        for (ch in s) when (ch) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&apos;")
            else -> append(ch)
        }
    }
}
