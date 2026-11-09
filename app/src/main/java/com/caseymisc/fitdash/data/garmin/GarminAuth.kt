package com.caseymisc.fitdash.data.garmin

import kotlinx.serialization.Serializable

/**
 * Identity constants for talking to Garmin Connect the way the official Android app does.
 *
 * Garmin has no public API for personal use. These values mirror the maintained
 * `python-garminconnect` library (0.3.x, MIT). If Garmin changes its login again, this file
 * and [GarminClient] are the places to update; compare against the latest release of that
 * library.
 */
object GarminAuth {
    const val SSO = "https://sso.garmin.com"
    const val CONNECT_API = "https://connectapi.garmin.com"
    const val DI_TOKEN_URL = "https://diauth.garmin.com/di-oauth2-service/oauth/token"
    const val DI_GRANT_TYPE = "https://connectapi.garmin.com/di-oauth2-service/oauth/grant/service_ticket"

    /** Client IDs the DI token endpoint accepts, newest first. */
    val DI_CLIENT_IDS = listOf(
        "GARMIN_CONNECT_MOBILE_ANDROID_DI_2025Q2",
        "GARMIN_CONNECT_MOBILE_ANDROID_DI_2024Q4",
        "GARMIN_CONNECT_MOBILE_ANDROID_DI",
        "GARMIN_CONNECT_MOBILE_IOS_DI",
    )

    /** Headers the official Android app sends to the native API. */
    val NATIVE_HEADERS = mapOf(
        "User-Agent" to "GCM-Android-5.23",
        "X-Garmin-User-Agent" to
            "com.garmin.android.apps.connectmobile/5.23; ; Google/sdk_gphone64_arm64/google; Android/33; Dalvik/2.1.0",
        "X-Garmin-Paired-App-Version" to "10861",
        "X-Garmin-Client-Platform" to "Android",
        "X-App-Ver" to "10861",
        "X-Lang" to "en",
        "X-GCExperience" to "GC5",
        "Accept-Language" to "en-US,en;q=0.9",
    )

    /** A sign-in page and the service URL Garmin redirects to with `?ticket=ST-...`. */
    enum class LoginFlow(val signInUrl: String, val serviceUrl: String) {
        /** The flow the Garmin Connect Android app runs in its own web view. */
        MOBILE(
            signInUrl = "$SSO/mobile/sso/en_US/sign-in?clientId=GCM_ANDROID_DARK" +
                "&service=https%3A%2F%2Fmobile.integration.garmin.com%2Fgcm%2Fandroid",
            serviceUrl = "https://mobile.integration.garmin.com/gcm/android",
        ),

        /** The connect.garmin.com website sign-in, as a fallback. */
        PORTAL(
            signInUrl = "$SSO/portal/sso/en-US/sign-in?clientId=GarminConnect" +
                "&service=https%3A%2F%2Fconnect.garmin.com%2Fapp",
            serviceUrl = "https://connect.garmin.com/app",
        ),
    }
}

@Serializable
data class GarminTokens(
    val accessToken: String,
    val refreshToken: String? = null,
    val clientId: String,
    /** Access token expiry, epoch seconds (from the JWT `exp` claim). */
    val expiresAt: Long,
    val displayName: String? = null,
    val fullName: String? = null,
)

/** Where tokens are persisted (encrypted on device; in-memory in tests). */
interface GarminTokenStorage {
    suspend fun load(): GarminTokens?
    suspend fun save(tokens: GarminTokens)
    suspend fun clear()
}
