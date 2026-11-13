package com.stevenpg.howlongtocook.data.settings

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class SecretKind(val key: String) {
    S3_SECRET_ACCESS_KEY("s3_secret_access_key"),
    SERVER_TOKEN("server_token"),
}

/** Stores credentials; abstracted so tests can use an in-memory fake. */
interface SecretStore {
    suspend fun get(kind: SecretKind): String?
    suspend fun put(kind: SecretKind, value: String?)
    suspend fun has(kind: SecretKind): Boolean = !get(kind).isNullOrEmpty()
}

private val Context.secretsStore: DataStore<Preferences> by preferencesDataStore(name = "secrets")

/**
 * Encrypts values with an AES-256-GCM key that never leaves the Android Keystore.
 * The backing file is excluded from backups (res/xml/data_extraction_rules.xml):
 * the Keystore key would not be restored, so the ciphertext would be useless anyway.
 */
@Singleton
class KeystoreSecretStore @Inject constructor(
    @ApplicationContext context: Context,
) : SecretStore {
    private val store = context.secretsStore

    override suspend fun get(kind: SecretKind): String? {
        val blob = store.data.map { it[stringPreferencesKey(kind.key)] }.first() ?: return null
        return runCatching { decrypt(blob) }.getOrNull()
    }

    override suspend fun put(kind: SecretKind, value: String?) {
        store.edit { prefs ->
            val k = stringPreferencesKey(kind.key)
            if (value.isNullOrEmpty()) prefs.remove(k) else prefs[k] = encrypt(value)
        }
    }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val iv = cipher.iv
        val ct = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(byteArrayOf(iv.size.toByte()) + iv + ct, Base64.NO_WRAP)
    }

    private fun decrypt(blob: String): String {
        val bytes = Base64.decode(blob, Base64.NO_WRAP)
        val ivLen = bytes[0].toInt()
        val iv = bytes.copyOfRange(1, 1 + ivLen)
        val ct = bytes.copyOfRange(1 + ivLen, bytes.size)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        }
        return String(cipher.doFinal(ct), Charsets.UTF_8)
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "howlongtocook_secrets_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
