package work.becode.llmquota

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class HttpStatusError(val code: Int, val detail: String? = null) : Exception("HTTP $code")

/**
 * Only the error fields of a failed response, never the whole body: token responses can
 * echo credentials, so the UI shows the server's error code and message and nothing else.
 */
fun errorDetail(body: String): String? = runCatching {
    val root = Json.Default.parseToJsonElement(body).jsonObject
    val err = root["error"]
    val parts = listOf(
        (err as? JsonPrimitive)?.contentOrNull,
        (err as? JsonObject)?.let { obj ->
            listOfNotNull(obj["type"]?.jsonPrimitive?.contentOrNull, obj["message"]?.jsonPrimitive?.contentOrNull).joinToString(": ")
        },
        root["error_description"]?.jsonPrimitive?.contentOrNull,
    )
    parts.filter { !it.isNullOrBlank() }.joinToString(" – ").take(200).ifBlank { null }
}.getOrNull()

private fun call(
    url: String,
    method: String = "GET",
    headers: Map<String, String> = emptyMap(),
    body: String? = null,
): String {
    val connection = URL(url).openConnection() as HttpURLConnection
    try {
        connection.requestMethod = method
        connection.connectTimeout = 15_000
        connection.readTimeout = 15_000
        headers.forEach { (k, v) -> connection.setRequestProperty(k, v) }
        if (body != null) {
            connection.doOutput = true
            connection.outputStream.use { it.write(body.toByteArray()) }
        }
        val code = connection.responseCode
        if (code !in 200..299) {
            val failure = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            throw HttpStatusError(code, failure.let { errorDetail(it) })
        }
        return connection.inputStream.bufferedReader().use { it.readText() }
    } finally {
        connection.disconnect()
    }
}

private fun formBody(vararg pairs: Pair<String, String>): String =
    pairs.joinToString("&") { (k, v) -> "${enc(k)}=${enc(v)}" }

private fun enc(v: String) = URLEncoder.encode(v, "UTF-8")

/** OAuth clients mirror codex-quota and the Claude CLI. */
object Api {
    const val CODEX_REDIRECT_URI = "http://localhost:1455/auth/callback"
    private const val CODEX_AUTH_URL = "https://auth.openai.com/oauth/authorize"
    private const val CODEX_TOKEN_URL = "https://auth.openai.com/oauth/token"
    private val CODEX_CLIENT_ID = BuildConfig.CODEX_CLIENT_ID
    private const val CODEX_USAGE_URL = "https://chatgpt.com/backend-api/wham/usage"
    private const val CLAUDE_TOKEN_URL = "https://platform.claude.com/v1/oauth/token"
    private val CLAUDE_CLIENT_ID = BuildConfig.CLAUDE_CLIENT_ID
    private const val CLAUDE_SCOPES = "org:create_api_key user:profile user:inference user:sessions:claude_code user:mcp_servers user:file_upload user:plugins"
    private const val CLAUDE_USAGE_URL = "https://api.anthropic.com/api/oauth/usage"
    private const val DEEPSEEK_BALANCE_URL = "https://api.deepseek.com/user/balance"

    fun codexAuthorizeUrl(challenge: String, state: String): String = "$CODEX_AUTH_URL?" + formBody(
        "client_id" to CODEX_CLIENT_ID,
        "redirect_uri" to CODEX_REDIRECT_URI,
        "response_type" to "code",
        "scope" to "openid profile email offline_access",
        "originator" to "pi",
        "code_challenge" to challenge,
        "code_challenge_method" to "S256",
        "state" to state,
    )

    fun codexExchange(code: String, verifier: String): TokenResponse = parseTokenResponse(
        call(
            CODEX_TOKEN_URL, "POST",
            mapOf("Content-Type" to "application/x-www-form-urlencoded"),
            formBody(
                "grant_type" to "authorization_code",
                "client_id" to CODEX_CLIENT_ID,
                "redirect_uri" to CODEX_REDIRECT_URI,
                "code_verifier" to verifier,
                "code" to code,
            ),
        ),
    )

    fun codexRefresh(refreshToken: String): TokenResponse = parseTokenResponse(
        call(
            CODEX_TOKEN_URL, "POST",
            mapOf("Content-Type" to "application/x-www-form-urlencoded"),
            formBody("grant_type" to "refresh_token", "client_id" to CODEX_CLIENT_ID, "refresh_token" to refreshToken),
        ),
    )

    fun codexUsage(accessToken: String, accountId: String): String = call(
        CODEX_USAGE_URL, headers = mapOf(
            "Authorization" to "Bearer $accessToken",
            "ChatGPT-Account-Id" to accountId,
            "User-Agent" to "codex-cli",
        ),
    )

    fun claudeRefresh(refreshToken: String): TokenResponse = parseTokenResponse(
        call(
            CLAUDE_TOKEN_URL, "POST", mapOf("Content-Type" to "application/json"),
            """{"grant_type":"refresh_token","refresh_token":"$refreshToken","client_id":"$CLAUDE_CLIENT_ID"}""",
        ),
    )

    fun claudeAuthorizeUrl(redirectUri: String, challenge: String, state: String): String =
        "https://claude.com/cai/oauth/authorize?" + formBody(
            "code" to "true",
            "client_id" to CLAUDE_CLIENT_ID,
            "response_type" to "code",
            "redirect_uri" to redirectUri,
            "scope" to CLAUDE_SCOPES,
            "code_challenge" to challenge,
            "code_challenge_method" to "S256",
            "state" to state,
        )

    /** Returns the raw token response; the caller reads tokens and account from it. */
    fun claudeExchange(code: String, state: String, verifier: String, redirectUri: String): String = call(
        CLAUDE_TOKEN_URL, "POST", mapOf("Content-Type" to "application/json"),
        buildJsonObject {
            put("grant_type", "authorization_code")
            put("code", code)
            put("state", state)
            put("client_id", CLAUDE_CLIENT_ID)
            put("redirect_uri", redirectUri)
            put("code_verifier", verifier)
        }.toString(),
    )

    fun claudeUsage(accessToken: String): String = call(
        CLAUDE_USAGE_URL, headers = mapOf(
            "Authorization" to "Bearer $accessToken",
            "anthropic-beta" to "oauth-2025-04-20",
        ),
    )

    fun deepseekBalance(apiKey: String): String = call(
        DEEPSEEK_BALANCE_URL, headers = mapOf("Authorization" to "Bearer $apiKey"),
    )
}
