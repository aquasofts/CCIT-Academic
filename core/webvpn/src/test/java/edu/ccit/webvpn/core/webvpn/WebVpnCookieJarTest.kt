package edu.ccit.webvpn.core.webvpn

import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebVpnCookieJarTest {
    private val baseUrl = "https://webvpn.ccit.edu.cn/".toHttpUrl()

    @Test
    fun saveFromResponse_mergesCookiesInsteadOfReplacingHostState() {
        val jar = WebVpnCookieJar()
        jar.saveFromResponse(baseUrl, listOf(cookie("webvpn-token", "token")))
        jar.saveFromResponse(baseUrl, listOf(cookie("webvpn-jwt", "jwt")))

        assertEquals(
            setOf("webvpn-token", "webvpn-jwt"),
            jar.loadForRequest(baseUrl).map { it.name }.toSet(),
        )
    }

    @Test
    fun loadForRequest_respectsCookiePathAndExpiry() {
        var now = 1_000L
        val jar = WebVpnCookieJar(clock = { now })
        jar.saveFromResponse(
            baseUrl,
            listOf(
                cookie("root", "1", path = "/", expiresAt = 10_000L),
                cookie("auth", "2", path = "/auth", expiresAt = 10_000L),
            ),
        )

        assertEquals(
            listOf("root"),
            jar.loadForRequest("https://webvpn.ccit.edu.cn/site-nav/home".toHttpUrl())
                .map { it.name },
        )

        now = 11_000L
        assertTrue(jar.loadForRequest(baseUrl).isEmpty())
    }

    @Test
    fun snapshotAndRestore_preservesUsableCookies() {
        val original = WebVpnCookieJar()
        original.saveFromResponse(baseUrl, listOf(cookie("webvpn-token", "secret")))

        val restored = WebVpnCookieJar()
        restored.restore(baseUrl, original.snapshot())

        assertEquals("secret", restored.loadForRequest(baseUrl).single().value)
    }

    @Test
    fun snapshotAndRestore_preservesHostOnlyCookieForProxiedSubdomain() {
        val academicUrl =
            "https://http-10-198-47-148-8080.webvpn.ccit.edu.cn/jsxsd/".toHttpUrl()
        val academicCookie = Cookie.Builder()
            .name("JSESSIONID")
            .value("academic-session")
            .hostOnlyDomain(academicUrl.host)
            .path("/jsxsd")
            .secure()
            .httpOnly()
            .build()
        val original = WebVpnCookieJar().apply {
            saveFromResponse(academicUrl, listOf(academicCookie))
        }

        val restored = WebVpnCookieJar().apply {
            restore(baseUrl, original.snapshot())
        }

        assertEquals(
            "academic-session",
            restored.loadForRequest(academicUrl).single().value,
        )
        assertTrue(restored.loadForRequest(baseUrl).isEmpty())
    }

    private fun cookie(
        name: String,
        value: String,
        path: String = "/",
        expiresAt: Long = Long.MAX_VALUE,
    ): Cookie = Cookie.Builder()
        .name(name)
        .value(value)
        .hostOnlyDomain(baseUrl.host)
        .path(path)
        .expiresAt(expiresAt)
        .secure()
        .httpOnly()
        .build()
}
