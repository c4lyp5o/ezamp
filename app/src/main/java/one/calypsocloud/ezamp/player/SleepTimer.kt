package one.calypsocloud.ezamp.player

import android.os.SystemClock
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Stop playback after a while. Lives with the playback service's ExoPlayer, so it keeps counting
 * with the screen off or the activity gone. Everything runs on Main, the player's thread.
 */
object SleepTimer {
    /** [endsAtMs] is on the elapsedRealtime clock (0 = no countdown). */
    data class State(val endsAtMs: Long = 0L, val endOfTrack: Boolean = false) {
        val active: Boolean get() = endsAtMs > 0L || endOfTrack
    }

    private const val FADE_MS = 10_000L
    private const val TICK_MS = 500L

    private val mutable = MutableStateFlow(State())
    val state: StateFlow<State> = mutable

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var player: ExoPlayer? = null
    private var countdown: Job? = null
    private var fadeFrom: Float? = null

    private val listener = object : Player.Listener {
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            // ExoPlayer paused itself at the end of the track: the end-of-track timer has fired.
            if (!playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM && mutable.value.endOfTrack) {
                android.util.Log.i("ezamp-sleep", "paused at end of track")
                cancel()
            }
        }
    }

    internal fun attach(exo: ExoPlayer): Unit {
        player = exo
        exo.addListener(listener)
    }

    internal fun detach(exo: ExoPlayer): Unit {
        cancel()
        exo.removeListener(listener)
        if (player === exo) player = null
    }

    /** Pause after [minutes], fading out over the last ten seconds. */
    fun start(minutes: Int): Unit {
        cancel()
        val total = minutes * 60_000L
        mutable.value = State(endsAtMs = SystemClock.elapsedRealtime() + total)
        val deadline = mutable.value.endsAtMs
        countdown = scope.launch {
            // Handler delays use uptime, which excludes deep sleep. Keep waits short and derive
            // both the fade and expiry from elapsed time, including after the device resumes.
            while (true) {
                val remaining = (deadline - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
                if (remaining == 0L) break
                if (remaining <= FADE_MS) {
                    val p = player ?: return@launch cancel()
                    val from = fadeFrom ?: p.volume.also { fadeFrom = it }
                    p.volume = from * (remaining.toFloat() / FADE_MS)
                }
                delay(minOf(remaining, TICK_MS))
            }
            val p = player ?: return@launch cancel()
            p.pause()
            android.util.Log.i("ezamp-sleep", "paused after $minutes min")
            cancel()
        }
    }

    /** Pause when the current track ends, without starting the next one. */
    fun endOfTrack(): Unit {
        cancel()
        player?.pauseAtEndOfMediaItems = true
        mutable.value = State(endOfTrack = true)
    }

    /** Turn the timer off; a fade in progress gets its volume back at once. */
    fun cancel(): Unit {
        countdown?.cancel()
        countdown = null
        fadeFrom?.let { player?.volume = it }
        fadeFrom = null
        player?.pauseAtEndOfMediaItems = false
        mutable.value = State()
    }
}
