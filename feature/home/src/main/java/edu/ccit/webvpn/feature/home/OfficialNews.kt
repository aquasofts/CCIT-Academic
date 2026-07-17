package edu.ccit.webvpn.feature.home

import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.IOException
import java.io.StringReader
import java.net.URI
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.parser.Parser

internal data class OfficialNewsSource(
    val id: String,
    val title: String,
    val ownerId: String,
    val contentId: String,
    val contentBaseUrl: String,
    val listUrl: String = OFFICIAL_NEWS_LIST_URL,
    val detailUrl: String = OFFICIAL_NEWS_DETAIL_URL,
)

internal data class OfficialNewsReference(
    val ownerId: String,
    val newsId: String,
    val contentBaseUrl: String,
    val detailUrl: String,
    val detailLoaded: Boolean = false,
)

internal object OfficialNewsSources {
    val all = listOf(
        OfficialNewsSource(
            id = "official_school_news",
            title = "学校新闻",
            ownerId = "2144790275",
            contentId = "1074518",
            contentBaseUrl = "https://www.ccit.edu.cn/",
        ),
        OfficialNewsSource(
            id = "official_notices",
            title = "通知公告",
            ownerId = "2144790275",
            contentId = "1074519",
            contentBaseUrl = "https://www.ccit.edu.cn/",
        ),
        OfficialNewsSource(
            id = "official_academic_news",
            title = "学术新闻",
            ownerId = "1913672758",
            contentId = "1073128",
            contentBaseUrl = "https://kjc.ccit.edu.cn/",
        ),
    )
}

internal class OfficialNewsParser {
    fun parseList(xml: String, source: OfficialNewsSource): List<HomeArticle> {
        val payload = soapReturn(xml, "getListByContentIdReturn")
        val articles = mutableListOf<HomeArticle>()
        JsonReader(StringReader(payload)).use { reader ->
            reader.beginArray()
            while (reader.hasNext()) {
                var newsId = ""
                var title = ""
                var date = ""
                var html = ""
                reader.beginObject()
                while (reader.hasNext()) {
                    when (reader.nextName()) {
                        "id" -> newsId = reader.nextScalarString()
                        "title" -> title = reader.nextScalarString()
                        "date" -> date = reader.nextScalarString()
                        "content" -> html = reader.nextScalarString()
                        else -> reader.skipValue()
                    }
                }
                reader.endObject()
                if (newsId.isBlank()) continue
                val cleanHtml = sanitizeOfficialHtml(html, source.contentBaseUrl)
                val summary = officialSummary(cleanHtml)
                val identity = "${source.ownerId}:$newsId"
                articles += HomeArticle(
                    id = UUID.nameUUIDFromBytes(
                        "official:$identity".toByteArray(StandardCharsets.UTF_8),
                    ).toString(),
                    sourceId = source.id,
                    sourceName = source.title,
                    sourceAvatarUrl = "",
                    title = title.trim().ifBlank { summary.take(80).ifBlank { "无标题" } },
                    link = source.contentBaseUrl,
                    guid = identity,
                    publishedAt = parseOfficialDate(date),
                    html = cleanHtml,
                    summary = summary,
                    coverUrl = firstSecureImage(cleanHtml),
                    allowedArticleHosts = emptySet(),
                    officialReference = OfficialNewsReference(
                        ownerId = source.ownerId,
                        newsId = newsId,
                        contentBaseUrl = source.contentBaseUrl,
                        detailUrl = source.detailUrl,
                    ),
                    section = HomeSection.OFFICIAL,
                )
            }
            reader.endArray()
        }
        return articles
    }

