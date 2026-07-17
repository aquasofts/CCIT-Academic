package edu.ccit.webvpn.settings

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.preferencesDataStore
import com.google.android.material.color.utilities.Variant
import dagger.hilt.android.qualifiers.ApplicationContext
import edu.ccit.webvpn.feature.home.DefaultHomeFeedUrls
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private val Context.settingsDataStore by preferencesDataStore(
    name = "appearance_settings",
    produceMigrations = { context ->
        listOf(SharedPreferencesMigration(context, "academic_features"))
    },
)

enum class Theme { BLUE, GREEN, ORANGE, PINK, PURPLE, DYNAMIC, CUSTOM }
enum class DarkPreference { FOLLOW_SYSTEM, ALWAYS, DISABLED }
enum class NavigationLabel { ALWAYS, SELECTED, NONE }

@Immutable
data class ThemeSettings(
    val theme: Theme = Theme.DYNAMIC,
    val customColor: Int = 0xFF705D61.toInt(),
    val customVariant: Variant = Variant.CONTENT,
)

@Immutable
data class UISettings(
    val appIconThemed: Boolean = true,
    val bottomNavFloating: Boolean = false,
    val bottomNavLabel: NavigationLabel = NavigationLabel.ALWAYS,
    val darkAmoled: Boolean = false,
    val darkPreference: DarkPreference = DarkPreference.FOLLOW_SYSTEM,
    val reduceEffect: Boolean = false,
)

@Immutable
data class AppearanceState(
    val theme: ThemeSettings = ThemeSettings(),
    val ui: UISettings = UISettings(),
)

@Immutable
data class AcademicFeatureSettings(
    val orderIds: List<String> = emptyList(),
)

@Immutable
data class RssFeedSettings(
    val wechatUrls: List<String> = DefaultHomeFeedUrls.wechat,
    val newsUrls: List<String> = DefaultHomeFeedUrls.news,
)

@Immutable
abstract class Settings<T>(protected val flow: Flow<T>) : Flow<T> by flow {
    suspend fun snapshot(): T = first()
    abstract fun set(new: T)
    abstract fun save(transform: (old: T) -> T)
}

interface SettingsRepository {
    val themeSettings: Settings<ThemeSettings>
    val uiSettings: Settings<UISettings>
    val academicFeatureSettings: Settings<AcademicFeatureSettings>
    val rssFeedSettings: Settings<RssFeedSettings>
}

@Singleton
class DataStoreSettingsRepository @Inject constructor(
    @ApplicationContext context: Context,
) : SettingsRepository {
    private val dataStore = context.settingsDataStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        scope.launch { dataStore.edit { it.remove(FavoriteIdsKey) } }
    }

    private fun <T> settings(
        read: (Preferences) -> T,
        write: (androidx.datastore.preferences.core.MutablePreferences, T) -> Unit,
    ): Settings<T> = object : Settings<T>(
        dataStore.data
            .catch { error -> if (error is IOException) emit(androidx.datastore.preferences.core.emptyPreferences()) else throw error }
            .map(read)
            .distinctUntilChanged(),
    ) {
        override fun set(new: T) {
            scope.launch { dataStore.edit { write(it, new) } }
        }

        override fun save(transform: (old: T) -> T) {
            scope.launch { dataStore.edit { preferences -> write(preferences, transform(read(preferences))) } }
        }
    }

    override val themeSettings: Settings<ThemeSettings> = settings(
        read = ::readThemeSettings,
        write = ::writeThemeSettings,
    )

    override val uiSettings: Settings<UISettings> = settings(
        read = ::readUiSettings,
        write = ::writeUiSettings,
    )

    override val academicFeatureSettings: Settings<AcademicFeatureSettings> = settings(
        read = ::readAcademicFeatureSettings,
        write = ::writeAcademicFeatureSettings,
    )

    override val rssFeedSettings: Settings<RssFeedSettings> = settings(
        read = ::readRssFeedSettings,
        write = ::writeRssFeedSettings,
    )
}

