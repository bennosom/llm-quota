package work.becode.llmquota

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Base64

class CodexIdentityTest {

    @Test
    fun readsAccountIdAndEmailFromAuthClaim() {
        val claims = """{"email":"a@b.de","https://api.openai.com/auth":{"chatgpt_account_id":"acc-1"}}"""
        val payload = Base64.getUrlEncoder().withoutPadding().encodeToString(claims.toByteArray())
        val identity = parseCodexIdToken("e30.$payload.sig")
        assertEquals("acc-1", identity.accountId)
        assertEquals("a@b.de", identity.email)
    }
}
