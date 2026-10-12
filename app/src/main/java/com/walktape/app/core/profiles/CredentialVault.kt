package com.walktape.app.core.profiles

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Non-exportable per-profile keys; independent from the optional UI lock. */
class CredentialVault {
    private fun store() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    private fun alias(id: String): String {
        require(id.matches(Regex("[a-f0-9-]{36}")))
        return "walktape.profile.$id"
    }

    fun encrypt(id: String, password: String): String {
        val keyAlias = alias(id)
        check(!store().containsAlias(keyAlias))
        val key = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(keyAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true).build())
        }.generateKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, key)
            updateAAD(id.toByteArray(Charsets.UTF_8))
        }
        return Base64.encodeToString(cipher.iv + cipher.doFinal(password.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }

    fun decrypt(id: String, encrypted: String): String {
        val key = store().getKey(alias(id), null) as? SecretKey
            ?: throw java.security.GeneralSecurityException("Protected key unavailable")
        val bytes = Base64.decode(encrypted, Base64.NO_WRAP)
        require(bytes.size >= 28)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            updateAAD(id.toByteArray(Charsets.UTF_8))
        }
        return cipher.doFinal(bytes.copyOfRange(12, bytes.size)).toString(Charsets.UTF_8)
    }

    fun delete(id: String) { if (id.isNotEmpty()) store().deleteEntry(alias(id)) }
}
