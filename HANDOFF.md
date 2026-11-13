# HANDOFF

Where ezamp actually stands, for whoever picks this up next (human or agent). Written 2026-11-11 at 0.4.4, state and version refreshed 2026-11-13 for 0.9.3. Read this before touching code; it exists so you do not rediscover the traps.

## State

Version **0.9.3**. Everything below marked ✅ was verified on a device, not assumed; rows added in 0.9.x were verified on the Android emulator (`emulator-5554` on the build box), not yet on the phone.

Device used: Nothing A063 (Spacewar), Android 15, wireless ADB. Server: Jellyfin 12.4.0 "The Pirate Bay" at `192.168.1.50:8096` on the LAN, library 5,535 tracks / 532 albums / 54 album artists / 31 playlists (4 with music).

| Area | State |
| --- | --- |
| Local index (SQLite) | ✅ albums, tracks, search, favourites, counts |
| Onboarding | ✅ URL probe, Jellyfin check, username/password, all failure paths |
| Layouts | ✅ art-forward and classic, switchable in SET |
| Navigation | ✅ LIBRARY · PLAYLISTS tabs; back peels one layer, leaves only from bare Library; notification opens the app (0.9.x, emulator) |
| Library carousel | ✅ ALBUMS · TRACKS · ARTISTS · GENRES label strip, one page per swipe, search per page (0.9.0, emulator) |
| Artists | ✅ every credited name (0.8.0), tap-through |
| Genres | ✅ migration 7, `;`-split tags, 195 genres here, genre → track list (0.9.0, emulator) |
| Shuffle / repeat | ✅ current track leads the shuffle order; 12/12 tracks played once (0.9.0, emulator) |
| Playlists | ✅ own tab, Favourites pinned first as a permanent playlist, video playlists filtered out |
| Sync | ✅ two-phase, incremental; 8.5 s warm |
| Playback | ✅ MediaSessionService, background-safe, notification |
| Queue/resume | ✅ survives process death, ±5 s |
| Scrobbling | ✅ 204s on start/progress/stop; positions absolute, resume reports the real offset |
| Play mode | ✅ Direct / Saver (mp3 192k); seek restarts the transcode at the target |
| Favourites | ✅ heart on track rows, local-first then Jellyfin (200) |
| Offline downloads | ✅ album level; plays with Wi-Fi **and** mobile data off (media session captured mid-flight: `PLAYING`, position 27.6 s, correct track) |
| Desktop/iOS | ⬜ later milestones, see [ROADMAP.md](ROADMAP.md) |

Latest measured (release/R8):

- cold start 360–550 ms · search 5–7 ms · sync 8.5 s (phase 1 at 7.3–9.3 s)
- tap→audio 161–388 ms warm, 366–544 ms cold FLAC
- jank 11.2% · PSS 180 MB · CPU playing 12% foreground / 2% paused of one core
- APK 2.7 MB

## 0.9.3 frontend cleanup (2026-11-13)

Removed the onboarding tagline and server helper, layout descriptions, and visible performance footer. Playback descriptions are now "lossless" and "transcoded mp3 192k". Storage offers 4/8/16/32/64 GB and Unlimited, with wrapping choices; Unlimited stores the existing 0 MB sentinel. Existing caps and download policy are preserved. `ezamp-perf` logging remains intact.

Windows `assembleRelease`, all 48 JVM tests and migration verification passed. Emulator settings checks confirmed the shortened descriptions, 16/32/64 GB and Unlimited persistence, Unlimited restoration after process death, and all six storage labels within screen bounds at 130% text size.

## 0.9.2 review fixes (2026-11-13)

- Playlist membership edits enter one ordered IO queue at tap time and complete even if the sheet closes. The sheet defers membership reloads while edits are pending and rejects results from before a newer tap.
- Queue reads, saves and cached track-list/id changes share an IO-side monitor. This prevents concurrent restoration from assigning an old id to a newly saved queue; no disk work moved onto Main.
- Sleep expiry and fade use the elapsed-time deadline, recalculated at most every 500 ms while execution runs. The timer does not wake a suspended device; it catches up on the next tick after resume.
- Windows release build, all 48 JVM tests and SQLDelight migration verification passed. Emulator: 20 rapid membership taps returned to absent; one final add plus immediate sheet close persisted exactly one track (checked in SQLite after restart). Queue process-death test restored the saved track at 2,737 ms without autoplay. Sleep selection and cancellation worked; end-of-track mode stopped at 218,320 ms and reset to SLEEP (media-session state plus `ezamp-sleep` log).
- Still unverified: physical-phone deep-sleep expiry/fade timing and equalizer behavior on vendor audio effects. The earlier performance numbers are historical, not new 0.9.2 measurements.

