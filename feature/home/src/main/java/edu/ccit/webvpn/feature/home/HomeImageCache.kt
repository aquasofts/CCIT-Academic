package edu.ccit.webvpn.feature.home

import android.content.Context
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup

internal data class CachedHomeImage(val file: File, val mimeType: String)

/**
 * A deterministic file cache for feed and article images.
 *
 * Coil and WebView maintain independent HTTP caches and either cache can revalidate on every
 * reopen when a publisher sends restrictive headers. This cache owns the bytes instead, so both
 * renderers can reuse exactly the same local file without contacting the publisher again.
 */
internal class HomeImageCache private constructor(context: Context) {
    private val directory = File(context.cacheDir, "home_image_cache").apply { mkdirs() }
    private val locks = ConcurrentHashMap<String, Any>()
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    fun cached(url: String): CachedHomeImage? {
        if (!isSafeHttpsUrl(url)) return null
        val key = cacheKey(url)
        val image = File(directory, "$key.img")
        if (!image.isFile || image.length() <= 0L) return null
        image.setLastModified(System.currentTimeMillis())
        val mime = runCatching { File(directory, "$key.mime").takeIf(File::isFile)?.readText() }.getOrNull()
            ?.takeIf { it.startsWith("image/") }
            ?: mimeFromUrl(url)
        return CachedHomeImage(image, mime)
    }

    suspend fun getOrFetch(url: String): CachedHomeImage? = withContext(Dispatchers.IO) {
        getOrFetchBlocking(url)
    }

    fun getOrFetchBlocking(url: String): CachedHomeImage? {
        cached(url)?.let { return it }
        if (!isSafeHttpsUrl(url)) return null
        val key = cacheKey(url)
        return synchronized(locks.getOrPut(key) { Any() }) {
            try {
                cached(url)?.let { return@synchronized it }
                val request = Request.Builder()
                    .url(url)
                    .header("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8")
                    .header("User-Agent", "Cithub Image Cache")
                    .get()
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful || !response.request.url.isHttps) return@synchronized null
                    val body = response.body
                    val length = body.contentLength()
                    if (length > MAX_IMAGE_BYTES) return@synchronized null
                    val mime = body.contentType()?.toString()?.substringBefore(';')
                        ?.takeIf { it.startsWith("image/") }
                        ?: mimeFromUrl(response.request.url.toString())
                    if (!mime.startsWith("image/")) return@synchronized null
                    val target = File(directory, "$key.img")
                    val temporary = File(directory, "$key.part")
                    var total = 0L
                    body.byteStream().use { input ->
                        temporary.outputStream().buffered().use { output ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            while (true) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                total += read
                                if (total > MAX_IMAGE_BYTES) throw IOException("Image exceeds cache limit")
                                output.write(buffer, 0, read)
                            }
                        }
                    }
                    if (total == 0L || (!temporary.renameTo(target) && runCatching {
                            temporary.copyTo(target, overwrite = true)
                            temporary.delete()
                        }.isFailure)
                    ) {
                        temporary.delete()
                        return@synchronized null
                    }
                    File(directory, "$key.mime").writeText(mime)
                    trim()
                    CachedHomeImage(target, mime)
                }
            } catch (_: IOException) {
                File(directory, "$key.part").delete()
                null
            }
        }
    }

    suspend fun prefetch(articles: List<HomeArticle>) {
        val urls = articles.asSequence().flatMap(::articleImageUrls).distinct().toList()
        if (urls.isEmpty()) return
        val permits = Semaphore(PREFETCH_CONCURRENCY)
        coroutineScope {
            urls.map { url -> async(Dispatchers.IO) { permits.withPermit { getOrFetchBlocking(url) } } }.awaitAll()
        }
    }

    private fun trim() {
        val images = directory.listFiles { file -> file.extension == "img" }.orEmpty()
            .sortedBy(File::lastModified)
        var total = images.sumOf(File::length)
        images.forEach { image ->
            if (total <= MAX_CACHE_BYTES) return
            total -= image.length()
            image.delete()
            File(directory, "${image.nameWithoutExtension}.mime").delete()
        }
    }

    companion object {
        @Volatile private var instance: HomeImageCache? = null

        fun get(context: Context): HomeImageCache = instance ?: synchronized(this) {
            instance ?: HomeImageCache(context.applicationContext).also { instance = it }
        }

        private const val MAX_IMAGE_BYTES = 24L * 1024 * 1024
        private const val MAX_CACHE_BYTES = 192L * 1024 * 1024
        private const val PREFETCH_CONCURRENCY = 4
    }
}

internal fun articleImageUrls(article: HomeArticle): Sequence<String> = sequence {
    if (isSafeHttpsUrl(article.sourceAvatarUrl)) yield(article.sourceAvatarUrl)
    if (isSafeHttpsUrl(article.coverUrl)) yield(article.coverUrl)
    val document = Jsoup.parseBodyFragment(article.html, article.link)
    for (image in document.select("img")) {
        val raw = image.attr("src").ifBlank { image.attr("data-src") }
        val resolved = resolveHttpsUrl(raw, article.link)
        if (isSafeHttpsUrl(resolved)) yield(resolved)
    }
}

private fun cacheKey(url: String): String = MessageDigest.getInstance("SHA-256")
    .digest(url.toByteArray())
    .joinToString("") { "%02x".format(it) }

private fun mimeFromUrl(url: String): String = when (url.substringBefore('?').substringAfterLast('.', "").lowercase()) {
    "png" -> "image/png"
    "gif" -> "image/gif"
    "webp" -> "image/webp"
    "avif" -> "image/avif"
    "svg" -> "image/svg+xml"
    else -> "image/jpeg"
}
