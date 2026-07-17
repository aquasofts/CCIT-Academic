package edu.ccit.webvpn.feature.home

import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import java.util.UUID
import org.jsoup.Jsoup

internal enum class HomeSection {
    WECHAT,
    NEWS,
    OFFICIAL,
}

internal data class FeedSource(
    val id: String,
    val fallbackTitle: String,
    val url: String,
    val section: HomeSection,
    val allowedArticleHosts: Set<String>,
)

object DefaultHomeFeedUrls {
    val wechat = listOf("https://cloudflare-rss-hub-pages.pages.dev/api/rss.xml")
    val news = listOf("https://cit-news.pages.dev/rss.xml")
}

internal object HomeFeedSources {
    fun fromUrls(wechatUrls: List<String>, newsUrls: List<String>): List<FeedSource> =
        sources(HomeSection.WECHAT, wechatUrls) + sources(HomeSection.NEWS, newsUrls)

    private fun sources(section: HomeSection, urls: List<String>): List<FeedSource> = urls
        .map(String::trim)
        .filter(::isSafeHttpsUrl)
        .distinct()
        .mapIndexed { index, url -> source(section, url, index) }

    private fun source(section: HomeSection, url: String, index: Int): FeedSource {
        val known = knownSources[url]
        val fallback = known?.first ?: when (section) {
            HomeSection.WECHAT -> "公众号订阅 ${index + 1}"
            HomeSection.NEWS -> "校内新闻订阅 ${index + 1}"
            HomeSection.OFFICIAL -> error("官方新闻不使用 RSS 来源")
        }
        val id = known?.second ?: UUID.nameUUIDFromBytes(
            "${section.name}:$url".toByteArray(StandardCharsets.UTF_8),
        ).toString()
        val articleHosts = known?.third.orEmpty()
        return FeedSource(id, fallback, url, section, articleHosts)
    }

    private val knownSources = mapOf(
        DefaultHomeFeedUrls.wechat[0] to Triple(
            "公众号订阅",
            "cloudflare_rss_hub",
            setOf("mp.weixin.qq.com"),
        ),
        DefaultHomeFeedUrls.news[0] to Triple(
            "长工程News",
            "campus_news",
            setOf("cit-news.pages.dev"),
        ),
    )

    val all = fromUrls(DefaultHomeFeedUrls.wechat, DefaultHomeFeedUrls.news)

}

internal data class HomeArticle(
    val id: String,
    val sourceId: String,
    val sourceName: String,
    val sourceAvatarUrl: String,
    val title: String,
    val link: String,
    val guid: String,
    val publishedAt: Instant?,
    val html: String,
    val summary: String,
    val coverUrl: String,
    val allowedArticleHosts: Set<String>,
    val officialReference: OfficialNewsReference? = null,
    val section: HomeSection = HomeSection.NEWS,
)

internal data class ParsedFeed(
    val title: String,
    val imageUrl: String,
    val articles: List<HomeArticle>,
)

internal enum class ArticleContentKind {
    COMPLETE,
    PLACEHOLDER,
    INTERACTIVE,
    EMPTY,
}

internal fun articleContentKind(article: HomeArticle): ArticleContentKind {
    if (article.html.isBlank()) return ArticleContentKind.EMPTY
    val document = Jsoup.parseBodyFragment(article.html)
    val text = document.text()
    return when {
        text.contains("文章内容正在获取中") -> ArticleContentKind.PLACEHOLDER
        document.selectFirst("iframe, video, audio, mpvoice, mp-common-videosnap") != null -> {
            ArticleContentKind.INTERACTIVE
        }
        else -> ArticleContentKind.COMPLETE
    }
}

internal fun isSafeHttpsUrl(raw: String): Boolean = runCatching {
    val uri = java.net.URI(raw)
    uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank()
}.getOrDefault(false)

internal fun resolveHttpsUrl(raw: String, baseUrl: String): String {
    val value = raw.trim()
    if (value.isBlank()) return ""
    return runCatching { java.net.URI(baseUrl).resolve(value).toString() }.getOrDefault(value)
}

internal fun isAllowedArticleUrl(raw: String, allowedHosts: Set<String>): Boolean = runCatching {
    val uri = java.net.URI(raw)
    uri.scheme.equals("https", ignoreCase = true) &&
        allowedHosts.any { allowed -> uri.host.equals(allowed, ignoreCase = true) }
}.getOrDefault(false)

internal fun parseRssDate(raw: String): Instant? {
    if (raw.isBlank()) return null
    val formatters = listOf(
        DateTimeFormatter.RFC_1123_DATE_TIME,
        DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("EEE, d MMM yyyy HH:mm:ss Z", Locale.ENGLISH),
    )
    formatters.forEach { formatter ->
        try {
            return ZonedDateTime.parse(raw.trim(), formatter).toInstant()
        } catch (_: DateTimeParseException) {
            // Try the next common RSS date representation.
        }
    }
    val value = raw.trim()
    return runCatching { Instant.parse(value) }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(value).toInstant() }.getOrNull()
        ?: localDateTimeInstant(value, "yyyy-MM-dd HH:mm:ss")
        ?: localDateTimeInstant(value, "yyyy-MM-dd'T'HH:mm:ss")
}

private fun localDateTimeInstant(raw: String, pattern: String): Instant? = runCatching {
    LocalDateTime.parse(raw, DateTimeFormatter.ofPattern(pattern)).toInstant(ZoneOffset.UTC)
}.getOrNull()

internal fun String.httpsHostOrNull(): String? = runCatching {
    java.net.URI(this).takeIf { it.scheme.equals("https", true) }?.host
}.getOrNull()
