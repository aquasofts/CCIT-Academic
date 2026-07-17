package edu.ccit.webvpn.feature.tieba.network

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.provider.Settings
import android.util.Base64
import android.util.Log
import android.net.Uri
import android.graphics.BitmapFactory
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.huanchengfly.tieba.post.api.models.protos.PbContent
import com.huanchengfly.tieba.post.api.models.protos.Post
import com.huanchengfly.tieba.post.api.models.protos.PostInfoList
import com.huanchengfly.tieba.post.api.models.protos.SubPostList
import com.huanchengfly.tieba.post.api.models.protos.ThreadInfo
import com.huanchengfly.tieba.post.api.models.protos.User
import com.huanchengfly.tieba.post.api.models.protos.frsPage.FrsPageResponse
import com.huanchengfly.tieba.post.api.models.protos.pbFloor.PbFloorResponse
import com.huanchengfly.tieba.post.api.models.protos.pbPage.PbPageResponse
import com.huanchengfly.tieba.post.api.models.protos.profile.ProfileResponse
import com.huanchengfly.tieba.post.api.models.protos.userPost.UserPostResponse
import edu.ccit.webvpn.feature.tieba.FloorReply
import edu.ccit.webvpn.feature.tieba.FloorReplyPage
import edu.ccit.webvpn.feature.tieba.FloorSort
import edu.ccit.webvpn.feature.tieba.ForumPage
import edu.ccit.webvpn.feature.tieba.ForumRule
import edu.ccit.webvpn.feature.tieba.ForumRuleItem
import edu.ccit.webvpn.feature.tieba.ForumSort
import edu.ccit.webvpn.feature.tieba.ForumSummary
import edu.ccit.webvpn.feature.tieba.ForumThread
import edu.ccit.webvpn.feature.tieba.LoadPicPageData
import edu.ccit.webvpn.feature.tieba.SignOutcome
import edu.ccit.webvpn.feature.tieba.TARGET_FORUM_ID
import edu.ccit.webvpn.feature.tieba.TARGET_FORUM_NAME
import edu.ccit.webvpn.feature.tieba.TiebaContent
import edu.ccit.webvpn.feature.tieba.TiebaUserForum
import edu.ccit.webvpn.feature.tieba.TiebaUserPost
import edu.ccit.webvpn.feature.tieba.TiebaUserPostPage
import edu.ccit.webvpn.feature.tieba.TiebaUserProfile
import edu.ccit.webvpn.feature.tieba.TiebaReplyResult
import edu.ccit.webvpn.feature.tieba.TiebaUploadedImage
import edu.ccit.webvpn.feature.tieba.ThreadFloor
import edu.ccit.webvpn.feature.tieba.ThreadPage
import edu.ccit.webvpn.feature.tieba.data.AccountEntity
import edu.ccit.webvpn.feature.tieba.data.TiebaClientConfig
import edu.ccit.webvpn.feature.tieba.data.TiebaSettingsRepository
import edu.ccit.webvpn.feature.tieba.originalImageUrl
import java.io.IOException
import java.security.MessageDigest
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.io.File
import java.io.RandomAccessFile
import okhttp3.Cache
import okhttp3.MultipartBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

/** The JSON endpoints retained for login, profile and TiebaLite's in-forum search. */
internal interface TiebaSupportApi {
    @GET("mo/q/search/thread")
    suspend fun search(
        @Query("word") keyword: String,
        @Query("pn") page: Int,
        @Query("st") order: Int = 1,
        @Query("tt") filter: Int = 1,
        @Query("rn") pageSize: Int = 30,
        @Query("fname") forumName: String = TARGET_FORUM_NAME,
        @Query("ct") contentType: Int = 2,
        @Query("is_use_zonghe") useCombined: Int? = null,
        @Query("cv") clientVersion: String = TIEBA_V12_VERSION,
        @Header("Referer") referer: String,
    ): SearchEnvelope

    @GET("mo/q/newmoindex")
    suspend fun profile(
        @Query("need_user") needUser: Int = 1,
        @Header("Cookie") cookie: String,
    ): ProfileEnvelope
}

internal data class SearchEnvelope(
    @SerializedName("no") val errorCode: Int = -1,
    @SerializedName("error") val errorMessage: String = "",
    val data: SearchData? = null,
)

internal data class SearchData(
    @SerializedName("has_more") val hasMore: Int = 0,
    @SerializedName("post_list") val posts: List<SearchPost> = emptyList(),
)

internal data class SearchPost(
    val tid: String = "",
    val title: String = "",
    val content: String = "",
    val time: Long = 0,
    val user: SearchUser? = null,
    @SerializedName("post_num") val replyCount: String = "",
    val media: List<SearchMedia>? = null,
    @SerializedName("forum_id") val forumId: Long = TARGET_FORUM_ID,
    @SerializedName("forum_name") val forumName: String = "",
    @SerializedName("forum_info") val forumInfo: SearchForumInfo? = null,
)

internal data class SearchUser(
    @SerializedName("user_id") val id: Long = 0,
    @SerializedName("user_name") val name: String = "",
    @SerializedName("show_nickname") val nickname: String = "",
    val portrait: String = "",
)

internal data class SearchMedia(
    val type: String = "",
    val src: String = "",
    @SerializedName("small_pic") val smallPic: String = "",
    @SerializedName("big_pic") val bigPic: String = "",
    @SerializedName("water_pic") val waterPic: String = "",
)

internal data class SearchForumInfo(
    @SerializedName("forum_name") val name: String = "",
)

internal data class ProfileEnvelope(
    @SerializedName("no") val errorCode: Int = -1,
    @SerializedName("error") val errorMessage: String = "",
    val data: ProfileData? = null,
)

internal data class ProfileData(
    @SerializedName("itb_tbs") val itbTbs: String = "",
    val tbs: String = "",
    @SerializedName("portrait_url") val avatarUrl: String = "",
    val uid: Long = 0,
    @SerializedName("name_show") val nickname: String = "",
    val intro: String = "",
    val name: String = "",
    @SerializedName("concern_num") val concerned: String = "0",
    @SerializedName("fans_num") val fans: String = "0",
    @SerializedName("post_num") val posts: String = "0",
    @SerializedName("is_login") val isLoggedIn: Boolean = false,
)

data class LoginCookies(
    val bduss: String,
    val sToken: String,
    val baiduId: String?,
    val raw: String,
)

data class SignResponse(
    val outcome: SignOutcome,
    val message: String,
    val signedDays: Int? = null,
)

