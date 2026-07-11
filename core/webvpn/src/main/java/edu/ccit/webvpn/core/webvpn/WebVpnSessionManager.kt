package edu.ccit.webvpn.core.webvpn

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.security.KeyStore
import java.util.Base64
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.webVpnDataStore by preferencesDataStore(name = "webvpn_session")

interface WebVpnSessionStore {
    suspend fun clearLegacyToken()
    suspend fun saveCookies(cookies: List<String>)
    suspend fun getCookies(): List<String>
    suspend fun clearSession()
    suspend fun getOrCreateDeviceId(): String
}

interface WebVpnCredentialStore {
    suspend fun getSavedAccounts(): List<SavedWebVpnAccount>
    suspend fun getSavedPassword(username: String): String?
    suspend fun saveCredential(username: String, password: String)
    suspend fun deleteCredential(username: String)
}

interface AcademicCredentialStore {
    suspend fun getSavedAcademicAccounts(): List<SavedAcademicAccount>
    suspend fun getSavedAcademicPassword(username: String): String?
    suspend fun saveAcademicCredential(username: String, password: String)
    suspend fun deleteAcademicCredential(username: String)
}

data class SavedAcademicAccount(
    val username: String,
    val lastUsedAt: Long,
)

class WebVpnSessionManager(
    context: Context,
    private val json: Json = Json,
) : WebVpnSessionStore, WebVpnCredentialStore, AcademicCredentialStore {
    private val appContext = context.applicationContext
    private val secretCipher = WebVpnSecretCipher()
    private val tokenKey = stringPreferencesKey("token")
    private val cookiesKey = stringPreferencesKey("cookies")
    private val savedCredentialsKey = stringPreferencesKey("saved_credentials")
    private val savedAcademicCredentialsKey = stringPreferencesKey("saved_academic_credentials")
    private val deviceIdKey = stringPreferencesKey("device_id")
    private val credentialMutex = Mutex()

    override suspend fun clearLegacyToken() {
        appContext.webVpnDataStore.edit { it.remove(tokenKey) }
    }

    override suspend fun saveCookies(cookies: List<String>) {
        appContext.webVpnDataStore.edit { preferences ->
            if (cookies.isEmpty()) {
                preferences.remove(cookiesKey)
            } else {
                preferences[cookiesKey] = secretCipher.encrypt(json.encodeToString(cookies))
            }
        }
    }

    override suspend fun getCookies(): List<String> {
        val encoded = readSecret(cookiesKey) ?: return emptyList()
        return runCatching { json.decodeFromString<List<String>>(encoded) }
            .getOrElse {
                appContext.webVpnDataStore.edit { it.remove(cookiesKey) }
                emptyList()
            }
    }

    override suspend fun clearSession() {
        appContext.webVpnDataStore.edit {
            it.remove(tokenKey)
            it.remove(cookiesKey)
        }
    }

    override suspend fun getOrCreateDeviceId(): String {
        val preferences = appContext.webVpnDataStore.data.first()
        val existing = preferences[deviceIdKey]
        if (existing != null && WebVpnDeviceId.isCompatible(existing)) return existing

        // FingerprintJS visitorId used by the official frontend is 32 lowercase hex chars.
        // Migrate UUID values written by earlier App versions because auth/finish validates it.
        val next = WebVpnDeviceId.create()
        appContext.webVpnDataStore.edit { it[deviceIdKey] = next }
        return next
    }

    override suspend fun getSavedAccounts(): List<SavedWebVpnAccount> = credentialMutex.withLock {
        readCredentials()
            .sortedByDescending(StoredWebVpnCredential::lastUsedAt)
            .map { SavedWebVpnAccount(it.username, it.lastUsedAt) }
    }

    override suspend fun getSavedPassword(username: String): String? = credentialMutex.withLock {
        val normalizedUsername = username.trim()
        readCredentials().firstOrNull { it.username == normalizedUsername }?.password
    }

    override suspend fun saveCredential(username: String, password: String) {
        val normalizedUsername = username.trim()
        require(normalizedUsername.isNotBlank()) { "用户名不能为空" }
        require(password.isNotBlank()) { "密码不能为空" }

        credentialMutex.withLock {
            val updated = readCredentials()
                .filterNot { it.username == normalizedUsername }
                .plus(
                    StoredWebVpnCredential(
                        username = normalizedUsername,
                        password = password,
                        lastUsedAt = System.currentTimeMillis(),
                    ),
                )
                .sortedByDescending(StoredWebVpnCredential::lastUsedAt)
                .take(MaxSavedCredentials)
            writeCredentials(updated)
        }
    }

    override suspend fun deleteCredential(username: String) {
        credentialMutex.withLock {
            writeCredentials(readCredentials().filterNot { it.username == username.trim() })
        }
    }

    override suspend fun getSavedAcademicAccounts(): List<SavedAcademicAccount> = credentialMutex.withLock {
        readAcademicCredentials()
            .sortedByDescending(StoredAcademicCredential::lastUsedAt)
            .map { SavedAcademicAccount(it.username, it.lastUsedAt) }
    }

    override suspend fun getSavedAcademicPassword(username: String): String? = credentialMutex.withLock {
        val normalizedUsername = username.trim()
        readAcademicCredentials().firstOrNull { it.username == normalizedUsername }?.password
    }

    override suspend fun saveAcademicCredential(username: String, password: String) {
        val normalizedUsername = username.trim()
        require(normalizedUsername.isNotBlank()) { "学号不能为空" }
        require(password.isNotBlank()) { "教务系统密码不能为空" }

        credentialMutex.withLock {
            val updated = readAcademicCredentials()
                .filterNot { it.username == normalizedUsername }
                .plus(StoredAcademicCredential(normalizedUsername, password, System.currentTimeMillis()))
                .sortedByDescending(StoredAcademicCredential::lastUsedAt)
                .take(MaxSavedCredentials)
            writeAcademicCredentials(updated)
        }
    }

    override suspend fun deleteAcademicCredential(username: String) {
        credentialMutex.withLock {
            writeAcademicCredentials(
                readAcademicCredentials().filterNot { it.username == username.trim() },
            )
        }
    }

    private suspend fun readCredentials(): List<StoredWebVpnCredential> {
        val encoded = readSecret(savedCredentialsKey) ?: return emptyList()
        return runCatching { json.decodeFromString<List<StoredWebVpnCredential>>(encoded) }
            .getOrElse {
                appContext.webVpnDataStore.edit { it.remove(savedCredentialsKey) }
                emptyList()
            }
    }

    private suspend fun writeCredentials(credentials: List<StoredWebVpnCredential>) {
        appContext.webVpnDataStore.edit { preferences ->
            if (credentials.isEmpty()) {
                preferences.remove(savedCredentialsKey)
            } else {
                preferences[savedCredentialsKey] = secretCipher.encrypt(json.encodeToString(credentials))
            }
        }
    }

    private suspend fun readAcademicCredentials(): List<StoredAcademicCredential> {
        val encoded = readSecret(savedAcademicCredentialsKey) ?: return emptyList()
        return runCatching { json.decodeFromString<List<StoredAcademicCredential>>(encoded) }
            .getOrElse {
                appContext.webVpnDataStore.edit { it.remove(savedAcademicCredentialsKey) }
                emptyList()
            }
    }

    private suspend fun writeAcademicCredentials(credentials: List<StoredAcademicCredential>) {
        appContext.webVpnDataStore.edit { preferences ->
            if (credentials.isEmpty()) {
                preferences.remove(savedAcademicCredentialsKey)
            } else {
                preferences[savedAcademicCredentialsKey] = secretCipher.encrypt(json.encodeToString(credentials))
            }
        }
    }

    private suspend fun readSecret(key: Preferences.Key<String>): String? {
        val stored = appContext.webVpnDataStore.data.first()[key] ?: return null

        // Migrate values written by the initial prototype, which used plaintext DataStore.
        if (!stored.startsWith(WebVpnSecretCipher.Prefix)) {
            appContext.webVpnDataStore.edit { it[key] = secretCipher.encrypt(stored) }
            return stored
        }

        return runCatching { secretCipher.decrypt(stored) }
            .getOrElse {
                appContext.webVpnDataStore.edit { it.remove(key) }
                null
            }
    }

    private companion object {
        const val MaxSavedCredentials = 10
    }
}

