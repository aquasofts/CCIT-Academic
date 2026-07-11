package edu.ccit.webvpn.core.webvpn

import java.io.IOException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class WebVpnAuthRepositoryTest {
    @Test
    fun login_acceptsCurrentCookieOnlySuccessResponse() = runBlocking {
        val api = FakeWebVpnApi(
            finishAuthResponse = ApiEnvelope(code = 0, message = "ok", data = null),
            userInfoResponse = ApiEnvelope(
                code = 0,
                message = "ok",
                data = UserInfo(userId = 7, username = "student"),
            ),
        )
        val sessionStore = FakeSessionStore()
        val repository = WebVpnAuthRepository(api, sessionStore, WebVpnCookieJar())

        val result = repository.login(
            username = "student",
            password = "password",
            captchaId = "captcha-id",
            code = "1234",
        )

        assertEquals(7L, result.userInfo.userId)
        assertEquals(1, sessionStore.clearLegacyTokenCount)
        val request = requireNotNull(api.lastFinishAuthRequest)
        val payload = Json.decodeFromString<FinishAuthPayload>(request.data)
        assertEquals("student", payload.username)
        assertEquals("captcha-id", payload.captchaId)
        assertTrue(payload.password != "password")
        assertTrue(payload.deviceId.matches(Regex("^[0-9a-f]{32}$")))
    }

    @Test
    fun login_clearsCredentialsWhenUserInfoValidationFails() = runBlocking {
        val api = FakeWebVpnApi(
            finishAuthResponse = ApiEnvelope(
                code = 0,
                message = "ok",
                data = FinishAuthData(token = "legacy-token"),
            ),
            userInfoResponse = ApiEnvelope(code = 401, message = "未授权", data = null),
        )
        val sessionStore = FakeSessionStore()
        val repository = WebVpnAuthRepository(api, sessionStore, WebVpnCookieJar())

        val error = runCatching {
            repository.login("student", "password", "captcha-id", "1234")
        }.exceptionOrNull()

        assertTrue(error is WebVpnApiException)
        assertTrue(!sessionStore.legacyTokenPresent)
        assertTrue(sessionStore.cookies.isEmpty())
        assertTrue(sessionStore.clearCount >= 1)
    }

    @Test
    fun restoreSession_validatesEncryptedStoreCredentialsThroughUserInfo() = runBlocking {
        val api = FakeWebVpnApi(
            userInfoResponse = ApiEnvelope(
                code = 0,
                message = "ok",
                data = UserInfo(userId = 9, nickname = "同学"),
            ),
        )
        val sessionStore = FakeSessionStore().apply {
            cookies = listOf("webvpn-token=value; path=/; secure; httponly")
        }
        val repository = WebVpnAuthRepository(api, sessionStore, WebVpnCookieJar())

        val restored = repository.restoreSession()

        assertEquals(9L, restored?.userInfo?.userId)
        assertEquals(1, api.userInfoCalls)
    }

    @Test
    fun restoreSession_removesRejectedCredentials() = runBlocking {
        val api = FakeWebVpnApi(
            userInfoResponse = ApiEnvelope(code = 401, message = "未授权", data = null),
        )
        val sessionStore = FakeSessionStore().apply { legacyTokenPresent = true }
        val repository = WebVpnAuthRepository(api, sessionStore, WebVpnCookieJar())

        assertNull(repository.restoreSession())
        assertTrue(!sessionStore.legacyTokenPresent)
        assertEquals(0, api.userInfoCalls)
    }

    @Test
    fun restoreSession_removesLegacyTokenRejectedWithHttp400() = runBlocking {
        val errorBody = """{"code":400,"message":"认证信息格式错误","data":null}"""
            .toResponseBody("application/json".toMediaType())
        val api = FakeWebVpnApi(
            userInfoError = HttpException(Response.error<ApiEnvelope<UserInfo>>(400, errorBody)),
        )
        val sessionStore = FakeSessionStore().apply {
            legacyTokenPresent = true
            cookies = listOf("webvpn-token=value; path=/; secure; httponly")
        }
        val repository = WebVpnAuthRepository(api, sessionStore, WebVpnCookieJar())

        assertNull(repository.restoreSession())
        assertTrue(!sessionStore.legacyTokenPresent)
        assertTrue(sessionStore.clearCount > 0)
    }

    @Test
    fun revalidateSession_doesNotClearCredentialsOnTemporaryNetworkFailure() = runBlocking {
        val api = FakeWebVpnApi(userInfoError = IOException("offline"))
        val sessionStore = FakeSessionStore().apply {
            cookies = listOf("webvpn-token=value; path=/; secure; httponly")
        }
        val repository = WebVpnAuthRepository(api, sessionStore, WebVpnCookieJar())

        val error = runCatching { repository.revalidateSession() }.exceptionOrNull()

        assertTrue(error is WebVpnApiException)
        assertTrue(sessionStore.cookies.isNotEmpty())
        assertEquals(0, sessionStore.clearCount)
    }

    @Test
    fun loginHttp400_surfacesServerJsonMessage() = runBlocking {
        val errorBody = """{"code":400,"message":"设备标识格式错误","data":null}"""
            .toResponseBody("application/json".toMediaType())
        val api = FakeWebVpnApi(
            finishAuthError = HttpException(
                Response.error<ApiEnvelope<FinishAuthData>>(400, errorBody),
            ),
        )
        val repository = WebVpnAuthRepository(api, FakeSessionStore(), WebVpnCookieJar())

        val error = runCatching {
            repository.login("student", "password", "captcha-id", "1234")
        }.exceptionOrNull()

        assertEquals("auth/finish：设备标识格式错误", error?.message)
    }

    @Test
    fun login_preservesCaptchaCookieUntilFinishCompletes() = runBlocking {
        val baseUrl = "https://webvpn.ccit.edu.cn/".toHttpUrl()
        val captchaCookie = Cookie.Builder()
            .name("captcha-session")
            .value("active")
            .hostOnlyDomain(baseUrl.host)
            .path("/")
            .build()
        val cookieJar = WebVpnCookieJar().apply {
            saveFromResponse(baseUrl, listOf(captchaCookie))
        }
        val sessionStore = FakeSessionStore()
        val repository = WebVpnAuthRepository(FakeWebVpnApi(), sessionStore, cookieJar)

        repository.login("student", "password", "captcha-id", "1234")

        assertEquals("active", cookieJar.loadForRequest(baseUrl).single().value)
        assertEquals(0, sessionStore.clearCount)
    }

    @Test
    fun api_usesCurrentFinishEndpoint() {
        val method = WebVpnApi::class.java.declaredMethods.single { it.name == "finishAuth" }
        val post = requireNotNull(method.getAnnotation(retrofit2.http.POST::class.java))
        val headers = requireNotNull(method.getAnnotation(retrofit2.http.Headers::class.java))
            .value
            .toSet()

        assertEquals("api/access/auth/finish", post.value)
        assertTrue("Origin: https://webvpn.ccit.edu.cn" in headers)
        assertTrue("Content-Type: application/json" in headers)
    }

    @Test
    fun browserRequest_neverSendsAuthorizationAndMatchesHarHeaders() {
        val original = Request.Builder()
            .url("https://webvpn.ccit.edu.cn/api/access/user/info")
            .header("Authorization", "Bearer stale-token")
            .build()

        val request = WebVpnNetwork.asBrowserRequest(original)

        assertNull(request.header("Authorization"))
        assertEquals("application/json, text/plain, */*", request.header("Accept"))
        assertEquals("zh-CN,zh;q=0.9", request.header("Accept-Language"))
        assertEquals("cors", request.header("Sec-Fetch-Mode"))
        assertTrue(request.header("User-Agent").orEmpty().startsWith("Mozilla/5.0"))
    }
}

