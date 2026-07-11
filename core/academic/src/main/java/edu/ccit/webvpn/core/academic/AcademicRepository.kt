package edu.ccit.webvpn.core.academic

import edu.ccit.webvpn.core.webvpn.WebVpnCookieJar
import edu.ccit.webvpn.core.webvpn.WebVpnSessionStore
import java.io.IOException
import java.time.LocalDate
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.CacheControl
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

class AcademicRepository(
    client: OkHttpClient,
    private val sessionStore: WebVpnSessionStore,
    private val cookieJar: WebVpnCookieJar,
) {
    private val client = client
    private val noRedirectClient = client.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .build()
    private var timetableSchemeId: String? = null

    suspend fun restoreSession(): AcademicOverview? = withContext(Dispatchers.IO) {
        runCatching { requestOverview() }
            .getOrElse { error ->
                if (error is AcademicApiException && error.loginRequired) null else throw error
            }
    }

    fun webViewCookies(): List<String> = cookieJar
        .loadForRequest(BaseUrl)
        .map { it.toString() }

    suspend fun loadCaptcha(): ByteArray = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(BaseUrl.resolve("verifycode.servlet")!!)
            .cacheControl(CacheControl.FORCE_NETWORK)
            .get()
            .build()
        execute(client, request).use { response ->
            response.requireSuccessful("加载教务验证码失败")
            val bytes = response.body?.bytes() ?: byteArrayOf()
            if (bytes.isEmpty()) throw AcademicApiException("教务系统未返回验证码图片")
            persistCookies()
            bytes
        }
    }

    suspend fun login(
        username: String,
        password: String,
        captchaCode: String,
    ): AcademicOverview = withContext(Dispatchers.IO) {
        val normalizedUsername = username.trim()
        require(normalizedUsername.isNotBlank()) { "请输入教务系统账号" }
        require(password.isNotBlank()) { "请输入教务系统密码" }
        require(captchaCode.isNotBlank()) { "请输入教务系统验证码" }

        val form = FormBody.Builder()
            .add("userAccount", "")
            .add("userPassword", "")
            .add("RANDOMCODE", captchaCode.trim())
            .add("encoded", encodeCredentials(normalizedUsername, password))
            .build()
        val request = Request.Builder()
            .url(BaseUrl.resolve("xk/LoginToXk")!!)
            .post(form)
            .build()

        execute(noRedirectClient, request).use { response ->
            val location = response.header("Location")
            val successfulRedirect = response.code in 300..399 &&
                location?.contains("/jsxsd/framework/xsMain.jsp", ignoreCase = true) == true
            if (!successfulRedirect) {
                val html = response.body?.string().orEmpty()
                val message = AcademicHtmlParser.loginError(html)
                    ?: if (response.code in 300..399) "教务系统返回了未识别的登录跳转" else "教务系统账号、密码或验证码错误"
                throw AcademicApiException(message, loginRequired = true)
            }
        }

        persistCookies()
        requestOverview()
    }

    suspend fun loadGrades(
        semester: String,
        bestOnly: Boolean,
    ): List<CourseGrade> = withContext(Dispatchers.IO) {
        val url = BaseUrl.resolve("kscj/cjcx_list")!!.newBuilder()
            .addQueryParameter("kksj", semester)
            .addQueryParameter("xsfs", if (bestOnly) "max" else "all")
            .build()
        val html = getHtml(url.toString(), "加载成绩失败")
        if (AcademicHtmlParser.isStudentLoginPage(html)) {
            throw AcademicApiException("教务系统登录已过期，请重新登录", loginRequired = true)
        }
        AcademicHtmlParser.parseGrades(html)
    }

    suspend fun loadTimetable(semester: String? = null): AcademicTimetable = withContext(Dispatchers.IO) {
        val requestedSemester = semester?.takeIf(String::isNotBlank)
        var current: AcademicTimetable? = null
        if (requestedSemester == null || timetableSchemeId.isNullOrBlank()) {
            current = requestTimetable()
            timetableSchemeId = current.schemeId
            if (requestedSemester == null || current.selectedTerm == requestedSemester) {
                return@withContext current.withTeachingWeek(LocalDate.now())
            }
        }

        val form = FormBody.Builder()
            .add("jx0404id", "")
            .add("cj0701id", "")
            .add("zc", "")
            .add("demo", "")
            .add("xnxq01id", requestedSemester!!)
            .add("sfFD", "1")
            .add("kbjcmsid", timetableSchemeId.orEmpty())
            .build()
        val request = Request.Builder()
            .url(BaseUrl.resolve("xskb/xskb_list.do")!!)
            .post(form)
            .build()
        val timetable = execute(client, request).use { response ->
            response.requireSuccessful("加载理论课表失败")
            parseTimetableResponse(response.body?.string().orEmpty())
        }
        timetableSchemeId = timetable.schemeId.ifBlank { timetableSchemeId.orEmpty() }
        persistCookies()
        timetable.withTeachingWeek(LocalDate.now())
    }

    suspend fun logout() = withContext(Dispatchers.IO) {
        // The academic JSESSIONID belongs to the proxied host. Keep the parent-domain
        // WebVPN cookies so signing out here does not disconnect the whole app.
        cookieJar.clearHost(BaseUrl.host)
        persistCookies()
    }

    private suspend fun requestOverview(): AcademicOverview {
        val html = getHtml(BaseUrl.resolve("kscj/cjcx_query")!!.toString(), "连接教务系统失败")
        if (AcademicHtmlParser.isStudentLoginPage(html)) {
            throw AcademicApiException("请登录教务系统", loginRequired = true)
        }
        val terms = AcademicHtmlParser.parseTerms(html)
        if (terms.isEmpty()) {
            throw AcademicApiException("无法识别教务系统的学期列表，页面结构可能已更新")
        }
        persistCookies()
        return AcademicOverview(terms)
    }

    private suspend fun requestTimetable(): AcademicTimetable {
        val html = getHtml(BaseUrl.resolve("xskb/xskb_list.do")!!.toString(), "加载理论课表失败")
        return parseTimetableResponse(html).also { persistCookies() }
    }

    private fun AcademicTimetable.withTeachingWeek(date: LocalDate): AcademicTimetable {
        val form = FormBody.Builder()
            .add("rq", date.toString())
            .add("sjmsValue", schemeId)
            .build()
        val request = Request.Builder()
            .url(BaseUrl.resolve("framework/main_index_loadkb.jsp")!!)
            .post(form)
            .build()
        val teachingWeek = runCatching {
            execute(client, request).use { response ->
                if (!response.isSuccessful) return@use null
                AcademicHtmlParser.parseTeachingWeek(response.body?.string().orEmpty())
            }
        }.getOrNull()
        return copy(
            referenceDate = date,
            referenceWeek = teachingWeek?.week,
            totalWeeks = teachingWeek?.totalWeeks,
        )
    }

    private fun parseTimetableResponse(html: String): AcademicTimetable {
        if (AcademicHtmlParser.isStudentLoginPage(html)) {
            throw AcademicApiException("教务系统登录已过期，请重新登录", loginRequired = true)
        }
        return AcademicHtmlParser.parseTimetable(html)
            ?: throw AcademicApiException("无法识别理论课表，页面结构可能已更新")
    }

    private fun getHtml(url: String, failureMessage: String): String {
        val request = Request.Builder().url(url).get().build()
        return execute(client, request).use { response ->
            response.requireSuccessful(failureMessage)
            val finalUrl = response.request.url
            if (finalUrl.host == WebVpnHost && finalUrl.encodedPath.startsWith("/auth/")) {
                throw AcademicApiException("WebVPN 登录已过期，请重新登录", loginRequired = true)
            }
            if (finalUrl.host == BaseUrl.host &&
                (finalUrl.encodedPath == "/jsxsd/" ||
                    finalUrl.encodedPath.endsWith("/xk/LoginToXk"))
            ) {
                throw AcademicApiException("教务系统登录已过期，请重新登录", loginRequired = true)
            }
            response.body?.string() ?: throw AcademicApiException("教务系统返回了空页面")
        }
    }

    private fun execute(client: OkHttpClient, request: Request): Response = try {
        client.newCall(request).execute()
    } catch (_: IOException) {
        throw AcademicApiException("无法连接学校教务系统，请检查网络后重试")
    }

    private fun Response.requireSuccessful(message: String) {
        if (!isSuccessful) {
            throw AcademicApiException("$message（HTTP $code）", loginRequired = code == 401 || code == 402)
        }
    }

    private suspend fun persistCookies() {
        // Includes both the WebVPN parent-domain cookies and the proxied JSESSIONID.
        sessionStore.saveCookies(cookieJar.snapshot())
    }

    companion object {
        const val BaseUrlString = "https://http-10-198-47-148-8080.webvpn.ccit.edu.cn/jsxsd/"
        private val BaseUrl = BaseUrlString.toHttpUrl()
        private const val WebVpnHost = "webvpn.ccit.edu.cn"

        internal fun encodeCredentials(username: String, password: String): String {
            val encoder = Base64.getEncoder()
            return encoder.encodeToString(username.toByteArray(Charsets.UTF_8)) +
                "%%%" +
                encoder.encodeToString(password.toByteArray(Charsets.UTF_8))
        }
    }
}
