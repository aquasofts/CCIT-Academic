package edu.ccit.webvpn.feature.tieba.ui

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.MediaController
import android.widget.VideoView
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.InsertPhoto
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.round
import androidx.compose.ui.util.lerp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import coil3.compose.AsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import coil3.size.SizeResolver
import edu.ccit.webvpn.core.ui.CcitCard
import edu.ccit.webvpn.core.ui.CcitColors
import edu.ccit.webvpn.feature.tieba.FloorSort
import edu.ccit.webvpn.feature.tieba.ForumPage
import edu.ccit.webvpn.feature.tieba.ForumRule
import edu.ccit.webvpn.feature.tieba.ForumSort
import edu.ccit.webvpn.feature.tieba.ForumSummary
import edu.ccit.webvpn.feature.tieba.ForumThread
import edu.ccit.webvpn.feature.tieba.LoadPicPageData
import edu.ccit.webvpn.feature.tieba.SignOutcome
import edu.ccit.webvpn.feature.tieba.TARGET_FORUM_DISPLAY_NAME
import edu.ccit.webvpn.feature.tieba.TiebaAccount
import edu.ccit.webvpn.feature.tieba.TiebaContent
import edu.ccit.webvpn.feature.tieba.TiebaRuntime
import edu.ccit.webvpn.feature.tieba.TiebaUserPost
import edu.ccit.webvpn.feature.tieba.TiebaUserProfile
import edu.ccit.webvpn.feature.tieba.ThreadFloor
import edu.ccit.webvpn.feature.tieba.network.isAuthorizedTiebaImageUrl
import edu.ccit.webvpn.feature.tieba.network.parseLoginCookies
import java.text.DateFormat
import java.time.Instant
import java.time.ZoneId
import java.util.Date
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.filterIsInstance

private const val ForumRoute = "forum"
private const val SearchRoute = "search"
private const val ThreadRoute = "thread"
private const val FloorRepliesRoute = "floor_replies"
private const val ProfileRoute = "profile"
private const val ImageRoute = "image"
private const val ForumRuleRoute = "forum_rule"
private val TiebaWindowInsets = WindowInsets(0, 0, 0, 0)

/** Mirrors TiebaLite's photo-view handoff: the viewer receives the original URL, never the preview URL. */
private data class PhotoViewData(
    val data: LoadPicPageData? = null,
    val picItems: List<PicItem>,
    val index: Int = 0,
)

private data class PicItem(
    val picId: String,
    val picIndex: Int,
    val originUrl: String,
    val postId: Long? = null,
)

private class ForumScreenState {
    var page by mutableIntStateOf(1)
    var goodOnly by mutableStateOf(false)
    var threads by mutableStateOf(emptyList<ForumThread>())
    var forum by mutableStateOf(ForumSummary())
    var hasMore by mutableStateOf(false)
    var loading by mutableStateOf(false)
    var refreshing by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var queryKey by mutableStateOf<String?>(null)
    val listState = LazyListState()
}

private class ForumRuleScreenState {
    var loading by mutableStateOf(false)
    var rule by mutableStateOf<ForumRule?>(null)
    var error by mutableStateOf<String?>(null)
}

private data class ContentActionTarget(
    val text: String,
    val threadId: Long,
    val forumId: Long,
    val forumName: String,
    val postId: Long?,
    val subPostId: Long? = null,
    val replyUserId: Long? = null,
    val replyUserName: String = "",
    val replyUserPortrait: String = "",
)

private class SearchScreenState {
    var keyword by mutableStateOf("")
    var submittedKeyword by mutableStateOf("")
    var page by mutableIntStateOf(1)
    var result by mutableStateOf(ForumPage(ForumSummary(), emptyList(), 1, false))
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    val listState = LazyListState()
}

private class ThreadScreenState(initialTitle: String) {
    var page by mutableIntStateOf(0)
    var title by mutableStateOf(initialTitle)
    var totalPages by mutableIntStateOf(1)
    var replyCount by mutableIntStateOf(0)
    var body by mutableStateOf<ThreadFloor?>(null)
    var floors by mutableStateOf(emptyList<ThreadFloor>())
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var queryKey by mutableStateOf<String?>(null)
    val listState = LazyListState()
}

private class FloorRepliesScreenState {
    var page by mutableIntStateOf(0)
    var totalPages by mutableIntStateOf(1)
    var replies by mutableStateOf(emptyList<edu.ccit.webvpn.feature.tieba.FloorReply>())
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    val listState = LazyListState()
}

private class UserPostsScreenState {
    var page by mutableIntStateOf(0)
    var posts by mutableStateOf(emptyList<TiebaUserPost>())
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var hasMore by mutableStateOf(false)
    val listState = LazyListState()
}

private class UserProfileScreenState {
    var profile by mutableStateOf<TiebaUserProfile?>(null)
    var profileLoading by mutableStateOf(true)
    var profileError by mutableStateOf<String?>(null)
    var selectedTab by mutableStateOf(UserProfileTab.Threads)
    val tabStates = UserProfileTab.entries.associateWith { UserPostsScreenState() }
}

@Composable
fun TiebaRootScreen(active: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val runtime = remember(context) { TiebaRuntime.get(context) }
    val navigator = rememberNavController()
    var photoViewData by remember { mutableStateOf<PhotoViewData?>(null) }
    val forumState = remember { ForumScreenState() }
    val searchState = remember { SearchScreenState() }
    val threadStates = remember { mutableMapOf<String, ThreadScreenState>() }
    val floorRepliesStates = remember { mutableMapOf<String, FloorRepliesScreenState>() }
    val profileStates = remember { mutableMapOf<Long, UserProfileScreenState>() }
    val forumRuleState = remember { ForumRuleScreenState() }

    fun openThread(thread: ForumThread, focusPostId: Long = 0) {
        navigator.navigate(
            "$ThreadRoute/${thread.id}?title=${Uri.encode(thread.title)}" +
                "&forumId=${thread.forumId}&forumName=${Uri.encode(thread.forumName)}&postId=$focusPostId",
        )
    }

    fun openProfile(uid: Long) {
        if (uid > 0) navigator.navigate("$ProfileRoute/$uid")
    }

    fun openReply(target: ContentActionTarget) {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW).setData(
                    Uri.parse(officialTiebaReplyUri(target.threadId, target.postId)),
                ),
            )
        }.onFailure {
            Toast.makeText(context, "无法打开百度贴吧", Toast.LENGTH_SHORT).show()
        }
    }

    NavHost(navigator, startDestination = ForumRoute, modifier = modifier.fillMaxSize()) {
        composable(ForumRoute) {
            ForumScreen(
                active = active,
                runtime = runtime,
                state = forumState,
                onSearch = { navigator.navigate(SearchRoute) },
                onThread = ::openThread,
                onProfile = { openProfile(it.authorId) },
                onRule = { navigator.navigate(ForumRuleRoute) },
            )
        }
        composable(ForumRuleRoute) {
            ForumRuleScreen(runtime, forumRuleState, navigator::navigateUp)
        }
        composable(SearchRoute) {
            SearchScreen(runtime, searchState, navigator::navigateUp, ::openThread) { openProfile(it.authorId) }
        }
        composable(
            "$ThreadRoute/{id}?title={title}&forumId={forumId}&forumName={forumName}&postId={postId}",
            arguments = listOf(
                navArgument("id") { type = NavType.StringType },
                navArgument("title") { type = NavType.StringType; defaultValue = "" },
                navArgument("forumId") { type = NavType.LongType; defaultValue = edu.ccit.webvpn.feature.tieba.TARGET_FORUM_ID },
                navArgument("forumName") { type = NavType.StringType; defaultValue = edu.ccit.webvpn.feature.tieba.TARGET_FORUM_NAME },
                navArgument("postId") { type = NavType.LongType; defaultValue = 0L },
            ),
        ) { entry ->
            val forumId = entry.arguments?.getLong("forumId") ?: edu.ccit.webvpn.feature.tieba.TARGET_FORUM_ID
            val forumName = entry.arguments?.getString("forumName").orEmpty().ifBlank { edu.ccit.webvpn.feature.tieba.TARGET_FORUM_NAME }
            val threadId = entry.arguments?.getString("id").orEmpty()
            val focusPostId = entry.arguments?.getLong("postId")?.takeIf { it > 0 }?.toString()
            val screenState = threadStates.getOrPut("$threadId:${focusPostId.orEmpty()}") {
                ThreadScreenState(entry.arguments?.getString("title").orEmpty())
            }
            ThreadScreen(
                runtime = runtime,
                state = screenState,
                threadId = threadId,
                forumId = forumId,
                forumName = forumName,
                focusPostId = focusPostId,
                onBack = navigator::navigateUp,
                onImage = { data ->
                    photoViewData = data
                    navigator.navigate(ImageRoute)
                },
                onReplies = { postId ->
                    navigator.navigate(
                        "$FloorRepliesRoute/${entry.arguments?.getString("id").orEmpty()}/$postId" +
                            "?forumId=$forumId&forumName=${Uri.encode(forumName)}",
                    )
                },
                onProfile = ::openProfile,
                onReply = ::openReply,
            )
        }
        composable(
            "$FloorRepliesRoute/{threadId}/{postId}?forumId={forumId}&forumName={forumName}",
            arguments = listOf(
                navArgument("threadId") { type = NavType.StringType },
                navArgument("postId") { type = NavType.StringType },
                navArgument("forumId") { type = NavType.LongType; defaultValue = edu.ccit.webvpn.feature.tieba.TARGET_FORUM_ID },
                navArgument("forumName") { type = NavType.StringType; defaultValue = edu.ccit.webvpn.feature.tieba.TARGET_FORUM_NAME },
            ),
        ) { entry ->
            val threadId = entry.arguments?.getString("threadId").orEmpty()
            val postId = entry.arguments?.getString("postId").orEmpty()
            FloorRepliesScreen(
                runtime = runtime,
                state = floorRepliesStates.getOrPut("$threadId:$postId") { FloorRepliesScreenState() },
                threadId = threadId,
                postId = postId,
                forumId = entry.arguments?.getLong("forumId") ?: edu.ccit.webvpn.feature.tieba.TARGET_FORUM_ID,
                forumName = entry.arguments?.getString("forumName").orEmpty(),
                onBack = navigator::navigateUp,
                onImage = { data -> photoViewData = data; navigator.navigate(ImageRoute) },
                onProfile = ::openProfile,
                onReply = ::openReply,
            )
        }
        composable(
            "$ProfileRoute/{uid}",
            arguments = listOf(navArgument("uid") { type = NavType.LongType }),
        ) { entry ->
            val uid = entry.arguments?.getLong("uid") ?: 0
            UserProfileScreen(
                runtime = runtime,
                state = profileStates.getOrPut(uid) { UserProfileScreenState() },
                uid = uid,
                onBack = navigator::navigateUp,
                onThread = { post ->
                    openThread(
                        ForumThread(
                            id = post.threadId.toString(), title = post.title, excerpt = post.excerpt,
                            authorName = "", authorNickname = "", authorPortrait = "", replyCount = post.replyCount.toString(),
                            viewCount = "", lastReplyTime = post.time, isTop = false, isGood = false,
                            imageUrls = post.imageUrls, forumId = post.forumId, forumName = post.forumName,
                        ),
                        focusPostId = if (post.isReply) post.postId else 0,
                    )
                },
            )
        }
        composable(ImageRoute) {
            val data = photoViewData
            if (data == null || data.picItems.isEmpty()) {
                LaunchedEffect(Unit) { navigator.navigateUp() }
            } else {
                FullImageScreen(runtime, data, navigator::navigateUp)
            }
        }
    }
}

