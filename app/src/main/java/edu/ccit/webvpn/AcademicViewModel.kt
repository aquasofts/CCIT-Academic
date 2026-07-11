package edu.ccit.webvpn

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import edu.ccit.webvpn.core.academic.AcademicApiException
import edu.ccit.webvpn.core.academic.AcademicOverview
import edu.ccit.webvpn.core.academic.AcademicRepository
import edu.ccit.webvpn.core.academic.AcademicTerm
import edu.ccit.webvpn.core.academic.AcademicTimetable
import edu.ccit.webvpn.core.academic.CourseGrade
import edu.ccit.webvpn.core.webvpn.AcademicCredentialStore
import edu.ccit.webvpn.core.webvpn.SavedAcademicAccount
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AcademicUiState(
    val active: Boolean = false,
    val initializing: Boolean = false,
    val loggedIn: Boolean = false,
    val defaultUsername: String = "",
    val savedAccounts: List<SavedAcademicAccount> = emptyList(),
    val selectedSavedUsername: String? = null,
    val captcha: ByteArray? = null,
    val loadingCaptcha: Boolean = false,
    val submitting: Boolean = false,
    val terms: List<AcademicTerm> = emptyList(),
    val selectedTerm: String = "",
    val bestOnly: Boolean = false,
    val grades: List<CourseGrade> = emptyList(),
    val loadingGrades: Boolean = false,
    val timetable: AcademicTimetable? = null,
    val loadingTimetable: Boolean = false,
    val webViewCookies: List<String> = emptyList(),
    val message: String? = null,
)

