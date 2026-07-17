package edu.ccit.webvpn

import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import dagger.hilt.android.AndroidEntryPoint
import edu.ccit.webvpn.core.ui.CcitCard
import edu.ccit.webvpn.core.ui.CcitOutlinedButton
import edu.ccit.webvpn.core.ui.CcitColors
import edu.ccit.webvpn.core.ui.CcitPrimaryButton
import edu.ccit.webvpn.core.ui.CcitAcademicTheme
import edu.ccit.webvpn.core.ui.CcitPalette
import edu.ccit.webvpn.core.ui.ccitBackground
import edu.ccit.webvpn.core.ui.ccitTextFieldColors
import edu.ccit.webvpn.core.webvpn.CaptchaData
import edu.ccit.webvpn.core.webvpn.RequiredAccountAction
import edu.ccit.webvpn.settings.AppearanceState
import edu.ccit.webvpn.settings.AppearanceViewModel
import edu.ccit.webvpn.settings.DarkPreference
import edu.ccit.webvpn.settings.Theme
import edu.ccit.webvpn.feature.tieba.TiebaRuntime
import kotlinx.coroutines.delay

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val appearanceViewModel: AppearanceViewModel = viewModel()
            val appearance by appearanceViewModel.state.collectAsStateWithLifecycle()
            val darkTheme = when (appearance.ui.darkPreference) {
                DarkPreference.FOLLOW_SYSTEM -> isSystemInDarkTheme()
                DarkPreference.ALWAYS -> true
                DarkPreference.DISABLED -> false
            }
            CcitAcademicTheme(
                palette = appearance.theme.theme.toPalette(),
                customSeedColor = appearance.theme.customColor,
                customVariant = appearance.theme.customVariant,
                darkTheme = darkTheme,
                amoled = appearance.ui.darkAmoled,
                reduceMotion = appearance.ui.reduceEffect,
            ) {
                val webVpnViewModel: WebVpnViewModel = viewModel()
                val academicViewModel: AcademicViewModel = viewModel()
                WebVpnApp(
                    viewModel = webVpnViewModel,
                    academicViewModel = academicViewModel,
                    appearance = appearance,
                    appearanceViewModel = appearanceViewModel,
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        TiebaRuntime.get(this).onAppForegrounded()
    }
}

private fun Theme.toPalette(): CcitPalette = when (this) {
    Theme.BLUE -> CcitPalette.Blue
    Theme.GREEN -> CcitPalette.Green
    Theme.ORANGE -> CcitPalette.Orange
    Theme.PINK -> CcitPalette.Pink
    Theme.PURPLE -> CcitPalette.Purple
    Theme.DYNAMIC -> CcitPalette.Dynamic
    Theme.CUSTOM -> CcitPalette.Custom
}

