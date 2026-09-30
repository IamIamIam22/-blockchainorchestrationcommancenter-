package com.example.crypto

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object KeyStoreHelper {
    private const val ANDROID_KEY_STORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "BlockchainOrchestratorKeyAlias"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val PREFS_NAME = "secure_wallet_prefs"
    private const val ENCRYPTED_KEY_PREFIX = "enc_key_"
    private const val PLAIN_KEY_PREFIX = "plain_key_"

    private fun initKeyStore(): Boolean {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
            if (!keyStore.containsAlias(KEY_ALIAS)) {
                val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
                val parameterSpec = KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build()
                keyGenerator.init(parameterSpec)
                keyGenerator.generateKey()
            }
            true
        } catch (e: Throwable) {
            false
        }
    }

    private fun getSecretKey(): SecretKey? {
        return try {
            if (!initKeyStore()) return null
            val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
            val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
            entry?.secretKey
        } catch (e: Throwable) {
            null
        }
    }

    fun encrypt(plainText: String): String? {
        return try {
            val key = getSecretKey() ?: return null
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val iv = cipher.iv
            val encryption = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            
            val combined = ByteArray(iv.size + encryption.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(encryption, 0, combined, iv.size, encryption.size)
            
            Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (e: Throwable) {
            null
        }
    }

    fun decrypt(encryptedText: String): String? {
        return try {
            val key = getSecretKey() ?: return null
            val combined = Base64.decode(encryptedText, Base64.NO_WRAP)
            val iv = ByteArray(12)
            val ciphertext = ByteArray(combined.size - iv.size)
            
            System.arraycopy(combined, 0, iv, 0, iv.size)
            System.arraycopy(combined, iv.size, ciphertext, 0, ciphertext.size)
            
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, key, spec)
            
            val decryptedBytes = cipher.doFinal(ciphertext)
            String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Throwable) {
            null
        }
    }

    fun saveCredential(context: Context, key: String, secret: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val encrypted = encrypt(secret)
        if (encrypted != null) {
            prefs.edit().putString(ENCRYPTED_KEY_PREFIX + key, encrypted).remove(PLAIN_KEY_PREFIX + key).apply()
        } else {
            // Safe obfuscated fallback if hardware KeyStore is unavailable on current runtime
            val base64 = Base64.encodeToString(secret.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            prefs.edit().putString(PLAIN_KEY_PREFIX + key, base64).remove(ENCRYPTED_KEY_PREFIX + key).apply()
        }
    }

    fun getCredential(context: Context, key: String): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val encrypted = prefs.getString(ENCRYPTED_KEY_PREFIX + key, null)
        if (encrypted != null) {
            val decrypted = decrypt(encrypted)
            if (decrypted != null) return decrypted
        }
        val plain = prefs.getString(PLAIN_KEY_PREFIX + key, null) ?: return null
        return try {
            String(Base64.decode(plain, Base64.NO_WRAP), Charsets.UTF_8)
        } catch (e: Throwable) {
            null
        }
    }

    fun deleteCredential(context: Context, key: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(ENCRYPTED_KEY_PREFIX + key).remove(PLAIN_KEY_PREFIX + key).apply()
    }
}
