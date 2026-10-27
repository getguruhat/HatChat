package dev.openinbox.app.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores the Instagram access token on the phone only.
 *
 * The token is encrypted with an AES key that lives in the Android Keystore
 * (hardware-backed on most phones, and the key can never be exported). Only the
 * ciphertext is written to SharedPreferences, and backups are disabled in the
 * manifest, so the token never leaves the device.
 */
class TokenStore(context: Context) {

    private val prefs = context.getSharedPreferences("secure_store", Context.MODE_PRIVATE)

    fun saveToken(token: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        prefs.edit()
            .putString(KEY_TOKEN, Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putString(KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putLong(KEY_SAVED_AT, System.currentTimeMillis())
            .apply()
    }

    fun loadToken(): String? {
        val data = prefs.getString(KEY_TOKEN, null) ?: return null
        val iv = prefs.getString(KEY_IV, null) ?: return null
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKey(),
                GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)),
            )
            String(cipher.doFinal(Base64.decode(data, Base64.NO_WRAP)), Charsets.UTF_8)
        } catch (e: Exception) {
            // Key was wiped (e.g. lock screen reset) — the stored token is unreadable.
            clear()
            null
        }
    }

    /** When the token was last saved or refreshed, in epoch millis (0 if never). */
    fun savedAt(): Long = prefs.getLong(KEY_SAVED_AT, 0L)

    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "openinbox_token_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_TOKEN = "ig_token"
        const val KEY_IV = "ig_token_iv"
        const val KEY_SAVED_AT = "ig_token_saved_at"
    }
}
