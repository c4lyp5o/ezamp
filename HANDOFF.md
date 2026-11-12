# HANDOFF

Where ezamp actually stands, for whoever picks this up next (human or agent). Written 2026-11-11 at 0.4.4, state and version refreshed 2026-11-12 for 0.6.0. Read this before touching code; it exists so you do not rediscover the traps.

## State

Version **0.6.0**. Everything below marked ✅ was verified on the device, not assumed.

Device used: Nothing A063 (Spacewar), Android 15, wireless ADB. Server: Jellyfin 12.4.0 "The Pirate Bay" at `192.168.1.50:8096` on the LAN, library 5,535 tracks / 532 albums / 54 album artists / 31 playlists (4 with music).

| Area | State |
| --- | --- |
| Local index (SQLite) | ✅ albums, tracks, search, favourites, counts |
| Onboarding | ✅ URL probe, Jellyfin check, username/password, all failure paths |
| Layouts | ✅ art-forward and classic, switchable in SET |
| Artists | ✅ grouped by album artist, tap-through |
| Playlists | ✅ shelf + screen + playback, video playlists filtered out |
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

## Known open issues

1. **Cold tap is ~312 ms direct, ~678 ms in Saver.** Direct is close to the floor; Saver pays ffmpeg start-up (measured twice). If Saver is used at home, expect the first second to feel softer than Direct.
2. ~~`start` scrobble can carry `pos=0`~~ — fixed: absolute positions plus a 700 ms delay before the start report; verified `pos=124820ms`.
3. **Artists are album artists only** — 54 here versus 127 artists Jellyfin knows. Track-level-only artists (features, splits) need their own table from `/Artists`.
4. ~~**Offline downloads are album-level only**~~ — M3 shipped all of it except playlist-level download: downloaded-only SQL scope + badges + chip + guard (proven on device), STORAGE section with refusing cap + per-album delete, foreground/network sync with throttle + banner, 50k-query validation (all scoped <10 ms). Playlist download shipped in 0.8.0.
5. **CPU while playing is 12%** of one core: decode + `HwBinder`, UI thread idle. Media3 1.9.4 removed `setEnableAudioOffload`, so there is no offload shortcut on this version. Only chase this if battery complaints appear.
6. ~~**No automated tests.**~~ 0.8.0: 34 JVM tests in `app/src/test` run on every gate (`testDebugUnitTest`), plus `verifyDebugEzampDbMigration`. Not yet covered: `QueueStore` round-trip, sync transforms, any UI.

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

1. Build release, install, confirm 0.6.0 in the app and that the library still syncs (`ezamp-perf` line).
2. M3 remainder: downloaded-only browsing and playback as a first-class state, a storage cap with per-album delete and re-download, and sync on a schedule and on network change.
3. Cold tap in Saver is 678 ms, which is ffmpeg start-up on the server, not the client. Only chase it if the first second of a Saver track becomes a reason not to use Saver.
4. Keep `ezamp-perf` logging intact and add a line per new hot path — it is the only regression signal that exists.

## Repository facts

- Git repository on `master`, commits authored as `m3lis5a`. Push is owner-gated: never push without Calypso's explicit ok.
- APKs, keystores and `dist/` are ignored: screenshots and sideloadable builds live on the build box, not in git.
- No secrets in the tree. Credentials and API keys live outside the repo, in `~/.hermes/secrets/` on the owner's machines; never write them into files, logs or URLs. Auth travels in an `Authorization: MediaBrowser Token="…"` header.
- `VERSION` drives `versionName`/`versionCode`; bump it with every shipped change and add a CHANGELOG entry.
