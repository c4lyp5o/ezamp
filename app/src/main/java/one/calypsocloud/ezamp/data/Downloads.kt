package one.calypsocloud.ezamp.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Offline copies of tracks, stored as plain files in `filesDir/downloads`.
 *
 * Two rules keep this simple:
 *  - the database is the source of truth for *what* is downloaded (state + path), the file being
 *    present is what makes it playable;
 *  - playback never learns about downloads. `PlayerHolder` asks for a local file and falls back to
 *    the stream, so there is one playback path, not two.
 */
object Downloads {

    const val QUEUED = 0
    const val RUNNING = 1
    const val DONE = 2
    const val FAILED = 3

    data class Row(
        val trackId: String,
        val albumId: String?,
        val state: Int,
        val bytes: Long,
        val totalBytes: Long,
        val path: String?,
        val error: String?,
        val mime: String? = null,
    ) {
        val fraction: Float
            get() = when {
                state == DONE -> 1f
                totalBytes > 0 -> (bytes.toFloat() / totalBytes).coerceIn(0f, 1f)
                else -> 0f
            }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<Map<String, Row>>(emptyMap())
    val state: StateFlow<Map<String, Row>> = _state

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    private val _used = MutableStateFlow(0L)

    /**
     * Committed bytes on disk, kept in step with [state] rather than re-queried. The SET sheet and
     * the cap both read this, so they can never disagree with the rows they are describing.
     */
    val used: StateFlow<Long> = _used

    private val capStop = HashSet<String>()

    fun capBytes(): Long = StoragePolicy.capBytes(Prefs.storageCapMb)

    /** No room to start another album. The UI says so instead of queueing work that cannot land. */
    fun atCap(): Boolean = StoragePolicy.atCap(_used.value, Prefs.storageCapMb)

    private fun publish(map: Map<String, Row>) {
        _state.value = map
        _used.value = StoragePolicy.used(map.values.filter { it.state == DONE }.map { it.bytes })
    }

    /** Two at a time: enough to saturate a LAN without drowning a spinning disk on the server. */
    private val permits = Semaphore(2)
    private var cancelled = HashSet<String>()

    private fun dir(ctx: Context): File = File(ctx.filesDir, "downloads").apply { mkdirs() }

    fun init(ctx: Context) {
        val stored = runCatching {
            LibraryStore.database().libraryQueries.allDownloads().executeAsList()
                .associate { it.trackId to it.toRow() }
        }.getOrDefault(emptyMap())
        // A row claiming DONE whose file vanished (cleared data, uninstall leftovers) is not done.
        publish(stored.mapValues { (_, r) ->
            if (r.state == DONE && (r.path == null || !File(r.path).exists())) {
                r.copy(state = FAILED, error = "file missing")
            } else r
        })
    }

    fun localFile(trackId: String): File? {
        val row = _state.value[trackId] ?: return null
        if (row.state != DONE) return null
        val f = row.path?.let { File(it) } ?: return null
        return if (f.exists() && f.length() > 0) f else null
    }

    /** Mime of the stored copy, which can differ from the track's current stream mime. */
    fun localMime(trackId: String): String? = _state.value[trackId]?.takeIf { it.state == DONE }?.mime

    fun isDownloaded(trackId: String): Boolean = localFile(trackId) != null

    /** done/total for an album, downloaded meaning state == DONE. */
    fun albumProgress(albumId: String): Pair<Int, Int> {
        val rows = _state.value.values.filter { it.albumId == albumId }
        return rows.count { it.state == DONE } to rows.size
    }

    fun albumBusy(albumId: String): Boolean =
        _state.value.values.any { it.albumId == albumId && (it.state == QUEUED || it.state == RUNNING) }

    suspend fun totalBytes(): Long = withContext(Dispatchers.IO) {
        runCatching { LibraryStore.database().libraryQueries.downloadBytesTotal().executeAsOne() }.getOrDefault(0L)
    }

    /** What the storage list shows: one row per album that has something finished on disk. */
    data class AlbumUsage(val albumId: String, val name: String, val bytes: Long, val tracks: Int)

    suspend fun albumUsage(): List<AlbumUsage> = withContext(Dispatchers.IO) {
        runCatching {
            LibraryStore.database().libraryQueries.downloadsByAlbum().executeAsList().map { r ->
                AlbumUsage(
                    albumId = r.albumId ?: "",
                    name = r.name ?: "",
                    bytes = r.bytes ?: 0L,
                    tracks = (r.tracks ?: 0L).toInt(),
                )
            }
        }.getOrDefault(emptyList())
    }

    fun downloadAlbum(ctx: Context, albumId: String, tracks: List<Track>) {
        if (tracks.isEmpty()) return
        if (atCap()) {
            // Refuse at the door: the UI shows CAP REACHED, this is the belt to that braces.
            android.util.Log.w("ezamp-dl", "cap reached (${_used.value} B) — refusing $albumId")
            return
        }
        cancelled.remove(albumId)
        capStop.remove(albumId)
        val pending = tracks.filter { isDownloaded(it.id).not() }
        if (pending.isEmpty()) return
        val base = _state.value.toMutableMap()
        pending.forEach { t ->
            val m = if (Prefs.playMode == "saver") "audio/mpeg" else t.mime
            base[t.id] = Row(t.id, albumId, QUEUED, 0, 0, null, null, m)
        }
        publish(base)
        persist(ctx, pending.map { base.getValue(it.id) })

        scope.launch {
            _busy.value = true
            try {
                pending.forEach { track ->
                    launch {
                        permits.withPermit { fetchOne(ctx, track) }
                    }
                }
            } finally {
                _busy.value = false
            }
        }
    }

    fun cancelAlbum(ctx: Context, albumId: String) {
        cancelled.add(albumId)
        val rows = _state.value.values.filter { it.albumId == albumId && it.state == QUEUED }
        if (rows.isEmpty()) return
        rows.forEach { row -> deleteFilesFor(ctx, row.trackId) }
        val dead = rows.map { it.copy(state = FAILED, error = "cancelled") }
        publish(_state.value + dead.associate { it.trackId to it })
        persist(ctx, dead)
    }

    fun deleteAlbum(ctx: Context, albumId: String) {
        val rows = _state.value.values.filter { it.albumId == albumId }
        // Drop the rows first: the storage figure is derived from them, so it must fall at the same
        // moment as the files rather than whenever the DB write gets scheduled.
        publish(_state.value - rows.map { it.trackId }.toSet())
        rows.forEach { row -> row.path?.let { File(it).delete() }; deleteFilesFor(ctx, row.trackId) }
        scope.launch {
            runCatching { LibraryStore.database().libraryQueries.deleteDownloadsForAlbum(albumId) }
        }
    }

    fun deleteAll(ctx: Context) {
        publish(emptyMap())
        dir(ctx).listFiles()?.forEach { it.delete() }
        scope.launch {
            runCatching { LibraryStore.database().libraryQueries.deleteAllDownloads() }
        }
    }

    /**
     * The cap can be crossed inside an album, so the check runs per finished track. What is left
     * queued is failed with a reason rather than silently dropping — a stopped queue that says why
     * is the only version he can act on.
     */
    private fun stopForCap(ctx: Context, albumId: String?) {
        if (albumId == null || !capStop.add(albumId)) return
        cancelled.add(albumId)
        val queued = _state.value.values.filter { it.albumId == albumId && it.state == QUEUED }
        if (queued.isEmpty()) return
        val dead = queued.map { it.copy(state = FAILED, error = "storage cap reached") }
        publish(_state.value + dead.associate { it.trackId to it })
        persist(ctx, dead)
        android.util.Log.w("ezamp-dl", "cap reached at ${_used.value} B — stopped ${dead.size} of $albumId")
    }

    private suspend fun fetchOne(ctx: Context, track: Track) {
        val albumId = _state.value[track.id]?.albumId
        if (albumId != null && cancelled.contains(albumId)) return
        val token = Prefs.token ?: return fail(ctx, track, "not signed in")
        val saver = Prefs.playMode == "saver"
        val mime = if (saver) "audio/mpeg" else track.mime
        val url = if (saver) (LibraryStore.api()?.saverUrl(track.id, Prefs.saverKbps) ?: track.uri) else track.uri
        val dest = File(dir(ctx), fileName(track.id, mime))
        val part = File(dir(ctx), "${fileName(track.id, mime)}.part")
        update(ctx, track, RUNNING, 0, track.durationMs, null, "", mime)

        try {
            withContext(Dispatchers.IO) {
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    setRequestProperty("Authorization", "MediaBrowser Token=\"$token\"")
                    connectTimeout = 15_000
                    readTimeout = 30_000
                }
                if (conn.responseCode !in 200..299) {
                    conn.disconnect()
                    throw IllegalStateException("HTTP ${conn.responseCode}")
                }
                val total = conn.contentLengthLong
                conn.inputStream.use { input ->
                    part.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        var written = 0L
                        var lastPublish = 0L
                        while (true) {
                            if (albumId != null && cancelled.contains(albumId)) {
                                throw java.io.IOException("cancelled")
                            }
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            written += n
                            if (written - lastPublish > 512 * 1024) {
                                lastPublish = written
                                val id = track.id
                                val bytes = written
                                publish(_state.value + (id to
                                    (_state.value[id] ?: Row(id, albumId, RUNNING, 0, total, null, null, mime))
                                        .copy(state = RUNNING, bytes = bytes, totalBytes = total)))
                            }
                        }
                        out.flush()
                    }
                }
                conn.disconnect()
            }
            part.renameTo(dest)
            update(ctx, track, DONE, dest.length(), dest.length(), dest.absolutePath, null, mime)
        } catch (e: Exception) {
            part.delete()
            val why = if (e.message == "cancelled") "cancelled" else (e.message ?: e::class.java.simpleName)
            update(ctx, track, FAILED, 0, 0, null, why, mime)
        }
    }

    private fun fail(ctx: Context, track: Track, why: String) =
        update(ctx, track, FAILED, 0, 0, null, why, track.mime)

    private fun update(
        ctx: Context,
        track: Track,
        state: Int,
        bytes: Long,
        total: Long,
        path: String?,
        error: String?,
        mime: String?,
    ) {
        val albumId = _state.value[track.id]?.albumId
        val row = Row(track.id, albumId, state, bytes, total, path, error, mime)
        publish(_state.value + (track.id to row))
        persist(ctx, listOf(row))
        if (state == FAILED) android.util.Log.w("ezamp-dl", "${track.name} failed: $error")
        if (state == DONE) {
            android.util.Log.i("ezamp-dl", "${track.name} saved ${bytes / 1024}KB $mime")
            if (StoragePolicy.overCap(_used.value, Prefs.storageCapMb)) stopForCap(ctx, albumId)
        }
    }

    private fun persist(ctx: Context, rows: List<Row>) {
        val db = runCatching { LibraryStore.database() }.getOrNull() ?: return
        val stamp = java.time.Instant.now().toString()
        runCatching {
            db.libraryQueries.transaction {
                rows.forEach { r ->
                    db.libraryQueries.upsertDownload(
                        trackId = r.trackId,
                        albumId = r.albumId,
                        state = r.state.toLong(),
                        bytes = r.bytes,
                        totalBytes = r.totalBytes,
                        path = r.path,
                        error = r.error,
                        updatedAt = stamp,
                        mime = r.mime,
                    )
                }
            }
        }
    }

    /** ExoPlayer sniffs by extension; a file called `<id>` with no suffix is a guess, so keep one. */
    private fun fileName(trackId: String, mime: String?): String {
        val safe = trackId.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return if (mime == null) safe else "$safe.${extFor(mime)}"
    }

    private fun extFor(mime: String): String = when (mime) {
        "audio/mpeg" -> "mp3"
        "audio/flac" -> "flac"
        "audio/mp4" -> "m4a"
        "audio/aac" -> "aac"
        "audio/ogg" -> "ogg"
        "audio/opus" -> "opus"
        "audio/wav" -> "wav"
        else -> "bin"
    }

    private fun deleteFilesFor(ctx: Context, trackId: String) {
        val safe = trackId.replace(Regex("[^A-Za-z0-9._-]"), "_")
        dir(ctx).listFiles()?.forEach { f ->
            if (f.name == safe || f.name.startsWith("$safe.")) f.delete()
        }
    }

    private fun one.calypsocloud.ezamp.db.LibraryDownload.toRow() = Row(
        trackId = trackId,
        albumId = albumId,
        state = state.toInt(),
        bytes = bytes,
        totalBytes = totalBytes,
        path = path,
        error = error,
        mime = mime,
    )
}
