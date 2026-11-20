package work.becode.llmquota

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** Encrypted on-device store for tokens and keys. Access tokens are cached until they expire. */
class Secrets(context: Context) {
    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "llm_balance_secrets",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun get(key: String): String? = prefs.getString(key, null)?.takeIf { it.isNotBlank() }

    fun put(key: String, value: String?) {
        prefs.edit().apply { if (value.isNullOrBlank()) remove(key) else putString(key, value.trim()) }.apply()
    }

    fun putLong(key: String, value: Long) = prefs.edit().putLong(key, value).apply()
    fun getLong(key: String): Long = prefs.getLong(key, 0L)
}
