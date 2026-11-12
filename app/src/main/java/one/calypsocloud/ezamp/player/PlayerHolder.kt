package one.calypsocloud.ezamp.player

import android.content.ComponentName
import android.content.Context
import android.os.SystemClock
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import one.calypsocloud.ezamp.data.LibraryStore
import one.calypsocloud.ezamp.data.Prefs
import one.calypsocloud.ezamp.data.Track

data class PlayState(
    val itemId: String? = null,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val playing: Boolean = false,
    val buffering: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val hasNext: Boolean = false,
    val hasPrev: Boolean = false,
    val count: Int = 0,
    val index: Int = 0,
    val artUri: String? = null,
    /** True when this track is being fetched as a transcode rather than the server's own file. */
    val transcoding: Boolean = false,
    /** Transient refusal reason, set when play() declines (e.g. offline mode). Cleared on next play. */
    val notice: String? = null,
    val shuffle: Boolean = false,
    /** Player.REPEAT_MODE_OFF / _ALL / _ONE. */
    val repeat: Int = Player.REPEAT_MODE_OFF,
) {
    val active: Boolean get() = itemId != null
}

object PlayerHolder {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _state = MutableStateFlow(PlayState())
    val state: StateFlow<PlayState> = _state

    private var controller: MediaController? = null
    private var appCtx: Context? = null
    private var queue: List<Track> = emptyList()
    private var savedIndex = -1
    private var lastSaveAt = 0L
    private var pending: Pair<List<Track>, Int>? = null
    private var tapAt = 0L
    private var firstAudioReported = false
    private var tickerStarted = false
    private var positionTracking = false

    /**
     * A progressive transcode starts at whatever `startTimeTicks` we asked for, so its timeline is
     * offset by that much. `seekBase` is that offset: every position we expose is seekBase + player
     * position, and seeking means rebuilding the item at the new offset (Jellyfin does not serve
     * Range on transcoded output, so a normal seek cannot work).
     */
    private var seekBase = 0L
    private var seekIndex = -1

