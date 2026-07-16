package edu.ccit.webvpn.settings

import android.os.Build
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.outlined.Animation
import androidx.compose.material.icons.outlined.BlurOn
import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Houseboat
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.android.material.color.utilities.Variant
import edu.ccit.webvpn.R
import edu.ccit.webvpn.feature.tieba.ui.TiebaSettingsScreen
import edu.ccit.webvpn.settings.preference.SegmentedPreference
import edu.ccit.webvpn.settings.preference.SegmentedPrefsScreen
import edu.ccit.webvpn.settings.preference.SegmentedTextPrefsScreen
import edu.ccit.webvpn.settings.preference.preference

private const val SettingsHomeRoute = "settings_home"
private const val ThemeSettingsRoute = "settings_theme"
private const val UiSettingsRoute = "settings_ui"
private const val TiebaSettingsRoute = "settings_tieba"

@Composable
fun AppearanceSettingsScreen(
    current: AppearanceState,
    themeSettings: Settings<ThemeSettings>,
    uiSettings: Settings<UISettings>,
    reduceEffect: Boolean,
    onThemedIconChange: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    val navigator = rememberNavController()
    val duration = if (reduceEffect) 120 else 300
    NavHost(
        navController = navigator,
        startDestination = SettingsHomeRoute,
        modifier = Modifier.fillMaxSize(),
        enterTransition = {
            fadeIn(tween(duration, easing = FastOutSlowInEasing)) +
                scaleIn(
                    initialScale = if (reduceEffect) 1f else 0.9f,
                    animationSpec = tween(duration, easing = FastOutSlowInEasing),
                )
        },
        exitTransition = {
            fadeOut(tween(duration, easing = FastOutSlowInEasing)) +
                scaleOut(
                    targetScale = if (reduceEffect) 1f else 1.1f,
                    animationSpec = tween(duration, easing = FastOutSlowInEasing),
                )
        },
        popEnterTransition = {
            fadeIn(tween(duration, easing = FastOutSlowInEasing)) +
                scaleIn(
                    initialScale = if (reduceEffect) 1f else 1.1f,
                    animationSpec = tween(duration, easing = FastOutSlowInEasing),
                )
        },
        popExitTransition = {
            fadeOut(tween(duration, easing = FastOutSlowInEasing)) +
                scaleOut(
                    targetScale = if (reduceEffect) 1f else 0.9f,
                    animationSpec = tween(duration, easing = FastOutSlowInEasing),
                )
        },
    ) {
        composable(SettingsHomeRoute) {
            SettingsHomeScreen(
                onBack = onBack,
                onTheme = { navigator.navigate(ThemeSettingsRoute) },
                onUi = { navigator.navigate(UiSettingsRoute) },
                onTieba = { navigator.navigate(TiebaSettingsRoute) },
            )
        }
        composable(ThemeSettingsRoute) {
            ThemeSettingsScreen(
                settings = themeSettings,
                initial = current.theme,
                onBack = navigator::navigateUp,
            )
        }
        composable(UiSettingsRoute) {
            UiSettingsScreen(
                settings = uiSettings,
                initial = current.ui,
                onThemedIconChange = onThemedIconChange,
                onBack = navigator::navigateUp,
            )
        }
        composable(TiebaSettingsRoute) {
            TiebaSettingsScreen(onBack = navigator::navigateUp)
        }
    }
}

@Composable
private fun SettingsHomeScreen(
    onBack: () -> Unit,
    onTheme: () -> Unit,
    onUi: () -> Unit,
    onTieba: () -> Unit,
) {
    val themeTitle = stringResource(R.string.settings_theme_title)
    val themeSummary = stringResource(R.string.settings_theme_summary)
    val uiTitle = stringResource(R.string.settings_ui_title)
    val uiSummary = stringResource(R.string.settings_ui_summary)
    SettingsScaffold(title = stringResource(R.string.settings_title), onBack = onBack) { padding ->
        SegmentedTextPrefsScreen(contentPadding = padding) {
            group(verticalPadding = 6.dp) {
                preference(
                    onClick = onTheme,
                    title = themeTitle,
                    summary = themeSummary,
                    icon = Icons.Outlined.Palette,
                    trailingIcon = Icons.AutoMirrored.Rounded.Label,
                )
                preference(
                    onClick = onUi,
                    title = uiTitle,
                    summary = uiSummary,
                    icon = Icons.Outlined.Tune,
                    trailingIcon = Icons.Outlined.Style,
                )
                preference(
                    onClick = onTieba,
                    title = "贴吧设置",
                    summary = "阅读偏好、自动签到与最近结果",
                    icon = Icons.Outlined.Forum,
                    trailingIcon = Icons.Outlined.Tune,
                )
            }
        }
    }
}

