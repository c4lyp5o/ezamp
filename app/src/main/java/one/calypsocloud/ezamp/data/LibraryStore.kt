package one.calypsocloud.ezamp.data

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import one.calypsocloud.ezamp.db.EzampDb
import one.calypsocloud.ezamp.db.LibraryAlbum
import one.calypsocloud.ezamp.db.LibraryTrack
import java.time.Instant
import kotlin.math.roundToLong

// ---------------------------------------------------------------- domain view of the library

data class Album(
    val id: String,
    val name: String,
    val artist: String,
    val year: Int?,
    val trackCount: Int,
    val imageTag: String?,
    val created: String?,
    val artUri: String,
)

data class Track(
    val id: String,
    val name: String,
    val album: String,
    val albumId: String?,
    val artist: String,
    val trackNo: Int?,
    val disc: Int?,
    val durationMs: Long,
    val imageTag: String?,
    val container: String?,
    val mime: String?,
    val uri: String,
    val artUri: String?,
    val transcodeUri: String,
    val favourite: Boolean = false,
)

val DIRECT_CONTAINERS = setOf("flac", "mp3", "m4a", "aac", "ogg", "opus", "wav", "wma")

fun mimeFor(container: String?): String? = when (container?.lowercase()) {
    "flac" -> "audio/flac"
    "mp3" -> "audio/mpeg"
    "m4a" -> "audio/mp4"
    "aac" -> "audio/aac"
    "ogg" -> "audio/ogg"
    "opus" -> "audio/opus"
    "wav" -> "audio/wav"
    "wma" -> "audio/x-ms-wma"
    else -> null
}

data class Artist(val name: String, val albumCount: Int)

data class Playlist(
    val id: String,
    val name: String,
    val trackCount: Int,
    val imageTag: String?,
    val artUri: String,
)

// ---------------------------------------------------------------- state

data class LibState(
    val ready: Boolean = false,
    val syncing: Boolean = false,
    val error: String? = null,
    val albums: List<Album> = emptyList(),
    val trackCount: Int = 0,
    val favourites: List<Track> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val recent: List<Album> = emptyList(),
    val syncMs: Long = 0,
    val lastSync: String? = null,
    // perf probes
    val coldStartMs: Long = 0,
    val searchMs: Long = 0,
    val firstAudioMs: Long = 0,
    val query: String = "",
    val results: List<Track> = emptyList(),
)

/**
 * Local-first library store. The UI only ever reads [state] (or awaits a DB query);
 * Jellyfin is a sync source and a file host, never a query API.
 *
 * The index is SQLite (SQLDelight). Tracks are NOT held in RAM — only albums (532 of them,
 * cheap) plus the current query results. Everything touching the DB runs on IO.
 */
object LibraryStore {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(LibState())
    val state: StateFlow<LibState> = _state

    private var api: JellyfinApi? = null
    private var db: EzampDb? = null
    private var searchSeq = 0L

    fun attach(ctx: Context) {
        if (db == null) {
            val driver = AndroidSqliteDriver(EzampDb.Schema, ctx.applicationContext, "ezamp.db")
            db = EzampDb(driver)
        }
    }

    private fun q() = db!!.libraryQueries

    /** For features that own their own tables (downloads); everything else goes through queries. */
    fun database(): EzampDb = db!!

    fun api(): JellyfinApi? {
        if (api == null) {
            val base = Prefs.baseUrl ?: return null
            val token = Prefs.token ?: return null
            api = JellyfinApi(base.trimEnd('/'), token)
        }
        return api
    }

    fun setApi(a: JellyfinApi) {
        api = a
    }

    fun markColdStart(ms: Long) {
        _state.value = _state.value.copy(coldStartMs = ms)
    }

    fun noteFirstAudio(ms: Long) {
        android.util.Log.i("ezamp-perf", "tap-to-audio=${ms}ms")
        _state.value = _state.value.copy(firstAudioMs = ms)
    }

    /** Paint from the DB immediately, then refresh from the server in the background. */
    fun start(userId: String) {
        scope.launch {
            refresh(null, 0)
            sync(userId)
        }
    }

    fun resync() {
        val uid = Prefs.userId ?: return
        scope.launch { sync(uid) }
    }

    // ------------------------------------------------------------ sync

