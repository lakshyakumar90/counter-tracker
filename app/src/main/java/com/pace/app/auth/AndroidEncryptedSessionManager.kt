package com.pace.app.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.user.UserSession
import java.security.KeyStore
import java.util.concurrent.CancellationException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Stores refresh/access tokens encrypted with an Android Keystore AES-GCM key. */
class AndroidEncryptedSessionManager(context: Context) : SessionManager {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val json = Json { encodeDefaults = true }

    @Suppress("ApplySharedPref")
    override suspend fun saveSession(session: UserSession) = withContext(Dispatchers.IO) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val plaintext = json.encodeToString(session).encodeToByteArray()
        val encrypted = cipher.doFinal(plaintext)
        val stored = cipher.iv + encrypted
        check(preferences.edit().putString(SESSION_KEY, Base64.encodeToString(stored, Base64.NO_WRAP)).commit()) {
            "Could not persist the encrypted account session"
        }
    }

    @Suppress("ApplySharedPref")
    override suspend fun loadSession(): UserSession? = withContext(Dispatchers.IO) {
        val stored = preferences.getString(SESSION_KEY, null) ?: return@withContext null
        try {
            val payload = Base64.decode(stored, Base64.NO_WRAP)
            require(payload.size > GCM_IV_BYTES)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), javax.crypto.spec.GCMParameterSpec(GCM_TAG_BITS, payload.copyOfRange(0, GCM_IV_BYTES)))
            json.decodeFromString(payload.copyOfRange(GCM_IV_BYTES, payload.size).let(cipher::doFinal).decodeToString())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            check(preferences.edit().remove(SESSION_KEY).commit()) { "Could not clear an unreadable account session" }
            null
        }
    }

    @Suppress("ApplySharedPref")
    override suspend fun deleteSession() = withContext(Dispatchers.IO) {
        check(preferences.edit().remove(SESSION_KEY).commit()) { "Could not clear the account session" }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val KEY_ALIAS = "pace.auth.session.v1"
        const val PREFERENCES_NAME = "pace_auth_session"
        const val SESSION_KEY = "encrypted_session"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_IV_BYTES = 12
        const val GCM_TAG_BITS = 128
    }
}