class AcademicViewModel(
    private val repository: AcademicRepository,
    private val credentialStore: AcademicCredentialStore,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AcademicUiState())
    val uiState: StateFlow<AcademicUiState> = _uiState.asStateFlow()
    private var activeUsername: String? = null
    private var sessionGeneration = 0
    private var initializationJob: Job? = null

    fun onWebVpnReady(username: String) {
        val normalized = username.trim()
        if (_uiState.value.active && activeUsername == normalized) return
        activeUsername = normalized
        val generation = ++sessionGeneration
        initializationJob?.cancel()
        initializationJob = viewModelScope.launch {
            _uiState.value = AcademicUiState(
                active = true,
                initializing = true,
                defaultUsername = normalized,
            )
            val savedAccounts = runCatching { credentialStore.getSavedAcademicAccounts() }
                .getOrDefault(emptyList())
            if (generation != sessionGeneration) return@launch
            _uiState.update {
                it.copy(
                    savedAccounts = savedAccounts,
                    selectedSavedUsername = normalized.takeIf { username ->
                        savedAccounts.any { account -> account.username == username }
                    },
                )
            }

            runCatching { repository.restoreSession() }
                .onSuccess { overview ->
                    if (generation != sessionGeneration) return@onSuccess
                    if (overview == null) {
                        _uiState.update { it.copy(initializing = false) }
                        loadCaptcha()
                    } else {
                        enterGradeState(overview)
                        loadGrades()
                    }
                }
                .onFailure { error ->
                    if (generation != sessionGeneration) return@onFailure
                    _uiState.update {
                        it.copy(
                            initializing = false,
                            message = error.message ?: "连接教务系统失败",
                        )
                    }
                    loadCaptcha()
                }
        }
    }

    fun onWebVpnCleared() {
        activeUsername = null
        sessionGeneration += 1
        initializationJob?.cancel()
        initializationJob = null
        _uiState.value = AcademicUiState()
    }

    fun refreshCaptcha() {
        if (!_uiState.value.active || _uiState.value.loadingCaptcha) return
        viewModelScope.launch { loadCaptcha() }
    }

    fun selectSavedAccount(username: String) {
        if (_uiState.value.savedAccounts.none { it.username == username }) return
        _uiState.update {
            it.copy(defaultUsername = username, selectedSavedUsername = username)
        }
    }

    fun useManualCredentials() {
        _uiState.update { it.copy(selectedSavedUsername = null) }
    }

    fun forgetSavedAccount(username: String) {
        viewModelScope.launch {
            runCatching { credentialStore.deleteAcademicCredential(username) }
                .onSuccess {
                    val accounts = credentialStore.getSavedAcademicAccounts()
                    _uiState.update { state ->
                        state.copy(
                            savedAccounts = accounts,
                            selectedSavedUsername = state.selectedSavedUsername
                                ?.takeIf { selected -> accounts.any { it.username == selected } },
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(message = error.message ?: "删除已保存教务账号失败") }
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
        if (!state.active || state.submitting) return
        viewModelScope.launch {
            _uiState.update { it.copy(submitting = true, message = null) }
            try {
                val normalizedUsername = username.trim()
                val selectedUsername = state.selectedSavedUsername
                val resolvedPassword = if (selectedUsername == normalizedUsername) {
                    credentialStore.getSavedAcademicPassword(normalizedUsername)
                        ?: error("本机没有可用的已保存密码，请重新输入")
                } else {
                    password
                }
                val overview = repository.login(normalizedUsername, resolvedPassword, captchaCode)
                val shouldSave = rememberPassword || selectedUsername == normalizedUsername
                var saveWarning: String? = null
                if (shouldSave) {
                    runCatching {
                        credentialStore.saveAcademicCredential(normalizedUsername, resolvedPassword)
                    }.onFailure {
                        saveWarning = "已登录，但教务账号密码保存失败"
                    }
                }
                val savedAccounts = runCatching { credentialStore.getSavedAcademicAccounts() }
                    .getOrDefault(state.savedAccounts)
                enterGradeState(overview)
                _uiState.update {
                    it.copy(
                        defaultUsername = normalizedUsername,
                        savedAccounts = savedAccounts,
                        selectedSavedUsername = normalizedUsername.takeIf { shouldSave },
                        message = saveWarning,
                    )
                }
                loadGrades()
            } catch (error: Throwable) {
                _uiState.update {
                    it.copy(
                        submitting = false,
                        loggedIn = false,
                        captcha = null,
                        message = error.message ?: "教务系统登录失败",
                    )
                }
                loadCaptcha()
            }
        }
    }

    fun selectTerm(term: String) {
        if (_uiState.value.terms.none { it.value == term }) return
        _uiState.update { it.copy(selectedTerm = term) }
    }

    fun setBestOnly(bestOnly: Boolean) {
        _uiState.update { it.copy(bestOnly = bestOnly) }
    }

    fun queryGrades() {
        if (!_uiState.value.loggedIn || _uiState.value.loadingGrades) return
        viewModelScope.launch { loadGrades() }
    }

    fun queryTimetable(semester: String? = null) {
        val state = _uiState.value
        if (!state.loggedIn || state.loadingTimetable) return
        viewModelScope.launch {
            _uiState.update { it.copy(loadingTimetable = true, message = null) }
            runCatching { repository.loadTimetable(semester) }
                .onSuccess { timetable ->
                    _uiState.update { it.copy(timetable = timetable, loadingTimetable = false) }
                }
                .onFailure { error ->
                    val loginRequired = (error as? AcademicApiException)?.loginRequired == true
                    _uiState.update {
                        it.copy(
                            loggedIn = !loginRequired,
                            loadingTimetable = false,
                            timetable = null,
                            message = error.message ?: "理论课表加载失败",
                        )
                    }
                    if (loginRequired) loadCaptcha()
                }
        }
    }

    fun logout() {
        val state = _uiState.value
        if (!state.loggedIn || state.submitting) return
        viewModelScope.launch {
            _uiState.update { it.copy(submitting = true, message = null) }
            runCatching { repository.logout() }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(message = error.message ?: "退出教务系统失败")
                    }
                }
            _uiState.update {
                it.copy(
                    loggedIn = false,
                    submitting = false,
                    terms = emptyList(),
                    selectedTerm = "",
                    grades = emptyList(),
                    timetable = null,
                    loadingTimetable = false,
                    webViewCookies = emptyList(),
                )
            }
            loadCaptcha()
        }
    }

    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }

    private suspend fun enterGradeState(overview: AcademicOverview) {
        _uiState.update {
            it.copy(
                initializing = false,
                loggedIn = true,
                captcha = null,
                submitting = false,
                terms = overview.terms,
                selectedTerm = overview.defaultTerm,
                grades = emptyList(),
                webViewCookies = repository.webViewCookies(),
            )
        }
    }

    private suspend fun loadCaptcha() {
        _uiState.update { it.copy(loadingCaptcha = true) }
        runCatching { repository.loadCaptcha() }
            .onSuccess { image ->
                _uiState.update { it.copy(captcha = image, loadingCaptcha = false) }
            }
            .onFailure { error ->
                _uiState.update {
                    it.copy(
                        captcha = null,
                        loadingCaptcha = false,
                        message = error.message ?: "教务验证码加载失败",
                    )
                }
            }
    }

    private suspend fun loadGrades() {
        val state = _uiState.value
        _uiState.update { it.copy(loadingGrades = true, message = null) }
        runCatching { repository.loadGrades(state.selectedTerm, state.bestOnly) }
            .onSuccess { grades ->
                _uiState.update { it.copy(grades = grades, loadingGrades = false) }
            }
            .onFailure { error ->
                val loginRequired = (error as? AcademicApiException)?.loginRequired == true
                _uiState.update {
                    it.copy(
                        loggedIn = !loginRequired,
                        loadingGrades = false,
                        grades = emptyList(),
                        message = error.message ?: "成绩加载失败",
                    )
                }
                if (loginRequired) loadCaptcha()
            }
    }

    override fun onCleared() {
        initializationJob?.cancel()
        super.onCleared()
    }

    class Factory(
        private val repository: AcademicRepository,
        private val credentialStore: AcademicCredentialStore,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(AcademicViewModel::class.java))
            return AcademicViewModel(repository, credentialStore) as T
        }
    }
}
