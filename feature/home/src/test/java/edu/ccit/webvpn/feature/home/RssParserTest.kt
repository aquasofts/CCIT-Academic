package edu.ccit.webvpn.feature.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlinx.coroutines.runBlocking

@RunWith(RobolectricTestRunner::class)
class RssParserTest {
    private val parser = HomeFeedParser()
    private val source = FeedSource(
        id = "test",
        fallbackTitle = "默认来源",
        url = "https://feed.example/rss.xml?token=secret",
        section = HomeSection.WECHAT,
        allowedArticleHosts = setOf("article.example"),
    )

    @Test
    fun parsesWechatHtmlSourceImageAndDate() {
        val feed = parse(
            """
                <?xml version="1.0" encoding="UTF-8"?>
                <rss version="2.0"><channel>
                  <title>公众号来源</title>
                  <image><url>https://images.example/avatar.png</url><title>公众号来源</title></image>
                  <item>
                    <title>完整文章</title><link>https://article.example/one</link><guid>one</guid>
                    <pubDate>Thu, 16 Jul 2026 13:35:12 +0000</pubDate>
                    <description><![CDATA[<section><img data-src="https://images.example/cover.gif"><p>正文 <b>内容</b></p></section>]]></description>
                  </item>
                </channel></rss>
            """.trimIndent(),
            source,
        )

        assertEquals("公众号来源", feed.title)
        assertEquals("https://images.example/avatar.png", feed.imageUrl)
        assertEquals(1, feed.articles.size)
        assertEquals("正文 内容", feed.articles.single().summary)
        assertEquals("https://images.example/cover.gif", feed.articles.single().coverUrl)
        assertEquals("2026-07-16T13:35:12Z", feed.articles.single().publishedAt.toString())
        assertEquals(ArticleContentKind.COMPLETE, articleContentKind(feed.articles.single()))
    }

    @Test
    fun contentEncodedWinsAndDuplicatesAreRemoved() {
        val feed = parse(
            """
                <rss version="2.0" xmlns:content="http://purl.org/rss/1.0/modules/content/"><channel>
                  <item><title>新闻</title><link>https://article.example/news</link><guid>news</guid>
                    <description>旧摘要</description><content:encoded>&lt;p&gt;完整新闻&lt;/p&gt;</content:encoded></item>
                  <item><title>重复新闻</title><link>https://article.example/news</link><guid>news-copy</guid></item>
                </channel></rss>
            """.trimIndent(),
            source.copy(section = HomeSection.NEWS),
        )

        assertEquals(1, feed.articles.size)
        assertEquals("完整新闻", feed.articles.single().summary)
        assertEquals("<p>完整新闻</p>", feed.articles.single().html)
    }

    @Test
    fun parsesCurrentCampusNewsContentWithoutOpeningNetworkPage() {
        val feed = parse(
            """
                <?xml version="1.0" encoding="UTF-8"?>
                <rss version="2.0" xmlns:content="http://purl.org/rss/1.0/modules/content/"><channel>
                  <title>长工程News</title><description>长春工程学院新闻</description>
                  <link>https://cit-news.pages.dev/</link><language>zh_CN</language>
                  <item><title>新闻一</title>
                    <link>https://cit-news.pages.dev/posts/%E6%96%B0%E9%97%BB%E4%B8%80/</link>
                    <guid isPermaLink="true">https://cit-news.pages.dev/posts/%E6%96%B0%E9%97%BB%E4%B8%80/</guid>
                    <pubDate>Sun, 10 Nov 2024 00:00:00 GMT</pubDate>
                    <content:encoded>&lt;p&gt;测试&lt;/p&gt;</content:encoded>
                  </item>
                </channel></rss>
            """.trimIndent(),
            source.copy(
                fallbackTitle = "长工程News",
                url = "https://cit-news.pages.dev/rss.xml",
                section = HomeSection.NEWS,
                allowedArticleHosts = setOf("cit-news.pages.dev"),
            ),
        )

        val article = feed.articles.single()
        assertEquals("长工程News", feed.title)
        assertEquals("新闻一", article.title)
        assertEquals("<p>测试</p>", article.html)
        assertEquals("测试", article.summary)
        assertEquals(ArticleContentKind.COMPLETE, articleContentKind(article))
    }

