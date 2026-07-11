# Endurain Bridge

A small Android companion app that uploads activities recorded in **[OpenTracks]** to a self-hosted
**[Endurain]** server. OpenTracks records the GPS track (background GPS, BLE sensors, barometer);
this app reads the finished track over OpenTracks' **Dashboard/Data API**, serializes it to GPX, and
uploads it to Endurain via its REST API — no manual export/import needed.

[OpenTracks]: https://codeberg.org/OpenTracksApp/OpenTracks
[Endurain]: https://github.com/endurain-project/endurain

## How it works (Vía B — dashboard integration)

```
OpenTracks (Data API enabled, "Endurain Bridge" selected as Dashboard)
   → sends Intent "Intent.OpenTracks-Dashboard" with content:// URIs [track, trackpoints, markers]
   → DashboardReceiverActivity reads them (URI grant held while reading)
   → dedup check by track UUID (Endurain has no server-side dedup — see note)
   → GpxWriter builds a GPX file in app cache
   → WorkManager UploadWorker POSTs it to Endurain (retriable, network-constrained)
   → notification: "Actividad subida a Endurain ✓"
```

Authentication uses an **Endurain API key** (`X-API-Key` header) — no username/password stored on
the device. Every request also sends the mandatory `X-Client-Type: mobile` header.

## Setup (on your phone)

1. **Generate an API key in Endurain** with the `activities:upload` scope (Settings → your profile →
   API keys in the Endurain web UI). Confirm your Endurain version exposes API-key auth on
   `POST /api/v1/activities/create/upload` (present on current versions).
2. Install this app, open it, enter your **server URL** (e.g. `https://endurain.example.com`) and the
   **API key**, tap **Probar conexión** to sanity-check reachability, then **Guardar**.
3. In **OpenTracks**: Settings → enable the **Data API**, then Settings → **Dashboard** → select
   **Endurain Bridge**.
4. Record an activity. When you stop and open its dashboard, the track uploads automatically.

> **Duplicates:** Endurain does *not* deduplicate uploads server-side — re-uploading a file creates a
> hidden duplicate activity. This app keeps a local ledger of uploaded OpenTracks track UUIDs and
> skips anything already sent.

## Building

Requires JDK 17 and the Android SDK (compileSdk 35).

**Easiest:** open the project in **Android Studio** (bundles the SDK), let it sync, and Run.

**Command line:** install the Android SDK, then:
```bash
cp local.properties.example local.properties   # edit sdk.dir to your SDK path
./gradlew assembleDebug                          # -> app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug                           # build + install on a connected device
```
If you don't have the SDK, install command-line tools + the needed packages:
```bash
# with Homebrew: brew install --cask android-commandlinetools
sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"
```

## Project layout

| Area | Files |
|---|---|
| Settings + API key (encrypted) | `data/Settings.kt`, `ui/SettingsActivity.kt` |
| Endurain HTTP client | `endurain/EndurainClient.kt`, `endurain/UploadResult.kt` |
| OpenTracks Data API | `opentracks/OpenTracksContract.kt`, `opentracks/OpenTracksReader.kt`, `opentracks/Model.kt` |
| GPX serialization | `opentracks/GpxWriter.kt` |
| Dashboard receiver | `ui/DashboardReceiverActivity.kt` |
| Upload + dedup + notifications | `upload/UploadWorker.kt`, `upload/UploadEnqueuer.kt`, `dedup/UploadLedger.kt`, `upload/Notifications.kt` |

## Testing without OpenTracks

To verify the Endurain upload path in isolation: on the settings screen, **long-press “Probar
conexión”** to pick a `.gpx` file and upload it directly through the same WorkManager path.

## Roadmap

- **M4 (not yet built):** live-recording auto-upload via a foreground service that holds the URI
  grant until recording ends; optional Vía A (Android share-sheet) entry point; mapping OpenTracks
  categories → Endurain numeric activity types via `PUT /api/v1/activities/edit`.

## Key API facts (confirmed from source)

- OpenTracks dashboard action `Intent.OpenTracks-Dashboard`; URI list under extra key
  `Intent.OpenTracks-Dashboard.Payload` (indices 0=track, 1=trackpoints, 2=markers); protocol v2.
  Coordinates are microdegrees (int × 1E6); non-location points store NULL lat/lon.
- Endurain upload: `POST /api/v1/activities/create/upload`, multipart field `file`
  (`application/gpx+xml`), headers `X-API-Key` + `X-Client-Type: mobile`, returns `201`.
