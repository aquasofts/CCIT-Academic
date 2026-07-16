package edu.ccit.webvpn.feature.tieba.network

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
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
import edu.ccit.webvpn.feature.tieba.ForumSort
import edu.ccit.webvpn.feature.tieba.ForumSummary
import edu.ccit.webvpn.feature.tieba.ForumThread
import edu.ccit.webvpn.feature.tieba.SignOutcome
import edu.ccit.webvpn.feature.tieba.TARGET_FORUM_ID
import edu.ccit.webvpn.feature.tieba.TARGET_FORUM_NAME
import edu.ccit.webvpn.feature.tieba.TiebaContent
import edu.ccit.webvpn.feature.tieba.TiebaUserForum
import edu.ccit.webvpn.feature.tieba.TiebaUserPost
import edu.ccit.webvpn.feature.tieba.TiebaUserPostPage
import edu.ccit.webvpn.feature.tieba.TiebaUserProfile
import edu.ccit.webvpn.feature.tieba.ThreadFloor
import edu.ccit.webvpn.feature.tieba.ThreadPage
import edu.ccit.webvpn.feature.tieba.data.AccountEntity
import edu.ccit.webvpn.feature.tieba.originalImageUrl
import java.io.IOException
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import okhttp3.Cache
import okhttp3.FormBody
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

data class SignResponse(val outcome: SignOutcome, val message: String)

