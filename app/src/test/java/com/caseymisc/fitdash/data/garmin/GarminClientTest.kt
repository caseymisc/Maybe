package com.caseymisc.fitdash.data.garmin

import com.caseymisc.fitdash.data.TestServer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class GarminClientTest {
    private class MemoryStorage(var tokens: GarminTokens? = null) : GarminTokenStorage {
        override suspend fun load() = tokens
        override suspend fun save(tokens: GarminTokens) { this.tokens = tokens }
        override suspend fun clear() { tokens = null }
    }

    private fun jwt(clientId: String, exp: Long): String {
        val enc = Base64.getUrlEncoder().withoutPadding()
        val header = enc.encodeToString("""{"alg":"none"}""".toByteArray())
        val payload = enc.encodeToString("""{"client_id":"$clientId","exp":$exp}""".toByteArray())
        return "$header.$payload.sig"
    }

    private val now = 1_800_000_000L

    @Test
    fun ticketExchangeFallsThroughClientIdsAndLoadsProfile() = runBlocking {
        TestServer().use { server ->
            server.route("/token") { req ->
                if ("GARMIN_CONNECT_MOBILE_ANDROID_DI_2025Q2" in req.body) {
                    400 to """{"error":"invalid_client"}"""
                } else {
                    200 to """{"access_token":"${jwt("GARMIN_CONNECT_MOBILE_ANDROID_DI_2024Q4", now + 3600)}","refresh_token":"r1","expires_in":3600}"""
                }
            }
            server.route("/userprofile-service/socialProfile") { 200 to """{"displayName":"runner42","fullName":"Pat"}""" }
            server.start()

            val storage = MemoryStorage()
            val client = GarminClient(storage, apiBase = server.url, tokenUrl = "${server.url}/token", now = { now })
            val tokens = client.signInWithTicket("ST-123", GarminAuth.LoginFlow.MOBILE.serviceUrl)

            assertEquals("runner42", tokens.displayName)
            assertEquals("GARMIN_CONNECT_MOBILE_ANDROID_DI_2024Q4", tokens.clientId)
            assertEquals(now + 3600, tokens.expiresAt)
            assertEquals(tokens, storage.tokens)

            val exchange = server.requests.last { it.path == "/token" }
            assertTrue(exchange.body.contains("service_ticket=ST-123"))
            assertTrue(exchange.body.contains("grant_type=https%3A%2F%2Fconnectapi.garmin.com"))
            val basic = String(Base64.getDecoder().decode(exchange.headers["authorization"]!!.removePrefix("Basic ")))
            assertEquals("GARMIN_CONNECT_MOBILE_ANDROID_DI_2024Q4:", basic)
            assertEquals("GCM-Android-5.23", exchange.headers["user-agent"])
        }
    }

    @Test
    fun refreshesExpiringTokenBeforeCallingApi() = runBlocking {
        TestServer().use { server ->
            server.route("/token") { req ->
                assertTrue(req.body.contains("grant_type=refresh_token"))
                assertTrue(req.body.contains("refresh_token=old-refresh"))
                200 to """{"access_token":"${jwt("CID", now + 7200)}","refresh_token":"new-refresh"}"""
            }
            server.route("/hrv-service/hrv/2026-11-09") { req ->
                200 to """{"auth":"${req.headers["authorization"]}"}"""
            }
            server.start()

            val storage = MemoryStorage(GarminTokens("old", "old-refresh", "CID", expiresAt = now + 10, displayName = "me"))
            val client = GarminClient(storage, apiBase = server.url, tokenUrl = "${server.url}/token", now = { now })
            val result = client.get("/hrv-service/hrv/2026-11-09").toString()

            assertTrue(result.contains("Bearer ey"))
            assertEquals("new-refresh", storage.tokens!!.refreshToken)
            assertEquals("me", storage.tokens!!.displayName)
        }
    }

    @Test
    fun retriesOnceAfter401AndSignsOutWhenRefreshIsRejected() = runBlocking {
        TestServer().use { server ->
            server.route("/token") { 401 to "" }
            server.route("/x") { 401 to "" }
            server.start()

            val storage = MemoryStorage(GarminTokens("a", "r", "CID", expiresAt = now + 3600, displayName = "me"))
            val client = GarminClient(storage, apiBase = server.url, tokenUrl = "${server.url}/token", now = { now })
            val error = runCatching { client.get("/x") }.exceptionOrNull()

            assertTrue(error is GarminAuthException)
            assertNull(storage.tokens)
        }
    }

    @Test
    fun emptyResponseIsNull() = runBlocking {
        TestServer().use { server ->
            server.route("/empty") { 204 to "" }
            server.start()
            val storage = MemoryStorage(GarminTokens("a", "r", "CID", expiresAt = now + 3600, displayName = "me"))
            val client = GarminClient(storage, apiBase = server.url, tokenUrl = "${server.url}/token", now = { now })
            assertNull(client.get("/empty", mapOf("date" to "2026-11-09")))
        }
    }
}