@Composable
private fun WebVpnApp(
    viewModel: WebVpnViewModel,
    academicViewModel: AcademicViewModel,
    appearance: AppearanceState,
    appearanceViewModel: AppearanceViewModel,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val academicState by academicViewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewModel.onAppForegrounded()
                Lifecycle.Event.ON_STOP -> viewModel.onAppBackgrounded()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            viewModel.onAppForegrounded()
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.onAppBackgrounded()
        }
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    LaunchedEffect(academicState.message) {
        academicState.message?.let {
            snackbarHostState.showSnackbar(it)
            academicViewModel.consumeMessage()
        }
    }

    LaunchedEffect(
        state.loginResult?.userInfo?.username,
        state.loginResult?.requiredAction,
    ) {
        val result = state.loginResult
        if (result != null && result.requiredAction == RequiredAccountAction.None) {
            academicViewModel.onWebVpnReady(result.userInfo.username.orEmpty())
        } else {
            academicViewModel.onWebVpnCleared()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().ccitBackground(),
        containerColor = CcitColors.Shell,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            AuthenticatedApp(
                result = state.loginResult,
                webVpnState = state,
                loggingOut = state.submitting,
                checkingSession = state.checkingSession,
                academicState = academicState,
                onRefreshWebVpnCaptcha = viewModel::refreshCaptcha,
                onWebVpnLogin = viewModel::login,
                onSelectSavedWebVpnAccount = viewModel::selectSavedAccount,
                onForgetSavedWebVpnAccount = viewModel::forgetSavedAccount,
                onUseManualWebVpnCredentials = viewModel::useManualCredentials,
                onRefreshAcademicCaptcha = academicViewModel::refreshCaptcha,
                onAcademicLogin = academicViewModel::login,
                onSelectSavedAcademicAccount = academicViewModel::selectSavedAccount,
                onForgetSavedAcademicAccount = academicViewModel::forgetSavedAccount,
                onUseManualAcademicCredentials = academicViewModel::useManualCredentials,
                onSelectAcademicTerm = academicViewModel::selectTerm,
                onBestOnlyChanged = academicViewModel::setBestOnly,
                onQueryGrades = academicViewModel::queryGrades,
                onQueryTimetable = academicViewModel::queryTimetable,
                onSelectCourseSelectionTerm = academicViewModel::selectCourseSelectionTerm,
                onQueryCourseSelection = academicViewModel::queryCourseSelection,
                onLoadEvaluationBatches = academicViewModel::loadEvaluationBatches,
                onOpenEvaluationBatch = academicViewModel::openEvaluationBatch,
                onCloseEvaluationBatch = academicViewModel::closeEvaluationBatch,
                onOpenEvaluationCourse = academicViewModel::openEvaluationCourse,
                onCloseEvaluationForm = academicViewModel::closeEvaluationForm,
                onSaveEvaluation = academicViewModel::saveEvaluation,
                onAcademicLogout = academicViewModel::logout,
                onLogout = viewModel::logout,
                appearance = appearance,
                appearanceViewModel = appearanceViewModel,
            )
        }
    }
}

