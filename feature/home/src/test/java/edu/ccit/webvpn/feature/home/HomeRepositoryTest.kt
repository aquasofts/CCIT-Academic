package edu.ccit.webvpn.feature.home

import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HomeRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var cacheDir: File

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        cacheDir = createTempDirectory("home-feed-test").toFile()
    }

    @After
    fun tearDown() {
        server.shutdown()
        cacheDir.deleteRecursively()
    }

    @Test
    fun refreshCachesAndFallsBackWithoutLeakingUrl() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody(feed("缓存文章")))
        server.enqueue(MockResponse().setResponseCode(500))
        val repository = repository(listOf(source("one")))

        val first = repository.refresh(HomeSection.WECHAT)
        val second = repository.refresh(HomeSection.WECHAT)

        assertEquals("缓存文章", first.articles.single().title)
        assertEquals("缓存文章", second.articles.single().title)
        assertTrue(second.errorMessage.orEmpty().contains("服务器返回错误（HTTP 500）"))
        assertFalse(second.errorMessage.orEmpty().contains("token"))
        assertEquals(FeedFailureKind.HTTP, second.sourceStatuses.single().failure)
        assertTrue(second.sourceStatuses.single().usedCache)
        assertEquals("缓存文章", repository.loadCached(HomeSection.WECHAT).single().title)
    }

    @Test
    fun oneFailedSourceDoesNotHideSuccessfulSource() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody(feed("成功来源")))
        server.enqueue(MockResponse().setResponseCode(500))
        val repository = repository(listOf(source("one"), source("two")))

        val result = repository.refresh(HomeSection.WECHAT)

        assertEquals(listOf("成功来源"), result.articles.map(HomeArticle::title))
        assertTrue(result.errorMessage.orEmpty().contains("服务器返回错误（HTTP 500）"))
        assertEquals(1, result.sourceStatuses.count { it.fresh })
        assertEquals(1, result.sourceStatuses.count { it.failure == FeedFailureKind.HTTP })
        assertEquals(2, server.requestCount)
    }

    @Test
    fun multipleSourcesAreMergedByPublishedTimeNewestFirst() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody(feed("较早", "Wed, 15 Jul 2026 08:00:00 +0000")))
        server.enqueue(MockResponse().setResponseCode(200).setBody(feed("较新", "Thu, 16 Jul 2026 08:00:00 +0000")))
        val repository = repository(listOf(source("one"), source("two")))

        val result = repository.refresh(HomeSection.WECHAT)

        assertEquals(listOf("较新", "较早"), result.articles.map(HomeArticle::title))
        assertEquals(null, result.errorMessage)
    }

    @Test
    fun officialSourcesArePostedMergedAndOpenedThroughDetailAction() = runBlocking {
        server.enqueue(MockResponse().setBody(officialList("较早", "2026-07-14 08:00:00", "101")))
        server.enqueue(MockResponse().setBody(officialList("最新", "2026-07-16 08:00:00", "102")))
        server.enqueue(MockResponse().setBody(officialList("中间", "2026-07-15 08:00:00", "103")))
        val listUrl = server.url("/ccit/news/getList").toString()
        val detailUrl = server.url("/ccit/news/getOwner").toString()
        val officialSources = listOf("学校新闻", "通知公告", "学术新闻").mapIndexed { index, title ->
            OfficialNewsSource(
                id = "official-$index",
                title = title,
                ownerId = "${200 + index}",
                contentId = "${300 + index}",
                contentBaseUrl = "https://www.ccit.edu.cn/",
                listUrl = listUrl,
                detailUrl = detailUrl,
            )
        }
        val repository = HomeRepository(
            cacheDirectory = cacheDir,
            client = OkHttpClient.Builder().build(),
            sources = emptyList(),
            allowInsecureTestTransport = true,
            officialSources = officialSources,
        )

        val result = repository.refresh(HomeSection.OFFICIAL)

        assertEquals(listOf("最新", "中间", "较早"), result.articles.map(HomeArticle::title))
        repeat(3) {
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/ccit/news/getList", request.path)
            assertTrue(request.body.readUtf8().contains("getListByContentId"))
        }

        server.enqueue(MockResponse().setBody(officialDetail("详情正文", "打开后的标题")))
        val detailed = repository.loadOfficialDetail(result.articles.first())
        val detailRequest = server.takeRequest()
        assertEquals("/ccit/news/getOwner", detailRequest.path)
        assertTrue(detailRequest.body.readUtf8().contains("getWbnewsById"))
        assertEquals("打开后的标题", detailed.title)
        assertEquals("详情正文", detailed.summary)
        assertTrue(detailed.officialReference?.detailLoaded == true)
    }

    @Test
    fun singleSourceArticlesAreSortedNewestFirst() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """
                    <rss><channel><title>单一来源</title>
                      <item><title>较早</title><link>https://article.example/old</link>
                        <pubDate>Wed, 15 Jul 2026 08:00:00 +0000</pubDate></item>
                      <item><title>较新</title><link>https://article.example/new</link>
                        <pubDate>Thu, 16 Jul 2026 08:00:00 +0000</pubDate></item>
                    </channel></rss>
                """.trimIndent(),
            ),
        )
        val repository = repository(listOf(source("single")))

        val result = repository.refresh(HomeSection.WECHAT)

        assertEquals(listOf("较新", "较早"), result.articles.map(HomeArticle::title))
    }

    @Test
    fun timeoutReturnsGenericFailure() = runBlocking {
        server.enqueue(MockResponse().setHeadersDelay(300, TimeUnit.MILLISECONDS).setBody(feed("迟到")))
        val client = OkHttpClient.Builder().readTimeout(50, TimeUnit.MILLISECONDS).build()
        val repository = repository(listOf(source("slow")), client)

        val result = repository.refresh(HomeSection.WECHAT)

        assertTrue(result.articles.isEmpty())
        assertEquals("slow：网络连接失败", result.errorMessage)
        assertEquals(FeedFailureKind.NETWORK, result.sourceStatuses.single().failure)
    }

    @Test
    fun responseCharsetIsRespectedAndCacheIsNormalizedToUtf8() = runBlocking {
        val charset = charset("ISO-8859-1")
        val xml = feed("Café")
            .replace("<rss>", "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?><rss>")
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/rss+xml; charset=ISO-8859-1")
                .setBody(Buffer().write(xml.toByteArray(charset))),
        )
        val repository = repository(listOf(source("latin")))

        val result = repository.refresh(HomeSection.WECHAT)

        assertEquals("Café", result.articles.single().title)
        assertEquals(null, result.errorMessage)
        assertEquals("Café", repository.loadCached(HomeSection.WECHAT).single().title)
    }

    @Test
    fun redirectAndEmptyFeedAreSuccessful() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", server.url("/final")))
        server.enqueue(MockResponse().setBody("<rss version=\"2.0\"><channel><title>空来源</title></channel></rss>"))
        val repository = repository(listOf(source("redirect")))

        val result = repository.refresh(HomeSection.WECHAT)

        assertTrue(result.articles.isEmpty())
        assertEquals(null, result.errorMessage)
        assertTrue(result.sourceStatuses.single().fresh)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun nonFeedAndDoctypeReturnSpecificSafeFailures() = runBlocking {
        server.enqueue(MockResponse().setBody("<html><body>not a feed</body></html>"))
        server.enqueue(MockResponse().setBody("<!DOCTYPE rss><rss><channel/></rss>"))
        val repository = repository(listOf(source("invalid"), source("unsafe")))

        val result = repository.refresh(HomeSection.WECHAT)

        assertTrue(result.articles.isEmpty())
        assertEquals(
            setOf(FeedFailureKind.INVALID_FEED, FeedFailureKind.UNSAFE_DOCUMENT),
            result.sourceStatuses.mapNotNull(FeedSourceStatus::failure).toSet(),
        )
        assertFalse(result.errorMessage.orEmpty().contains("token"))
    }

    @Test
    fun oversizedResponseIsRejectedBeforeParsing() = runBlocking {
        server.enqueue(
            MockResponse()
                .setBody("<rss/>")
                .setHeader("Content-Length", 5 * 1024 * 1024 + 1),
        )
        val repository = repository(listOf(source("large")))

        val result = repository.refresh(HomeSection.WECHAT)

        assertTrue(result.articles.isEmpty())
        assertEquals(FeedFailureKind.TOO_LARGE, result.sourceStatuses.single().failure)
    }

    @Test
    fun htmlWrapperIncludesSecurityAndResponsiveRules() {
        val document = articleHtmlDocument(
            body = "<p>正文</p>",
            textColor = "#F0F0F0",
            backgroundColor = "#101010",
            linkColor = "#AFC6FF",
        )
        assertTrue(document.contains("Content-Security-Policy"))
        assertTrue(document.contains("img, video, iframe, table"))
        assertTrue(document.contains("<p>正文</p>"))
        assertTrue(document.contains("background: #101010"))
        assertTrue(document.contains("color: #F0F0F0"))
        assertTrue(document.contains("a { color: #AFC6FF; }"))
        assertTrue(document.contains("script-src 'nonce-ccit-images'"))
        assertTrue(document.contains("IntersectionObserver"))
    }

    @Test
    fun articleLoadStateAlwaysTerminatesOnSuccessFailureAndTimeout() {
        assertEquals(
            ArticleLoadState.Ready,
            reduceArticleLoadState(ArticleLoadState.Loading, ArticleLoadEvent.Finished),
        )
        val failed = reduceArticleLoadState(
            ArticleLoadState.Loading,
            ArticleLoadEvent.Failed("原文加载失败"),
        )
        assertEquals(ArticleLoadState.Failed("原文加载失败"), failed)
        assertEquals(failed, reduceArticleLoadState(failed, ArticleLoadEvent.Finished))
        assertEquals(
            ArticleLoadState.Failed("原文加载超时"),
            reduceArticleLoadState(ArticleLoadState.Loading, ArticleLoadEvent.Timeout),
        )
        assertEquals(
            ArticleLoadState.Ready,
            reduceArticleLoadState(ArticleLoadState.Ready, ArticleLoadEvent.Timeout),
        )
    }

    private fun repository(
        sources: List<FeedSource>,
        client: OkHttpClient = OkHttpClient.Builder().build(),
    ) = HomeRepository(
        cacheDirectory = cacheDir,
        client = client,
        sources = sources,
        allowInsecureTestTransport = true,
    )

    private fun source(id: String) = FeedSource(
        id = id,
        fallbackTitle = id,
        url = server.url("/$id?token=private").toString(),
        section = HomeSection.WECHAT,
        allowedArticleHosts = setOf("article.example"),
    )

    private fun feed(title: String, pubDate: String? = null) = """
        <rss><channel><title>测试来源</title><item><title>$title</title>
        <link>https://article.example/$title</link><guid>$title</guid>
        ${pubDate?.let { "<pubDate>$it</pubDate>" }.orEmpty()}
        <description>&lt;p&gt;摘要&lt;/p&gt;</description></item></channel></rss>
    """.trimIndent()

    private fun officialList(title: String, date: String, id: String): String = officialSoap(
        "getListByContentIdReturn",
        """[{"content":"<p>$title 正文</p>","date":"$date","id":"$id","title":"$title"}]""",
    )

    private fun officialDetail(content: String, title: String): String = officialSoap(
        "getWbnewsByIdReturn",
        """{"wbcontent":"<p>$content</p>","wbdate":"2026-07-16 09:00:00","wbtitle":"$title"}""",
    )

    private fun officialSoap(returnElement: String, json: String): String {
        val escaped = json
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
        return """
            <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/">
              <soapenv:Body><$returnElement>$escaped</$returnElement></soapenv:Body>
            </soapenv:Envelope>
        """.trimIndent()
    }
}
