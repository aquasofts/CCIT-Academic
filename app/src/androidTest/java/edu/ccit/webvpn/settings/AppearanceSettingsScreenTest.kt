package edu.ccit.webvpn.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import edu.ccit.webvpn.core.ui.CcitAcademicTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppearanceSettingsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun opensBothSubpagesUpdatesControlsAndReturns() {
        val themeSettings = FakeSettings(ThemeSettings())
        val uiSettings = FakeSettings(UISettings())
        val rssSettings = FakeSettings(RssFeedSettings())

        compose.setContent {
            CcitAcademicTheme {
                AppearanceSettingsScreen(
                    current = AppearanceState(),
                    themeSettings = themeSettings,
                    uiSettings = uiSettings,
                    rssFeedSettings = rssSettings,
                    currentRssFeeds = rssSettings.value,
                    reduceEffect = false,
                    onThemedIconChange = {},
                    onBack = {},
                )
            }
        }

        compose.onNodeWithText("主题与配色").performClick()
        compose.onNodeWithText("梅洛粉").performClick()
        compose.runOnIdle { assertEquals(Theme.PINK, themeSettings.value.theme) }
        compose.onNodeWithContentDescription("返回").performClick()

        compose.onNodeWithText("界面与动效").performClick()
        compose.onNodeWithText("悬浮底部导航").performClick()
        compose.onNodeWithText("导航标签").performClick()
        compose.onNodeWithText("仅选中项").performClick()
        compose.onNodeWithText("降低视觉效果").performScrollTo().performClick()
        compose.runOnIdle {
            assertTrue(uiSettings.value.bottomNavFloating)
            assertEquals(NavigationLabel.SELECTED, uiSettings.value.bottomNavLabel)
            assertTrue(uiSettings.value.reduceEffect)
        }
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("RSS 订阅").performClick()
        compose.onNodeWithText(
            "wechatrss.waytomaster.com/api/rss/MzA4ODEyOTE2OA==",
            substring = true,
        ).assertIsDisplayed()
        compose.onNodeWithText("cit-news.pages.dev/rss.xml").assertIsDisplayed()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("设置").assertIsDisplayed()
    }
}

private class FakeSettings<T> private constructor(
    private val state: MutableStateFlow<T>,
) : Settings<T>(state) {
    constructor(initial: T) : this(MutableStateFlow(initial))

    val value: T
        get() = state.value

    override fun set(new: T) {
        state.value = new
    }

    override fun save(transform: (old: T) -> T) {
        state.value = transform(state.value)
    }
}
