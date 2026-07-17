package edu.ccit.webvpn.feature.tieba

import android.content.Context
import android.webkit.CookieManager
import androidx.room.Room
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.request.crossfade
import edu.ccit.webvpn.feature.tieba.data.AccountEntity
import edu.ccit.webvpn.feature.tieba.data.TiebaDatabase
import edu.ccit.webvpn.feature.tieba.data.TiebaSettingsRepository
import edu.ccit.webvpn.feature.tieba.network.LoginCookies
import edu.ccit.webvpn.feature.tieba.network.SignResponse
import edu.ccit.webvpn.feature.tieba.network.TiebaNetworkRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class TiebaRuntime private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val database = Room.databaseBuilder(appContext, TiebaDatabase::class.java, "tieba_lite.db").build()
    internal val accountDao = database.accountDao()
    val settings = TiebaSettingsRepository(appContext)
    val network = TiebaNetworkRepository.create(appContext, settings)
    private val signMutex = Mutex()
    private val _signState = MutableStateFlow<TiebaSignState>(TiebaSignState.Idle)
    val signState: StateFlow<TiebaSignState> = _signState.asStateFlow()

    val account = accountDao.observe().map { entity -> entity?.toPublic() }
        .stateIn(scope, SharingStarted.Eagerly, null)

    init {
        SingletonImageLoader.setSafe(TiebaImageLoaderFactory)
    }

    suspend fun login(cookies: LoginCookies) {
        val account = network.loadAccount(cookies)
        accountDao.replace(account)
        onAppForegrounded()
    }

    suspend fun refreshAccount(): Result<Unit> = runCatching {
        val current = accountDao.get() ?: error("尚未登录贴吧")
        val refreshed = network.loadAccount(
            LoginCookies(current.bduss, current.sToken, null, current.cookie),
        )
        accountDao.replace(refreshed)
    }

    suspend fun logout() {
        settings.disableSign()
        accountDao.deleteAll()
        clearTiebaCookies()
        _signState.value = TiebaSignState.Idle
    }

    fun onAppForegrounded() {
        scope.launch {
            settings.refreshClientActiveTimestamp()
            val current = accountDao.get()
            runCatching { network.syncClientConfig(current) }
            autoSignForToday()
        }
    }

    private suspend fun autoSignForToday() {
        signMutex.withLock {
            val preferences = settings.preferences.first()
            val current = accountDao.get()
            if (!preferences.sign.enabled || current == null || !shouldAutoSign(preferences.sign.lastRunAt)) return
            performSign(current)
        }
    }

    suspend fun signNow(forumTbs: String): SignResponse = signMutex.withLock {
        val current = accountDao.get()
        if (current == null) {
            return@withLock SignResponse(SignOutcome.FAILED, "请先登录贴吧账号")
        }
        performSign(current, forumTbs)
    }

    private suspend fun performSign(current: AccountEntity, forumTbs: String? = null): SignResponse {
        _signState.value = TiebaSignState.Running
        val response = runCatching {
            if (forumTbs == null) network.signWithFreshForumState(current) else network.sign(current, forumTbs)
        }
            .getOrElse { error -> SignResponse(SignOutcome.FAILED, error.message ?: "签到失败") }
        settings.recordSign(response.outcome, response.message)
        _signState.value = TiebaSignState.Finished(response.outcome, response.message)
        return response
    }

    companion object {
        @Volatile private var instance: TiebaRuntime? = null

        fun get(context: Context): TiebaRuntime = instance ?: synchronized(this) {
            instance ?: TiebaRuntime(context).also { instance = it }
        }
    }
}

private object TiebaImageLoaderFactory : SingletonImageLoader.Factory {
    override fun newImageLoader(context: PlatformContext): ImageLoader = ImageLoader.Builder(context)
        .diskCache {
            DiskCache.Builder()
                .directory(context.cacheDir.resolve("tieba_image_cache"))
                .maxSizePercent(0.02)
                .build()
        }
        .crossfade(true)
        .build()
}

private fun AccountEntity.toPublic() = TiebaAccount(
    uid = uid,
    username = name,
    nickname = nickname,
    avatarUrl = portrait,
    intro = intro,
    fans = fans,
    posts = posts,
    concerned = concerned,
    lastUpdatedAt = lastUpdate,
)

private fun clearTiebaCookies() {
    val manager = CookieManager.getInstance()
    val urls = listOf(
        "https://tieba.baidu.com",
        "https://tiebac.baidu.com",
        "https://passport.baidu.com",
        "https://wappass.baidu.com",
    )
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
