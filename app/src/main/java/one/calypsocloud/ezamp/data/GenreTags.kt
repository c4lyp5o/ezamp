package one.calypsocloud.ezamp.data

/** Genre strings as Jellyfin hands them over, cleaned into what the GENRES page should show. */
object GenreTags {

    /**
     * Jellyfin's Genres list, with any entry still holding several genres split on ";" (this
     * server returned "Acid Techno; Ambient Techno; Idm" as one genre). Only ";" splits: "," and
     * "/" sit inside real names such as "Alt. Pop/Rock". Numeric ID3 codes become names (see
     * [name]). Trimmed, blanks dropped, first spelling kept when two differ only by case.
     */
    fun of(genres: List<String>): List<String> =
        genres
            .flatMap { it.split(';') }
            .map { name(it.trim()) }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }

    private val v23 = Regex("""^\((\d{1,3}|RX|CR)\)(.*)$""")

    /**
     * Old ID3 tags store the genre as a code: ID3v1 "13", ID3v2.3 "(13)" or "(13)Pop", where text
     * after the code refines it and wins. Codes outside the table are left as they are.
     */
    fun name(raw: String): String {
        v23.find(raw)?.let { m ->
            val rest = m.groupValues[2].trim()
            if (rest.isNotEmpty()) return rest
            return when (val code = m.groupValues[1]) {
                "RX" -> "Remix"
                "CR" -> "Cover"
                else -> ID3[code.toInt()] ?: raw
            }
        }
        if (raw.length in 1..3 && raw.all { it.isDigit() }) return ID3[raw.toInt()] ?: raw
        return raw
    }

    /** ID3v1 (0–79) plus the Winamp extensions (80–191). 133 is mapped to "Punk", not its original name. */
    private val ID3: Map<Int, String> = listOf(
        "Blues", "Classic Rock", "Country", "Dance", "Disco", "Funk", "Grunge", "Hip-Hop", "Jazz", "Metal",
        "New Age", "Oldies", "Other", "Pop", "R&B", "Rap", "Reggae", "Rock", "Techno", "Industrial",
        "Alternative", "Ska", "Death Metal", "Pranks", "Soundtrack", "Euro-Techno", "Ambient", "Trip-Hop", "Vocal", "Jazz+Funk",
        "Fusion", "Trance", "Classical", "Instrumental", "Acid", "House", "Game", "Sound Clip", "Gospel", "Noise",
        "Alternative Rock", "Bass", "Soul", "Punk", "Space", "Meditative", "Instrumental Pop", "Instrumental Rock", "Ethnic", "Gothic",
        "Darkwave", "Techno-Industrial", "Electronic", "Pop-Folk", "Eurodance", "Dream", "Southern Rock", "Comedy", "Cult", "Gangsta",
        "Top 40", "Christian Rap", "Pop/Funk", "Jungle", "Native American", "Cabaret", "New Wave", "Psychedelic", "Rave", "Showtunes",
        "Trailer", "Lo-Fi", "Tribal", "Acid Punk", "Acid Jazz", "Polka", "Retro", "Musical", "Rock & Roll", "Hard Rock",
        "Folk", "Folk-Rock", "National Folk", "Swing", "Fast Fusion", "Bebop", "Latin", "Revival", "Celtic", "Bluegrass",
        "Avantgarde", "Gothic Rock", "Progressive Rock", "Psychedelic Rock", "Symphonic Rock", "Slow Rock", "Big Band", "Chorus", "Easy Listening", "Acoustic",
        "Humour", "Speech", "Chanson", "Opera", "Chamber Music", "Sonata", "Symphony", "Booty Bass", "Primus", "Porn Groove",
        "Satire", "Slow Jam", "Club", "Tango", "Samba", "Folklore", "Ballad", "Power Ballad", "Rhythmic Soul", "Freestyle",
        "Duet", "Punk Rock", "Drum Solo", "A Cappella", "Euro-House", "Dance Hall", "Goa", "Drum & Bass", "Club-House", "Hardcore Techno",
        "Terror", "Indie", "BritPop", "Punk", "Polsk Punk", "Beat", "Christian Gangsta Rap", "Heavy Metal", "Black Metal", "Crossover",
        "Contemporary Christian", "Christian Rock", "Merengue", "Salsa", "Thrash Metal", "Anime", "J-Pop", "Synthpop", "Abstract", "Art Rock",
        "Baroque", "Bhangra", "Big Beat", "Breakbeat", "Chillout", "Downtempo", "Dub", "EBM", "Eclectic", "Electro",
        "Electroclash", "Emo", "Experimental", "Garage", "Global", "IDM", "Illbient", "Industro-Goth", "Jam Band", "Krautrock",
        "Leftfield", "Lounge", "Math Rock", "New Romantic", "Nu-Breakz", "Post-Punk", "Post-Rock", "Psytrance", "Shoegaze", "Space Rock",
        "Trop Rock", "World Music", "Neoclassical", "Audiobook", "Audio Theatre", "Neue Deutsche Welle", "Podcast", "Indie Rock", "G-Funk", "Dubstep",
        "Garage Rock", "Psybient",
    ).withIndex().associate { (i, name) -> i to name }
}
