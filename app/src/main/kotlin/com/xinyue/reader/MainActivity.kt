package com.xinyue.reader

import android.os.Bundle
import android.view.KeyEvent
import androidx.annotation.DrawableRes
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.xinyue.reader.core.ui.XinYueTheme
import com.xinyue.reader.core.ui.XinYueIcons
import com.xinyue.reader.feature.backup.BackupRoute
import com.xinyue.reader.feature.importing.ImportRoute
import com.xinyue.reader.feature.library.LibraryRoute
import com.xinyue.reader.feature.library.StatisticsRoute
import com.xinyue.reader.feature.reader.GlobalReaderSettingsRoute
import com.xinyue.reader.feature.reader.GlobalReaderSettingsSection
import com.xinyue.reader.feature.reader.ReaderRoute
import com.xinyue.reader.feature.settings.SettingsEntry
import com.xinyue.reader.feature.settings.SettingsScreen
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.serialization.Serializable

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private var volumePageTurnHandler: ((Int) -> Boolean)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            XinYueTheme {
                XinYueApp(
                    registerVolumePageTurnHandler = { handler -> volumePageTurnHandler = handler },
                    onExit = ::finish,
                )
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (event.repeatCount == 0) {
            val direction = when (keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> -1
                KeyEvent.KEYCODE_VOLUME_DOWN -> 1
                else -> null
            }
            if (direction != null && volumePageTurnHandler?.invoke(direction) == true) return true
        }
        return super.onKeyDown(keyCode, event)
    }
}

internal enum class MainTab(
    val contentDescription: String,
    @DrawableRes val outlinedIconRes: Int,
    @DrawableRes val selectedIconRes: Int,
) {
    LIBRARY("书架", XinYueIcons.LibraryOutlined, XinYueIcons.LibrarySelected),
    STATISTICS("统计", XinYueIcons.Information, XinYueIcons.Information),
    SETTINGS("设置", XinYueIcons.SettingsOutlined, XinYueIcons.SettingsSelected),
    ;

    @DrawableRes
    fun iconRes(selected: Boolean): Int = if (selected) selectedIconRes else outlinedIconRes
}

internal fun mainTabForState(name: String): MainTab =
    MainTab.entries.firstOrNull { it.name == name } ?: MainTab.LIBRARY

@Serializable
private data object RootDestination : NavKey

@Serializable
private data object ImportDestination : NavKey

@Serializable
private data object BackupDestination : NavKey

@Serializable
private data class ReaderDestination(val bookId: String) : NavKey

@Serializable
private data class StatisticsDestination(val bookId: String? = null) : NavKey

@Serializable
private data class GlobalSettingsDestination(val sectionName: String) : NavKey