    @Test
    fun parsesAtomLinksRelativeMediaAndIsoDates() {
        val feed = parse(
            """
                <feed xmlns="http://www.w3.org/2005/Atom" xmlns:media="http://search.yahoo.com/mrss/">
                  <title>Atom 来源</title>
                  <entry>
                    <title>Atom 文章</title>
                    <link rel="self" href="https://feed.example/entry/1.xml" />
                    <link rel="alternate" href="/article/1" />
                    <id>atom-1</id><updated>2026-07-16T12:30:00+08:00</updated>
                    <content type="html">&lt;p&gt;Atom 正文&lt;/p&gt;</content>
                    <media:thumbnail url="/images/cover.jpg" />
                  </entry>
                </feed>
            """.trimIndent(),
            source,
        )

        val article = feed.articles.single()
        assertEquals("https://feed.example/article/1", article.link)
        assertEquals("https://feed.example/images/cover.jpg", article.coverUrl)
        assertEquals("2026-07-16T04:30:00Z", article.publishedAt.toString())
        assertEquals("Atom 正文", article.summary)
    }

    @Test
    fun customSourcesAreValidatedDeduplicatedAndStable() {
        val sources = HomeFeedSources.fromUrls(
            wechatUrls = listOf(" https://example.com/rss.xml ", "https://example.com/rss.xml", "http://unsafe/rss"),
            newsUrls = emptyList(),
        )

        assertEquals(1, sources.size)
        assertEquals("公众号订阅 1", sources.single().fallbackTitle)
        assertEquals("https://example.com/rss.xml", sources.single().url)
    }

    @Test
    fun pendingEmptyAndInteractiveArticlesHaveDeterministicStates() {
        val pending = article("<p>[提示] 文章内容正在获取中，请稍后刷新</p>")
        val interactive = article("<p>视频</p><iframe src=\"https://article.example/video\"></iframe>")
        val empty = article("")

        assertEquals(ArticleContentKind.PLACEHOLDER, articleContentKind(pending))
        assertEquals(ArticleContentKind.INTERACTIVE, articleContentKind(interactive))
        assertEquals(ArticleContentKind.EMPTY, articleContentKind(empty))
    }

    @Test
    fun invalidDateAndUnsafeItemAreHandled() {
        val feed = parse(
            """
                <rss><channel>
                  <item><title>有效</title><link>https://article.example/ok</link><pubDate>not-a-date</pubDate></item>
                  <item><title>危险</title><link>javascript:alert(1)</link></item>
                </channel></rss>
            """.trimIndent(),
            source,
        )
        assertEquals(1, feed.articles.size)
        assertNull(feed.articles.single().publishedAt)
    }

    @Test
    fun rejectsDoctypeAndOversizedXml() {
        assertThrows(FeedParsingException::class.java) {
            parse("<!DOCTYPE rss><rss><channel/></rss>", source)
        }
        assertThrows(FeedParsingException::class.java) {
            parse("x".repeat(6 * 1024 * 1024 + 1), source)
        }
        assertThrows(FeedParsingException::class.java) {
            parse("<html><body>not a feed</body></html>", source)
        }
        assertThrows(FeedParsingException::class.java) {
            parse("<opml version=\"2.0\"><body/></opml>", source)
        }
        assertThrows(FeedParsingException::class.java) {
            parse("<rss><channel><item>", source)
        }
    }

    @Test
    fun urlPolicyOnlyAllowsApprovedHttpsHosts() {
        assertTrue(isAllowedArticleUrl("https://article.example/a", setOf("article.example")))
        assertFalse(isAllowedArticleUrl("http://article.example/a", setOf("article.example")))
        assertFalse(isAllowedArticleUrl("javascript:alert(1)", setOf("article.example")))
        assertFalse(isAllowedArticleUrl("https://other.example/a", setOf("article.example")))
    }

    @Test
    fun parsesBothProvidedFeedFiles() {
        val wechat = parse(
            resource("/feeds/wechat-description.rss"),
            source.copy(
                fallbackTitle = "学生工作处",
                url = "https://wechatrss.waytomaster.com/feed.rss",
                allowedArticleHosts = setOf("mp.weixin.qq.com"),
            ),
        )
        val aggregated = parse(
            resource("/feeds/campus-content-encoded.xml"),
            source.copy(
                fallbackTitle = "Cloudflare RSS Hub",
                url = DefaultHomeFeedUrls.wechat.single(),
                section = HomeSection.WECHAT,
                allowedArticleHosts = setOf("mp.weixin.qq.com"),
            ),
        )

        assertEquals("长春工程学院学生工作处", wechat.title)
        assertEquals(5, wechat.articles.size)
        assertEquals(ArticleContentKind.PLACEHOLDER, articleContentKind(wechat.articles[0]))
        assertEquals(ArticleContentKind.INTERACTIVE, articleContentKind(wechat.articles[2]))
        assertEquals("2026-07-16T13:35:12Z", wechat.articles[0].publishedAt.toString())
        assertEquals("Cloudflare RSS Hub", aggregated.title)
        assertEquals(6, aggregated.articles.size)
        assertEquals("长春工程学院学生工作处", aggregated.articles.first().sourceName)
        assertTrue(aggregated.articles.first().html.isNotBlank())
        assertTrue(aggregated.articles.all { isSafeHttpsUrl(it.link) })
    }