    fun parseDetail(xml: String, current: HomeArticle): HomeArticle {
        val reference = requireNotNull(current.officialReference)
        val payload = soapReturn(xml, "getWbnewsByIdReturn")
        var title = ""
        var date = ""
        var rawHtml = ""
        JsonReader(StringReader(payload)).use { reader ->
            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "wbtitle" -> title = reader.nextScalarString()
                    "wbdate" -> date = reader.nextScalarString()
                    "wbcontent" -> rawHtml = reader.nextScalarString()
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
        }
        // The list response already expands VSB's extension-less image tags into working URLs.
        // Prefer it when available; the detail endpoint otherwise needs a browser-only fallback
        // loop to guess jpg/png/gif extensions.
        val html = if (rawHtml.contains("<vsbimg", ignoreCase = true) && current.html.isNotBlank()) {
            current.html
        } else {
            sanitizeOfficialHtml(rawHtml, reference.contentBaseUrl).ifBlank { current.html }
        }
        return current.copy(
            title = title.trim().ifBlank { current.title },
            publishedAt = parseOfficialDate(date) ?: current.publishedAt,
            html = html,
            summary = officialSummary(html).ifBlank { current.summary },
            coverUrl = firstSecureImage(html).ifBlank { current.coverUrl },
            officialReference = reference.copy(detailLoaded = true),
        )
    }

    private fun soapReturn(xml: String, returnElement: String): String {
        if (xml.length > MAX_OFFICIAL_XML_CHARS) throw FeedParsingException(FeedFailureKind.TOO_LARGE)
        if (xml.contains("<!DOCTYPE", ignoreCase = true)) {
            throw FeedParsingException(FeedFailureKind.UNSAFE_DOCUMENT)
        }
        val document = try {
            Jsoup.parse(xml, "", Parser.xmlParser())
        } catch (error: Throwable) {
            throw FeedParsingException(FeedFailureKind.INVALID_XML, error)
        }
        val expected = returnElement.lowercase()
        return document.getAllElements()
            .firstOrNull { it.normalName().substringAfterLast(':').lowercase() == expected }
            ?.wholeText()
            ?.takeIf(String::isNotBlank)
            ?: throw FeedParsingException(FeedFailureKind.INVALID_FEED)
    }
}

internal fun officialListEnvelope(source: OfficialNewsSource, count: Int = 100): String {
    require(source.ownerId.all(Char::isDigit) && source.contentId.all(Char::isDigit))
    return """
        <soapenv:Envelope xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:xsd="http://www.w3.org/2001/XMLSchema" xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:web="http://webservice.vsb.webber">
          <soapenv:Header/><soapenv:Body><web:getListByContentId soapenv:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
            <owner xsi:type="soapenc:string" xmlns:soapenc="http://schemas.xmlsoap.org/soap/encoding/">${source.ownerId}</owner>
            <contentid xsi:type="soapenc:string" xmlns:soapenc="http://schemas.xmlsoap.org/soap/encoding/">${source.contentId}</contentid>
            <start xsi:type="xsd:int">0</start><count xsi:type="xsd:int">${count.coerceIn(1, 100)}</count>
          </web:getListByContentId></soapenv:Body>
        </soapenv:Envelope>
    """.trimIndent()
}

internal fun officialDetailEnvelope(reference: OfficialNewsReference): String {
    require(reference.ownerId.all(Char::isDigit) && reference.newsId.all(Char::isDigit))
    return """
        <soapenv:Envelope xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:xsd="http://www.w3.org/2001/XMLSchema" xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:web="http://webservice.vsb.webber">
          <soapenv:Header/><soapenv:Body><web:getWbnewsById soapenv:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
            <wbnewsid xsi:type="xsd:int">${reference.newsId}</wbnewsid>
            <owner xsi:type="soapenc:string" xmlns:soapenc="http://schemas.xmlsoap.org/soap/encoding/">${reference.ownerId}</owner>
          </web:getWbnewsById></soapenv:Body>
        </soapenv:Envelope>
    """.trimIndent()
}

private fun JsonReader.nextScalarString(): String = when (peek()) {
    JsonToken.NULL -> {
        nextNull()
        ""
    }
    JsonToken.STRING, JsonToken.NUMBER, JsonToken.BOOLEAN -> nextString()
    else -> {
        skipValue()
        ""
    }
}

