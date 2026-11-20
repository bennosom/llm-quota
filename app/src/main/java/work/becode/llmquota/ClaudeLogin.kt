package work.becode.llmquota

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant

private const val CLAUDE_CALLBACK_PATH = "/callback"

class ClaudeLoginSession internal constructor(
    override val authUrl: String,
    private val callback: LoopbackCallback,
    private val verifier: String,
    private val state: String,
    private val redirectUri: String,
) : OAuthFlow {

    override suspend fun await(): StoredAccount = withContext(Dispatchers.IO) {
        val code = try {
            callback.awaitCode()
        } finally {
            cancel()
        }
        val body = Api.claudeExchange(code, state, verifier, redirectUri)
        val tokens = parseTokenResponse(body)
        val account = parseClaudeAccount(body)
        StoredAccount(
            provider = CLAUDE,
            id = account.uuid,
            label = account.email ?: "Claude",
            refresh = tokens.refreshToken ?: error("Token-Antwort ohne refresh_token"),
            access = tokens.accessToken,
            expires = Instant.now().epochSecond + tokens.expiresInSec,
        )
    }

    override fun cancel() = callback.close()
}

object ClaudeLogin {
    /** Claude's CLI listens on a free loopback port; the same redirect rule applies here. */
    fun start(): ClaudeLoginSession {
        val verifier = Pkce.verifier()
        val state = Pkce.state()
        val server = bindLoopback(0)
        val redirectUri = "http://localhost:${server.localPort}$CLAUDE_CALLBACK_PATH"
        return ClaudeLoginSession(
            authUrl = Api.claudeAuthorizeUrl(redirectUri, Pkce.challenge(verifier), state),
            callback = LoopbackCallback(server, CLAUDE_CALLBACK_PATH, state),
            verifier = verifier,
            state = state,
            redirectUri = redirectUri,
        )
    }
}

data class ClaudeAccount(val uuid: String, val email: String?)

/** Reads the account uuid and email from the Claude token response. */
fun parseClaudeAccount(body: String): ClaudeAccount {
    val account = Json.Default.parseToJsonElement(body).jsonObject["account"] as? JsonObject
        ?: error("Token-Antwort ohne account")
    val uuid = account["uuid"]?.jsonPrimitive?.contentOrNull ?: error("Token-Antwort ohne account.uuid")
    return ClaudeAccount(uuid, account["email_address"]?.jsonPrimitive?.contentOrNull)
}