    @Test
    fun cloudflareHubIsTheOnlyDefaultWechatSource() {
        assertEquals(
            listOf("https://cloudflare-rss-hub-pages.pages.dev/api/rss.xml"),
            DefaultHomeFeedUrls.wechat,
        )
        val configured = HomeFeedSources.fromUrls(DefaultHomeFeedUrls.wechat, emptyList()).single()
        assertEquals("公众号订阅", configured.fallbackTitle)
        assertEquals(setOf("mp.weixin.qq.com"), configured.allowedArticleHosts)
    }

    @Test
    fun parsesRdfDublinCoreAndMediaEnclosure() {
        val feed = parse(
            """
                <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"
                  xmlns="http://purl.org/rss/1.0/"
                  xmlns:dc="http://purl.org/dc/elements/1.1/"
                  xmlns:content="http://purl.org/rss/1.0/modules/content/"
                  xmlns:media="http://search.yahoo.com/mrss/">
                  <channel rdf:about="https://feed.example/rss"><title>RDF 来源</title><link>https://feed.example/</link></channel>
                  <item rdf:about="https://article.example/rdf">
                    <title>RDF 文章</title><link>https://article.example/rdf</link>
                    <dc:date>2026-07-16T12:30:00+08:00</dc:date>
                    <content:encoded>&lt;p&gt;RDF 正文&lt;/p&gt;</content:encoded>
                    <media:content url="https://images.example/rdf.jpg" type="image/jpeg" medium="image" />
                  </item>
                </rdf:RDF>
            """.trimIndent(),
            source,
        )

        val article = feed.articles.single()
        assertEquals("RDF 来源", feed.title)
        assertEquals("RDF 正文", article.summary)
        assertEquals("https://images.example/rdf.jpg", article.coverUrl)
        assertEquals("2026-07-16T04:30:00Z", article.publishedAt.toString())
    }

    @Test
    fun parsesRss091AndImageEnclosure() {
        val feed = parse(
            """
                <rss version="0.91"><channel><title>旧版 RSS</title><link>https://feed.example/</link>
                  <description>兼容性测试</description><item><title>旧版文章</title>
                  <link>https://article.example/legacy</link><description>旧版正文</description>
                  <enclosure url="https://images.example/legacy.png" type="image/png" length="12" />
                  </item></channel></rss>
            """.trimIndent(),
            source,
        )

        assertEquals("旧版 RSS", feed.title)
        assertEquals("旧版正文", feed.articles.single().summary)
        assertEquals("https://images.example/legacy.png", feed.articles.single().coverUrl)
    }

    @Test
    fun itemCreatorAndSourceOverrideInfrastructureChannelName() {
        val feed = parse(
            """
                <rss version="2.0" xmlns:dc="http://purl.org/dc/elements/1.1/"><channel>
                  <title>Cloudflare RSS Hub</title>
                  <item><title>公众号文章</title><link>https://article.example/source</link>
                    <dc:creator>长春工程学院学生工作处</dc:creator>
                    <source>接口服务名</source><description><![CDATA[<img data-src="https://images.example/body.jpg"><p>正文</p>]]></description>
                  </item>
                </channel></rss>
            """.trimIndent(),
            source,
        )

        val article = feed.articles.single()
        assertEquals("长春工程学院学生工作处", article.sourceName)
        assertTrue(article.html.contains("src=\"https://images.example/body.jpg\""))
        assertFalse(article.html.contains("data-src"))
    }

    private fun parse(xml: String, feedSource: FeedSource): ParsedFeed = runBlocking {
        parser.parse(xml, feedSource)
    }

    private fun resource(path: String): String = checkNotNull(javaClass.getResource(path)).readText()

    private fun article(html: String): HomeArticle = HomeArticle(
        id = "id",
        sourceId = source.id,
        sourceName = source.fallbackTitle,
        sourceAvatarUrl = "",
        title = "标题",
        link = "https://article.example/article",
        guid = "guid",
        publishedAt = null,
        html = html,
        summary = "",
        coverUrl = "",
        allowedArticleHosts = source.allowedArticleHosts,
    )
}