@Composable
internal fun WebVpnLoginScreen(
    state: WebVpnUiState,
    onRefreshCaptcha: () -> Unit,
    onLogin: (String, String, String, Boolean) -> Unit,
    onSelectSavedAccount: (String) -> Unit,
    onForgetSavedAccount: (String) -> Unit,
    onUseManualCredentials: () -> Unit,
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var rememberPassword by remember { mutableStateOf(false) }
    var captchaCode by remember(state.captcha?.id) { mutableStateOf("") }
    var captchaEdited by remember(state.captcha?.id) { mutableStateOf(false) }
    val configuration = state.configuration
    val usingSavedPassword = state.selectedSavedUsername != null
    var contentVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(40)
        contentVisible = true
    }

    LaunchedEffect(state.selectedSavedUsername) {
        state.selectedSavedUsername?.let { selected ->
            username = selected
            password = ""
            rememberPassword = false
        }
    }

    LaunchedEffect(state.captcha?.id, state.recognizedCaptchaCode) {
        if (!captchaEdited && state.recognizedCaptchaCode.isNotBlank()) {
            captchaCode = state.recognizedCaptchaCode
        }
    }

    AnimatedVisibility(
        visible = contentVisible,
        enter = fadeIn(tween(320)) + slideInVertically(tween(360)) { it / 18 },
    ) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 30.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.VpnKey,
                contentDescription = null,
                modifier = Modifier.size(34.dp),
                tint = CcitColors.Brown,
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Cithub", style = MaterialTheme.typography.headlineLarge)
                Text("长春工程学院 · WebVPN 统一入口", color = CcitColors.InkMuted)
            }
        }

        CcitCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (state.savedAccounts.isNotEmpty()) {
                    Text("已保存账号", style = MaterialTheme.typography.titleMedium)
                    state.savedAccounts.forEach { account ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CcitOutlinedButton(
                                onClick = { onSelectSavedAccount(account.username) },
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(
                                    if (state.selectedSavedUsername == account.username) {
                                        Icons.Default.CheckCircle
                                    } else {
                                        Icons.Default.Person
                                    },
                                    contentDescription = null,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(account.username)
                            }
                            IconButton(onClick = { onForgetSavedAccount(account.username) }) {
                                Icon(Icons.Default.Delete, contentDescription = "删除已保存账号")
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it.trim() },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("用户名（学号或工号）") },
                    singleLine = true,
                    enabled = !usingSavedPassword,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    colors = ccitTextFieldColors(),
                )

                AnimatedContent(
                    targetState = usingSavedPassword,
                    modifier = Modifier.animateContentSize(spring(stiffness = 600f)),
                    transitionSpec = {
                        (fadeIn(tween(200)) + slideInVertically(tween(240)) { it / 8 }) togetherWith
                            fadeOut(tween(130))
                    },
                    label = "saved credentials",
                ) { savedPassword ->
                if (savedPassword) {
                    CcitCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = CcitColors.Success,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("已使用本机加密保存的密码")
                            }
                            Text(
                                if (state.captchaAutofillEnabled) {
                                    "验证码将自动填写。"
                                } else {
                                    "请填写本次验证码。"
                                },
                                color = CcitColors.InkMuted,
                            )
                            CcitOutlinedButton(
                                onClick = onUseManualCredentials,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("重新输入账号密码")
                            }
                        }
                    }
                } else if (configuration?.requiresPassword != false) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("密码") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Next,
                        ),
                        colors = ccitTextFieldColors(),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { rememberPassword = !rememberPassword },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = rememberPassword,
                            onCheckedChange = { rememberPassword = it },
                        )
                        Text("在本机加密保存此账号和密码")
                    }
                    }
                }
                }

                if (configuration?.requiresGraphCaptcha == true) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = captchaCode,
                            onValueChange = {
                                captchaEdited = true
                                captchaCode = it.trim()
                            },
                            modifier = Modifier.weight(1f),
                            label = { Text("验证码") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    onLogin(username, password, captchaCode, rememberPassword)
                                },
                            ),
                            colors = ccitTextFieldColors(),
                        )
                        Spacer(Modifier.width(10.dp))
                        CaptchaImage(
                            state.captcha,
                            state.loadingCaptcha || state.recognizingCaptcha,
                        )
                        IconButton(
                            onClick = onRefreshCaptcha,
                            enabled = !state.loadingCaptcha && !state.recognizingCaptcha,
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "刷新验证码")
                        }
                    }
                }

                CcitPrimaryButton(
                    onClick = { onLogin(username, password, captchaCode, rememberPassword) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = configuration != null &&
                        !state.submitting &&
                        username.isNotBlank() &&
                        (!configuration.requiresPassword || usingSavedPassword || password.isNotBlank()) &&
                        (!configuration.requiresGraphCaptcha ||
                            (state.captcha != null && captchaCode.isNotBlank())),
                ) {
                    Crossfade(targetState = state.submitting, animationSpec = tween(160), label = "webvpn submit") { submitting ->
                        if (submitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = CcitColors.Surface,
                            )
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Lock, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(if (configuration == null) "正在加载配置…" else "登录")
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
private fun CaptchaImage(captcha: CaptchaData?, loading: Boolean) {
    val imageBitmap = remember(captcha?.captcha) {
        runCatching {
            captcha?.captcha
                ?.substringAfter("base64,", missingDelimiterValue = "")
                ?.takeIf(String::isNotBlank)
                ?.let { Base64.decode(it, Base64.DEFAULT) }
                ?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
                ?.asImageBitmap()
        }.getOrNull()
    }

    CcitCard(modifier = Modifier.size(width = 112.dp, height = 50.dp)) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Crossfade(
                targetState = when {
                    loading -> 0
                    imageBitmap == null -> 1
                    else -> 2
                },
                animationSpec = tween(180),
                label = "captcha",
            ) { captchaState ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    when (captchaState) {
                        0 -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        1 -> Text("点击刷新", color = CcitColors.InkMuted)
                        else -> imageBitmap?.let {
                            Image(bitmap = it, contentDescription = "图形验证码")
                        }
                    }
                }
            }
        }
    }
}
