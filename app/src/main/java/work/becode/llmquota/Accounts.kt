package work.becode.llmquota

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

const val CODEX = "codex"
const val CLAUDE = "claude"
const val DEEPSEEK = "deepseek"

/** A logged-in account. [access] is valid until [expires] (epoch seconds). */
@Serializable
data class StoredAccount(
    val provider: String,
    val id: String,
    val label: String,
    val refresh: String? = null,
    val access: String? = null,
    val expires: Long = 0,
    val apiKey: String? = null,
)

/** All accounts as one encrypted JSON list in [Secrets]. */
class AccountStore(private val secrets: Secrets) {
    @Synchronized
    fun list(): List<StoredAccount> =
        secrets.get(KEY)?.let { json.decodeFromString<List<StoredAccount>>(it) }.orEmpty()

    @Synchronized
    fun find(provider: String, id: String): StoredAccount? =
        list().firstOrNull { it.provider == provider && it.id == id }

    /** Replaces the account with the same provider and id, or appends it. */
    @Synchronized
    fun upsert(account: StoredAccount) {
        val all = list()
        val index = all.indexOfFirst { it.provider == account.provider && it.id == account.id }
        save(if (index < 0) all + account else all.toMutableList().also { it[index] = account })
    }

    @Synchronized
    fun remove(provider: String, id: String) =
        save(list().filterNot { it.provider == provider && it.id == id })

    /** Stores the accounts in the order of [keys] (`provider:id`); current tokens stay as they are. */
    @Synchronized
    fun reorder(keys: List<String>) {
        val order = keys.withIndex().associate { it.value to it.index }
        save(list().sortedBy { order["${it.provider}:${it.id}"] ?: Int.MAX_VALUE })
    }

    private fun save(accounts: List<StoredAccount>) = secrets.put(KEY, json.encodeToString(accounts))

    private companion object {
        const val KEY = "accounts"
        val json = Json { ignoreUnknownKeys = true }
    }
}
