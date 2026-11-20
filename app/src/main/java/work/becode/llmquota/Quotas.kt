package work.becode.llmquota

import java.time.Instant

/** Fetches quota per stored account. Tokens refreshed on the way are written back to [AccountStore]. */
class QuotaFetcher(private val store: AccountStore) {

    fun fetch(provider: String, id: String): SourceResult {
        val account = store.find(provider, id) ?: return SourceResult(id, error = "Account missing")
        return try {
            when (provider) {
                CODEX -> {
                    val body = usage(account) { Api.codexUsage(it, account.id) }
                    SourceResult(account.label, windows = parseCodexUsage(body))
                }
                CLAUDE -> {
                    val body = usage(account) { Api.claudeUsage(it) }
                    SourceResult(account.label, windows = parseClaudeUsage(body))
                }
                DEEPSEEK -> {
                    val result = parseDeepSeekBalance(Api.deepseekBalance(account.apiKey ?: error("API key missing")))
                    result.error?.let { SourceResult(account.label, error = it) }
                        ?: SourceResult(account.label, figure = "Balance" to "${result.balance} ${result.currency}")
                }
                else -> SourceResult(account.label, error = "Provider not supported")
            }
        } catch (e: Exception) {
            SourceResult(account.label, error = errorText(e))
        }
    }

    /** Runs [call] with a valid access token; on 401 it refreshes once and retries. */
    private fun usage(account: StoredAccount, call: (String) -> String): String {
        val access = account.access?.takeIf { account.expires > now() + 60 } ?: refresh(account)
        return try {
            call(access)
        } catch (e: HttpStatusError) {
            if (e.code != 401) throw e
            call(refresh(account))
        }
    }

    private fun refresh(account: StoredAccount): String {
        val token = account.refresh ?: error("Refresh token missing")
        val tokens = when (account.provider) {
            CODEX -> Api.codexRefresh(token)
            CLAUDE -> Api.claudeRefresh(token)
            else -> error("Provider not supported")
        }
        store.upsert(
            account.copy(
                access = tokens.accessToken,
                refresh = tokens.refreshToken ?: token,
                expires = now() + tokens.expiresInSec,
            ),
        )
        return tokens.accessToken
    }

    private fun now() = Instant.now().epochSecond
}

fun errorText(e: Exception): String = when (e) {
    is HttpStatusError -> listOfNotNull("HTTP ${e.code}", e.detail).joinToString(": ")
    else -> e.message ?: "Error"
}