    private suspend fun sync(userId: String) = withContext(Dispatchers.IO) {
        val a = api() ?: return@withContext
        _state.value = _state.value.copy(syncing = true, error = null)
        try {
            val t0 = System.nanoTime()
            // Albums and tracks are independent HTTP pulls: overlap them. Favourites come free
            // from UserData on the track rows, so there is no third pass over the library.
            val (albumsRaw, tracksRaw) = coroutineScope {
                val albums = async(Dispatchers.IO) {
                    pullAll(
                        a, userId, "MusicAlbum", limit = 1000,
                        fields = "ProductionYear,RunTimeTicks,AlbumArtist,AlbumPrimaryImageTag,ChildCount,DateCreated,SortName",
                        sortBy = "SortName",
                    )
                }
                val tracks = async(Dispatchers.IO) {
                    pullAll(
                        a, userId, "Audio", limit = 1000,
                        fields = "RunTimeTicks,AlbumId,Artists,IndexNumber,ParentIndexNumber,MediaSources,Etag,AlbumPrimaryImageTag,DateCreated,SortName",
                        sortBy = "SortName",
                    )
                }
                albums.await() to tracks.await()
            }
            val tookMs = (System.nanoTime() - t0) / 1_000_000
            val favIds = tracksRaw.filter { it.UserData?.IsFavorite == true }.mapTo(HashSet()) { it.Id }
            val counts = tracksRaw.groupingBy { it.AlbumId ?: "" }.eachCount()
            val known = tracksRaw.mapTo(HashSet()) { it.Id }

            val queries = q()
            queries.transaction {
                queries.clearTracks()
                queries.clearAlbums()
                albumsRaw.forEach { dto ->
                    queries.upsertAlbum(
                        id = dto.Id,
                        name = dto.Name,
                        sortName = dto.SortName ?: dto.Name.lowercase(),
                        artist = dto.AlbumArtist ?: dto.Artists.joinToString(", "),
                        year = dto.ProductionYear?.toLong(),
                        trackCount = (counts[dto.Id] ?: dto.ChildCount ?: 0).toLong(),
                        imageTag = dto.AlbumPrimaryImageTag,
                        created = dto.DateCreated,
                    )
                }
                tracksRaw.forEach { dto ->
                    val artist = dto.artistLine()
                    val album = dto.Album ?: ""
                    queries.upsertTrack(
                        id = dto.Id,
                        albumId = dto.AlbumId,
                        albumName = album,
                        title = dto.Name,
                        sortName = dto.SortName ?: dto.Name.lowercase(),
                        artist = artist,
                        trackNo = dto.IndexNumber?.toLong(),
                        discNo = dto.ParentIndexNumber?.toLong(),
                        durationMs = (dto.RunTimeTicks ?: 0L) / 10_000L,
                        container = dto.Container,
                        imageTag = dto.AlbumPrimaryImageTag,
                        favourite = if (favIds.contains(dto.Id)) 1L else 0L,
                        haystack = "${dto.Name} $artist $album".lowercase(),
                    )
                }
            }
            android.util.Log.i(
                "ezamp-perf",
                "sync=${tookMs}ms db albums=${queries.albumCount().executeAsOne()} tracks=${queries.trackCount().executeAsOne()}",
            )

            // Publish the library before touching playlists: browsing is usable from here on.
            val stamp = Instant.now().toString()
            Prefs.lastSync = stamp
            refresh(stamp, tookMs)

            syncPlaylists(a, userId, queries, known, t0)
        } catch (e: Exception) {
            _state.value = _state.value.copy(
                syncing = false,
                error = e.message ?: e::class.java.simpleName,
            )
        }
    }