## Known open issues

1. **Cold tap is ~312 ms direct, ~678 ms in Saver.** Direct is close to the floor; Saver pays ffmpeg start-up (measured twice). If Saver is used at home, expect the first second to feel softer than Direct.
2. ~~`start` scrobble can carry `pos=0`~~ — fixed: absolute positions plus a 700 ms delay before the start report; verified `pos=124820ms`.
3. ~~**Artists are album artists only**~~ — fixed in 0.8.0 (`libraryTrackArtist`); 122 artists here.
4. ~~**Offline downloads are album-level only**~~ — M3 shipped all of it except playlist-level download: downloaded-only SQL scope + badges + chip + guard (proven on device), STORAGE section with refusing cap + per-album delete, foreground/network sync with throttle + banner, 50k-query validation (all scoped <10 ms). Playlist download shipped in 0.8.0.
5. **CPU while playing is 12%** of one core: decode + `HwBinder`, UI thread idle. Media3 1.9.4 removed `setEnableAudioOffload`, so there is no offload shortcut on this version. Only chase this if battery complaints appear.
6. ~~**No automated tests.**~~ 0.9.2: 48 JVM tests (including genre-code and local-playlist SQL cases) in `app/src/test` run on every gate (`testDebugUnitTest`), plus `verifyDebugEzampDbMigration`. Not yet covered: `QueueStore` round-trip, sync transforms, any UI.

## How to build and install

Build box is Windows with the toolchain under `C:\ezamp` (JDK 21, `C:\android-sdk` with platform 36 + build-tools 36.0.0, Gradle 8.14.3). Source is mirrored to `C:\ezamp\src`.

```bat
set JAVA_HOME=C:\ezamp\tools\jdk21
set ANDROID_HOME=C:\android-sdk
cd /d C:\ezamp\src
C:\ezamp\tools\gradle-8.14.3\bin\gradle.bat :app:assembleRelease --console=plain > C:\ezamp\out\buildN.log 2>&1
```

Then copy `app/build/outputs/apk/release/app-release.apk` out and `adb install -r` it. Release builds are signed with the debug keystore so they upgrade over each other and keep data — replace that before distributing to anyone else.

ADB: the wireless port rotates whenever debugging is toggled or the phone sleeps.

```bash
~/.hermes/cache/scratch/mdnsvenv/bin/python tools/adb-port.py
```

If discovery finds nothing, wireless debugging is off on the phone — that is a one-toggle fix, not a script problem.

## Where the bodies are buried

These each cost real debugging time; the reasoning is in [DESIGN.md](DESIGN.md) and the traps are in [AGENTS.md](AGENTS.md).

- **ExoPlayer is thread-verified.** Reading `player.*` off the main thread is a fatal `IllegalStateException`, not a warning.
- **`/Items?ParentId=<playlist>` returns nothing** for playlists. Use `/Playlists/{id}/Items`.
- **Silent `runCatching` hid that** for three builds. Log exceptions.
- **Jellyfin playlists mix video and audio.** Filter by "do we have audio rows", never by name or `ChildCount`.
- **Schema changes need a `.sqm` migration** or existing installs crash on first query.
- **Debug-build performance numbers are meaningless.** R8 changed jank 43% → 11.2%. Two conclusions were wrong until measured on release.
- **Vision is not measurement.** Two layout calls were wrong in opposite directions; `uiautomator dump` bounds settled it both times.

## First moves for the next session

1. Build release, install, confirm 0.9.3 in the app and that the library still syncs (`ezamp-perf` lines `sync=` and `genres=`).
2. Confirm the 0.9.x navigation on the phone (it was verified on the emulator): back order, carousel swipe, notification tap, shuffle coverage.
3. Cold tap in Saver is 678 ms, which is ffmpeg start-up on the server, not the client. Only chase it if the first second of a Saver track becomes a reason not to use Saver.
4. Keep `ezamp-perf` logging intact and add a line per new hot path — it is the only regression signal that exists.

## Repository facts

- `feat/polish` is merged locally into `master`, commits authored as `m3lis5a`. Push is owner-gated: never push without Calypso's explicit ok.
- APKs, keystores and `dist/` are ignored: screenshots and sideloadable builds live on the build box, not in git.
- No secrets in the tree. Credentials and API keys live outside the repo, in `~/.hermes/secrets/` on the owner's machines; never write them into files, logs or URLs. Auth travels in an `Authorization: MediaBrowser Token="…"` header.
- `VERSION` drives `versionName`/`versionCode`; bump it with every shipped change and add a CHANGELOG entry.
