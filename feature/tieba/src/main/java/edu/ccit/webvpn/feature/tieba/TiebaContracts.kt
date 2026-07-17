package edu.ccit.webvpn.feature.tieba

import androidx.compose.runtime.Immutable
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.ZoneId

const val TARGET_FORUM_NAME = "长春工程学院"
const val TARGET_FORUM_DISPLAY_NAME = "长春工程学院吧"
const val TARGET_FORUM_ID = 64554L
const val TARGET_FORUM_URL = "https://tieba.baidu.com/f?kw=%E9%95%BF%E6%98%A5%E5%B7%A5%E7%A8%8B%E5%AD%A6%E9%99%A2"

enum class ForumSort { BY_REPLY, BY_SEND }
enum class FloorSort { ASCENDING, DESCENDING, HOT }
enum class SignOutcome { SUCCESS, ALREADY_SIGNED, FAILED }

@Immutable
data class TiebaReadingPreferences(
    val forumSort: ForumSort = ForumSort.BY_REPLY,
    val floorSort: FloorSort = FloorSort.ASCENDING,
    val onlyOriginalPoster: Boolean = false,
    val showBothNames: Boolean = false,
    val stickyFloorHeader: Boolean = false,
)

@Immutable
data class TiebaSignSettings(
    val enabled: Boolean = false,
    val lastRunAt: Long? = null,
    val lastOutcome: SignOutcome? = null,
    val lastMessage: String? = null,
)

sealed interface TiebaSignState {
    data object Idle : TiebaSignState
    data object Running : TiebaSignState
    data class Finished(val outcome: SignOutcome, val message: String) : TiebaSignState
}

@Immutable
data class TiebaPreferences(
    val reading: TiebaReadingPreferences = TiebaReadingPreferences(),
    val sign: TiebaSignSettings = TiebaSignSettings(),
)

@Immutable
data class TiebaAccount(
    val uid: Long,
    val username: String,
    val nickname: String,
    val avatarUrl: String,
    val intro: String,
    val fans: String,
    val posts: String,
    val concerned: String,
    val lastUpdatedAt: Long,
)

@Immutable
data class ForumSummary(
    val id: String = "",
    val name: String = TARGET_FORUM_NAME,
    val tbs: String = "",
    val slogan: String = "",
    val avatarUrl: String = "",
    val memberCount: String = "",
    val threadCount: String = "",
    val postCount: String = "",
    val isFollowed: Boolean = false,
    val forumRuleTitle: String = "",
    val signed: Boolean = false,
    val signedDays: Int = 0,
)

@Immutable
data class ForumRule(
    val title: String,
    val publishTime: String,
    val preface: String,
    val rules: List<ForumRuleItem>,
    val authorName: String,
    val authorPortrait: String,
)

@Immutable
data class ForumRuleItem(val title: String, val content: String)

@Immutable
data class ForumThread(
    val id: String,
    val title: String,
    val excerpt: String,
    val authorName: String,
    val authorNickname: String,
    val authorPortrait: String,
    val replyCount: String,
    val viewCount: String,
    val lastReplyTime: String,
    val isTop: Boolean,
    val isGood: Boolean,
    val imageUrls: List<String>,
    val videoUrl: String? = null,
    val authorId: Long = 0,
    val forumId: Long = TARGET_FORUM_ID,
    val forumName: String = TARGET_FORUM_NAME,
    val richExcerpt: List<TiebaContent> = emptyList(),
    val authorIsManager: Boolean = false,
)

@Immutable
data class ForumPage(
    val forum: ForumSummary,
    val threads: List<ForumThread>,
    val page: Int,
    val hasMore: Boolean,
)

@Immutable
sealed interface TiebaContent {
    @Immutable
    data class Text(val value: String) : TiebaContent

    @Immutable
    data class Link(val label: String, val url: String) : TiebaContent

    @Immutable
    data class Emoticon(val id: String, val name: String) : TiebaContent

    @Immutable
    data class Image(
        val previewUrl: String,
        val originalUrl: String,
        val picId: String = "",
        val width: Int? = null,
        val height: Int? = null,
    ) : TiebaContent

    @Immutable
    data class Video(
        val url: String,
        val thumbnailUrl: String = "",
        val width: Int? = null,
        val height: Int? = null,
    ) : TiebaContent
}

/**
 * The arguments TiebaLite sends to /c/f/pb/picpage before opening its photo viewer.
 * Keeping these values lets the viewer refresh an expired tbpicau URL instead of
 * accidentally displaying Tieba's 238 x 238 failure placeholder.
 */
