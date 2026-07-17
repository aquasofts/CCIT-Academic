package edu.ccit.webvpn.feature.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfficialNewsTest {
    private val parser = OfficialNewsParser()
    private val source = OfficialNewsSource(
        id = "school",
        title = "学校新闻",
        ownerId = "2144790275",
        contentId = "1074518",
        contentBaseUrl = "https://www.ccit.edu.cn/",
    )

    @Test
    fun parsesListTextImageAndShanghaiPublicationTime() {
        val payload = """[{"content":"<p>图文正文</p><img src=\"http://www.ccit.edu.cn/__local/a.jpg\">","date":"2026-07-13 13:16:07","id":"10907","title":"学校新闻标题"}]"""

        val article = parser.parseList(soap("getListByContentIdReturn", payload), source).single()

        assertEquals("学校新闻标题", article.title)
        assertEquals("学校新闻", article.sourceName)
        assertEquals("图文正文", article.summary)
        assertEquals("https://www.ccit.edu.cn/__local/a.jpg", article.coverUrl)
        assertEquals("2026-07-13T05:16:07Z", article.publishedAt.toString())
        assertEquals("10907", article.officialReference?.newsId)
        assertTrue(article.html.contains("loading=\"lazy\""))
        assertFalse(article.html.contains("http://"))
    }

    @Test
    fun parsesOpenedDetailAndMarksItLoaded() {
        val listedPayload = """[{"content":"<p>列表正文</p>","date":"2026-07-13 13:16:07","id":"10907","title":"旧标题"}]"""
        val listed = parser.parseList(soap("getListByContentIdReturn", listedPayload), source).single()
        val detailPayload = """{"wbcontent":"<p>详情正文</p><img src=\"/detail.png\">","wbdate":"2026-07-14 08:00:00","wbtitle":"详情标题"}"""

        val detailed = parser.parseDetail(soap("getWbnewsByIdReturn", detailPayload), listed)

        assertEquals("详情标题", detailed.title)
        assertEquals("详情正文", detailed.summary)
        assertEquals("https://www.ccit.edu.cn/detail.png", detailed.coverUrl)
        assertEquals("2026-07-14T00:00:00Z", detailed.publishedAt.toString())
        assertTrue(detailed.officialReference?.detailLoaded == true)
        assertTrue(officialDetailEnvelope(requireNotNull(detailed.officialReference)).contains("<wbnewsid xsi:type=\"xsd:int\">10907</wbnewsid>"))
    }

    @Test
    fun defaultsMatchTheThreePortalNewsGroups() {
        assertEquals(listOf("学校新闻", "通知公告", "学术新闻"), OfficialNewsSources.all.map { it.title })
        assertEquals(
            listOf("1074518", "1074519", "1073128"),
            OfficialNewsSources.all.map { it.contentId },
        )
        assertTrue(officialListEnvelope(OfficialNewsSources.all.first()).contains("<count xsi:type=\"xsd:int\">100</count>"))
    }

    @Test
    fun coverPrefetchStartsBelowViewportAndStopsAtTwentyImages() {
        val articles = (0 until 30).map { index ->
            HomeArticle(
                id = index.toString(), sourceId = "official", sourceName = "学校新闻",
                sourceAvatarUrl = "", title = "标题 $index", link = "https://www.ccit.edu.cn/",
                guid = index.toString(), publishedAt = null, html = "<p>正文</p>", summary = "",
                coverUrl = "https://www.ccit.edu.cn/images/$index.jpg", allowedArticleHosts = emptySet(),
            )
        }

        val urls = officialCoverUrlsToPrefetch(articles, lastVisibleIndex = 3)

        assertEquals(20, urls.size)
        assertEquals("https://www.ccit.edu.cn/images/4.jpg", urls.first())
        assertEquals("https://www.ccit.edu.cn/images/23.jpg", urls.last())
    }

    private fun soap(returnElement: String, json: String): String {
        val escaped = json
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/">
              <soapenv:Body><ns1:response xmlns:ns1="http://webservice.vsb.webber">
                <$returnElement>$escaped</$returnElement>
              </ns1:response></soapenv:Body>
            </soapenv:Envelope>
        """.trimIndent()
    }
}
