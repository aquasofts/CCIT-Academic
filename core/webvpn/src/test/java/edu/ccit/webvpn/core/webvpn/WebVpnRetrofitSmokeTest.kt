package edu.ccit.webvpn.core.webvpn

import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test

class WebVpnRetrofitSmokeTest {
    @Test
    fun createsConvertersForEveryEndpoint() = runBlocking {
        val requestedPaths = mutableListOf<String>()
        val contentType = "application/json".toMediaType()
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                requestedPaths += chain.request().url.encodedPath
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("""{"code":0,"message":"ok"}""".toResponseBody(contentType))
                    .build()
            }
            .build()
        val api = WebVpnNetwork.createApi(client)

        api.getAuthenticationList()
        api.getGraphCaptcha()
        api.finishAuth(FinishAuthRequest(externalId = "local", data = "payload"))
        api.getUserInfo()
        api.logout()

        assertEquals(
            listOf(
                "/api/access/authentication/list",
                "/api/access/graph-captcha/validate-code",
                "/api/access/auth/finish",
                "/api/access/user/info",
                "/api/access/user/logout",
            ),
            requestedPaths,
        )
    }
}