@Immutable
data class LoadPicPageData(
    val forumId: Long,
    val forumName: String,
    val seeLz: Boolean,
    val objType: String,
    val picId: String,
    val picIndex: Int,
    val threadId: Long,
    val postId: Long,
    val originUrl: String?,
)

@Immutable
data class FloorReply(
    val id: String = "",
    val authorName: String,
    val authorNickname: String = "",
    val authorPortrait: String = "",
    val content: String,
    val time: String,
    val richContent: List<TiebaContent> = emptyList(),
    val authorId: Long = 0,
    val authorLevel: Int = 0,
    val authorTitle: String = "",
    val authorIp: String = "",
    val authorIsManager: Boolean = false,
)

@Immutable
data class FloorReplyPage(
    val replies: List<FloorReply>,
    val page: Int,
    val totalPages: Int,
)

@Immutable
data class TiebaReplyResult(
    val threadId: Long,
    val postId: Long,
    val experienceAdded: String = "",
)

@Immutable
data class TiebaUploadedImage(
    val picId: String,
    val width: Int,
    val height: Int,
)

@Immutable
data class ThreadFloor(
    val postId: String,
    val floor: Int,
    val authorName: String,
    val authorNickname: String,
    val authorPortrait: String,
    val content: String,
    val time: String,
    val imageUrls: List<String>,
    val videoUrls: List<String>,
    val replyCount: Int = 0,
    val replies: List<FloorReply>,
    val richContent: List<TiebaContent> = emptyList(),
    val authorId: Long = 0,
    val authorLevel: Int = 0,
    val authorTitle: String = "",
    val authorIp: String = "",
    val authorIsManager: Boolean = false,
)

@Immutable
data class ThreadPage(
    val threadId: String,
    val title: String,
    val floors: List<ThreadFloor>,
    val page: Int,
    val totalPages: Int,
    val replyCount: Int = 0,
    val body: ThreadFloor? = null,
    val forumId: Long = TARGET_FORUM_ID,
    val forumName: String = TARGET_FORUM_NAME,
)

@Immutable
data class TiebaUserForum(
    val id: Long,
    val name: String,
)

@Immutable
data class TiebaUserProfile(
    val uid: Long,
    val username: String,
    val nickname: String,
    val avatarUrl: String,
    val intro: String,
    val sex: String,
    val tiebaAge: String,
    val address: String,
    val threadCount: Int,
    val postCount: Int,
    val forumCount: Int,
    val followingCount: Int,
    val fansCount: Int,
    val agreeCount: Int,
    val isOfficial: Boolean,
    val followedForumsPrivate: Boolean,
    val followedForums: List<TiebaUserForum>,
)

@Immutable
data class TiebaUserPost(
    val key: String,
    val threadId: Long,
    val postId: Long,
    val forumId: Long,
    val forumName: String,
    val title: String,
    val excerpt: String,
    val time: String,
    val replyCount: Int,
    val imageUrls: List<String>,
    val isReply: Boolean,
)

@Immutable
data class TiebaUserPostPage(
    val posts: List<TiebaUserPost>,
    val page: Int,
    val hasMore: Boolean,
)

sealed interface ForumRouteDecision {
    data object Native : ForumRouteDecision
    data class External(val url: String) : ForumRouteDecision
}

fun forumRouteDecision(forumName: String): ForumRouteDecision {
    val canonical = forumName.trim().removeSuffix("吧")
    return if (canonical == TARGET_FORUM_NAME) {
        ForumRouteDecision.Native
    } else {
        val encoded = URLEncoder.encode(canonical, StandardCharsets.UTF_8.name())
        ForumRouteDecision.External("https://tieba.baidu.com/f?kw=$encoded")
    }
}

fun originalImageUrl(raw: String): String {
    val normalized = when {
        raw.startsWith("//") -> "https:$raw"
        raw.startsWith("http://") -> "https://${raw.removePrefix("http://")}"
        else -> raw
    }
    return normalized.substringBefore("?tbpicau=").substringBefore("?t=")
}

internal fun shouldAutoSign(
    lastRunAt: Long?,
    now: Instant = Instant.now(),
    zoneId: ZoneId = ZoneId.systemDefault(),
): Boolean {
    val today = now.atZone(zoneId).toLocalDate()
    val lastRunDate = lastRunAt?.let { Instant.ofEpochMilli(it).atZone(zoneId).toLocalDate() }
    return lastRunDate != today
}