internal object WebVpnDeviceId {
    private val CompatiblePattern = Regex("^[0-9a-f]{32}$")

    fun isCompatible(value: String): Boolean = CompatiblePattern.matches(value)

    fun create(): String = UUID.randomUUID().toString().replace("-", "")
}

@Serializable
private data class StoredWebVpnCredential(
    val username: String,
    val password: String,
    val lastUsedAt: Long,
)

@Serializable
private data class StoredAcademicCredential(
    val username: String,
    val password: String,
    val lastUsedAt: Long,
)

private class WebVpnSecretCipher {
    private val keyStore = KeyStore.getInstance(KeyStoreName).apply { load(null) }

    fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(Transformation)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return buildString {
            append(Prefix)
            append(Base64.getEncoder().encodeToString(cipher.iv))
            append(':')
            append(Base64.getEncoder().encodeToString(encrypted))
        }
    }

    fun decrypt(value: String): String {
        require(value.startsWith(Prefix)) { "未知的会话密文版本" }
        val parts = value.removePrefix(Prefix).split(':', limit = 2)
        require(parts.size == 2) { "会话密文格式错误" }

        val cipher = Cipher.getInstance(Transformation)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateKey(),
            GCMParameterSpec(128, Base64.getDecoder().decode(parts[0])),
        )
        return cipher.doFinal(Base64.getDecoder().decode(parts[1])).toString(Charsets.UTF_8)
    }

    private fun getOrCreateKey(): SecretKey {
        (keyStore.getKey(KeyAlias, null) as? SecretKey)?.let { return it }

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KeyStoreName).run {
            init(
                KeyGenParameterSpec.Builder(
                    KeyAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build(),
            )
            generateKey()
        }
    }

    companion object {
        const val Prefix = "v1:"
        private const val KeyStoreName = "AndroidKeyStore"
        private const val KeyAlias = "ccit_webvpn_session_v1"
        private const val Transformation = "AES/GCM/NoPadding"
    }
}
