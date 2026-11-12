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

    @Test fun artistsInScopeCountOnlyAlbumsWithSomethingFinished() {
        // Artist One has A1 (done) and A4 (untouched): one album in scope, not two.
        val rows = q.scopedArtists().executeAsList().associate { it.artist to it.albumCount }
        assertEquals(mapOf("Artist One" to 1L, "Artist Two" to 1L), rows)
        assertEquals(listOf("A1"), q.scopedAlbumsByArtist("Artist One").executeAsList().map { it.id })
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
