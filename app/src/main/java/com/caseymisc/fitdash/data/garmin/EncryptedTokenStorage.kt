package com.caseymisc.fitdash.data.garmin

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.caseymisc.fitdash.data.settings.dataStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Stores Garmin tokens in DataStore, encrypted with an AES key held in the Android Keystore. */
class EncryptedTokenStorage(private val context: Context) : GarminTokenStorage {
    private val key = stringPreferencesKey("garmin_tokens")
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun load(): GarminTokens? {
        val stored = context.dataStore.data.first()[key] ?: return null
        return runCatching {
            json.decodeFromString(GarminTokens.serializer(), decrypt(stored))
        }.getOrNull()
    }

    override suspend fun save(tokens: GarminTokens) {
        val encrypted = encrypt(json.encodeToString(GarminTokens.serializer(), tokens))
        context.dataStore.edit { it[key] = encrypted }
    }

    override suspend fun clear() {
        context.dataStore.edit { it.remove(key) }
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val bytes = cipher.iv + cipher.doFinal(plain.toByteArray())
        return Base64.getEncoder().encodeToString(bytes)
    }

    private fun decrypt(stored: String): String {
        val bytes = Base64.getDecoder().decode(stored)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, bytes, 0, IV_SIZE))
        return String(cipher.doFinal(bytes, IV_SIZE, bytes.size - IV_SIZE))
    }

    private companion object {
        const val ALIAS = "fitdash_garmin_tokens"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
    }
}
