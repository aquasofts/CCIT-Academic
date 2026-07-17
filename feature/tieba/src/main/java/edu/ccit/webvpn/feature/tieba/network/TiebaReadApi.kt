package edu.ccit.webvpn.feature.tieba.network

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.provider.Settings
import android.util.Base64
import android.util.Log
import com.huanchengfly.tieba.post.api.models.protos.AppPosInfo
import com.huanchengfly.tieba.post.api.models.protos.CommonRequest
import com.huanchengfly.tieba.post.api.models.protos.frsPage.AdParam as FrsAdParam
import com.huanchengfly.tieba.post.api.models.protos.frsPage.FrsPageRequest
import com.huanchengfly.tieba.post.api.models.protos.frsPage.FrsPageRequestData
import com.huanchengfly.tieba.post.api.models.protos.frsPage.FrsPageResponse
import com.huanchengfly.tieba.post.api.models.protos.forumRuleDetail.ForumRuleDetailRequest
import com.huanchengfly.tieba.post.api.models.protos.forumRuleDetail.ForumRuleDetailRequestData
import com.huanchengfly.tieba.post.api.models.protos.forumRuleDetail.ForumRuleDetailResponse
import com.huanchengfly.tieba.post.api.models.protos.pbFloor.PbFloorRequest
import com.huanchengfly.tieba.post.api.models.protos.pbFloor.PbFloorRequestData
import com.huanchengfly.tieba.post.api.models.protos.pbFloor.PbFloorResponse
import com.huanchengfly.tieba.post.api.models.protos.pbPage.AdParam as PbAdParam
import com.huanchengfly.tieba.post.api.models.protos.pbPage.PbPageRequest
import com.huanchengfly.tieba.post.api.models.protos.pbPage.PbPageRequestData
import com.huanchengfly.tieba.post.api.models.protos.pbPage.PbPageResponse
import com.huanchengfly.tieba.post.api.models.protos.profile.ProfileRequest
import com.huanchengfly.tieba.post.api.models.protos.profile.ProfileRequestData
import com.huanchengfly.tieba.post.api.models.protos.profile.ProfileResponse
import com.huanchengfly.tieba.post.api.models.protos.userPost.UserPostRequest
import com.huanchengfly.tieba.post.api.models.protos.userPost.UserPostRequestData
import com.huanchengfly.tieba.post.api.models.protos.userPost.UserPostResponse
import com.huanchengfly.tieba.post.api.models.protos.addPost.AddPostRequest
import com.huanchengfly.tieba.post.api.models.protos.addPost.AddPostRequestData
import com.huanchengfly.tieba.post.api.models.protos.addPost.AddPostResponse
import com.huanchengfly.tieba.post.utils.helios.Base32
import com.huanchengfly.tieba.post.utils.helios.Hasher
import com.squareup.wire.Message
import java.io.EOFException
import java.io.IOException
import java.net.ConnectException
import java.net.ProtocolException
import java.net.SocketTimeoutException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.net.ssl.SSLHandshakeException
import okhttp3.Interceptor
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.wire.WireConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST

internal const val TIEBA_V12_VERSION = "12.52.1.0"
internal const val TIEBA_V12_POST_VERSION = "12.35.1.0"
private const val PROTOBUF_BOUNDARY = "--------7da3d81520810*"
private const val TRACE_HEADER = "X-CCIT-Tieba-Request"
private const val DEFAULT_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/135.0.0.0 Mobile Safari/537.36"

internal data class TiebaReadCredentials(
    val uid: Long,
    val bduss: String,
    val sToken: String,
    val zid: String?,
)

internal interface TiebaReadApi {
    @Headers("$TRACE_HEADER: FRS")
    @POST("c/f/frs/page?cmd=301001")
    suspend fun forum(
        @Body body: RequestBody,
        @Header("forum_name") forumName: String,
        @Header("client_user_token") userToken: String? = null,
    ): FrsPageResponse

    @Headers("$TRACE_HEADER: PB")
    @POST("c/f/pb/page?cmd=302001&format=protobuf")
    suspend fun thread(
        @Body body: RequestBody,
        @Header("client_user_token") userToken: String? = null,
    ): PbPageResponse