class TiebaNetworkRepository internal constructor(
    private val context: Context,
    private val client: OkHttpClient,
    private val supportApi: TiebaSupportApi,
    private val readApi: TiebaReadApi,
    private val readRequests: TiebaReadRequestFactory,
    private val gson: Gson,
) {
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
                        replyCount = post.replyCount,
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

    /** This is the only content-writing request exposed by the module. */
    suspend fun sign(account: AccountEntity): SignResponse {
        val profile = supportApi.profile(cookie = account.cookie).data
            ?: return SignResponse(SignOutcome.FAILED, "账号已失效，请重新登录")
        if (!profile.isLoggedIn) return SignResponse(SignOutcome.FAILED, "账号已失效，请重新登录")
        val tbs = profile.tbs.ifBlank { profile.itbTbs }.ifBlank { account.tbs }
        val request = Request.Builder()
            .url("https://tieba.baidu.com/sign/add")
            .header("Cookie", account.cookie)
            .header("Referer", "https://tieba.baidu.com/f?kw=$TARGET_FORUM_NAME")
            .post(FormBody.Builder().add("ie", "utf-8").add("kw", TARGET_FORUM_NAME).add("tbs", tbs).build())
            .build()
        val json = gson.fromJson(execute(request), JsonObject::class.java)
        val code = json.get("no")?.asInt ?: -1
        val message = json.get("error")?.asString
            ?: json.getAsJsonObject("data")?.get("errmsg")?.asString
            ?: if (code == 0) "签到成功" else "签到失败（$code）"
        return when {
            code == 0 -> SignResponse(SignOutcome.SUCCESS, "长春工程学院吧签到成功")
            code == 1101 || message.contains("已签") -> SignResponse(SignOutcome.ALREADY_SIGNED, "今日已经签到")
            message.contains("未关注") || code == 1004 -> SignResponse(SignOutcome.FAILED, "尚未关注长春工程学院吧")
            else -> SignResponse(SignOutcome.FAILED, message)
        }
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
                slogan = forum.slogan.ifBlank {
                    listOf(forum.first_class, forum.second_class).filter(String::isNotBlank).joinToString(" · ")
                },
                avatarUrl = forum.avatar.takeIf(String::isNotBlank)?.let(::toOriginalTiebaImage).orEmpty(),
                memberCount = forum.member_num.toString(),
                threadCount = forum.thread_num.toString(),
                postCount = forum.post_num.toString(),
                isFollowed = forum.is_like == 1,
            ),
            threads = threads,
            page = page.current_page.takeIf { it > 0 } ?: requestedPage,
            hasMore = page.has_more == 1 || page.total_page > requestedPage,
        )
    }

    private fun mapForumThread(source: ThreadInfo, users: Map<Long, User>): ForumThread? {
        val id = source.threadId.takeIf { it > 0 } ?: source.id.takeIf { it > 0 } ?: return null
        val author = users[source.authorId] ?: source.author
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
            replyCount = source.replyNum.toString(),
            viewCount = source.viewNum.toString(),
            lastReplyTime = source.lastTime,
            isTop = source.isTop == 1,
            isGood = source.isGood == 1,
            imageUrls = images,
            videoUrl = source.videoInfo?.videoUrl?.takeIf(String::isNotBlank),
            authorId = author?.id ?: source.authorId,
            forumId = source.forumInfo?.id ?: TARGET_FORUM_ID,
            forumName = source.forumInfo?.name.orEmpty().ifBlank { TARGET_FORUM_NAME },
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
        val images = buildList {
            post.media.forEach { media ->
                sequenceOf(media.originPic, media.bigPic, media.srcPic)
                    .firstOrNull(String::isNotBlank)?.let(::add)
            }
            post.rich_abstract.filter { it.type == 3 || it.type == 20 }
                .forEach { it.originalImage()?.let(::add) }
        }.map(::normalizeTiebaUrl).distinctBy(::tiebaImageIdentity)
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
        fun create(context: Context): TiebaNetworkRepository {
            val appContext = context.applicationContext
            val gson = Gson()
            val cache = Cache(java.io.File(appContext.cacheDir, "tieba_http"), 64L * 1024L * 1024L)
            val supportClient = OkHttpClient.Builder()
                .cache(cache)
                .addInterceptor { chain ->
                    chain.proceed(
                        chain.request().newBuilder()
                            .header("User-Agent", DEFAULT_WEB_USER_AGENT)
                            .header("Accept-Language", "zh-CN,zh;q=0.9")
                            .build(),
                    )
                }.build()
            val identity = TiebaClientIdentity(appContext)
            val readClient = supportClient.newBuilder()
                .addInterceptor(tiebaReadHeaderInterceptor(appContext, identity))
                .build()
            return TiebaNetworkRepository(
                context = appContext,
                client = supportClient,
                supportApi = createSupportRetrofit(supportClient, gson).create(TiebaSupportApi::class.java),
                readApi = createTiebaReadRetrofit(readClient).create(TiebaReadApi::class.java),
                readRequests = TiebaReadRequestFactory(appContext, identity),
                gson = gson,
            )
        }

        internal fun createSupportRetrofit(client: OkHttpClient, gson: Gson = Gson()): Retrofit = Retrofit.Builder()
            .baseUrl("https://tieba.baidu.com/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .validateEagerly(true)
            .build()
    }
}

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
        originalUrl = toOriginalTiebaImage(original.ifBlank { preview }),
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

private fun plainText(value: String): String = Jsoup.parse(value.replace("<br/>", "\n")).text()
    .replace(Regex("(?:image_emoticon|shoubai_emoji)\\d*"), "[表情]")

private fun portraitUrl(portrait: String): String = when {
    portrait.isBlank() -> ""
    portrait.startsWith("http") || portrait.startsWith("//") -> originalImageUrl(portrait)
    else -> "https://himg.bdimg.com/sys/portrait/item/${portrait.substringBefore('?')}.jpg"
}

private fun normalizeTiebaUrl(raw: String): String = when {
    raw.startsWith("//") -> "https:$raw"
    raw.startsWith("http://") -> "https://${raw.removePrefix("http://")}"
    else -> raw
}

private fun toOriginalTiebaImage(raw: String): String {
    val url = originalImageUrl(raw)
    return if (url.contains("/forum/") && url.contains("/sign=")) {
        val parsed = url.toHttpUrl()
        "https://${parsed.host}/forum/pic/item/${url.substringAfterLast('/')}"
    } else url
}

private fun formatEpoch(seconds: Long): String = if (seconds <= 0) "" else DateFormat.getDateTimeInstance(
    DateFormat.SHORT,
    DateFormat.SHORT,
).format(Date(seconds * 1000))

private const val DEFAULT_WEB_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Mobile Safari/537.36"
