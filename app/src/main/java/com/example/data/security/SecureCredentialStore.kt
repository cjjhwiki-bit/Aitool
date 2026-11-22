package com.example.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Context.secureDataStore: DataStore<Preferences> by preferencesDataStore(name = "polychat_secure_prefs")

data class AppPreferences(
    val silentAutoFailover: Boolean = false,
    val themeMode: String = "SYSTEM", // SYSTEM, LIGHT, DARK
    val dynamicColorEnabled: Boolean = true,
)

/**
 * Encrypts sensitive API keys and proxy credentials using Android Keystore AES-256-GCM
 * before persisting into Jetpack DataStore.
 */
class SecureCredentialStore(private val context: Context) {

    private val keyAlias = "polychat_master_aes_key_v1"

    private val silentAutoFailoverKey = booleanPreferencesKey("silent_auto_failover")
    private val themeModeKey = stringPreferencesKey("theme_mode")
    private val dynamicColorKey = booleanPreferencesKey("dynamic_color")
    private val proxyUsernameKey = stringPreferencesKey("enc_proxy_username")
    private val proxyPasswordKey = stringPreferencesKey("enc_proxy_password")

    private fun apiKeyPrefKey(providerId: String) = stringPreferencesKey("enc_api_key_$providerId")

    val preferencesFlow: Flow<AppPreferences> = context.secureDataStore.data.map { prefs ->
        AppPreferences(
            silentAutoFailover = prefs[silentAutoFailoverKey] ?: false,
            themeMode = prefs[themeModeKey] ?: "SYSTEM",
            dynamicColorEnabled = prefs[dynamicColorKey] ?: true,
        )
    }

    suspend fun setSilentAutoFailover(enabled: Boolean) {
        context.secureDataStore.edit { it[silentAutoFailoverKey] = enabled }
    }

    suspend fun setThemeMode(mode: String) {
        context.secureDataStore.edit { it[themeModeKey] = mode }
    }

    suspend fun setDynamicColorEnabled(enabled: Boolean) {
        context.secureDataStore.edit { it[dynamicColorKey] = enabled }
    }

    suspend fun saveApiKey(providerId: String, plainKey: String) {
        val trimmed = plainKey.trim()
        context.secureDataStore.edit { prefs ->
            if (trimmed.isEmpty()) {
                prefs.remove(apiKeyPrefKey(providerId))
            } else {
                prefs[apiKeyPrefKey(providerId)] = encrypt(trimmed)
            }
        }
    }

    suspend fun getApiKey(providerId: String): String {
        val encrypted = context.secureDataStore.data.first()[apiKeyPrefKey(providerId)] ?: return ""
        return decrypt(encrypted)
    }

    fun observeHasApiKey(providerId: String): Flow<Boolean> =
        context.secureDataStore.data.map { !it[apiKeyPrefKey(providerId)].isNullOrBlank() }

    suspend fun getMaskedApiKey(providerId: String): String {
        val key = getApiKey(providerId)
        if (key.isEmpty()) return ""
        return if (key.length <= 8) "••••" else "${key.take(4)}••••${key.takeLast(4)}"
    }

    suspend fun saveProxyCredentials(username: String, password: String) {
        context.secureDataStore.edit { prefs ->
            if (username.isBlank()) prefs.remove(proxyUsernameKey)
            else prefs[proxyUsernameKey] = encrypt(username.trim())

            if (password.isBlank()) prefs.remove(proxyPasswordKey)
            else prefs[proxyPasswordKey] = encrypt(password)
        }
    }

    suspend fun getProxyCredentials(): Pair<String, String> {
        val prefs = context.secureDataStore.data.first()
        val u = prefs[proxyUsernameKey]?.let { decrypt(it) }.orEmpty()
        val p = prefs[proxyPasswordKey]?.let { decrypt(it) }.orEmpty()
        return u to p
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = keyStore.getKey(keyAlias, null) as? SecretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val spec = KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    private fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val combined = ByteArray(1 + iv.size + encrypted.size)
        combined[0] = iv.size.toByte()
        System.arraycopy(iv, 0, combined, 1, iv.size)
        System.arraycopy(encrypted, 0, combined, 1 + iv.size, encrypted.size)
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): String {
        return try {
            val combined = Base64.decode(encoded, Base64.NO_WRAP)
            val ivLen = combined[0].toInt() and 0xFF
            val iv = combined.copyOfRange(1, 1 + ivLen)
            val cipherBytes = combined.copyOfRange(1 + ivLen, combined.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(cipherBytes), Charsets.UTF_8)
        } catch (_: Exception) {
            ""
        }
    }
}
