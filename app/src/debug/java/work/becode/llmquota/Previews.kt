package work.becode.llmquota

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId

private val NOW = Instant.parse("2026-11-20T15:00:00Z")

private fun reset(minutes: Long) = NOW.plusSeconds(minutes * 60).epochSecond

private val PREVIEW_ACCOUNTS = listOf(
    StoredAccount(CODEX, "codex", "dev@example.com") to SourceResult(
        "dev@example.com",
        windows = listOf(QuotaWindow("5h", 64, reset(190)), QuotaWindow("Week", 11, reset(3 * 24 * 60 + 400))),
    ),
    StoredAccount(CLAUDE, "claude", "me@example.com") to SourceResult(
        "me@example.com",
        windows = listOf(QuotaWindow("5h", 3, reset(25)), QuotaWindow("Week", 47, reset(5 * 24 * 60))),
    ),
    StoredAccount(DEEPSEEK, "deepseek", "", apiKey = "sk-1234567890abcd") to SourceResult(
        "sk-...abcd",
        figure = "Balance" to "12.34 USD",
    ),
)

/** The dashboard with dummy accounts: green, yellow and red bars. Also the source of the README screenshot. */
@Preview(showBackground = true, backgroundColor = 0xFF000000, widthDp = 360, heightDp = 640)
@Composable
fun DashboardPreview() {
    LlmQuotaTheme {
        Column {
            DashboardHeader(onAdd = {})
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                StampLine("Last updated ${formatStamp(NOW, NOW, ZoneId.systemDefault())}")
                PREVIEW_ACCOUNTS.forEach { (account, result) ->
                    AccountCard(
                        account = account,
                        result = result,
                        loading = false,
                        dragging = false,
                        now = NOW,
                        dragOffset = 0f,
                        shift = { 0f },
                        onHeight = {},
                        onOpenUsage = {},
                        onRefresh = {},
                        onDeleteRequest = {},
                        onDragStart = {},
                        onDragBy = {},
                        onDragEnd = {},
                    )
                    androidx.compose.foundation.layout.Spacer(Modifier.padding(bottom = CARD_GAP))
                }
            }
        }
    }
}
