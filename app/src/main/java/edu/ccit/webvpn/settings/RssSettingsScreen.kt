package edu.ccit.webvpn.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.RssFeed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import edu.ccit.webvpn.feature.home.DefaultHomeFeedUrls
import java.net.URI

private enum class RssKind(val title: String) {
    WECHAT("公众号"),
    NEWS("校内新闻"),
}

private data class RssEditor(
    val kind: RssKind,
    val index: Int? = null,
    val initialUrl: String = "",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RssSettingsScreen(
    settings: Settings<RssFeedSettings>,
    initial: RssFeedSettings,
    onBack: () -> Unit,
) {
    var current by remember(initial) { mutableStateOf(initial) }
    var editor by remember { mutableStateOf<RssEditor?>(null) }

    fun persist(value: RssFeedSettings) {
        current = value
        settings.set(value)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("RSS 订阅") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            persist(
                                RssFeedSettings(
                                    wechatUrls = DefaultHomeFeedUrls.wechat,
                                    newsUrls = DefaultHomeFeedUrls.news,
                                ),
                            )
                        },
                    ) { Text("恢复默认") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                end = 16.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "intro") {
                Text(
                    "同一分类可添加多个 RSS。首页会并发加载并去重，再按发布时间从新到旧整合。仅支持 HTTPS 地址。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            rssSection(
                kind = RssKind.WECHAT,
                urls = current.wechatUrls,
                onAdd = { editor = RssEditor(RssKind.WECHAT) },
                onEdit = { index, url -> editor = RssEditor(RssKind.WECHAT, index, url) },
                onDelete = { index ->
                    persist(current.copy(wechatUrls = current.wechatUrls.filterIndexed { i, _ -> i != index }))
                },
            )
            rssSection(
                kind = RssKind.NEWS,
                urls = current.newsUrls,
                onAdd = { editor = RssEditor(RssKind.NEWS) },
                onEdit = { index, url -> editor = RssEditor(RssKind.NEWS, index, url) },
                onDelete = { index ->
                    persist(current.copy(newsUrls = current.newsUrls.filterIndexed { i, _ -> i != index }))
                },
            )
        }
    }

    editor?.let { editing ->
        RssUrlDialog(
            editor = editing,
            existingUrls = when (editing.kind) {
                RssKind.WECHAT -> current.wechatUrls
                RssKind.NEWS -> current.newsUrls
            },
            onDismiss = { editor = null },
            onConfirm = { normalized ->
                val updated = when (editing.kind) {
                    RssKind.WECHAT -> current.copy(
                        wechatUrls = current.wechatUrls.withEditedUrl(editing.index, normalized),
                    )
                    RssKind.NEWS -> current.copy(
                        newsUrls = current.newsUrls.withEditedUrl(editing.index, normalized),
                    )
                }
                persist(updated)
                editor = null
            },
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.rssSection(
    kind: RssKind,
    urls: List<String>,
    onAdd: () -> Unit,
    onEdit: (Int, String) -> Unit,
    onDelete: (Int) -> Unit,
) {
    item(key = "${kind.name}-header") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(kind.title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = onAdd) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("添加")
            }
        }
    }
    if (urls.isEmpty()) {
        item(key = "${kind.name}-empty") {
            OutlinedCard(Modifier.fillMaxWidth()) {
                Text(
                    "尚未配置 ${kind.title} RSS",
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    } else {
        itemsIndexed(
            items = urls,
            key = { index, url -> "${kind.name}-$index-$url" },
        ) { index, url ->
            OutlinedCard(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.RssFeed,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            rssDisplayName(url),
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            if (runCatching { URI(url).rawQuery }.getOrNull().isNullOrBlank()) url else "已配置访问参数",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(onClick = { onEdit(index, url) }) {
                        Icon(Icons.Outlined.Edit, contentDescription = "编辑 ${kind.title} RSS")
                    }
                    IconButton(onClick = { onDelete(index) }) {
                        Icon(Icons.Outlined.Delete, contentDescription = "删除 ${kind.title} RSS")
                    }
                }
            }
        }
    }
}

@Composable
private fun RssUrlDialog(
    editor: RssEditor,
    existingUrls: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember(editor) { mutableStateOf(editor.initialUrl) }
    var error by remember(editor) { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editor.index == null) "添加${editor.kind.title} RSS" else "编辑${editor.kind.title} RSS") },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = {
                    value = it
                    error = null
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("RSS 地址") },
                placeholder = { Text("https://example.com/rss.xml") },
                supportingText = error?.let { message -> ({ Text(message) }) },
                isError = error != null,
                minLines = 2,
                maxLines = 5,
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val normalized = normalizeRssUrl(value)
                    error = when {
                        normalized == null -> "请输入有效的 HTTPS RSS 地址"
                        existingUrls.withIndex().any { (index, url) ->
                            index != editor.index && url.equals(normalized, ignoreCase = true)
                        } -> "该地址已存在"
                        else -> null
                    }
                    if (error == null) onConfirm(requireNotNull(normalized))
                },
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

private fun List<String>.withEditedUrl(index: Int?, url: String): List<String> =
    if (index == null) this + url else mapIndexed { current, old -> if (current == index) url else old }

internal fun normalizeRssUrl(raw: String): String? {
    val trimmed = raw.trim()
    val markdownTarget = trimmed.substringAfter("](", missingDelimiterValue = trimmed).removeSuffix(")").trim()
    return runCatching {
        URI(markdownTarget).takeIf {
            it.scheme.equals("https", ignoreCase = true) &&
                !it.host.isNullOrBlank() &&
                it.userInfo.isNullOrBlank()
        }?.toASCIIString()
    }.getOrNull()
}

private fun rssDisplayName(url: String): String = runCatching {
    val uri = URI(url)
    uri.host + uri.rawPath.orEmpty().ifBlank { "/" }
}.getOrDefault(url)
