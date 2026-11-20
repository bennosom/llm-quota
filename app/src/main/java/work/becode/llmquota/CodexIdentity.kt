package work.becode.llmquota

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Base64

data class CodexIdentity(val accountId: String, val email: String?)

/**
 * Reads account id and email from the id_token payload. The signature is not checked:
 * the token arrives directly from the TLS token endpoint in the same exchange.
 */
fun parseCodexIdToken(idToken: String): CodexIdentity {
    val payload = idToken.split(".").getOrNull(1) ?: error("Invalid id_token")
    val claims = Json.Default.parseToJsonElement(String(Base64.getUrlDecoder().decode(payload))).jsonObject
    val auth = claims["https://api.openai.com/auth"]?.jsonObject
    val accountId = (auth?.get("chatgpt_account_id") ?: claims["chatgpt_account_id"])?.jsonPrimitive?.contentOrNull
        ?: error("Account ID missing in token")
    return CodexIdentity(accountId, claims["email"]?.jsonPrimitive?.contentOrNull)
}
