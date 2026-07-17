package edu.ccit.webvpn.feature.tieba.network

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Base64
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName
import com.huanchengfly.tieba.post.utils.helios.Base32
import com.huanchengfly.tieba.post.utils.helios.Hasher
import edu.ccit.webvpn.feature.tieba.data.AccountEntity
import edu.ccit.webvpn.feature.tieba.data.TiebaClientConfig
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
import okhttp3.FormBody
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Field
import retrofit2.http.FieldMap
import retrofit2.http.FormUrlEncoded
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST

/**
 * Sign-in protocol ported from TiebaLite commit 910fd564c47f77ab6a807f1bc122279e7b9aa0b1.
 * Only the Retrofit suspend return type and HTTPS base URL differ from upstream.
 */
internal interface TiebaOfficialApi {
    @Headers(
        "$FORCE_LOGIN_HEADER: true",
        "Cookie: ka=open",
        "$DROP_HEADERS_HEADER: Charset,client_type",
        "$NO_COMMON_PARAMS_HEADER: oaid",
    )
    @POST("c/c/forum/sign")
    @FormUrlEncoded
    suspend fun signFlow(
        @Field("fid") forumId: String,
        @Field("kw") forumName: String,
        @Field("tbs") tbs: String,
        @Header("client_user_token") clientUserToken: String,
        @Field("_client_version") clientVersion: String = TIEBA_LITE_SIGN_VERSION,
        @Header("User-Agent") userAgent: String = "bdtb for Android $clientVersion",
    ): TiebaSignResultBean

    @POST("c/s/sync")
    @FormUrlEncoded
    @Headers(
        "$DROP_HEADERS_HEADER: Charset,client_type",
        "$NO_COMMON_PARAMS_HEADER: oaid,mac,_phone_imei,android_id,swan_game_ver,sdk_ver",
    )
    suspend fun sync(
        @FieldMap fields: Map<String, String>,
        @Header("Cookie") cookie: String,
    ): TiebaSyncBean
}

internal data class TiebaSignResultBean(
    @SerializedName("user_info") val userInfo: UserInfo? = null,
    @SerializedName("error_code") val errorCode: String? = null,
    @SerializedName("error_msg") val errorMsg: String? = null,
    val time: Long? = null,
) {
    data class UserInfo(
        @SerializedName("user_id") val userId: String? = null,
        @SerializedName("is_sign_in") val isSignIn: Int? = null,
        @SerializedName("cont_sign_num") val contSignNum: Int? = null,
        @SerializedName("user_sign_rank") val userSignRank: Int? = null,
        @SerializedName("sign_time") val signTime: String? = null,
        @SerializedName("sign_bonus_point") val signBonusPoint: Int? = null,
        @SerializedName("level_name") val levelName: String? = null,
        @SerializedName("levelup_score") val levelUpScore: String? = null,
        @SerializedName("all_level_info") val allLevelInfo: List<AllLevelInfo> = emptyList(),
    ) {
        data class AllLevelInfo(val id: String, val name: String, val score: String)
    }
}

internal data class TiebaSyncBean(
    val client: Client,
    @SerializedName("wl_config") val wlConfig: WlConfig,
) {
    data class Client(@SerializedName("client_id") val clientId: String)
    data class WlConfig(@SerializedName("sample_id") val sampleId: String)
}

internal data class TiebaCommonResponse(
    @SerializedName(value = "error_code", alternate = ["errno", "no"])
    val errorCode: Int = 0,
    @SerializedName(value = "error_msg", alternate = ["errmsg", "error"])
    val errorMessage: JsonElement? = null,
)

internal class TiebaApiException(
    val code: Int,
    override val message: String,
) : IOException(message)

