package work.becode.llmquota

/** An interactive login: open [authUrl] in the browser, then [await] the stored account. */
interface OAuthFlow {
    val authUrl: String
    suspend fun await(): StoredAccount
    fun cancel()
}
