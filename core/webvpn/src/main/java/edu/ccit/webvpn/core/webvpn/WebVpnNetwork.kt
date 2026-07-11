package edu.ccit.webvpn.core.webvpn

import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.Request
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

object WebVpnNetwork {
    const val BaseUrl = "https://webvpn.ccit.edu.cn/"

    fun createClient(
        cookieJar: WebVpnCookieJar = WebVpnCookieJar(),
        enableHttpLogging: Boolean = false,
    ): OkHttpClient {
        val clientBuilder = OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                chain.proceed(asBrowserRequest(chain.request()))
            }

        if (enableHttpLogging) {
            clientBuilder.addInterceptor(
                HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC },
            )
        }

        return clientBuilder.build()
    }

    fun createApi(client: OkHttpClient): WebVpnApi {
        val json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

        return Retrofit.Builder()
            .baseUrl(BaseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(WebVpnApi::class.java)
    }

    /**
     * auth/finish establishes the verified current session with a Cookie. The successful browser
     * trace never sends Authorization, including on user/info. A legacy Bearer header alongside
     * that Cookie can make the server reject an otherwise valid session with HTTP 400.
     */
    internal fun asBrowserRequest(request: Request): Request = request.newBuilder()
        .header("User-Agent", BrowserUserAgent)
        .header("Accept-Language", "zh-CN,zh;q=0.9")
        .removeHeader("Authorization")
        .apply {
            if (request.url.host == BaseHost &&
                request.url.encodedPath.startsWith(AccessApiPrefix)
            ) {
                header("Accept", "application/json, text/plain, */*")
                header("Sec-Fetch-Dest", "empty")
                header("Sec-Fetch-Mode", "cors")
                header("Sec-Fetch-Site", "same-origin")
                header("DNT", "1")
            }
        }
        .build()

    private const val BaseHost = "webvpn.ccit.edu.cn"
    private const val AccessApiPrefix = "/api/access/"
    private const val BrowserUserAgent =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 " +
            "Safari/537.36 Edg/150.0.0.0"
}