internal data class TiebaOfficialIdentity(
    val cuid: String,
    val aid: String,
    val androidId: String,
) {
    companion object {
        fun create(context: Context, uuid: String): TiebaOfficialIdentity {
            val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID).orEmpty()
            val rawCuid = md5Official("com.baidu$androidId").uppercase(Locale.ROOT)
            val cuid = "$rawCuid|V${Base32.encode(Hasher.hash(rawCuid.toByteArray()))}"
            val rawAidAndroidId = androidId.ifBlank { "000000000" }
            val encodedAid = Base32.encode(sha1Official("com.helios$rawAidAndroidId$uuid"))
            val rawAid = "A00-$encodedAid-"
            val aid = "$rawAid${Base32.encode(Hasher.hash(rawAid.toByteArray()))}"
            return TiebaOfficialIdentity(cuid, aid, androidId.ifBlank { "000" })
        }
    }
}

internal class TiebaOfficialClient(
    context: Context,
    gson: Gson,
    private val account: AccountEntity?,
    private val config: TiebaClientConfig,
    baseUrl: String = "https://c.tieba.baidu.com/",
    private val clock: () -> Long = System::currentTimeMillis,
    stNumber: () -> Int = { ThreadLocalRandom.current().nextInt(100, 850) },
    stSizeFactor: () -> Double = { Math.random() * 8 + 0.4 },
) {
    private val appContext = context.applicationContext
    private val identity = TiebaOfficialIdentity.create(appContext, config.uuid)
    private val initTime = clock()
    private val commonParams: List<ParamExpression> = listOf(
        "BDUSS" to { account?.bduss },
        "_client_id" to { config.clientId },
        "_client_type" to { "2" },
        "_os_version" to { Build.VERSION.SDK_INT.toString() },
        "model" to { Build.MODEL },
        "net_type" to { "1" },
        "_phone_imei" to { "000000000000000" },
        "timestamp" to { clock().toString() },
        "active_timestamp" to { config.activeTimestamp.toString() },
        "android_id" to { Base64.encodeToString(identity.androidId.toByteArray(), Base64.DEFAULT) },
        "baiduid" to { config.baiduId },
        "brand" to { Build.BRAND },
        "cmode" to { "1" },
        "cuid" to { identity.cuid },
        "cuid_galaxy2" to { identity.cuid },
        "cuid_gid" to { "" },
        "event_day" to { SimpleDateFormat("yyyyMdd", Locale.getDefault()).format(Date(clock())) },
        "extra" to { "" },
        "first_install_time" to { config.firstInstallTime.toString() },
        "framework_ver" to { "3340042" },
        "from" to { "tieba" },
        "is_teenager" to { "0" },
        "last_update_time" to { config.lastUpdateTime.toString() },
        "mac" to { "02:00:00:00:00:00" },
        "sample_id" to { config.sampleId },
        "sdk_ver" to { "2.34.0" },
        "start_scheme" to { "" },
        "start_type" to { "1" },
        "swan_game_ver" to { "1038000" },
        "_client_version" to { TIEBA_LITE_OFFICIAL_VERSION },
        "c3_aid" to { identity.aid },
        "oaid" to { "{\"v\":\"\",\"sc\":0,\"sup\":0,\"isTrackLimited\":0}" },
    )
    private val api: TiebaOfficialApi

    init {
        val client = OkHttpClient.Builder()
            .readTimeout(60, TimeUnit.SECONDS)
            .connectTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .addInterceptor(
                CommonHeaderInterceptor(
                    "User-Agent" to { "bdtb for Android $TIEBA_LITE_OFFICIAL_VERSION" },
                    "Cookie" to { "CUID=${identity.cuid};ka=open;TBBRAND=${Build.MODEL};BAIDUID=${config.baiduId};" },
                    "cuid" to { identity.cuid },
                    "cuid_galaxy2" to { identity.cuid },
                    "cuid_gid" to { "" },
                    "c3_aid" to { identity.aid },
                    "client_type" to { "2" },
                    "Charset" to { "UTF-8" },
                    "client_logid" to { initTime.toString() },
                ),
            )
            .addInterceptor(CommonParamInterceptor(commonParams))
            .addInterceptor(StParamInterceptor(stNumber, stSizeFactor))
            .addInterceptor(DropInterceptor)
            .addInterceptor(FailureResponseInterceptor(gson))
            .addInterceptor(ForceLoginInterceptor { account?.bduss?.isNotBlank() == true })
            .addInterceptor(SortAndSignInterceptor("tiebaclient!!!"))
            .build()
        api = Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .client(client)
            .validateEagerly(true)
            .build()
            .create(TiebaOfficialApi::class.java)
    }

    suspend fun sign(forumId: String, forumName: String, tbs: String): TiebaSignResultBean {
        val current = requireNotNull(account) { "请先登录贴吧账号" }
        return api.signFlow(forumId, forumName, tbs, current.uid.toString())
    }

    suspend fun sync(): TiebaSyncBean = api.sync(
        fields = syncFields(appContext, config),
        cookie = config.baiduId?.let { "ka=open;BAIDUID=$it" } ?: "ka=open",
    )

    private fun syncFields(context: Context, config: TiebaClientConfig): Map<String, String> {
        val metrics = context.resources.displayMetrics
        return linkedMapOf<String, String>().apply {
            config.clientId?.let { put("_client_id", it) }
            put("_msg_status", "1")
            put("_phone_screen", "${metrics.widthPixels},${metrics.heightPixels}")
            put("_pic_quality", "0")
            put("board", Build.BOARD)
            put("brand", Build.BRAND)
            put("cam", Base64.encodeToString("02:00:00:00:00:00".toByteArray(), Base64.DEFAULT))
            put("di_diordna", Base64.encodeToString(identity.androidId.toByteArray(), Base64.DEFAULT))
            put("iemi", Base64.encodeToString("000000000000000".toByteArray(), Base64.DEFAULT))
            put("incremental", Build.VERSION.INCREMENTAL)
            put("md5", "F86F4C238491AB3BEBFA33AC42C1582B")
            put("signmd5", "225172691")
            put("package", "com.baidu.tieba")
            put("versioncode", "202965248")
            put("running_abi", "64")
            put("support_abi", "64")
            put("scr_dip", metrics.density.toString())
            put("scr_h", metrics.heightPixels.toString())
            put("scr_w", metrics.widthPixels.toString())
            account?.sToken?.takeIf(String::isNotBlank)?.let { put("stoken", it) }
        }
    }
}

