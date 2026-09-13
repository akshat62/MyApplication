package com.reelbot.mobile.instagram

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores the Instagram/Facebook long-lived access token and connected account info,
 * encrypted at rest with a key that never leaves the Android Keystore. This is the one
 * component of V2 the audit found genuinely correct (AndroidKeyStore + AES/GCM, not
 * plaintext) — rebuilt here to fit V3's OAuth flow instead of a manual token-paste field.
 */
class TokenVault(context: Context) {

    private val prefs = context.getSharedPreferences("ig_auth", Context.MODE_PRIVATE)
    private val alias = "reelbot_ig_token_key"

    fun saveSession(session: MetaSession) {
        prefs.edit {
            putString("ig_user_id", session.igUserId)
            putString("ig_username", session.igUsername)
            putString("fb_page_id", session.facebookPageId)
            putLong("expires_at", session.expiresAtEpochMs)
        }
        encryptAndStore("token", session.accessToken)
    }

    fun clear() {
        prefs.edit { clear() }
    }

    fun currentSession(): MetaSession? {
        val igUserId = prefs.getString("ig_user_id", null) ?: return null
        val token = decrypt("token") ?: return null
        return MetaSession(
            accessToken = token,
            igUserId = igUserId,
            igUsername = prefs.getString("ig_username", "") ?: "",
            facebookPageId = prefs.getString("fb_page_id", "") ?: "",
            expiresAtEpochMs = prefs.getLong("expires_at", 0L)
        )
    }

    fun isConnected(): Boolean = currentSession() != null
    fun isExpired(): Boolean = currentSession()?.let { it.expiresAtEpochMs in 1..System.currentTimeMillis() } ?: true

    private fun encryptAndStore(key: String, plaintext: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        prefs.edit {
            putString("$key.data", Base64.encodeToString(encrypted, Base64.NO_WRAP))
            putString("$key.iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
        }
    }

    private fun decrypt(key: String): String? {
        return try {
            val data = prefs.getString("$key.data", null) ?: return null
            val iv = prefs.getString("$key.iv", null) ?: return null
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
            String(cipher.doFinal(Base64.decode(data, Base64.NO_WRAP)), Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }

    companion object {
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}

data class MetaSession(
    val accessToken: String,
    val igUserId: String,
    val igUsername: String,
    val facebookPageId: String,
    val expiresAtEpochMs: Long
)
