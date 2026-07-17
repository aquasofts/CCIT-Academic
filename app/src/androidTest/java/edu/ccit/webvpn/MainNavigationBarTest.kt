package edu.ccit.webvpn

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import edu.ccit.webvpn.core.ui.CcitAcademicTheme
import edu.ccit.webvpn.settings.NavigationLabel
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainNavigationBarTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun fourAlwaysVisibleItemsFitNarrowWidthAndSelect() {
        var selected by mutableStateOf(MainTab.Tieba)
        compose.setContent {
            CcitAcademicTheme(reduceMotion = true) {
                Box(Modifier.width(320.dp)) {
                    MainNavigationBar(
                        selectedTab = selected,
                        floating = false,
                        labelMode = NavigationLabel.ALWAYS,
                        reduceMotion = true,
                        onSelect = { selected = it },
                    )
                }
            }
        }

        listOf("贴吧", "新闻", "教务", "我的").forEach {
            compose.onNodeWithText(it).assertIsDisplayed()
        }
        compose.onNodeWithText("新闻").performClick()
        compose.runOnIdle { assertEquals(MainTab.News, selected) }
    }
}
