package edu.ccit.webvpn.core.webvpn

import java.util.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

class WebVpnCookieJar(
    private val clock: () -> Long = System::currentTimeMillis,
) : CookieJar {
    private val cookies = LinkedHashMap<CookieKey, Cookie>()

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        removeExpired()
        cookies.forEach { cookie ->
            val key = cookie.key()
            if (cookie.expiresAt <= clock()) {
                this.cookies.remove(key)
            } else {
                this.cookies[key] = cookie
            }
        }
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        removeExpired()
        return cookies.values.filter { it.matches(url) }
    }

    @Synchronized
    fun snapshot(): List<String> {
        removeExpired()
        return cookies.values.map { cookie ->
            val payload = Json.encodeToString(PersistedCookie.from(cookie))
            PersistedCookiePrefix + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.toByteArray(Charsets.UTF_8))
        }
    }

    @Synchronized
    fun restore(url: HttpUrl, serializedCookies: List<String>) {
        cookies.clear()
        serializedCookies
            .mapNotNull { serialized ->
                if (serialized.startsWith(PersistedCookiePrefix)) {
                    runCatching {
                        val json = Base64.getUrlDecoder()
                            .decode(serialized.removePrefix(PersistedCookiePrefix))
                            .toString(Charsets.UTF_8)
                        Json.decodeFromString<PersistedCookie>(json).toCookie()
                    }.getOrNull()
                } else {
                    // Compatibility with the initial Cookie.toString() snapshot format.
                    Cookie.parse(url, serialized)
                }
            }
            .filter { it.expiresAt > clock() }
            .forEach { cookies[it.key()] = it }
    }

    @Synchronized
    fun clear() {
        cookies.clear()
    }

    @Synchronized
    fun clearHost(host: String) {
        cookies.entries.removeAll { it.value.domain == host }
    }

    private fun removeExpired() {
        val now = clock()
        cookies.entries.removeAll { it.value.expiresAt <= now }
    }

    private fun Cookie.key() = CookieKey(name, domain, path)

    private data class CookieKey(
        val name: String,
        val domain: String,
        val path: String,
    )

    @Serializable
    private data class PersistedCookie(
        val name: String,
        val value: String,
        val expiresAt: Long,
        val domain: String,
        val path: String,
        val secure: Boolean,
        val httpOnly: Boolean,
        val hostOnly: Boolean,
    ) {
        fun toCookie(): Cookie = Cookie.Builder()
            .name(name)
            .value(value)
            .apply {
                if (hostOnly) hostOnlyDomain(domain) else domain(domain)
                if (secure) secure()
                if (httpOnly) httpOnly()
            }
            .path(path)
            .expiresAt(expiresAt)
            .build()

        companion object {
            fun from(cookie: Cookie) = PersistedCookie(
                name = cookie.name,
                value = cookie.value,
                expiresAt = cookie.expiresAt,
                domain = cookie.domain,
                path = cookie.path,
                secure = cookie.secure,
                httpOnly = cookie.httpOnly,
                hostOnly = cookie.hostOnly,
            )
        }
    }

    private companion object {
        const val PersistedCookiePrefix = "v2:"
    }
}