private typealias ParamExpression = Pair<String, () -> String?>

private class CommonHeaderInterceptor(private vararg val additionHeaders: ParamExpression) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        return chain.proceed(request.newBuilder().apply {
            additionHeaders.forEach { (name, expression) ->
                expression()?.let { if (request.header(name) == null) addHeader(name, it) }
            }
        }.build())
    }
}

private class CommonParamInterceptor(private val additionParams: List<ParamExpression>) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        var headers = request.headers
        var body = request.body
        val noCommonParams = headers[NO_COMMON_PARAMS_HEADER]?.split(',').orEmpty()
        headers = headers.newBuilder().removeAll(NO_COMMON_PARAMS_HEADER).build()
        if (body == null || body.contentLength() == 0L) {
            body = FormBody.Builder().apply {
                additionParams.forEach { (name, expression) ->
                    expression()?.let { if (name !in noCommonParams) add(name, it) }
                }
            }.build()
        } else if (body is FormBody) {
            val oldBody = body
            body = FormBody.Builder().addAllEncoded(oldBody).apply {
                additionParams.forEach { (name, expression) ->
                    expression()?.let {
                        if (!oldBody.containsEncodedName(name) && name !in noCommonParams) add(name, it)
                    }
                }
            }.build()
        }
        return chain.proceed(request.newBuilder().headers(headers).method(request.method, body).build())
    }
}

private class StParamInterceptor(
    private val number: () -> Int,
    private val sizeFactor: () -> Double,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val body = request.body
        if (body !is FormBody) return chain.proceed(request)
        val num = number()
        val fields = if (num in 100..120) {
            listOf("stErrorNums" to "0")
        } else {
            listOf(
                "stErrorNums" to "1",
                "stMethod" to "1",
                "stMode" to "1",
                "stTimesNum" to "1",
                "stTime" to num.toString(),
                "stSize" to (sizeFactor() * num).roundToInt().toString(),
            )
        }
        val newBody = FormBody.Builder().addAllEncoded(body).apply {
            fields.forEach { (name, value) -> add(name, value) }
        }.build()
        return chain.proceed(request.newBuilder().method(request.method, newBody).build())
    }
}

