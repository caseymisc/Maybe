package com.caseymisc.fitdash.data.garmin

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.Base64
import java.util.concurrent.TimeUnit

open class GarminException(message: String) : IOException(message)

/** The user needs to (re)connect their Garmin account. */
class GarminAuthException(message: String) : GarminException(message)

class GarminClient(
    private val storage: GarminTokenStorage,
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build(),
    private val apiBase: String = GarminAuth.CONNECT_API,
    private val tokenUrl: String = GarminAuth.DI_TOKEN_URL,
    private val now: () -> Long = { System.currentTimeMillis() / 1000 },
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val tokenLock = Mutex()

    suspend fun isSignedIn(): Boolean = storage.load() != null

    suspend fun signOut() = storage.clear()

    /** Exchanges the SSO service ticket for DI OAuth2 tokens and stores them. */
    suspend fun signInWithTicket(ticket: String, serviceUrl: String): GarminTokens {
        var lastStatus = 0
        for (clientId in GarminAuth.DI_CLIENT_IDS) {
            val form = FormBody.Builder()
                .add("client_id", clientId)
                .add("service_ticket", ticket)
                .add("grant_type", GarminAuth.DI_GRANT_TYPE)
                .add("service_url", serviceUrl)
                .build()
            val (code, body) = tokenPost(clientId, form, "application/json,text/html;q=0.9,*/*;q=0.8")
            if (code == 429) throw GarminException("Garmin is rate limiting sign-ins. Try again in a few minutes.")
            lastStatus = code
            if (code !in 200..299) continue
            val tokens = parseTokenResponse(body, clientId, previous = null) ?: continue
            val withProfile = tokens.withProfile()
            storage.save(withProfile)
            return withProfile
        }
        throw GarminAuthException("Garmin token exchange failed (HTTP $lastStatus). Please try signing in again.")
    }

    /** GET a connectapi path and return its JSON, or null for an empty response. */
    suspend fun get(path: String, params: Map<String, String> = emptyMap()): JsonElement? {
        val first = call(path, params, validTokens())
        if (first.code != 401) return first.parse(path)
        // Token rejected: refresh once and retry.
        val refreshed = tokenLock.withLock { refresh(storage.load() ?: throw notSignedIn()) }
        return call(path, params, refreshed).parse(path)
    }

    suspend fun displayName(): String =
        validTokens().displayName ?: throw GarminAuthException("Garmin profile is missing. Please sign in again.")

    private data class RawResponse(val code: Int, val body: String)

    private fun RawResponse.parse(path: String): JsonElement? = when {
        code == 204 || (code in 200..299 && body.isBlank()) -> null
        code in 200..299 -> json.parseToJsonElement(body)
        code == 401 -> throw GarminAuthException("Garmin session expired. Please sign in again.")
        code == 403 -> throw GarminException("Garmin refused $path (HTTP 403). It may be blocking this network.")
        code == 429 -> throw GarminException("Garmin is rate limiting requests. Try again later.")
        else -> throw GarminException("Garmin API error HTTP $code for $path")
    }

    private suspend fun call(path: String, params: Map<String, String>, tokens: GarminTokens): RawResponse =
        withContext(Dispatchers.IO) {
            val url = apiBase.toHttpUrl().newBuilder()
                .addPathSegments(path.trimStart('/'))
                .apply { params.forEach { (k, v) -> addQueryParameter(k, v) } }
                .build()
            val request = Request.Builder()
                .url(url)
                .apply { GarminAuth.NATIVE_HEADERS.forEach { (k, v) -> header(k, v) } }
                .header("Authorization", "Bearer ${tokens.accessToken}")
                .header("Accept", "application/json")
                .build()
            http.newCall(request).execute().use { it.toRaw() }
        }

    private suspend fun validTokens(): GarminTokens = tokenLock.withLock {
        val tokens = storage.load() ?: throw notSignedIn()
        if (tokens.expiresAt - now() > REFRESH_MARGIN_S) tokens else refresh(tokens)
    }

    /** Must be called with [tokenLock] held. */
    private suspend fun refresh(tokens: GarminTokens): GarminTokens {
        val refreshToken = tokens.refreshToken
            ?: throw GarminAuthException("Garmin session expired. Please sign in again.")
        val form = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("client_id", tokens.clientId)
            .add("refresh_token", refreshToken)
            .build()
        val (code, body) = tokenPost(tokens.clientId, form, "application/json")
        if (code == 400 || code == 401) {
            storage.clear()
            throw GarminAuthException("Garmin session expired. Please sign in again.")
        }
        if (code !in 200..299) throw GarminException("Garmin token refresh failed (HTTP $code).")
        val refreshed = parseTokenResponse(body, tokens.clientId, previous = tokens)
            ?: throw GarminException("Garmin token refresh returned an unexpected response.")
        storage.save(refreshed)
        return refreshed
    }

    private suspend fun tokenPost(clientId: String, form: FormBody, accept: String): RawResponse =
        withContext(Dispatchers.IO) {
            val basic = Base64.getEncoder().encodeToString("$clientId:".toByteArray())
            val request = Request.Builder()
                .url(tokenUrl)
                .apply { GarminAuth.NATIVE_HEADERS.forEach { (k, v) -> header(k, v) } }
                .header("Authorization", "Basic $basic")
                .header("Accept", accept)
                .header("Cache-Control", "no-cache")
                .post(form)
                .build()
            http.newCall(request).execute().use { it.toRaw() }
        }

    private fun Response.toRaw() = RawResponse(code, body?.string().orEmpty())

    private fun parseTokenResponse(body: String, clientId: String, previous: GarminTokens?): GarminTokens? {
        val obj = runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull() ?: return null
        val access = (obj["access_token"] as? JsonPrimitive)?.contentOrNull ?: return null
        val refresh = (obj["refresh_token"] as? JsonPrimitive)?.contentOrNull ?: previous?.refreshToken
        val claims = decodeJwtClaims(access)
        val exp = (claims?.get("exp") as? JsonPrimitive)?.longOrNull
            ?: (obj["expires_in"] as? JsonPrimitive)?.longOrNull?.let { now() + it }
            ?: (now() + 3600)
        val jwtClient = (claims?.get("client_id") as? JsonPrimitive)?.contentOrNull
        return GarminTokens(
            accessToken = access,
            refreshToken = refresh,
            clientId = jwtClient ?: clientId,
            expiresAt = exp,
            displayName = previous?.displayName,
            fullName = previous?.fullName,
        )
    }

    private fun decodeJwtClaims(token: String): JsonObject? = runCatching {
        val payload = token.split('.')[1]
        val decoded = String(Base64.getUrlDecoder().decode(payload.padEnd((payload.length + 3) / 4 * 4, '=')))
        json.parseToJsonElement(decoded) as? JsonObject
    }.getOrNull()

    /** Most API paths need the user's display name; fetch it once at sign-in. */
    private suspend fun GarminTokens.withProfile(): GarminTokens {
        val response = call("/userprofile-service/socialProfile", emptyMap(), this)
        val profile = response.parse("socialProfile") as? JsonObject
        val name = (profile?.get("displayName") as? JsonPrimitive)?.contentOrNull
            ?: throw GarminException("Signed in, but couldn't read your Garmin profile.")
        return copy(displayName = name, fullName = (profile?.get("fullName") as? JsonPrimitive)?.contentOrNull)
    }

    private fun notSignedIn() = GarminAuthException("Not signed in to Garmin.")

    private companion object {
        const val REFRESH_MARGIN_S = 300
    }
}
