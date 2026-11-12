package one.calypsocloud.ezamp.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamUrlTest {

    private val api = JellyfinApi("http://jf.local:8096", "token-never-in-urls")

    @Test fun directUrlUsesKnownContainerAndNeverCarriesTheToken() {
        val url = api.streamUrl("abc", "FLAC")
        assertEquals("http://jf.local:8096/Audio/abc/stream.flac?static=true&mediaSourceId=abc", url)
        assertFalse(url.contains("token-never-in-urls"))
    }

    @Test fun directUrlDropsUnknownContainers() {
        assertEquals("http://jf.local:8096/Audio/abc/stream?static=true&mediaSourceId=abc", api.streamUrl("abc", "mkv"))
        assertEquals("http://jf.local:8096/Audio/abc/stream?static=true&mediaSourceId=abc", api.streamUrl("abc", null))
    }

    @Test fun saverUrlTranscodesToMp3AtTheRequestedRate() {
        val url = api.saverUrl("abc", kbps = 192)
        assertTrue(url.startsWith("http://jf.local:8096/Audio/abc/stream.mp3?static=false&audioCodec=mp3"))
        assertTrue(url.contains("&audioBitRate=192000"))
        assertFalse(url.contains("startTimeTicks"))
        assertFalse(url.contains("token-never-in-urls"))
    }

    @Test fun saverSeekRoundTripsThroughTicks() {
        // Jellyfin ticks are 100 ns: 1 ms = 10,000 ticks. The service re-derives the offset from the
        // URL, so what saverUrl writes and transcodeOffsetMs reads must agree exactly.
        val url = api.saverUrl("abc", kbps = 192, startMs = 92_986)
        assertTrue(url.contains("&startTimeTicks=929860000"))
        assertEquals(92_986L, JellyfinApi.transcodeOffsetMs(url))
    }

    @Test fun offsetIsZeroWhenThereIsNone() {
        assertEquals(0L, JellyfinApi.transcodeOffsetMs(api.saverUrl("abc")))
        assertEquals(0L, JellyfinApi.transcodeOffsetMs(api.streamUrl("abc", "mp3")))
        assertEquals(0L, JellyfinApi.transcodeOffsetMs("file:///data/downloads/abc.flac"))
        assertEquals(0L, JellyfinApi.transcodeOffsetMs(""))
    }
}
