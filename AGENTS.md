# AGENTS.md

Instructions for AI coding agents in this repository. Humans want [CONTRIBUTING.md](CONTRIBUTING.md) and [DESIGN.md](DESIGN.md); this is the condensed version. Read [HANDOFF.md](HANDOFF.md) before your first change — it says what is done, what is unverified and what will bite you.

## Project

ezamp is a local-first Jellyfin music client for Android: Kotlin + Jetpack Compose, a SQLite index via SQLDelight, playback in a Media3 `MediaSessionService`. Jellyfin is a sync source and file host only. There is no server component and no web UI.

```
app/src/main/java/one/calypsocloud/ezamp/
  data/Api.kt            DTOs, JellyfinApi (auth header), Auth (probe/signIn), Prefs
  data/LibraryStore.kt   the index: schema access, two-phase sync, search, queries
  player/PlaybackService.kt  MediaSessionService + ExoPlayer + scrobble reporting
  player/PlayerHolder.kt     MediaController facade, PlayState, queue persistence
  player/QueueStore.kt       queue*.json (session state, not library data)
  player/SleepTimer.kt       sleep timer bound to the service's ExoPlayer
  player/Eq.kt               system Equalizer on the player's audio session
  ui/Library.kt          Root (all navigation state + back), album/playlist/favourites screens
  ui/Browse.kt           Library carousel, search field, pages, artist/genre screens, playlists tab
  ui/Lists.kt            hold-a-track sheet, new-playlist sheet, name field (local playlists)
  ui/*.kt                other Compose screens; Theme.kt holds the ez house-style tokens
app/src/main/sqldelight/one/calypsocloud/ezamp/db/
  Library.sq             current schema + queries
  1.sqm … 8.sqm          migrations; each new table/column needs one (8 = local playlists)
```

## Commands

The build box is a Windows machine with the toolchain under `C:\ezamp`. From `C:\ezamp\src`:

```bat
set JAVA_HOME=C:\ezamp\tools\jdk21
set ANDROID_HOME=C:\android-sdk
C:\ezamp\tools\gradle-8.14.3\bin\gradle.bat :app:assembleRelease --console=plain > C:\ezamp\out\buildN.log 2>&1
```

Redirect Gradle to a log file and read the log; piping it through `findstr`/`Select-String` inside an `ssh cmd /c` call breaks on nested quotes and reports nonsense like `'error:' is not recognized`. Grep the returned log for `^e: |BUILD (SUCCESSFUL|FAILED)`.

Always build **`assembleRelease`**. Debug builds are 6× larger, unminified, and their performance numbers are meaningless (R8 and cold JIT differ enough to invert a conclusion — see [DESIGN.md](DESIGN.md#performance-budgets)).

## Device testing

An Android emulator (`emulator-5554`, headless) runs on the build box itself; drive it with that box's `C:\android-sdk\platform-tools\adb.exe` over SSH. Build there too — never on the Linux dev box. The phone below is for checks the emulator cannot make (real radios, real battery).

Wireless ADB to the phone. The connect port **rotates every time wireless debugging is toggled or the screen sleeps**, so never hardcode it:

```bash
~/.hermes/cache/scratch/mdnsvenv/bin/python tools/adb-port.py   # finds and connects
adb devices
```

Put player state assertions where they cannot lie:

```bash
adb shell dumpsys media_session | grep -A10 'package=one.calypsocloud'
adb shell dumpsys gfxinfo one.calypsocloud.ezamp | grep 'Janky frames'
adb logcat -d | grep -E 'ezamp-perf|ezamp-scrobble'
```

In-app perf lines use the `ezamp-perf` tag (`sync=… total=…`, `search=…ms`, `play <container>`, `tap-to-audio=…ms`). Keep them; they are the cheapest regression test we have.

## Rules

- **No dependency bumps without a reason.** The stack is pinned to the newest *buildable* set: Compose 1.12 and core-ktx 1.19 require `compileSdk 37`, which the SDK repository does not publish, so they are off-limits. Verify a version exists before using it.
- **Schema changes need a migration.** Add the table/column to `Library.sq` **and** write the next `<n>.sqm`. SQLDelight derives the schema version from migration count; skipping this crashes every existing install on first query.
- **Never read player state off the main thread.** ExoPlayer has thread verification on: `player.currentPosition` from `Dispatchers.IO` throws `IllegalStateException: Player is accessed on the wrong thread` and kills the process. Keep the scope on `Dispatchers.Main` and push only I/O to `withContext(Dispatchers.IO)`.
- **Jellyfin playlist membership is `/Playlists/{id}/Items`.** `/Items?ParentId=<playlistId>` returns nothing — it is not a folder query.
- **Do not swallow API failures.** `runCatching { … }.getOrDefault(emptyList())` hid a wrong endpoint for three builds. Catch, log with the exception message, and decide.
- **Auth travels in headers only** (`Authorization: MediaBrowser Token="…"`), never in a query string, never in a URL, never in a log line.
- **Minimal, task-scoped diffs.** Match Kotlin style already in the file: 4-space indent, explicit return types on public functions, no wildcard imports.
- **Verify on device before claiming a fix.** A green build proves compilation, nothing else. Screenshots plus view-hierarchy bounds (`uiautomator dump`) settle layout questions that eyes get wrong — twice now a vision read claimed an overlap that was not there and missed one that was.
- **Repository state:** commits land as `m3lis5a`. Push is owner-gated — never push without Calypso's explicit ok. APKs, keystores and `dist/` (screenshots plus sideload builds) are ignored by git.

## Pitfalls

- `adb install -r` preserves app data, but a **failed/partial install** can leave the app signed out; check before blaming the app for a login screen.
- Wireless ADB drops when the phone sleeps, and dies outright when Wi-Fi is toggled. Rediscover with `tools/adb-port.py` instead of asking for a port.
- Tapping by guessed coordinates has caused several misfires (one tap landed on a weather widget). Use `python3 tools/tap.py <device> <label>` — it taps the dump's own bounds for a node whose text matches.
- Automation taps land wrong when the keyboard is up: `uiautomator dump` is the only reliable way to get current coordinates, and the dump itself fails silently while a window animates (the pulled file is then stale — check `stat` mtime).
- Jellyfin reports 31 playlists here but only 4 contain audio; the rest are video playlists. Filter by "have we got audio rows", never by playlist name or `ChildCount`.
- Compose `Text` clips by default. Any single-line label needs `maxLines` **and** `overflow = TextOverflow.Ellipsis`. Cards in a row need `minLines = 2` so subtitles align.
