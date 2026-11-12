package one.calypsocloud.ezamp.data

import org.junit.Assert.assertEquals
import org.junit.Test

class GenreTagsTest {

    @Test fun semicolonListsSplitWithOrWithoutSpaces() {
        assertEquals(
            listOf("Acid Techno", "Ambient Techno", "Idm", "Breakbeat"),
            GenreTags.of(listOf("Acid Techno; Ambient Techno; Idm", "Acid Techno;Breakbeat")),
        )
    }

    @Test fun slashesAndCommasStayInsideTheName() {
        assertEquals(listOf("Alt. Pop/Rock, Alt. Dance"), GenreTags.of(listOf("Alt. Pop/Rock, Alt. Dance")))
    }

    @Test fun caseDuplicatesBlanksAndPaddingAreDropped() {
        assertEquals(listOf("IDM", "Ambient"), GenreTags.of(listOf(" IDM ", "idm;", ";; Ambient ;")))
        assertEquals(emptyList<String>(), GenreTags.of(emptyList()))
    }
}
