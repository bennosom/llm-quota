package work.becode.llmquota

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ClaudeAccountTest {

    @Test
    fun readsUuidAndEmailFromTokenResponse() {
        val account = parseClaudeAccount("""{"access_token":"a","account":{"uuid":"u-1","email_address":"x@y.de"}}""")
        assertEquals("u-1", account.uuid)
        assertEquals("x@y.de", account.email)
    }

    @Test
    fun emailIsOptional() {
        assertNull(parseClaudeAccount("""{"account":{"uuid":"u-2"}}""").email)
    }
}
