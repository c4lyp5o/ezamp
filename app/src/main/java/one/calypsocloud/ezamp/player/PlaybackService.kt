package one.calypsocloud.ezamp.player

import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import one.calypsocloud.ezamp.data.Prefs

/**
 * Playback lives here, not in the Activity. That is the whole reason the app stays
 * responsive while audio is running.
 */
class PlaybackService : MediaSessionService() {

    private var player: ExoPlayer? = null
    private var session: MediaSession? = null
    private var reportedItem: String? = null
    // ExoPlayer is single-threaded with thread verification on: read player state on Main only.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()

        // Resolve the token per media source creation, not once at service start:
        // the service can be created before the user has signed in.
        val dataSource = androidx.media3.datasource.DataSource.Factory {
            val httpNow = DefaultHttpDataSource.Factory()
                .setDefaultRequestProperties(mapOf("Authorization" to "MediaBrowser Token=\"${Prefs.token ?: ""}\""))
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(15_000)
                .setReadTimeoutMs(30_000)
            DefaultDataSource.Factory(this, httpNow).createDataSource()
        }

        // Default LoadControl pre-buffers 2.5 s before it will start audio, which is most of our
        // 550 ms tap->sound. Music on a LAN needs far less runway than video.
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 20_000,
                /* maxBufferMs = */ 30_000,
                /* bufferForPlaybackMs = */ 400,
                /* bufferForPlaybackAfterRebufferMs = */ 800,
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val exo = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSource))
            .setLoadControl(loadControl)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        // No PreloadConfiguration here on purpose: measured on 1.9.4, a natural item boundary already
        // arrives in READY with the next item buffered (26-38 s ahead) and no BUFFERING event, and an
        // explicit 20 s preload target changed nothing. See DESIGN.md, "Track boundaries".

        exo.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying && transitionAt > 0) {
                    android.util.Log.i(
                        "ezamp-perf",
                        "skip-to-audio=${android.os.SystemClock.uptimeMillis() - transitionAt}ms",
                    )
                    transitionAt = 0
                }
                val id = exo.currentMediaItem?.mediaId ?: return
                if (isPlaying) {
                    // Let a restored seek land first, otherwise "start" reports 0:00 every cold start.
                    scope.launch {
                        delay(700)
                        report("start", id, absolutePosition(), false)
                    }
                } else {
                    report("progress", id, absolutePosition(), true)
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                transitionAt = android.os.SystemClock.uptimeMillis()
                mediaItem?.mediaId?.let { id ->
                    report("start", id, absolutePosition(), false)
                    android.util.Log.i(
                        "ezamp-perf",
                        "transition $id reason=$reason state=${exo.playbackState} bufferedNext=${exo.bufferedPosition}",
                    )
                }
            }

            // ExoPlayer's shuffle order is random over the whole queue, so the current track can
            // land mid-order and everything "before" it never plays. Keep the current track first
            // whenever shuffle turns on or the queue is replaced. No-op when it already is first,
            // which also stops our own setShuffleOrder from re-triggering this.
            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                if (shuffleModeEnabled) currentFirst(exo)
            }

            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED && exo.shuffleModeEnabled) {
                    currentFirst(exo)
                }
            }

            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                Eq.attach(audioSessionId)
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_BUFFERING) {
                    android.util.Log.i("ezamp-perf", "buffering at ${absolutePosition()}ms")
                }
                if (state == Player.STATE_ENDED) {
                    exo.currentMediaItem?.mediaId?.let { report("stop", it, absolutePosition(), false) }
                }
            }
        })

        player = exo
        SleepTimer.attach(exo)
        Eq.attach(exo.audioSessionId)
        // Without a session activity the notification has nothing to fire, so tapping it did nothing.
        val open = android.app.PendingIntent.getActivity(
            this,
            0,
            Intent(this, one.calypsocloud.ezamp.MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, exo).setSessionActivity(open).build()
        startReporting()
    }

    private fun currentFirst(exo: ExoPlayer) {
        val n = exo.mediaItemCount
        if (n < 2) return
        val cur = exo.currentMediaItemIndex
        if (exo.currentTimeline.getFirstWindowIndex(true) == cur) return
        val rest = (0 until n).filter { it != cur }.shuffled()
        exo.setShuffleOrder(
            androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder(
                intArrayOf(cur) + rest.toIntArray(),
                System.nanoTime(),
            ),
        )
        android.util.Log.i("ezamp-perf", "shuffle order: current first of $n")
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    /**
     * Position within the *track*. A saver transcode starts at whatever offset we asked for, so the
     * player's own position is relative to that; the offset is in the URL, which keeps this
     * self-contained (no coupling to the UI-side queue).
     */
    private var transitionAt = 0L

    private fun absolutePosition(): Long {
        val p = player ?: return 0L
        val uri = p.currentMediaItem?.localConfiguration?.uri?.toString().orEmpty()
        return one.calypsocloud.ezamp.data.JellyfinApi.transcodeOffsetMs(uri) + p.currentPosition.coerceAtLeast(0)
    }

    /** Progress every 10 s keeps the server's position honest without spamming it. */
    private fun startReporting() {
        scope.launch {
            while (true) {
                delay(10_000)
                val p = player ?: continue
                val id = p.currentMediaItem?.mediaId ?: continue
                if (p.isPlaying) report("progress", id, absolutePosition(), false)
            }
        }
    }

    private fun report(event: String, itemId: String, positionMs: Long, paused: Boolean) {
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { api()?.reportPlayback(event, itemId, positionMs, paused) } }
                .onSuccess { code ->
                    // Progress fires every 10 s; only speak up when something is wrong.
                    if (event != "progress" || (code != 200 && code != 204)) {
                        android.util.Log.i("ezamp-scrobble", "$event http=$code pos=${positionMs}ms")
                    }
                    // A new session closed the previous track's; favourites changed during it
                    // can now be re-sent without being overwritten (see LibraryStore.favReplay).
                    if (event == "start" && code in 200..299) {
                        one.calypsocloud.ezamp.data.LibraryStore.replayFavourites(itemId)
                    }
                }
                .onFailure { android.util.Log.w("ezamp-scrobble", "$event failed: ${it.message}") }
        }
    }

    private fun api(): one.calypsocloud.ezamp.data.JellyfinApi? {
        val base = Prefs.baseUrl ?: return null
        val token = Prefs.token ?: return null
        return one.calypsocloud.ezamp.data.JellyfinApi(base, token)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        player?.let { p ->
            p.currentMediaItem?.mediaId?.let { report("stop", it, p.currentPosition.coerceAtLeast(0), false) }
        }
        player?.let { SleepTimer.detach(it) }
        Eq.release()
        scope.cancel()
        session?.let { s ->
            s.player.release()
            s.release()
        }
        session = null
        player = null
        super.onDestroy()
    }
}
