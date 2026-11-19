package com.example.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Provides cryptographic pairing-code generation, SHA-256 session fingerprinting,
 * HMAC-SHA256 webhook signing, and Android Keystore AES/GCM/NoPadding session encryption.
 */
class SessionCryptoManager(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val secureRandom = SecureRandom()

    fun generatePairingCode(): String {
        val part1 = buildString {
            append("HBP")
            append(PAIRING_ALPHABET[secureRandom.nextInt(PAIRING_ALPHABET.length)])
        }
        val part2 = buildString {
            repeat(4) {
                append(PAIRING_ALPHABET[secureRandom.nextInt(PAIRING_ALPHABET.length)])
            }
        }
        return "$part1-$part2"
    }

    fun generateSessionFingerprint(ownerUid: String, e164Number: String, pairingCode: String): String {
        val raw = "$ownerUid|$e164Number|$pairingCode|${System.nanoTime()}"
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }.take(32)
    }

    fun sha256Short(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }.take(24)
    }

    fun signWebhookPayload(secret: String, payload: String): String {
        val keyBytes = (if (secret.isBlank()) "honest-bot-pro-default-hmac" else secret).toByteArray(Charsets.UTF_8)
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(keyBytes, "HmacSHA256"))
        val raw = mac.doFinal(payload.toByteArray(Charsets.UTF_8))
        return "sha256=" + raw.joinToString("") { "%02x".format(it) }
    }

    fun storeEncryptedSessionToken(accountId: String, sessionToken: String): Boolean {
        return try {
            val secretKey = getOrCreateAesKey()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val encrypted = cipher.doFinal(sessionToken.toByteArray(Charsets.UTF_8))
            val combined = Base64.encodeToString(iv + encrypted, Base64.NO_WRAP)
            prefs.edit().putString("sess_$accountId", combined).apply()
            true
        } catch (_: Exception) {
            false
        }
    }

    fun readEncryptedSessionToken(accountId: String): String? {
        val encoded = prefs.getString("sess_$accountId", null) ?: return null
        return try {
            val raw = Base64.decode(encoded, Base64.NO_WRAP)
            if (raw.size <= GCM_IV_LENGTH) return null
            val iv = raw.copyOfRange(0, GCM_IV_LENGTH)
            val cipherBytes = raw.copyOfRange(GCM_IV_LENGTH, raw.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateAesKey(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(cipherBytes), Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    fun removeEncryptedSessionToken(accountId: String) {
        prefs.edit().remove("sess_$accountId").apply()
    }

    fun countStoredVaultSessions(): Int =
        prefs.all.keys.count { it.startsWith("sess_") }

    fun isHardwareKeystoreReady(): Boolean {
        return try {
            getOrCreateAesKey()
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun getOrCreateAesKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existingKey = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existingKey != null) return existingKey

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "honest_bot_pro_session_aes_key"
        const val PREFS_NAME = "honest_bot_pro_encrypted_vault"
        const val GCM_IV_LENGTH = 12
        const val PAIRING_ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"
    }
}
