package edu.ccit.webvpn.feature.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import edu.ccit.webvpn.core.ui.CcitAcademicTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun showsBothFeedPagesAndChangesSelection() {
        compose.setContent {
            CcitAcademicTheme(reduceMotion = true) {
                HomeRootScreen(active = false, reduceMotion = true)
            }
        }

        compose.onNodeWithText("首页").assertIsDisplayed()
        compose.onNodeWithText("公众号").assertIsDisplayed()
        compose.onNodeWithText("校内新闻").assertIsDisplayed().performClick()
        compose.onNodeWithText("校内新闻").assertIsDisplayed()
        compose.onNodeWithText("官方新闻").assertIsDisplayed()
    }

    @Test
    fun placeholderArticleShowsOriginalButtonInDarkTheme() {
        compose.setContent {
            CcitAcademicTheme(darkTheme = true, reduceMotion = true) {
                ArticleReaderScreen(article = article("<p>[提示] 文章内容正在获取中，请稍后刷新</p>"), onBack = {})
            }
        }

        compose.onNodeWithText("RSS 源暂未提供完整正文").assertIsDisplayed()
        compose.onNodeWithText("查看原文").assertIsDisplayed()
    }

    @Test
    fun interactiveArticleUsesDeterministicPlaceholder() {
        compose.setContent {
            CcitAcademicTheme(reduceMotion = true) {
                ArticleReaderScreen(
                    article = article("<p>视频正文</p><iframe src=\"https://article.example/video\"></iframe>"),
                    onBack = {},
                )
            }
        }

        compose.onNodeWithText("文章包含暂不支持的互动内容").assertIsDisplayed()
        compose.onNodeWithText("查看原文").assertIsDisplayed()
    }

    private fun article(html: String) = HomeArticle(
        id = "reader-test",
        sourceId = "test",
        sourceName = "测试来源",
        sourceAvatarUrl = "",
        title = "测试文章",
        link = "https://article.example/story",
        guid = "reader-test",
        publishedAt = null,
        html = html,
        summary = "",
        coverUrl = "",
        allowedArticleHosts = setOf("article.example"),
    )
}
