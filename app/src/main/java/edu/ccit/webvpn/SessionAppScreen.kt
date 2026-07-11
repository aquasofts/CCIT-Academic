package edu.ccit.webvpn

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import androidx.compose.ui.viewinterop.AndroidView
import edu.ccit.webvpn.core.ui.WebVpnCard
import edu.ccit.webvpn.core.ui.WebVpnColors
import edu.ccit.webvpn.core.webvpn.LoginResult
import edu.ccit.webvpn.core.webvpn.RequiredAccountAction
import kotlinx.coroutines.CancellationException
import kotlin.math.roundToInt

private enum class MainTab(val label: String) {
    Favorites("收藏"),
    Academic("教务系统"),
    Mine("我的"),
}

@Composable
fun AuthenticatedApp(
    result: LoginResult,
    loggingOut: Boolean,
    checkingSession: Boolean,
    academicState: AcademicUiState,
    onRefreshAcademicCaptcha: () -> Unit,
    onAcademicLogin: (String, String, String, Boolean) -> Unit,
    onSelectSavedAcademicAccount: (String) -> Unit,
    onForgetSavedAcademicAccount: (String) -> Unit,
    onUseManualAcademicCredentials: () -> Unit,
    onSelectAcademicTerm: (String) -> Unit,
    onBestOnlyChanged: (Boolean) -> Unit,
    onQueryGrades: () -> Unit,
    onQueryTimetable: (String?) -> Unit,
    onAcademicLogout: () -> Unit,
    onLogout: () -> Unit,
) {
    val context = LocalContext.current
    val preferences = remember { AcademicFeaturePreferences(context) }
    val featureOrder = remember { mutableStateListOf<AcademicFeature>().apply { addAll(preferences.loadOrder()) } }
    val favorites = remember { mutableStateListOf<String>().apply { addAll(preferences.loadFavorites()) } }
    var selectedTab by remember { mutableStateOf(MainTab.Academic) }
    var openedFeature by remember { mutableStateOf<AcademicFeature?>(null) }
    var arranging by remember { mutableStateOf(false) }
    var predictiveBackProgress by remember { mutableFloatStateOf(0f) }

    PredictiveBackHandler(enabled = openedFeature != null) { progress ->
        try {
            progress.collect { event -> predictiveBackProgress = event.progress }
            openedFeature = null
        } catch (_: CancellationException) {
            // A cancelled gesture leaves the user on the current feature.
        } finally {
            predictiveBackProgress = 0f
        }
    }

    fun toggleFavorite(feature: AcademicFeature) {
        if (feature.id in favorites) favorites.remove(feature.id) else favorites.add(feature.id)
        preferences.saveFavorites(favorites.toSet())
    }

    fun openFeature(feature: AcademicFeature) {
        if (!academicState.loggedIn) {
            selectedTab = MainTab.Academic
            return
        }
        arranging = false
        openedFeature = feature
        selectedTab = MainTab.Academic
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (selectedTab) {
                MainTab.Favorites -> FavoritesScreen(
                    features = featureOrder.filter { it.id in favorites },
                    favorites = favorites.toSet(),
                    onOpen = ::openFeature,
                    onToggleFavorite = ::toggleFavorite,
                )
                MainTab.Academic -> AcademicHomeScreen(
                    state = academicState,
                    featureOrder = featureOrder,
                    favorites = favorites.toSet(),
                    openedFeature = openedFeature,
                    predictiveBackProgress = predictiveBackProgress,
                    arranging = arranging,
                    onToggleArranging = { arranging = !arranging },
                    onBack = { openedFeature = null },
                    onOpen = ::openFeature,
                    onToggleFavorite = ::toggleFavorite,
                    onMove = { from, to ->
                        if (from != to && from in featureOrder.indices && to in featureOrder.indices) {
                            featureOrder.add(to, featureOrder.removeAt(from))
                            preferences.saveOrder(featureOrder)
                        }
                    },
                    onRefreshCaptcha = onRefreshAcademicCaptcha,
                    onLogin = onAcademicLogin,
                    onSelectSavedAccount = onSelectSavedAcademicAccount,
                    onForgetSavedAccount = onForgetSavedAcademicAccount,
                    onUseManualCredentials = onUseManualAcademicCredentials,
                    onSelectTerm = onSelectAcademicTerm,
                    onBestOnlyChanged = onBestOnlyChanged,
                    onQueryGrades = onQueryGrades,
                    onQueryTimetable = onQueryTimetable,
                )
                MainTab.Mine -> MineScreen(
                    result = result,
                    academicState = academicState,
                    loggingOut = loggingOut,
                    checkingSession = checkingSession,
                    onAcademicLogout = {
                        CookieManager.getInstance().removeAllCookies(null)
                        onAcademicLogout()
                    },
                    onLogout = {
                        CookieManager.getInstance().removeAllCookies(null)
                        onLogout()
                    },
                )
            }
        }
        NavigationBar(containerColor = WebVpnColors.Surface) {
            MainTab.entries.forEach { tab ->
                NavigationBarItem(
                    selected = selectedTab == tab,
                    onClick = {
                        selectedTab = tab
                        if (tab != MainTab.Academic) openedFeature = null
                        arranging = false
                    },
                    icon = {
                        Icon(
                            when (tab) {
                                MainTab.Favorites -> Icons.Default.Favorite
                                MainTab.Academic -> Icons.Default.School
                                MainTab.Mine -> Icons.Default.AccountCircle
                            },
                            contentDescription = tab.label,
                        )
                    },
                    label = { Text(tab.label) },
                )
            }
        }
    }
}

