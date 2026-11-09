package com.caseymisc.fitdash.data

import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest

/** Tiny local HTTP server for client tests, routing by path prefix (longest match wins). */
class TestServer : AutoCloseable {
    data class Recorded(val method: String, val path: String, val query: String?, val headers: Map<String, String>, val body: String)

    val requests = mutableListOf<Recorded>()
    private val routes = mutableMapOf<String, (Recorded) -> Pair<Int, String>>()
    private val server = MockWebServer()
    val url: String get() = server.url("/").toString().trimEnd('/')

    fun route(prefix: String, handler: (Recorded) -> Pair<Int, String>) {
        routes[prefix] = handler
    }

    fun start() = apply {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val requestUrl = request.requestUrl!!
                val rec = Recorded(
                    method = request.method.orEmpty(),
                    path = requestUrl.encodedPath,
                    query = requestUrl.encodedQuery,
                    headers = request.headers.toMultimap().mapValues { it.value.first() }.mapKeys { it.key.lowercase() },
                    body = request.body.readUtf8(),
                )
                synchronized(requests) { requests += rec }
                val handler = routes.entries
                    .filter { rec.path.startsWith(it.key) }
                    .maxByOrNull { it.key.length }
                    ?.value ?: return MockResponse().setResponseCode(404)
                val (code, body) = handler(rec)
                return MockResponse().setResponseCode(code).setBody(body)
            }
        }
        server.start()
    }

    override fun close() = server.shutdown()
}
