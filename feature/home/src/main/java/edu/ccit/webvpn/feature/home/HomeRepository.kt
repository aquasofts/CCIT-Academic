package edu.ccit.webvpn.feature.home

import android.content.Context
import android.util.AtomicFile
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

internal data class FeedRefreshResult(
    val articles: List<HomeArticle>,
    val sourceStatuses: List<FeedSourceStatus>,
) {
    val errorMessage: String? = sourceStatuses
        .mapNotNull(FeedSourceStatus::failureMessage)
        .takeIf(List<String>::isNotEmpty)
        ?.let { messages ->
            val visible = messages.take(MAX_VISIBLE_FAILURES).joinToString("；")
            if (messages.size > MAX_VISIBLE_FAILURES) {
                "$visible；另有 ${messages.size - MAX_VISIBLE_FAILURES} 个来源加载失败"
            } else {
                visible
            }
        }

    private companion object {
        const val MAX_VISIBLE_FAILURES = 2
    }
}

internal class HomeRepository(
    private val cacheDirectory: File,
    private val client: OkHttpClient,
    private val sources: List<FeedSource> = HomeFeedSources.all,
    private val parser: HomeFeedParser = HomeFeedParser(),
    private val allowInsecureTestTransport: Boolean = false,
    private val officialSources: List<OfficialNewsSource> = OfficialNewsSources.all,
    private val officialParser: OfficialNewsParser = OfficialNewsParser(),
) {
    suspend fun loadCached(section: HomeSection): List<HomeArticle> = withContext(Dispatchers.IO) {
        if (section == HomeSection.OFFICIAL) {
            officialSources.flatMap { source -> readOfficialCache(source) }.mergedAndSorted()
        } else {
            sectionSources(section).flatMap { source -> readCached(source) }.mergedAndSorted()
        }
    }

    suspend fun refresh(section: HomeSection): FeedRefreshResult = if (section == HomeSection.OFFICIAL) {
        refreshOfficial()
    } else {
        refreshFeeds(section)
    }

    suspend fun loadOfficialDetail(article: HomeArticle): HomeArticle = withContext(Dispatchers.IO) {
        val reference = article.officialReference ?: return@withContext article
        if (reference.detailLoaded) return@withContext article
        val request = officialRequest(reference.detailUrl, officialDetailEnvelope(reference))
        client.newCall(request).execute().use { response ->
            validateResponse(response)
            officialParser.parseDetail(response.readLimitedDocument(), article)
        }
    }

    private suspend fun refreshFeeds(section: HomeSection): FeedRefreshResult = supervisorScope {
        val results = sectionSources(section).map { source ->
            async(Dispatchers.IO) {
                try {
                    SourceResult(
                        articles = fetch(source),
                        status = FeedSourceStatus(source.id, source.fallbackTitle, fresh = true, usedCache = false),
                    )
                } catch (error: Throwable) {
                    val failure = error.toFeedFailure()
                    val cached = readCached(source)
                    SourceResult(
                        articles = cached,
                        status = FeedSourceStatus(
                            sourceId = source.id,
                            sourceName = source.fallbackTitle,
                            fresh = false,
                            usedCache = cached.isNotEmpty(),
                            failure = failure.first,
                            httpStatus = failure.second,
                        ),
                    )
                }
            }
        }.awaitAll()
        FeedRefreshResult(
            articles = results.flatMap(SourceResult::articles).mergedAndSorted(),
            sourceStatuses = results.map(SourceResult::status),
        )
    }

    private suspend fun refreshOfficial(): FeedRefreshResult = supervisorScope {
        val results = officialSources.map { source ->
            async(Dispatchers.IO) {
                try {
                    SourceResult(
                        articles = fetchOfficial(source),
                        status = FeedSourceStatus(source.id, source.title, fresh = true, usedCache = false),
                    )
                } catch (error: Throwable) {
                    val failure = error.toFeedFailure()
                    val cached = readOfficialCache(source)
                    SourceResult(
                        articles = cached,
                        status = FeedSourceStatus(
                            sourceId = source.id,
                            sourceName = source.title,
                            fresh = false,
                            usedCache = cached.isNotEmpty(),
                            failure = failure.first,
                            httpStatus = failure.second,
                        ),
                    )
                }
            }
        }.awaitAll()
        FeedRefreshResult(
            articles = results.flatMap(SourceResult::articles).mergedAndSorted(),
            sourceStatuses = results.map(SourceResult::status),
        )
    }

    private fun sectionSources(section: HomeSection): List<FeedSource> = sources.filter { it.section == section }

    private suspend fun fetch(source: FeedSource): List<HomeArticle> {
        val initialUrl = source.url.toHttpUrlOrNull()
            ?: throw FeedLoadException(FeedFailureKind.UNSAFE_TRANSPORT)
        if (!initialUrl.isHttps && !allowInsecureTestTransport) {
            throw FeedLoadException(FeedFailureKind.UNSAFE_TRANSPORT)
        }
        val request = Request.Builder()
            .url(initialUrl)
            .header("Accept", "application/rss+xml, application/atom+xml, application/xml, text/xml;q=0.9, */*;q=0.8")
            .header("User-Agent", "Cithub RSS Reader")
            .get()
            .build()
        val response = client.newCall(request).execute()
        response.use {
            validateResponse(it)
            val xml = it.readLimitedDocument()
            val parsed = parser.parse(xml, source)
            writeCache(source, xml)
            return parsed.articles
        }
    }

    private fun fetchOfficial(source: OfficialNewsSource): List<HomeArticle> {
        val request = officialRequest(source.listUrl, officialListEnvelope(source))
        client.newCall(request).execute().use { response ->
            validateResponse(response)
            val xml = response.readLimitedDocument()
            val articles = officialParser.parseList(xml, source)
            writeOfficialCache(source, xml)
            return articles
        }
    }

    private fun officialRequest(url: String, envelope: String): Request {
        val initialUrl = url.toHttpUrlOrNull() ?: throw FeedLoadException(FeedFailureKind.UNSAFE_TRANSPORT)
        if (!initialUrl.isHttps && !allowInsecureTestTransport) {
            throw FeedLoadException(FeedFailureKind.UNSAFE_TRANSPORT)
        }
        return Request.Builder()
            .url(initialUrl)
            .header("Accept", "text/xml, application/xml;q=0.9, */*;q=0.8")
            .header("User-Agent", "Cithub Official News Reader")
            .post(envelope.toRequestBody(OFFICIAL_SOAP_MEDIA_TYPE))
            .build()
    }

    private fun validateResponse(response: Response) {
        val finalUrl = response.request.url
        val secureTransport = finalUrl.isHttps || allowInsecureTestTransport
        if (!response.isSuccessful || !secureTransport) {
            val reason = if (!secureTransport) FeedFailureKind.UNSAFE_TRANSPORT else FeedFailureKind.HTTP
            throw FeedLoadException(reason, httpStatus = response.code.takeIf { !response.isSuccessful })
        }
    }

    private fun Response.readLimitedDocument(): String {
        val responseBody = body
        if (responseBody.contentLength() > MAX_RESPONSE_BYTES) {
            throw FeedLoadException(FeedFailureKind.TOO_LARGE)
        }
        responseBody.byteStream().use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > MAX_RESPONSE_BYTES) throw FeedLoadException(FeedFailureKind.TOO_LARGE)
                output.write(buffer, 0, read)
            }
            return FeedDocumentDecoder.decode(
                bytes = output.toByteArray(),
                httpCharset = responseBody.contentType()?.charset(null),
            )
        }
    }

    private suspend fun readCached(source: FeedSource): List<HomeArticle> = runCatching {
        val file = AtomicFile(cacheFile(source))
        if (!file.baseFile.isFile) return@runCatching emptyList()
        if (file.baseFile.length() > MAX_RESPONSE_BYTES) return@runCatching emptyList()
        val xml = file.openRead().bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        parser.parse(xml, source).articles
    }.getOrDefault(emptyList())

    private fun readOfficialCache(source: OfficialNewsSource): List<HomeArticle> = runCatching {
        val file = AtomicFile(cacheFile(source))
        if (!file.baseFile.isFile) return@runCatching emptyList()
        if (file.baseFile.length() > MAX_RESPONSE_BYTES) return@runCatching emptyList()
        val xml = file.openRead().bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        officialParser.parseList(xml, source)
    }.getOrDefault(emptyList())

    private fun writeCache(source: FeedSource, xml: String) {
        writeCacheFile(cacheFile(source), xml)
    }

    private fun writeOfficialCache(source: OfficialNewsSource, xml: String) {
        writeCacheFile(cacheFile(source), xml)
    }

    private fun writeCacheFile(target: File, xml: String) {
        if (!cacheDirectory.exists() && !cacheDirectory.mkdirs()) return
        val atomicFile = AtomicFile(target)
        val stream = runCatching { atomicFile.startWrite() }.getOrNull() ?: return
        try {
            stream.write(xml.toByteArray(StandardCharsets.UTF_8))
            stream.flush()
            atomicFile.finishWrite(stream)
        } catch (_: IOException) {
            atomicFile.failWrite(stream)
        }
    }

    private fun cacheFile(source: FeedSource): File = File(cacheDirectory, "${source.id}.xml")
    private fun cacheFile(source: OfficialNewsSource): File = File(cacheDirectory, "${source.id}.xml")

    private data class SourceResult(
        val articles: List<HomeArticle>,
        val status: FeedSourceStatus,
    )

    companion object {
        private const val MAX_RESPONSE_BYTES = 5 * 1024 * 1024

        fun create(
            context: Context,
            sources: List<FeedSource> = HomeFeedSources.all,
        ): HomeRepository = HomeRepository(
            cacheDirectory = File(context.noBackupFilesDir, "home_feed_cache"),
            client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .callTimeout(30, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build(),
            sources = sources,
        )
    }
}

private fun Throwable.toFeedFailure(): Pair<FeedFailureKind, Int?> = when (this) {
    is FeedLoadException -> reason to httpStatus
    is FeedParsingException -> reason to null
    is IOException -> FeedFailureKind.NETWORK to null
    else -> FeedFailureKind.INVALID_FEED to null
}

private fun List<HomeArticle>.mergedAndSorted(): List<HomeArticle> =
    distinctBy { article ->
        if (article.officialReference != null) article.id else article.link.ifBlank { article.guid }.ifBlank { article.id }
    }
        .sortedWith(
            compareByDescending<HomeArticle> { it.publishedAt != null }
                .thenByDescending { it.publishedAt }
                .thenBy { it.title },
        )

private val OFFICIAL_SOAP_MEDIA_TYPE = "text/plain;charset=UTF-8".toMediaType()
