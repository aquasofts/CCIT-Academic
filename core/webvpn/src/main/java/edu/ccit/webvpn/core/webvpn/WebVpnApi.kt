package edu.ccit.webvpn.core.webvpn

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Query

interface WebVpnApi {
    @GET("api/access/authentication/list")
    suspend fun getAuthenticationList(): ApiEnvelope<AuthenticationListData>

    @GET("api/access/graph-captcha/validate-code")
    suspend fun getGraphCaptcha(
        @Query("width") width: Int = 150,
        @Query("height") height: Int = 50,
    ): ApiEnvelope<CaptchaData>

    @Headers(
        "Accept: application/json, text/plain, */*",
        "Content-Type: application/json",
        "Origin: https://webvpn.ccit.edu.cn",
    )
    @POST("api/access/auth/finish")
    suspend fun finishAuth(@Body request: FinishAuthRequest): ApiEnvelope<FinishAuthData>

    @GET("api/access/user/info")
    suspend fun getUserInfo(): ApiEnvelope<UserInfo>

    @POST("api/access/user/logout")
    suspend fun logout(): ApiEnvelope<Unit>
}