    /**
     * Playlist membership is one request per playlist and the slowest part of a sync, so it runs
     * after the library is already browsable. Playlists whose server-side item count is unchanged
     * keep their rows and are not re-fetched.
     */
    private suspend fun syncPlaylists(
        a: JellyfinApi,
        userId: String,
        queries: one.calypsocloud.ezamp.db.LibraryQueries,
        known: Set<String>,
        t0: Long,
    ) {
        val tP = System.nanoTime()
        val playlistsRaw = try {
            pullAll(
                a, userId, "Playlist", limit = 500,
                fields = "ChildCount,PrimaryImageTag,DateCreated,SortName",
                sortBy = "SortName",
            )
        } catch (e: Exception) {
            android.util.Log.w("ezamp-perf", "playlist list failed: ${e.message}")
            return
        }
        if (playlistsRaw.isEmpty()) return

        val meta = queries.allPlaylistMeta().executeAsList().associateBy { it.id }
        val stale = playlistsRaw.filter { meta[it.Id]?.remoteCount != (it.ChildCount ?: 0).toLong() }

        val members = HashMap<String, List<ItemDto>>()
        stale.chunked(10).forEach { chunk ->
            coroutineScope {
                chunk.map { pl ->
                    async(Dispatchers.IO) {
                        pl.Id to try {
                            a.playlistItems(userId = userId, playlistId = pl.Id).Items
                        } catch (e: Exception) {
                            android.util.Log.w("ezamp-perf", "playlist ${pl.Name} failed: ${e.message}")
                            null
                        }
                    }
                }.awaitAll()
            }.forEach { (id, items) -> if (items != null) members[id] = items }
        }

        queries.transaction {
            val live = playlistsRaw.mapTo(HashSet()) { it.Id }
            meta.keys.filterNot { live.contains(it) }.forEach { gone ->
                queries.deletePlaylistTracks(gone)
                queries.deletePlaylist(gone)
            }
            playlistsRaw.forEach { pl ->
                val fetched = members[pl.Id]
                val old = meta[pl.Id]
                if (fetched != null) {
                    // Jellyfin playlists can mix video and audio; only audio entries exist in our
                    // index, so the honest count is the number of members we actually stored.
                    val rows = fetched.filter { known.contains(it.Id) }
                    queries.deletePlaylistTracks(pl.Id)
                    rows.forEachIndexed { i, t ->
                        queries.upsertPlaylistTrack(playlistId = pl.Id, trackId = t.Id, position = i.toLong())
                    }
                    queries.upsertPlaylist(
                        id = pl.Id,
                        name = pl.Name,
                        sortName = pl.SortName ?: pl.Name.lowercase(),
                        trackCount = rows.size.toLong(),
                        imageTag = pl.PrimaryImageTag,
                        created = pl.DateCreated,
                        remoteCount = pl.ChildCount?.toLong() ?: 0L,
                    )
                } else if (old != null) {
                    queries.upsertPlaylist(
                        id = pl.Id,
                        name = pl.Name,
                        sortName = pl.SortName ?: pl.Name.lowercase(),
                        trackCount = old.trackCount,
                        imageTag = pl.PrimaryImageTag,
                        created = pl.DateCreated,
                        remoteCount = old.remoteCount,
                    )
                }
            }
        }
        android.util.Log.i(
            "ezamp-perf",
            "playlists=${playlistsRaw.size} fetched=${members.size} music=${queries.allPlaylists().executeAsList().size} " +
                "phase2=${(System.nanoTime() - tP) / 1_000_000}ms total=${(System.nanoTime() - t0) / 1_000_000}ms",
        )
        refresh(Prefs.lastSync, 0)
    }

    private suspend fun pullAll(
        a: JellyfinApi,
        userId: String,
        type: String,
        limit: Int,
        fields: String,
        sortBy: String,
        filters: String? = null,
    ): List<ItemDto> {
        val out = ArrayList<ItemDto>(1024)
        var start = 0
        while (true) {
            val page = a.items(
                userId = userId, types = type, limit = limit, start = start,
                fields = fields, sortBy = sortBy, filters = filters,
            )
            out.addAll(page.Items)
            if (page.Items.size < limit) break
            start += limit
        }
        return out
    }

    /** Re-read the small aggregates from the DB. Never loads all tracks into memory. */
    private suspend fun refresh(stamp: String?, syncMs: Long) = withContext(Dispatchers.Default) {
        val a = api()
        val queries = q()
        val albums = queries.allAlbums().executeAsList().map { it.toDomain(a) }
        if (albums.isEmpty()) return@withContext
        val recent = queries.recentAlbums().executeAsList().map { it.toDomain(a) }
        val favs = queries.favouriteTracks().executeAsList().map { it.toDomain(a) }
        val lists = queries.allPlaylists().executeAsList().map { it.toDomain(a) }
        val count = queries.trackCount().executeAsOne().toInt()
        _state.value = _state.value.copy(
            ready = true,
            syncing = false,
            albums = albums,
            recent = recent,
            favourites = favs,
            playlists = lists,
            trackCount = count,
            syncMs = syncMs,
            lastSync = stamp ?: _state.value.lastSync,
            error = null,
        )
    }

    // ------------------------------------------------------------ queries

    /** Whole album, in disc/track order, straight from the index. */
    /**
     * Flip a favourite locally first (the row is the truth for the UI), then tell the server. A
     * failed server call leaves the local row ahead of Jellyfin until the next sync corrects it,
     * which is the right way round for a local-first client.
     */
    fun toggleFavourite(trackId: String, favourite: Boolean) {
        scope.launch { setFavourite(trackId, favourite) }
    }