@Composable
private fun FavoritesScreen(
    features: List<AcademicFeature>,
    favorites: Set<String>,
    onOpen: (AcademicFeature) -> Unit,
    onToggleFavorite: (AcademicFeature) -> Unit,
) {
    FeatureListScreen(
        title = "收藏",
        subtitle = if (features.isEmpty()) "长按教务功能即可添加到这里" else "快速打开常用功能",
        features = features,
        favorites = favorites,
        arranging = false,
        onOpen = onOpen,
        onToggleFavorite = onToggleFavorite,
        onMove = { _, _ -> },
    )
}

@Composable
private fun AcademicHomeScreen(
    state: AcademicUiState,
    featureOrder: List<AcademicFeature>,
    favorites: Set<String>,
    openedFeature: AcademicFeature?,
    predictiveBackProgress: Float,
    arranging: Boolean,
    onToggleArranging: () -> Unit,
    onBack: () -> Unit,
    onOpen: (AcademicFeature) -> Unit,
    onToggleFavorite: (AcademicFeature) -> Unit,
    onMove: (Int, Int) -> Unit,
    onRefreshCaptcha: () -> Unit,
    onLogin: (String, String, String, Boolean) -> Unit,
    onSelectSavedAccount: (String) -> Unit,
    onForgetSavedAccount: (String) -> Unit,
    onUseManualCredentials: () -> Unit,
    onSelectTerm: (String) -> Unit,
    onBestOnlyChanged: (Boolean) -> Unit,
    onQueryGrades: () -> Unit,
    onQueryTimetable: (String?) -> Unit,
) {
    when {
        state.initializing -> CenteredLoading("正在连接教务系统…")
        !state.loggedIn -> LazyColumn(
            modifier = Modifier.fillMaxSize().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text("教务系统", style = MaterialTheme.typography.headlineLarge)
                Text("登录后使用成绩、课表、选课等功能", color = WebVpnColors.InkMuted)
            }
            item {
                AcademicSection(
                    state = state,
                    onRefreshCaptcha = onRefreshCaptcha,
                    onLogin = onLogin,
                    onSelectSavedAccount = onSelectSavedAccount,
                    onForgetSavedAccount = onForgetSavedAccount,
                    onUseManualCredentials = onUseManualCredentials,
                    onSelectTerm = onSelectTerm,
                    onBestOnlyChanged = onBestOnlyChanged,
                    onQueryGrades = onQueryGrades,
                )
            }
        }
        openedFeature == null -> FeatureListScreen(
            title = "教务系统",
            subtitle = "${featureOrder.size} 项学生服务",
            features = featureOrder,
            favorites = favorites,
            arranging = arranging,
            onToggleArranging = onToggleArranging,
            onOpen = onOpen,
            onToggleFavorite = onToggleFavorite,
            onMove = onMove,
        )
        openedFeature == AcademicFeature.Grades -> FeaturePageHeader(
            openedFeature.title,
            onBack,
            predictiveBackProgress,
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    AcademicGradesSection(state, onSelectTerm, onBestOnlyChanged, onQueryGrades)
                }
            }
        }
        openedFeature == AcademicFeature.Timetable -> FeaturePageHeader(
            openedFeature.title,
            onBack,
            predictiveBackProgress,
        ) {
            TimetableScreen(
                timetable = state.timetable,
                loading = state.loadingTimetable,
                onLoad = onQueryTimetable,
            )
        }
        else -> FeaturePageHeader(openedFeature.title, onBack, predictiveBackProgress) {
            AcademicWebPage(openedFeature, state.webViewCookies)
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun FeatureListScreen(
    title: String,
    subtitle: String,
    features: List<AcademicFeature>,
    favorites: Set<String>,
    arranging: Boolean,
    onToggleArranging: (() -> Unit)? = null,
    onOpen: (AcademicFeature) -> Unit,
    onToggleFavorite: (AcademicFeature) -> Unit,
    onMove: (Int, Int) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.headlineLarge)
                    Text(subtitle, color = WebVpnColors.InkMuted)
                }
                if (onToggleArranging != null) {
                    IconButton(onClick = onToggleArranging) {
                        Icon(
                            Icons.Default.GridView,
                            contentDescription = if (arranging) "完成排列" else "排列",
                            tint = if (arranging) WebVpnColors.Success else WebVpnColors.Brown,
                        )
                    }
                }
            }
        }
        items(features, key = { it.id }) { feature ->
            FeatureRow(
                modifier = Modifier.animateItem(placementSpec = tween(durationMillis = 260)),
                feature = feature,
                favorite = feature.id in favorites,
                arranging = arranging,
                index = features.indexOf(feature),
                itemCount = features.size,
                onOpen = { onOpen(feature) },
                onToggleFavorite = { onToggleFavorite(feature) },
                onMove = onMove,
            )
        }
        if (features.isEmpty()) item { EmptyFavoritesCard() }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FeatureRow(
    modifier: Modifier = Modifier,
    feature: AcademicFeature,
    favorite: Boolean,
    arranging: Boolean,
    index: Int,
    itemCount: Int,
    onOpen: () -> Unit,
    onToggleFavorite: () -> Unit,
    onMove: (Int, Int) -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var dragDistance by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    val threshold = with(LocalDensity.current) { 72.dp.toPx() }
    val currentIndex by rememberUpdatedState(index)
    val currentMove by rememberUpdatedState(onMove)
    val settlingOffset by animateFloatAsState(
        targetValue = if (dragging) dragDistance else 0f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 520f),
        label = "drag offset",
    )
    val dragScale by animateFloatAsState(
        targetValue = if (dragging) 1.025f else 1f,
        animationSpec = spring(stiffness = 500f),
        label = "drag scale",
    )
    val cardColor by animateColorAsState(
        targetValue = if (dragging) WebVpnColors.Card else WebVpnColors.Surface,
        label = "drag card color",
    )
    val displayedOffset = if (dragging) dragDistance else settlingOffset
    Box(
        modifier = modifier
            .zIndex(if (dragging) 2f else 0f)
            .offset { IntOffset(0, displayedOffset.roundToInt()) }
            .graphicsLayer { scaleX = dragScale; scaleY = dragScale }
            .shadow(if (dragging) 10.dp else 0.dp, MaterialTheme.shapes.medium),
    ) {
        WebVpnCard(
            modifier = Modifier.fillMaxWidth().combinedClickable(
                enabled = !arranging,
                onClick = onOpen,
                onLongClick = { menuExpanded = true },
            ),
        ) {
            Surface(color = cardColor) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    color = WebVpnColors.CardStrong,
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Icon(
                        feature.icon,
                        contentDescription = null,
                        tint = WebVpnColors.Brown,
                        modifier = Modifier.padding(10.dp).size(24.dp),
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(feature.title, style = MaterialTheme.typography.titleMedium)
                    Text(feature.description, color = WebVpnColors.InkMuted)
                }
                if (favorite && !arranging) {
                    Icon(Icons.Default.Favorite, contentDescription = "已收藏", tint = WebVpnColors.Brown)
                }
                if (arranging) {
                    Icon(
                        Icons.Default.DragHandle,
                        contentDescription = "拖动排列 ${feature.title}",
                        tint = WebVpnColors.Brown,
                        modifier = Modifier
                            .size(40.dp)
                            .padding(8.dp)
                            .pointerInput(feature.id, itemCount) {
                                detectDragGestures(
                                    onDragStart = { dragging = true },
                                    onDragEnd = {
                                        dragging = false
                                        dragDistance = 0f
                                    },
                                    onDragCancel = {
                                        dragging = false
                                        dragDistance = 0f
                                    },
                                ) { change, amount ->
                                    change.consume()
                                    dragDistance += amount.y
                                    val liveIndex = currentIndex
                                    if (dragDistance > threshold && liveIndex < itemCount - 1) {
                                        currentMove(liveIndex, liveIndex + 1)
                                        dragDistance -= threshold
                                    } else if (dragDistance < -threshold && liveIndex > 0) {
                                        currentMove(liveIndex, liveIndex - 1)
                                        dragDistance += threshold
                                    }
                                }
                            },
                    )
                }
            }
            }
        }
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            DropdownMenuItem(
                text = { Text(if (favorite) "取消收藏" else "收藏") },
                leadingIcon = {
                    Icon(
                        if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = null,
                    )
                },
                onClick = {
                    menuExpanded = false
                    onToggleFavorite()
                },
            )
        }
    }
}