private val ThemeKey = stringPreferencesKey("theme")
private val CustomColorKey = intPreferencesKey("custom_color")
private val CustomVariantKey = stringPreferencesKey("custom_variant")
private val AppIconThemedKey = booleanPreferencesKey("app_icon_themed")
private val BottomNavFloatingKey = booleanPreferencesKey("bottom_nav_floating")
private val BottomNavLabelKey = stringPreferencesKey("bottom_nav_label")
private val DarkAmoledKey = booleanPreferencesKey("dark_amoled")
private val DarkPreferenceKey = stringPreferencesKey("dark_preference")
private val ReduceEffectKey = booleanPreferencesKey("reduce_effect")
private val FavoriteIdsKey = stringSetPreferencesKey("favorite_ids")
private val FeatureOrderKey = stringPreferencesKey("feature_order")
private val WechatRssUrlsKey = stringPreferencesKey("rss_wechat_urls")
private val NewsRssUrlsKey = stringPreferencesKey("rss_news_urls")

internal fun readThemeSettings(preferences: Preferences): ThemeSettings = ThemeSettings(
    theme = preferences[ThemeKey].enumOr(Theme.DYNAMIC),
    customColor = preferences[CustomColorKey] ?: 0xFF705D61.toInt(),
    customVariant = preferences[CustomVariantKey].enumOr(Variant.CONTENT),
)

internal fun writeThemeSettings(
    preferences: androidx.datastore.preferences.core.MutablePreferences,
    value: ThemeSettings,
) {
    preferences[ThemeKey] = value.theme.name
    preferences[CustomColorKey] = value.customColor
    preferences[CustomVariantKey] = value.customVariant.name
}

internal fun readUiSettings(preferences: Preferences): UISettings = UISettings(
    appIconThemed = preferences[AppIconThemedKey] ?: true,
    bottomNavFloating = preferences[BottomNavFloatingKey] ?: false,
    bottomNavLabel = preferences[BottomNavLabelKey].enumOr(NavigationLabel.ALWAYS),
    darkAmoled = preferences[DarkAmoledKey] ?: false,
    darkPreference = preferences[DarkPreferenceKey].enumOr(DarkPreference.FOLLOW_SYSTEM),
    reduceEffect = preferences[ReduceEffectKey] ?: false,
)

internal fun writeUiSettings(
    preferences: androidx.datastore.preferences.core.MutablePreferences,
    value: UISettings,
) {
    preferences[AppIconThemedKey] = value.appIconThemed
    preferences[BottomNavFloatingKey] = value.bottomNavFloating
    preferences[BottomNavLabelKey] = value.bottomNavLabel.name
    preferences[DarkAmoledKey] = value.darkAmoled
    preferences[DarkPreferenceKey] = value.darkPreference.name
    preferences[ReduceEffectKey] = value.reduceEffect
}

internal fun readAcademicFeatureSettings(preferences: Preferences): AcademicFeatureSettings =
    AcademicFeatureSettings(
        orderIds = preferences[FeatureOrderKey]
            ?.split(',')
            ?.filter(String::isNotBlank)
            .orEmpty(),
    )

internal fun writeAcademicFeatureSettings(
    preferences: androidx.datastore.preferences.core.MutablePreferences,
    value: AcademicFeatureSettings,
) {
    preferences.remove(FavoriteIdsKey)
    preferences[FeatureOrderKey] = value.orderIds.joinToString(",")
}

internal fun readRssFeedSettings(preferences: Preferences): RssFeedSettings = RssFeedSettings(
    wechatUrls = preferences.readRssUrls(WechatRssUrlsKey, DefaultHomeFeedUrls.wechat),
    newsUrls = preferences.readRssUrls(NewsRssUrlsKey, DefaultHomeFeedUrls.news),
)

internal fun writeRssFeedSettings(
    preferences: androidx.datastore.preferences.core.MutablePreferences,
    value: RssFeedSettings,
) {
    preferences[WechatRssUrlsKey] = value.wechatUrls.normalizedRssUrls().joinToString("\n")
    preferences[NewsRssUrlsKey] = value.newsUrls.normalizedRssUrls().joinToString("\n")
}

private fun Preferences.readRssUrls(key: Preferences.Key<String>, defaults: List<String>): List<String> =
    get(key)?.lineSequence()?.toList()?.normalizedRssUrls() ?: defaults

private fun List<String>.normalizedRssUrls(): List<String> = map(String::trim)
    .filter(String::isNotBlank)
    .distinct()

private inline fun <reified T : Enum<T>> String?.enumOr(default: T): T =
    enumValues<T>().firstOrNull { it.name == this } ?: default
