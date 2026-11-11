# Contributing to ezamp

Thanks for helping. This page covers the toolchain, the build, testing on a device, and what a change has to satisfy. Architecture lives in [DESIGN.md](DESIGN.md); the current state lives in [HANDOFF.md](HANDOFF.md).

## Toolchain

The versions below are known to work together. ezamp is pinned to the newest **buildable** set, not the newest published one — see the warning after the table before bumping anything.

| Component | Version |
| --- | --- |
| JDK | 21 (`C:\ezamp\tools\jdk21`) |
| Android SDK | platform 36, build-tools 36.0.0 |
| Gradle | 8.14.3 |
| AGP | 8.13.2 |
| Kotlin | 2.3.21 |
| Compose BOM | 2026.05.01 |
| Media3 | 1.9.4 |
| Ktor | 3.5.2 |
| Coil | 3.3.0 |
| SQLDelight | 2.4.1 |
| compileSdk / targetSdk / minSdk | 36 / 36 / 26 |

⚠️ **Do not bump Compose past 1.11-era BOMs or `core-ktx` past 1.17.** Compose 1.12 and core-ktx 1.19 require `compileSdk 37`, which the SDK repository does not publish yet; the build fails at dependency resolution. Check that a version is actually available before adopting it.

## Build

```bash
./gradlew :app:assembleRelease     # what you should be shipping
./gradlew :app:assembleDebug       # only for a readable stack trace
```

Release enables R8 and resource shrinking (2.7 MB versus 17 MB). If R8 breaks a reflective path, add a narrow rule to `app/proguard-rules.pro`; kotlinx.serialization and SQLDelight already ship consumer rules, so the existing file only covers our DTOs.

## Signing

Release builds are currently signed with the local debug keystore so consecutive builds upgrade over each other without losing data. That is a convenience for private builds only — **generate a real keystore and keep it out of the repo** before distributing an APK to anyone else.

## Testing on a device

There is no test suite yet; the checks are on-device and they are not optional.

```bash
~/.hermes/cache/scratch/mdnsvenv/bin/python tools/adb-port.py   # wireless ADB, port rotates
adb install -r app-release.apk
adb shell am start -n one.calypsocloud.ezamp/.MainActivity
adb logcat -d | grep -E 'ezamp-perf|ezamp-scrobble'
```

Minimum before you call a change done:

1. **It runs.** Launch, sync completes (`ezamp-perf: sync=… db albums=… tracks=…`), no `FATAL EXCEPTION` in logcat.
2. **The numbers did not regress.** Compare against the budgets in [DESIGN.md](DESIGN.md#performance-budgets) using the same commands (`dumpsys gfxinfo`, `dumpsys media_session`, `/proc/<pid>/stat`).
3. **Layout changes are proven with bounds**, not a screenshot alone: `adb shell uiautomator dump /sdcard/ui.xml`, pull it, and read the actual `bounds` of the elements. Two layout bugs in this project were misjudged from screenshots in both directions.
4. **Playback changes are proven in the media session**: `dumpsys media_session` shows `PLAYING` with an advancing position, and background playback survives HOME.

For authentication paths, test the failures too: unreachable host, a non-Jellyfin host, and a wrong password all have to produce their own message.

## What a change must satisfy

- **Minimal, task-scoped diffs.** Do not reformat untouched files.
- **Schema changes ship with a migration.** Edit `Library.sq` *and* add the next `<n>.sqm`, or existing installs crash on first query.
- **No new dependencies** without discussing it first; each one is a permanent version constraint.
- **Nothing blocking the UI thread.** Network, disk and DB work belongs off the main thread; player state must be read *on* the main thread (ExoPlayer verifies).
- **Auth in headers only.** Never in a query string, a URL, or a log line.
- **Bump `VERSION`** and add a `CHANGELOG.md` entry for anything user-visible. `VERSION` drives `versionName` and `versionCode` (`major*10000 + minor*100 + patch`).
- **Say what you measured.** "Faster" is not a result; a number from a release build is.

## Code style

- Kotlin, 4-space indent, no wildcard imports, explicit types on public functions and on `PlayState`-style data classes.
- Compose screens are functions per screen in `ui/`; shared drawing helpers in `ui/Glyphs.kt`; design tokens in `ui/Theme.kt` — never hardcode a colour or radius in a screen.
- Text that can overflow needs `maxLines` plus `overflow = TextOverflow.Ellipsis`; cards in a horizontal row need `minLines` so their subtitles align.
- Keep the `ezamp-perf` / `ezamp-scrobble` log lines working, and add one for any new hot path.

## Reporting a problem

Include the version (`VERSION`), the device and Android version, what you expected, and the relevant `adb logcat` lines. For layout issues, attach the `uiautomator` bounds of the elements involved — it converts an opinion into a fact.