@Composable
private fun ThemeSettingsScreen(
    settings: Settings<ThemeSettings>,
    initial: ThemeSettings,
    onBack: () -> Unit,
) {
    SettingsScaffold(title = stringResource(R.string.settings_theme_title), onBack = onBack) { padding ->
        SegmentedPrefsScreen(settings = settings, initialValue = initial, contentPadding = padding) {
            group(title = R.string.settings_group_color, verticalPadding = 6.dp) {
                customPreference(key = "theme_palette") { _ ->
                    ThemePaletteGrid(
                        selected = currentPreference.theme,
                        customColor = currentPreference.customColor,
                        onSelect = { theme -> update { it.copy(theme = theme) } },
                    )
                }
                if (currentPreference.theme == Theme.CUSTOM) {
                    customPreference(key = "seed_color") { shapes ->
                        var showPicker by remember { mutableStateOf(false) }
                        SegmentedPreference(
                            title = stringResource(R.string.settings_seed_color),
                            summary = stringResource(R.string.settings_seed_color_summary),
                            leadingIcon = Icons.Outlined.ColorLens,
                            shapes = shapes,
                            trailingContent = {
                                Surface(
                                    modifier = Modifier.size(36.dp),
                                    shape = CircleShape,
                                    color = Color(currentPreference.customColor),
                                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline),
                                ) {}
                            },
                            onClick = { showPicker = true },
                        )
                        if (showPicker) {
                            SeedColorDialog(
                                selected = currentPreference.customColor,
                                onSelect = { color ->
                                    settings.save { it.copy(theme = Theme.CUSTOM, customColor = color) }
                                    showPicker = false
                                },
                                onDismiss = { showPicker = false },
                            )
                        }
                    }
                    listPref(
                        value = currentPreference.customVariant,
                        onValueChange = { variant -> update { it.copy(customVariant = variant) } },
                        title = R.string.settings_variant,
                        leadingIcon = Icons.Outlined.Style,
                        options = linkedMapOf(
                            Variant.CONTENT to R.string.settings_variant_content,
                            Variant.EXPRESSIVE to R.string.settings_variant_expressive,
                            Variant.FIDELITY to R.string.settings_variant_fidelity,
                            Variant.FRUIT_SALAD to R.string.settings_variant_fruit,
                            Variant.MONOCHROME to R.string.settings_variant_monochrome,
                            Variant.NEUTRAL to R.string.settings_variant_neutral,
                            Variant.RAINBOW to R.string.settings_variant_rainbow,
                            Variant.TONAL_SPOT to R.string.settings_variant_tonal,
                            Variant.VIBRANT to R.string.settings_variant_vibrant,
                        ),
                    )
                }
            }
        }
    }
}

private val ThemeOptions = listOf(
    Theme.DYNAMIC to R.string.settings_theme_dynamic,
    Theme.BLUE to R.string.settings_theme_blue,
    Theme.GREEN to R.string.settings_theme_green,
    Theme.ORANGE to R.string.settings_theme_orange,
    Theme.PINK to R.string.settings_theme_pink,
    Theme.PURPLE to R.string.settings_theme_purple,
    Theme.CUSTOM to R.string.settings_theme_custom,
)

