package one.calypsocloud.ezamp.data

/** Who a track credits, kept free of Android so it is unit-tested. */
object ArtistCredits {

    /**
     * Every performer Jellyfin lists plus the album artist, trimmed, blanks dropped, first spelling
     * kept when two differ only by case (the tab must not show "AFX" and "Afx" as two people).
     */
    fun of(artists: List<String>, albumArtist: String?): List<String> =
        (artists + listOfNotNull(albumArtist))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
}
