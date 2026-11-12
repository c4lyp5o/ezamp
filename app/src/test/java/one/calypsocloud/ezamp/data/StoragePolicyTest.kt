package one.calypsocloud.ezamp.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StoragePolicyTest {

    private val mb = StoragePolicy.MB

    @Test fun zeroOrNegativeCapMeansUnlimited() {
        assertEquals(0L, StoragePolicy.capBytes(0))
        assertEquals(0L, StoragePolicy.capBytes(-1))
        assertFalse(StoragePolicy.atCap(Long.MAX_VALUE / 2, 0))
        assertFalse(StoragePolicy.overCap(Long.MAX_VALUE / 2, -5))
        assertEquals(Long.MAX_VALUE, StoragePolicy.remainingBytes(10 * mb, 0))
    }

    @Test fun capConvertsMegabytesWithoutIntOverflow() {
        // 8 GB in bytes overflows Int; the math must be done in Long.
        assertEquals(8192L * 1024 * 1024, StoragePolicy.capBytes(8192))
    }

    @Test fun atCapRefusesAtTheBoundaryButOverCapNeedsToCrossIt() {
        // atCap guards the door: exactly full means no new album.
        // overCap runs after each landed track: exactly full is not yet over.
        assertFalse(StoragePolicy.atCap(1024 * mb - 1, 1024))
        assertTrue(StoragePolicy.atCap(1024 * mb, 1024))
        assertFalse(StoragePolicy.overCap(1024 * mb, 1024))
        assertTrue(StoragePolicy.overCap(1024 * mb + 1, 1024))
    }

    @Test fun remainingNeverGoesNegative() {
        assertEquals(mb, StoragePolicy.remainingBytes(1023 * mb, 1024))
        assertEquals(0L, StoragePolicy.remainingBytes(2048 * mb, 1024))
    }

    @Test fun usedSumsOnlyWhatItIsGiven() {
        assertEquals(0L, StoragePolicy.used(emptyList()))
        assertEquals(6L, StoragePolicy.used(listOf(1L, 2L, 3L)))
    }
}