    @Headers("$TRACE_HEADER: PB_FLOOR")
    @POST("c/f/pb/floor?cmd=302002&format=protobuf")
    suspend fun floor(
        @Body body: RequestBody,
        @Header("client_user_token") userToken: String? = null,
    ): PbFloorResponse

    @Headers("$TRACE_HEADER: PROFILE")
    @POST("c/u/user/profile?cmd=303012&format=protobuf")
    suspend fun profile(
        @Body body: RequestBody,
        @Header("client_user_token") userToken: String? = null,
    ): ProfileResponse

    @Headers("$TRACE_HEADER: USER_POST")
    @POST("c/u/feed/userpost?cmd=303002&format=protobuf")
    suspend fun userPosts(
        @Body body: RequestBody,
        @Header("client_user_token") userToken: String? = null,
    ): UserPostResponse

    @Headers("$TRACE_HEADER: FORUM_RULE")
    @POST("c/f/forum/forumRuleDetail?cmd=309690&format=protobuf")
    suspend fun forumRule(@Body body: RequestBody): ForumRuleDetailResponse

    @Headers("$TRACE_HEADER: ADD_POST")
    @POST("c/c/post/add?cmd=309731&format=protobuf")
    suspend fun addPost(
        @Body body: RequestBody,
        @Header("client_user_token") userToken: String,
    ): AddPostResponse
}

internal class TiebaClientIdentity(context: Context) {
    private val appContext = context.applicationContext
    private val androidId = Settings.Secure.getString(appContext.contentResolver, Settings.Secure.ANDROID_ID)
        .orEmpty().ifBlank { "000" }
    private val packageInfo = appContext.packageManager.getPackageInfo(appContext.packageName, 0)
    private val installationSeed = "$androidId:${appContext.packageName}"
    private val stableSuffix = md5(installationSeed).take(3).toInt(16) % 1000
    private val rawCuid = md5("com.baidu$androidId").uppercase(Locale.ROOT)

    val cuid: String = "$rawCuid|V${Base32.encode(Hasher.hash(rawCuid.toByteArray()))}"
    val aid: String = run {
        val uuid = java.util.UUID.nameUUIDFromBytes(installationSeed.toByteArray()).toString()
        val encoded = Base32.encode(sha1("com.helios$androidId$uuid"))
        val rawAid = "A00-$encoded-"
        "$rawAid${Base32.encode(Hasher.hash(rawAid.toByteArray()))}"
    }
    val clientId: String = "wappc_${packageInfo.firstInstallTime}_$stableSuffix"
    val activeTimestamp: Long = System.currentTimeMillis()
    val clientLogId: String = activeTimestamp.toString()
    val firstInstallTime: Long = packageInfo.firstInstallTime
    val lastUpdateTime: Long = packageInfo.lastUpdateTime
    val userAgent: String = "$DEFAULT_USER_AGENT tieba/$TIEBA_V12_VERSION"

    fun commonRequest(
        credentials: TiebaReadCredentials?,
        clientVersion: String = TIEBA_V12_VERSION,
        tbs: String? = null,
        postMode: Boolean = false,
    ): CommonRequest {
        val metrics = appContext.resources.displayMetrics
        return CommonRequest(
            BDUSS = credentials?.bduss,
            _client_id = clientId,
            _client_type = 2,
            _client_version = clientVersion,
            _os_version = Build.VERSION.SDK_INT.toString(),
            _phone_imei = "",
            _timestamp = System.currentTimeMillis(),
            active_timestamp = firstInstallTime,
            android_id = if (postMode) androidId else Base64.encodeToString(androidId.toByteArray(), Base64.NO_WRAP),
            applist = "".takeIf { postMode },
            brand = Build.BRAND,
            c3_aid = aid,
            cmode = 1,
            cuid = cuid,
            cuid_galaxy2 = cuid,
            cuid_gid = "",
            event_day = SimpleDateFormat("yyyyMdd", Locale.getDefault()).format(Date()),
            extra = "",
            first_install_time = firstInstallTime,
            framework_ver = "3340042",
            from = "1020031h",
            is_teenager = 0,
            last_update_time = lastUpdateTime,
            lego_lib_version = "3.0.0",
            model = Build.MODEL,
            net_type = 1,
            oaid = "",
            personalized_rec_switch = 1,
            pversion = "1.0.3",
            q_type = 0,
            sample_id = null,
            scr_dip = metrics.density.toDouble(),
            scr_h = metrics.heightPixels,
            scr_w = metrics.widthPixels,
            sdk_ver = "2.34.0",
            start_scheme = "",
            start_type = 1,
            stoken = credentials?.sToken,
            tbs = tbs,
            swan_game_ver = "1038000",
            user_agent = userAgent,
            z_id = credentials?.zid,
        )
    }

