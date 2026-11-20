package work.becode.llmquota

import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder

private const val LOGIN_TIMEOUT_MS = 300_000
private const val REQUEST_TIMEOUT_MS = 10_000

/** Binds a single-use OAuth listener on 127.0.0.1. Port 0 picks a free port. */
fun bindLoopback(port: Int): ServerSocket = ServerSocket().apply {
    reuseAddress = true
    soTimeout = LOGIN_TIMEOUT_MS
    bind(InetSocketAddress("127.0.0.1", port))
}

/** Waits for the redirect on [path] and returns its code once [state] matches. */
class LoopbackCallback(private val server: ServerSocket, private val path: String, private val state: String) {

    fun awaitCode(): String {
        while (true) {
            val code = try {
                server.accept().use { handleRequest(it) }
            } catch (e: IOException) {
                error("Sign-in cancelled or expired")
            }
            if (code != null) return code
        }
    }

    fun close() {
        runCatching { server.close() }
    }

    /** Returns the code for a matching callback, or null for any other request. */
    private fun handleRequest(socket: Socket): String? {
        socket.soTimeout = REQUEST_TIMEOUT_MS
        val requestLine = socket.getInputStream().bufferedReader().readLine().orEmpty()
        val target = requestLine.split(" ").getOrElse(1) { "" }
        if (!target.startsWith(path)) {
            respond(socket, 404, "Page not found", "Nothing to see here. You can close this tab.")
            return null
        }
        val params = parseQuery(target.substringAfter('?', ""))
        return when {
            params["state"] != state -> {
                respond(
                    socket, 400, "This sign-in has expired",
                    "The link no longer matches the sign-in you started. Start the sign-in again in LLM Quota.",
                )
                null
            }
            params["error"] != null -> {
                respond(
                    socket, 400, "Sign-in was not completed",
                    "The provider did not grant access. You can close this tab and try again in LLM Quota.",
                )
                error("Sign-in denied: ${params["error"]}")
            }
            else -> {
                respond(
                    socket, 200, "You're signed in",
                    "All set. You can close this tab and return to LLM Quota.",
                )
                params["code"] ?: error("Code missing")
            }
        }
    }

    private fun respond(socket: Socket, status: Int, title: String, message: String) {
        val body = "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><title>LLM Quota</title></head>" +
            "<body style=\"background:#000;color:#39FF14;font-family:monospace;padding:2em\">" +
            "<h1>$title</h1><p>$message</p></body></html>"
        val reason = if (status == 200) "OK" else "Error"
        val head = "HTTP/1.1 $status $reason\r\nContent-Type: text/html; charset=utf-8\r\n" +
            "Content-Length: ${body.toByteArray().size}\r\nConnection: close\r\n\r\n"
        socket.getOutputStream().write((head + body).toByteArray())
    }

    private fun parseQuery(query: String): Map<String, String> =
        query.split("&").filter { it.contains('=') }.associate {
            it.substringBefore('=') to URLDecoder.decode(it.substringAfter('='), "UTF-8")
        }
}
