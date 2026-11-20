package work.becode.llmquota

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.Socket
import java.util.concurrent.Executors

class LoopbackCallbackTest {

    private fun get(port: Int, target: String): String = Socket("127.0.0.1", port).use { socket ->
        socket.getOutputStream().write("GET $target HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n".toByteArray())
        socket.getInputStream().readBytes().toString(Charsets.UTF_8)
    }

    @Test
    fun successPageConfirmsSignIn() {
        val server = bindLoopback(0)
        val callback = LoopbackCallback(server, "/callback", "s1")
        val pool = Executors.newSingleThreadExecutor()
        val code = pool.submit<String> { callback.awaitCode() }
        val response = get(server.localPort, "/callback?state=s1&code=abc")
        assertEquals("abc", code.get())
        assertTrue(response.startsWith("HTTP/1.1 200 OK"))
        assertTrue(response.contains("<h1>You're signed in</h1>"))
        pool.shutdown()
        server.close()
    }

    @Test
    fun deniedPageExplainsNextStep() {
        val server = bindLoopback(0)
        val callback = LoopbackCallback(server, "/callback", "s1")
        val pool = Executors.newSingleThreadExecutor()
        val result = pool.submit<String> { runCatching { callback.awaitCode() }.exceptionOrNull()?.message.orEmpty() }
        val response = get(server.localPort, "/callback?state=s1&error=access_denied")
        assertTrue(response.startsWith("HTTP/1.1 400 Error"))
        assertTrue(response.contains("<h1>Sign-in was not completed</h1>"))
        assertTrue(result.get().startsWith("Sign-in denied"))
        pool.shutdown()
        server.close()
    }

    @Test
    fun unknownPathShowsNotFoundPage() {
        val server = bindLoopback(0)
        val callback = LoopbackCallback(server, "/callback", "s1")
        val pool = Executors.newSingleThreadExecutor()
        val code = pool.submit<String> { callback.awaitCode() }
        val response = get(server.localPort, "/other")
        assertTrue(response.startsWith("HTTP/1.1 404 Error"))
        assertTrue(response.contains("<h1>Page not found</h1>"))
        // Finish the flow so the executor thread exits.
        get(server.localPort, "/callback?state=s1&code=done")
        assertEquals("done", code.get())
        pool.shutdown()
        server.close()
    }
}
