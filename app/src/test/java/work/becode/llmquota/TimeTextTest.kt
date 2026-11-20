package work.becode.llmquota

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class TimeTextTest {
    private val zone = ZoneId.of("UTC")
    private val now = Instant.parse("2026-11-20T15:00:00Z")

    @Test
    fun relativeUsesOnlyTheHighestUnit() {
        assertEquals("in 4 days", formatRelative(now.plusSeconds(4 * 86_400 + 5 * 3_600), now))
        assertEquals("in 1 day", formatRelative(now.plusSeconds(86_400 + 100), now))
        assertEquals("in 3 h", formatRelative(now.plusSeconds(3 * 3_600 + 59 * 60), now))
        assertEquals("in 12 min", formatRelative(now.plusSeconds(12 * 60 + 30), now))
        assertEquals("in <1 min", formatRelative(now.plusSeconds(20), now))
        assertEquals("now", formatRelative(now.minusSeconds(5), now))
    }

    @Test
    fun absoluteNamesTodayOrTheDate() {
        assertEquals("Today, 21:18", formatAbsolute(Instant.parse("2026-11-20T21:18:00Z"), now, zone))
        assertEquals("24 Nov, 12:58", formatAbsolute(Instant.parse("2026-11-24T12:58:00Z"), now, zone))
    }

    @Test
    fun stampNamesTodayOrTheDate() {
        assertEquals("Today, 16:27:15", formatStamp(Instant.parse("2026-11-20T16:27:15Z"), now, zone))
        assertEquals("19 Nov 2026, 16:27:15", formatStamp(Instant.parse("2026-11-19T16:27:15Z"), now, zone))
    }

    @Test
    fun resetCombinesAbsoluteAndRelative() {
        val at = Instant.parse("2026-11-20T21:18:00Z")
        assertEquals("Reset Today, 21:18 · in 6 h", formatReset(at.epochSecond, now, zone))
        assertEquals("Reset –", formatReset(null, now, zone))
    }
}
