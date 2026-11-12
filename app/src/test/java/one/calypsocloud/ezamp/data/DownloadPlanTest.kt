package one.calypsocloud.ezamp.data

import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadPlanTest {

    private fun track(id: String, albumId: String?, mime: String = "audio/flac") = Track(
        id = id, name = id, album = "", albumId = albumId, artist = "", trackNo = null, disc = null,
        durationMs = 0, imageTag = null, container = "flac", mime = mime, uri = "", artUri = null,
        transcodeUri = "",
    )

    private fun row(id: String, state: Int) = Downloads.Row(id, "A", state, 0, 0, null, null)

    @Test fun playlistRowsKeepTheirRealAlbumNotThePlaylist() {
        val rows = DownloadPlan.queue(
            listOf(track("t1", "A1"), track("t2", "A2")), emptyMap(), DownloadPlan.playlistBatch("P"), saver = false,
        )
        assertEquals(listOf("A1", "A2"), rows.map { it.albumId })
        assertEquals(listOf("pl:P", "pl:P"), rows.map { it.batch })
        assertEquals(listOf("pl:P", "pl:P"), rows.map { it.batchKey })
    }

    @Test fun finishedAndInFlightTracksAreNotQueuedTwice() {
        val existing = mapOf(
            "done" to row("done", Downloads.DONE),
            "queued" to row("queued", Downloads.QUEUED),
            "running" to row("running", Downloads.RUNNING),
            "failed" to row("failed", Downloads.FAILED),
        )
        val tracks = listOf("done", "queued", "running", "failed", "new").map { track(it, "A") }
        val rows = DownloadPlan.queue(tracks, existing, "A", saver = false)
        assertEquals(listOf("failed", "new"), rows.map { it.trackId })
    }

    @Test fun duplicateTracksInOnePlaylistQueueOnce() {
        val rows = DownloadPlan.queue(listOf(track("t1", "A"), track("t1", "A")), emptyMap(), "pl:P", saver = false)
        assertEquals(listOf("t1"), rows.map { it.trackId })
    }

    @Test fun albumDownloadsFallBackToTheirOwnAlbumId() {
        val rows = DownloadPlan.queue(listOf(track("t1", null)), emptyMap(), "A9", saver = false, fallbackAlbumId = "A9")
        assertEquals("A9", rows.single().albumId)
    }

    @Test fun saverDownloadsAreStoredAsMp3() {
        assertEquals("audio/mpeg", DownloadPlan.queue(listOf(track("t1", "A")), emptyMap(), "A", saver = true).single().mime)
        assertEquals("audio/flac", DownloadPlan.queue(listOf(track("t1", "A")), emptyMap(), "A", saver = false).single().mime)
    }

    @Test fun rowsRestoredFromDiskFallBackToAlbumForCancel() {
        // batch is in memory only; after a restart cancel must still find the row by album.
        assertEquals("A", row("t", Downloads.QUEUED).batchKey)
    }

    @Test fun progressCountsFinishedOverTheGivenTracks() {
        val rows = mapOf("a" to row("a", Downloads.DONE), "b" to row("b", Downloads.FAILED))
        assertEquals(1 to 3, DownloadPlan.progress(listOf("a", "b", "c"), rows))
    }
}
