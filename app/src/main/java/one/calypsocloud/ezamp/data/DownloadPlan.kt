package one.calypsocloud.ezamp.data

/**
 * Which tracks a download request actually queues, kept free of Android so it is unit-tested.
 *
 * A batch is one user request: an album (`batch = albumId`) or a playlist (`batch = "pl:<id>"`).
 * Rows always carry the track's *real* album, never the batch, so album badges, the storage list
 * and downloaded-only mode see playlist downloads exactly like album downloads. Cancel and the cap
 * stop act on the batch, so stopping a playlist never touches an album download running alongside.
 */
object DownloadPlan {

    fun playlistBatch(playlistId: String) = "pl:$playlistId"

    /**
     * Rows to queue for [tracks]. Skips anything finished or already in flight, so the same track
     * requested from an album and a playlist is fetched once. [fallbackAlbumId] covers tracks the
     * index has no album for (album downloads pass their own id).
     */
    fun queue(
        tracks: List<Track>,
        existing: Map<String, Downloads.Row>,
        batch: String,
        saver: Boolean,
        fallbackAlbumId: String? = null,
    ): List<Downloads.Row> = tracks
        .distinctBy { it.id }
        .filter { existing[it.id]?.state !in setOf(Downloads.DONE, Downloads.QUEUED, Downloads.RUNNING) }
        .map { t ->
            Downloads.Row(
                trackId = t.id,
                albumId = t.albumId ?: fallbackAlbumId,
                state = Downloads.QUEUED,
                bytes = 0,
                totalBytes = 0,
                path = null,
                error = null,
                mime = if (saver) "audio/mpeg" else t.mime,
                batch = batch,
            )
        }

    /** done/total over a set of tracks, for the playlist badge. */
    fun progress(trackIds: Collection<String>, rows: Map<String, Downloads.Row>): Pair<Int, Int> =
        trackIds.count { rows[it]?.state == Downloads.DONE } to trackIds.size
}
