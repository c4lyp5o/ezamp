# Changelog

All notable changes to ezamp are recorded here. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project uses [Semantic Versioning](https://semver.org/). `VERSION` at the repo root is the single source of truth for the version string.

## [0.7.0] — 2026-11-12

### Added
- **Offline-first hardening (M3).** Downloaded-only mode is a first-class state: a persisted `downloadsOnly` switch scopes every screen in SQL (albums, recents, tracks, search, favourites, artists, playlists), with partial `3/12` badges, an OFFLINE chip, and a SET toggle. Tapping a non-downloaded track while offline refuses loud with a warn notice instead of silent nothing.
- **Storage management.** `SET → STORAGE` shows used-of-cap, 1/2/4/8 GB pills (default 4 GB), per-album bytes biggest-first with DELETE plus DELETE EVERYTHING. Album screens show CAP REACHED when the cap refuses new work — the cap refuses, never evicts.
- **Sync on foreground and network change.** Returning to the app or regaining connectivity syncs when 15 min elapsed since the last success; manual SYNC always bypasses the throttle. A warn banner appears while offline ("NO CONNECTION — tap to browse downloads") with one-tap escape into downloaded mode. No WorkManager, no new dependency.
- **Header into the title row.** The EZAMP header is gone; Library + SYNC + SET share one row. (Reversible from git history.)

### Performance
- **50k-track validation (synthetic rows, local only).** Every scoped query under 10 ms at 10x the reference library; one new index on `libraryAlbum(created)` removed the only temp B-tree sort (`recentAlbums`/`scopedRecent` → 0.0 ms). Harness kept as a throwaway, untracked.

## [0.6.0] — 2026-11-12

### Added
- **Play mode: Direct or Saver.** `SET → PLAYBACK`. Direct streams the server's own file; Saver asks Jellyfin to transcode to **mp3 192k**. Downloads follow the same switch, so one setting governs both what you stream and what you store.
- **Transcode marker.** A quiet `⇄` chip in the mini player (the same glyph ezllm uses for a translated hop) whenever the current track is being transcoded.
- **Favourites.** A heart on every track row — album, playlist, search and favourites. Tapping writes to local SQLite first, then to Jellyfin (`POST/DELETE /Users/{id}/FavoriteItems/{itemId}`); the favourites tab refreshes immediately.
- **Scrolling titles.** Long track titles scroll in the mini player instead of clipping mid-word.

### Changed
- Downloaded files now carry an extension and their own mime, captured at download time. A track downloaded while Saver was on stays an mp3 even after switching to Direct.

### Fixed
- **Seeking a transcode.** Jellyfin does not serve Range on transcoded output, so a normal seek cannot work. A saver seek now restarts the transcode at the target and treats that as the new zero: position, duration, resume and scrobble all offset correctly (verified: seek to 92.9 s of a 185 s track, playback resumed at 1:32 of 3:05, zero player errors).
- **Scrobble positions.** Reports carried `pos=0` on every restore because the service read the item's relative position and one path hardcoded `0L`. Positions are now absolute (offset read from the URL), and the "start" report waits 700 ms so a restored seek can land: `start http=204 pos=124820ms`.
- **Saver mode showed `0:00` as the track duration**, because a live transcode reports no duration; the queue's own metadata is used instead.

### Note on preloading
- I added an explicit 20 s preload and measured it against a control build with preloading off. ExoPlayer 1.9.4 already buffers the next playlist item itself: a natural boundary arrives in `READY` with 26–38 s buffered and no `BUFFERING` event in **both** builds, and skip-to-audio was 11–17 ms either way. The config was removed rather than shipped as cargo cult.

## [0.5.0] — 2026-11-11

### Added
- **Offline downloads.** An album's DOWNLOAD action on the album screen fetches every track to `filesDir/downloads` with live progress (`DOWNLOADING 3/15`), then reads `DOWNLOADED 15/15`. Tapping a finished album deletes its files. Downloads are recorded in the new `libraryDownload` table (`state`, `bytes`, `totalBytes`, `path`), so they survive restarts and are never touched by a library sync.
- Playback prefers a downloaded file: the player builds the same `MediaItem` with a `file://` URI, so offline playback is the only playback path, not a parallel one.
- Storage line in the SET sheet: `OFFLINE x MB ON DEVICE` with DELETE for everything.

### Notes
- Two concurrent fetches, progress flushed every 512 KB rather than per chunk; cancelled and failed downloads leave no partial file behind.

## [0.4.4] — 2026-11-11

### Fixed
- **Keyboard covered the mini player and nav bar.** The scaffold padded both the content and the bottom bar for the IME, so the bar's background was drawn at `y1466–1578` while its own labels ran to `y1624` and the mini-player row ended at `y1521` — the bar's surface floated over the player. The inset now applies once, on a single bottom-bar surface, and the tab bar steps aside while the keyboard is open.

### Changed
- `VERSION` now drives `versionName` and `versionCode` (`major*10000 + minor*100 + patch`). Previously every build reported `0.1.0-alpha` regardless of the APK's filename.
- Documentation set added: README, AGENTS, CONTRIBUTING, DESIGN, ROADMAP, HANDOFF, CHANGELOG, VERSION and an MIT license.

## [0.4.3] — 2026-11-11

### Fixed
- **Fatal crash on the first play after upgrading:** `IllegalStateException: Player is accessed on the wrong thread`. The scrobble ticker read `player.currentPosition` from `Dispatchers.IO`; ExoPlayer runs with thread verification. Player reads stay on Main, only the HTTP call moves to IO.

## [0.4.2] — 2026-11-11

### Added
- Scrobble reporting to Jellyfin: `/Sessions/Playing`, `/Sessions/Playing/Progress` every 10 s, `/Sessions/Playing/Stopped`, with `PlaySessionId` pinned to the install's device id and `PlayMethod: DirectPlay`. Verified HTTP 204 on all three.

## [0.4.1] — 2026-11-11

### Added
- **Queue persistence and resume.** The queue and playhead are written to `filesDir/queue.json` on play, on track change and every 5 s while playing; on cold start the queue is rebuilt without auto-playing. Killing the process at 17.9 s and reopening resumes at 19.8 s.

## [0.4.0] — 2026-11-11

### Added
- **Playlists.** Shelf on the library screen and a playlist screen with PLAY ALL and per-track playback. Membership is fetched from `/Playlists/{id}/Items` (the `/Items?ParentId` route returns nothing for playlists). Only playlists that contain audio appear: on the reference library 4 of 31 playlists hold music, the rest are video playlists.
- **Artists tab**, grouped by album artist with album counts and tap-through.

### Changed
- **Sync split into two phases.** Albums and tracks publish the index first (7.3–9.3 s), playlists sync afterwards, so browsing is usable before the slow part finishes. Playlist re-fetch is skipped when the server's `ChildCount` matches the stored `remoteCount`: total sync 24.9 s → 8.5 s warm.
- Playlist track counts now mean *stored audio rows*, not Jellyfin's `ChildCount`, which counts video entries.

### Fixed
- Shelf card titles clipped and collided with their subtitles: titles reserve two lines (`minLines = 2`) plus spacing, so every subtitle in a row aligns.

## [0.3.1] — 2026-11-11

### Added
- `ezamp-perf` logging for `play <container>` and `tap-to-audio`.

### Changed
- ExoPlayer `DefaultLoadControl` retuned for music: `bufferForPlayback` 2,500 ms → 400 ms, `minBuffer` 20 s, `maxBuffer` 30 s. Tap→audio 552 ms → 161–388 ms.

## [0.3.0] — 2026-11-11

### Added
- **SQLite index (SQLDelight 2.4.1)** replacing the JSON file: `libraryAlbum`, `libraryTrack`, indexes, and a pre-lowercased `haystack` column for search. Tracks are no longer held in memory.
- Release build configuration: R8 + resource shrinking, 2.7 MB APK versus 17 MB debug.
- Artists query derived from album rows.

### Changed
- Sync pulls albums and tracks **in parallel**; the separate favourites pass was deleted because `UserData.IsFavorite` already arrives on every track row.
- `PlayState.artUri` comes from `MediaMetadata.artworkUri`; the player no longer looks covers up in the library.

### Fixed
- Search latency was printed as `11738 ms`: microseconds divided by 1,000 instead of 1,000,000.

## [0.2.0] — 2026-11-11

### Added
- **ez house style**, ported token-for-token from the shared `ez/ez.css`: dark surfaces, rose accent `#EF7A98`, 6 dp radius, Public Sans variable font, tabular figures.
- **Two-step onboarding**: server URL → `/System/Info/Public` check (shows server name and version) → username and password via `/Users/AuthenticateByName`. The API-key path was removed from the app.
- **Two layouts**, art-forward and classic, switchable from the SET sheet (`Prefs.variant`).
- Human-readable error copy for connection failures.

### Fixed
- Text clipped mid-glyph (`maxLines` without `overflow`) on 13 call sites.
- Keyboard covered the mini player.
- Ktor internals leaked into UI copy (`Connect timeout has expired [url=…]`).

## [0.1.0] — 2026-11-11

### Added
- First working client: album grid, album detail, search, favourites, mini player, now-playing screen, hand-drawn Canvas transport icons.
- Playback in a `MediaSessionService` with a `MediaController` façade, per-request auth on the data source, and explicit MIME types for extension-less stream URLs.
- Remote album art through Coil with an auth interceptor; missing covers degrade to a typographic tile showing the album initial.
