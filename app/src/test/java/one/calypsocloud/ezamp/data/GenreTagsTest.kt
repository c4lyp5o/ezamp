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

    @Test fun numericId3CodesBecomeNames() {
        // The codes this library actually has, plus the table's ends.
        assertEquals(
            listOf("Pop", "Electronic", "R&B", "Metal", "Heavy Metal", "Rock", "Techno", "Death Metal", "Ambient", "Blues", "Psybient"),
            GenreTags.of(listOf("13", "52", "14", "9", "137", "17", "18", "22", "26", "0", "191")),
        )
    }

    @Test fun id3v23ParenthesisedCodesAndRefinements() {
        assertEquals("Rock", GenreTags.name("(17)"))
        assertEquals("Britpop", GenreTags.name("(17)Britpop"))
        assertEquals("Remix", GenreTags.name("(RX)"))
        assertEquals("Cover", GenreTags.name("(CR)"))
    }

    @Test fun unknownCodesAndRealNamesAreUntouched() {
        assertEquals("255", GenreTags.name("255"))
        assertEquals("(999)", GenreTags.name("(999)"))
        assertEquals("2000s", GenreTags.name("2000s"))
        assertEquals("80s Pop", GenreTags.name("80s Pop"))
        assertEquals("1234", GenreTags.name("1234"))
    }

    @Test fun codeAndItsNameCollapseToOne() {
        assertEquals(listOf("Pop"), GenreTags.of(listOf("13", "pop", "(13)")))
    }

    @Test fun caseDuplicatesBlanksAndPaddingAreDropped() {
        assertEquals(listOf("IDM", "Ambient"), GenreTags.of(listOf(" IDM ", "idm;", ";; Ambient ;")))
        assertEquals(emptyList<String>(), GenreTags.of(emptyList()))
    }
}