internal fun mapOfficialSignResult(result: TiebaSignResultBean): SignResponse {
    val code = result.errorCode?.toIntOrNull()
    val message = result.errorMsg.orEmpty()
    val userInfo = result.userInfo
    val days = userInfo?.contSignNum
    return when {
        code == 0 && userInfo != null &&
            userInfo.signBonusPoint != null && userInfo.userSignRank != null -> SignResponse(
                SignOutcome.SUCCESS,
                days?.let { "已签${it}天" } ?: "签到成功",
                days,
            )
        code == 1101 || message.contains("已签") -> SignResponse(
            SignOutcome.ALREADY_SIGNED,
            days?.let { "已签${it}天" } ?: "今日已经签到",
            days,
        )
        code == 1004 || message.contains("未关注") -> SignResponse(
            SignOutcome.FAILED,
            "尚未关注长春工程学院吧",
        )
        code == 0 -> SignResponse(SignOutcome.FAILED, "签到响应数据无效")
        else -> SignResponse(
            SignOutcome.FAILED,
            message.ifBlank { code?.let { "签到失败（$it）" } ?: "签到失败（响应缺少错误码）" },
        )
    }
}

internal fun mapOfficialSignFailure(code: Int, message: String): SignResponse = when {
    code == 1101 || message.contains("已签") -> SignResponse(SignOutcome.ALREADY_SIGNED, "今日已经签到")
    code == 1004 || message.contains("未关注") -> SignResponse(SignOutcome.FAILED, "尚未关注长春工程学院吧")
    else -> SignResponse(SignOutcome.FAILED, message.ifBlank { "签到失败（$code）" })
}

private data class UploadPictureEnvelope(
    @SerializedName("error_code") val errorCode: String = "-1",
    @SerializedName("error_msg") val errorMessage: String = "",
    val picId: String? = null,
    val picInfo: UploadPictureInfo? = null,
)

private data class UploadPictureInfo(val originPic: UploadPictureSize? = null)
private data class UploadPictureSize(val width: String = "0", val height: String = "0")

