package edu.ccit.webvpn

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import edu.ccit.webvpn.core.ui.CcitCard
import edu.ccit.webvpn.core.ui.CcitColors
import edu.ccit.webvpn.core.ui.ccitBackground
import edu.ccit.webvpn.core.ui.CcitOutlinedButton
import edu.ccit.webvpn.core.academic.EvaluationAnswer
import edu.ccit.webvpn.core.webvpn.LoginResult
import edu.ccit.webvpn.core.webvpn.RequiredAccountAction
import edu.ccit.webvpn.settings.AppearanceSettingsScreen
import edu.ccit.webvpn.settings.AppearanceState
import edu.ccit.webvpn.settings.AppearanceViewModel
import edu.ccit.webvpn.settings.AcademicFeatureSettings
import edu.ccit.webvpn.settings.NavigationLabel
import edu.ccit.webvpn.feature.tieba.ui.TiebaAccountCard
import edu.ccit.webvpn.feature.tieba.ui.TiebaLoginScreen
import edu.ccit.webvpn.feature.tieba.ui.TiebaRootScreen
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private enum class MainTab(val label: String) {
    Tieba("贴吧"),
    Academic("教务系统"),
    Mine("我的"),
}

private const val AcademicHomeRoute = "academic_home"
private const val AcademicFeatureRoute = "academic_feature"
private const val FeatureIdArgument = "featureId"
private const val MainContentRoute = "main_content"
private const val AppearanceSettingsRoute = "appearance_settings"
private const val TiebaLoginRoute = "tieba_login"

