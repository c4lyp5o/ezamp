package one.calypsocloud.ezamp.player

import android.content.Context
import android.media.AudioManager
import android.media.audiofx.Equalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import one.calypsocloud.ezamp.data.Prefs

/**
 * The phone's own equalizer effect on the player's audio session. The filtering runs inside the
 * system audio server, not in this process, so it costs the UI and playback threads nothing; a
 * band change is one small binder call. Everything here is called on Main.
 */
object Eq {
    const val OFF: Int = -2
    const val CUSTOM: Int = -1

    data class State(
        val available: Boolean = false,
        val presets: List<String> = emptyList(),
        /** Each preset's curve, in millibels per band, read once from the phone. */
        val presetLevels: List<List<Int>> = emptyList(),
        val freqsHz: List<Int> = emptyList(),
        val minMb: Int = -1500,
        val maxMb: Int = 1500,
        /** [OFF], [CUSTOM] or a preset index. */
        val mode: Int = OFF,
        /** The curve in use (or last used, while [OFF]), in millibels per band. */
        val levels: List<Int> = emptyList(),
    )

    private val mutable = MutableStateFlow(State(mode = Prefs.eqMode))
    val state: StateFlow<State> = mutable

    private var eq: Equalizer? = null

    private class Info(
        val presets: List<String>,
        val presetLevels: List<List<Int>>,
        val freqsHz: List<Int>,
        val minMb: Int,
        val maxMb: Int,
    )

    private fun read(e: Equalizer): Info {
        val bands = e.numberOfBands.toInt()
        val range = e.bandLevelRange
        val presets = (0 until e.numberOfPresets.toInt()).map { e.getPresetName(it.toShort()) }
        val curves = presets.indices.map { p ->
            e.usePreset(p.toShort())
            (0 until bands).map { e.getBandLevel(it.toShort()).toInt() }
        }
        return Info(presets, curves, (0 until bands).map { e.getCenterFreq(it.toShort()) / 1000 }, range[0].toInt(), range[1].toInt())
    }

    private fun adopt(info: Info) {
        val bands = info.freqsHz.size
        val mode = mutable.value.mode.takeIf { it < info.presets.size } ?: OFF
        val saved = Prefs.eqLevels.split(',').mapNotNull { it.trim().toIntOrNull() }
        val custom = if (saved.size == bands) saved.map { it.coerceIn(info.minMb, info.maxMb) } else List(bands) { 0 }
        mutable.value = State(
            available = true,
            presets = info.presets,
            presetLevels = info.presetLevels,
            freqsHz = info.freqsHz,
            minMb = info.minMb,
            maxMb = info.maxMb,
            mode = mode,
            levels = if (mode >= 0) info.presetLevels[mode] else custom,
        )
    }

    /** For the settings screen while nothing plays: read the bands from a throwaway effect. */
    suspend fun probe(ctx: Context): Unit {
        if (mutable.value.available) return
        val info = withContext(Dispatchers.IO) {
            runCatching {
                val session = ctx.getSystemService(AudioManager::class.java).generateAudioSessionId()
                val e = Equalizer(0, session)
                try { read(e) } finally { e.release() }
            }.onFailure { android.util.Log.w("ezamp-eq", "equalizer unavailable: ${it.message}") }.getOrNull()
        } ?: return
        if (!mutable.value.available) adopt(info)
    }

    /** Bind to the player's audio session; called again whenever ExoPlayer changes it. */
    internal fun attach(sessionId: Int): Unit {
        release()
        if (sessionId == 0) return
        runCatching {
            val e = Equalizer(0, sessionId)
            eq = e
            if (!mutable.value.available) adopt(read(e))
            apply()
        }.onFailure { android.util.Log.w("ezamp-eq", "equalizer not attached: ${it.message}") }
    }

    internal fun release(): Unit {
        eq?.let { runCatching { it.release() } }
        eq = null
    }

    fun select(mode: Int): Unit {
        val s = mutable.value
        if (!s.available) return
        val levels = when {
            mode >= 0 -> s.presetLevels[mode]
            mode == CUSTOM -> Prefs.eqLevels.split(',').mapNotNull { it.trim().toIntOrNull() }
                .takeIf { it.size == s.freqsHz.size } ?: s.levels
            else -> s.levels
        }
        mutable.value = s.copy(mode = mode, levels = levels)
        Prefs.eqMode = mode
        apply()
    }

    /** Live while dragging; [commit] saves when the finger lifts. Touching a band makes it custom. */
    fun setBand(band: Int, mb: Int): Unit {
        val s = mutable.value
        if (!s.available || band !in s.levels.indices) return
        val level = mb.coerceIn(s.minMb, s.maxMb)
        // A drag reports every pixel; only a new step is worth a call into the audio server.
        if (s.mode == CUSTOM && s.levels[band] == level) return
        val levels = s.levels.toMutableList().also { it[band] = level }
        val wasOn = s.mode != OFF
        mutable.value = s.copy(mode = CUSTOM, levels = levels)
        if (wasOn) {
            eq?.let { e -> runCatching { e.setBandLevel(band.toShort(), level.toShort()) } }
        } else {
            apply()
        }
    }

    fun commit(): Unit {
        val s = mutable.value
        Prefs.eqMode = s.mode
        if (s.mode == CUSTOM) Prefs.eqLevels = s.levels.joinToString(",")
    }

    private fun apply() {
        val e = eq ?: return
        val s = mutable.value
        runCatching {
            if (s.mode == OFF) {
                e.enabled = false
            } else {
                s.levels.forEachIndexed { i, mb -> e.setBandLevel(i.toShort(), mb.toShort()) }
                e.enabled = true
            }
        }.onFailure { android.util.Log.w("ezamp-eq", "equalizer apply failed: ${it.message}") }
    }
}