class TiebaNetworkRepository internal constructor(
    private val context: Context,
    private val client: OkHttpClient,
    private val supportApi: TiebaSupportApi,
    private val readApi: TiebaReadApi,
    private val readRequests: TiebaReadRequestFactory,
    private val picPageApi: TiebaPicPageApi,
    private val picPageRequests: TiebaPicPageRequestFactory,
    private val gson: Gson,
    private val settings: TiebaSettingsRepository,
    private val officialClientFactory: (AccountEntity?, TiebaClientConfig) -> TiebaOfficialClient = { account, config ->
        TiebaOfficialClient(context, gson, account, config)
    },
) {
    private val identity = TiebaClientIdentity(context)
    /** Mirrors TiebaLite's photo-view initialization and returns a fresh, signed original URL. */
    suspend fun resolveOriginalImage(
        data: LoadPicPageData,
        account: AccountEntity? = null,
    ): String = runRead {
        val response = picPageApi.picPage(
            picPageRequests.picPage(data, account?.toReadCredentials()),
        )
        if (response.errorCode != "0") {
            throw TiebaReadFailure.Service(IOException("PicPage API ${response.errorCode}"))
        }
        val picture = response.picList.firstOrNull { it.img.original.id == data.picId }
            ?: response.picList.firstOrNull { it.overallIndex.toIntOrNull() == data.picIndex }
            ?: throw TiebaReadFailure.Data()
        picture.img.bestQualitySrc().takeIf(::isAuthorizedTiebaImageUrl)
            ?: throw TiebaReadFailure.Data()
    }

    suspend fun loadForum(
        page: Int,
        sort: ForumSort,
        goodOnly: Boolean,
        account: AccountEntity? = null,
    ): ForumPage = runRead {
        val credentials = account?.toReadCredentials()
        val sortType = if (sort == ForumSort.BY_REPLY) 0 else 1
        val loadType = if (page <= 1) 1 else 2
        val resolved = readWithAnonymousRetry(credentials, { it.errorCode() }) { attempt ->
            readForum(page, sortType, goodOnly, loadType, attempt)
        }
        resolved.requireSuccess("FRS")
        mapForum(resolved, page)
    }

    suspend fun search(keyword: String, page: Int): ForumPage = runRead {
        val encodedName = TiebaReadRequestFactory.encodedForumName()
        val referer = "https://tieba.baidu.com/mo/q/hybrid-usergrow-search/searchGlobal" +
            "?entryPage=frs&loadingSignal=1&forumName=$encodedName&forumId=$TARGET_FORUM_ID" +
            "&customfullscreen=1&nonavigationbar=1&timestamp=${System.currentTimeMillis()}" +
            "&_client_version=$TIEBA_V12_VERSION&_client_type=2"
        val response = supportApi.search(keyword.trim(), page, referer = referer)
        if (response.errorCode != 0) throw TiebaReadFailure.Service(IOException("Search API ${response.errorCode}"))
        val data = response.data ?: throw TiebaReadFailure.Data()
        val threads = data.posts.asSequence()
            .filter { canonicalForumName(it.forumName.ifBlank { it.forumInfo?.name.orEmpty() }) == TARGET_FORUM_NAME }
            .mapNotNull { post ->
                post.tid.takeIf(String::isNotBlank)?.let { id ->
                    ForumThread(
                        id = id,
                        title = post.title.ifBlank { "无标题" },
                        excerpt = plainText(post.content),
                        authorName = post.user?.name.orEmpty(),
                        authorNickname = post.user?.nickname.orEmpty(),
                        authorPortrait = portraitUrl(post.user?.portrait.orEmpty()),
                        replyCount = post.replyCount.toIntOrNull()
                            ?.let(::replyCountExcludingFirstFloor)
                            ?.toString()
                            ?: post.replyCount,
                        viewCount = "",
                        lastReplyTime = formatEpoch(post.time),
                        isTop = false,
                        isGood = false,
                        imageUrls = post.media.orEmpty().filter { it.type == "pic" }
                            .mapNotNull { media ->
                                sequenceOf(media.bigPic, media.smallPic, media.waterPic, media.src)
                                    .firstOrNull(String::isNotBlank)
                            }
                            .map(::normalizeTiebaUrl).distinctBy(::tiebaImageIdentity),
                        authorId = post.user?.id ?: 0,
                        forumId = post.forumId.takeIf { it > 0 } ?: TARGET_FORUM_ID,
                        forumName = post.forumName.ifBlank { post.forumInfo?.name.orEmpty() }.ifBlank { TARGET_FORUM_NAME },
                    )
                }
            }.distinctBy(ForumThread::id).toList()
        ForumPage(ForumSummary(), threads, page, data.hasMore == 1)
    }

    suspend fun loadThread(
        threadId: String,
        page: Int,
        sort: FloorSort,
        onlyOriginalPoster: Boolean,
        account: AccountEntity? = null,
        forumId: Long = TARGET_FORUM_ID,
        forumName: String = TARGET_FORUM_NAME,
        focusPostId: String? = null,
    ): ThreadPage = runRead {
        val parsedThreadId = threadId.toLongOrNull()?.takeIf { it > 0 } ?: throw TiebaReadFailure.Data()
        val credentials = account?.toReadCredentials()
        val sortType = when (sort) {
            FloorSort.ASCENDING -> 0
            FloorSort.DESCENDING -> 1
            FloorSort.HOT -> 2
        }
        val resolved = readWithAnonymousRetry(credentials, { it.errorCode() }) { attempt ->
            readThread(
                parsedThreadId,
                page,
                sortType,
                onlyOriginalPoster,
                focusPostId?.toLongOrNull() ?: 0,
                forumId,
                attempt,
            )
        }
        resolved.requireSuccess("PB")
        mapThread(resolved, parsedThreadId, page, forumId, forumName)
    }

    suspend fun loadFloorReplies(
        threadId: String,
        postId: String,
        page: Int,
        subPostId: String? = null,
        account: AccountEntity? = null,
        forumId: Long = TARGET_FORUM_ID,
        forumName: String = TARGET_FORUM_NAME,
    ): FloorReplyPage = runRead {
        val tid = threadId.toLongOrNull()?.takeIf { it > 0 } ?: throw TiebaReadFailure.Data()
        val pid = postId.toLongOrNull()?.takeIf { it > 0 } ?: throw TiebaReadFailure.Data()
        val spid = subPostId?.toLongOrNull() ?: 0L
        val credentials = account?.toReadCredentials()
        val resolved = readWithAnonymousRetry(credentials, { it.errorCode() }) { attempt ->
            readFloor(tid, pid, page, spid, forumId, attempt)
        }
        resolved.requireSuccess("PB_FLOOR")
        val data = resolved.data_ ?: throw TiebaReadFailure.Data()
        requireExpectedForum(data.forum?.id, data.forum?.name, forumId, forumName)
        val responsePage = data.page ?: throw TiebaReadFailure.Data()
        FloorReplyPage(
            replies = data.subpost_list.map { mapReply(it, emptyMap()) }
                .distinctBy { it.id.ifBlank { "${it.authorName}:${it.time}:${it.content}" } },
            page = responsePage.current_page.takeIf { it > 0 } ?: page,
            totalPages = responsePage.total_page.coerceAtLeast(page),
        )
    }

    /** Sends a reply entirely in-app using the same protobuf endpoint and field mapping as TiebaLite. */
    suspend fun addReply(
        content: String,
        forumId: Long,
        forumName: String,
        threadId: Long,
        postId: Long? = null,
        subPostId: Long? = null,
        replyUserId: Long? = null,
        replyUserName: String = "",
        replyUserPortrait: String = "",
        account: AccountEntity,
    ): TiebaReplyResult = runRead {
        val body = content.trim()
        if (body.isBlank() || threadId <= 0 || forumId <= 0) throw TiebaReadFailure.Data()
        val credentials = account.toReadCredentials()
        val response = readApi.addPost(
            body = readRequests.addPost(
                content = if (subPostId != null) {
                    "回复 #(reply, ${replyPortraitToken(replyUserPortrait)}, $replyUserName) :$body"
                } else {
                    body
                },
                forumId = forumId,
                forumName = forumName,
                threadId = threadId,
                postId = postId,
                subPostId = subPostId,
                replyUserId = replyUserId,
                nickname = account.nickname,
                tbs = account.tbs,
                credentials = credentials,
            ),
            userToken = account.uid.toString(),
        )
        val code = response.error?.error_code ?: 0
        if (code != 0) {
            throw IOException(response.error?.error_msg.orEmpty().ifBlank { "回复失败（$code）" })
        }
        val result = response.data_ ?: throw TiebaReadFailure.Data()
        TiebaReplyResult(
            threadId = result.tid.toLongOrNull() ?: threadId,
            postId = result.pid.toLongOrNull() ?: throw TiebaReadFailure.Data(),
            experienceAdded = result.exp?.inc.orEmpty(),
        )
    }

    /** Chunked image upload copied from TiebaLite's ImageUploader contract. */
    suspend fun uploadReplyImages(
        imageUris: List<Uri>,
        forumName: String,
        saveOriginal: Boolean,
        account: AccountEntity,
    ): List<TiebaUploadedImage> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        require(imageUris.isNotEmpty() && imageUris.size <= 9)
        imageUris.mapIndexed { index, uri ->
            val temporary = File.createTempFile("tieba_reply_${index}_", ".img", context.cacheDir)
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    temporary.outputStream().use(input::copyTo)
                } ?: throw IOException("无法读取所选图片")
                val maxSize = if (saveOriginal) 10L * 1024 * 1024 else 5L * 1024 * 1024
                if (temporary.length() <= 0 || temporary.length() > maxSize) {
                    throw IOException("图片大小不能超过 ${maxSize / 1024 / 1024} MB")
                }
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(temporary.path, options)
                if (options.outWidth <= 0 || options.outHeight <= 0) throw IOException("图片格式不受支持")
                uploadReplyImage(
                    file = temporary,
                    width = options.outWidth,
                    height = options.outHeight,
                    forumName = forumName,
                    saveOriginal = saveOriginal,
                    account = account,
                )
            } finally {
                temporary.delete()
            }
        }
    }

    private suspend fun uploadReplyImage(
        file: File,
        width: Int,
        height: Int,
        forumName: String,
        saveOriginal: Boolean,
        account: AccountEntity,
    ): TiebaUploadedImage {
        val fileLength = file.length()
        val digest = MessageDigest.getInstance("MD5")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        val md5 = digest.digest().joinToString("") { "%02x".format(it) }
        val chunks = ((fileLength + TIEBA_UPLOAD_CHUNK_SIZE - 1) / TIEBA_UPLOAD_CHUNK_SIZE).toInt()
        var finalResult: UploadPictureEnvelope? = null
        RandomAccessFile(file, "r").use { random ->
            repeat(chunks) { chunkIndex ->
                val remaining = fileLength - chunkIndex.toLong() * TIEBA_UPLOAD_CHUNK_SIZE
                val chunkSize = minOf(TIEBA_UPLOAD_CHUNK_SIZE.toLong(), remaining).toInt()
                val bytes = ByteArray(chunkSize)
                random.readFully(bytes)
                val isFinish = chunkIndex == chunks - 1
                val common = officialCommonFields(account) + mapOf(
                    "stoken" to account.sToken,
                    "tbs" to account.tbs,
                )
                val multipart = MultipartBody.Builder(TIEBA_UPLOAD_BOUNDARY)
                    .setType(MultipartBody.FORM)
                    .apply {
                        common.forEach { (name, value) -> addFormDataPart(name, value) }
                        addFormDataPart("alt", "json")
                        addFormDataPart("chunkNo", "${chunkIndex + 1}")
                        addFormDataPart("forum_name", forumName)
                        addFormDataPart("groupId", "1")
                        addFormDataPart("height", height.toString())
                        addFormDataPart("isFinish", if (isFinish) "1" else "0")
                        addFormDataPart("is_bjh", "0")
                        addFormDataPart("pic_water_type", "0")
                        addFormDataPart("resourceId", "$md5$TIEBA_UPLOAD_CHUNK_SIZE")
                        addFormDataPart("saveOrigin", if (saveOriginal) "1" else "0")
                        addFormDataPart("size", fileLength.toString())
                        addFormDataPart("small_flow_fname", forumName)
                        addFormDataPart("width", width.toString())
                        addFormDataPart("chunk", "file", bytes.toRequestBody("application/octet-stream".toMediaType()))
                    }
                    .build()
                val request = Request.Builder()
                    .url("https://c.tieba.baidu.com/c/s/uploadPicture")
                    .header("User-Agent", "bdtb for Android $OFFICIAL_WRITE_VERSION")
                    .header("Cookie", "CUID=${identity.cuid};ka=open;TBBRAND=${Build.MODEL};")
                    .header("client_user_token", account.uid.toString())
                    .header("cuid", identity.cuid)
                    .header("cuid_galaxy2", identity.cuid)
                    .header("cuid_gid", "")
                    .header("c3_aid", identity.aid)
                    .post(multipart)
                    .build()
                val result = gson.fromJson(execute(request), UploadPictureEnvelope::class.java)
                if (result.errorCode.toIntOrNull() != 0) {
                    throw IOException(result.errorMessage.ifBlank { "图片上传失败（${result.errorCode}）" })
                }
                finalResult = result
            }
        }
        val result = finalResult ?: throw IOException("图片上传失败")
        val pictureId = result.picId?.takeIf(String::isNotBlank) ?: throw IOException("图片上传结果无效")
        return TiebaUploadedImage(
            picId = pictureId,
            width = result.picInfo?.originPic?.width?.toIntOrNull() ?: width,
            height = result.picInfo?.originPic?.height?.toIntOrNull() ?: height,
        )
    }

    suspend fun loadForumRule(account: AccountEntity? = null): ForumRule = runRead {
        val response = readApi.forumRule(readRequests.forumRule(TARGET_FORUM_ID, account?.toReadCredentials()))
        val code = response.error?.error_code ?: 0
        if (code != 0) apiFailure("FORUM_RULE", code)
        val data = response.data_ ?: throw TiebaReadFailure.Data()
        ForumRule(
            title = data.title,
            publishTime = data.publish_time,
            preface = data.preface,
            rules = data.rules.map { rule ->
                ForumRuleItem(rule.title, rule.content.mapTiebaContent().plainText())
            },
            authorName = data.bazhu?.name_show.orEmpty().ifBlank { data.bazhu?.user_name.orEmpty() },
            authorPortrait = portraitUrl(data.bazhu?.portrait.orEmpty()),
        )
    }

    suspend fun loadUserProfile(uid: Long, account: AccountEntity? = null): TiebaUserProfile = runRead {
        if (uid <= 0) throw TiebaReadFailure.Data()
        val credentials = account?.toReadCredentials()
        val response = readWithAnonymousRetry(credentials, { it.errorCode() }) { attempt ->
            readApi.profile(readRequests.profile(uid, attempt), attempt?.uid?.toString())
        }
        response.requireSuccess("PROFILE")
        mapUserProfile(response)
    }

    suspend fun loadUserPosts(
        uid: Long,
        page: Int,
        isThread: Boolean,
        account: AccountEntity? = null,
    ): TiebaUserPostPage = runRead {
        if (uid <= 0 || page < 1) throw TiebaReadFailure.Data()
        val credentials = account?.toReadCredentials()
        val response = readWithAnonymousRetry(credentials, { it.errorCode() }) { attempt ->
            readApi.userPosts(readRequests.userPosts(uid, page, isThread, attempt), attempt?.uid?.toString())
        }
        response.requireSuccess("USER_POST")
        mapUserPosts(response, page, isThread)
    }

    suspend fun loadAccount(cookies: LoginCookies): AccountEntity {
        val response = supportApi.profile(cookie = cookies.raw)
        val profile = response.data
        if (response.errorCode != 0 || profile == null || !profile.isLoggedIn) {
            throw IOException(response.errorMessage.ifBlank { "贴吧账号登录状态无效" })
        }
        return AccountEntity(
            uid = profile.uid,
            name = profile.name,
            nickname = profile.nickname.ifBlank { profile.name },
            bduss = cookies.bduss,
            tbs = profile.tbs.ifBlank { profile.itbTbs },
            portrait = originalImageUrl(profile.avatarUrl),
            sToken = cookies.sToken,
            cookie = cookies.raw,
            intro = profile.intro,
            fans = profile.fans,
            posts = profile.posts,
            concerned = profile.concerned,
        )
    }

    suspend fun refreshForumTbs(account: AccountEntity): String = runRead {
        val response = readForum(
            page = 1,
            sortType = 0,
            goodOnly = false,
            loadType = 1,
            credentials = account.toReadCredentials(),
        )
        response.requireSuccess("FORUM_SIGN_TBS")
        val data = response.data_ ?: throw TiebaReadFailure.Data()
        requireTargetForum(data.forum?.id, data.forum?.name)
        data.anti?.tbs?.takeIf(String::isNotBlank)
            ?: throw IOException("签到凭据无效，请刷新贴吧页面后重试")
    }

    /** Automatic sign-in must consume the anti.tbs from this exact FRS response. */
    suspend fun signWithFreshForumState(account: AccountEntity): SignResponse =
        sign(account, refreshForumTbs(account))

    /** Mirrors TiebaLite's OfficialTiebaApi.signFlow and uses FRS data.anti.tbs only. */
    suspend fun sign(account: AccountEntity, tbs: String): SignResponse {
        if (tbs.isBlank()) return SignResponse(SignOutcome.FAILED, "签到凭据无效，请刷新贴吧页面后重试")
        val config = settings.clientConfig(account.cookie.cookieValue("BAIDUID"))
        return try {
            val result = officialClientFactory(account, config).sign(
                TARGET_FORUM_ID.toString(),
                TARGET_FORUM_NAME,
                tbs,
            )
            if (
                result.errorCode?.toIntOrNull() == 0 &&
                (result.userInfo == null || result.userInfo.signBonusPoint == null ||
                    result.userInfo.userSignRank == null)
            ) {
                Log.w(
                    "TiebaSign",
                    "invalid response code=0 hasUserInfo=${result.userInfo != null} " +
                        "hasBonus=${result.userInfo?.signBonusPoint != null} " +
                        "hasRank=${result.userInfo?.userSignRank != null}",
                )
            }
            mapOfficialSignResult(result)
        } catch (error: TiebaApiException) {
            mapOfficialSignFailure(error.code, error.message)
        }
    }

    /** TiebaLite starts this sync opportunistically; failures are deliberately handled by the caller. */
    suspend fun syncClientConfig(account: AccountEntity?) {
        val config = settings.clientConfig(account?.cookie?.cookieValue("BAIDUID"))
        val result = officialClientFactory(account, config).sync()
        settings.updateClientSync(result.client.clientId, result.wlConfig.sampleId)
    }

    private fun officialCommonFields(account: AccountEntity): Map<String, String> {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            .orEmpty().ifBlank { "000" }
        return linkedMapOf(
            "BDUSS" to account.bduss,
            "_client_id" to identity.clientId,
            "_client_type" to "2",
            "_client_version" to OFFICIAL_WRITE_VERSION,
            "_os_version" to Build.VERSION.SDK_INT.toString(),
            "_phone_imei" to "",
            "_timestamp" to System.currentTimeMillis().toString(),
            "active_timestamp" to identity.firstInstallTime.toString(),
            "android_id" to Base64.encodeToString(androidId.toByteArray(), Base64.NO_WRAP),
            "brand" to Build.BRAND,
            "c3_aid" to identity.aid,
            "cmode" to "1",
            "cuid" to identity.cuid,
            "cuid_galaxy2" to identity.cuid,
            "cuid_gid" to "",
            "event_day" to java.text.SimpleDateFormat("yyyyMdd", Locale.getDefault()).format(Date()),
            "extra" to "",
            "first_install_time" to identity.firstInstallTime.toString(),
            "framework_ver" to "3340042",
            "from" to "tieba",
            "is_teenager" to "0",
            "last_update_time" to context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime.toString(),
            "mac" to "02:00:00:00:00:00",
            "model" to Build.MODEL,
            "net_type" to "1",
            "oaid" to "",
            "sdk_ver" to "2.34.0",
            "start_scheme" to "",
            "start_type" to "1",
            "swan_game_ver" to "1038000",
        )
    }

    private suspend fun readForum(
        page: Int,
        sortType: Int,
        goodOnly: Boolean,
        loadType: Int,
        credentials: TiebaReadCredentials?,
    ) = readApi.forum(
        body = readRequests.forum(page, sortType, goodOnly, loadType, credentials),
        forumName = TiebaReadRequestFactory.encodedForumName(),
        userToken = credentials?.uid?.toString(),
    )

    private suspend fun readThread(
        threadId: Long,
        page: Int,
        sortType: Int,
        onlyOriginalPoster: Boolean,
        focusPostId: Long,
        forumId: Long,
        credentials: TiebaReadCredentials?,
    ) = readApi.thread(
        body = readRequests.thread(
            threadId,
            page,
            sortType,
            onlyOriginalPoster,
            credentials,
            postId = focusPostId,
            forumId = forumId,
        ),
        userToken = credentials?.uid?.toString(),
    )

    private suspend fun readFloor(
        threadId: Long,
        postId: Long,
        page: Int,
        subPostId: Long,
        forumId: Long,
        credentials: TiebaReadCredentials?,
    ) = readApi.floor(
        body = readRequests.floor(threadId, postId, page, subPostId, credentials, forumId),
        userToken = credentials?.uid?.toString(),
    )

    private suspend fun <T> readWithAnonymousRetry(
        credentials: TiebaReadCredentials?,
        errorCode: (T) -> Int,
        request: suspend (TiebaReadCredentials?) -> T,
    ): T {
        if (credentials == null) return request(null)
        val authenticated = try {
            request(credentials)
        } catch (failure: HttpException) {
            if (failure.code() == 401 || failure.code() == 403) return request(null)
            throw failure
        }
        return if (errorCode(authenticated) != 0) request(null) else authenticated
    }

    private fun mapForum(response: FrsPageResponse, requestedPage: Int): ForumPage {
        val data = response.data_ ?: throw TiebaReadFailure.Data()
        val forum = data.forum ?: throw TiebaReadFailure.Data()
        requireTargetForum(forum.id, forum.name)
        val users = data.user_list.associateBy(User::id)
        val threads = data.thread_list.asSequence()
            .filter { it.ala_info == null && it.forumInfo != null }
            .filter { canonicalForumName(it.forumInfo?.name.orEmpty()) == TARGET_FORUM_NAME && it.forumInfo?.id == TARGET_FORUM_ID }
            .mapNotNull { mapForumThread(it, users) }
            .distinctBy(ForumThread::id)
            .toList()
        val page = data.page ?: throw TiebaReadFailure.Data()
        return ForumPage(
            forum = ForumSummary(
                id = forum.id.toString(),
                name = forum.name,
                tbs = data.anti?.tbs.orEmpty(),
                slogan = forum.slogan.ifBlank {
                    listOf(forum.first_class, forum.second_class).filter(String::isNotBlank).joinToString(" · ")
                },
                avatarUrl = forum.avatar.takeIf(String::isNotBlank)?.let(::toOriginalTiebaImage).orEmpty(),
                memberCount = forum.member_num.toString(),
                threadCount = forum.thread_num.toString(),
                postCount = forum.post_num.toString(),
                isFollowed = forum.is_like == 1,
                forumRuleTitle = data.forum_rule?.title.orEmpty(),
                signed = forum.sign_in_info?.user_info?.is_sign_in == 1,
                signedDays = forum.sign_in_info?.user_info?.cont_sign_num ?: 0,
            ),
            threads = threads,
            page = page.current_page.takeIf { it > 0 } ?: requestedPage,
            hasMore = page.has_more == 1 || page.total_page > requestedPage,
        )
    }

    private fun mapForumThread(source: ThreadInfo, users: Map<Long, User>): ForumThread? {
        val id = source.threadId.takeIf { it > 0 } ?: source.id.takeIf { it > 0 } ?: return null
        val author = users[source.authorId] ?: source.author
        val richExcerpt = source.richAbstract.mapTiebaContent()
        val images = buildList {
            source.media.forEach { media ->
                sequenceOf(media.originPic, media.bigPic, media.srcPic)
                    .firstOrNull(String::isNotBlank)?.let(::add)
            }
            source.richAbstract.filter { it.type == 3 || it.type == 20 }
                .forEach { content -> content.originalImage()?.let(::add) }
        }.map(::normalizeTiebaUrl).distinctBy(::tiebaImageIdentity)
        return ForumThread(
            id = id.toString(),
            title = source.title.ifBlank { "无标题" },
            excerpt = source.richAbstract.mapTiebaContent().plainText().ifBlank {
                source._abstract.joinToString("") { it.text }.let(::plainText)
            },
            authorName = author?.name.orEmpty(),
            authorNickname = author?.nameShow.orEmpty(),
            authorPortrait = portraitUrl(author?.portrait.orEmpty()),
            replyCount = replyCountExcludingFirstFloor(source.replyNum).toString(),
            viewCount = source.viewNum.toString(),
            lastReplyTime = source.lastTime,
            isTop = source.isTop == 1,
            isGood = source.isGood == 1,
            imageUrls = images,
            videoUrl = source.videoInfo?.videoUrl?.takeIf(String::isNotBlank),
            authorId = author?.id ?: source.authorId,
            forumId = source.forumInfo?.id ?: TARGET_FORUM_ID,
            forumName = source.forumInfo?.name.orEmpty().ifBlank { TARGET_FORUM_NAME },
            richExcerpt = richExcerpt,
            authorIsManager = author?.is_manager == 1 || author?.is_bawu == 1,
        )
    }

    private fun mapThread(
        response: PbPageResponse,
        threadId: Long,
        requestedPage: Int,
        expectedForumId: Long,
        expectedForumName: String,
    ): ThreadPage {
        val data = response.data_ ?: throw TiebaReadFailure.Data()
        val forum = data.forum ?: throw TiebaReadFailure.Data()
        requireExpectedForum(forum.id, forum.name, expectedForumId, expectedForumName)
        val page = data.page ?: throw TiebaReadFailure.Data()
        val thread = data.thread ?: throw TiebaReadFailure.Data()
        if (data.anti == null || thread.author == null) throw TiebaReadFailure.Data()
        val users = data.user_list.associateBy(User::id)
        val mappedPosts = data.post_list.map { mapPost(it, users) }.distinctBy(ThreadFloor::postId)
        val body = data.first_floor_post?.let { mapPost(it, users) }
            ?: mappedPosts.firstOrNull { it.floor == 1 }
        val floors = mappedPosts.filterNot { it.postId == body?.postId || it.floor == 1 }
        return ThreadPage(
            threadId = threadId.toString(),
            title = thread.title.ifBlank { "帖子" },
            floors = floors,
            page = page.current_page.takeIf { it > 0 } ?: requestedPage,
            totalPages = page.total_page.coerceAtLeast(requestedPage),
            replyCount = replyCountExcludingFirstFloor(thread.replyNum),
            body = body,
            forumId = forum.id,
            forumName = forum.name,
        )
    }

    private fun mapPost(post: Post, users: Map<Long, User>): ThreadFloor {
        val author = post.author ?: users[post.author_id] ?: throw TiebaReadFailure.Data()
        val content = post.content
        val richContent = content.mapTiebaContent()
        return ThreadFloor(
            postId = post.id.toString(),
            floor = post.floor,
            authorName = author.name,
            authorNickname = author.nameShow,
            authorPortrait = portraitUrl(author.portrait),
            content = richContent.plainText(),
            time = formatEpoch(post.time.toLong()),
            imageUrls = richContent.filterIsInstance<TiebaContent.Image>().map(TiebaContent.Image::originalUrl).distinct(),
            videoUrls = buildList {
                post.video_info?.videoUrl?.takeIf(String::isNotBlank)?.let { add(normalizeTiebaUrl(it)) }
                richContent.filterIsInstance<TiebaContent.Video>().mapTo(this) { it.url }
            }.distinct(),
            replyCount = post.sub_post_number,
            replies = post.sub_post_list?.sub_post_list.orEmpty().map { mapReply(it, users) },
            richContent = richContent,
            authorId = author.id,
            authorLevel = author.level_id,
            authorTitle = author.level_name,
            authorIp = author.ip_address.ifBlank { author.ip },
            authorIsManager = author.is_manager == 1 || author.is_bawu == 1,
        )
    }

    private fun mapReply(reply: SubPostList, users: Map<Long, User>): FloorReply {
        val author = reply.author ?: users[reply.author_id]
        val richContent = reply.content.mapTiebaContent()
        return FloorReply(
            id = reply.id.takeIf { it > 0 }?.toString().orEmpty(),
            authorName = author?.name.orEmpty(),
            authorNickname = author?.nameShow.orEmpty(),
            authorPortrait = portraitUrl(author?.portrait.orEmpty()),
            content = richContent.plainText(),
            time = formatEpoch(reply.time.toLong()),
            richContent = richContent,
            authorId = author?.id ?: reply.author_id,
            authorLevel = author?.level_id ?: 0,
            authorTitle = author?.level_name.orEmpty(),
            authorIp = author?.ip_address.orEmpty().ifBlank { author?.ip.orEmpty() },
            authorIsManager = author?.is_manager == 1 || author?.is_bawu == 1,
        )
    }

    private fun mapUserProfile(response: ProfileResponse): TiebaUserProfile {
        val user = response.data_?.user ?: throw TiebaReadFailure.Data()
        return TiebaUserProfile(
            uid = user.id,
            username = user.name,
            nickname = user.nameShow.ifBlank { user.name },
            avatarUrl = portraitUrl(user.portraith.ifBlank { user.portrait }),
            intro = user.display_intro.ifBlank { user.intro },
            sex = when (user.sex) {
                1 -> "男"
                2 -> "女"
                else -> "未知"
            },
            tiebaAge = user.tb_age,
            address = user.ip_address,
            threadCount = user.thread_num,
            postCount = user.post_num,
            forumCount = user.my_like_num,
            followingCount = user.concern_num,
            fansCount = user.fans_num,
            agreeCount = user.total_agree_num.toInt(),
            isOfficial = user.is_guanfang == 1,
            followedForumsPrivate = user.privSets?.like != 1,
            followedForums = user.likeForum.mapNotNull { forum ->
                forum.forum_id.takeIf { it > 0 }?.let { TiebaUserForum(it, forum.forum_name) }
            }.distinctBy(TiebaUserForum::id),
        )
    }

    private fun mapUserPosts(response: UserPostResponse, page: Int, isThread: Boolean): TiebaUserPostPage {
        val rawPosts = response.data_?.post_list ?: throw TiebaReadFailure.Data()
        val posts = if (isThread) {
            rawPosts.map(::mapUserThread)
        } else {
            rawPosts.flatMap(::mapUserReplies)
        }.distinctBy(TiebaUserPost::key)
        return TiebaUserPostPage(posts, page, rawPosts.size >= 20)
    }

    private fun mapUserThread(post: PostInfoList): TiebaUserPost {
        // TiebaLite treats the explicit media list as authoritative. The rich abstract often
        // repeats the same image through a different CDN path, which otherwise creates a
        // duplicated two-column preview on user profile pages.
        val mediaImages = post.media.mapNotNull { media ->
            sequenceOf(media.originPic, media.bigPic, media.srcPic)
                .firstOrNull(String::isNotBlank)
        }
        val images = (mediaImages.ifEmpty {
            post.rich_abstract.filter { it.type == 3 || it.type == 20 }
                .mapNotNull { it.originalImage() }
        }).map(::normalizeTiebaUrl).distinctBy(::tiebaImageIdentity)
        val excerpt = post.rich_abstract.mapTiebaContent().plainText().ifBlank {
            post._abstract.ifBlank {
                post.abstract_thread.joinToString("") { it.text }
            }
        }
        return TiebaUserPost(
            key = "thread:${post.thread_id}",
            threadId = post.thread_id.toLong(),
            postId = post.post_id.toLong(),
            forumId = post.forum_id.toLong(),
            forumName = post.forum_name,
            title = post.title.ifBlank { "无标题" },
            excerpt = plainText(excerpt),
            time = formatEpoch(post.create_time.toLong()),
            replyCount = post.reply_num,
            imageUrls = images,
            isReply = false,
        )
    }

    private fun mapUserReplies(post: PostInfoList): List<TiebaUserPost> {
        val contents = post.content
        if (contents.isEmpty()) {
            return listOf(
                TiebaUserPost(
                    key = "reply:${post.thread_id}:${post.post_id}",
                    threadId = post.thread_id.toLong(),
                    postId = post.post_id.toLong(),
                    forumId = post.forum_id.toLong(),
                    forumName = post.forum_name,
                    title = post.title.ifBlank { "原帖" },
                    excerpt = plainText(post._abstract),
                    time = formatEpoch(post.create_time.toLong()),
                    replyCount = post.reply_num,
                    imageUrls = emptyList(),
                    isReply = true,
                ),
            )
        }
        return contents.map { content ->
            TiebaUserPost(
                key = "reply:${post.thread_id}:${content.post_id}",
                threadId = post.thread_id.toLong(),
                postId = content.post_id.toLong(),
                forumId = post.forum_id.toLong(),
                forumName = post.forum_name,
                title = post.title.ifBlank { "原帖" },
                excerpt = content.post_content.joinToString("") { it.text }.let(::plainText),
                time = formatEpoch(content.create_time.toLong()),
                replyCount = post.reply_num,
                imageUrls = emptyList(),
                isReply = true,
            )
        }
    }

    private fun requireTargetForum(id: Long?, name: String?) {
        if (id != TARGET_FORUM_ID || canonicalForumName(name.orEmpty()) != TARGET_FORUM_NAME) {
            throw TiebaReadFailure.WrongForum()
        }
    }

    private fun requireExpectedForum(id: Long?, name: String?, expectedId: Long, expectedName: String) {
        val idMatches = expectedId <= 0 || id == expectedId
        val nameMatches = expectedName.isBlank() || canonicalForumName(name.orEmpty()) == canonicalForumName(expectedName)
        if (!idMatches || !nameMatches) throw TiebaReadFailure.WrongForum()
    }

    private fun FrsPageResponse.errorCode(): Int = error?.error_code ?: 0
    private fun PbPageResponse.errorCode(): Int = error?.error_code ?: 0
    private fun PbFloorResponse.errorCode(): Int = error?.error_code ?: 0
    private fun ProfileResponse.errorCode(): Int = error?.error_code ?: 0
    private fun UserPostResponse.errorCode(): Int = error?.error_code ?: 0

    private fun FrsPageResponse.requireSuccess(kind: String) {
        if (errorCode() != 0) apiFailure(kind, errorCode())
    }

    private fun PbPageResponse.requireSuccess(kind: String) {
        if (errorCode() != 0) apiFailure(kind, errorCode())
    }

    private fun PbFloorResponse.requireSuccess(kind: String) {
        if (errorCode() != 0) apiFailure(kind, errorCode())
    }

    private fun ProfileResponse.requireSuccess(kind: String) {
        if (errorCode() != 0) apiFailure(kind, errorCode())
    }

    private fun UserPostResponse.requireSuccess(kind: String) {
        if (errorCode() != 0) apiFailure(kind, errorCode())
    }

    private fun apiFailure(kind: String, code: Int): Nothing {
        if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            Log.d("TiebaRead", "$kind API $code")
        }
        throw TiebaReadFailure.Service(IOException("Tieba API $code"))
    }

    private suspend fun <T> runRead(block: suspend () -> T): T = try {
        block()
    } catch (failure: Throwable) {
        throw failure.toTiebaReadFailure(context)
    }

    private suspend fun execute(request: Request): String = kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWith(Result.failure(e))
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.use {
                    if (!it.isSuccessful) continuation.resumeWith(Result.failure(IOException("HTTP ${it.code}")))
                    else continuation.resumeWith(Result.success(it.body?.string().orEmpty()))
                }
            }
        })
    }

    companion object {
        fun create(
            context: Context,
            settings: TiebaSettingsRepository = TiebaSettingsRepository(context.applicationContext),
        ): TiebaNetworkRepository {
            val appContext = context.applicationContext
            val gson = Gson()
            val cache = Cache(java.io.File(appContext.cacheDir, "tieba_http"), 64L * 1024L * 1024L)
            val supportClient = OkHttpClient.Builder()
                .cache(cache)
                .addInterceptor { chain ->
                    val original = chain.request()
                    val builder = original.newBuilder()
                    if (original.header("User-Agent") == null) builder.header("User-Agent", DEFAULT_WEB_USER_AGENT)
                    if (original.header("Accept-Language") == null) builder.header("Accept-Language", "zh-CN,zh;q=0.9")
                    chain.proceed(
                        builder.build(),
                    )
                }.build()
            val identity = TiebaClientIdentity(appContext)
            val readClient = supportClient.newBuilder()
                .addInterceptor(tiebaReadHeaderInterceptor(appContext, identity))
                .build()
            val picPageClient = supportClient.newBuilder()
                .addInterceptor { chain ->
                    chain.proceed(
                        chain.request().newBuilder()
                            .header("User-Agent", "bdtb for Android 7.2.0.0")
                            .header("Cookie", "ka=open")
                            .header("Pragma", "no-cache")
                            .header("cuid", identity.cuid)
                            .header("cuid_galaxy2", identity.cuid)
                            .header("client_logid", identity.firstInstallTime.toString())
                            .build(),
                    )
                }.build()
            return TiebaNetworkRepository(
                context = appContext,
                client = supportClient,
                supportApi = createSupportRetrofit(supportClient, gson).create(TiebaSupportApi::class.java),
                readApi = createTiebaReadRetrofit(readClient).create(TiebaReadApi::class.java),
                readRequests = TiebaReadRequestFactory(appContext, identity),
                picPageApi = createPicPageRetrofit(picPageClient, gson).create(TiebaPicPageApi::class.java),
                picPageRequests = TiebaPicPageRequestFactory(appContext, identity),
                gson = gson,
                settings = settings,
            )
        }

        internal fun createSupportRetrofit(client: OkHttpClient, gson: Gson = Gson()): Retrofit = Retrofit.Builder()
            .baseUrl("https://tieba.baidu.com/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .validateEagerly(true)
            .build()

        internal fun createPicPageRetrofit(client: OkHttpClient, gson: Gson = Gson()): Retrofit = Retrofit.Builder()
            .baseUrl("https://c.tieba.baidu.com/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .validateEagerly(true)
            .build()
    }
}

private fun String.cookieValue(name: String): String? = split(';').firstNotNullOfOrNull { part ->
    val separator = part.indexOf('=')
    if (separator <= 0 || !part.substring(0, separator).trim().equals(name, ignoreCase = true)) {
        null
    } else {
        part.substring(separator + 1).trim().takeIf(String::isNotBlank)
    }
}

private const val OFFICIAL_WRITE_VERSION = "12.41.7.1"
private const val TIEBA_UPLOAD_CHUNK_SIZE = 512_000
private const val TIEBA_UPLOAD_BOUNDARY = "--------7da3d81520810*"

internal fun replyCountExcludingFirstFloor(totalPostCount: Int): Int =
    (totalPostCount - 1).coerceAtLeast(0)

fun parseLoginCookies(raw: String): LoginCookies? {
    val parsed = raw.split(';').mapNotNull { part ->
        val index = part.indexOf('=')
        if (index <= 0) null else part.substring(0, index).trim().uppercase(Locale.ROOT) to part.substring(index + 1).trim()
    }.toMap()
    val bduss = parsed["BDUSS"]?.takeIf(String::isNotBlank) ?: return null
    val sToken = parsed["STOKEN"]?.takeIf(String::isNotBlank) ?: return null
    return LoginCookies(bduss, sToken, parsed["BAIDUID"], raw)
}

private fun AccountEntity.toReadCredentials() = TiebaReadCredentials(uid, bduss, sToken, zid)

private fun List<PbContent>.mapTiebaContent(): List<TiebaContent> = mapNotNull { item ->
    when (item.type) {
        0, 9, 27, 35, 40 -> item.text.takeIf(String::isNotEmpty)?.let(TiebaContent::Text)
        1 -> TiebaContent.Link(
            label = item.text.ifBlank { item.link },
            url = normalizeTiebaUrl(item.link),
        )
        2 -> TiebaContent.Emoticon(
            id = item.text.ifBlank { "image_emoticon1" }.let {
                if (it == "image_emoticon") "image_emoticon1" else it
            },
            name = item.c.ifBlank { "表情" },
        )
        3, 20 -> item.toTiebaImage()
        4 -> item.text.takeIf(String::isNotEmpty)?.let(TiebaContent::Text)
        5 -> {
            val videoUrl = sequenceOf(item.link, item.src)
                .firstOrNull { it.startsWith("http") && (it.contains(".mp4") || it.contains(".m3u8")) }
            if (videoUrl == null) {
                TiebaContent.Text("[视频]${item.text}")
            } else {
                val (width, height) = item.dimensions()
                TiebaContent.Video(
                    url = normalizeTiebaUrl(videoUrl),
                    thumbnailUrl = item.src.takeUnless { it == videoUrl }.orEmpty().let(::normalizeTiebaUrl),
                    width = width,
                    height = height,
                )
            }
        }
        10 -> TiebaContent.Text("[语音]")
        else -> item.text.takeIf(String::isNotEmpty)?.let(TiebaContent::Text)
    }
}

private fun PbContent.toTiebaImage(): TiebaContent.Image? {
    val preview = if (type == 20) {
        src
    } else {
        sequenceOf(bigCdnSrc, cdnSrcActive, cdnSrc, bigSrc, src, originSrc)
            .firstOrNull(String::isNotBlank).orEmpty()
    }
    if (preview.isBlank()) return null
    val original = if (type == 20) {
        src
    } else {
        sequenceOf(originSrc, bigCdnSrc, bigSrc, cdnSrcActive, cdnSrc, src)
            .firstOrNull(String::isNotBlank).orEmpty()
    }
    val (width, height) = dimensions()
    return TiebaContent.Image(
        previewUrl = normalizeTiebaUrl(preview),
        // Do not remove tbpicau or rewrite to an unsigned /forum/pic/item URL. An unsigned
        // item URL is exactly what makes Baidu return its 238 x 238 Tieba-logo placeholder.
        originalUrl = normalizeTiebaUrl(original.ifBlank { preview }),
        picId = imageId(original.ifBlank { preview }),
        width = width,
        height = height,
    )
}

private fun PbContent.dimensions(): Pair<Int?, Int?> {
    val parsed = bsize.split(',').mapNotNull(String::toIntOrNull)
    return if (parsed.size >= 2 && parsed[0] > 0 && parsed[1] > 0) {
        parsed[0] to parsed[1]
    } else {
        width.takeIf { it > 0 } to height.takeIf { it > 0 }
    }
}

private fun List<TiebaContent>.plainText(): String = buildString {
    this@plainText.forEach { item ->
        when (item) {
            is TiebaContent.Text -> append(item.value)
            is TiebaContent.Link -> append(item.label)
            is TiebaContent.Emoticon -> append("#(${item.name})")
            is TiebaContent.Image -> if (isNotEmpty()) append('\n')
            is TiebaContent.Video -> append("[视频]")
        }
    }
}.trim()

private fun PbContent.originalImage(): String? = sequenceOf(bigCdnSrc, cdnSrcActive, cdnSrc, bigSrc, src, originSrc)
    .firstOrNull(String::isNotBlank)

private fun canonicalForumName(value: String): String = value.trim().removeSuffix("吧")

private fun tiebaImageIdentity(raw: String): String {
    val normalized = normalizeTiebaUrl(raw)
    return runCatching { normalized.toHttpUrl().pathSegments.lastOrNull() }
        .getOrNull()
        ?.takeIf(String::isNotBlank)
        ?: normalized.substringBefore('?')
}

private fun imageId(raw: String): String = tiebaImageIdentity(raw).substringBeforeLast('.')

private fun plainText(value: String): String = Jsoup.parse(value.replace("<br/>", "\n")).text()
    .replace(Regex("(?:image_emoticon|shoubai_emoji)\\d*"), "[表情]")

private fun portraitUrl(portrait: String): String = when {
    portrait.isBlank() -> ""
    portrait.startsWith("http") || portrait.startsWith("//") -> originalImageUrl(portrait)
    else -> "https://himg.bdimg.com/sys/portrait/item/${portrait.substringBefore('?')}.jpg"
}

private fun replyPortraitToken(portrait: String): String = portrait
    .substringBefore('?')
    .substringAfterLast('/')
    .removeSuffix(".jpg")

private fun normalizeTiebaUrl(raw: String): String = when {
    raw.startsWith("//") -> "https:$raw"
    raw.startsWith("http://") -> "https://${raw.removePrefix("http://")}"
    else -> raw
}

private fun toOriginalTiebaImage(raw: String): String = normalizeTiebaUrl(raw)

private fun formatEpoch(seconds: Long): String = if (seconds <= 0) "" else DateFormat.getDateTimeInstance(
    DateFormat.SHORT,
    DateFormat.SHORT,
).format(Date(seconds * 1000))

private const val DEFAULT_WEB_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Mobile Safari/537.36"