@Composable
private fun EmptyFavoritesCard() {
    WebVpnCard(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Default.FavoriteBorder, contentDescription = null, tint = WebVpnColors.Rose)
            Text("还没有收藏功能", style = MaterialTheme.typography.titleMedium)
            Text("前往教务系统，长按功能即可收藏", color = WebVpnColors.InkMuted)
        }
    }
}

@Composable
private fun FeaturePageHeader(
    title: String,
    onBack: () -> Unit,
    predictiveBackProgress: Float,
    content: @Composable () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationX = size.width * predictiveBackProgress * 0.32f
                scaleX = 1f - predictiveBackProgress * 0.035f
                scaleY = 1f - predictiveBackProgress * 0.035f
                alpha = 1f - predictiveBackProgress * 0.08f
            },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text(title, style = MaterialTheme.typography.titleLarge)
        }
        Box(Modifier.weight(1f).fillMaxWidth()) { content() }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun AcademicWebPage(feature: AcademicFeature, cookies: List<String>) {
    val url = "https://http-10-198-47-148-8080.webvpn.ccit.edu.cn/jsxsd/${feature.path}"
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            CookieManager.getInstance().apply {
                setAcceptCookie(true)
                cookies.forEach { setCookie(url, it) }
                flush()
            }
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                webViewClient = WebViewClient()
                loadUrl(url)
            }
        },
        update = { webView ->
            if (webView.url == null) webView.loadUrl(url)
        },
    )
}

