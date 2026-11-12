# ezamp 🎵

A fast, local-first Jellyfin music client for Android. ezamp keeps its own index of your library on the device; Jellyfin is a sync source and a file host, never part of the browsing path. Part of the `ez-*` family alongside ezmail, eziarr and ezhost.

**Status: 0.9.1** — library browsed as a swipeable ALBUMS · TRACKS · ARTISTS · GENRES carousel with search in place, a playlists tab led by a permanent Favourites playlist, shuffle and repeat, gapless playback in Direct and Saver, queue restore and scrobbling are working on device. Offline: album and playlist downloads, downloaded-only mode, a storage cap, and sync on foreground/network. Desktop is next ([ROADMAP.md](ROADMAP.md)). Verified 2026-11-11 on a Nothing A063 (Android 15) against Jellyfin 12.4.0; the 0.9.x navigation, carousel, genres, Favourites playlist and shuffle work verified 2026-11-12 on the Android emulator.

## Why this exists

Finamp did the job but felt slow: every tab was a live `/Items` request (1.0 s for 500 tracks, 1.7 s for 1,000 on this server), search hit the network per keystroke, and playback work sat next to the UI thread. The server was never the problem — direct FLAC streams start in 60 ms cold, 8 ms warm. So ezamp fixes it structurally rather than by polishing:

- **One sync, then everything is local.** A SQLite index (`SQLDelight`) holds albums, tracks, playlists and favourites. Browsing, searching and album screens never touch the network.
- **Playback lives in a `MediaSessionService`.** The Activity talks to it through a `MediaController`, so a busy player can never freeze a screen.
- **The current track's artwork rides inside the media metadata.** Now-playing does not go looking for covers in the library.

## Measured on device (release build, R8)

All numbers are from the R8 release build on the phone above. Debug-build numbers are misleading and are not quoted here; see [DESIGN.md](DESIGN.md#performance-budgets) for why.

| What | Number |
| --- | --- |
| Cold start to browsable library | **360–550 ms** |
| Search across 5,535 tracks | **5–7 ms** |
| Sync (albums + tracks) | **7.3–9.3 s** |
| Sync including playlists, warm | **8.5 s** |
| Tap → first audio | **161–388 ms** warm, 366–544 ms cold FLAC |
| Janky frames | **11.2%** |
| Memory (PSS) | **180 MB** |
| CPU while playing | **12%** of one core foreground, 17% screen-off, 2% paused |
| Release APK | **2.7 MB** (debug is 17 MB) |

Battery/CPU while playing is decode and audio routing, not UI: the main thread is idle at 0% during playback.

## Features

- **Any Jellyfin server.** Onboarding asks for the server URL, checks it is really a Jellyfin server (`/System/Info/Public`), then signs in with username and password (`/Users/AuthenticateByName`). No API key is stored in or shipped with the app.
- **Two tabs:** LIBRARY and PLAYLISTS. Back steps out one layer at a time and only leaves the app from bare Library.
- **Library:** a label strip you swipe (one page per swipe) or tap — ALBUMS (recently added shelf, then every album), TRACKS (all of them A–Z), ARTISTS (every credited name, features included), GENRES (multi-genre tags split on `;`).
- **Search:** one field under the strip filters whichever page is showing; instant and on-device (tracks over a pre-lowercased haystack column).
- **Playlists:** a permanent Favourites playlist first, then the server's playlists that actually contain music; a Jellyfin playlist that is all video is not a music playlist.
- **Playback:** plays from the `MediaSessionService`, survives leaving the app, position ticker only runs while a screen shows progress.
- **Resume:** the queue and playhead survive the process being killed. Reopen and it is where you left it.
- **Scrobbling:** start, progress (every 10 s) and stop are reported to Jellyfin, so play counts and "continue listening" stay truthful.
- **Play mode:** Direct (server's own file) or Saver (mp3 192k transcode). Measured on this library: an album is 205 MB direct and 79 MB in saver — 61 % smaller — for roughly 300 ms extra to first audio.
- **Favourites:** heart on any track row, written locally first and synced to Jellyfin; they gather in the Favourites playlist.
- **Shuffle and repeat** (off/all/one) in Now Playing, remembered across restarts; SHUFFLE on album, playlist and genre screens. Swipe Now Playing down to close, swipe the mini player to skip, and tap the media notification to come back.
- **Offline downloads:** download an album from its screen; when it is finished the same queue plays from disk with the radios off. Storage used is shown in SET, with one-tap delete.
- **Two layouts**, switchable at any time from the SET sheet: art-forward (default) and classic dense rows.

## Install

ezamp is not on Play Store; you sideload the APK.

```bash
adb install -r ezamp-<version>.apk
```

Upgrades install over the previous build and keep sign-in, index and queue. The APK is signed with a debug keystore (see [CONTRIBUTING.md](CONTRIBUTING.md#signing)); for your own build, use your own key.

## Build from source

Requires JDK 17+, Android SDK 36 (`compileSdk = 36`) and Gradle 8.14.3. On a fresh machine, `tools/` provisioning lives outside this repo — see [CONTRIBUTING.md](CONTRIBUTING.md#toolchain) for the exact versions that are known to work together, and read the pinned-version notes before bumping anything.

```bash
# VERSION at the repo root drives versionName/versionCode
./gradlew :app:assembleRelease     # 2.7 MB, R8 + resource shrinking
./gradlew :app:assembleDebug       # 17 MB, for stack traces
```

## Documentation

| File | What it is for |
| --- | --- |
| [AGENTS.md](AGENTS.md) | Condensed instructions for AI coding agents |
| [CONTRIBUTING.md](CONTRIBUTING.md) | Setup, build, device testing, review rules |
| [DESIGN.md](DESIGN.md) | Architecture, data model, theme, performance budgets |
| [CHANGELOG.md](CHANGELOG.md) | Per-version history |
| [ROADMAP.md](ROADMAP.md) | Milestones, what is done, what is next, non-goals |
| [HANDOFF.md](HANDOFF.md) | Current state, open issues, and how to pick this up cold |
| [VERSION](VERSION) | The version string, single source of truth |

## License

MIT — see [LICENSE](LICENSE).
