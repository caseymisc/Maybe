package com.caseymisc.fitdash.data

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress

/** Tiny local HTTP server for client tests. */
class TestServer : AutoCloseable {
    data class Recorded(val method: String, val path: String, val query: String?, val headers: Map<String, String>, val body: String)

    val requests = mutableListOf<Recorded>()
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    val url: String get() = "http://127.0.0.1:${server.address.port}"

    fun route(prefix: String, handler: (Recorded) -> Pair<Int, String>) {
        server.createContext(prefix) { ex: HttpExchange ->
            val rec = Recorded(
                method = ex.requestMethod,
                path = ex.requestURI.path,
                query = ex.requestURI.rawQuery,
                headers = ex.requestHeaders.mapValues { it.value.first() }.mapKeys { it.key.lowercase() },
                body = ex.requestBody.readBytes().decodeToString(),
            )
            synchronized(requests) { requests += rec }
            val (code, body) = handler(rec)
            val bytes = body.toByteArray()
            ex.sendResponseHeaders(code, if (bytes.isEmpty()) -1 else bytes.size.toLong())
            if (bytes.isNotEmpty()) ex.responseBody.use { it.write(bytes) }
            ex.close()
        }
    }

    fun start() = apply { server.start() }

    override fun close() = server.stop(0)
}
