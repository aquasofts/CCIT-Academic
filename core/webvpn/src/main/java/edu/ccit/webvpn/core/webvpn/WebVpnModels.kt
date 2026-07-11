package edu.ccit.webvpn.core.webvpn

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ApiEnvelope<T>(
    val code: Int,
    val message: String = "",
    val data: T? = null,
)

@Serializable
data class AuthenticationListData(
    val list: List<AuthenticationMethod> = emptyList(),
)

@Serializable
data class AuthenticationMethod(
    val externalId: String,
    val name: String = "",
    val authType: Int,
    val authOptions: AuthOptions? = null,
)

@Serializable
data class AuthOptions(
    val staticVerification: Int? = null,
    val useGraphValidateCode: Int? = null,
    val dynamicVerification: List<Int> = emptyList(),
)

data class LocalLoginConfiguration(
    val externalId: String,
    val requiresPassword: Boolean,
    val requiresGraphCaptcha: Boolean,
    val dynamicVerificationTypes: List<Int>,
)

@Serializable
data class CaptchaData(
    val id: String,
    val captcha: String,
)

@Serializable
data class FinishAuthRequest(
    val externalId: String,
    val data: String,
)

@Serializable
data class FinishAuthPayload(
    val deviceId: String,
    @SerialName("userName") val username: String,
    val password: String? = null,
    val captchaId: String? = null,
    val code: String? = null,
)

@Serializable
data class FinishAuthData(
    // Older deployments returned a token. The current CCIT deployment authenticates
    // subsequent same-origin calls with cookies and may return no data at all.
    val token: String? = null,
)

@Serializable
data class UserInfo(
    val userId: Long? = null,
    val username: String? = null,
    val nickname: String? = null,
    val fullName: String? = null,
    val groups: List<String> = emptyList(),
    val authType: Int = 0,
    val passwordChanged: Int = 0,
    val needTriggerTFA: Boolean = false,
    val needChangePwd: Boolean = false,
    val needToBindLocalAccount: Boolean = false,
    val bindWechat: Boolean = false,
    val bindOtp: Boolean = false,
)

enum class RequiredAccountAction {
    None,
    CompleteTwoFactorAuthentication,
    ChangePassword,
    BindLocalAccount,
}

data class LoginResult(
    val userInfo: UserInfo,
    val requiredAction: RequiredAccountAction = userInfo.requiredAction(),
)

data class SavedWebVpnAccount(
    val username: String,
    val lastUsedAt: Long,
)

fun UserInfo.requiredAction(): RequiredAccountAction = when {
    needToBindLocalAccount -> RequiredAccountAction.BindLocalAccount
    needTriggerTFA -> RequiredAccountAction.CompleteTwoFactorAuthentication
    needChangePwd -> RequiredAccountAction.ChangePassword
    else -> RequiredAccountAction.None
}

class WebVpnApiException(
    val apiCode: Int,
    override val message: String,
) : Exception(message)