    fun connect(ctx: Context) {
        appCtx = ctx.applicationContext
        if (controller != null) return
        val token = SessionToken(ctx, ComponentName(ctx, PlaybackService::class.java))
        val future = MediaController.Builder(ctx, token).buildAsync()
        future.addListener({
            val c = runCatching { future.get() }.getOrNull() ?: return@addListener
            controller = c
            c.shuffleModeEnabled = Prefs.shuffle
            c.repeatMode = Prefs.repeat
            c.addListener(object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) = publish(player)
            })
            publish(c)
            if (pending != null) {
                val (tracks, index) = pending!!
                pending = null
                play(tracks, index)
            } else {
                restore(c)
            }
            startTicker()
        }, MoreExecutors.directExecutor())
    }

    /** Rebuild the last queue without starting audio, so the app reopens where he left it. */
    private fun restore(c: MediaController) {
        if (c.mediaItemCount > 0) return
        val ctx = appCtx ?: return
        val saved = QueueStore.load(ctx) ?: return
        if (saved.tracks.isEmpty()) return
        queue = saved.tracks
        savedIndex = saved.index
        val current = saved.tracks.getOrNull(saved.index)
        val resumeSaver = current != null && isSaver(current.id)
        seekBase = if (resumeSaver) saved.positionMs else 0L
        seekIndex = saved.index
        runCatching {
            val items = saved.tracks.map { t ->
                if (t.id == current?.id && resumeSaver) itemFor(t, saved.positionMs) else itemFor(t, 0L)
            }
            c.setMediaItems(items, saved.index, if (resumeSaver) 0L else saved.positionMs)
            publish(c)
            android.util.Log.i(
                "ezamp-perf",
                "restore ${saved.tracks.size} items at ${saved.positionMs}ms local=${one.calypsocloud.ezamp.data.Downloads.isDownloaded(saved.tracks.getOrNull(saved.index)?.id ?: "")}",
            )
        }
    }

    fun play(tracks: List<Track>, startIndex: Int) {
        val c = controller
        if (c == null) {
            pending = tracks to startIndex
            return
        }
        if (tracks.isEmpty()) return
        val first = tracks[startIndex]
        if (Prefs.downloadsOnly && !one.calypsocloud.ezamp.data.Downloads.isDownloaded(first.id)) {
            _state.value = _state.value.copy(notice = "Not downloaded — turn off OFFLINE or download it first")
            android.util.Log.i("ezamp-offline", "refused ${first.id}: downloadsOnly and not on device")
            return
        }
        _state.value = _state.value.copy(notice = null)
        android.util.Log.i(
            "ezamp-perf",
            "play ${first.container} ${first.durationMs / 1000}s mode=${Prefs.playMode} local=${one.calypsocloud.ezamp.data.Downloads.isDownloaded(first.id)}",
        )
        tapAt = SystemClock.uptimeMillis()
        firstAudioReported = false
        queue = tracks
        savedIndex = startIndex
        persist(force = true)
        seekBase = 0L
        seekIndex = startIndex
        val items = tracks.map { itemFor(it, 0L) }
        c.setMediaItems(items, startIndex.coerceIn(0, items.lastIndex), 0L)
        c.prepare()
        c.play()
    }

    fun toggle() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun toggleShuffle() {
        val c = controller ?: return
        val on = !c.shuffleModeEnabled
        c.shuffleModeEnabled = on
        Prefs.shuffle = on
    }

    /** off -> all -> one -> off. */
    fun cycleRepeat() {
        val c = controller ?: return
        val next = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        c.repeatMode = next
        Prefs.repeat = next
    }

    /** SHUFFLE on a list: shuffle mode on, start anywhere. The service puts that track first in the order. */
    fun playShuffled(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        Prefs.shuffle = true
        controller?.shuffleModeEnabled = true
        play(tracks, tracks.indices.random())
    }

    fun next() = controller?.takeIf { it.hasNextMediaItem() }?.seekToNextMediaItem()
    fun prev() = controller?.takeIf { it.hasPreviousMediaItem() }?.seekToPreviousMediaItem()

    fun stop() {
        val c = controller ?: return
        c.stop()
        c.clearMediaItems()
        queue = emptyList()
        appCtx?.let { QueueStore.clear(it) }
        _state.value = PlayState()
    }

    /** Write the queue + playhead. Cheap enough to call every few seconds; throttled by default. */
    private fun persist(force: Boolean = false) {
        val c = controller ?: return
        val ctx = appCtx ?: return
        if (queue.isEmpty()) return
        val now = SystemClock.uptimeMillis()
        val moved = c.currentMediaItemIndex != savedIndex
        if (!force && !moved && now - lastSaveAt < 5_000) return
        val idx = c.currentMediaItemIndex
        savedIndex = idx
        lastSaveAt = now
        val track = queue.getOrNull(idx)
        val base = if (track != null && isSaver(track.id)) seekBase else 0L
        QueueStore.save(ctx, queue, idx, (base + c.currentPosition).coerceAtLeast(0))
    }

    fun seekToFraction(f: Float) {
        val c = controller ?: return
        val idx = c.currentMediaItemIndex
        val track = queue.getOrNull(idx) ?: return
        val total = if (track.durationMs > 0) track.durationMs else c.duration
        if (total <= 0) return
        val target = (total * f.coerceIn(0f, 1f)).toLong()
        if (!isSaver(track.id)) {
            c.seekTo(target)
            return
        }
        // Transcode: restart ffmpeg at the target and treat that as the new zero.
        val wasPlaying = c.isPlaying
        seekBase = target
        seekIndex = idx
        c.replaceMediaItem(idx, itemFor(track, target))
        c.seekTo(0)
        if (wasPlaying) c.play()
        android.util.Log.i("ezamp-perf", "seek transcode to ${target}ms")
    }

    private fun publish(p: Player) {
        val md = p.currentMediaItem?.mediaMetadata
        val idx = p.currentMediaItemIndex
        if (idx != seekIndex) {
            seekBase = 0L
            seekIndex = idx
        }
        val track = queue.getOrNull(idx)
        val offset = if (track != null && isSaver(track.id)) seekBase else 0L
        _state.value = PlayState(
            itemId = p.currentMediaItem?.mediaId,
            title = md?.title?.toString().orEmpty(),
            artist = md?.artist?.toString().orEmpty(),
            album = md?.albumTitle?.toString().orEmpty(),
            playing = p.isPlaying,
            buffering = p.playbackState == Player.STATE_BUFFERING,
            positionMs = (offset + p.currentPosition).coerceAtLeast(0),
            // A live transcode reports no duration, so the queue's own metadata is the truth there.
            durationMs = if (track != null && isSaver(track.id) && track.durationMs > 0) {
                track.durationMs
            } else p.duration.coerceAtLeast(0),
            hasNext = p.hasNextMediaItem(),
            hasPrev = p.hasPreviousMediaItem(),
            count = p.mediaItemCount,
            artUri = md?.artworkUri?.toString(),
            transcoding = track != null && isSaver(track.id),
            index = idx,
            shuffle = p.shuffleModeEnabled,
            repeat = p.repeatMode,
        )
        if (!firstAudioReported && p.isPlaying && tapAt > 0) {
            firstAudioReported = true
            LibraryStore.noteFirstAudio(SystemClock.uptimeMillis() - tapAt)
        }
    }

    /** The position ticker only runs while a screen actually shows a progress bar. */
    fun setPositionTracking(on: Boolean) {
        positionTracking = on
        if (on) startTicker()
    }

    private fun startTicker() {
        if (tickerStarted) return
        tickerStarted = true
        scope.launch {
            while (true) {
                val c = controller
                if (positionTracking && c != null && c.isPlaying) publish(c)
                if (c != null && c.isPlaying) persist()
                delay(1000)
            }
        }
    }

    /**
     * One playback path: a downloaded track is the same MediaItem with a file:// uri. Downloads
     * therefore work offline without the player knowing that downloads exist.
     */
    /** Saver stream (no local copy) — the one case where positions are offset. */
    private fun isSaver(id: String): Boolean =
        Prefs.playMode == "saver" && one.calypsocloud.ezamp.data.Downloads.localFile(id) == null

    /** Build the item for a track, starting a transcode at [startMs] when saver mode is on. */
    private fun itemFor(t: Track, startMs: Long): MediaItem {
        if (!isSaver(t.id)) return t.toMediaItem()
        val url = LibraryStore.api()?.saverUrl(t.id, Prefs.saverKbps, startMs) ?: t.uri
        return t.copy(uri = url).toMediaItem()
    }

    private fun Track.toMediaItem(): MediaItem {
        val local = one.calypsocloud.ezamp.data.Downloads.localFile(id)
        val playUri = local?.let { android.net.Uri.fromFile(it) } ?: android.net.Uri.parse(uri)
        // A downloaded file keeps the mime it was fetched with, even if play mode has since flipped.
        val effectiveMime = when {
            local != null -> one.calypsocloud.ezamp.data.Downloads.localMime(id) ?: mime
            isSaver(id) -> "audio/mpeg"
            else -> mime
        }
        return MediaItem.Builder()
            .setMediaId(id)
            .setUri(playUri)
            .apply { effectiveMime?.let { setMimeType(it) } }
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(name)
                    .setArtist(artist)
                    .setAlbumTitle(album)
                    .apply { artUri?.let { setArtworkUri(android.net.Uri.parse(it)) } }
                    .build(),
            )
            .build()
    }
}
