# ezamp architecture & design

How ezamp is put together and why. Companion to [README.md](README.md) (what it does) and [HANDOFF.md](HANDOFF.md) (where it currently stands).

## The one idea

**The network is never on a UI path.** A sync copies the library into a local SQLite index; every screen reads that index. Playback streams from the server, but even the queue survives the process dying. If the server is down or the Wi-Fi drops, browsing, search and album screens behave exactly as they do online.

This is the deliberate opposite of a thin client — and the specific reason Finamp felt slow: a thin client pays ~1 s per 500 tracks for every tab, every search keystroke and every album open.

## Layers

```
UI (Compose)  ── reads ──►  LibraryStore.state (StateFlow)
   │                              ▲
   │ play/seek                    │ refresh() from SQLite
   ▼                              │
PlayerHolder (MediaController) ──► MediaSessionService (ExoPlayer)
   │                                     │
   │ queue.json                          │ /Sessions/Playing (scrobble)
   ▼                                     ▼
          JellyfinApi (Ktor, auth in header) ──► Jellyfin server
```

Rules that keep the layers honest:

- **UI never calls the API.** Screens read `LibraryStore.state` and call `LibraryStore` suspend functions; only the store and the player service talk HTTP.
- **The player never reaches into the library.** Artwork, title, artist and album travel inside `MediaMetadata`; `PlayState` is built from the player, not from a lookup.
- **Sync writes, UI reads.** A sync replaces the index inside one transaction; `refresh()` then re-reads small aggregates (album list, favourites, playlists, counts). The full track list is never materialised in memory — `trackCount` is a `COUNT(*)`.

## Data model

SQLite through SQLDelight 2.4.1 (`app/src/main/sqldelight/.../Library.sq`). Table names are prefixed so generated row types (`LibraryAlbum`, `LibraryTrack`) cannot collide with the domain models (`Album`, `Track`).

| Table | Purpose | Notes |
| --- | --- | --- |
| `libraryAlbum` | one row per album | `sortName`, `artist` (album artist), `year`, `trackCount`, `imageTag` |
| `libraryTrack` | one row per audio item | `albumId`, `haystack` (lowercased `title + artist + album`), `favourite`, `container`, `durationMs` |
| `libraryPlaylist` | one row per playlist holding music | `trackCount` = **stored audio rows**, `remoteCount` = server's `ChildCount` (change detection) |
| `libraryPlaylistTrack` | playlist membership | `(playlistId, trackId, position)` |
| `libraryDownload` | offline copies (own table; `mime` captured at fetch time) | `state` (0 queued, 1 downloading, 2 done, 3 failed), `bytes`, `totalBytes`, `path` |

Indexes cover `libraryTrack(albumId)`, `libraryTrack(sortName)`, `libraryAlbum(sortName)` and `libraryPlaylistTrack(playlistId, position)`.

**Migrations.** `1.sqm` adds the playlist tables, `2.sqm` adds `remoteCount`, `3.sqm` adds `libraryDownload`. Every schema change needs both the `.sq` edit and the next `.sqm`; SQLDelight derives the schema version from the migration count, so a `.sq`-only change crashes existing installs on the first query.

**Search** is `LIKE '%needle%'` against a pre-lowercased `haystack` column — no `LOWER()` per row, no FTS table yet. 5–7 ms over 5,535 rows.

## Sync

Two phases, because they have wildly different costs:

1. **Albums + tracks, in parallel** (`coroutineScope { async(IO) … }`). Favourites are not a separate request: `UserData.IsFavorite` already rides on every track row, so the old third pass was deleted.
2. **Playlists**, after the library is already visible. Membership is one request per playlist (`/Playlists/{id}/Items`) and playlists can mix video with audio, so each one is fetched, filtered to known audio ids, and counted honestly.

Phase 2 is incremental: a playlist whose server-side `ChildCount` matches the stored `remoteCount` is not re-fetched at all. On this library that turns a 15.7 s phase into 1.1 s (`fetched=0`).

Measured: phase 1 lands at **7.3–9.3 s**; whole sync **8.5 s** warm, ~22–25 s on the very first run when every playlist is unknown.

## Playback