    private fun md5(value: String): String = digest("MD5", value).joinToString("") { "%02x".format(it) }
    private fun sha1(value: String): ByteArray = digest("SHA-1", value)
    private fun digest(algorithm: String, value: String): ByteArray =
        MessageDigest.getInstance(algorithm).digest(value.toByteArray(StandardCharsets.UTF_8))
}

internal class TiebaReadRequestFactory(
    private val context: Context,
    private val identity: TiebaClientIdentity,
) {
    private val metrics get() = context.resources.displayMetrics

    fun forum(
        page: Int,
        sortType: Int,
        goodOnly: Boolean,
        loadType: Int,
        credentials: TiebaReadCredentials?,
    ): RequestBody = protobufBody(
        FrsPageRequest(
            FrsPageRequestData(
                ad_param = FrsAdParam(load_count = 0, refresh_count = 4, yoga_lib_version = "1.0"),
                app_pos = appPosition(),
                call_from = 0,
                category_id = 0,
                cid = 0,
                common = identity.commonRequest(credentials),
                ctime = 0,
                data_size = 0,
                hot_thread_id = 0,
                is_default_navtab = 0,
                is_good = if (goodOnly) 1 else 0,
                is_selection = 0,
                kw = encodedForumName(),
                last_click_tid = 0,
                load_type = loadType,
                net_error = 0,
                pn = page,
                q_type = 2,
                rn = 90,
                rn_need = 30,
                scr_dip = metrics.density.toDouble(),
                scr_h = metrics.heightPixels,
                scr_w = metrics.widthPixels,
                sort_type = if (goodOnly) -1 else sortType,
                st_param = 0,
                st_type = "recom_flist",
                up_schema = "",
                with_group = 1,
                yuelaou_locate = "",
            ),
        ),
        credentials = credentials,
        includeSToken = true,
    )

    fun thread(
        threadId: Long,
        page: Int,
        sortType: Int,
        onlyOriginalPoster: Boolean,
        credentials: TiebaReadCredentials?,
        postId: Long = 0,
        forumId: Long = edu.ccit.webvpn.feature.tieba.TARGET_FORUM_ID,
    ): RequestBody = protobufBody(
        PbPageRequest(
            PbPageRequestData(
                common = identity.commonRequest(credentials),
                kz = threadId,
                pid = postId,
                pn = page,
                r = sortType,
                lz = if (onlyOriginalPoster) 1 else 0,
                forum_id = forumId,
                ad_param = PbAdParam(load_count = 0, refresh_count = 1, is_req_ad = 1),
                mark = 0,
                last_pid = 0,
                app_pos = appPosition(),
                back = 0,
                banner = 0,
                broadcast_id = 0,
                floor_rn = 4,
                floor_sort_type = 1,
                from_push = 0,
                from_smart_frs = 0,
                immersion_video_comment_source = 0,
                is_comm_reverse = 0,
                is_fold_comment_req = 0,
                is_jumpfloor = 0,
                jumpfloor_num = 0,
                need_repost_recommend_forum = 0,
                obj_locate = "",
                obj_param1 = "10",
                obj_source = "",
                ori_ugc_type = 0,
                pb_rn = 0,
                q_type = 2,
                request_times = 0,
                rn = 15,
                s_model = 0,
                scr_dip = metrics.density.toDouble(),
                scr_h = metrics.heightPixels,
                scr_w = metrics.widthPixels,
                similar_from = 0,
                source_type = 2,
                st_type = "",
                thread_type = 0,
                weipost = 0,
                with_floor = 1,
            ),
        ),
        credentials = credentials,
        includeSToken = true,
    )

    fun floor(
        threadId: Long,
        postId: Long,
        page: Int,
        subPostId: Long,
        credentials: TiebaReadCredentials?,
        forumId: Long = edu.ccit.webvpn.feature.tieba.TARGET_FORUM_ID,
    ): RequestBody = protobufBody(
        PbFloorRequest(
            PbFloorRequestData(
                common = identity.commonRequest(credentials),
                forum_id = forumId,
                kz = threadId,
                pid = postId,
                pn = page,
                spid = subPostId,
                scr_dip = metrics.density.toDouble(),
                scr_h = metrics.heightPixels,
                scr_w = metrics.widthPixels,
                is_comm_reverse = 0,
                ori_ugc_type = 0,
            ),
        ),
        credentials = credentials,
        includeSToken = false,
    )

    fun profile(uid: Long, credentials: TiebaReadCredentials?): RequestBody {
        val self = credentials?.uid == uid
        return protobufBody(
            ProfileRequest(
                ProfileRequestData(
                    uid = credentials?.uid,
                    need_post_count = 1,
                    friend_uid = uid.takeUnless { self },
                    is_guest = if (self) 0 else 1,
                    pn = 1,
                    rn = 20,
                    has_plist = 1,
                    common = identity.commonRequest(credentials),
                    scr_w = metrics.widthPixels,
                    scr_h = metrics.heightPixels,
                    q_type = 0,
                    scr_dip = metrics.density.toDouble(),
                    is_from_usercenter = 1,
                    page = 1,
                    friend_uid_portrait = "",
                ),
            ),
            credentials = credentials,
            includeSToken = true,
        )
    }

    fun userPosts(
        uid: Long,
        page: Int,
        isThread: Boolean,
        credentials: TiebaReadCredentials?,
    ): RequestBody = protobufBody(
        UserPostRequest(
            UserPostRequestData(
                uid = uid,
                rn = 20,
                is_thread = if (isThread) 1 else 0,
                need_content = 1,
                subtype = 0.takeUnless { isThread },
                pn = page,
                common = identity.commonRequest(credentials),
                scr_w = metrics.widthPixels,
                scr_h = metrics.heightPixels,
                scr_dip = metrics.density.toDouble(),
                q_type = 1,
                is_view_card = if (isThread) 1 else 0,
            ),
        ),
        credentials = credentials,
        includeSToken = true,
    )

    fun forumRule(forumId: Long, credentials: TiebaReadCredentials?): RequestBody = protobufBody(
        ForumRuleDetailRequest(
            ForumRuleDetailRequestData(
                forum_id = forumId,
                common = identity.commonRequest(credentials),
            ),
        ),
        credentials = credentials,
        includeSToken = true,
    )

    /** Mirrors TiebaLite MixedTiebaApiImpl.addPostFlow and its V12_POST payload. */
    fun addPost(
        content: String,
        forumId: Long,
        forumName: String,
        threadId: Long,
        postId: Long?,
        subPostId: Long?,
        replyUserId: Long?,
        nickname: String,
        tbs: String,
        credentials: TiebaReadCredentials,
    ): RequestBody = protobufBody(
        AddPostRequest(
            AddPostRequestData(
                anonymous = "1",
                barrage_time = "0".takeIf { postId == null },
                can_no_forum = "0",
                common = identity.commonRequest(
                    credentials = credentials,
                    clientVersion = TIEBA_V12_POST_VERSION,
                    tbs = tbs,
                    postMode = true,
                ),
                content = content,
                entrance_type = "0",
                fid = forumId.toString(),
                floor_num = "0",
                kw = forumName,
                is_ad = "0",
                is_addition = "0",
                is_barrage = "0",
                is_feedback = "0",
                is_giftpost = "0",
                is_pictxt = "0",
                is_show_bless = 0,
                is_twzhibo_thread = "0",
                name_show = nickname,
                new_vcode = "1",
                post_from = when {
                    postId == null && subPostId == null -> "13"
                    subPostId == null -> "0"
                    else -> null
                },
                quote_id = postId?.toString(),
                reply_uid = replyUserId?.toString().takeIf { postId != null },
                repostid = postId?.toString(),
                sub_post_id = subPostId?.toString(),
                show_custom_figure = 0,
                takephoto_num = "0",
                tid = threadId.toString(),
                v_fid = "".takeIf { postId == null },
                v_fname = "".takeIf { postId == null },
                vcode_tag = "12",
            ),
        ),
        credentials = credentials,
        includeSToken = true,
    )

    private fun appPosition() = AppPosInfo(
        addr_timestamp = 0,
        ap_connected = true,
        ap_mac = "02:00:00:00:00:00",
        asp_shown_info = "",
        coordinate_type = "BD09LL",
    )

    private fun protobufBody(
        message: Message<*, *>,
        credentials: TiebaReadCredentials?,
        includeSToken: Boolean,
    ): RequestBody = MultipartBody.Builder(PROTOBUF_BOUNDARY)
        .setType(MultipartBody.FORM)
        .apply {
            if (includeSToken && credentials != null) addFormDataPart("stoken", credentials.sToken)
            addFormDataPart("data", "file", message.encode().toRequestBody())
        }
        .build()

    companion object {
        fun encodedForumName(): String = URLEncoder.encode(
            edu.ccit.webvpn.feature.tieba.TARGET_FORUM_NAME,
            StandardCharsets.UTF_8.name(),
        )
    }
}

