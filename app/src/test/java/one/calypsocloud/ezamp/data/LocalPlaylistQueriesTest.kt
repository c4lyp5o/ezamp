package one.calypsocloud.ezamp.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import one.calypsocloud.ezamp.db.EzampDb
import one.calypsocloud.ezamp.db.LibraryQueries
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Local playlists run the real generated queries on in-memory SQLite. Contract: append order is
 * kept, a track is in a playlist once, an empty playlist still lists (with no cover), other owners
 * never see it, a library sync's clears leave it alone, and downloaded-only scopes its tracks.
 */
class LocalPlaylistQueriesTest {

    private lateinit var driver: JdbcSqliteDriver
    private lateinit var q: LibraryQueries
    private val me = "http://server|user"

    @Before fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        EzampDb.Schema.create(driver)
        q = EzampDb(driver).libraryQueries
        q.upsertAlbum("A1", "Alpha", "alpha", "x", 2026, 0, "tagA", null)
        listOf("t1", "t2", "t3").forEach { id ->
            q.upsertTrack(id, "A1", "Alpha", id, id, "x", 1, 1, 1000, "flac", "tagA", 0L, id)
        }
    }

    @After fun tearDown() = driver.close()

    @Test fun appendsInOrderAndOnlyOnce() {
        q.createLocalPlaylist("local-1", me, "Mix", 1)
        q.addLocalPlaylistTrack("local-1", "t2")
        q.addLocalPlaylistTrack("local-1", "t1")
        q.addLocalPlaylistTrack("local-1", "t2")
        assertEquals(listOf("t2", "t1"), q.localPlaylistTracks("local-1").executeAsList().map { it.id })
        val row = q.localPlaylists(me).executeAsOne()
        assertEquals(2L, row.trackCount)
        assertEquals("A1", row.coverId)
    }

    @Test fun emptyPlaylistListsWithoutCover() {
        q.createLocalPlaylist("local-1", me, "Empty", 1)
        val row = q.localPlaylists(me).executeAsOne()
        assertEquals(0L, row.trackCount)
        assertNull(row.coverId)
        assertNull(row.coverTag)
    }

    @Test fun ownerScopesListsAndMembership() {
        q.createLocalPlaylist("local-1", me, "Mine", 1)
        q.createLocalPlaylist("local-2", "http://other|user", "Theirs", 2)
        q.addLocalPlaylistTrack("local-1", "t1")
        q.addLocalPlaylistTrack("local-2", "t1")
        assertEquals(listOf("Mine"), q.localPlaylists(me).executeAsList().map { it.name })
        assertEquals(listOf("local-1"), q.localPlaylistsWithTrack("t1", me).executeAsList())
    }

    @Test fun newestEditFirst() {
        q.createLocalPlaylist("local-1", me, "Old", 1)
        q.createLocalPlaylist("local-2", me, "New", 2)
        q.touchLocalPlaylist(3, "local-1")
        assertEquals(listOf("Old", "New"), q.localPlaylists(me).executeAsList().map { it.name })
    }

    @Test fun removeAndDelete() {
        q.createLocalPlaylist("local-1", me, "Mix", 1)
        q.addLocalPlaylistTrack("local-1", "t1")
        q.addLocalPlaylistTrack("local-1", "t2")
        q.removeLocalPlaylistTrack("local-1", "t1")
        assertEquals(listOf("t2"), q.localPlaylistTracks("local-1").executeAsList().map { it.id })
        q.deleteLocalPlaylistTracks("local-1")
        q.deleteLocalPlaylist("local-1")
        assertEquals(0, q.localPlaylists(me).executeAsList().size)
    }

    @Test fun librarySyncClearsLeaveLocalPlaylistsAlone() {
        q.createLocalPlaylist("local-1", me, "Mix", 1)
        q.addLocalPlaylistTrack("local-1", "t1")
        q.clearPlaylists()
        q.clearPlaylistTracks()
        q.clearTracks()
        // Tracks come back with the same ids on the next sync; membership was never touched.
        q.upsertTrack("t1", "A1", "Alpha", "t1", "t1", "x", 1, 1, 1000, "flac", "tagA", 0L, "t1")
        assertEquals(listOf("t1"), q.localPlaylistTracks("local-1").executeAsList().map { it.id })
    }

    @Test fun downloadedOnlyScopesTracks() {
        q.createLocalPlaylist("local-1", me, "Mix", 1)
        q.addLocalPlaylistTrack("local-1", "t1")
        q.addLocalPlaylistTrack("local-1", "t2")
        q.upsertDownload("t2", "A1", Downloads.DONE.toLong(), 10, 10, null, null, null, "audio/flac")
        q.upsertDownload("t1", "A1", Downloads.QUEUED.toLong(), 0, 0, null, null, null, "audio/flac")
        assertEquals(listOf("t2"), q.scopedLocalPlaylistTracks("local-1").executeAsList().map { it.id })
    }
}