/** Exact TiebaLite official-client dispatch URI, intentionally launched without package checks. */
internal fun officialTiebaReplyUri(threadId: Long, postId: Long?): String = if (postId != null) {
    "com.baidu.tieba://unidispatch/pb?obj_locate=comment_lzl_cut_guide&obj_source=wise&obj_name=index&obj_param2=chrome&has_token=0&qd=scheme&refer=tieba.baidu.com&wise_sample_id=3000232_2&hightlight_anchor_pid=$postId&is_anchor_to_comment=1&comment_sort_type=0&fr=bpush&tid=$threadId"
} else {
    "com.baidu.tieba://unidispatch/pb?obj_locate=pb_reply&obj_source=wise&obj_name=index&obj_param2=chrome&has_token=0&qd=scheme&refer=tieba.baidu.com&wise_sample_id=3000232_2-99999_9&fr=bpush&tid=$threadId"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ForumScreen(
    active: Boolean,
    runtime: TiebaRuntime,
    state: ForumScreenState,
    onSearch: () -> Unit,
    onThread: (ForumThread) -> Unit,
    onProfile: (ForumThread) -> Unit,
    onRule: () -> Unit,
) {
    val preferences by runtime.settings.preferences.collectAsStateWithLifecycle(initialValue = edu.ccit.webvpn.feature.tieba.TiebaPreferences())
    val account by runtime.account.collectAsStateWithLifecycle()
    val signState by runtime.signState.collectAsStateWithLifecycle()
    val signedToday = state.forum.signed ||
        (preferences.sign.lastOutcome != SignOutcome.FAILED && isToday(preferences.sign.lastRunAt))
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var sortMenu by remember { mutableStateOf(false) }

    fun load(targetPage: Int, append: Boolean, pullRefresh: Boolean = false) {
        if (state.loading || state.refreshing) return
        scope.launch {
            if (pullRefresh) state.refreshing = true else state.loading = true
            state.error = null
            runCatching {
                runtime.network.loadForum(
                    targetPage,
                    preferences.reading.forumSort,
                    state.goodOnly,
                    runtime.accountDao.get(),
                )
            }.onSuccess { result ->
                state.threads = if (append) (state.threads + result.threads).distinctBy(ForumThread::id) else result.threads
                state.forum = result.forum
                state.page = targetPage
                state.hasMore = result.hasMore
            }.onFailure { state.error = it.message ?: "加载失败" }
            if (pullRefresh) state.refreshing = false else state.loading = false
        }
    }

    LaunchedEffect(active, state.goodOnly, preferences.reading.forumSort, state.refreshing) {
        if (!active) return@LaunchedEffect
        val queryKey = "${state.goodOnly}:${preferences.reading.forumSort}"
        if (state.queryKey != queryKey || state.threads.isEmpty()) {
            if (state.queryKey != queryKey) state.listState.scrollToItem(0)
            state.queryKey = queryKey
            load(1, false)
        }
    }

    val shouldLoadMore by remember {
        derivedStateOf {
            val layoutInfo = state.listState.layoutInfo
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf false
            state.hasMore && !state.loading && !state.refreshing && lastVisible >= layoutInfo.totalItemsCount - 4
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) load(state.page + 1, true)
    }

    Scaffold(
        contentWindowInsets = TiebaWindowInsets,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                windowInsets = TiebaWindowInsets,
                expandedHeight = 56.dp,
                title = { Text(TARGET_FORUM_DISPLAY_NAME, maxLines = 1) },
                actions = {
                    IconButton(onClick = onSearch) { Icon(Icons.Default.Search, "吧内搜索") }
                    TextButton(
                        onClick = {
                            if (account == null) {
                                scope.launch { snackbar.showSnackbar("请先在“我的”中登录贴吧账号") }
                            } else {
                                scope.launch {
                                    val result = runtime.signNow(state.forum.tbs)
                                    if (result.outcome != SignOutcome.FAILED) {
                                        state.forum = state.forum.copy(
                                            signed = true,
                                            signedDays = result.signedDays ?: state.forum.signedDays,
                                        )
                                    }
                                    snackbar.showSnackbar(result.message)
                                }
                            }
                        },
                        enabled = signState !is edu.ccit.webvpn.feature.tieba.TiebaSignState.Running && (account == null || !signedToday),
                    ) {
                        if (signState is edu.ccit.webvpn.feature.tieba.TiebaSignState.Running) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text(
                                if (signedToday) {
                                    state.forum.signedDays.takeIf { it > 0 }?.let { "已签${it}天" } ?: "已签"
                                } else {
                                    "签到"
                                },
                            )
                        }
                    }
                    Box {
                        IconButton(onClick = { sortMenu = true }) { Icon(Icons.Default.FilterList, "排序") }
                        DropdownMenu(sortMenu, onDismissRequest = { sortMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("按最后回复${if (preferences.reading.forumSort == ForumSort.BY_REPLY) " ✓" else ""}") },
                                onClick = { sortMenu = false; scope.launch { runtime.settings.setForumSort(ForumSort.BY_REPLY) } },
                            )
                            DropdownMenuItem(
                                text = { Text("按发布时间${if (preferences.reading.forumSort == ForumSort.BY_SEND) " ✓" else ""}") },
                                onClick = { sortMenu = false; scope.launch { runtime.settings.setForumSort(ForumSort.BY_SEND) } },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = { load(1, false, pullRefresh = true) },
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = state.listState,
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                item {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = !state.goodOnly,
                            onClick = { state.goodOnly = false },
                            enabled = !state.refreshing,
                            label = { Text("最新") },
                        )
                        FilterChip(
                            selected = state.goodOnly,
                            onClick = { state.goodOnly = true },
                            enabled = !state.refreshing,
                            label = { Text("精品") },
                        )
                    }
                }
                state.error?.let { message ->
                    item { ErrorCard(message) { load(1, false, pullRefresh = state.threads.isNotEmpty()) } }
                }
                val pinnedThreads = state.threads.filter(ForumThread::isTop)
                if (state.forum.forumRuleTitle.isNotBlank() || pinnedThreads.isNotEmpty()) {
                    item(key = "forum_notices", contentType = "forum_notices") {
                        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            state.forum.forumRuleTitle.takeIf(String::isNotBlank)?.let { rule ->
                                CompactForumNoticeRow(label = "吧规", title = rule, onClick = onRule)
                            }
                            pinnedThreads.forEach { thread ->
                                CompactForumNoticeRow(
                                    label = "置顶",
                                    title = thread.title,
                                    onClick = { onThread(thread) },
                                )
                            }
                        }
                        HorizontalDivider(
                            Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                }
                items(state.threads.filterNot(ForumThread::isTop), key = ForumThread::id) { thread ->
                    ThreadRow(thread, preferences.reading.showBothNames, onThread, onProfile)
                }
                if (state.loading) item { LoadingRow() }
                if (!state.loading && state.threads.isEmpty() && state.error == null && active) item { Text("暂无帖子") }
            }
        }
    }
}