internal sealed class TiebaReadFailure(message: String, cause: Throwable? = null) : IOException(message, cause) {
    class Offline(cause: Throwable?) : TiebaReadFailure("网络不可用", cause)
    class Timeout(cause: Throwable?) : TiebaReadFailure("连接超时", cause)
    class SecureConnection(cause: Throwable?) : TiebaReadFailure("安全连接失败", cause)
    class Service(cause: Throwable?) : TiebaReadFailure("贴吧服务异常", cause)
    class Data(cause: Throwable? = null) : TiebaReadFailure("帖子数据异常", cause)
    class WrongForum : TiebaReadFailure("帖子不属于本吧")
}

internal fun Throwable.toTiebaReadFailure(context: Context): TiebaReadFailure {
    if (this is TiebaReadFailure) return this
    val connectivity = context.getSystemService(android.net.ConnectivityManager::class.java)
    val network = connectivity.activeNetwork
    val capabilities = network?.let(connectivity::getNetworkCapabilities)
    val validated = capabilities?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
    return when {
        !validated -> TiebaReadFailure.Offline(this)
        this is SocketTimeoutException -> TiebaReadFailure.Timeout(this)
        this is SSLHandshakeException -> TiebaReadFailure.SecureConnection(this)
        this is HttpException || this is ConnectException || this is ProtocolException || this is IOException && this !is EOFException ->
            TiebaReadFailure.Service(this)
        else -> TiebaReadFailure.Data(this)
    }
}

