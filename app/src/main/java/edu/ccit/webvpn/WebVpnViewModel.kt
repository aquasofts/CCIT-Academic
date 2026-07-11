package edu.ccit.webvpn

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import edu.ccit.webvpn.core.webvpn.CaptchaData
import edu.ccit.webvpn.core.webvpn.LocalLoginConfiguration
import edu.ccit.webvpn.core.webvpn.LoginResult
import edu.ccit.webvpn.core.webvpn.SavedWebVpnAccount
import edu.ccit.webvpn.core.webvpn.WebVpnAuthRepository
import edu.ccit.webvpn.core.webvpn.WebVpnCredentialStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class WebVpnUiState(
    val initializing: Boolean = true,
    val configuration: LocalLoginConfiguration? = null,
    val captcha: CaptchaData? = null,
    val loadingCaptcha: Boolean = false,
    val submitting: Boolean = false,
    val checkingSession: Boolean = false,
    val loginResult: LoginResult? = null,
    val lastSessionCheckedAt: Long? = null,
    val savedAccounts: List<SavedWebVpnAccount> = emptyList(),
    val selectedSavedUsername: String? = null,
    val message: String? = null,
)

class WebVpnViewModel(
    private val repository: WebVpnAuthRepository,
    private val credentialStore: WebVpnCredentialStore,
) : ViewModel() {
    private val _uiState = MutableStateFlow(WebVpnUiState())
    val uiState: StateFlow<WebVpnUiState> = _uiState.asStateFlow()
    private var sessionMonitorJob: Job? = null
    private var appInForeground: Boolean = false

    init {
        initialize()
    }

    fun onAppForegrounded() {
        appInForeground = true
        startSessionMonitor()
    }

    private fun startSessionMonitor() {
        if (!appInForeground) return
        sessionMonitorJob?.cancel()
        sessionMonitorJob = viewModelScope.launch {
            checkSessionIfNeeded(force = false)
            while (isActive) {
                delay(SessionCheckIntervalMs)
                checkSessionIfNeeded(force = true)
            }
        }
    }

    fun onAppBackgrounded() {
        appInForeground = false
        sessionMonitorJob?.cancel()
        sessionMonitorJob = null
    }

    fun refreshCaptcha() {
        if (_uiState.value.loadingCaptcha) return
        viewModelScope.launch { loadCaptcha() }
    }

    fun selectSavedAccount(username: String) {
        if (_uiState.value.savedAccounts.none { it.username == username }) return
        _uiState.update { it.copy(selectedSavedUsername = username) }
    }

    fun useManualCredentials() {
        _uiState.update { it.copy(selectedSavedUsername = null) }
    }

    fun forgetSavedAccount(username: String) {
        viewModelScope.launch {
            runCatching { credentialStore.deleteCredential(username) }
                .onSuccess {
                    val accounts = credentialStore.getSavedAccounts()
                    _uiState.update { state ->
                        state.copy(
                            savedAccounts = accounts,
                            selectedSavedUsername = state.selectedSavedUsername
                                ?.takeIf { selected -> accounts.any { it.username == selected } },
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(message = error.message ?: "删除已保存账号失败") }
                }
        }
    }

    fun login(
        username: String,
        password: String,
        captchaCode: String,
        rememberPassword: Boolean,
    ) {
        val state = _uiState.value
        val configuration = state.configuration ?: return
        if (state.submitting) return

        viewModelScope.launch {
            _uiState.update { it.copy(submitting = true, message = null) }
            val normalizedUsername = username.trim()
            val selectedUsername = state.selectedSavedUsername

            try {
                val resolvedPassword = if (selectedUsername == normalizedUsername) {
                    credentialStore.getSavedPassword(normalizedUsername)
                        ?: error("已保存的账号密码不可用，请重新输入密码")
                } else {
                    password
                }

                val result = repository.login(
                    username = normalizedUsername,
                    password = resolvedPassword,
                    captchaId = state.captcha?.id.orEmpty(),
                    code = captchaCode,
                    configuration = configuration,
                )

                var saveWarning: String? = null
                val shouldSave = rememberPassword || selectedUsername == normalizedUsername
                if (shouldSave) {
                    runCatching { credentialStore.saveCredential(normalizedUsername, resolvedPassword) }
                        .onFailure { saveWarning = "已登录，但账号密码保存失败" }
                }
                val savedAccounts = runCatching { credentialStore.getSavedAccounts() }
                    .getOrDefault(state.savedAccounts)

                _uiState.update {
                    it.copy(
                        submitting = false,
                        loginResult = result,
                        lastSessionCheckedAt = System.currentTimeMillis(),
                        savedAccounts = savedAccounts,
                        selectedSavedUsername = normalizedUsername.takeIf { shouldSave },
                        message = saveWarning,
                    )
                }
                startSessionMonitor()
            } catch (error: Throwable) {
                _uiState.update {
                    it.copy(
                        submitting = false,
                        captcha = null,
                        message = error.message ?: "登录失败",
                    )
                }
                if (configuration.requiresGraphCaptcha) loadCaptcha()
            }
        }
    }

    fun logout() {
        if (_uiState.value.submitting) return
        sessionMonitorJob?.cancel()
        sessionMonitorJob = null
        viewModelScope.launch {
            _uiState.update { it.copy(submitting = true) }
            repository.logout()
            enterLoginState(message = null)
        }
    }

    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }

    private fun initialize() {
        viewModelScope.launch {
            val savedAccounts = runCatching { credentialStore.getSavedAccounts() }
                .getOrDefault(emptyList())
            _uiState.update { it.copy(savedAccounts = savedAccounts) }

            runCatching { repository.restoreSession() }
                .onSuccess { restored ->
                    if (restored != null) {
                        val restoredUsername = restored.userInfo.username
                        _uiState.update {
                            it.copy(
                                initializing = false,
                                loginResult = restored,
                                lastSessionCheckedAt = System.currentTimeMillis(),
                                selectedSavedUsername = restoredUsername
                                    ?.takeIf { username -> savedAccounts.any { it.username == username } },
                            )
                        }
                    } else {
                        enterLoginState(message = null)
                    }
                }
                .onFailure { error ->
                    enterLoginState(error.message ?: "登录状态验证失败")
                }
        }
    }

    private suspend fun checkSessionIfNeeded(force: Boolean) {
        val state = _uiState.value
        if (state.loginResult == null || state.submitting || state.checkingSession) return
        val now = System.currentTimeMillis()
        if (!force && now - (state.lastSessionCheckedAt ?: 0L) < ResumeCheckDebounceMs) return

        _uiState.update { it.copy(checkingSession = true) }
        runCatching { repository.revalidateSession() }
            .onSuccess { result ->
                if (result == null) {
                    enterLoginState("WebVPN 登录已过期，请重新登录")
                } else {
                    _uiState.update {
                        it.copy(
                            checkingSession = false,
                            loginResult = result,
                            lastSessionCheckedAt = System.currentTimeMillis(),
                        )
                    }
                }
            }
            .onFailure { error ->
                // A transient network failure is not proof that the server session expired.
                _uiState.update {
                    it.copy(
                        checkingSession = false,
                        message = error.message ?: "暂时无法验证 WebVPN 登录状态",
                    )
                }
            }
    }

    private suspend fun enterLoginState(message: String?) {
        val previous = _uiState.value
        _uiState.value = WebVpnUiState(
            initializing = false,
            savedAccounts = previous.savedAccounts,
            selectedSavedUsername = previous.selectedSavedUsername,
            message = message,
        )
        loadConfiguration()
    }

    private suspend fun loadConfiguration() {
        runCatching { repository.loadLoginConfiguration() }
            .onSuccess { configuration ->
                _uiState.update { it.copy(configuration = configuration) }
                if (configuration.requiresGraphCaptcha) loadCaptcha()
            }
            .onFailure { error ->
                _uiState.update {
                    it.copy(message = error.message ?: "登录配置加载失败")
                }
            }
    }

    private suspend fun loadCaptcha() {
        _uiState.update { it.copy(loadingCaptcha = true) }
        runCatching { repository.loadCaptcha() }
            .onSuccess { captcha ->
                _uiState.update { it.copy(captcha = captcha, loadingCaptcha = false) }
            }
            .onFailure { error ->
                _uiState.update {
                    it.copy(
                        captcha = null,
                        loadingCaptcha = false,
                        message = error.message ?: "验证码加载失败",
                    )
                }
            }
    }

    override fun onCleared() {
        sessionMonitorJob?.cancel()
        super.onCleared()
    }

    class Factory(
        private val repository: WebVpnAuthRepository,
        private val credentialStore: WebVpnCredentialStore,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(WebVpnViewModel::class.java))
            return WebVpnViewModel(repository, credentialStore) as T
        }
    }

    private companion object {
        const val ResumeCheckDebounceMs = 30_000L
        const val SessionCheckIntervalMs = 5 * 60_000L
    }
}