@Composable
private fun CompactForumNoticeRow(
    label: String,
    title: String,
    onClick: (() -> Unit)? = null,
) {
    val clickModifier = if (onClick == null) Modifier else Modifier.clickable(onClick = onClick)
    Row(
        modifier = Modifier.fillMaxWidth()
            .then(clickModifier)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = MaterialTheme.shapes.extraSmall,
        ) {
            Text(
                text = label,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp),
                style = MaterialTheme.typography.labelMedium,
            )
        }
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ForumRuleScreen(
    runtime: TiebaRuntime,
    state: ForumRuleScreenState,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    fun load() {
        if (state.loading) return
        scope.launch {
            state.loading = true
            state.error = null
            runCatching { runtime.network.loadForumRule(runtime.accountDao.get()) }
                .onSuccess { state.rule = it }
                .onFailure { state.error = it.message ?: "吧规加载失败" }
            state.loading = false
        }
    }
    LaunchedEffect(Unit) { if (state.rule == null) load() }
    Scaffold(
        contentWindowInsets = TiebaWindowInsets,
        topBar = {
            TopAppBar(
                title = { Text("吧规") },
                navigationIcon = { BackButton(onBack) },
                windowInsets = TiebaWindowInsets,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            state.error?.let { item { ErrorCard(it, ::load) } }
            state.rule?.let { rule ->
                item {
                    Text(rule.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    if (rule.authorName.isNotBlank() || rule.publishTime.isNotBlank()) {
                        Text(
                            listOf(rule.authorName, rule.publishTime).filter(String::isNotBlank).joinToString(" · "),
                            color = CcitColors.InkMuted,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
                if (rule.preface.isNotBlank()) item { Text(rule.preface) }
                items(rule.rules, key = { "${it.title}:${it.content.hashCode()}" }) { item ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(item.content, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            if (state.loading) item { LoadingRow() }
        }
    }
}

@Composable
private fun ThreadRow(
    thread: ForumThread,
    showBothNames: Boolean,
    onClick: (ForumThread) -> Unit,
    onProfile: (ForumThread) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().clickable { onClick(thread) }.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TiebaAsyncImage(
                    url = thread.authorPortrait,
                    contentDescription = null,
                    modifier = Modifier.size(34.dp).clip(CircleShape),
                    contentScale = ContentScale.Crop,
                    onClick = { if (thread.authorId > 0) onProfile(thread) },
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        displayName(thread.authorName, thread.authorNickname, showBothNames).ifBlank { "贴吧用户" },
                        style = MaterialTheme.typography.labelLarge,
                    )
                    if (thread.authorIsManager) AuthorIdentityBadges("", 0, isManager = true)
                    if (thread.lastReplyTime.isNotBlank()) {
                        Text(thread.lastReplyTime, style = MaterialTheme.typography.labelSmall, color = CcitColors.InkMuted)
                    }
                }
                Text("${thread.replyCount} 回复", style = MaterialTheme.typography.labelMedium, color = CcitColors.InkMuted)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (thread.isTop) Text("置顶 ", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                if (thread.isGood) Text("精品 ", color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold)
                Text(thread.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (thread.richExcerpt.isNotEmpty()) {
                RichTiebaText(
                    content = thread.richExcerpt,
                    maxLines = 4,
                    style = MaterialTheme.typography.bodyMedium.copy(color = CcitColors.InkMuted),
                )
            } else if (thread.excerpt.isNotBlank()) {
                Text(thread.excerpt, maxLines = 4, overflow = TextOverflow.Ellipsis, color = CcitColors.InkMuted)
            }
            val images = thread.imageUrls.take(3)
            if (images.size == 1) {
                TiebaAsyncImage(
                    url = images.single(),
                    contentDescription = "帖子图片",
                    modifier = Modifier.fillMaxWidth().aspectRatio(2f).clip(MaterialTheme.shapes.small),
                    contentScale = ContentScale.Crop,
                )
            } else if (images.isNotEmpty()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    images.forEach { url ->
                        TiebaAsyncImage(
                            url = url,
                            contentDescription = "帖子图片",
                            modifier = Modifier.weight(1f).height(96.dp).clip(MaterialTheme.shapes.small),
                            contentScale = ContentScale.Crop,
                        )
                    }
                }
            }
            if (thread.viewCount.isNotBlank()) {
                Text("${thread.viewCount} 次浏览", style = MaterialTheme.typography.labelSmall, color = CcitColors.InkMuted)
            }
        }
        HorizontalDivider(Modifier.padding(start = 60.dp), color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchScreen(
    runtime: TiebaRuntime,
    state: SearchScreenState,
    onBack: () -> Unit,
    onThread: (ForumThread) -> Unit,
    onProfile: (ForumThread) -> Unit,
) {
    val scope = rememberCoroutineScope()
    fun search(targetPage: Int, append: Boolean) {
        val query = if (append) state.submittedKeyword else state.keyword.trim()
        if (query.isBlank() || state.loading) return
        if (!append) state.submittedKeyword = query
        scope.launch {
            state.loading = true
            state.error = null
            runCatching { runtime.network.search(query, targetPage) }
                .onSuccess { loaded ->
                    state.result = if (append) loaded.copy(threads = (state.result.threads + loaded.threads).distinctBy(ForumThread::id)) else loaded
                    state.page = targetPage
                    if (!append) state.listState.scrollToItem(0)
                }.onFailure { state.error = it.message ?: "搜索失败" }
            state.loading = false
        }
    }
    val shouldLoadMore by remember {
        derivedStateOf {
            val layoutInfo = state.listState.layoutInfo
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf false
            state.result.hasMore && !state.loading && lastVisible >= layoutInfo.totalItemsCount - 4
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) search(state.page + 1, true)
    }
    Scaffold(
        contentWindowInsets = TiebaWindowInsets,
        topBar = { TopAppBar(title = { Text("吧内搜索") }, navigationIcon = { BackButton(onBack) }, windowInsets = TiebaWindowInsets) },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            state = state.listState,
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(state.keyword, { state.keyword = it }, label = { Text("搜索 $TARGET_FORUM_DISPLAY_NAME") }, singleLine = true, modifier = Modifier.weight(1f))
                    IconButton(onClick = { search(1, false) }) { Icon(Icons.Default.Search, "搜索") }
                }
            }
            state.error?.let { item { ErrorCard(it) { search(1, false) } } }
            items(state.result.threads, key = ForumThread::id) { ThreadRow(it, false, onThread, onProfile) }
            if (state.loading) item { LoadingRow() }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThreadScreen(
    runtime: TiebaRuntime,
    state: ThreadScreenState,
    threadId: String,
    forumId: Long,
    forumName: String,
    focusPostId: String?,
    onBack: () -> Unit,
    onImage: (PhotoViewData) -> Unit,
    onReplies: (String) -> Unit,
    onProfile: (Long) -> Unit,
    onReply: (ContentActionTarget) -> Unit,
) {
    val preferences by runtime.settings.preferences.collectAsStateWithLifecycle(initialValue = edu.ccit.webvpn.feature.tieba.TiebaPreferences())
    val scope = rememberCoroutineScope()
    var refreshKey by remember { mutableIntStateOf(0) }

    fun loadPage(targetPage: Int, append: Boolean) {
        if (state.loading) return
        scope.launch {
            state.loading = true
            state.error = null
            runCatching {
                runtime.network.loadThread(
                    threadId,
                    targetPage,
                    preferences.reading.floorSort,
                    preferences.reading.onlyOriginalPoster,
                    runtime.accountDao.get(),
                    forumId = forumId,
                    forumName = forumName,
                    focusPostId = focusPostId,
                )
            }.onSuccess { loaded ->
                state.title = loaded.title
                if (!append || state.body == null) state.body = loaded.body
                state.floors = if (append) {
                    (state.floors + loaded.floors).distinctBy(ThreadFloor::postId)
                } else {
                    loaded.floors
                }
                state.totalPages = loaded.totalPages
                state.replyCount = loaded.replyCount
                state.page = targetPage
            }.onFailure { state.error = it.message ?: "帖子加载失败" }
            state.loading = false
        }
    }

    val queryKey = "$threadId:$forumId:$forumName:$focusPostId:${preferences.reading.floorSort}:${preferences.reading.onlyOriginalPoster}"
    LaunchedEffect(queryKey, refreshKey) {
        if (state.queryKey != queryKey) {
            state.queryKey = queryKey
            state.page = 0
            state.totalPages = 1
            state.body = null
            state.floors = emptyList()
            state.listState.scrollToItem(0)
        }
        if (state.page == 0 || refreshKey > 0) loadPage(1, false)
    }

    val shouldLoadMore by remember {
        derivedStateOf {
            val layoutInfo = state.listState.layoutInfo
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf false
            !state.loading && state.error == null && state.page in 1 until state.totalPages &&
                lastVisible >= layoutInfo.totalItemsCount - 4
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) loadPage(state.page + 1, true)
    }

    Scaffold(contentWindowInsets = TiebaWindowInsets, containerColor = MaterialTheme.colorScheme.background, topBar = {
        TopAppBar(
            windowInsets = TiebaWindowInsets,
            title = { ThreadTopBarTitle() },
            navigationIcon = { BackButton(onBack) },
            actions = {
                if (state.listState.canScrollBackward) {
                    IconButton({ scope.launch { state.listState.animateScrollToItem(0) } }) {
                        Icon(Icons.Default.VerticalAlignTop, "回到顶部")
                    }
                }
            },
        )
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.background)) {
            LazyColumn(
                Modifier.fillMaxSize(),
                state = state.listState,
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                    state.error?.let { item { ErrorCard(it) { refreshKey++ } } }
                    state.body?.let { firstPost ->
                        item(key = "thread_body", contentType = "thread_body") {
                            Column {
                                FloorBody(
                                    floor = firstPost,
                                    threadId = threadId,
                                    forumId = forumId,
                                    showBothNames = preferences.reading.showBothNames,
                                    title = state.title,
                                    forumName = forumName,
                                    seeLz = preferences.reading.onlyOriginalPoster,
                                    isOriginalPost = true,
                                    onImage = onImage,
                                    onReplies = { onReplies(firstPost.postId) },
                                    onProfile = onProfile,
                                    onReply = onReply,
                                )
                                HorizontalDivider(
                                    Modifier.padding(horizontal = 16.dp),
                                    thickness = 2.dp,
                                )
                                ThreadReplyHeader(
                                    replyCount = state.replyCount,
                                    sort = preferences.reading.floorSort,
                                    onlyOriginalPoster = preferences.reading.onlyOriginalPoster,
                                    onToggleOriginalPoster = {
                                        scope.launch {
                                            runtime.settings.setOnlyOriginalPoster(!preferences.reading.onlyOriginalPoster)
                                        }
                                    },
                                    onSort = { sort -> scope.launch { runtime.settings.setFloorSort(sort) } },
                                )
                            }
                        }
                    }
                    items(state.floors, key = ThreadFloor::postId, contentType = { "floor" }) { floor ->
                        FloorBody(
                            floor = floor,
                            threadId = threadId,
                            forumId = forumId,
                            showBothNames = preferences.reading.showBothNames,
                            forumName = forumName,
                            seeLz = preferences.reading.onlyOriginalPoster,
                            onImage = onImage,
                            onReplies = { onReplies(floor.postId) },
                            onProfile = onProfile,
                            onReply = onReply,
                        )
                    }
                    if (state.loading) item(key = "thread_loading") { LoadingRow() }
                    if (!state.loading && state.page > 0 && state.floors.isEmpty() && state.error == null) {
                        item { Text("暂无回复", Modifier.padding(16.dp)) }
                    }
                }
        }
    }
}

@Composable
private fun ThreadTopBarTitle() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            shape = MaterialTheme.shapes.small,
        ) {
            Icon(
                imageVector = Icons.Default.School,
                contentDescription = null,
                modifier = Modifier.padding(7.dp).size(19.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                text = "长春工程学院",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            Text(
                text = "校园贴吧",
                style = MaterialTheme.typography.labelSmall,
                color = CcitColors.InkMuted,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ThreadReplyHeader(
    replyCount: Int,
    sort: FloorSort,
    onlyOriginalPoster: Boolean,
    onToggleOriginalPoster: () -> Unit,
    onSort: (FloorSort) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("回复 $replyCount", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        VerticalDivider(Modifier.height(24.dp).padding(horizontal = 10.dp))
        Text(
            "只看楼主",
            Modifier.clickable(onClick = onToggleOriginalPoster),
            style = MaterialTheme.typography.labelLarge,
            color = if (onlyOriginalPoster) MaterialTheme.colorScheme.primary else CcitColors.InkMuted,
        )
        Spacer(Modifier.weight(1f))
        Text(
            "正序",
            Modifier.clickable { onSort(FloorSort.ASCENDING) },
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (sort == FloorSort.ASCENDING) FontWeight.Bold else FontWeight.Normal,
            color = if (sort == FloorSort.ASCENDING) MaterialTheme.colorScheme.onSurface else CcitColors.InkMuted,
        )
        VerticalDivider(Modifier.height(24.dp).padding(horizontal = 10.dp))
        Text(
            "倒序",
            Modifier.clickable { onSort(FloorSort.DESCENDING) },
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (sort == FloorSort.DESCENDING) FontWeight.Bold else FontWeight.Normal,
            color = if (sort == FloorSort.DESCENDING) MaterialTheme.colorScheme.onSurface else CcitColors.InkMuted,
        )
    }
}

@Composable
private fun FloorHeader(
    floor: ThreadFloor,
    showBothNames: Boolean,
    isOriginalPost: Boolean,
    onProfile: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TiebaAsyncImage(
            url = floor.authorPortrait,
            contentDescription = null,
            modifier = Modifier.size(36.dp).clip(CircleShape),
            contentScale = ContentScale.Crop,
            onClick = onProfile,
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                displayName(floor.authorName, floor.authorNickname, showBothNames).ifBlank { "贴吧用户" },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
            )
            AuthorIdentityBadges(floor.authorTitle, floor.authorLevel, floor.authorIsManager)
            Text(
                listOfNotNull(
                    floor.time.takeIf(String::isNotBlank),
                    "第 ${floor.floor} 楼".takeUnless { isOriginalPost },
                    floor.authorIp.takeIf(String::isNotBlank)?.let { "IP属地 $it" },
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = CcitColors.InkMuted,
            )
        }
        if (isOriginalPost) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = MaterialTheme.shapes.extraSmall,
            ) {
                Text("楼主", Modifier.padding(horizontal = 7.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun AuthorIdentityBadges(title: String, level: Int, isManager: Boolean = false) {
    if (title.isBlank() && level <= 0 && !isManager) return
    Row(
        modifier = Modifier.padding(top = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isManager) {
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                shape = MaterialTheme.shapes.extraSmall,
            ) {
                Text("吧主", Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall)
            }
        }
        title.takeIf(String::isNotBlank)?.let {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                shape = MaterialTheme.shapes.extraSmall,
            ) {
                Text(it, Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall)
            }
        }
        if (level > 0) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = MaterialTheme.shapes.extraSmall,
            ) {
                Text("Lv.$level", Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FloorBody(
    floor: ThreadFloor,
    threadId: String,
    forumId: Long,
    showBothNames: Boolean,
    title: String? = null,
    forumName: String = "",
    seeLz: Boolean = false,
    isOriginalPost: Boolean = false,
    onImage: (PhotoViewData) -> Unit,
    onReplies: () -> Unit,
    onProfile: (Long) -> Unit,
    onReply: (ContentActionTarget) -> Unit,
) {
    val floorTarget = ContentActionTarget(
        text = listOfNotNull(title, floor.content).filter(String::isNotBlank).joinToString("\n"),
        threadId = threadId.toLongOrNull() ?: 0,
        forumId = forumId,
        forumName = forumName,
        postId = floor.postId.toLongOrNull().takeUnless { isOriginalPost },
        replyUserId = floor.authorId.takeIf { !isOriginalPost && it > 0 },
        replyUserName = floor.authorNickname.ifBlank { floor.authorName },
        replyUserPortrait = floor.authorPortrait,
    )
    LongPressActionMenu(target = floorTarget, onReply = onReply) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp)) {
        FloorHeader(floor, showBothNames, isOriginalPost) {
            if (floor.authorId > 0) onProfile(floor.authorId)
        }
        val contentIndent = if (isOriginalPost) 0.dp else 46.dp
        Column(
            Modifier.fillMaxWidth().padding(start = contentIndent, top = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            title?.takeIf(String::isNotBlank)?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
            TiebaContentBody(
                content = floor.richContent,
                fallbackText = floor.content,
                threadId = threadId.toLongOrNull() ?: 0,
                postId = floor.postId.toLongOrNull() ?: 0,
                forumId = forumId,
                forumName = forumName,
                seeLz = seeLz,
                onImage = onImage,
            )
            floor.videoUrls
                .filterNot { url -> floor.richContent.filterIsInstance<TiebaContent.Video>().any { it.url == url } }
                .forEach { VideoPlayer(it) }
            if (floor.replyCount > 0 || floor.replies.isNotEmpty()) {
                Surface(
                    onClick = onReplies,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = MaterialTheme.shapes.small,
                ) {
                    Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        floor.replies.forEach { reply ->
                            val author = reply.authorNickname.ifBlank { reply.authorName }.ifBlank { "贴吧用户" }
                            LongPressActionMenu(
                                target = ContentActionTarget(
                                    text = reply.content,
                                    threadId = threadId.toLongOrNull() ?: 0,
                                    forumId = forumId,
                                    forumName = forumName,
                                    postId = floor.postId.toLongOrNull(),
                                    subPostId = reply.id.toLongOrNull(),
                                    replyUserId = reply.authorId.takeIf { it > 0 },
                                    replyUserName = author,
                                    replyUserPortrait = reply.authorPortrait,
                                ),
                                onReply = onReply,
                                onClick = onReplies,
                            ) {
                                RichTiebaText(
                                    content = listOf(TiebaContent.Text("$author：")) + reply.richContent.ifEmpty {
                                        listOf(TiebaContent.Text(reply.content))
                                    },
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
                                    maxLines = 4,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                        if (floor.replyCount > floor.replies.size || floor.replies.isEmpty()) {
                            Text(
                                if (floor.replyCount > 0) "查看全部 ${floor.replyCount} 条回复" else "查看楼中楼",
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                }
            }
        }
    }
    }
    if (!isOriginalPost) {
        HorizontalDivider(Modifier.padding(start = 62.dp), color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FloorRepliesScreen(
    runtime: TiebaRuntime,
    state: FloorRepliesScreenState,
    threadId: String,
    postId: String,
    forumId: Long,
    forumName: String,
    onBack: () -> Unit,
    onImage: (PhotoViewData) -> Unit,
    onProfile: (Long) -> Unit,
    onReply: (ContentActionTarget) -> Unit,
) {
    var refreshKey by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    fun loadPage(targetPage: Int, append: Boolean) {
        if (state.loading) return
        scope.launch {
            state.loading = true
            state.error = null
            runCatching {
                runtime.network.loadFloorReplies(
                    threadId,
                    postId,
                    targetPage,
                    account = runtime.accountDao.get(),
                    forumId = forumId,
                    forumName = forumName,
                )
            }
                .onSuccess { loaded ->
                    state.replies = if (append) {
                        (state.replies + loaded.replies).distinctBy { it.id.ifBlank { "${it.authorId}:${it.time}:${it.content}" } }
                    } else {
                        loaded.replies
                    }
                    state.page = targetPage
                    state.totalPages = loaded.totalPages
                }
                .onFailure { state.error = it.message ?: "帖子数据异常" }
            state.loading = false
        }
    }

    LaunchedEffect(threadId, postId, refreshKey) {
        if (refreshKey > 0) state.listState.scrollToItem(0)
        if (state.page == 0 || refreshKey > 0) loadPage(1, false)
    }
    val shouldLoadMore by remember {
        derivedStateOf {
            val layoutInfo = state.listState.layoutInfo
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf false
            !state.loading && state.error == null && state.page in 1 until state.totalPages &&
                lastVisible >= layoutInfo.totalItemsCount - 4
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) loadPage(state.page + 1, true)
    }

    Scaffold(
        contentWindowInsets = TiebaWindowInsets,
        topBar = {
            TopAppBar(
                title = { Text("楼中楼") },
                navigationIcon = { BackButton(onBack) },
                actions = { IconButton({ refreshKey++ }) { Icon(Icons.Default.Refresh, "刷新") } },
                windowInsets = TiebaWindowInsets,
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            state = state.listState,
            contentPadding = PaddingValues(vertical = 4.dp),
        ) {
            state.error?.let { item { ErrorCard(it) { refreshKey++ } } }
            items(
                state.replies,
                key = { it.id.ifBlank { "${it.authorName}:${it.time}:${it.content.hashCode()}" } },
                contentType = { "floor_reply" },
            ) { reply ->
                FloorReplyItem(
                    reply = reply,
                    threadId = threadId.toLongOrNull() ?: 0,
                    forumId = forumId,
                    forumName = forumName,
                    onImage = onImage,
                    onProfile = onProfile,
                    parentPostId = postId.toLongOrNull(),
                    onReply = onReply,
                )
            }
            if (state.loading) item { LoadingRow() }
            if (!state.loading && state.replies.isEmpty() && state.error == null) item { Text("暂无回复") }
        }
    }
}

@Composable
private fun FloorReplyItem(
    reply: edu.ccit.webvpn.feature.tieba.FloorReply,
    threadId: Long,
    forumId: Long,
    forumName: String,
    onImage: (PhotoViewData) -> Unit,
    onProfile: (Long) -> Unit,
    parentPostId: Long?,
    onReply: (ContentActionTarget) -> Unit,
) {
    val author = reply.authorNickname.ifBlank { reply.authorName }.ifBlank { "贴吧用户" }
    LongPressActionMenu(
        target = ContentActionTarget(
            text = reply.content,
            threadId = threadId,
            forumId = forumId,
            forumName = forumName,
            postId = parentPostId,
            subPostId = reply.id.toLongOrNull(),
            replyUserId = reply.authorId.takeIf { it > 0 },
            replyUserName = author,
            replyUserPortrait = reply.authorPortrait,
        ),
        onReply = onReply,
    ) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TiebaAsyncImage(
                url = reply.authorPortrait,
                contentDescription = null,
                modifier = Modifier.size(36.dp).clip(CircleShape),
                contentScale = ContentScale.Crop,
                onClick = { if (reply.authorId > 0) onProfile(reply.authorId) },
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    author,
                    style = MaterialTheme.typography.labelLarge,
                )
                AuthorIdentityBadges(reply.authorTitle, reply.authorLevel, reply.authorIsManager)
                if (reply.time.isNotBlank()) {
                    Text(
                        listOfNotNull(
                            reply.time,
                            reply.authorIp.takeIf(String::isNotBlank)?.let { "IP属地 $it" },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = CcitColors.InkMuted,
                    )
                }
            }
        }
        TiebaContentBody(
            content = reply.richContent,
            fallbackText = reply.content,
            threadId = threadId,
            postId = reply.id.toLongOrNull() ?: 0,
            forumId = forumId,
            forumName = forumName,
            seeLz = false,
            onImage = onImage,
            modifier = Modifier.fillMaxWidth().padding(start = 46.dp, top = 8.dp),
        )
    }
    }
    HorizontalDivider(Modifier.padding(start = 62.dp), color = MaterialTheme.colorScheme.outlineVariant)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TiebaContentBody(
    content: List<TiebaContent>,
    fallbackText: String,
    threadId: Long,
    postId: Long,
    forumId: Long,
    forumName: String,
    seeLz: Boolean,
    onImage: (PhotoViewData) -> Unit,
    onLongPress: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val effectiveContent = content.ifEmpty {
        fallbackText.takeIf(String::isNotBlank)?.let { listOf(TiebaContent.Text(it)) }.orEmpty()
    }
    val blocks = remember(effectiveContent) {
        buildList<List<TiebaContent>> {
            var text = mutableListOf<TiebaContent>()
            fun flushText() {
                if (text.isNotEmpty()) {
                    add(text)
                    text = mutableListOf()
                }
            }
            effectiveContent.forEach { item ->
                when (item) {
                    is TiebaContent.Image, is TiebaContent.Video -> {
                        flushText()
                        add(listOf(item))
                    }
                    else -> text += item
                }
            }
            flushText()
        }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEach { block ->
            when (val media = block.singleOrNull()) {
                is TiebaContent.Image -> {
                    val ratio = if (media.width != null && media.height != null && media.height > 0) {
                        (media.width.toFloat() / media.height).coerceIn(0.65f, 2.2f)
                    } else {
                        4f / 3f
                    }
                    TiebaAsyncImage(
                        url = media.previewUrl,
                        contentDescription = "帖子图片",
                        modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp, max = 520.dp)
                            .aspectRatio(ratio).clip(MaterialTheme.shapes.small),
                        contentScale = ContentScale.Fit,
                        onClick = {
                            onImage(
                                PhotoViewData(
                                    data = LoadPicPageData(
                                        forumId = forumId,
                                        forumName = forumName,
                                        seeLz = seeLz,
                                        objType = "pb",
                                        picId = media.picId.ifBlank {
                                            media.originalUrl.substringBefore('?').substringAfterLast('/').substringBeforeLast('.')
                                        },
                                        picIndex = 1,
                                        threadId = threadId,
                                        postId = postId,
                                        originUrl = media.originalUrl,
                                    ),
                                    picItems = listOf(
                                        PicItem(
                                            picId = media.picId.ifBlank {
                                                media.originalUrl.substringBefore('?').substringAfterLast('/').substringBeforeLast('.')
                                            },
                                            picIndex = 1,
                                            originUrl = media.originalUrl,
                                            postId = postId,
                                        ),
                                    ),
                                ),
                            )
                        },
                    )
                }
                is TiebaContent.Video -> VideoPlayer(media.url)
                else -> {
                    val textModifier = if (onLongPress == null) {
                        Modifier.fillMaxWidth()
                    } else {
                        Modifier.fillMaxWidth().combinedClickable(onClick = {}, onLongClick = onLongPress)
                    }
                    RichTiebaText(block, textModifier)
                }
            }
        }
    }
}

@Composable
private fun RichTiebaText(
    content: List<TiebaContent>,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyLarge,
) {
    val linkColor = MaterialTheme.colorScheme.primary
    val annotated = remember(content, linkColor) {
        buildAnnotatedString {
            content.forEachIndexed { index, item ->
                when (item) {
                    is TiebaContent.Text -> append(item.value)
                    is TiebaContent.Link -> withStyle(SpanStyle(color = linkColor)) {
                        append(item.label.ifBlank { item.url })
                    }
                    is TiebaContent.Emoticon -> appendInlineContent(
                        id = "tieba-emoticon-$index-${item.id}",
                        alternateText = "#(${item.name})",
                    )
                    is TiebaContent.Image -> append("[图片]")
                    is TiebaContent.Video -> append("[视频]")
                }
            }
        }
    }
    val inlineContent = content.mapIndexedNotNull { index, item ->
        (item as? TiebaContent.Emoticon)?.let { emoticon ->
            "tieba-emoticon-$index-${emoticon.id}" to InlineTextContent(
                placeholder = Placeholder(21.sp, 21.sp, PlaceholderVerticalAlign.TextCenter),
            ) {
                TiebaAsyncImage(
                    url = emoticonModel(emoticon.id),
                    contentDescription = emoticon.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            }
        }
    }.toMap()
    Text(
        text = annotated,
        modifier = modifier,
        inlineContent = inlineContent,
        maxLines = maxLines,
        overflow = if (maxLines == Int.MAX_VALUE) TextOverflow.Clip else TextOverflow.Ellipsis,
        style = style,
    )
}

@Composable
private fun TiebaAsyncImage(
    url: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    onClick: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    var retry by remember(url) { mutableIntStateOf(0) }
    var failed by remember(url, retry) { mutableStateOf(false) }
    val request = remember(url, retry) {
        ImageRequest.Builder(context)
            .data(url)
            .httpHeaders(
                NetworkHeaders.Builder()
                    .set("Referer", "https://tieba.baidu.com/")
                    .build(),
            )
            .build()
    }
    val interactiveModifier = if (onClick == null) modifier else modifier.clickable(onClick = onClick)
    Box(
        interactiveModifier.background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        if (url.isNotBlank()) {
            AsyncImage(
                model = request,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
                onSuccess = { failed = false },
                onError = { failed = true },
            )
        }
        if (url.isBlank()) {
            Icon(Icons.Default.AccountCircle, contentDescription, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        } else if (failed) {
            IconButton(onClick = { retry++ }) {
                Icon(Icons.Default.Refresh, "重新加载图片", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private val bundledEmoticons = ((1..50) + (77..84) + 89).mapTo(hashSetOf()) { "image_emoticon$it" }

private fun emoticonModel(id: String): String = if (id in bundledEmoticons) {
    "file:///android_asset/emoticon/$id.webp"
} else {
    "https://static.tieba.baidu.com/tb/editor/images/client/$id.png"
}

@Composable
private fun VideoPlayer(url: String) {
    AndroidView(
        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
        factory = { context ->
            VideoView(context).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                setMediaController(MediaController(context).also { it.setAnchorView(this) })
                setVideoURI(Uri.parse(url))
            }
        },
    )
}

private enum class UserProfileTab(val label: String) { Threads("主题"), Replies("回复"), Forums("关注的吧") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UserProfileScreen(
    runtime: TiebaRuntime,
    state: UserProfileScreenState,
    uid: Long,
    onBack: () -> Unit,
    onThread: (TiebaUserPost) -> Unit,
) {
    val account by runtime.account.collectAsStateWithLifecycle()
    var refreshKey by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val tabState = state.tabStates.getValue(state.selectedTab)

    LaunchedEffect(account?.uid, uid, state.selectedTab) {
        if (state.selectedTab == UserProfileTab.Replies && account?.uid != uid) {
            state.selectedTab = UserProfileTab.Threads
        }
    }

    LaunchedEffect(uid, refreshKey) {
        if (state.profile != null && refreshKey == 0) return@LaunchedEffect
        state.profileLoading = true
        state.profileError = null
        runCatching { runtime.network.loadUserProfile(uid, runtime.accountDao.get()) }
            .onSuccess { state.profile = it }
            .onFailure { state.profileError = it.message ?: "用户资料加载失败" }
        state.profileLoading = false
    }

    fun loadPosts(targetPage: Int, append: Boolean) {
        val targetTab = state.selectedTab
        val targetState = state.tabStates.getValue(targetTab)
        if (targetState.loading || targetTab == UserProfileTab.Forums) return
        scope.launch {
            targetState.loading = true
            targetState.error = null
            runCatching {
                runtime.network.loadUserPosts(
                    uid = uid,
                    page = targetPage,
                    isThread = targetTab == UserProfileTab.Threads,
                    account = runtime.accountDao.get(),
                )
            }.onSuccess { result ->
                targetState.posts = if (append) (targetState.posts + result.posts).distinctBy(TiebaUserPost::key) else result.posts
                targetState.page = targetPage
                targetState.hasMore = result.hasMore
            }.onFailure { targetState.error = it.message ?: "用户帖子加载失败" }
            targetState.loading = false
        }
    }

    LaunchedEffect(uid, state.selectedTab, state.profile?.uid) {
        if (state.profile != null && state.selectedTab != UserProfileTab.Forums && tabState.page == 0) {
            loadPosts(1, false)
        }
    }
    val shouldLoadMore by remember(state.selectedTab) {
        derivedStateOf {
            val layoutInfo = tabState.listState.layoutInfo
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf false
            tabState.hasMore && !tabState.loading && lastVisible >= layoutInfo.totalItemsCount - 4
        }
    }
    LaunchedEffect(shouldLoadMore, state.selectedTab) {
        if (shouldLoadMore) loadPosts(tabState.page + 1, true)
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = TiebaWindowInsets,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            val user = state.profile
            if (user == null) {
                TopAppBar(
                    title = { Text("用户资料") },
                    navigationIcon = { BackButton(onBack) },
                    windowInsets = TiebaWindowInsets,
                )
            } else {
                UserProfileTopAppBar(
                    user = user,
                    onBack = onBack,
                    onRefresh = { refreshKey++ },
                    scrollBehavior = scrollBehavior,
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.background)) {
            when {
                state.profileLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.profileError != null -> ErrorCard(state.profileError.orEmpty()) { refreshKey++ }
                state.profile != null -> {
                    val user = requireNotNull(state.profile)
                    val tabs = buildList {
                        add(UserProfileTab.Threads)
                        if (account?.uid == uid) add(UserProfileTab.Replies)
                        add(UserProfileTab.Forums)
                    }
                    Column(Modifier.fillMaxSize()) {
                        PrimaryTabRow(
                            selectedTabIndex = tabs.indexOf(state.selectedTab).coerceAtLeast(0),
                            containerColor = MaterialTheme.colorScheme.background,
                        ) {
                            tabs.forEach { tab ->
                                val count = when (tab) {
                                    UserProfileTab.Threads -> user.threadCount
                                    UserProfileTab.Replies -> user.postCount
                                    UserProfileTab.Forums -> user.forumCount
                                }
                                Tab(
                                    selected = state.selectedTab == tab,
                                    onClick = { state.selectedTab = tab },
                                    text = { Text("${tab.label} $count") },
                                )
                            }
                        }
                        if (state.selectedTab == UserProfileTab.Forums) {
                            UserForumList(user, Modifier.weight(1f), tabState.listState)
                        } else {
                            LazyColumn(
                                Modifier.weight(1f).fillMaxWidth(),
                                state = tabState.listState,
                            ) {
                                tabState.error?.let { message -> item { ErrorCard(message) { loadPosts(1, false) } } }
                                items(tabState.posts, key = TiebaUserPost::key, contentType = { "user_post" }) { post ->
                                    UserPostItem(post, user, onThread)
                                }
                                if (tabState.loading) item { LoadingRow() }
                                if (!tabState.loading && tabState.posts.isEmpty() && tabState.error == null) item { Text("暂无内容", Modifier.padding(20.dp)) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UserProfileTopAppBar(
    user: TiebaUserProfile,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val density = LocalDensity.current
    val expandedHeight = 328.dp
    val collapsedHeight = 64.dp
    SideEffect {
        scrollBehavior.state.heightOffsetLimit = with(density) {
            -(expandedHeight - collapsedHeight).toPx()
        }
    }

    val collapseFraction = scrollBehavior.state.collapsedFraction.coerceIn(0f, 1f)
    val detailAlpha = (1f - collapseFraction * 2.2f).coerceIn(0f, 1f)
    val expandedTitleAlpha = (1f - collapseFraction * 2f).coerceIn(0f, 1f)
    val collapsedTitleAlpha = ((collapseFraction - 0.45f) / 0.55f).coerceIn(0f, 1f)
    val currentHeight = lerp(expandedHeight.value, collapsedHeight.value, collapseFraction).dp
    val avatarSize = lerp(96f, 36f, collapseFraction).dp
    val avatarStart = lerp(18f, 60f, collapseFraction).dp
    val avatarTop = lerp(70f, 14f, collapseFraction).dp

    Box(
        Modifier.fillMaxWidth().height(currentHeight).clipToBounds()
            .background(MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        TiebaAsyncImage(
            url = user.avatarUrl,
            contentDescription = "${user.nickname}的头像",
            modifier = Modifier.offset(avatarStart, avatarTop).size(avatarSize).clip(CircleShape),
            contentScale = ContentScale.Crop,
        )
        Text(
            text = user.nickname.ifBlank { user.username },
            modifier = Modifier.fillMaxWidth().padding(start = 130.dp, end = 16.dp, top = 68.dp)
                .graphicsLayer { alpha = expandedTitleAlpha },
            maxLines = 2,
            fontSize = 24.sp,
            lineHeight = 28.sp,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = user.nickname.ifBlank { user.username },
            modifier = Modifier.fillMaxWidth().padding(start = 104.dp, end = 56.dp, top = 17.dp)
                .graphicsLayer { alpha = collapsedTitleAlpha },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
        )

        Column(
            Modifier.fillMaxWidth().padding(start = 130.dp, end = 16.dp, top = 132.dp)
                .graphicsLayer { alpha = detailAlpha },
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (user.nickname != user.username && user.username.isNotBlank()) {
                Text("(${user.username})", style = MaterialTheme.typography.titleMedium)
            }
            Row(
                Modifier.fillMaxWidth().height(54.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                UserStat("关注", user.followingCount.toString(), Modifier.weight(1f))
                VerticalDivider(Modifier.height(34.dp))
                UserStat("粉丝", user.fansCount.toString(), Modifier.weight(1f))
                VerticalDivider(Modifier.height(34.dp))
                UserStat("获赞", user.agreeCount.toString(), Modifier.weight(1f))
                VerticalDivider(Modifier.height(34.dp))
                UserStat("吧龄", user.tiebaAge.takeIf(String::isNotBlank)?.let { "$it 年" } ?: "-", Modifier.weight(1.15f))
            }
        }

        Column(
            Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 224.dp)
                .graphicsLayer { alpha = detailAlpha },
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Text(
                user.intro.ifBlank { "这个人很低调，暂时没有个人简介。" },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                ProfileInfoChip(user.sex, emphasized = true)
                ProfileInfoChip("ID: ${user.uid}")
                if (user.address.isNotBlank()) ProfileInfoChip("IP 属地：${user.address}")
                if (user.isOfficial) ProfileInfoChip("官方账号", emphasized = true)
            }
        }

        Box(Modifier.align(Alignment.TopStart).height(collapsedHeight), contentAlignment = Alignment.CenterStart) {
            BackButton(onBack)
        }
        Box(Modifier.align(Alignment.TopEnd).height(collapsedHeight), contentAlignment = Alignment.CenterEnd) {
            IconButton(onRefresh) { Icon(Icons.Default.Refresh, "刷新") }
        }
    }
}

@Composable
private fun ProfileInfoChip(text: String, emphasized: Boolean = false) {
    Surface(
        color = if (emphasized) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
        contentColor = if (emphasized) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer,
        shape = CircleShape,
    ) {
        Text(text, Modifier.padding(horizontal = 11.dp, vertical = 5.dp), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun UserStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = CcitColors.InkMuted)
        Text(value, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun UserPostItem(post: TiebaUserPost, user: TiebaUserProfile, onClick: (TiebaUserPost) -> Unit) {
    Column(
        Modifier.fillMaxWidth().clickable { onClick(post) }.padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TiebaAsyncImage(
                user.avatarUrl,
                null,
                Modifier.size(40.dp).clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(user.nickname.ifBlank { user.username }, style = MaterialTheme.typography.labelLarge)
                Text(post.time, style = MaterialTheme.typography.labelSmall, color = CcitColors.InkMuted)
            }
            if (post.forumName.isNotBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    shape = MaterialTheme.shapes.extraSmall,
                ) {
                    Text(
                        "${post.forumName.removeSuffix("吧")}吧",
                        Modifier.padding(horizontal = 7.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
        Text(
            post.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (post.excerpt.isNotBlank()) {
            Text(post.excerpt, maxLines = 5, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
        }
        if (post.imageUrls.isNotEmpty()) {
            val images = post.imageUrls.take(3)
            if (images.size == 1) {
                TiebaAsyncImage(
                    images.single(),
                    null,
                    Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 460.dp).clip(MaterialTheme.shapes.small),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    images.forEach { url ->
                        TiebaAsyncImage(
                            url,
                            null,
                            Modifier.weight(1f).height(104.dp).clip(MaterialTheme.shapes.small),
                            contentScale = ContentScale.Crop,
                        )
                    }
                }
            }
        }
    }
    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
}

@Composable
private fun UserForumList(
    user: TiebaUserProfile,
    modifier: Modifier,
    listState: LazyListState,
) {
    when {
        user.followedForumsPrivate -> Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Text("该用户隐藏了关注的吧") }
        user.followedForums.isEmpty() -> Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Text("暂无公开的关注吧") }
        else -> LazyColumn(modifier.fillMaxWidth(), state = listState) {
            items(user.followedForums, key = { it.id }) { forum ->
                ListItem(
                    headlineContent = { Text("${forum.name}吧") },
                    supportingContent = { Text("吧 ID：${forum.id}") },
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun FullImageScreen(runtime: TiebaRuntime, data: PhotoViewData, onBack: () -> Unit) {
    val item = data.picItems.getOrNull(data.index.coerceIn(data.picItems.indices)) ?: return
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var retry by remember(item.picId) { mutableIntStateOf(0) }
    var resolvedUrl by remember(item.picId, retry) { mutableStateOf<String?>(null) }
    var resolving by remember(item.picId, retry) { mutableStateOf(true) }
    var loading by remember(item.picId, retry) { mutableStateOf(false) }
    var failed by remember(item.picId, retry) { mutableStateOf(false) }
    var dimensions by remember(item.picId, retry) { mutableStateOf<Pair<Int, Int>?>(null) }
    val context = LocalContext.current
    LaunchedEffect(data.data, item.picId, retry) {
        resolving = true
        loading = false
        failed = false
        dimensions = null
        val refreshed = data.data?.let { requestData ->
            runCatching { runtime.network.resolveOriginalImage(requestData, runtime.accountDao.get()) }
                .getOrNull()
        }
        // Never fall back to a protobuf preview when picpage metadata is available: it may be
        // resized. Also reject unsigned item URLs because Baidu serves the 238 x 238 Tieba-logo
        // placeholder with HTTP 200 for those URLs.
        resolvedUrl = refreshed?.takeIf(::isAuthorizedTiebaImageUrl)
            ?: item.originUrl.takeIf { data.data == null && isAuthorizedTiebaImageUrl(it) }
        resolving = false
        if (resolvedUrl == null) failed = true
    }
    val request = remember(resolvedUrl, retry) {
        resolvedUrl?.let { originalUrl ->
            ImageRequest.Builder(context)
                .data(originalUrl)
                .httpHeaders(
                    NetworkHeaders.Builder()
                        .set("Referer", "https://tieba.baidu.com/")
                        .build(),
                )
                // TiebaLite's original-image loader does not downsample the source for zooming.
                .size(SizeResolver.ORIGINAL)
                .build()
        }
    }
    val transform = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 6f)
        if (scale == 1f) {
            offsetX = 0f
            offsetY = 0f
        } else {
            offsetX += pan.x
            offsetY += pan.y
        }
    }
    BackHandler(onBack = onBack)
    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        request?.let { originalRequest ->
            AsyncImage(
                model = originalRequest,
                contentDescription = "原图",
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offsetX
                    translationY = offsetY
                }.transformable(transform),
                contentScale = ContentScale.Fit,
                onLoading = {
                    loading = true
                    failed = false
                },
                onSuccess = { result ->
                    loading = false
                    failed = false
                    dimensions = result.result.image.width to result.result.image.height
                },
                onError = {
                    loading = false
                    failed = true
                },
            )
        }
        if (resolving || loading) {
            CircularProgressIndicator(color = Color.White)
        }
        if (failed) {
            Surface(
                color = Color(0xCC202124),
                contentColor = Color.White,
                shape = MaterialTheme.shapes.large,
            ) {
                Column(
                    Modifier.padding(horizontal = 24.dp, vertical = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Default.ErrorOutline, contentDescription = null)
                    Text("真实原图加载失败")
                    Text(
                        "未使用贴吧返回的占位图",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.LightGray,
                    )
                    TextButton(onClick = { retry++ }) { Text("重试") }
                }
            }
        }
        Row(
            Modifier.align(Alignment.TopStart).fillMaxWidth().background(Color(0x66000000)).padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = Color.White)
            }
            Text(
                dimensions?.let { (width, height) -> "原图 · ${width} × ${height}" }
                    ?: if (resolving) "正在获取真实原图…" else "正在加载原图…",
                color = Color.White,
                style = MaterialTheme.typography.titleSmall,
            )
        }
    }
}

@Composable
fun TiebaAccountCard(onLogin: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val runtime = remember(context) { TiebaRuntime.get(context) }
    val account by runtime.account.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    CcitCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AccountCircle, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("贴吧账号", style = MaterialTheme.typography.titleMedium)
            }
            if (account == null) {
                Button(onClick = onLogin, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.AutoMirrored.Filled.Login, null)
                    Spacer(Modifier.width(8.dp))
                    Text("登录百度贴吧")
                }
            } else {
                AccountContent(requireNotNull(account))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            scope.launch { busy = true; runtime.refreshAccount(); busy = false }
                        },
                        enabled = !busy,
                        modifier = Modifier.weight(1f),
                    ) { Text("刷新状态") }
                    OutlinedButton(
                        onClick = { scope.launch { busy = true; runtime.logout(); busy = false } },
                        enabled = !busy,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, null)
                        Spacer(Modifier.width(6.dp))
                        Text("退出")
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountContent(account: TiebaAccount) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(account.avatarUrl, null, Modifier.size(58.dp).clip(CircleShape), contentScale = ContentScale.Crop)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(account.nickname.ifBlank { account.username }, style = MaterialTheme.typography.titleMedium)
            Text("用户名：${account.username}", color = CcitColors.InkMuted, style = MaterialTheme.typography.bodySmall)
            Text("${account.fans} 粉丝 · ${account.posts} 帖子", color = CcitColors.InkMuted, style = MaterialTheme.typography.bodySmall)
        }
        Icon(Icons.Default.Check, "账号有效", tint = MaterialTheme.colorScheme.primary)
    }
}

private const val LoginUrl = "https://wappass.baidu.com/passport?login&u=https%3A%2F%2Ftieba.baidu.com%2Findex%2Ftbwise%2Fmine"

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TiebaLoginScreen(onBack: () -> Unit, onLoggedIn: () -> Unit) {
    val context = LocalContext.current
    val runtime = remember(context) { TiebaRuntime.get(context) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var submitting by remember { mutableStateOf(false) }
    BackHandler {
        if (webView?.canGoBack() == true) webView?.goBack() else onBack()
    }
    Scaffold(
        contentWindowInsets = TiebaWindowInsets,
        topBar = { TopAppBar(title = { Text("登录贴吧账号") }, navigationIcon = { BackButton(onBack) }, windowInsets = TiebaWindowInsets) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { viewContext ->
                    CookieManager.getInstance().setAcceptCookie(true)
                    WebView(viewContext).apply {
                        webView = this
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView, url: String?) {
                                super.onPageFinished(view, url)
                                if (submitting || url == null ||
                                    !(url.startsWith("https://tieba.baidu.com/index/tbwise/") || url.startsWith("https://tiebac.baidu.com/index/tbwise/"))
                                ) return
                                val raw = CookieManager.getInstance().getCookie(url).orEmpty()
                                val cookies = parseLoginCookies(raw) ?: return
                                submitting = true
                                scope.launch {
                                    runCatching { runtime.login(cookies) }
                                        .onSuccess { onLoggedIn() }
                                        .onFailure {
                                            submitting = false
                                            snackbar.showSnackbar(it.message ?: "登录失败")
                                        }
                                }
                            }
                        }
                        loadUrl(LoginUrl)
                    }
                },
            )
            if (submitting) Surface(Modifier.align(Alignment.Center), shape = MaterialTheme.shapes.large, tonalElevation = 8.dp) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("正在登录…")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TiebaSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val runtime = remember(context) { TiebaRuntime.get(context) }
    val preferences by runtime.settings.preferences.collectAsStateWithLifecycle(initialValue = edu.ccit.webvpn.feature.tieba.TiebaPreferences())
    val account by runtime.account.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    fun enableWhenReady() {
        if (account == null) {
            scope.launch { snackbar.showSnackbar("请先在“我的”中登录贴吧账号") }
            return
        }
        scope.launch {
            runtime.settings.setSignEnabled(true)
            runtime.onAppForegrounded()
        }
    }

    Scaffold(
        contentWindowInsets = TiebaWindowInsets,
        topBar = { TopAppBar(title = { Text("贴吧设置") }, navigationIcon = { BackButton(onBack) }, windowInsets = TiebaWindowInsets) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(vertical = 8.dp)) {
            item { SectionTitle("阅读") }
            item {
                ChoiceItem("吧内默认排序", listOf("按最后回复" to ForumSort.BY_REPLY, "按发布时间" to ForumSort.BY_SEND), preferences.reading.forumSort) {
                    scope.launch { runtime.settings.setForumSort(it) }
                }
            }
            item {
                ChoiceItem("楼层默认顺序", listOf("正序" to FloorSort.ASCENDING, "倒序" to FloorSort.DESCENDING, "热门" to FloorSort.HOT), preferences.reading.floorSort) {
                    scope.launch { runtime.settings.setFloorSort(it) }
                }
            }
            item { SwitchItem("默认只看楼主", null, preferences.reading.onlyOriginalPoster) { scope.launch { runtime.settings.setOnlyOriginalPoster(it) } } }
            item { SwitchItem("同时显示用户名和昵称", null, preferences.reading.showBothNames) { scope.launch { runtime.settings.setShowBothNames(it) } } }
            item { ListItem(headlineContent = { Text("图片画质") }, leadingContent = { Icon(Icons.Default.Image, null) }, trailingContent = { Text("始终高质量") }) }
            item { HorizontalDivider() }
            item { SectionTitle("签到") }
            item {
                SwitchItem(
                    "自动签到",
                    if (account == null) "请先登录" else "每天首次打开或回到应用前台时自动签到一次",
                    preferences.sign.enabled,
                    enabled = account != null,
                ) { desired ->
                    if (!desired) scope.launch { runtime.settings.setSignEnabled(false) }
                    else enableWhenReady()
                }
            }
            item {
                val outcome = preferences.sign.lastOutcome
                ListItem(
                    headlineContent = { Text("最近一次签到") },
                    supportingContent = {
                        Column {
                            Text(preferences.sign.lastRunAt?.let { DateFormat.getDateTimeInstance().format(Date(it)) } ?: "尚未执行")
                            preferences.sign.lastMessage?.let { Text(it) }
                        }
                    },
                    leadingContent = { Icon(if (outcome == SignOutcome.FAILED) Icons.Default.ErrorOutline else Icons.Default.Check, null) },
                )
            }
        }
    }
}

@Composable
private fun <T> ChoiceItem(title: String, options: List<Pair<String, T>>, selected: T, onSelect: (T) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        options.forEach { (label, value) ->
            Row(Modifier.fillMaxWidth().clickable { onSelect(value) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected == value, { onSelect(value) })
                Text(label)
            }
        }
    }
}

@Composable
private fun SwitchItem(title: String, summary: String?, checked: Boolean, enabled: Boolean = true, onChecked: (Boolean) -> Unit) {
    ListItem(
        modifier = Modifier.clickable(enabled = enabled) { onChecked(!checked) },
        headlineContent = { Text(title) },
        supportingContent = summary?.let { { Text(it) } },
        trailingContent = { Switch(checked, onChecked, enabled = enabled) },
    )
}

@Composable
private fun SectionTitle(text: String) = Text(text, Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.titleLarge)

@Composable
private fun BackButton(onBack: () -> Unit) = IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }

@Composable
private fun ErrorCard(message: String, retry: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.ErrorOutline, null, tint = MaterialTheme.colorScheme.error)
            Spacer(Modifier.width(8.dp))
            Text(message, Modifier.weight(1f))
            TextButton(retry) { Text("重试") }
        }
    }
}

@Composable
private fun LoadingRow() = Box(Modifier.fillMaxWidth().padding(18.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp) }

private fun displayName(name: String, nickname: String, both: Boolean): String = when {
    both && nickname.isNotBlank() && nickname != name -> "$nickname（$name）"
    nickname.isNotBlank() -> nickname
    name.isNotBlank() -> name
    else -> "匿名吧友"
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LongPressActionMenu(
    target: ContentActionTarget,
    onReply: (ContentActionTarget) -> Unit,
    onClick: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val interactionSource = remember { MutableInteractionSource() }
    var expanded by remember { mutableStateOf(false) }
    var pressOffset by remember { mutableStateOf(IntOffset.Zero) }
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.filterIsInstance<PressInteraction.Press>().collect { press ->
            pressOffset = press.pressPosition.round()
        }
    }
    Box(
        modifier = Modifier.combinedClickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick,
            onLongClick = { expanded = true },
        ),
    ) {
        content()
        Box(Modifier.offset { pressOffset }) {
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                containerColor = MaterialTheme.colorScheme.background,
                shape = MaterialTheme.shapes.small,
                shadowElevation = 7.dp,
            ) {
                fun dismissAfter(action: () -> Unit) {
                    action()
                    expanded = false
                }
                DropdownMenuItem(text = { Text("回复") }, onClick = { dismissAfter { onReply(target) } })
                DropdownMenuItem(text = { Text("复制") }, onClick = { dismissAfter { context.copyText(target.text) } })
            }
        }
    }
}

private fun Context.copyText(text: String) {
    getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("贴吧内容", text))
    Toast.makeText(this, "已复制", Toast.LENGTH_SHORT).show()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReplyScreen(
    runtime: TiebaRuntime,
    target: ContentActionTarget,
    onBack: () -> Unit,
    onSent: () -> Unit,
) {
    val account by runtime.account.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val drafts = remember(context) { context.getSharedPreferences("tieba_reply_drafts", Context.MODE_PRIVATE) }
    val draftKey = remember(target) { "${target.threadId}:${target.postId ?: 0}:${target.subPostId ?: 0}" }
    var text by rememberSaveable(target.threadId, target.postId, target.subPostId) { mutableStateOf("") }
    var activePanel by rememberSaveable { mutableIntStateOf(0) }
    var selectedImages by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var saveOriginal by rememberSaveable { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var replySent by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(draftKey) {
        val restored = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            drafts.getString(draftKey, "").orEmpty()
        }
        if (text.isBlank()) text = restored
    }
    val latestText by rememberUpdatedState(text)
    DisposableEffect(draftKey) {
        onDispose {
            drafts.edit().apply {
                if (replySent || latestText.isBlank()) remove(draftKey) else putString(draftKey, latestText)
            }.apply()
        }
    }
    val imagePicker = rememberLauncherForActivityResult(PickMultipleVisualMedia(9)) { uris ->
        selectedImages = (selectedImages + uris.map(Uri::toString)).distinct().take(9)
    }

    Scaffold(
        contentWindowInsets = TiebaWindowInsets,
        topBar = {
            TopAppBar(
                title = { Text("回复") },
                navigationIcon = { BackButton(onBack) },
                windowInsets = TiebaWindowInsets,
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding(),
            verticalArrangement = Arrangement.Bottom,
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 3.dp,
            ) {
                Column(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        account?.let {
                            TiebaAsyncImage(
                                url = it.avatarUrl,
                                contentDescription = it.nickname,
                                modifier = Modifier.size(32.dp).clip(CircleShape),
                                contentScale = ContentScale.Crop,
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            target.replyUserName.takeIf(String::isNotBlank)?.let { "回复 $it" } ?: "回复帖子",
                            Modifier.weight(1f),
                            fontWeight = FontWeight.Bold,
                        )
                        Text(text.length.toString(), color = CcitColors.InkMuted, style = MaterialTheme.typography.labelMedium)
                    }
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).heightIn(min = 96.dp, max = 220.dp),
                        placeholder = { Text(target.replyUserName.takeIf(String::isNotBlank)?.let { "回复 $it" } ?: "说点什么吧") },
                        enabled = !sending && account != null,
                    )
                    (error ?: if (account == null) "请先在“我的”中登录贴吧账号" else null)?.let {
                        Text(it, Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.error)
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = { activePanel = if (activePanel == 1) 0 else 1 }, enabled = !sending) {
                            Icon(Icons.Outlined.EmojiEmotions, contentDescription = "插入表情")
                        }
                        if (target.postId == null) {
                            IconButton(onClick = { activePanel = if (activePanel == 2) 0 else 2 }, enabled = !sending) {
                                Icon(Icons.Outlined.InsertPhoto, contentDescription = "插入图片")
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        if (sending) {
                            CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
                        } else {
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        val entity = runtime.accountDao.get()
                                        if (entity == null) {
                                            error = "请先在“我的”中登录贴吧账号"
                                            return@launch
                                        }
                                        sending = true
                                        error = null
                                        runCatching {
                                            val uploaded = if (selectedImages.isEmpty()) {
                                                emptyList()
                                            } else {
                                                runtime.network.uploadReplyImages(
                                                    imageUris = selectedImages.map(Uri::parse),
                                                    forumName = target.forumName,
                                                    saveOriginal = saveOriginal,
                                                    account = entity,
                                                )
                                            }
                                            val replyContent = buildString {
                                                append(text.trim())
                                                uploaded.forEach { image ->
                                                    if (isNotEmpty()) append('\n')
                                                    append("#(pic,${image.picId},${image.width},${image.height})")
                                                }
                                            }
                                            runtime.network.addReply(
                                                content = replyContent,
                                                forumId = target.forumId,
                                                forumName = target.forumName,
                                                threadId = target.threadId,
                                                postId = target.postId,
                                                subPostId = target.subPostId,
                                                replyUserId = target.replyUserId,
                                                replyUserName = target.replyUserName,
                                                replyUserPortrait = target.replyUserPortrait,
                                                account = entity,
                                            )
                                        }.onSuccess { result ->
                                            val message = result.experienceAdded.takeIf(String::isNotBlank)
                                                ?.let { "回复成功，经验 +$it" } ?: "回复成功"
                                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                            replySent = true
                                            text = ""
                                            drafts.edit().remove(draftKey).apply()
                                            onSent()
                                        }.onFailure { failure ->
                                            error = failure.message ?: "回复失败"
                                        }
                                        sending = false
                                    }
                                },
                                enabled = (text.isNotBlank() || selectedImages.isNotEmpty()) && account != null,
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "发送回复")
                            }
                        }
                    }
                    if (activePanel == 1) {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(48.dp),
                            modifier = Modifier.fillMaxWidth().height(240.dp).padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            gridItems((1..50).toList(), key = { it }) { id ->
                                TiebaAsyncImage(
                                    url = emoticonModel("image_emoticon$id"),
                                    contentDescription = "表情 $id",
                                    modifier = Modifier.size(44.dp).clickable {
                                        text += "#(image_emoticon$id)"
                                    }.padding(7.dp),
                                    contentScale = ContentScale.Fit,
                                )
                            }
                        }
                    }
                    if (activePanel == 2 && target.postId == null) {
                        Column(Modifier.fillMaxWidth().height(240.dp).padding(12.dp)) {
                            LazyRow(
                                modifier = Modifier.fillMaxWidth().height(150.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                item(key = "add") {
                                    Surface(
                                        onClick = {
                                            imagePicker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
                                        },
                                        modifier = Modifier.size(112.dp),
                                        shape = MaterialTheme.shapes.medium,
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(Icons.Outlined.InsertPhoto, contentDescription = "选择图片")
                                        }
                                    }
                                }
                                itemsIndexed(selectedImages, key = { _, uri -> uri }) { index, uri ->
                                    Box(Modifier.size(112.dp)) {
                                        AsyncImage(
                                            model = uri,
                                            contentDescription = "待上传图片 ${index + 1}",
                                            modifier = Modifier.fillMaxSize().clip(MaterialTheme.shapes.medium),
                                            contentScale = ContentScale.Crop,
                                        )
                                        IconButton(
                                            onClick = { selectedImages = selectedImages.filterIndexed { i, _ -> i != index } },
                                            modifier = Modifier.align(Alignment.TopEnd).size(32.dp),
                                        ) {
                                            Icon(Icons.Default.Close, contentDescription = "移除图片")
                                        }
                                    }
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = saveOriginal, onCheckedChange = { saveOriginal = it })
                                Text("原图")
                                Spacer(Modifier.weight(1f))
                                Text("${selectedImages.size}/9", color = CcitColors.InkMuted)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun FloorSort.label(): String = when (this) {
    FloorSort.ASCENDING -> "正序"
    FloorSort.DESCENDING -> "倒序"
    FloorSort.HOT -> "热门"
}

private fun isToday(timestamp: Long?): Boolean = timestamp?.let {
    Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() == java.time.LocalDate.now()
} ?: false