private object DropInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        var headers = request.headers
        val dropHeaders = headers[DROP_HEADERS_HEADER]
        if (dropHeaders != null) {
            headers = headers.newBuilder().apply {
                removeAll(DROP_HEADERS_HEADER)
                dropHeaders.split(',').forEach(::removeAll)
            }.build()
        }
        return chain.proceed(request.newBuilder().headers(headers).build())
    }
}

private class FailureResponseInterceptor(private val gson: Gson) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        val body = response.body
        if (!response.isSuccessful || body == null || body.contentLength() == 0L) return response
        val charset = body.contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8
        val clone = body.source().also { it.request(Long.MAX_VALUE) }.buffer.clone()
        val common = runCatching {
            clone.inputStream().reader(charset).use { gson.fromJson(it, TiebaCommonResponse::class.java) }
        }.getOrNull() ?: return response
        if (common.errorCode != 0) {
            throw TiebaApiException(common.errorCode, common.errorMessage.tiebaErrorMessage().ifBlank { "未知错误" })
        }
        return response
    }
}

private class ForceLoginInterceptor(private val isLoggedIn: () -> Boolean) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val force = request.header(FORCE_LOGIN_HEADER) == "true"
        val cleaned = request.newBuilder().removeHeader(FORCE_LOGIN_HEADER).build()
        if (force && !isLoggedIn()) throw TiebaApiException(11, "请先登录贴吧账号")
        return chain.proceed(cleaned)
    }
}

private class SortAndSignInterceptor(private val appSecret: String) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val body = request.body
        if (body !is FormBody || !body.containsEncodedName("_client_version") || body.containsEncodedName("sign")) {
            return chain.proceed(request)
        }
        val sortedEncodedRaw = body.sortedEncodedRaw()
        val signedBody = FormBody.Builder().apply {
            sortedEncodedRaw.split('&').forEach {
                val (name, value) = it.split('=', limit = 2)
                addEncoded(name, value)
            }
            addEncoded("sign", md5Official(body.sortedRaw(separator = false) + appSecret))
        }.build()
        return chain.proceed(request.newBuilder().method(request.method, signedBody).build())
    }
}

private fun FormBody.containsEncodedName(name: String): Boolean =
    (0 until size).any { encodedName(it) == name }

private fun FormBody.Builder.addAllEncoded(body: FormBody): FormBody.Builder = apply {
    repeat(body.size) { addEncoded(body.encodedName(it), body.encodedValue(it)) }
}

private fun FormBody.sortedEncodedRaw(): String = (0 until size)
    .map { "${encodedName(it)}=${encodedValue(it)}" }
    .sorted()
    .joinToString("&")

private fun FormBody.sortedRaw(separator: Boolean): String = (0 until size)
    .map { "${name(it)}=${value(it)}" }
    .sorted()
    .joinToString(if (separator) "&" else "")

internal fun JsonElement?.tiebaErrorMessage(): String = when {
    this == null || isJsonNull -> ""
    isJsonPrimitive -> runCatching { asString }.getOrDefault("")
    isJsonObject -> listOf("errmsg", "error_msg", "error")
        .firstNotNullOfOrNull { key -> asJsonObject.get(key)?.tiebaErrorMessage()?.takeIf(String::isNotBlank) }
        .orEmpty()
    else -> ""
}

private fun md5Official(value: String): String = MessageDigest.getInstance("MD5")
    .digest(value.toByteArray(StandardCharsets.UTF_8))
    .joinToString("") { "%02x".format(it) }

private fun sha1Official(value: String): ByteArray = MessageDigest.getInstance("SHA-1")
    .digest(value.toByteArray(StandardCharsets.UTF_8))

internal const val TIEBA_LITE_SIGN_VERSION = "11.10.8.6"
private const val TIEBA_LITE_OFFICIAL_VERSION = "12.41.7.1"
private const val FORCE_LOGIN_HEADER = "force_login"
private const val DROP_HEADERS_HEADER = "drop_headers"
private const val NO_COMMON_PARAMS_HEADER = "no_common_params"