@Composable
private fun ThemePaletteGrid(
    selected: Theme,
    customColor: Int,
    onSelect: (Theme) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxWidth().height(326.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        userScrollEnabled = false,
    ) {
        items(ThemeOptions, key = { it.first.name }) { (theme, label) ->
            val picked = theme == selected
            val preview = when (theme) {
                Theme.DYNAMIC -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.secondary
                Theme.BLUE -> Color(0xFF4477E0) to Color(0xFF739AE8)
                Theme.GREEN -> Color(0xFF019C74) to Color(0xFF72D6B5)
                Theme.ORANGE -> Color(0xFFFD742D) to Color(0xFFFFB68C)
                Theme.PINK -> Color(0xFFE986A7) to Color(0xFFFFB1C3)
                Theme.PURPLE -> Color(0xFF8A2BE2) to Color(0xFFD3B3FF)
                Theme.CUSTOM -> Color(customColor) to Color(customColor).copy(alpha = 0.55f)
            }
            Surface(
                onClick = { onSelect(theme) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (picked) 78.dp else 72.dp)
                    .animateContentSize(spring(stiffness = Spring.StiffnessLow)),
                shape = MaterialTheme.shapes.large,
                color = if (picked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(
                    width = if (picked) 2.dp else 1.dp,
                    color = if (picked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                ),
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
                        Surface(modifier = Modifier.size(28.dp), shape = CircleShape, color = preview.first) {}
                        Surface(modifier = Modifier.size(28.dp), shape = CircleShape, color = preview.second) {}
                    }
                    Text(
                        text = stringResource(label),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun UiSettingsScreen(
    settings: Settings<UISettings>,
    initial: UISettings,
    onThemedIconChange: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    SettingsScaffold(title = stringResource(R.string.settings_ui_title), onBack = onBack) { padding ->
        SegmentedPrefsScreen(settings = settings, initialValue = initial, contentPadding = padding) {
            group(title = R.string.settings_group_dark, verticalPadding = 6.dp) {
                listPref(
                    value = currentPreference.darkPreference,
                    onValueChange = { preference -> update { it.copy(darkPreference = preference) } },
                    title = R.string.settings_dark_mode,
                    leadingIcon = Icons.Outlined.DarkMode,
                    options = linkedMapOf(
                        DarkPreference.FOLLOW_SYSTEM to R.string.settings_dark_system,
                        DarkPreference.ALWAYS to R.string.settings_dark_always,
                        DarkPreference.DISABLED to R.string.settings_dark_disabled,
                    ),
                )
                toggleablePreference(
                    value = currentPreference.darkAmoled,
                    onValueChange = { enabled -> update { it.copy(darkAmoled = enabled) } },
                    title = R.string.settings_dark_amoled,
                    summary = R.string.settings_dark_amoled_summary,
                    leadingIcon = Icons.Outlined.BlurOn,
                    enabled = currentPreference.darkPreference != DarkPreference.DISABLED,
                )
            }
            group(title = R.string.settings_group_navigation, verticalPadding = 6.dp) {
                toggleablePreference(
                    value = currentPreference.bottomNavFloating,
                    onValueChange = { enabled -> update { it.copy(bottomNavFloating = enabled) } },
                    title = R.string.settings_nav_floating,
                    summary = R.string.settings_nav_floating_summary,
                    leadingIcon = Icons.Outlined.Houseboat,
                )
                listPref(
                    value = currentPreference.bottomNavLabel,
                    onValueChange = { label -> update { it.copy(bottomNavLabel = label) } },
                    title = R.string.settings_nav_label,
                    leadingIcon = Icons.AutoMirrored.Rounded.Label,
                    options = linkedMapOf(
                        NavigationLabel.ALWAYS to R.string.settings_nav_label_always,
                        NavigationLabel.SELECTED to R.string.settings_nav_label_selected,
                        NavigationLabel.NONE to R.string.settings_nav_label_none,
                    ),
                )
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                group(title = R.string.settings_group_icon, verticalPadding = 6.dp) {
                    toggleablePreference(
                        value = currentPreference.appIconThemed,
                        onValueChange = { enabled -> update { it.copy(appIconThemed = enabled) } },
                        title = R.string.settings_icon_themed,
                        summary = R.string.settings_icon_themed_summary,
                        leadingIcon = Icons.Outlined.ColorLens,
                        onCheckedChange = onThemedIconChange,
                    )
                }
            }
            group(title = R.string.settings_group_effect, verticalPadding = 6.dp) {
                toggleablePreference(
                    value = currentPreference.reduceEffect,
                    onValueChange = { enabled -> update { it.copy(reduceEffect = enabled) } },
                    title = R.string.settings_reduce_effect,
                    summary = R.string.settings_reduce_effect_summary,
                    leadingIcon = Icons.Outlined.Animation,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            LargeTopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.action_back))
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.01f),
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
    ) { inner ->
        content(
            PaddingValues(
                start = 16.dp,
                top = inner.calculateTopPadding() + 8.dp,
                end = 16.dp,
                bottom = inner.calculateBottomPadding() + 16.dp,
            ),
        )
    }
}

private val SeedColors = listOf(
    0xFF4477E0.toInt(), 0xFF019C74.toInt(), 0xFFFD742D.toInt(), 0xFFE986A7.toInt(),
    0xFF8A2BE2.toInt(), 0xFF705D61.toInt(), 0xFF0061A4.toInt(), 0xFF6750A4.toInt(),
    0xFFB3261E.toInt(), 0xFF386A20.toInt(), 0xFF7D5700.toInt(), 0xFF006874.toInt(),
)

@Composable
private fun SeedColorDialog(selected: Int, onSelect: (Int) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_seed_color)) },
        text = {
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.height(224.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(SeedColors) { argb ->
                    Box(contentAlignment = Alignment.Center) {
                        Surface(
                            onClick = { onSelect(Color(argb).toArgb()) },
                            modifier = Modifier.size(44.dp).clip(CircleShape),
                            shape = CircleShape,
                            color = Color(argb),
                            border = if (argb == selected) {
                                BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface)
                            } else null,
                        ) {}
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_done)) } },
    )
}
