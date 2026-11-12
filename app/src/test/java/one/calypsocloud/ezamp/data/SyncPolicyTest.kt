package one.calypsocloud.ezamp.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncPolicyTest {

    private val now = 1_800_000_000_000L
    private val window = SyncPolicy.THROTTLE_MS

    @Test fun throttleIsFifteenMinutes() {
        assertEquals(15 * 60 * 1000L, window)
    }

    @Test fun neverSyncedIsAlwaysDue() {
        assertTrue(SyncPolicy.due(lastSyncMs = 0, nowMs = now))
    }

    @Test fun insideTheWindowIsNotDue() {
        assertFalse(SyncPolicy.due(now - 1, now))
        assertFalse(SyncPolicy.due(now - window + 1, now))
    }

    @Test fun theWindowBoundaryIsDue() {
        assertTrue(SyncPolicy.due(now - window, now))
        assertTrue(SyncPolicy.due(now - window - 1, now))
    }
}