- **`PlaybackService : MediaSessionService`** owns ExoPlayer. That is the structural fix for "app stops responding while playing": the Activity only holds a `MediaController`.
- **`DefaultLoadControl` is tuned for music**, not video: `minBuffer 20 s`, `maxBuffer 30 s`, `bufferForPlayback 400 ms` (down from Media3's 2,500 ms default), `bufferForPlaybackAfterRebuffer 800 ms`, time-prioritised. The default's 2.5 s pre-buffer was most of a 550 ms tap→audio.
- **Auth resolves per request**, not once at service creation: a `DataSource.Factory` closure reads `Prefs.token` when the source is built, so signing in after the service starts still works.
- **Direct play only.** Every sampled track reports `SupportsDirectPlay=true` and the library is mp3/flac, so there is no transcoder in the v1 playback path.
- **Position ticker** runs at 1 s and only while a screen shows a progress bar (`setPositionTracking`), because a 500 ms ticker recomposing the tree cost 44% CPU.

### Queue persistence & resume

`QueueStore` writes `filesDir/queue.json` — the queue is session state, so it must survive process death but must never be clobbered by a library sync. Written on play, on track change, and every 5 s while playing; a tmp file plus rename avoids torn writes; capped at 2,000 items.

On cold start `PlayerHolder.restore()` rebuilds `MediaItem`s and calls `setMediaItems(items, index, positionMs)` **without** `play()`, so the app reopens paused at the right place. Verification: killed at 17.9 s → reopened → played from 19.8 s (drift bounded by the 5 s save interval).

### Scrobbling

`/Sessions/Playing` on start, `/Sessions/Playing/Progress` every 10 s while playing, `/Sessions/Playing/Stopped` on end/destroy, with `PlaySessionId = Prefs.deviceId` and `PlayMethod = DirectPlay`. Without this the server's play counts and resume points come from every client except the one actually in use.

All player reads stay on `Dispatchers.Main`; only the HTTP call moves to IO. This is not style — ExoPlayer verifies threads and throws fatally otherwise.

## Play mode — Direct and Saver

`Prefs.playMode` is one switch with one meaning: *what bytes do we ask the server for*. Both playback and downloads read it at the moment they act, so the switch can never leave a stale URL behind:

| | Direct (default) | Saver |
| --- | --- | --- |
| Stream | `/Audio/{id}/stream.{ext}?static=true` | `/Audio/{id}/stream.mp3?static=false&audioCodec=mp3&audioBitRate=192000` |
| Downloads | the server's own file | the same transcode, saved as mp3 |
| Cost | 205 MB per album here | 79 MB per album here |

`Track` deliberately describes the **server's** file and says nothing about the mode; the player and the downloader decide how to fetch it. That is why flipping the switch takes effect on the next play instead of needing a queue rebuild.

**Seeking a transcode is a rebuild, not a seek.** Jellyfin does not serve Range on transcoded output, so a progressive transcode has no seekable timeline. Instead:

- the item's URL carries `startTimeTicks`, and `PlayerHolder.seekBase` holds that offset;
- every position the UI shows, persists, or scrobbles is `seekBase + playerPosition`;
- a seek replaces the item at the same index with a fresh URL at the target, then treats that as zero;
- the duration comes from the queue's metadata, because a live transcode reports none.

`PlaybackService.absolutePosition()` re-derives the offset from the URL rather than being handed it, which keeps the service independent of the UI-side queue.

## Favourites

Local first, server second: the row in SQLite flips immediately (that is what the UI renders), then `POST`/`DELETE /Users/{userId}/FavoriteItems/{itemId}` tells Jellyfin. If the call fails the local row stays ahead until the next sync corrects it — the right way round for a client whose index is the truth.

## Track boundaries

Measured on 1.9.4 rather than assumed: a natural transition (`reason=1`) arrives with `PlaybackState=READY`, the next item already buffered 26–38 s ahead, and no `BUFFERING` state change. An explicit 20 s `PreloadConfiguration` produced identical numbers (skip-to-audio 11–17 ms either way) and was removed. Strict gaplessness — encoder delay and padding between consecutive files — is a property of the files, not the player, and remains unmeasured.

## Offline downloads

Files live in `filesDir/downloads`, one per track, named by track id; the `libraryDownload` table is the record of what is present and in what state. Presence of the file is what makes a track playable offline — `Downloads.localFile()` checks it, so a row claiming DONE whose file vanished is demoted to FAILED at startup rather than trusted.

Two properties matter more than the transfer itself:

- **One playback path.** `PlayerHolder.toMediaItem()` asks for a local file and falls back to the stream, producing the same `MediaItem` either way. There is no "offline mode" branch in the player, so the two paths cannot drift.
- **Sync never sees it.** Downloads have their own table, written outside the library transaction, so a sync cannot delete or overwrite them.

Transfers run on `Dispatchers.IO` with a two-permit semaphore, write to a `.part` file and rename on success (so a killed process cannot leave a half-file that looks complete), flush progress to the state flow every 512 KB instead of per chunk, and delete the part file on failure or cancel.

## Theme — the ez house style

Ported token-for-token from the canonical `ez/ez.css` shared by the nine `ez-*` apps (byte-identical across them, ported from ezllm/web + ezdyndns), not invented here.

| Token | Value |
| --- | --- |
| background | `#171719` |
| surface | `#131217` |
| raised | `#212023` |
| line | `#232326` |
| ink | `#f2f2f3` |
| dim | `#bdbdc0` |
| mute | `#8a8c92` |
| ok / warn / bad | `#44a282` / `#d9a441` / `#e5566f` |
| accent (ezamp) | rose `#EF7A98` — the only free slot in the family |
| radius | 6 dp |
| type | Public Sans variable, 13.5 sp body, 1.55 line height, tabular figures on numbers |

Rules: colour only on data and status, never decoration; one accent per app; tabular figures for every count, time and duration so columns do not jitter.

## Layouts

`Prefs.variant` selects `"art"` (two-column art-forward grid, default) or `"classic"` (dense 46 dp rows), switched from the SET sheet. It is a preference, not a second codebase: the same screens, different list composables.

## Performance budgets

Targets, all measured on the release build:

| Budget | Target | Current |
| --- | --- | --- |
| Cold start → browsable | < 1,000 ms | 360–550 ms |
| Search | < 50 ms | 5–7 ms |
| Tap → audio | < 300 ms | 161–388 ms warm |
| Sync | < 10 s | 8.5 s |
| Janky frames | < 15% | 11.2% |

**Debug builds lie.** The 550 ms tap→audio was identical on debug and release, which killed the "it's just JIT" theory and pointed at the real cause (Media3's 2.5 s pre-buffer). Equally, jank fell 43% → 11.2% and PSS 274 → 180 MB purely by shipping R8. Never quote a debug number as a result.

Remaining known cost while playing is decode + audio routing (`MediaCodec_loop`, `ExoPlayer:Playb`, `HwBinder`), 12% of one core foreground and 2% paused; the UI thread is idle. That is expected, not a bug — an audio offload path is not available in Media3 1.9.4 (`setEnableAudioOffload` was removed).

## Deliberate non-goals

- No server-side component: the server is fast; the client was the problem.
- No fork of Finamp: it would inherit the architecture being escaped.
- No web UI, no sync engine of our own, no library write-back beyond play state.
- No API key in the app; onboarding is server probe → username/password → token.