private fun sanitizeOfficialHtml(raw: String, baseUrl: String): String {
    if (raw.isBlank()) return ""
    val document = Jsoup.parseBodyFragment(raw, baseUrl)
    document.select("script, noscript, object, embed").remove()
    expandVsbImages(document, baseUrl)
    document.getAllElements().forEach { element ->
        element.attributes().asList()
            .filter { it.key.startsWith("on", ignoreCase = true) }
            .forEach { element.removeAttr(it.key) }
        element.attr("style").takeIf(String::isNotBlank)?.let { style ->
            val safeStyle = style.split(';')
                .filterNot { declaration ->
                    val property = declaration.substringBefore(':').trim()
                    property.equals("color", true) || property.equals("background", true) ||
                        property.equals("background-color", true)
                }
                .joinToString(";")
            if (safeStyle.isBlank()) element.removeAttr("style") else element.attr("style", safeStyle)
        }
    }
    document.select("img").forEach { image ->
        val rawUrl = image.attr("src").ifBlank { image.attr("data-src") }
        val secureUrl = secureContentUrl(rawUrl, baseUrl)
        if (secureUrl.isBlank()) {
            image.remove()
        } else {
            image.attr("src", secureUrl)
            image.removeAttr("data-src")
            image.attr("loading", "lazy")
            image.attr("decoding", "async")
        }
    }
    document.select("a[href]").forEach { anchor ->
        val secureUrl = secureContentUrl(anchor.attr("href"), baseUrl)
        if (secureUrl.isBlank()) anchor.removeAttr("href") else anchor.attr("href", secureUrl)
    }
    return document.body().html().trim()
}

private fun expandVsbImages(document: Document, baseUrl: String) {
    document.select("vsbimg[src]").forEach { image ->
        val match = VSB_IMAGE_PATH.matchEntire(image.attr("src")) ?: return@forEach
        val hash = match.groupValues[1]
        val localPath = "${hash.take(1)}/${hash.drop(1).take(2)}/${hash.drop(3).take(2)}/${hash.drop(5)}"
        val url = "${baseUrl.trimEnd('/')}/__local/${localPath}_${match.groupValues[2]}_${match.groupValues[3]}.jpg"
        image.tagName("img")
        image.attr("src", url)
    }
}

private fun secureContentUrl(raw: String, baseUrl: String): String {
    if (raw.isBlank()) return ""
    return runCatching {
        val resolved = URI(baseUrl).resolve(raw.trim())
        val secure = if (resolved.scheme.equals("http", true) && resolved.host.endsWith("ccit.edu.cn", true)) {
            URI("https", resolved.userInfo, resolved.host, resolved.port, resolved.path, resolved.query, resolved.fragment)
        } else {
            resolved
        }
        secure.toString().takeIf(::isSafeHttpsUrl).orEmpty()
    }.getOrDefault("")
}

private fun firstSecureImage(html: String): String = Jsoup.parseBodyFragment(html)
    .selectFirst("img[src]")
    ?.attr("src")
    ?.takeIf(::isSafeHttpsUrl)
    .orEmpty()

private fun officialSummary(html: String): String {
    val text = Jsoup.parseBodyFragment(html).also { it.select("script, style, noscript").remove() }.text().trim()
    return if (text.length <= OFFICIAL_SUMMARY_LIMIT) text else text.take(OFFICIAL_SUMMARY_LIMIT).trimEnd() + "…"
}

private fun parseOfficialDate(raw: String): Instant? = runCatching {
    LocalDateTime.parse(raw.trim(), OFFICIAL_DATE_FORMATTER)
        .atZone(OFFICIAL_TIME_ZONE)
        .toInstant()
}.getOrNull()

private val OFFICIAL_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
private val OFFICIAL_TIME_ZONE = ZoneId.of("Asia/Shanghai")
private val VSB_IMAGE_PATH = Regex("""/_vsl/([A-Fa-f0-9]+)/([A-Fa-f0-9]+)/([A-Fa-f0-9]+)""")
private const val OFFICIAL_SUMMARY_LIMIT = 140
private const val MAX_OFFICIAL_XML_CHARS = 6 * 1024 * 1024
private const val OFFICIAL_NEWS_LIST_URL = "https://gateway.ccit.edu.cn/ccit/news/getList"
private const val OFFICIAL_NEWS_DETAIL_URL = "https://gateway.ccit.edu.cn/ccit/news/getOwner"
