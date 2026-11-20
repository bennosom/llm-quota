package work.becode.llmquota

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuotaParsingTest {

    @Test
    fun codexUsageMapsUsedPercentToRemainingAndKeepsResetEpoch() {
        val body = """{"rate_limit":{"primary_window":{"used_percent":57,"limit_window_seconds":18000,"reset_after_seconds":100,"reset_at":1795183198},
            "secondary_window":{"used_percent":100,"limit_window_seconds":604800,"reset_after_seconds":5,"reset_at":1795521539}}}"""
        val windows = parseCodexUsage(body)
        assertEquals(listOf("5h", "Week"), windows.map { it.label })
        assertEquals(listOf(43, 0), windows.map { it.remainingPercent })
        assertEquals(1795183198L, windows[0].resetEpochSec)
    }

    @Test
    fun codexUsageWithoutRateLimitIsEmpty() {
        assertEquals(emptyList<QuotaWindow>(), parseCodexUsage("{}"))
    }

    @Test
    fun codexUsageToleratesJsonNullFields() {
        assertEquals(emptyList<QuotaWindow>(), parseCodexUsage("""{"rate_limit":null}"""))
        val windows = parseCodexUsage("""{"rate_limit":{"primary_window":null,"secondary_window":{"used_percent":20,"reset_at":null}}}""")
        assertEquals(listOf("Week"), windows.map { it.label })
        assertEquals(listOf(80), windows.map { it.remainingPercent })
        assertNull(windows[0].resetEpochSec)
    }

    @Test
    fun claudeUsageTreatsUtilizationAsPercentUsed() {
        val body = """{"five_hour":{"utilization":100.0,"resets_at":"2026-11-20T11:00:00+00:00"},
            "seven_day":{"utilization":31.0,"resets_at":"2026-11-25T09:00:00Z"},"extra_usage":null}"""
        val windows = parseClaudeUsage(body)
        assertEquals(listOf(0, 69), windows.map { it.remainingPercent })
        assertEquals(java.time.Instant.parse("2026-11-20T11:00:00Z").epochSecond, windows[0].resetEpochSec)
    }

    @Test
    fun claudeUtilizationAboveHundredClampsToZero() {
        assertEquals(0, parseClaudeUsage("""{"five_hour":{"utilization":104.0,"resets_at":null}}""")[0].remainingPercent)
        assertNull(parseClaudeUsage("""{"five_hour":{"utilization":104.0,"resets_at":null}}""")[0].resetEpochSec)
    }

    @Test
    fun deepSeekBalanceReadsFirstEntry() {
        val result = parseDeepSeekBalance("""{"is_available":true,"balance_infos":[{"currency":"USD","total_balance":"4.20","granted_balance":"0.00","topped_up_balance":"4.20"}]}""")
        assertEquals("4.20", result.balance)
        assertEquals("USD", result.currency)
    }

    @Test
    fun tokenResponseKeepsOptionalRotatedRefreshToken() {
        val rotated = parseTokenResponse("""{"access_token":"a","refresh_token":"r2","expires_in":3600}""")
        assertEquals("r2", rotated.refreshToken)
        val kept = parseTokenResponse("""{"access_token":"a","expires_in":900}""")
        assertNull(kept.refreshToken)
        assertEquals(900L, kept.expiresInSec)
    }

    @Test
    fun windowLabelFollowsTheBackendLength() {
        assertEquals("5h", windowLabel(18_000, "x"))
        assertEquals("Week", windowLabel(604_800, "x"))
        assertEquals("2d", windowLabel(172_800, "x"))
        assertEquals("30 min", windowLabel(1_800, "x"))
        assertEquals("fallback", windowLabel(null, "fallback"))
    }
}
