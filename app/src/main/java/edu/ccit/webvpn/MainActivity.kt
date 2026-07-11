package edu.ccit.webvpn

import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
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
import androidx.compose.material3.OutlinedButton
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
import edu.ccit.webvpn.core.academic.AcademicRepository
import edu.ccit.webvpn.core.ui.WebVpnCard
import edu.ccit.webvpn.core.ui.WebVpnColors
import edu.ccit.webvpn.core.ui.WebVpnPrimaryButton
import edu.ccit.webvpn.core.ui.WebVpnTheme
import edu.ccit.webvpn.core.ui.webVpnBackground
import edu.ccit.webvpn.core.ui.webVpnTextFieldColors
import edu.ccit.webvpn.core.webvpn.CaptchaData
import edu.ccit.webvpn.core.webvpn.LoginResult
import edu.ccit.webvpn.core.webvpn.RequiredAccountAction
import edu.ccit.webvpn.core.webvpn.WebVpnAuthRepository
import edu.ccit.webvpn.core.webvpn.WebVpnCookieJar
import edu.ccit.webvpn.core.webvpn.WebVpnNetwork
import edu.ccit.webvpn.core.webvpn.WebVpnSessionManager

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sessionManager = WebVpnSessionManager(applicationContext)
        val cookieJar = WebVpnCookieJar()
        val client = WebVpnNetwork.createClient(
            cookieJar = cookieJar,
        )
        val repository = WebVpnAuthRepository(
            api = WebVpnNetwork.createApi(client),
            sessionStore = sessionManager,
            cookieJar = cookieJar,
        )
        val academicRepository = AcademicRepository(client, sessionManager, cookieJar)

        setContent {
            WebVpnTheme {
                val webVpnViewModel: WebVpnViewModel = viewModel(
                    factory = WebVpnViewModel.Factory(repository, sessionManager),
                )
                val academicViewModel: AcademicViewModel = viewModel(
                    factory = AcademicViewModel.Factory(academicRepository, sessionManager),
                )
                WebVpnApp(webVpnViewModel, academicViewModel)
            }
        }
    }
}

@Composable
private fun WebVpnApp(
    viewModel: WebVpnViewModel,
    academicViewModel: AcademicViewModel,
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
        modifier = Modifier.fillMaxSize().webVpnBackground(),
        containerColor = WebVpnColors.Shell,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            when {
                state.initializing -> LoadingScreen("正在验证登录状态…")
                state.loginResult != null -> AuthenticatedApp(
                    result = state.loginResult!!,
                    loggingOut = state.submitting,
                    checkingSession = state.checkingSession,
                    academicState = academicState,
                    onRefreshAcademicCaptcha = academicViewModel::refreshCaptcha,
                    onAcademicLogin = academicViewModel::login,
                    onSelectSavedAcademicAccount = academicViewModel::selectSavedAccount,
                    onForgetSavedAcademicAccount = academicViewModel::forgetSavedAccount,
                    onUseManualAcademicCredentials = academicViewModel::useManualCredentials,
                    onSelectAcademicTerm = academicViewModel::selectTerm,
                    onBestOnlyChanged = academicViewModel::setBestOnly,
                    onQueryGrades = academicViewModel::queryGrades,
                    onQueryTimetable = academicViewModel::queryTimetable,
                    onAcademicLogout = academicViewModel::logout,
                    onLogout = viewModel::logout,
                )
                else -> LoginScreen(
                    state = state,
                    onRefreshCaptcha = viewModel::refreshCaptcha,
                    onLogin = viewModel::login,
                    onSelectSavedAccount = viewModel::selectSavedAccount,
                    onForgetSavedAccount = viewModel::forgetSavedAccount,
                    onUseManualCredentials = viewModel::useManualCredentials,
                )
            }
        }
    }
}

@Composable
private fun LoginScreen(
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
    val configuration = state.configuration
    val usingSavedPassword = state.selectedSavedUsername != null

    LaunchedEffect(state.selectedSavedUsername) {
        state.selectedSavedUsername?.let { selected ->
            username = selected
            password = ""
            rememberPassword = false
        }
    }

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
                tint = WebVpnColors.Brown,
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text("WebVPN", style = MaterialTheme.typography.headlineLarge)
                Text("长春工程学院统一入口", color = WebVpnColors.InkMuted)
            }
        }

        WebVpnCard(modifier = Modifier.fillMaxWidth()) {
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
                            OutlinedButton(
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
                    colors = webVpnTextFieldColors(),
                )

                if (usingSavedPassword) {
                    WebVpnCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = WebVpnColors.Success,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("已使用本机加密保存的密码")
                            }
                            Text("只需填写本次验证码即可登录。", color = WebVpnColors.InkMuted)
                            OutlinedButton(
                                onClick = onUseManualCredentials,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("重新输入账号密码")
                            }
                        }
                    }
                } else if (configuration?.requiresPassword != false) {
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
                        colors = webVpnTextFieldColors(),
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

                if (configuration?.requiresGraphCaptcha == true) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = captchaCode,
                            onValueChange = { captchaCode = it.trim() },
                            modifier = Modifier.weight(1f),
                            label = { Text("验证码") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    onLogin(username, password, captchaCode, rememberPassword)
                                },
                            ),
                            colors = webVpnTextFieldColors(),
                        )
                        Spacer(Modifier.width(10.dp))
                        CaptchaImage(state.captcha, state.loadingCaptcha)
                        IconButton(onClick = onRefreshCaptcha, enabled = !state.loadingCaptcha) {
                            Icon(Icons.Default.Refresh, contentDescription = "刷新验证码")
                        }
                    }
                }

                WebVpnPrimaryButton(
                    onClick = { onLogin(username, password, captchaCode, rememberPassword) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = configuration != null &&
                        !state.submitting &&
                        username.isNotBlank() &&
                        (!configuration.requiresPassword || usingSavedPassword || password.isNotBlank()) &&
                        (!configuration.requiresGraphCaptcha ||
                            (state.captcha != null && captchaCode.isNotBlank())),
                ) {
                    if (state.submitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = WebVpnColors.Surface,
                        )
                    } else {
                        Icon(Icons.Default.Lock, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (configuration == null) "正在加载配置…" else "登录")
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

    WebVpnCard(modifier = Modifier.size(width = 112.dp, height = 50.dp)) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            when {
                loading -> CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                imageBitmap == null -> Text("点击刷新", color = WebVpnColors.InkMuted)
                else -> Image(bitmap = imageBitmap, contentDescription = "图形验证码")
            }
        }
    }
}

@Composable
private fun LoadingScreen(message: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator()
        Spacer(Modifier.height(14.dp))
        Text(message, color = WebVpnColors.InkMuted)
    }
}
