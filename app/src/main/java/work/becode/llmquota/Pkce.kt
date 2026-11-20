package work.becode.llmquota

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/** PKCE (RFC 7636, S256) and state values for the OAuth authorization code flow. */
object Pkce {
    private val random = SecureRandom()
    private val encoder = Base64.getUrlEncoder().withoutPadding()

    fun verifier(): String = encoder.encodeToString(ByteArray(32).also { random.nextBytes(it) })

    fun challenge(verifier: String): String =
        encoder.encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()))

    fun state(): String = encoder.encodeToString(ByteArray(32).also { random.nextBytes(it) })
}
