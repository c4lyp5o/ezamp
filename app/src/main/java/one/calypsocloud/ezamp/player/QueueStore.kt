package one.calypsocloud.ezamp.player

import android.content.Context
import one.calypsocloud.ezamp.data.Track
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * The queue is session state, not library data: it must survive the process being killed but must
 * never be clobbered by a library sync. A small JSON file next to the database does that with one
 * moving part and no schema migration.
 */
object QueueStore {

    private const val FILE = "queue.json"
    private const val MAX_ITEMS = 2_000

    data class Saved(val tracks: List<Track>, val index: Int, val positionMs: Long)

    private fun file(ctx: Context) = File(ctx.filesDir, FILE)

    fun save(ctx: Context, tracks: List<Track>, index: Int, positionMs: Long) {
        if (tracks.isEmpty()) {
            clear(ctx)
            return
        }
        runCatching {
            val arr = JSONArray()
            tracks.take(MAX_ITEMS).forEach { t ->
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
            val root = JSONObject()
                .put("version", 1)
                .put("index", index.coerceIn(0, tracks.lastIndex))
                .put("positionMs", positionMs.coerceAtLeast(0))
                .put("tracks", arr)
            val tmp = File(ctx.filesDir, "$FILE.tmp")
            tmp.writeText(root.toString())
            tmp.renameTo(file(ctx))
        }
    }

    fun load(ctx: Context): Saved? = runCatching {
        val f = file(ctx)
        if (!f.exists()) return null
        val root = JSONObject(f.readText())
        val arr = root.getJSONArray("tracks")
        if (arr.length() == 0) return null
        val tracks = ArrayList<Track>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val container = o.optString("container").ifBlank { null }
            tracks += Track(
                id = o.getString("id"),
                name = o.optString("name"),
                album = o.optString("album"),
                albumId = o.optString("albumId").ifBlank { null },
                artist = o.optString("artist"),
                trackNo = null,
                disc = null,
                durationMs = o.optLong("durationMs"),
                imageTag = null,
                container = container,
                mime = o.optString("mime").ifBlank { null },
                uri = o.getString("uri"),
                artUri = o.optString("artUri").ifBlank { null },
                transcodeUri = "",
            )
        }
        Saved(tracks, root.optInt("index", 0), root.optLong("positionMs", 0))
    }.getOrNull()

    fun clear(ctx: Context) {
        runCatching { file(ctx).delete() }
    }
}