private fun academicFeatureRoute(feature: AcademicFeature): String =
    "$AcademicFeatureRoute/${feature.id}"

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
    onSelectCourseSelectionTerm: (String) -> Unit,
    onQueryCourseSelection: () -> Unit,
    onLoadEvaluationBatches: () -> Unit,
    onOpenEvaluationBatch: (String) -> Unit,
    onCloseEvaluationBatch: () -> Unit,
    onOpenEvaluationCourse: (String) -> Unit,
    onCloseEvaluationForm: () -> Unit,
    onSaveEvaluation: (List<EvaluationAnswer>, String, Boolean) -> Unit,
    onAcademicLogout: () -> Unit,
    onLogout: () -> Unit,
    appearance: AppearanceState,
    appearanceViewModel: AppearanceViewModel,
) {
    val storedFeatures by appearanceViewModel.academicFeatureSettings.collectAsStateWithLifecycle(
        initialValue = AcademicFeatureSettings(),
    )
    val resolvedOrder = remember(storedFeatures.orderIds) {
        val saved = storedFeatures.orderIds.mapNotNull(AcademicFeature::fromId).distinct()
        saved + AcademicFeature.defaults.filterNot(saved::contains)
    }
    val featureOrder = remember { mutableStateListOf<AcademicFeature>() }
    var selectedTab by remember { mutableStateOf(MainTab.Academic) }
    var arranging by remember { mutableStateOf(false) }
    val pagerState = rememberPagerState(
        initialPage = MainTab.Academic.ordinal,
        pageCount = { MainTab.entries.size },
    )
    val academicNavController = rememberNavController()
    val rootNavController = rememberNavController()

    LaunchedEffect(resolvedOrder, arranging) {
        if (!arranging && featureOrder != resolvedOrder) {
            featureOrder.clear()
            featureOrder.addAll(resolvedOrder)
        }
    }

    LaunchedEffect(selectedTab) {
        pagerState.animateScrollToPage(
            page = selectedTab.ordinal,
            animationSpec = if (appearance.ui.reduceEffect) {
                tween(180, easing = FastOutSlowInEasing)
            } else {
                spring(dampingRatio = 0.9f, stiffness = 700f)
            },
        )
        if (selectedTab != MainTab.Academic && academicNavController.currentDestination != null) {
            academicNavController.popBackStack(AcademicHomeRoute, inclusive = false)
        }
    }

    LaunchedEffect(academicState.loggedIn) {
        if (!academicState.loggedIn && academicNavController.currentDestination != null) {
            academicNavController.popBackStack(AcademicHomeRoute, inclusive = false)
        }
    }

    fun openFeature(feature: AcademicFeature) {
        if (!academicState.loggedIn) {
            selectedTab = MainTab.Academic
            return
        }
        arranging = false
        selectedTab = MainTab.Academic
        academicNavController.navigate(academicFeatureRoute(feature)) {
            launchSingleTop = true
        }
    }

    NavHost(
        navController = rootNavController,
        startDestination = MainContentRoute,
        modifier = Modifier.fillMaxSize(),
        enterTransition = {
            scaleIn(
                tween(if (appearance.ui.reduceEffect) 120 else 300, easing = FastOutSlowInEasing),
                initialScale = if (appearance.ui.reduceEffect) 1f else 0.9f,
            ) + fadeIn(tween(if (appearance.ui.reduceEffect) 120 else 300))
        },
        exitTransition = {
            scaleOut(
                tween(if (appearance.ui.reduceEffect) 120 else 300, easing = FastOutSlowInEasing),
                targetScale = if (appearance.ui.reduceEffect) 1f else 1.1f,
            ) + fadeOut(tween(if (appearance.ui.reduceEffect) 120 else 300))
        },
        popEnterTransition = {
            scaleIn(
                tween(if (appearance.ui.reduceEffect) 120 else 300, easing = FastOutSlowInEasing),
                initialScale = if (appearance.ui.reduceEffect) 1f else 1.1f,
            ) + fadeIn(tween(if (appearance.ui.reduceEffect) 120 else 300))
        },
        popExitTransition = {
            scaleOut(
                tween(if (appearance.ui.reduceEffect) 120 else 300, easing = FastOutSlowInEasing),
                targetScale = if (appearance.ui.reduceEffect) 1f else 0.9f,
            ) + fadeOut(tween(if (appearance.ui.reduceEffect) 120 else 300))
        },
    ) {
        composable(MainContentRoute) {
    Column(Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            userScrollEnabled = false,
            beyondViewportPageCount = 2,
        ) { page ->
            val tab = MainTab.entries[page]
            Box(Modifier.fillMaxSize().clipToBounds()) {
            when (tab) {
                MainTab.Tieba -> TiebaRootScreen(active = selectedTab == MainTab.Tieba)
                MainTab.Academic -> AcademicHomeScreen(
                    state = academicState,
                    navController = academicNavController,
                    featureOrder = featureOrder,
                    arranging = arranging,
                    onToggleArranging = { arranging = !arranging },
                    onOpen = ::openFeature,
                    onMove = { from, to ->
                        if (from != to && from in featureOrder.indices && to in featureOrder.indices) {
                            featureOrder.add(to, featureOrder.removeAt(from))
                        }
                    },
                    onOrderSettled = {
                        appearanceViewModel.academicFeatureSettings.save {
                            it.copy(orderIds = featureOrder.map(AcademicFeature::id))
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
                    onSelectCourseSelectionTerm = onSelectCourseSelectionTerm,
                    onQueryCourseSelection = onQueryCourseSelection,
                    onLoadEvaluationBatches = onLoadEvaluationBatches,
                    onOpenEvaluationBatch = onOpenEvaluationBatch,
                    onCloseEvaluationBatch = onCloseEvaluationBatch,
                    onOpenEvaluationCourse = onOpenEvaluationCourse,
                    onCloseEvaluationForm = onCloseEvaluationForm,
                    onSaveEvaluation = onSaveEvaluation,
                    reduceMotion = appearance.ui.reduceEffect,
                )
                MainTab.Mine -> MineScreen(
                    result = result,
                    academicState = academicState,
                    loggingOut = loggingOut,
                    checkingSession = checkingSession,
                    onOpenSettings = { rootNavController.navigate(AppearanceSettingsRoute) },
                    onTiebaLogin = { rootNavController.navigate(TiebaLoginRoute) },
                    onAcademicLogout = {
                        academicNavController.popBackStack(AcademicHomeRoute, inclusive = false)
                        clearCookiesForDomains(AcademicCookieDomains)
                        onAcademicLogout()
                    },
                    onLogout = {
                        clearCookiesForDomains(WebVpnCookieDomains)
                        onLogout()
                    },
                )
            }
            }
        }
        MainNavigationBar(
            selectedTab = selectedTab,
            floating = appearance.ui.bottomNavFloating,
            labelMode = appearance.ui.bottomNavLabel,
            reduceMotion = appearance.ui.reduceEffect,
            onSelect = { tab ->
                selectedTab = tab
                arranging = false
            },
        )
    }
        }
        composable(AppearanceSettingsRoute) {
            AppearanceSettingsScreen(
                current = appearance,
                themeSettings = appearanceViewModel.themeSettings,
                uiSettings = appearanceViewModel.uiSettings,
                reduceEffect = appearance.ui.reduceEffect,
                onThemedIconChange = appearanceViewModel::setThemedAppIcon,
                onBack = rootNavController::navigateUp,
            )
        }
        composable(TiebaLoginRoute) {
            TiebaLoginScreen(
                onBack = rootNavController::navigateUp,
                onLoggedIn = rootNavController::navigateUp,
            )
        }
    }
}

@Composable
private fun MainNavigationBar(
    selectedTab: MainTab,
    floating: Boolean,
    labelMode: NavigationLabel,
    reduceMotion: Boolean,
    onSelect: (MainTab) -> Unit,
) {
    val content: @Composable () -> Unit = {
        Row(
            modifier = Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MainTab.entries.forEach { tab ->
                val selected = selectedTab == tab
                val showLabel = labelMode == NavigationLabel.ALWAYS ||
                    (labelMode == NavigationLabel.SELECTED && selected)
                val width by animateDpAsState(
                    targetValue = when {
                        showLabel && selected -> 108.dp
                        showLabel -> 88.dp
                        else -> 52.dp
                    },
                    animationSpec = if (reduceMotion) tween(120) else {
                        spring(dampingRatio = 0.78f, stiffness = 650f)
                    },
                    label = "${tab.name} navigation indicator width",
                )
                val iconScale by animateFloatAsState(
                    targetValue = if (selected && !reduceMotion) 1.12f else 1f,
                    animationSpec = if (reduceMotion) tween(120) else {
                        spring(dampingRatio = 0.78f, stiffness = 650f)
                    },
                    label = "${tab.name} navigation icon",
                )
                Surface(
                    onClick = { onSelect(tab) },
                    modifier = Modifier.width(width).height(52.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        Color.Transparent
                    },
                    contentColor = if (selected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                    Icon(
                        when (tab) {
                            MainTab.Tieba -> Icons.Default.Forum
                            MainTab.Academic -> Icons.Default.School
                            MainTab.Mine -> Icons.Default.AccountCircle
                        },
                        contentDescription = tab.label,
                        modifier = Modifier.graphicsLayer {
                            scaleX = iconScale
                            scaleY = iconScale
                        },
                    )
                        if (showLabel) {
                            Spacer(Modifier.width(7.dp))
                            Text(tab.label, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }
    if (floating) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
        ) {
            content()
        }
    } else {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            content = content,
        )
    }
}

@Composable
private fun AcademicHomeScreen(
    state: AcademicUiState,
    navController: NavHostController,
    featureOrder: List<AcademicFeature>,
    arranging: Boolean,
    onToggleArranging: () -> Unit,
    onOpen: (AcademicFeature) -> Unit,
    onMove: (Int, Int) -> Unit,
    onOrderSettled: () -> Unit,
    onRefreshCaptcha: () -> Unit,
    onLogin: (String, String, String, Boolean) -> Unit,
    onSelectSavedAccount: (String) -> Unit,
    onForgetSavedAccount: (String) -> Unit,
    onUseManualCredentials: () -> Unit,
    onSelectTerm: (String) -> Unit,
    onBestOnlyChanged: (Boolean) -> Unit,
    onQueryGrades: () -> Unit,
    onQueryTimetable: (String?) -> Unit,
    onSelectCourseSelectionTerm: (String) -> Unit,
    onQueryCourseSelection: () -> Unit,
    onLoadEvaluationBatches: () -> Unit,
    onOpenEvaluationBatch: (String) -> Unit,
    onCloseEvaluationBatch: () -> Unit,
    onOpenEvaluationCourse: (String) -> Unit,
    onCloseEvaluationForm: () -> Unit,
    onSaveEvaluation: (List<EvaluationAnswer>, String, Boolean) -> Unit,
    reduceMotion: Boolean,
) {
    when {
        state.initializing -> CenteredLoading("正在连接教务系统…")
        !state.loggedIn -> LazyColumn(
            modifier = Modifier.fillMaxSize().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text("教务系统", style = MaterialTheme.typography.headlineLarge)
                Text("登录后使用成绩、课表、选课等功能", color = CcitColors.InkMuted)
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
        else -> NavHost(
            navController = navController,
            startDestination = AcademicHomeRoute,
            modifier = Modifier.fillMaxSize(),
            enterTransition = {
                scaleIn(
                    tween(if (reduceMotion) 120 else 300, easing = FastOutSlowInEasing),
                    initialScale = if (reduceMotion) 1f else 0.9f,
                ) + fadeIn(tween(if (reduceMotion) 120 else 300))
            },
            exitTransition = {
                scaleOut(
                    tween(if (reduceMotion) 120 else 300, easing = FastOutSlowInEasing),
                    targetScale = if (reduceMotion) 1f else 1.1f,
                ) + fadeOut(tween(if (reduceMotion) 120 else 300))
            },
            popEnterTransition = {
                scaleIn(
                    tween(if (reduceMotion) 120 else 300, easing = FastOutSlowInEasing),
                    initialScale = if (reduceMotion) 1f else 1.1f,
                ) + fadeIn(tween(if (reduceMotion) 120 else 300))
            },
            popExitTransition = {
                scaleOut(
                    tween(if (reduceMotion) 120 else 300, easing = FastOutSlowInEasing),
                    targetScale = if (reduceMotion) 1f else 0.9f,
                ) + fadeOut(tween(if (reduceMotion) 120 else 300))
            },
        ) {
            composable(AcademicHomeRoute) {
                FeatureListScreen(
                    title = "教务系统",
                    subtitle = "${featureOrder.size} 项学生服务",
                    features = featureOrder,
                    arranging = arranging,
                    onToggleArranging = onToggleArranging,
                    onOpen = onOpen,
                    onMove = onMove,
                    onOrderSettled = onOrderSettled,
                )
            }
            composable(
                route = "$AcademicFeatureRoute/{$FeatureIdArgument}",
                arguments = listOf(
                    navArgument(FeatureIdArgument) { type = NavType.StringType },
                ),
            ) { backStackEntry ->
                val feature = AcademicFeature.fromId(
                    backStackEntry.arguments?.getString(FeatureIdArgument).orEmpty(),
                ) ?: return@composable
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = CcitColors.Shell,
                ) {
                    when (feature) {
                        AcademicFeature.Grades -> FeaturePageHeader(
                            feature.title,
                            navController::popBackStack,
                        ) {
                            AcademicGradesScreen(state, onSelectTerm, onBestOnlyChanged, onQueryGrades)
                        }
                        AcademicFeature.Timetable -> FeaturePageHeader(
                            feature.title,
                            navController::popBackStack,
                        ) {
                            TimetableScreen(
                                timetable = state.timetable,
                                loading = state.loadingTimetable,
                                onLoad = onQueryTimetable,
                            )
                        }
                        AcademicFeature.SelectionResults -> FeaturePageHeader(
                            feature.title,
                            navController::popBackStack,
                        ) {
                            CourseSelectionResultsScreen(
                                state = state,
                                onSelectTerm = onSelectCourseSelectionTerm,
                                onQuery = onQueryCourseSelection,
                            )
                        }
                        AcademicFeature.Evaluation -> FeaturePageHeader(
                            feature.title,
                            navController::popBackStack,
                        ) {
                            StudentEvaluationScreen(
                                state = state,
                                reduceMotion = reduceMotion,
                                onLoadBatches = onLoadEvaluationBatches,
                                onOpenBatch = onOpenEvaluationBatch,
                                onCloseBatch = onCloseEvaluationBatch,
                                onOpenCourse = onOpenEvaluationCourse,
                                onCloseForm = onCloseEvaluationForm,
                                onSave = onSaveEvaluation,
                            )
                        }
                        else -> FeaturePageHeader(
                            feature.title,
                            navController::popBackStack,
                        ) {
                            AcademicWebPage(feature, state.webViewCookies)
                        }
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun FeatureListScreen(
    title: String,
    subtitle: String,
    features: List<AcademicFeature>,
    arranging: Boolean,
    onToggleArranging: (() -> Unit)? = null,
    onOpen: (AcademicFeature) -> Unit,
    onMove: (Int, Int) -> Unit,
    onOrderSettled: () -> Unit = {},
) {
    var draggingFeatureId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(arranging) {
        if (!arranging) draggingFeatureId = null
    }
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
                    Text(subtitle, color = CcitColors.InkMuted)
                }
                if (onToggleArranging != null) {
                    IconButton(onClick = onToggleArranging) {
                        Icon(
                            Icons.Default.GridView,
                            contentDescription = if (arranging) "完成排列" else "排列",
                            tint = if (arranging) CcitColors.Success else CcitColors.Brown,
                        )
                    }
                }
            }
        }
        itemsIndexed(features, key = { _, feature -> feature.id }) { index, feature ->
            FeatureRow(
                modifier = Modifier.animateItem(
                    placementSpec = if (draggingFeatureId == feature.id) null else tween(140),
                ),
                feature = feature,
                arranging = arranging,
                index = index,
                itemCount = features.size,
                onOpen = { onOpen(feature) },
                onMove = onMove,
                onDraggingChanged = { dragging ->
                    draggingFeatureId = if (dragging) feature.id else null
                },
                onOrderSettled = onOrderSettled,
            )
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FeatureRow(
    modifier: Modifier = Modifier,
    feature: AcademicFeature,
    arranging: Boolean,
    index: Int,
    itemCount: Int,
    onOpen: () -> Unit,
    onMove: (Int, Int) -> Unit,
    onDraggingChanged: (Boolean) -> Unit,
    onOrderSettled: () -> Unit,
) {
    var dragDistance by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    var itemHeightPx by remember { mutableFloatStateOf(0f) }
    val settleOffset = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()
    val itemSpacingPx = with(LocalDensity.current) { 12.dp.toPx() }
    val currentIndex by rememberUpdatedState(index)
    val currentMove by rememberUpdatedState(onMove)
    val currentItemHeight by rememberUpdatedState(itemHeightPx)
    val dragScale by animateFloatAsState(
        targetValue = if (dragging) 1.025f else 1f,
        animationSpec = spring(dampingRatio = 0.9f, stiffness = 700f),
        label = "drag scale",
    )
    val cardColor by animateColorAsState(
        targetValue = if (dragging) CcitColors.Card else CcitColors.Surface,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "drag card color",
    )
    Box(
        modifier = modifier
            .zIndex(if (dragging) 2f else 0f)
            .offset {
                IntOffset(0, (if (dragging) dragDistance else settleOffset.value).roundToInt())
            }
            .graphicsLayer { scaleX = dragScale; scaleY = dragScale }
            .onGloballyPositioned { itemHeightPx = it.size.height.toFloat() },
    ) {
        CcitCard(
            modifier = Modifier.fillMaxWidth().clickable(enabled = !arranging, onClick = onOpen),
        ) {
            Surface(color = cardColor) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    color = CcitColors.CardStrong,
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Icon(
                        feature.icon,
                        contentDescription = null,
                        tint = CcitColors.Brown,
                        modifier = Modifier.padding(10.dp).size(24.dp),
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(feature.title, style = MaterialTheme.typography.titleMedium)
                    Text(feature.description, color = CcitColors.InkMuted)
                }
                if (arranging) {
                    Icon(
                        Icons.Default.DragHandle,
                        contentDescription = "拖动排列 ${feature.title}",
                        tint = CcitColors.Brown,
                        modifier = Modifier
                            .size(40.dp)
                            .padding(8.dp)
                            .pointerInput(feature.id, itemCount) {
                                detectDragGestures(
                                    onDragStart = {
                                        dragging = true
                                        onDraggingChanged(true)
                                        coroutineScope.launch { settleOffset.snapTo(0f) }
                                    },
                                    onDragEnd = {
                                        val releaseOffset = dragDistance
                                        dragging = false
                                        dragDistance = 0f
                                        onDraggingChanged(false)
                                        onOrderSettled()
                                        coroutineScope.launch {
                                            settleOffset.snapTo(releaseOffset)
                                            settleOffset.animateTo(
                                                0f,
                                                spring(stiffness = Spring.StiffnessVeryLow),
                                            )
                                        }
                                    },
                                    onDragCancel = {
                                        val releaseOffset = dragDistance
                                        dragging = false
                                        dragDistance = 0f
                                        onDraggingChanged(false)
                                        onOrderSettled()
                                        coroutineScope.launch {
                                            settleOffset.snapTo(releaseOffset)
                                            settleOffset.animateTo(
                                                0f,
                                                spring(stiffness = Spring.StiffnessVeryLow),
                                            )
                                        }
                                    },
                                ) { change, amount ->
                                    change.consume()
                                    dragDistance += amount.y
                                    val liveIndex = currentIndex
                                    val itemExtent = (currentItemHeight + itemSpacingPx)
                                        .coerceAtLeast(itemSpacingPx * 2f)
                                    val swapThreshold = itemExtent * 0.55f
                                    if (dragDistance > swapThreshold && liveIndex < itemCount - 1) {
                                        currentMove(liveIndex, liveIndex + 1)
                                        dragDistance -= itemExtent
                                    } else if (dragDistance < -swapThreshold && liveIndex > 0) {
                                        currentMove(liveIndex, liveIndex - 1)
                                        dragDistance += itemExtent
                                    }
                                }
                            },
                    )
                }
            }
            }
        }
    }
}

@Composable
private fun FeaturePageHeader(
    title: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(
        Modifier.fillMaxSize(),
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
    onOpenSettings: () -> Unit,
    onTiebaLogin: () -> Unit,
    onAcademicLogout: () -> Unit,
    onLogout: () -> Unit,
) {
    val info = result.userInfo
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("我的", style = MaterialTheme.typography.headlineLarge)
                    Text("WebVPN 账号与连接信息", color = CcitColors.InkMuted)
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Default.Settings, contentDescription = "设置")
                }
            }
        }
        item {
            TiebaAccountCard(onLogin = onTiebaLogin)
        }
        item {
            CcitCard(Modifier.fillMaxWidth()) {
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
                CcitCard(Modifier.fillMaxWidth()) {
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
                CcitOutlinedButton(
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
            CcitOutlinedButton(
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
        Text(label, color = CcitColors.InkMuted)
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
        Text(message, color = CcitColors.InkMuted)
    }
}

private val AcademicCookieDomains = listOf(
    "https://http-10-198-47-148-8080.webvpn.ccit.edu.cn",
)

private val WebVpnCookieDomains = listOf(
    "https://webvpn.ccit.edu.cn",
)

private fun clearCookiesForDomains(urls: List<String>) {
    val manager = CookieManager.getInstance()
    urls.forEach { url ->
        manager.getCookie(url)?.split(';')?.mapNotNull { cookie ->
            cookie.substringBefore('=').trim().takeIf(String::isNotBlank)
        }?.distinct()?.forEach { name ->
            manager.setCookie(url, "$name=; Max-Age=0; Path=/; Secure; SameSite=Lax")
            manager.setCookie(url, "$name=; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Path=/")
        }
    }
    manager.flush()
}