@Composable
private fun XinYueApp(
    registerVolumePageTurnHandler: (((Int) -> Boolean)?) -> Unit,
    onExit: () -> Unit,
) {
    val backStack = rememberNavBackStack(RootDestination)
    var selectedTabName by rememberSaveable { mutableStateOf(MainTab.LIBRARY.name) }
    var librarySearchActive by rememberSaveable { mutableStateOf(false) }
    val selectedTab = mainTabForState(selectedTabName)
    val stateHolder = rememberSaveableStateHolder()

    BackHandler(
        enabled = backStack.size == 1 && !librarySearchActive,
        onBack = onExit,
    )
    NavDisplay(
        backStack = backStack,
        onBack = { if (backStack.size > 1) backStack.removeLastOrNull() else onExit() },
        entryProvider = entryProvider {
            entry<RootDestination> {
                MainRootShell(
                    selectedTab = selectedTab,
                    onSelectTab = { selectedTabName = it.name },
                    showBottomBar = !(selectedTab == MainTab.LIBRARY && librarySearchActive),
                ) { tab ->
                    stateHolder.SaveableStateProvider(tab.name) {
                        when (tab) {
                            MainTab.LIBRARY -> LibraryRoute(
                                isSearchActive = librarySearchActive,
                                onSearchActiveChange = { librarySearchActive = it },
                                onOpenBook = { backStack.add(ReaderDestination(it)) },
                                onImport = { backStack.add(ImportDestination) },
                                onOpenStatistics = { backStack.add(StatisticsDestination(it)) },
                                onOpenBackup = { backStack.add(BackupDestination) },
                                showTopActions = false,
                            )
                            MainTab.STATISTICS -> StatisticsRoute(
                                bookId = null,
                                onBack = {},
                            )
                            MainTab.SETTINGS -> SettingsScreen { entry ->
                                when (entry) {
                                    SettingsEntry.BACKUP -> backStack.add(BackupDestination)
                                    SettingsEntry.APPEARANCE -> backStack.add(GlobalSettingsDestination(GlobalReaderSettingsSection.APPEARANCE.name))
                                    SettingsEntry.THEMES -> backStack.add(GlobalSettingsDestination(GlobalReaderSettingsSection.THEMES.name))
                                    SettingsEntry.FONTS -> backStack.add(GlobalSettingsDestination(GlobalReaderSettingsSection.FONTS.name))
                                    SettingsEntry.TYPOGRAPHY -> backStack.add(GlobalSettingsDestination(GlobalReaderSettingsSection.TYPOGRAPHY.name))
                                    SettingsEntry.BEHAVIOR -> backStack.add(GlobalSettingsDestination(GlobalReaderSettingsSection.BEHAVIOR.name))
                                    SettingsEntry.INFORMATION -> backStack.add(GlobalSettingsDestination(GlobalReaderSettingsSection.INFORMATION.name))
                                }
                            }
                        }
                    }
                }
            }
            entry<ImportDestination> { ImportRoute(onBack = { backStack.removeLastOrNull() }) }
            entry<BackupDestination> { BackupRoute(onBack = { backStack.removeLastOrNull() }) }
            entry<ReaderDestination> { destination ->
                ReaderRoute(
                    bookId = destination.bookId,
                    onBack = { backStack.removeLastOrNull() },
                    registerVolumePageTurnHandler = registerVolumePageTurnHandler,
                )
            }
            entry<StatisticsDestination> { destination ->
                StatisticsRoute(bookId = destination.bookId, onBack = { backStack.removeLastOrNull() })
            }
            entry<GlobalSettingsDestination> { destination ->
                GlobalReaderSettingsRoute(
                    section = GlobalReaderSettingsSection.valueOf(destination.sectionName),
                    onBack = { backStack.removeLastOrNull() },
                )
            }
        },
    )
}

@Composable
private fun MainRootShell(
    selectedTab: MainTab,
    onSelectTab: (MainTab) -> Unit,
    showBottomBar: Boolean,
    content: @Composable (MainTab) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val useRail = showBottomBar && maxWidth >= 600.dp
        Row(Modifier.fillMaxSize()) {
            if (useRail) {
                MainNavigationRail(
                    selectedTab = selectedTab,
                    onSelectTab = onSelectTab,
                )
            }
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(bottom = if (showBottomBar && !useRail) 96.dp else 0.dp),
            ) {
                content(selectedTab)
            }
        }
        if (showBottomBar && !useRail) {
            MainFloatingNavigationBar(
                selectedTab = selectedTab,
                onSelectTab = onSelectTab,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
private fun MainNavigationRail(
    selectedTab: MainTab,
    onSelectTab: (MainTab) -> Unit,
) {
    NavigationRail(
        modifier = Modifier
            .fillMaxHeight()
            .padding(vertical = 12.dp),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        MainTab.entries.forEach { tab ->
            val isSelected = selectedTab == tab
            NavigationRailItem(
                selected = isSelected,
                onClick = { onSelectTab(tab) },
                icon = {
                    Icon(
                        painter = painterResource(tab.iconRes(isSelected)),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                },
                label = { androidx.compose.material3.Text(tab.contentDescription) },
                alwaysShowLabel = true,
                modifier = Modifier
                    .testTag("nav_${tab.name.lowercase()}")
                    .semantics {
                        contentDescription = tab.contentDescription
                    },
            )
        }
    }
}

@Composable
private fun MainFloatingNavigationBar(
    selectedTab: MainTab,
    onSelectTab: (MainTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(top = 0.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp),
            shape = RectangleShape,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column {
                androidx.compose.material3.HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MainTab.entries.forEach { tab ->
                        MainFloatingNavigationItem(
                            tab = tab,
                            selected = selectedTab == tab,
                            onClick = { onSelectTab(tab) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MainFloatingNavigationItem(
    tab: MainTab,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxHeight()
            .testTag("nav_${tab.name.lowercase()}")
            .semantics {
                contentDescription = tab.contentDescription
                role = Role.Tab
                this.selected = selected
            },
        shape = RectangleShape,
        color = androidx.compose.ui.graphics.Color.Transparent,
        contentColor = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(tab.iconRes(selected)),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
