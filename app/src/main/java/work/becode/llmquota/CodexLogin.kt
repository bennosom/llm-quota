package work.becode.llmquota

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant

private const val CODEX_PORT = 1455
private const val CODEX_CALLBACK_PATH = "/auth/callback"

class CodexLoginSession internal constructor(
    override val authUrl: String,
    private val callback: LoopbackCallback,
    private val verifier: String,
) : OAuthFlow {

    override suspend fun await(): StoredAccount = withContext(Dispatchers.IO) {
        val code = try {
            callback.awaitCode()
        } finally {
            cancel()
        }
        val tokens = Api.codexExchange(code, verifier)
        val identity = parseCodexIdToken(tokens.idToken ?: error("Token-Antwort ohne id_token"))
        StoredAccount(
            provider = CODEX,
            id = identity.accountId,
            label = identity.email ?: identity.accountId,
            refresh = tokens.refreshToken ?: error("Token-Antwort ohne refresh_token"),
            access = tokens.accessToken,
            expires = Instant.now().epochSecond + tokens.expiresInSec,
        )
    }

    override fun cancel() = callback.close()
}

object CodexLogin {
    /** Binds the callback listener first, so the browser redirect cannot arrive before it exists. */
    fun start(): CodexLoginSession {
        val verifier = Pkce.verifier()
        val state = Pkce.state()
        val server = bindLoopback(CODEX_PORT)
        return CodexLoginSession(
            authUrl = Api.codexAuthorizeUrl(Pkce.challenge(verifier), state),
            callback = LoopbackCallback(server, CODEX_CALLBACK_PATH, state),
            verifier = verifier,
        )
    }
}