internal fun createTiebaReadRetrofit(client: OkHttpClient, baseUrl: String = "https://tiebac.baidu.com/"): Retrofit =
    Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(WireConverterFactory.create())
        .validateEagerly(true)
        .build()

internal fun tiebaReadHeaderInterceptor(context: Context, identity: TiebaClientIdentity): Interceptor = Interceptor { chain ->
    val traceName = chain.request().header(TRACE_HEADER).orEmpty()
    val request = chain.request().newBuilder()
        .removeHeader(TRACE_HEADER)
        .header("Charset", "UTF-8")
        .header("client_type", "2")
        .header("cookie", "ka=open; CUID=${identity.cuid}; TBBRAND=${Build.MODEL}")
        .header("cuid", identity.cuid)
        .header("cuid_galaxy2", identity.cuid)
        .header("cuid_gid", "")
        .header("c3_aid", identity.aid)
        .header("User-Agent", identity.userAgent)
        .header("x_bd_data_type", "protobuf")
        .build()
    val startedAt = System.nanoTime()
    try {
        chain.proceed(request).also { response ->
            if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
                val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000
                Log.d(
                    "TiebaRead",
                    "$traceName ${request.url.host}${request.url.encodedPath} HTTP ${response.code} " +
                        "${response.body?.contentType()} ${elapsedMs}ms",
                )
            }
        }
    } catch (failure: Throwable) {
        if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            Log.d("TiebaRead", "$traceName ${request.url.host}${request.url.encodedPath} ${failure.javaClass.simpleName}")
        }
        throw failure
    }
}
