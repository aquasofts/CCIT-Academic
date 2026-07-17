package edu.ccit.webvpn.feature.home

import java.io.IOException

internal enum class FeedFailureKind(val userMessage: String) {
    NETWORK("网络连接失败"),
    HTTP("服务器返回错误"),
    ENCODING("字符编码无法识别"),
    INVALID_XML("XML 解析失败"),
    INVALID_FEED("不是支持的 RSS、Atom 或 RDF"),
    TOO_LARGE("订阅内容过大"),
    UNSAFE_DOCUMENT("订阅文档包含不安全内容"),
    UNSAFE_TRANSPORT("订阅地址不是安全的 HTTPS"),
}

internal data class FeedSourceStatus(
    val sourceId: String,
    val sourceName: String,
    val fresh: Boolean,
    val usedCache: Boolean,
    val failure: FeedFailureKind? = null,
    val httpStatus: Int? = null,
) {
    val failureMessage: String?
        get() = failure?.let { kind ->
            val detail = if (kind == FeedFailureKind.HTTP && httpStatus != null) {
                "（HTTP $httpStatus）"
            } else {
                ""
            }
            "$sourceName：${kind.userMessage}$detail"
        }
}

internal class FeedLoadException(
    val reason: FeedFailureKind,
    val httpStatus: Int? = null,
    cause: Throwable? = null,
) : IOException(reason.userMessage, cause)

