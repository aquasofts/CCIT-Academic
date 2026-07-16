package edu.ccit.webvpn.feature.tieba.ui

import android.annotation.SuppressLint
import android.net.Uri
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import edu.ccit.webvpn.core.ui.CcitCard
import edu.ccit.webvpn.core.ui.CcitColors
import edu.ccit.webvpn.feature.tieba.FloorSort
import edu.ccit.webvpn.feature.tieba.ForumPage
import edu.ccit.webvpn.feature.tieba.ForumSort
import edu.ccit.webvpn.feature.tieba.ForumSummary
import edu.ccit.webvpn.feature.tieba.ForumThread
import edu.ccit.webvpn.feature.tieba.SignOutcome
import edu.ccit.webvpn.feature.tieba.TARGET_FORUM_DISPLAY_NAME
import edu.ccit.webvpn.feature.tieba.TiebaAccount
import edu.ccit.webvpn.feature.tieba.TiebaContent
import edu.ccit.webvpn.feature.tieba.TiebaRuntime
import edu.ccit.webvpn.feature.tieba.TiebaUserPost
import edu.ccit.webvpn.feature.tieba.TiebaUserProfile
import edu.ccit.webvpn.feature.tieba.ThreadFloor
import edu.ccit.webvpn.feature.tieba.network.parseLoginCookies
import java.text.DateFormat
import java.time.Instant
import java.time.ZoneId
import java.util.Date
import kotlinx.coroutines.launch

private const val ForumRoute = "forum"
private const val SearchRoute = "search"
private const val ThreadRoute = "thread"
private const val FloorRepliesRoute = "floor_replies"
private const val ProfileRoute = "profile"
private const val ImageRoute = "image"
private val TiebaWindowInsets = WindowInsets(0, 0, 0, 0)

