package work.becode.llmquota

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlin.math.roundToInt

/** One quota window. [remainingPercent] is 0..100 of what is left; null when unknown. */
data class QuotaWindow(val label: String, val remainingPercent: Int?, val resetEpochSec: Long?)

data class SourceResult(
    val name: String,
    val windows: List<QuotaWindow> = emptyList(),
    val error: String? = null,
    /** A plain amount without a percentage, shown like a window's figure (DeepSeek balance). */
    val figure: Pair<String, String>? = null,
)

data class DeepSeekResult(val balance: String? = null, val currency: String? = null, val error: String? = null)

private val json = Json { ignoreUnknownKeys = true }

private fun remaining(usedPercent: Double?): Int? =
    usedPercent?.let { (100.0 - it).roundToInt().coerceIn(0, 100) }

/** Names a window by its length: whole weeks, days or hours; the backend decides the length. */
fun windowLabel(seconds: Long?, fallback: String): String = when {
    seconds == null || seconds <= 0 -> fallback
    seconds % 604_800 == 0L -> if (seconds == 604_800L) "Week" else "${seconds / 604_800} wk"
    seconds % 86_400 == 0L -> "${seconds / 86_400}d"
    seconds % 3_600 == 0L -> "${seconds / 3_600}h"
    else -> "${seconds / 60} min"
}

/** Codex `wham/usage`: rate_limit.primary_window and secondary_window, each labelled by its own length. JSON null counts as missing. */
fun parseCodexUsage(body: String): List<QuotaWindow> {
    val rate = json.parseToJsonElement(body).jsonObject["rate_limit"] as? JsonObject
        ?: return emptyList()
    return listOf("5h" to "primary_window", "Week" to "secondary_window").mapNotNull { (fallback, key) ->
        val window = rate[key] as? JsonObject ?: return@mapNotNull null
        QuotaWindow(
            label = windowLabel(window["limit_window_seconds"]?.jsonPrimitive?.longOrNull, fallback),
            remainingPercent = remaining(window["used_percent"]?.jsonPrimitive?.doubleOrNull),
            resetEpochSec = window["reset_at"]?.jsonPrimitive?.longOrNull,
        )
    }
}

/** Claude `oauth/usage`: five_hour and seven_day report utilization as percent used. */
fun parseClaudeUsage(body: String): List<QuotaWindow> {
    val root = json.parseToJsonElement(body).jsonObject
    return listOf("5h" to "five_hour", "Week" to "seven_day").mapNotNull { (label, key) ->
        val window = root[key] as? JsonObject ?: return@mapNotNull null
        QuotaWindow(
            label = label,
            remainingPercent = remaining(window["utilization"]?.jsonPrimitive?.doubleOrNull),
            resetEpochSec = window["resets_at"]?.jsonPrimitive?.contentOrNull
                ?.let { java.time.Instant.parse(it).epochSecond },
        )
    }
}

/** DeepSeek `user/balance`: first balance entry in balance_infos. */
fun parseDeepSeekBalance(body: String): DeepSeekResult {
    val infos = json.parseToJsonElement(body).jsonObject["balance_infos"]?.jsonArray
        ?: return DeepSeekResult(error = "keine Guthaben-Daten")
    val first: JsonObject = infos.firstOrNull()?.jsonObject
        ?: return DeepSeekResult(error = "keine Guthaben-Daten")
    return DeepSeekResult(
        balance = first["total_balance"]?.jsonPrimitive?.contentOrNull,
        currency = first["currency"]?.jsonPrimitive?.contentOrNull,
    )
}

/** Token endpoint response; refresh_token is present only when the server rotates it. */
data class TokenResponse(
    val accessToken: String,
    val refreshToken: String?,
    val expiresInSec: Long,
    val idToken: String? = null,
)

fun parseTokenResponse(body: String): TokenResponse {
    val root = json.parseToJsonElement(body).jsonObject
    return TokenResponse(
        accessToken = root["access_token"]?.jsonPrimitive?.contentOrNull
            ?: error("Token-Antwort ohne access_token"),
        refreshToken = root["refresh_token"]?.jsonPrimitive?.contentOrNull,
        expiresInSec = root["expires_in"]?.jsonPrimitive?.longOrNull ?: 3600L,
        idToken = root["id_token"]?.jsonPrimitive?.contentOrNull,
    )
}
