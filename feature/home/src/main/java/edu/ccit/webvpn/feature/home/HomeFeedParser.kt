package edu.ccit.webvpn.feature.home

import com.prof18.rssparser.RssParserBuilder
import com.prof18.rssparser.model.RssItem
import java.io.IOException
import java.io.StringReader
import java.nio.charset.StandardCharsets
import java.util.UUID
import javax.xml.parsers.SAXParserFactory
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler

internal class FeedParsingException(
    val reason: FeedFailureKind,
    cause: Throwable? = null,
) : IOException(reason.userMessage, cause)

internal class HomeFeedParser(
    private val parser: com.prof18.rssparser.RssParser = RssParserBuilder().build(),
) {
    suspend fun parse(xml: String, source: FeedSource): ParsedFeed {
        if (xml.length > MAX_XML_CHARS) throw FeedParsingException(FeedFailureKind.TOO_LARGE)
        if (xml.contains("<!DOCTYPE", ignoreCase = true)) {
            throw FeedParsingException(FeedFailureKind.UNSAFE_DOCUMENT)
        }
        if (!SYNDICATION_ROOT.containsMatchIn(xml.take(ROOT_SCAN_CHARS))) {
            throw FeedParsingException(FeedFailureKind.INVALID_FEED)
        }
        validateWellFormedXml(xml)

        val extensions = extractItemExtensions(xml)
        val channel = try {
            parser.parse(xml)
        } catch (error: Throwable) {
            throw FeedParsingException(FeedFailureKind.INVALID_XML, error)
        }
        val channelSourceName = channel.title.orEmpty().trim().ifBlank { source.fallbackTitle }
        val avatarUrl = resolveHttpsUrl(channel.image?.url.orEmpty(), source.url)
            .takeIf(::isSafeHttpsUrl)
            .orEmpty()
        val articles = channel.items
            .mapIndexedNotNull { index, item ->
                item.toHomeArticle(source, channelSourceName, avatarUrl, extensions.getOrNull(index))
            }
            .distinctBy { article -> article.link.ifBlank { article.guid }.ifBlank { article.id } }
        return ParsedFeed(channelSourceName, avatarUrl, articles)
    }

    private fun validateWellFormedXml(xml: String) {
        try {
            val factory = SAXParserFactory.newInstance().apply { isNamespaceAware = true }
            runCatching { factory.setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            factory.newSAXParser().parse(InputSource(StringReader(xml)), DefaultHandler())
        } catch (error: Throwable) {
            throw FeedParsingException(FeedFailureKind.INVALID_XML, error)
        }
    }

    private fun RssItem.toHomeArticle(
        source: FeedSource,
        channelSourceName: String,
        avatarUrl: String,
        extension: ItemExtension?,
    ): HomeArticle? {
        val rawLink = sequenceOf(link, youtubeItemData?.videoUrl, guid, extension?.link, extension?.guid)
            .map(String?::orEmpty)
            .map(String::trim)
            .firstOrNull { it.isNotBlank() }
            .orEmpty()
        val cleanLink = resolveHttpsUrl(rawLink, source.url)
        if (!isSafeHttpsUrl(cleanLink)) return null

        val html = sequenceOf(
            content,
            extension?.contentEncoded,
            description,
            itunesItemData?.summary,
            youtubeItemData?.description,
        )
            .map(String?::orEmpty)
            .map(String::trim)
            .firstOrNull { it.isNotBlank() }
            .orEmpty()
        val document = Jsoup.parseBodyFragment(html)
        document.select("img").forEach { image ->
            if (image.attr("src").isBlank()) {
                image.attr("data-src").takeIf(String::isNotBlank)?.let { image.attr("src", it) }
            }
            image.removeAttr("data-src")
            image.removeAttr("loading")
        }
        document.select("script, style, noscript").remove()
        val normalizedHtml = document.body().html().trim()
        val plainText = document.text().trim()
        val cleanTitle = title.orEmpty().trim().ifBlank {
            plainText.take(TITLE_FALLBACK_LIMIT).trim().ifBlank { "无标题" }
        }
        val cleanGuid = guid.orEmpty().trim()
        val sourceName = itemSourceName(
            itemTitle = cleanTitle,
            creator = extension?.creator.orEmpty(),
            itemSource = extension?.sourceName.orEmpty(),
            channelSource = channelSourceName,
        )
        val htmlCover = document.selectFirst("img[src], img[data-src]")?.let { image ->
            image.attr("src").ifBlank { image.attr("data-src") }
        }.orEmpty()
        val enclosureImage = rawEnclosure
            ?.takeIf { it.type.orEmpty().startsWith("image/", ignoreCase = true) }
            ?.url
            .orEmpty()
        val mediaImage = rawMediaContent
            ?.takeIf {
                it.medium.equals("image", ignoreCase = true) ||
                    it.type.orEmpty().startsWith("image/", ignoreCase = true)
            }
            ?.url
            .orEmpty()
        val coverUrl = sequenceOf(
            image,
            itunesItemData?.image,
            youtubeItemData?.thumbnailUrl,
            htmlCover,
            mediaImage,
            enclosureImage,
            extension?.mediaUrl,
        ).map(String?::orEmpty)
            .map { resolveHttpsUrl(it, cleanLink) }
            .firstOrNull(::isSafeHttpsUrl)
            .orEmpty()
        val summaryText = plainText
            .replace("[提示]", "")
            .replace("文章内容正在获取中，请稍后刷新", "")
            .replace("查看原文 →", "")
            .trim()
        val summary = if (summaryText.length <= SUMMARY_LIMIT) {
            summaryText
        } else {
            summaryText.take(SUMMARY_LIMIT).trimEnd() + "…"
        }
        val identity = cleanGuid.ifBlank { cleanLink }
        val stableId = UUID.nameUUIDFromBytes(
            "${source.id}:$identity".toByteArray(StandardCharsets.UTF_8),
        ).toString()
        return HomeArticle(
            id = stableId,
            sourceId = source.id,
            sourceName = sourceName,
            sourceAvatarUrl = avatarUrl,
            title = cleanTitle,
            link = cleanLink,
            guid = cleanGuid,
            publishedAt = parseRssDate(pubDate.orEmpty().ifBlank { extension?.publishedAt.orEmpty() }),
            html = normalizedHtml,
            summary = summary,
            coverUrl = coverUrl,
            allowedArticleHosts = source.allowedArticleHosts + listOfNotNull(cleanLink.httpsHostOrNull()),
            section = source.section,
        )
    }

    /**
     * RSS Parser owns the format parsing. This pass only supplements namespace fields that some
     * RSS 1.0/RDF producers emit outside the library's current model mapping. Items are kept in
     * document order so they can be paired with the library result without changing its behavior.
     */
    private fun extractItemExtensions(xml: String): List<ItemExtension> {
        val document = try {
            Jsoup.parse(xml, "", Parser.xmlParser())
        } catch (_: Throwable) {
            return emptyList()
        }
        return document.getAllElements()
            .filter { it.localTagName() == "item" || it.localTagName() == "entry" }
            .map { item ->
                val directChildren = item.children()
                val alternateLink = directChildren
                    .filter { it.localTagName() == "link" }
                    .firstOrNull { it.attr("rel").isBlank() || it.attr("rel").equals("alternate", true) }
                val encoded = item.getAllElements().firstOrNull {
                    it.normalName().equals("content:encoded", true) ||
                        it.normalName().endsWith(":encoded", true)
                }
                val date = item.getAllElements().firstOrNull {
                    it.normalName().equals("dc:date", true)
                }
                val media = item.getAllElements().firstOrNull {
                    (it.normalName().equals("media:content", true) ||
                        it.normalName().equals("media:thumbnail", true)) && it.hasAttr("url")
                }
                val creator = item.getAllElements().firstOrNull {
                    it.normalName().equals("dc:creator", true) ||
                        it.normalName().endsWith(":creator", true) ||
                        it.localTagName() == "author"
                }
                val itemSource = directChildren.firstOrNull { it.localTagName() == "source" }
                ItemExtension(
                    link = alternateLink?.attr("href")?.ifBlank { alternateLink.text() }.orEmpty(),
                    guid = directChildren.firstOrNull {
                        it.localTagName() == "guid" || it.localTagName() == "id"
                    }?.text().orEmpty(),
                    contentEncoded = encoded?.wholeText().orEmpty().trim(),
                    publishedAt = date?.text().orEmpty().trim(),
                    mediaUrl = media?.attr("url").orEmpty().trim(),
                    creator = creator?.text().orEmpty().trim(),
                    sourceName = itemSource?.text().orEmpty().trim(),
                )
            }
    }

    private fun Element.localTagName(): String = normalName().substringAfterLast(':').lowercase()

    private data class ItemExtension(
        val link: String,
        val guid: String,
        val contentEncoded: String,
        val publishedAt: String,
        val mediaUrl: String,
        val creator: String,
        val sourceName: String,
    )

    private companion object {
        val SYNDICATION_ROOT = Regex(
            pattern = """<\s*(?:rss|feed|(?:[A-Za-z_][\w.-]*:)?RDF)\b""",
            option = RegexOption.IGNORE_CASE,
        )
        const val ROOT_SCAN_CHARS = 4096
        const val MAX_XML_CHARS = 6 * 1024 * 1024
        const val SUMMARY_LIMIT = 140
        const val TITLE_FALLBACK_LIMIT = 80
    }
}

private fun itemSourceName(
    itemTitle: String,
    creator: String,
    itemSource: String,
    channelSource: String,
): String {
    val itemCandidates = listOf(creator.trim(), itemSource.trim()).filter(String::isNotBlank)
    return itemCandidates.firstOrNull { !it.isFeedInfrastructureName() }
        ?: ITEM_SOURCE_PREFIX.find(itemTitle)?.groupValues?.getOrNull(1)?.trim()?.takeIf(String::isNotBlank)
        ?: itemCandidates.firstOrNull()
        ?: channelSource
}

private fun String.isFeedInfrastructureName(): Boolean {
    val normalized = lowercase()
    return normalized.contains("cloudflare") || normalized == "rsshub" || normalized == "rss hub"
}

private val ITEM_SOURCE_PREFIX = Regex("""^(?:\[|【)([^]】]+)(?:]|】)""")
