package one.calypsocloud.ezamp.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ArtistCreditsTest {

    @Test fun featuresAreCreditedAlongsideTheAlbumArtist() {
        assertEquals(listOf("Siti Nurhaliza", "M. Nasir", "Various"), ArtistCredits.of(listOf("Siti Nurhaliza", "M. Nasir"), "Various"))
    }

    @Test fun albumArtistAlreadyListedIsNotRepeatedEvenInADifferentCase() {
        assertEquals(listOf("AFX"), ArtistCredits.of(listOf("AFX"), "Afx"))
    }

    @Test fun blanksAndPaddingAreDropped() {
        assertEquals(listOf("Sheila Majid"), ArtistCredits.of(listOf("  Sheila Majid ", "", "   "), null))
        assertEquals(emptyList<String>(), ArtistCredits.of(emptyList(), "  "))
    }
}
