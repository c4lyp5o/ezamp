# Roadmap

Milestones for ezamp. Platform order was decided up front: **Android → Linux/Windows → macOS/iOS**. Every milestone ends with numbers measured on a release build, not a claim.

Legend: ✅ done and verified · 🚧 in progress · ⬜ not started

## M0 — Measure before building ✅

Established what was actually slow, so the architecture targets a real bottleneck instead of a hunch.

- Jellyfin `/Items` costs ~1 s per 500 tracks, 1.7 s per 1,000 — the reason a thin client feels sluggish.
- Server-side media is fast: direct FLAC 206 with 60 ms cold / 8 ms warm TTFB, MP3 32 ms, 200 px artwork 12 KB in 20 ms.
- Conclusion: the client was the problem, and a local index plus a service-hosted player is the fix.

## M1 — Local index and the app's face ✅

- ✅ SQLite index via SQLDelight; browsing, search and album screens never touch the network.
- ✅ ez house style ported from the shared `ez/ez.css`; rose accent; Public Sans; tabular figures.
- ✅ Onboarding: server URL probe → Jellyfin check → username/password; no API key in the app.
- ✅ Reversible layouts (art-forward default, classic dense list).
- ✅ Sign-in failure paths tested: unreachable host, non-Jellyfin host, wrong password.
- ✅ Artists tab (an ARTISTS carousel page since 0.9.0).
- ✅ Release configuration: R8, 2.7 MB, and the first trustworthy perf numbers.

## M2 — Playback depth ✅ 2026-11-12

- ✅ Playlists (shelf, screen, PLAY ALL, per-track play), filtered to playlists that hold music. A tab of their own since 0.9.0.
- ✅ Two-phase sync with incremental playlist refresh — 24.9 s → 8.5 s.
- ✅ Queue persistence and resume across process death.
- ✅ Scrobbling: start/progress/stop to Jellyfin.
- ✅ **Offline downloads (album level)** — tracks fetch to app storage with progress, cancel and delete; playback prefers the local file, so a downloaded album plays with Wi-Fi and mobile data both off. Playlist-level download and a downloaded-only browsing mode are in M3.
- ✅ **Track boundaries** — measured, not guessed: ExoPlayer 1.9.4 already buffers the next item (boundary arrives in `READY`, 26–38 s buffered, no `BUFFERING` event), so no preload config was needed and none was shipped.
- ✅ **Play mode** — Direct or Saver (mp3 192k), with downloads following the same switch.
- ✅ **Favourites** — heart on track rows, local-first then Jellyfin.
- ✅ **Gapless, both modes** — verified by ear on a true gapless album in Direct and Saver, with a boundary-timing probe log showing 43–45 s prefetch at every transition (buffer innocent, renderer handoff clean). The earlier ~1 s report was the first album's own mastering, not the player. M2's last open item, closed 2026-11-12.
- ✅ Fix the `start` scrobble carrying `pos=0` when a restored queue resumes.

## M3 — Offline-first hardening ✅ 2026-11-12

- ✅ Downloaded-only mode (airplane-mode browsing and playback as a first-class state, not a degradation). SQL scope, partial badges, OFFLINE chip, play guard — proven on device with screenshots.
- ✅ Storage management UI: per-album size, delete, re-download, and a cap that refuses, never evicts.
- ✅ Sync on foreground and network change (15-min throttle, manual bypass); conflict-free since the index is read-only. No-network banner with one-tap escape.
- ✅ Large-library validation: 50k synthetic tracks locally — every scoped query under 10 ms, one index added for the recency sort.

## M3.5 — Open debts ✅ 2026-11-12

- ✅ Playlist download (batch-keyed cancel and cap stop; tracks keep their real album).
- ✅ JVM unit tests on every gate (34), migration verification against shipped schema snapshots.
- ✅ Migration 5 for the 0.7.0 recency index that upgraded installs never received.

## M3.6 — Navigation and browsing ✅ 2026-11-12 (0.9.0, 0.9.1)

- ✅ Back steps out one layer at a time; the media notification opens the app.
- ✅ Library carousel (ALBUMS · TRACKS · ARTISTS · GENRES) with search per page; the SEARCH and ARTISTS tabs retired.
- ✅ Genres — `libraryTrackGenre` + migration 7, `databases/7.db` snapshot of 0.8.0.
- ✅ Two tabs: LIBRARY · PLAYLISTS, with Favourites as a permanent playlist.
- ✅ Shuffle and repeat, SHUFFLE on lists, swipe gestures on Now Playing and the mini player.

## M4 — Polish ⬜

- ⬜ Search: FTS5 instead of `LIKE` if a large library demands it.
- ✅ Track-level artists (features/splits) — 0.8.0, `libraryTrackArtist` + migration 6.
- ⬜ Crossfade, equalizer, sleep timer — only if they can be done without touching the UI thread.
- ✅ Accessibility pass: every drawn or glyph control (play/pause, skip, close, hearts, clear search) has a spoken label and button role; screens checked at 130% system text.
- ✅ ID3 numeric genres ("13", "(13)", "(13)Pop", "(RX)") map to names (ID3v1 + Winamp table, 0–191) at sync.
- ✅ Now Playing on short (16:9) screens: the cover shrinks to fit, so the queue counter stays visible.

## M5 — Desktop (Kotlin Multiplatform) ⬜

- ⬜ Extract the platform-independent core: DTOs, API client, schema, sync logic.
- ⬜ Linux and Windows shells (Compose Multiplatform), sharing the index format.
- ⬜ Local file playback path shared with downloads.

## M6 — macOS and iOS ⬜

- ⬜ Same shared core, native playback sessions, platform-appropriate background audio and Now Playing integration.

## Non-goals

- **No server-side component.** The server is fast; the client was the bottleneck.
- **No fork of Finamp.** It would inherit the architecture being escaped.
- **No web UI.** Compose on Android and desktop covers the platforms that were asked for.
- **No API key in the app.** Username/password only.
- **No transcode-to-play-anything path.** Direct play is the default; Saver (mp3 transcode) exists to save bandwidth and storage, not to rescue unsupported codecs.
