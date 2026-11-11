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
- ✅ Artists tab.
- ✅ Release configuration: R8, 2.7 MB, and the first trustworthy perf numbers.

## M2 — Playback depth 🚧

- ✅ Playlists (shelf, screen, PLAY ALL, per-track play), filtered to playlists that hold music.
- ✅ Two-phase sync with incremental playlist refresh — 24.9 s → 8.5 s.
- ✅ Queue persistence and resume across process death.
- ✅ Scrobbling: start/progress/stop to Jellyfin.
- ✅ **Offline downloads (album level)** — tracks fetch to app storage with progress, cancel and delete; playback prefers the local file, so a downloaded album plays with Wi-Fi and mobile data both off. Playlist-level download and a downloaded-only browsing mode are in M3.
- ✅ **Track boundaries** — measured, not guessed: ExoPlayer 1.9.4 already buffers the next item (boundary arrives in `READY`, 26–38 s buffered, no `BUFFERING` event), so no preload config was needed and none was shipped.
- ✅ **Play mode** — Direct or Saver (mp3 192k), with downloads following the same switch.
- ✅ **Favourites** — heart on track rows, local-first then Jellyfin.
- 🚧 Gapless in the strict sense (encoder delay/padding between consecutive same-codec files) is still unverified — the network boundary is clean, which is a different question.
- ✅ Fix the `start` scrobble carrying `pos=0` when a restored queue resumes.

## M3 — Offline-first hardening ⬜

- ⬜ Downloaded-only mode (airplane-mode browsing and playback as a first-class state, not a degradation).
- ⬜ Storage management UI: per-album size, delete, re-download, and a cap.
- ⬜ Sync on a schedule and on network change; conflict-free since the index is read-only.
- ⬜ Large-library validation: the reference library is 5,535 tracks / 532 albums; validate at ~50,000 tracks and measure sync, search and index size.

## M4 — Desktop (Kotlin Multiplatform) ⬜

- ⬜ Extract the platform-independent core: DTOs, API client, schema, sync logic.
- ⬜ Linux and Windows shells (Compose Multiplatform), sharing the index format.
- ⬜ Local file playback path shared with downloads.

## M5 — macOS and iOS ⬜

- ⬜ Same shared core, native playback sessions, platform-appropriate background audio and Now Playing integration.

## M6 — Polish ⬜

- ⬜ Search: FTS5 instead of `LIKE` if a large library demands it.
- ⬜ Track-level artists (features/splits) — the artists tab currently groups album artists only: 54 of the 127 Jellyfin knows.
- ⬜ Crossfade, equalizer, sleep timer — only if they can be done without touching the UI thread.
- ⬜ Accessibility pass: content descriptions on the Canvas icons, dynamic type.

## Non-goals

- **No server-side component.** The server is fast; the client was the bottleneck.
- **No fork of Finamp.** It would inherit the architecture being escaped.
- **No web UI.** Compose on Android and desktop covers the platforms that were asked for.
- **No API key in the app.** Username/password only.
- **No transcode path in v1.** Direct play covers this library; transcoding is only worth it once a codec outside mp3/flac/ogg actually appears.