    suspend fun setFavourite(trackId: String, favourite: Boolean) {
        withContext(Dispatchers.IO) {
            runCatching { q().setFavourite(if (favourite) 1L else 0L, trackId) }
            val a = api()
            val uid = Prefs.userId
            if (a != null && uid != null) {
                val code = runCatching { a.setFavorite(uid, trackId, favourite) }.getOrDefault(-1)
                android.util.Log.i("ezamp-fav", "$trackId favourite=$favourite http=$code")
            }
            val favs = runCatching { q().favouriteTracks().executeAsList().map { it.toDomain(a) } }
                .getOrDefault(_state.value.favourites)
            _state.value = _state.value.copy(favourites = favs)
        }
    }

    suspend fun albumTracks(albumId: String): List<Track> = withContext(Dispatchers.IO) {
        val a = api()
        q().tracksForAlbum(albumId).executeAsList().map { it.toDomain(a) }
    }

    suspend fun playlistTracks(playlistId: String): List<Track> = withContext(Dispatchers.IO) {
        val a = api()
        q().playlistTracks(playlistId).executeAsList().map { it.toDomain(a) }
    }

    suspend fun artists(): List<Artist> = withContext(Dispatchers.IO) {
        q().artists().executeAsList().map { Artist(it.artist, it.albumCount.toInt()) }
    }

    suspend fun albumsByArtist(name: String): List<Album> = withContext(Dispatchers.IO) {
        val a = api()
        q().albumsByArtist(name).executeAsList().map { it.toDomain(a) }
    }

    /** Indexed search. 5.5k rows -> single-digit milliseconds on device, no network. */
    fun search(qText: String) {
        val needle = qText.trim().lowercase()
        if (qText.isBlank()) {
            _state.value = _state.value.copy(query = "", results = emptyList(), searchMs = 0)
            return
        }
        val seq = ++searchSeq
        _state.value = _state.value.copy(query = qText)
        scope.launch {
            val t0 = System.nanoTime()
            val a = api()
            val rows = q().searchTracks("%$needle%").executeAsList()
            val ms = ((System.nanoTime() - t0) / 1_000_000.0).roundToLong()
            android.util.Log.i("ezamp-perf", "search=${ms}ms hits=${rows.size} for \"$needle\"")
            if (seq != searchSeq) return@launch // a newer keystroke already won
            _state.value = _state.value.copy(
                query = qText,
                results = rows.map { it.toDomain(a) },
                searchMs = ms,
            )
        }
    }

    fun signOut() {
        Prefs.reset()
        api = null
        scope.launch {
            runCatching {
                val queries = q()
                queries.transaction {
                    queries.clearTracks()
                    queries.clearAlbums()
                }
            }
        }
        _state.value = LibState()
    }

    // ------------------------------------------------------------ mapping

    private fun ItemDto.artistLine(): String = when {
        !AlbumArtist.isNullOrBlank() -> AlbumArtist!!
        Artists.isNotEmpty() -> Artists.joinToString(", ")
        !Album.isNullOrBlank() -> Album!!
        else -> ""
    }

    private fun LibraryAlbum.toDomain(a: JellyfinApi?): Album = Album(
        id = id,
        name = name,
        artist = artist,
        year = year?.toInt(),
        trackCount = trackCount.toInt(),
        imageTag = imageTag,
        created = created,
        artUri = a?.imageUrl(id, imageTag) ?: "",
    )

    private fun one.calypsocloud.ezamp.db.LibraryPlaylist.toDomain(a: JellyfinApi?): Playlist = Playlist(
        id = id,
        name = name,
        trackCount = trackCount.toInt(),
        imageTag = imageTag,
        artUri = a?.imageUrl(id, imageTag) ?: "",
    )

    private fun LibraryTrack.toDomain(a: JellyfinApi?): Track {
        val c = container?.lowercase()
        val direct = c == null || c in DIRECT_CONTAINERS
        return Track(
            id = id,
            name = title,
            album = albumName,
            albumId = albumId,
            artist = artist,
            trackNo = trackNo?.toInt(),
            disc = discNo?.toInt(),
            durationMs = durationMs,
            imageTag = imageTag,
            container = container,
            mime = mimeFor(container),
            uri = if (direct) (a?.streamUrl(id, container) ?: "") else (a?.transcodeUrl(id) ?: ""),
            artUri = a?.imageUrl(albumId ?: id, imageTag),
            transcodeUri = a?.transcodeUrl(id) ?: "",
            favourite = favourite != 0L,
        )
    }
}
