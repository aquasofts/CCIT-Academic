package edu.ccit.webvpn.core.webvpn

import java.io.IOException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import retrofit2.HttpException

class WebVpnAuthRepository(
    private val api: WebVpnApi,
    private val sessionStore: WebVpnSessionStore,
    private val cookieJar: WebVpnCookieJar,
    private val json: Json = Json {
        encodeDefaults = false
        explicitNulls = false
    },
) {
    suspend fun loadAuthMethods(): List<AuthenticationMethod> =
        api.getAuthenticationList().unwrap().list

    suspend fun loadLoginConfiguration(): LocalLoginConfiguration {
        val method = loadAuthMethods()
            .firstOrNull { it.authType == PasswordAuthType }
            ?: throw WebVpnApiException(
                apiCode = MissingLocalLoginCode,
                message = "学校 WebVPN 当前未开放本地账号登录",
            )
        val options = method.authOptions
        return LocalLoginConfiguration(
            externalId = method.externalId,
            requiresPassword = options?.staticVerification == Enabled,
            requiresGraphCaptcha = options?.useGraphValidateCode == Enabled,
            dynamicVerificationTypes = options?.dynamicVerification.orEmpty(),
        )
    }

    suspend fun loadCaptcha(): CaptchaData = api.getGraphCaptcha().unwrap()

    suspend fun login(
        username: String,
        password: String,
        captchaId: String,
        code: String,
        configuration: LocalLoginConfiguration? = null,
    ): LoginResult {
        val normalizedUsername = username.trim()
        require(normalizedUsername.isNotBlank()) { "请输入用户名" }

        val loginConfiguration = configuration ?: loadLoginConfiguration()
        if (loginConfiguration.dynamicVerificationTypes.isNotEmpty()) {
            throw WebVpnApiException(
                apiCode = UnsupportedDynamicVerificationCode,
                message = "学校当前要求动态验证码，请先使用官方 WebVPN 网页完成登录",
            )
        }
        if (loginConfiguration.requiresPassword) {
            require(password.isNotBlank()) { "请输入密码" }
        }
        if (loginConfiguration.requiresGraphCaptcha) {
            require(captchaId.isNotBlank() && code.isNotBlank()) { "请输入图形验证码" }
        }

        val payload = FinishAuthPayload(
            deviceId = sessionStore.getOrCreateDeviceId(),
            username = normalizedUsername,
            password = password
                .takeIf { loginConfiguration.requiresPassword }
                ?.let(WebVpnCrypto::encryptPassword),
            captchaId = captchaId.takeIf { loginConfiguration.requiresGraphCaptcha },
            code = code.trim().takeIf { loginConfiguration.requiresGraphCaptcha },
        )

        try {
            val authEnvelope = runCatching {
                api.finishAuth(
                    FinishAuthRequest(
                        externalId = loginConfiguration.externalId,
                        data = json.encodeToString(payload),
                    ),
                )
            }.getOrElse { throw it.atStage("auth/finish") }
            runCatching { authEnvelope.ensureSuccess() }
                .getOrElse { throw it.atStage("auth/finish") }
            // The response may still contain a legacy token, but the verified current deployment
            // authenticates the next request with the Cookie set by auth/finish.
            sessionStore.clearLegacyToken()

            // The current WebVPN establishes its final Cookie session through auth/finish;
            // user/info is authoritative and must receive only that Cookie.
            val result = LoginResult(
                runCatching { validateSession() }
                    .getOrElse { throw it.atStage("user/info") },
            )
            sessionStore.saveCookies(cookieJar.snapshot())
            return result
        } catch (error: Throwable) {
            clearLocalSession()
            throw error.toUserFacingException()
        }
    }

    suspend fun restoreSession(): LoginResult? {
        sessionStore.clearLegacyToken()
        val cookies = sessionStore.getCookies()
        cookieJar.restore(WebVpnNetwork.BaseUrl.toHttpUrl(), cookies)
        if (cookieJar.snapshot().isEmpty()) return null

        return revalidateSession()
    }

    suspend fun revalidateSession(): LoginResult? {
        return try {
            LoginResult(validateSession())
        } catch (error: Throwable) {
            val mappedError = error.toUserFacingException()
            if (mappedError is WebVpnApiException &&
                mappedError.apiCode in InvalidSessionCodes
            ) {
                clearLocalSession()
                null
            } else {
                throw mappedError
            }
        }
    }

    suspend fun requireActiveSession(): LoginResult = revalidateSession()
        ?: throw WebVpnApiException(
            apiCode = UnauthorizedCode,
            message = "登录状态已失效，请重新登录",
        )

    suspend fun validateSession(): UserInfo = api.getUserInfo().unwrap()

    suspend fun logout() {
        try {
            api.logout()
        } catch (_: Throwable) {
            // Local credentials must still be removed when the network is unavailable.
        } finally {
            clearLocalSession()
        }
    }

    suspend fun clearLocalSession() {
        cookieJar.clear()
        sessionStore.clearSession()
    }

    private fun <T> ApiEnvelope<T>.ensureSuccess() {
        if (code != SuccessCode) {
            throw WebVpnApiException(
                apiCode = code,
                message = message.ifBlank { "WebVPN 接口返回错误：$code" },
            )
        }
    }

    private fun <T> ApiEnvelope<T>.unwrap(): T {
        ensureSuccess()
        return data ?: throw WebVpnApiException(
            apiCode = EmptyDataCode,
            message = "WebVPN 接口未返回数据",
        )
    }

    private fun Throwable.toUserFacingException(): Throwable = when (this) {
        is WebVpnApiException, is IllegalArgumentException -> this
        is HttpException -> WebVpnApiException(
            apiCode = code(),
            message = when (code()) {
                401, 402 -> "登录状态已失效，请重新登录"
                in 500..599 -> "学校 WebVPN 服务暂时不可用，请稍后重试"
                else -> responseApiMessage() ?: "WebVPN 请求失败（HTTP ${code()}）"
            },
        )
        is IOException -> WebVpnApiException(
            apiCode = NetworkErrorCode,
            message = "无法连接学校 WebVPN，请检查网络后重试",
        )
        else -> this
    }

    private fun Throwable.atStage(stage: String): Throwable {
        val mapped = toUserFacingException()
        return if (mapped is WebVpnApiException) {
            WebVpnApiException(mapped.apiCode, "$stage：${mapped.message}")
        } else {
            mapped
        }
    }

    private fun HttpException.responseApiMessage(): String? {
        val body = runCatching { response()?.errorBody()?.string() }.getOrNull()
            ?.takeIf(String::isNotBlank)
            ?: return null
        return runCatching {
            json.parseToJsonElement(body)
                .jsonObject["message"]
                ?.jsonPrimitive
                ?.contentOrNull
                ?.takeIf(String::isNotBlank)
        }.getOrNull()
    }

    private companion object {
        const val SuccessCode = 0
        const val BadRequestCode = 400
        const val UnauthorizedCode = 401
        const val TokenExpiredCode = 402
        const val PasswordAuthType = 1
        const val Enabled = 1
        const val MissingLocalLoginCode = -1
        const val EmptyDataCode = -2
        const val NetworkErrorCode = -3
        const val UnsupportedDynamicVerificationCode = -4
        val InvalidSessionCodes = setOf(BadRequestCode, UnauthorizedCode, TokenExpiredCode)
    }
}
