package edu.ccit.webvpn.feature.tieba.network

import android.content.Context
import com.google.gson.Gson
import com.huanchengfly.tieba.post.api.models.protos.Abstract as ThreadAbstract
import com.huanchengfly.tieba.post.api.models.protos.Anti
import com.huanchengfly.tieba.post.api.models.protos.Error
import com.huanchengfly.tieba.post.api.models.protos.Media
import com.huanchengfly.tieba.post.api.models.protos.Page
import com.huanchengfly.tieba.post.api.models.protos.PbContent
import com.huanchengfly.tieba.post.api.models.protos.Post
import com.huanchengfly.tieba.post.api.models.protos.SimpleForum
import com.huanchengfly.tieba.post.api.models.protos.SubPost
import com.huanchengfly.tieba.post.api.models.protos.SubPostList
import com.huanchengfly.tieba.post.api.models.protos.ThreadInfo
import com.huanchengfly.tieba.post.api.models.protos.User
import com.huanchengfly.tieba.post.api.models.protos.frsPage.ForumInfo
import com.huanchengfly.tieba.post.api.models.protos.frsPage.FrsPageResponse
import com.huanchengfly.tieba.post.api.models.protos.frsPage.FrsPageResponseData
import com.huanchengfly.tieba.post.api.models.protos.pbFloor.PbFloorResponse
import com.huanchengfly.tieba.post.api.models.protos.pbFloor.PbFloorResponseData
import com.huanchengfly.tieba.post.api.models.protos.pbPage.PbPageResponse
import com.huanchengfly.tieba.post.api.models.protos.pbPage.PbPageResponseData
import com.huanchengfly.tieba.post.api.models.protos.profile.ProfileResponse
import com.huanchengfly.tieba.post.api.models.protos.userPost.UserPostResponse
import edu.ccit.webvpn.feature.tieba.FloorSort
import edu.ccit.webvpn.feature.tieba.ForumSort
import edu.ccit.webvpn.feature.tieba.TARGET_FORUM_ID
import edu.ccit.webvpn.feature.tieba.TARGET_FORUM_NAME
import edu.ccit.webvpn.feature.tieba.TiebaContent
import edu.ccit.webvpn.feature.tieba.data.AccountEntity
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class TiebaNetworkTest {
    private val context: Context = RuntimeEnvironment.getApplication()

    @Test
    fun loginCookieParserIsCaseInsensitiveAndKeepsEqualsInValues() {
        val parsed = parseLoginCookies("foo=1; bduss=a=b=c; StOkEn=token; BAIDUID=device")
        assertEquals("a=b=c", parsed?.bduss)
        assertEquals("token", parsed?.sToken)
        assertEquals("device", parsed?.baiduId)
        assertNull(parseLoginCookies("BDUSS=only"))
    }

    @Test
    fun repositoryMapsTargetForumAndThreadFromWire() = runBlocking {
        val readApi = FakeTiebaReadApi(successForum(), successThread())
        val repository = repository(readApi)

        val forum = repository.loadForum(1, ForumSort.BY_REPLY, goodOnly = false)
        val thread = repository.loadThread("9", 1, FloorSort.ASCENDING, onlyOriginalPoster = false)

        assertEquals(TARGET_FORUM_NAME, forum.forum.name)
        assertEquals("9", forum.threads.single().id)
        assertEquals("摘要", forum.threads.single().excerpt)
        assertEquals("10", thread.body?.postId)
        assertEquals(1, thread.body?.floor)
        assertEquals("12", thread.floors.single().postId)
        assertEquals("正文#(黑线)", thread.floors.single().content)
        assertEquals("https://img.example/original.jpg", thread.floors.single().imageUrls.single())
        val image = thread.floors.single().richContent.filterIsInstance<TiebaContent.Image>().single()
        assertEquals("https://img.example/preview.jpg?token=kept", image.previewUrl)
        assertEquals("回复者", thread.floors.single().replies.single().authorNickname)
        assertEquals("#(泪)", thread.floors.single().replies.single().content)
    }

    @Test
    fun floorRepliesKeepAuthorAndRichContent() = runBlocking {
        val repository = repository(FakeTiebaReadApi(successForum(), successThread(), successFloor()))

        val page = repository.loadFloorReplies("9", "10", 1)

        assertEquals("回复者", page.replies.single().authorNickname)
        assertEquals("#(泪)", page.replies.single().content)
        assertEquals("image_emoticon9", page.replies.single().richContent.filterIsInstance<TiebaContent.Emoticon>().single().id)
    }

    @Test
    fun repositoryRejectsAnyOtherForum() = runBlocking {
        val otherForum = successForum().copy(
            data_ = successForum().data_?.copy(forum = ForumInfo(id = 1, name = "其他")),
        )
        val failure = runCatching {
            repository(FakeTiebaReadApi(otherForum, successThread())).loadForum(1, ForumSort.BY_REPLY, false)
        }.exceptionOrNull()

        assertNotNull(failure)
        assertEquals("帖子不属于本吧", failure?.message)
    }

    @Test
    fun inForumSearchDropsCrossForumResults() = runBlocking {
        val support = FakeTiebaSupportApi(
            SearchEnvelope(
                errorCode = 0,
                data = SearchData(
                    posts = listOf(
                        SearchPost(tid = "1", title = "目标", forumName = TARGET_FORUM_NAME),
                        SearchPost(tid = "2", title = "其他", forumName = "其他吧"),
                    ),
                ),
            ),
        )
        val results = repository(FakeTiebaReadApi(successForum(), successThread()), support).search("测试", 1)

        assertEquals(listOf("1"), results.threads.map { it.id })
    }

    @Test
    fun searchAcceptsTiebaPicMediaTypeAndMapsItsImage() = runBlocking {
        val support = FakeTiebaSupportApi(
            SearchEnvelope(
                errorCode = 0,
                data = SearchData(
                    posts = listOf(
                        SearchPost(
                            tid = "1",
                            title = "图片帖",
                            forumName = TARGET_FORUM_NAME,
                            user = SearchUser(id = 7, name = "user"),
                            media = listOf(
                                SearchMedia(
                                    type = "pic",
                                    bigPic = "https://img.example/forum/pic/item/search.jpg",
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val result = repository(FakeTiebaReadApi(successForum(), successThread()), support).search("图片", 1)

        assertEquals(7L, result.threads.single().authorId)
        assertEquals("https://img.example/forum/pic/item/search.jpg", result.threads.single().imageUrls.single())
    }

    @Test
    fun forumImagesWithTheSameTiebaItemAreShownOnlyOnce() = runBlocking {
        val original = successForum()
        val thread = original.data_!!.thread_list.single().copy(
            media = listOf(Media(originPic = "https://img-a.example/forum/pic/item/one.jpg?token=1")),
            richAbstract = listOf(PbContent(type = 3, originSrc = "https://img-b.example/forum/pic/item/one.jpg?token=2")),
        )
        val response = original.copy(data_ = original.data_!!.copy(thread_list = listOf(thread)))

        val page = repository(FakeTiebaReadApi(response, successThread())).loadForum(1, ForumSort.BY_REPLY, false)

        assertEquals(1, page.threads.single().imageUrls.size)
    }

    @Test
    fun accountApiErrorRetriesTheSameProtobufRequestAnonymouslyOnce() = runBlocking {
        val userTokens = mutableListOf<String?>()
        val api = object : TiebaReadApi {
            override suspend fun forum(body: RequestBody, forumName: String, userToken: String?): FrsPageResponse {
                userTokens += userToken
                return if (userToken != null) FrsPageResponse(error = Error(error_code = 4)) else successForum()
            }

            override suspend fun thread(body: RequestBody, userToken: String?): PbPageResponse = successThread()
            override suspend fun floor(body: RequestBody, userToken: String?): PbFloorResponse = PbFloorResponse(error = Error(error_code = 0))
            override suspend fun profile(body: RequestBody, userToken: String?): ProfileResponse = ProfileResponse(error = Error(error_code = 0))
            override suspend fun userPosts(body: RequestBody, userToken: String?): UserPostResponse = UserPostResponse(error = Error(error_code = 0))
        }
        val account = AccountEntity(
            uid = 7,
            name = "user",
            nickname = "nickname",
            bduss = "bduss",
            tbs = "tbs",
            portrait = "",
            sToken = "stoken",
            cookie = "BDUSS=bduss; STOKEN=stoken",
        )

        val result = repository(api).loadForum(1, ForumSort.BY_REPLY, false, account)

        assertEquals(TARGET_FORUM_NAME, result.forum.name)
        assertEquals(listOf("7", null), userTokens)
    }

    @Test
    fun bothRetrofitServiceFamiliesCanBeCreated() {
        val client = OkHttpClient()
        assertNotNull(TiebaNetworkRepository.createSupportRetrofit(client).create(TiebaSupportApi::class.java))
        assertNotNull(createTiebaReadRetrofit(client).create(TiebaReadApi::class.java))
    }

    private fun repository(
        readApi: TiebaReadApi,
        supportApi: TiebaSupportApi = FakeTiebaSupportApi(),
    ): TiebaNetworkRepository {
        val identity = TiebaClientIdentity(context)
        return TiebaNetworkRepository(
            context = context,
            client = OkHttpClient(),
            supportApi = supportApi,
            readApi = readApi,
            readRequests = TiebaReadRequestFactory(context, identity),
            gson = Gson(),
        )
    }

    private fun successForum() = FrsPageResponse(
        error = Error(error_code = 0),
        data_ = FrsPageResponseData(
            forum = ForumInfo(id = TARGET_FORUM_ID, name = TARGET_FORUM_NAME, member_num = 10),
            page = Page(current_page = 1, total_page = 2, has_more = 1),
            thread_list = listOf(
                ThreadInfo(
                    threadId = 9,
                    title = "主题",
                    authorId = 4,
                    _abstract = listOf(ThreadAbstract(type = 0, text = "摘要")),
                    forumInfo = SimpleForum(id = TARGET_FORUM_ID, name = TARGET_FORUM_NAME),
                ),
            ),
            user_list = listOf(User(id = 4, name = "user", nameShow = "昵称")),
        ),
    )

    private fun successThread() = PbPageResponse(
        error = Error(error_code = 0),
        data_ = PbPageResponseData(
            forum = SimpleForum(id = TARGET_FORUM_ID, name = TARGET_FORUM_NAME),
            page = Page(current_page = 1, total_page = 1),
            anti = Anti(),
            thread = ThreadInfo(id = 9, title = "主题", author = User(id = 4, name = "user")),
            first_floor_post =
                Post(
                    id = 10,
                    floor = 1,
                    time = 1_700_000_000,
                    author_id = 4,
                    content = listOf(
                        PbContent(type = 0, text = "正文"),
                        PbContent(type = 2, text = "image_emoticon10", c = "黑线"),
                        PbContent(
                            type = 3,
                            bigCdnSrc = "https://img.example/preview.jpg?token=kept",
                            originSrc = "https://img.example/original.jpg",
                            bsize = "800,600",
                        ),
                    ),
                    sub_post_number = 1,
                    sub_post_list = SubPost(
                        sub_post_list = listOf(
                            SubPostList(
                                id = 11,
                                author_id = 5,
                                time = 1_700_000_100,
                                content = listOf(PbContent(type = 2, text = "image_emoticon9", c = "泪")),
                            ),
                        ),
                    ),
                ),
            post_list = listOf(
                Post(
                    id = 12,
                    floor = 2,
                    time = 1_700_000_000,
                    author_id = 4,
                    content = listOf(
                        PbContent(type = 0, text = "正文"),
                        PbContent(type = 2, text = "image_emoticon10", c = "黑线"),
                        PbContent(
                            type = 3,
                            bigCdnSrc = "https://img.example/preview.jpg?token=kept",
                            originSrc = "https://img.example/original.jpg",
                            bsize = "800,600",
                        ),
                    ),
                    sub_post_number = 1,
                    sub_post_list = SubPost(
                        sub_post_list = listOf(
                            SubPostList(
                                id = 11,
                                author_id = 5,
                                time = 1_700_000_100,
                                content = listOf(PbContent(type = 2, text = "image_emoticon9", c = "泪")),
                            ),
                        ),
                    ),
                ),
            ),
            user_list = listOf(
                User(id = 4, name = "user", nameShow = "昵称"),
                User(id = 5, name = "reply", nameShow = "回复者"),
            ),
        ),
    )

    private fun successFloor() = PbFloorResponse(
        error = Error(error_code = 0),
        data_ = PbFloorResponseData(
            forum = SimpleForum(id = TARGET_FORUM_ID, name = TARGET_FORUM_NAME),
            page = Page(current_page = 1, total_page = 1),
            subpost_list = listOf(
                SubPostList(
                    id = 11,
                    time = 1_700_000_100,
                    author = User(id = 5, name = "reply", nameShow = "回复者"),
                    content = listOf(PbContent(type = 2, text = "image_emoticon9", c = "泪")),
                ),
            ),
        ),
    )
}

private class FakeTiebaReadApi(
    private val forumResponse: FrsPageResponse,
    private val threadResponse: PbPageResponse,
    private val floorResponse: PbFloorResponse = PbFloorResponse(error = Error(error_code = 0)),
) : TiebaReadApi {
    override suspend fun forum(body: RequestBody, forumName: String, userToken: String?): FrsPageResponse = forumResponse
    override suspend fun thread(body: RequestBody, userToken: String?): PbPageResponse = threadResponse
    override suspend fun floor(body: RequestBody, userToken: String?): PbFloorResponse = floorResponse
    override suspend fun profile(body: RequestBody, userToken: String?): ProfileResponse = ProfileResponse(error = Error(error_code = 0))
    override suspend fun userPosts(body: RequestBody, userToken: String?): UserPostResponse = UserPostResponse(error = Error(error_code = 0))
}

private class FakeTiebaSupportApi(
    private val searchResponse: SearchEnvelope = SearchEnvelope(errorCode = 0, data = SearchData()),
) : TiebaSupportApi {
    override suspend fun search(
        keyword: String,
        page: Int,
        order: Int,
        filter: Int,
        pageSize: Int,
        forumName: String,
        contentType: Int,
        useCombined: Int?,
        clientVersion: String,
        referer: String,
    ): SearchEnvelope = searchResponse

    override suspend fun profile(needUser: Int, cookie: String): ProfileEnvelope = ProfileEnvelope()
}
