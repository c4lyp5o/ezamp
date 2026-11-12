package one.calypsocloud.ezamp.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import one.calypsocloud.ezamp.db.EzampDb
import one.calypsocloud.ezamp.db.LibraryQueries
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Downloaded-only mode is SQL, so these run the real generated queries on in-memory SQLite.
 * Contract: only finished (state = 2) downloads exist in scope; queued, running and failed rows
 * are invisible, and no screen can name a track that cannot play.
 */
class ScopeQueriesTest {

    private lateinit var driver: JdbcSqliteDriver
    private lateinit var q: LibraryQueries

    private val queued = Downloads.QUEUED.toLong()
    private val running = Downloads.RUNNING.toLong()
    private val done = Downloads.DONE.toLong()
    private val failed = Downloads.FAILED.toLong()

    @Before fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        EzampDb.Schema.create(driver)
        q = EzampDb(driver).libraryQueries

        // A1: fully downloaded. A2: one of two done. A3: queued/running/failed only. A4: untouched.
        album("A1", "Alpha", "Artist One", created = "2026-01-01")
        album("A2", "Bravo", "Artist Two", created = "2026-03-01")
        album("A3", "Charlie", "Artist Three", created = "2026-02-01")
        album("A4", "Delta", "Artist One", created = "2026-04-01")
        track("t1", "A1", "one", trackNo = 1, fav = true)
        track("t2", "A1", "two", trackNo = 2)
        track("t3", "A2", "three", trackNo = 1, fav = true)
        track("t4", "A2", "four", trackNo = 2, fav = true)
        track("t5", "A3", "five", trackNo = 1)
        track("t6", "A3", "six", trackNo = 2)
        track("t7", "A3", "seven", trackNo = 3)
        track("t8", "A4", "eight", trackNo = 1, fav = true)
        dl("t1", "A1", done, 100)
        dl("t2", "A1", done, 200)
        dl("t3", "A2", done, 300)
        dl("t4", "A2", queued, 0)
        dl("t5", "A3", queued, 0)
        dl("t6", "A3", running, 50)
        dl("t7", "A3", failed, 0)
        // Credits as sync writes them: each track's album artist, plus a guest on t2 and t8.
        mapOf("A1" to "Artist One", "A2" to "Artist Two", "A3" to "Artist Three", "A4" to "Artist One")
            .let { byAlbum ->
                listOf("t1" to "A1", "t2" to "A1", "t3" to "A2", "t4" to "A2", "t5" to "A3", "t6" to "A3", "t7" to "A3", "t8" to "A4")
                    .forEach { (t, a) -> q.upsertTrackArtist(t, byAlbum.getValue(a)) }
            }
        q.upsertTrackArtist("t2", "Guest")
        q.upsertTrackArtist("t8", "Guest")
    }

    @After fun tearDown() = driver.close()

    @Test fun albumsInScopeHaveAtLeastOneFinishedTrack() {
        assertEquals(listOf("A1", "A2"), q.scopedAlbums().executeAsList().map { it.id })
    }

    @Test fun recentInScopeKeepsTheRecencyOrder() {
        assertEquals(listOf("A2", "A1"), q.scopedRecent().executeAsList().map { it.id })
    }

    @Test fun albumTracksInScopeAreOnlyFinishedOnes() {
        assertEquals(listOf("t3"), q.scopedTracksForAlbum("A2").executeAsList().map { it.id })
        assertEquals(emptyList<String>(), q.scopedTracksForAlbum("A3").executeAsList().map { it.id })
    }

    @Test fun favouritesAndSearchInScopeExcludeUnplayable() {
        assertEquals(listOf("t1", "t3"), q.scopedFavouriteTracks().executeAsList().map { it.id }.sorted())
        assertEquals(listOf("t5"), q.searchTracks("%five%").executeAsList().map { it.id })
        assertEquals(emptyList<String>(), q.scopedSearchTracks("%five%").executeAsList().map { it.id })
    }

    @Test fun trackCountInScopeCountsFinishedOnly() {
        assertEquals(3L, q.scopedTrackCount().executeAsOne())
        assertEquals(8L, q.trackCount().executeAsOne())
    }

    @Test fun artistsCountEveryCreditedNameNotJustAlbumArtists() {
        // Guest is credited on one track of A1 and one of A4 but is no album's artist.
        val rows = q.artists().executeAsList().associate { it.artist to it.albumCount }
        assertEquals(
            mapOf("Artist One" to 2L, "Artist Three" to 1L, "Artist Two" to 1L, "Guest" to 2L),
            rows,
        )
        assertEquals(listOf("A1", "A4"), q.albumsByArtist("Guest").executeAsList().map { it.id })
    }

    @Test fun artistsInScopeCountOnlyThroughFinishedTracks() {
        // Artist One: A1 done, A4 untouched -> 1. Guest: t2 done (A1), t8 not downloaded -> 1.
        // Artist Two: t3 done -> in. Artist Three: nothing finished -> out.
        val rows = q.scopedArtists().executeAsList().associate { it.artist to it.albumCount }
        assertEquals(mapOf("Artist One" to 1L, "Artist Two" to 1L, "Guest" to 1L), rows)
        assertEquals(listOf("A1"), q.scopedAlbumsByArtist("Artist One").executeAsList().map { it.id })
        assertEquals(listOf("A1"), q.scopedAlbumsByArtist("Guest").executeAsList().map { it.id })
    }

    @Test fun artistsAreOrderedIgnoringCase() {
        q.upsertTrackArtist("t1", "aaron")
        assertEquals("aaron", q.artists().executeAsList().first().artist)
    }

    @Test fun playlistTracksInScopeKeepPlaylistOrderAndDropUnplayable() {
        q.upsertPlaylist("P1", "Mix", "mix", 4, null, null, 4)
        q.upsertPlaylistTrack("P1", "t4", 0) // queued: out
        q.upsertPlaylistTrack("P1", "t3", 1)
        q.upsertPlaylistTrack("P1", "t8", 2) // not downloaded: out
        q.upsertPlaylistTrack("P1", "t1", 3)
        assertEquals(listOf("t3", "t1"), q.scopedPlaylistTracks("P1").executeAsList().map { it.id })
    }

    @Test fun storageListShowsOnlyAlbumsWithFinishedBytesBiggestFirst() {
        val rows = q.downloadsByAlbum().executeAsList().map { Triple(it.albumId, it.bytes, it.tracks) }
        assertEquals(listOf(Triple("A1", 300L, 2L), Triple("A2", 300L, 1L)), rows.sortedBy { it.first })
        assertEquals(600L, q.downloadBytesTotal().executeAsOne())
    }

    private fun album(id: String, name: String, artist: String, created: String) =
        q.upsertAlbum(id, name, name.lowercase(), artist, 2026, 0, null, created)

    private fun track(id: String, albumId: String, title: String, trackNo: Long, fav: Boolean = false) =
        q.upsertTrack(id, albumId, albumId, title, title, "x", trackNo, 1, 1000, "flac", null, if (fav) 1L else 0L, title)

    private fun dl(trackId: String, albumId: String, state: Long, bytes: Long) =
        q.upsertDownload(trackId, albumId, state, bytes, bytes, null, null, null, "audio/flac")
}
