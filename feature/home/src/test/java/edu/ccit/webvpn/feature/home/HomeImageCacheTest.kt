package edu.ccit.webvpn.feature.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeImageCacheTest {
    @Test
    fun `article image collector includes every reusable image and resolves relatives`() {
        val article = HomeArticle(
            id = "1",
            sourceId = "source",
            sourceName = "来源",
            sourceAvatarUrl = "https://cdn.example/avatar.png",
            title = "标题",
            link = "https://news.example/path/article.html",
            guid = "1",
            publishedAt = null,
            html = "<img src='../one.jpg'><img data-src='https://cdn.example/two.webp'>",
            summary = "",
            coverUrl = "https://cdn.example/cover.jpg",
            allowedArticleHosts = setOf("news.example"),
        )

        assertEquals(
            listOf(
                "https://cdn.example/avatar.png",
                "https://cdn.example/cover.jpg",
                "https://news.example/one.jpg",
                "https://cdn.example/two.webp",
            ),
            articleImageUrls(article).toList(),
        )
    }

    @Test
    fun `article wrapper reserves image geometry and animates reveal`() {
        val html = articleHtmlDocument("<img src='https://cdn.example/a.jpg' width='800' height='600'>")

        assertTrue(html.contains("--article-image-ratio:800/600"))
        assertTrue(html.contains("animation: image-shimmer"))
        assertTrue(html.contains("requestAnimationFrame"))
        assertTrue(html.contains("class=\"article-image is-loading\""))
    }
}