@Composable
private fun MineScreen(
    result: LoginResult,
    academicState: AcademicUiState,
    loggingOut: Boolean,
    checkingSession: Boolean,
    onAcademicLogout: () -> Unit,
    onLogout: () -> Unit,
) {
    val info = result.userInfo
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column(Modifier.padding(top = 20.dp, bottom = 4.dp)) {
                Text("我的", style = MaterialTheme.typography.headlineLarge)
                Text("WebVPN 账号与连接信息", color = WebVpnColors.InkMuted)
            }
        }
        item {
            WebVpnCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    InfoRow("连接状态", if (checkingSession) "正在确认" else "已连接")
                    InfoRow("用户名", info.username.orEmpty().ifBlank { "—" })
                    InfoRow("昵称", info.nickname ?: info.fullName ?: "—")
                    InfoRow("用户组", info.groups.joinToString("、").ifBlank { "—" })
                    InfoRow("账号类型", if (info.authType == 0) "本地账号" else "统一认证账号")
                    InfoRow("微信绑定", if (info.bindWechat) "已绑定" else "未绑定")
                    InfoRow("动态口令", if (info.bindOtp) "已绑定" else "未绑定")
                }
            }
        }
        if (result.requiredAction != RequiredAccountAction.None) {
            item {
                WebVpnCard(Modifier.fillMaxWidth()) {
                    Text(
                        when (result.requiredAction) {
                            RequiredAccountAction.CompleteTwoFactorAuthentication -> "请先完成账号二次认证"
                            RequiredAccountAction.ChangePassword -> "请先修改初始密码"
                            RequiredAccountAction.BindLocalAccount -> "请先绑定本地账号"
                            RequiredAccountAction.None -> ""
                        },
                        modifier = Modifier.padding(18.dp),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
        if (academicState.loggedIn) {
            item {
                OutlinedButton(
                    onClick = onAcademicLogout,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    enabled = !academicState.submitting && !loggingOut,
                ) {
                    if (academicState.submitting) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("退出教务系统")
                    }
                }
            }
        }
        item {
            OutlinedButton(
                onClick = onLogout,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = !loggingOut,
            ) {
                if (loggingOut) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("退出 WebVPN")
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = WebVpnColors.InkMuted)
        Spacer(Modifier.width(20.dp))
        Text(value)
    }
}

@Composable
private fun CenteredLoading(message: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(12.dp))
        Text(message, color = WebVpnColors.InkMuted)
    }
}
