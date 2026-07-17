package edu.ccit.webvpn.feature.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.util.EnumMap
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class FeedPageState(
    val articles: List<HomeArticle> = emptyList(),
    val initialLoading: Boolean = true,
    val refreshing: Boolean = false,
    val errorMessage: String? = null,
)

internal data class HomeUiState(
    val wechat: FeedPageState = FeedPageState(),
    val news: FeedPageState = FeedPageState(),
    val official: FeedPageState = FeedPageState(),
) {
    fun page(section: HomeSection): FeedPageState = when (section) {
        HomeSection.WECHAT -> wechat
        HomeSection.NEWS -> news
        HomeSection.OFFICIAL -> official
    }

    fun article(id: String): HomeArticle? = (wechat.articles + news.articles + official.articles)
        .firstOrNull { it.id == id }

    fun isInitiallyLoading(): Boolean = wechat.initialLoading || news.initialLoading || official.initialLoading
}

internal class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private var configuredSources = HomeFeedSources.all
    private var repository = HomeRepository.create(application, configuredSources)
    private val imageCache = HomeImageCache.get(application)
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    private val activated = mutableSetOf<HomeSection>()
    private val refreshJobs = EnumMap<HomeSection, Job>(HomeSection::class.java)
    private val detailJobs = mutableMapOf<String, Job>()
    private var configurationVersion = 0

    init {
        loadCaches(configurationVersion)
    }

    fun configure(wechatUrls: List<String>, newsUrls: List<String>) {
        val newSources = HomeFeedSources.fromUrls(wechatUrls, newsUrls)
        if (newSources == configuredSources) return

        val activeSections = activated.toSet()
        refreshJobs.values.forEach(Job::cancel)
        refreshJobs.clear()
        detailJobs.values.forEach(Job::cancel)
        detailJobs.clear()
        activated.clear()
        configuredSources = newSources
        repository = HomeRepository.create(getApplication(), configuredSources)
        configurationVersion += 1
        _uiState.value = HomeUiState()
        loadCaches(configurationVersion)
        activeSections.forEach(::ensureLoaded)
    }

    private fun loadCaches(version: Int) {
        HomeSection.entries.forEach { section ->
            viewModelScope.launch {
                val cached = repository.loadCached(section)
                _uiState.update { current ->
                    if (
                        version != configurationVersion ||
                        current.page(section).articles.isNotEmpty() ||
                        current.page(section).refreshing ||
                        refreshJobs[section]?.isActive == true
                    ) current else {
                        current.withPage(section, FeedPageState(articles = cached, initialLoading = false))
                    }
                }
                imageCache.prefetch(cached)
            }
        }
    }

    fun ensureLoaded(section: HomeSection) {
        if (activated.add(section)) refresh(section)
    }

    fun refresh(section: HomeSection) {
        if (refreshJobs[section]?.isActive == true) return
        val currentPage = _uiState.value.page(section)
        _uiState.update {
            it.withPage(
                section,
                currentPage.copy(
                    initialLoading = currentPage.articles.isEmpty(),
                    refreshing = currentPage.articles.isNotEmpty(),
                    errorMessage = null,
                ),
            )
        }
        refreshJobs[section] = viewModelScope.launch {
            val result = repository.refresh(section)
            _uiState.update { current ->
                val previous = current.page(section)
                current.withPage(
                    section,
                    previous.copy(
                        articles = result.articles.ifEmpty { previous.articles },
                        initialLoading = false,
                        refreshing = false,
                        errorMessage = result.errorMessage,
                    ),
                )
            }
            imageCache.prefetch(result.articles)
        }
    }

    fun loadArticleDetail(id: String) {
        if (detailJobs[id]?.isActive == true) return
        val article = _uiState.value.article(id) ?: return
        val reference = article.officialReference ?: return
        if (reference.detailLoaded) return
        detailJobs[id] = viewModelScope.launch {
            runCatching { repository.loadOfficialDetail(article) }
                .onSuccess { detailed ->
                    _uiState.update { current -> current.replaceArticle(detailed) }
                    imageCache.prefetch(listOf(detailed))
                }
        }
    }

    private fun HomeUiState.withPage(section: HomeSection, page: FeedPageState): HomeUiState = when (section) {
        HomeSection.WECHAT -> copy(wechat = page)
        HomeSection.NEWS -> copy(news = page)
        HomeSection.OFFICIAL -> copy(official = page)
    }

    private fun HomeUiState.replaceArticle(article: HomeArticle): HomeUiState = copy(
        wechat = wechat.replaceArticle(article),
        news = news.replaceArticle(article),
        official = official.replaceArticle(article),
    )

    private fun FeedPageState.replaceArticle(article: HomeArticle): FeedPageState {
        if (articles.none { it.id == article.id }) return this
        return copy(articles = articles.map { current -> if (current.id == article.id) article else current })
    }
}
