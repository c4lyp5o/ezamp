package one.calypsocloud.ezamp.player

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import one.calypsocloud.ezamp.data.Track
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * The queue is session state, not library data: it must survive the process being killed but must
 * never be clobbered by a library sync. Small JSON files next to the database do that with no
 * schema migration.
 *
 * A queue can hold 2,000 tracks (about a megabyte of JSON), so the track list is written only when
 * the queue itself changes; the 5 s playhead tick rewrites a tiny state file. All writes run on one
 * IO coroutine that keeps only the newest pending snapshot, never on the main thread. The state
 * file carries the queue id it belongs to; a mismatch (crash between writes) falls back to the
 * start of the queue instead of a wrong index.
 */
object QueueStore {

    private const val FILE = "queue.json"
    private const val STATE = "queue-state.json"
    private const val MAX_ITEMS = 2_000

    data class Saved(val tracks: List<Track>, val index: Int, val positionMs: Long)

    private class Write(val context: Context, val tracks: List<Track>, val index: Int, val position: Long)
    private val writes = Channel<Write>(Channel.CONFLATED)
    private val writer = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Owned by the writer coroutine; load() seeds them so a restored queue is not rewritten at once.
    @Volatile private var writtenTracks: List<Track>? = null
    @Volatile private var writtenId: String = ""

    init {
        writer.launch {
            for (w in writes) save(w.context, w.tracks, w.index, w.position)
        }
    }

    /** Queue a snapshot from any thread. An empty track list clears the saved queue, in order. */
    fun enqueue(ctx: Context, tracks: List<Track>, index: Int, positionMs: Long): Unit {
        writes.trySend(Write(ctx.applicationContext, tracks, index, positionMs))
    }

    private fun file(ctx: Context, name: String) = File(ctx.filesDir, name)

    private fun atomicWrite(ctx: Context, name: String, text: String) {
        val tmp = File(ctx.filesDir, "$name.tmp")
        tmp.writeText(text)
        check(tmp.renameTo(file(ctx, name)))
    }

    private fun save(ctx: Context, tracks: List<Track>, index: Int, positionMs: Long) {
        if (tracks.isEmpty()) {
            clear(ctx)
            return
        }
        val kept = tracks.take(MAX_ITEMS)
        runCatching {
            if (tracks !== writtenTracks) {
                val id = UUID.randomUUID().toString()
                val arr = JSONArray()
                kept.forEach { t ->
                    arr.put(
                        JSONObject()
                            .put("id", t.id)
                            .put("name", t.name)
                            .put("album", t.album)
                            .put("albumId", t.albumId ?: JSONObject.NULL)
                            .put("artist", t.artist)
                            .put("durationMs", t.durationMs)
                            .put("container", t.container ?: JSONObject.NULL)
                            .put("mime", t.mime ?: JSONObject.NULL)
                            .put("uri", t.uri)
                            .put("artUri", t.artUri ?: JSONObject.NULL)
                    )
                }
                atomicWrite(ctx, FILE, JSONObject().put("version", 2).put("queueId", id).put("tracks", arr).toString())
                writtenTracks = tracks
                writtenId = id
            }
            atomicWrite(ctx, STATE, JSONObject()
                .put("queueId", writtenId)
                .put("index", index.coerceIn(0, kept.lastIndex))
                .put("positionMs", positionMs.coerceAtLeast(0))
                .toString())
        }.onFailure {
            // Force a full rewrite next time; a half-written pair must not be trusted.
            writtenTracks = null
            android.util.Log.w("ezamp-queue", "Queue save failed (${it.javaClass.simpleName})")
        }
    }

    /** Blocking file read: call from Dispatchers.IO. */
    fun load(ctx: Context): Saved? = runCatching {
        // Left behind by the shelved radio builds; nothing reads them any more.
        file(ctx, "queue-rotation.json").takeIf { it.exists() }?.delete()
        file(ctx, "radio").takeIf { it.exists() }?.deleteRecursively()
        val f = file(ctx, FILE)
        if (!f.exists()) return null
        val root = JSONObject(f.readText())
        val arr = root.getJSONArray("tracks")
        if (arr.length() == 0) return null
        val tracks = ArrayList<Track>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            tracks += Track(
                id = o.getString("id"),
                name = o.optString("name"),
                album = o.optString("album"),
                albumId = o.nullableString("albumId"),
                artist = o.optString("artist"),
                trackNo = null,
                disc = null,
                durationMs = o.optLong("durationMs"),
                imageTag = null,
                container = o.nullableString("container"),
                mime = o.nullableString("mime"),
                uri = o.getString("uri"),
                artUri = o.nullableString("artUri"),
                transcodeUri = "",
            )
        }
        val id = root.optString("queueId")
        // Version 1 kept the playhead in queue.json; version 2 keeps it in the state file.
        val state = if (id.isEmpty()) root else stateFor(ctx, id) ?: JSONObject()
        if (id.isNotEmpty()) {
            writtenTracks = tracks
            writtenId = id
        }
        Saved(tracks, state.optInt("index", 0).coerceIn(0, tracks.lastIndex), state.optLong("positionMs", 0).coerceAtLeast(0))
    }.onFailure { android.util.Log.w("ezamp-queue", "Queue restore failed (${it.javaClass.simpleName})") }.getOrNull()

    private fun stateFor(ctx: Context, id: String): JSONObject? {
        val f = file(ctx, STATE)
        if (!f.exists()) return null
        return JSONObject(f.readText()).takeIf { it.optString("queueId") == id }
    }

    private fun JSONObject.nullableString(key: String): String? = if (isNull(key)) null else optString(key).ifBlank { null }

    private fun clear(ctx: Context) {
        writtenTracks = null
        writtenId = ""
        listOf(FILE, STATE).forEach { name -> runCatching { file(ctx, name).delete() } }
    }
}