private class FakeWebVpnApi(
    private val finishAuthResponse: ApiEnvelope<FinishAuthData> = ApiEnvelope(0, "ok"),
    private val userInfoResponse: ApiEnvelope<UserInfo> = ApiEnvelope(
        0,
        "ok",
        UserInfo(userId = 1),
    ),
    private val userInfoError: Throwable? = null,
    private val finishAuthError: Throwable? = null,
) : WebVpnApi {
    var lastFinishAuthRequest: FinishAuthRequest? = null
    var userInfoCalls: Int = 0

    override suspend fun getAuthenticationList(): ApiEnvelope<AuthenticationListData> =
        ApiEnvelope(
            code = 0,
            message = "ok",
            data = AuthenticationListData(
                list = listOf(
                    AuthenticationMethod(
                        externalId = "local-id",
                        authType = 1,
                        authOptions = AuthOptions(
                            staticVerification = 1,
                            useGraphValidateCode = 1,
                        ),
                    ),
                ),
            ),
        )

    override suspend fun getGraphCaptcha(width: Int, height: Int): ApiEnvelope<CaptchaData> =
        ApiEnvelope(0, "ok", CaptchaData("captcha-id", "data:image/png;base64,"))

    override suspend fun finishAuth(request: FinishAuthRequest): ApiEnvelope<FinishAuthData> {
        lastFinishAuthRequest = request
        finishAuthError?.let { throw it }
        return finishAuthResponse
    }

    override suspend fun getUserInfo(): ApiEnvelope<UserInfo> {
        userInfoCalls += 1
        userInfoError?.let { throw it }
        return userInfoResponse
    }

    override suspend fun logout(): ApiEnvelope<Unit> = ApiEnvelope(0, "ok", Unit)
}

private class FakeSessionStore : WebVpnSessionStore {
    var legacyTokenPresent: Boolean = false
    var clearLegacyTokenCount: Int = 0
    var cookies: List<String> = emptyList()
    var clearCount: Int = 0
    private var deviceId: String = "0123456789abcdef0123456789abcdef"

    override suspend fun clearLegacyToken() {
        legacyTokenPresent = false
        clearLegacyTokenCount += 1
    }

    override suspend fun saveCookies(cookies: List<String>) {
        this.cookies = cookies
    }

    override suspend fun getCookies(): List<String> = cookies

    override suspend fun clearSession() {
        clearCount += 1
        legacyTokenPresent = false
        cookies = emptyList()
    }

    override suspend fun getOrCreateDeviceId(): String = deviceId
}
