package one.calypsocloud.ezamp.data

/** A track's genres, kept free of Android so it is unit-tested. */
object GenreTags {

    /**
     * Jellyfin's Genres list, with any entry still holding several genres split on ";" (this
     * server returned "Acid Techno; Ambient Techno; Idm" as one genre). Only ";" splits: "," and
     * "/" sit inside real names such as "Alt. Pop/Rock". Trimmed, blanks dropped, first spelling
     * kept when two differ only by case.
     */
    fun of(genres: List<String>): List<String> =
        genres
            .flatMap { it.split(';') }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
}
