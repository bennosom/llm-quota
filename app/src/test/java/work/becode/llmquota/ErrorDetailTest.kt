package work.becode.llmquota

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ErrorDetailTest {

    @Test
    fun readsOAuthErrorFields() {
        assertEquals("invalid_grant – code expired", errorDetail("""{"error":"invalid_grant","error_description":"code expired"}"""))
    }

    @Test
    fun readsNestedApiErrorMessage() {
        assertEquals("invalid_request_error: Invalid request format", errorDetail("""{"type":"error","error":{"type":"invalid_request_error","message":"Invalid request format"}}"""))
    }

    @Test
    fun ignoresBodiesWithoutErrorFields() {
        assertNull(errorDetail("""{"access_token":"secret"}"""))
        assertNull(errorDetail("not json"))
    }
}