@Composable
fun TiebaRootScreen(active: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val runtime = remember(context) { TiebaRuntime.get(context) }
    val navigator = rememberNavController()
    var fullImageUrl by rememberSaveable { mutableStateOf("") }

    fun openThread(thread: ForumThread, focusPostId: Long = 0) {
        navigator.navigate(
            "$ThreadRoute/${thread.id}?title=${Uri.encode(thread.title)}" +
                "&forumId=${thread.forumId}&forumName=${Uri.encode(thread.forumName)}&postId=$focusPostId",
        )
    }

    fun openProfile(uid: Long) {
        if (uid > 0) navigator.navigate("$ProfileRoute/$uid")
    }

    NavHost(navigator, startDestination = ForumRoute, modifier = modifier.fillMaxSize()) {
        composable(ForumRoute) {
            ForumScreen(
                active = active,
                runtime = runtime,
                onSearch = { navigator.navigate(SearchRoute) },
                onThread = ::openThread,
                onProfile = { openProfile(it.authorId) },
            )
        }
        composable(SearchRoute) {
            SearchScreen(runtime, navigator::navigateUp, ::openThread) { openProfile(it.authorId) }
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
            ThreadScreen(
                runtime = runtime,
                threadId = entry.arguments?.getString("id").orEmpty(),
                initialTitle = entry.arguments?.getString("title").orEmpty(),
                forumId = forumId,
                forumName = forumName,
                focusPostId = entry.arguments?.getLong("postId")?.takeIf { it > 0 }?.toString(),
                onBack = navigator::navigateUp,
                onImage = { url ->
                    fullImageUrl = url
                    navigator.navigate(ImageRoute)
                },
                onReplies = { postId ->
                    navigator.navigate(
                        "$FloorRepliesRoute/${entry.arguments?.getString("id").orEmpty()}/$postId" +
                            "?forumId=$forumId&forumName=${Uri.encode(forumName)}",
                    )
                },
                onProfile = ::openProfile,
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
            FloorRepliesScreen(
                runtime = runtime,
                threadId = entry.arguments?.getString("threadId").orEmpty(),
                postId = entry.arguments?.getString("postId").orEmpty(),
                forumId = entry.arguments?.getLong("forumId") ?: edu.ccit.webvpn.feature.tieba.TARGET_FORUM_ID,
                forumName = entry.arguments?.getString("forumName").orEmpty(),
                onBack = navigator::navigateUp,
                onImage = { url -> fullImageUrl = url; navigator.navigate(ImageRoute) },
                onProfile = ::openProfile,
            )
        }
        composable(
            "$ProfileRoute/{uid}",
            arguments = listOf(navArgument("uid") { type = NavType.LongType }),
        ) { entry ->
            UserProfileScreen(
                runtime = runtime,
                uid = entry.arguments?.getLong("uid") ?: 0,
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
        composable(ImageRoute) { FullImageScreen(fullImageUrl, navigator::navigateUp) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ForumScreen(
    active: Boolean,
    runtime: TiebaRuntime,
    onSearch: () -> Unit,
    onThread: (ForumThread) -> Unit,
    onProfile: (ForumThread) -> Unit,
) {
    val preferences by runtime.settings.preferences.collectAsStateWithLifecycle(initialValue = edu.ccit.webvpn.feature.tieba.TiebaPreferences())
    val account by runtime.account.collectAsStateWithLifecycle()
    val signState by runtime.signState.collectAsStateWithLifecycle()
    val signedToday = preferences.sign.lastOutcome != SignOutcome.FAILED && isToday(preferences.sign.lastRunAt)
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var page by rememberSaveable { mutableIntStateOf(1) }
    var goodOnly by rememberSaveable { mutableStateOf(false) }
    var threads by remember { mutableStateOf(emptyList<ForumThread>()) }
    var hasMore by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var sortMenu by remember { mutableStateOf(false) }

    fun load(targetPage: Int, append: Boolean) {
        if (loading) return
        scope.launch {
            loading = true
            error = null
            runCatching {
                runtime.network.loadForum(
                    targetPage,
                    preferences.reading.forumSort,
                    goodOnly,
                    runtime.accountDao.get(),
                )
            }.onSuccess { result ->
                threads = if (append) (threads + result.threads).distinctBy(ForumThread::id) else result.threads
                page = targetPage
                hasMore = result.hasMore
            }.onFailure { error = it.message ?: "加载失败" }
            loading = false
        }
    }

    LaunchedEffect(active, goodOnly, preferences.reading.forumSort, refreshKey) {
        if (active) load(1, false)
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
                                    val result = runtime.signNow()
                                    snackbar.showSnackbar(result.message)
                                }
                            }
                        },
                        enabled = signState !is edu.ccit.webvpn.feature.tieba.TiebaSignState.Running && (account == null || !signedToday),
                    ) {
                        if (signState is edu.ccit.webvpn.feature.tieba.TiebaSignState.Running) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text(if (signedToday) "已签" else "签到")
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
                    IconButton(onClick = { refreshKey++ }) { Icon(Icons.Default.Refresh, "刷新") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            item {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !goodOnly, onClick = { goodOnly = false }, label = { Text("最新") })
                    FilterChip(selected = goodOnly, onClick = { goodOnly = true }, label = { Text("精品") })
                }
            }
            error?.let { message ->
                item { ErrorCard(message) { refreshKey++ } }
            }
            items(threads, key = ForumThread::id) { thread ->
                ThreadRow(thread, preferences.reading.showBothNames, onThread, onProfile)
            }
            if (loading) item { LoadingRow() }
            if (!loading && threads.isEmpty() && error == null && active) item { Text("暂无帖子") }
            if (!loading && hasMore) {
                item { OutlinedButton(onClick = { load(page + 1, true) }, modifier = Modifier.fillMaxWidth()) { Text("加载更多") } }
            }
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
            if (thread.excerpt.isNotBlank()) {
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
    onBack: () -> Unit,
    onThread: (ForumThread) -> Unit,
    onProfile: (ForumThread) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var keyword by rememberSaveable { mutableStateOf("") }
    var page by rememberSaveable { mutableIntStateOf(1) }
    var result by remember { mutableStateOf(ForumPage(ForumSummary(), emptyList(), 1, false)) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    fun search(targetPage: Int, append: Boolean) {
        if (keyword.isBlank() || loading) return
        scope.launch {
            loading = true
            error = null
            runCatching { runtime.network.search(keyword.trim(), targetPage) }
                .onSuccess { loaded ->
                    result = if (append) loaded.copy(threads = (result.threads + loaded.threads).distinctBy(ForumThread::id)) else loaded
                    page = targetPage
                }.onFailure { error = it.message ?: "搜索失败" }
            loading = false
        }
    }
    Scaffold(
        contentWindowInsets = TiebaWindowInsets,
        topBar = { TopAppBar(title = { Text("吧内搜索") }, navigationIcon = { BackButton(onBack) }, windowInsets = TiebaWindowInsets) },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(keyword, { keyword = it }, label = { Text("搜索 $TARGET_FORUM_DISPLAY_NAME") }, singleLine = true, modifier = Modifier.weight(1f))
                    IconButton(onClick = { search(1, false) }) { Icon(Icons.Default.Search, "搜索") }
                }
            }
            error?.let { item { ErrorCard(it) { search(1, false) } } }
            items(result.threads, key = ForumThread::id) { ThreadRow(it, false, onThread, onProfile) }
            if (loading) item { LoadingRow() }
            if (!loading && result.hasMore) item { OutlinedButton({ search(page + 1, true) }, Modifier.fillMaxWidth()) { Text("加载更多") } }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThreadScreen(
    runtime: TiebaRuntime,
    threadId: String,
    initialTitle: String,
    forumId: Long,
    forumName: String,
    focusPostId: String?,
    onBack: () -> Unit,
    onImage: (String) -> Unit,
    onReplies: (String) -> Unit,
    onProfile: (Long) -> Unit,
) {
    val preferences by runtime.settings.preferences.collectAsStateWithLifecycle(initialValue = edu.ccit.webvpn.feature.tieba.TiebaPreferences())
    val scope = rememberCoroutineScope()
    var page by rememberSaveable(threadId) { mutableIntStateOf(1) }
    var pageInput by rememberSaveable(threadId) { mutableStateOf("1") }
    var title by remember(threadId) { mutableStateOf(initialTitle) }
    var totalPages by remember(threadId) { mutableIntStateOf(1) }
    var body by remember(threadId) { mutableStateOf<ThreadFloor?>(null) }
    var floors by remember(threadId) { mutableStateOf(emptyList<ThreadFloor>()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var sortMenu by remember { mutableStateOf(false) }

    LaunchedEffect(threadId, forumId, forumName, focusPostId, page, preferences.reading.floorSort, preferences.reading.onlyOriginalPoster, refreshKey) {
        loading = true
        error = null
        body = null
        floors = emptyList()
        runCatching {
            runtime.network.loadThread(
                threadId,
                page,
                preferences.reading.floorSort,
                preferences.reading.onlyOriginalPoster,
                runtime.accountDao.get(),
                forumId = forumId,
                forumName = forumName,
                focusPostId = focusPostId,
            )
        }
            .onSuccess {
                title = it.title
                body = it.body
                floors = it.floors
                totalPages = it.totalPages
                pageInput = page.toString()
            }.onFailure { error = it.message ?: "帖子加载失败" }
        loading = false
    }

    Scaffold(contentWindowInsets = TiebaWindowInsets, containerColor = MaterialTheme.colorScheme.background, topBar = {
        TopAppBar(
            windowInsets = TiebaWindowInsets,
            title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = { BackButton(onBack) },
            actions = {
                Box {
                    IconButton({ sortMenu = true }) { Icon(Icons.Default.Tune, "阅读偏好") }
                    DropdownMenu(sortMenu, { sortMenu = false }) {
                        FloorSort.entries.forEach { sort ->
                            DropdownMenuItem(
                                text = { Text("${sort.label()}${if (sort == preferences.reading.floorSort) " ✓" else ""}") },
                                onClick = { sortMenu = false; scope.launch { runtime.settings.setFloorSort(sort) } },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(if (preferences.reading.onlyOriginalPoster) "显示全部楼层" else "只看楼主") },
                            onClick = { sortMenu = false; scope.launch { runtime.settings.setOnlyOriginalPoster(!preferences.reading.onlyOriginalPoster) } },
                        )
                    }
                }
                IconButton({ refreshKey++ }) { Icon(Icons.Default.Refresh, "刷新") }
            },
        )
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.background)) {
            if (loading) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 4.dp),
                ) {
                    error?.let { item { ErrorCard(it) { refreshKey++ } } }
                    body?.let { firstPost ->
                        item(key = "thread_body", contentType = "thread_body") {
                            Column {
                                Text(
                                    "正文",
                                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                )
                                FloorBody(
                                    floor = firstPost,
                                    showBothNames = preferences.reading.showBothNames,
                                    onImage = onImage,
                                    onReplies = { onReplies(firstPost.postId) },
                                    onProfile = onProfile,
                                )
                                HorizontalDivider(thickness = 3.dp)
                                Text(
                                    "回复",
                                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                    items(floors, key = ThreadFloor::postId, contentType = { "floor" }) { floor ->
                        FloorBody(
                            floor = floor,
                            showBothNames = preferences.reading.showBothNames,
                            onImage = onImage,
                            onReplies = { onReplies(floor.postId) },
                            onProfile = onProfile,
                        )
                    }
                    if (floors.isEmpty() && error == null) item { Text("本页暂无回复", Modifier.padding(16.dp)) }
                    if (error == null) {
                        item {
                            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                    OutlinedButton({ if (page > 1) page-- }, enabled = page > 1) { Text("上一页") }
                                    Text("第 $page / $totalPages 页", style = MaterialTheme.typography.labelLarge)
                                    OutlinedButton({ if (page < totalPages) page++ }, enabled = page < totalPages) { Text("下一页") }
                                }
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(pageInput, { pageInput = it.filter(Char::isDigit) }, modifier = Modifier.weight(1f), singleLine = true, label = { Text("输入页码") })
                                    Button({ page = pageInput.toIntOrNull()?.coerceIn(1, totalPages) ?: page }) { Text("跳转") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FloorHeader(floor: ThreadFloor, showBothNames: Boolean, onProfile: () -> Unit) {
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
            Text(
                listOfNotNull(
                    floor.time.takeIf(String::isNotBlank),
                    "第 ${floor.floor} 楼",
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = CcitColors.InkMuted,
            )
        }
    }
}

@Composable
private fun FloorBody(
    floor: ThreadFloor,
    showBothNames: Boolean,
    onImage: (String) -> Unit,
    onReplies: () -> Unit,
    onProfile: (Long) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
        FloorHeader(floor, showBothNames) {
            if (floor.authorId > 0) onProfile(floor.authorId)
        }
        Column(
            Modifier.fillMaxWidth().padding(start = 46.dp, top = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TiebaContentBody(
                content = floor.richContent,
                fallbackText = floor.content,
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
                            RichTiebaText(
                                content = listOf(TiebaContent.Text("$author：")) + reply.richContent.ifEmpty {
                                    listOf(TiebaContent.Text(reply.content))
                                },
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
                                maxLines = 4,
                                style = MaterialTheme.typography.bodyMedium,
                            )
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
    HorizontalDivider(Modifier.padding(start = 62.dp), color = MaterialTheme.colorScheme.outlineVariant)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FloorRepliesScreen(
    runtime: TiebaRuntime,
    threadId: String,
    postId: String,
    forumId: Long,
    forumName: String,
    onBack: () -> Unit,
    onImage: (String) -> Unit,
    onProfile: (Long) -> Unit,
) {
    var page by rememberSaveable(threadId, postId) { mutableIntStateOf(1) }
    var totalPages by remember(threadId, postId) { mutableIntStateOf(1) }
    var replies by remember(threadId, postId) { mutableStateOf(emptyList<edu.ccit.webvpn.feature.tieba.FloorReply>()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(threadId, postId, page, refreshKey) {
        loading = true
        error = null
        runCatching {
            runtime.network.loadFloorReplies(
                threadId,
                postId,
                page,
                account = runtime.accountDao.get(),
                forumId = forumId,
                forumName = forumName,
            )
        }
            .onSuccess {
                replies = it.replies
                totalPages = it.totalPages
            }
            .onFailure { error = it.message ?: "帖子数据异常" }
        loading = false
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
            contentPadding = PaddingValues(vertical = 4.dp),
        ) {
            error?.let { item { ErrorCard(it) { refreshKey++ } } }
            items(
                replies,
                key = { it.id.ifBlank { "${it.authorName}:${it.time}:${it.content.hashCode()}" } },
                contentType = { "floor_reply" },
            ) { reply ->
                FloorReplyItem(reply, onImage, onProfile)
            }
            if (loading) item { LoadingRow() }
            if (!loading && replies.isEmpty() && error == null) item { Text("暂无回复") }
            if (totalPages > 1) {
                item {
                    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        OutlinedButton({ page-- }, enabled = page > 1) { Text("上一页") }
                        Text("第 $page / $totalPages 页", modifier = Modifier.align(Alignment.CenterVertically))
                        OutlinedButton({ page++ }, enabled = page < totalPages) { Text("下一页") }
                    }
                }
            }
        }
    }
}

@Composable
private fun FloorReplyItem(
    reply: edu.ccit.webvpn.feature.tieba.FloorReply,
    onImage: (String) -> Unit,
    onProfile: (Long) -> Unit,
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
                    reply.authorNickname.ifBlank { reply.authorName }.ifBlank { "贴吧用户" },
                    style = MaterialTheme.typography.labelLarge,
                )
                if (reply.time.isNotBlank()) {
                    Text(reply.time, style = MaterialTheme.typography.labelSmall, color = CcitColors.InkMuted)
                }
            }
        }
        TiebaContentBody(
            content = reply.richContent,
            fallbackText = reply.content,
            onImage = onImage,
            modifier = Modifier.fillMaxWidth().padding(start = 46.dp, top = 8.dp),
        )
    }
    HorizontalDivider(Modifier.padding(start = 62.dp), color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun TiebaContentBody(
    content: List<TiebaContent>,
    fallbackText: String,
    onImage: (String) -> Unit,
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
                        contentScale = ContentScale.Crop,
                        onClick = { onImage(media.originalUrl) },
                    )
                }
                is TiebaContent.Video -> VideoPlayer(media.url)
                else -> RichTiebaText(block, Modifier.fillMaxWidth())
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
    uid: Long,
    onBack: () -> Unit,
    onThread: (TiebaUserPost) -> Unit,
) {
    val account by runtime.account.collectAsStateWithLifecycle()
    var profile by remember(uid) { mutableStateOf<TiebaUserProfile?>(null) }
    var profileLoading by remember(uid) { mutableStateOf(true) }
    var profileError by remember(uid) { mutableStateOf<String?>(null) }
    var selectedTab by rememberSaveable(uid) { mutableStateOf(UserProfileTab.Threads) }
    var page by remember(uid, selectedTab) { mutableIntStateOf(1) }
    var posts by remember(uid, selectedTab) { mutableStateOf(emptyList<TiebaUserPost>()) }
    var postsLoading by remember(uid, selectedTab) { mutableStateOf(false) }
    var postsError by remember(uid, selectedTab) { mutableStateOf<String?>(null) }
    var hasMore by remember(uid, selectedTab) { mutableStateOf(false) }
    var refreshKey by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(account?.uid, uid, selectedTab) {
        if (selectedTab == UserProfileTab.Replies && account?.uid != uid) {
            selectedTab = UserProfileTab.Threads
        }
    }

    LaunchedEffect(uid, refreshKey) {
        profileLoading = true
        profileError = null
        runCatching { runtime.network.loadUserProfile(uid, runtime.accountDao.get()) }
            .onSuccess { profile = it }
            .onFailure { profileError = it.message ?: "用户资料加载失败" }
        profileLoading = false
    }

    fun loadPosts(targetPage: Int, append: Boolean) {
        if (postsLoading || selectedTab == UserProfileTab.Forums) return
        scope.launch {
            postsLoading = true
            postsError = null
            runCatching {
                runtime.network.loadUserPosts(
                    uid = uid,
                    page = targetPage,
                    isThread = selectedTab == UserProfileTab.Threads,
                    account = runtime.accountDao.get(),
                )
            }.onSuccess { result ->
                posts = if (append) (posts + result.posts).distinctBy(TiebaUserPost::key) else result.posts
                page = targetPage
                hasMore = result.hasMore
            }.onFailure { postsError = it.message ?: "用户帖子加载失败" }
            postsLoading = false
        }
    }

    LaunchedEffect(uid, selectedTab, profile?.uid) {
        posts = emptyList()
        page = 1
        hasMore = false
        if (profile != null && selectedTab != UserProfileTab.Forums) loadPosts(1, false)
    }

    Scaffold(
        contentWindowInsets = TiebaWindowInsets,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { TopAppBar(title = { Text(profile?.nickname ?: "用户资料") }, navigationIcon = { BackButton(onBack) }, windowInsets = TiebaWindowInsets) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.background)) {
            when {
                profileLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                profileError != null -> ErrorCard(profileError.orEmpty()) { refreshKey++ }
                profile != null -> {
                    val user = requireNotNull(profile)
                    val tabs = buildList {
                        add(UserProfileTab.Threads)
                        if (account?.uid == uid) add(UserProfileTab.Replies)
                        add(UserProfileTab.Forums)
                    }
                    Column(Modifier.fillMaxSize()) {
                        UserProfileHeader(user)
                        PrimaryTabRow(selectedTabIndex = tabs.indexOf(selectedTab).coerceAtLeast(0)) {
                            tabs.forEach { tab ->
                                val count = when (tab) {
                                    UserProfileTab.Threads -> user.threadCount
                                    UserProfileTab.Replies -> user.postCount
                                    UserProfileTab.Forums -> user.forumCount
                                }
                                Tab(
                                    selected = selectedTab == tab,
                                    onClick = { selectedTab = tab },
                                    text = { Text("${tab.label} $count") },
                                )
                            }
                        }
                        if (selectedTab == UserProfileTab.Forums) {
                            UserForumList(user, Modifier.weight(1f))
                        } else {
                            LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                                postsError?.let { message -> item { ErrorCard(message) { loadPosts(1, false) } } }
                                items(posts, key = TiebaUserPost::key, contentType = { "user_post" }) { post ->
                                    UserPostItem(post, onThread)
                                }
                                if (postsLoading) item { LoadingRow() }
                                if (!postsLoading && posts.isEmpty() && postsError == null) item { Text("暂无内容", Modifier.padding(20.dp)) }
                                if (!postsLoading && hasMore) {
                                    item { OutlinedButton({ loadPosts(page + 1, true) }, Modifier.fillMaxWidth().padding(16.dp)) { Text("加载更多") } }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UserProfileHeader(user: TiebaUserProfile) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TiebaAsyncImage(
                user.avatarUrl,
                null,
                Modifier.size(80.dp).clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(user.nickname.ifBlank { user.username }, style = MaterialTheme.typography.headlineSmall)
                    if (user.isOfficial) AssistChip(onClick = {}, label = { Text("官方") })
                }
                if (user.nickname != user.username && user.username.isNotBlank()) {
                    Text("用户名：${user.username}", color = CcitColors.InkMuted)
                }
                if (user.intro.isNotBlank()) Text(user.intro, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Row(Modifier.fillMaxWidth()) {
            UserStat("关注", user.followingCount, Modifier.weight(1f))
            UserStat("粉丝", user.fansCount, Modifier.weight(1f))
            UserStat("获赞", user.agreeCount, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(onClick = {}, label = { Text(user.sex) })
            if (user.tiebaAge.isNotBlank()) AssistChip(onClick = {}, label = { Text("吧龄 ${user.tiebaAge} 年") })
            if (user.address.isNotBlank()) AssistChip(onClick = {}, label = { Text(user.address) })
        }
    }
}

@Composable
private fun UserStat(label: String, value: Int, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value.toString(), fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelMedium, color = CcitColors.InkMuted)
    }
}

@Composable
private fun UserPostItem(post: TiebaUserPost, onClick: (TiebaUserPost) -> Unit) {
    Column(Modifier.fillMaxWidth().clickable { onClick(post) }.padding(horizontal = 16.dp, vertical = 14.dp)) {
        Text(post.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (post.excerpt.isNotBlank()) {
            Text(post.excerpt, Modifier.padding(top = 8.dp), maxLines = 5, overflow = TextOverflow.Ellipsis)
        }
        if (post.imageUrls.isNotEmpty()) {
            val images = post.imageUrls.take(3)
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                images.forEach { url ->
                    TiebaAsyncImage(
                        url,
                        null,
                        Modifier.weight(1f).height(88.dp).clip(MaterialTheme.shapes.small),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
        }
        Text(
            listOf(post.forumName.takeIf(String::isNotBlank)?.let { "$it 吧" }, post.time.takeIf(String::isNotBlank))
                .filterNotNull().joinToString(" · "),
            Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.labelSmall,
            color = CcitColors.InkMuted,
        )
    }
    HorizontalDivider()
}

@Composable
private fun UserForumList(user: TiebaUserProfile, modifier: Modifier = Modifier) {
    when {
        user.followedForumsPrivate -> Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Text("该用户隐藏了关注的吧") }
        user.followedForums.isEmpty() -> Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Text("暂无公开的关注吧") }
        else -> LazyColumn(modifier.fillMaxWidth()) {
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
private fun FullImageScreen(url: String, onBack: () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    val transform = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 6f)
        offsetX += pan.x
        offsetY += pan.y
    }
    BackHandler(onBack = onBack)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        TiebaAsyncImage(
            url = url,
            contentDescription = "原图",
            modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = scale; scaleY = scale; translationX = offsetX; translationY = offsetY }
                .transformable(transform),
            contentScale = ContentScale.Fit,
        )
        IconButton(onBack, Modifier.align(Alignment.TopStart).padding(8.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
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

private fun FloorSort.label(): String = when (this) {
    FloorSort.ASCENDING -> "正序"
    FloorSort.DESCENDING -> "倒序"
    FloorSort.HOT -> "热门"
}

private fun isToday(timestamp: Long?): Boolean = timestamp?.let {
    Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() == java.time.LocalDate.now()
} ?: false
