package work.becode.llmquota

import java.util.UUID

/** Validates a DeepSeek key with one balance call and returns the account to store. */
fun deepSeekAccount(key: String): StoredAccount {
    val result = parseDeepSeekBalance(Api.deepseekBalance(key))
    result.error?.let { error(it) }
    return StoredAccount(
        provider = DEEPSEEK,
        id = UUID.randomUUID().toString(),
        label = maskKey(key),
        apiKey = key,
    )
}

/** The key's first three and last four characters, e.g. "sk-...abcd". */
fun maskKey(key: String): String = "${key.take(3)}...${key.takeLast(4)}"

/** The text that tells accounts of one provider apart; DeepSeek accounts always show their masked key. */
fun displayLabel(account: StoredAccount): String =
    if (account.provider == DEEPSEEK && account.apiKey != null) maskKey(account.apiKey) else account.label
