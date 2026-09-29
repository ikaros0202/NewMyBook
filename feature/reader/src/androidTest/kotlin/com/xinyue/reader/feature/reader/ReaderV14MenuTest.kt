package com.xinyue.reader.feature.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xinyue.reader.core.domain.model.AnnotationKind
import com.xinyue.reader.core.domain.model.AnnotationExportFormat
import com.xinyue.reader.core.domain.model.BookSearchIndexState
import com.xinyue.reader.core.domain.model.BookSearchIndexStatus
import com.xinyue.reader.core.domain.model.ReaderAnnotation
import com.xinyue.reader.core.domain.model.ReaderSettings
import com.xinyue.reader.core.domain.model.TextRangeAnchor
import com.xinyue.reader.core.text.DetectedChapter
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderV14MenuTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun approvedMenuUsesTopBookmarkAndMoreWithFiveLabeledActions() {
        compose.setContent {
            MaterialTheme {
                ReaderControls(
                    title = "公开样例",
                    chapterTitle = "第一章",
                    bookProgressPercent = 19,
                    onBack = {},
                    onOpenNavigation = {},
                    onOpenChapterManagement = {},
                    onOpenProgress = {},
                    onOpenSearch = {},
                    onOpenSettings = {},
                    onOpenNotes = {},
                    onToggleBookmark = {},
                    isBookmarked = false,
                    isSpeaking = false,
                    onLockTouch = {},
                    backgroundColor = Color(0xFFF7F3EA),
                    contentColor = Color(0xFF292721),
                    onToggleSpeech = {},
                )
            }
        }

        val tags = listOf(
            "reader-menu-back",
            "reader-menu-bookmark",
            "reader-menu-more",
            "reader-menu-navigation",
            "reader-menu-search",
            "reader-menu-speech",
            "reader-menu-appearance",
            "reader-menu-notes",
        )
        tags.forEach { tag ->
            val node = compose.onNodeWithTag(tag).assertIsDisplayed()
            val bounds = node.fetchSemanticsNode().boundsInRoot
            val minimum = 48.dp.value * compose.density.density
            assertTrue("$tag width is below 48dp", bounds.width >= minimum)
            assertTrue("$tag height is below 48dp", bounds.height >= minimum)
        }
        listOf("目录", "搜索", "朗读", "外观", "笔记").forEach { label ->
            compose.onNodeWithText(label).assertIsDisplayed()
        }
        val actionsHeight = compose.onNodeWithTag("reader-menu-actions")
            .fetchSemanticsNode().boundsInRoot.height
        assertTrue(
            "bottom actions should be at least 56dp",
            actionsHeight >= 56.dp.value * compose.density.density,
        )
        val progressHeight = compose.onNodeWithTag("reader-progress-info")
            .fetchSemanticsNode().boundsInRoot.height
        assertTrue(
            "progress row should stay compact",
            progressHeight <= 40.dp.value * compose.density.density,
        )
        compose.onNodeWithTag("reader-progress-slider").assertDoesNotExist()
        compose.onNodeWithText("进度").assertDoesNotExist()

        compose.onNodeWithContentDescription("更多阅读操作").performClick()
        compose.onNodeWithText("目录管理").assertIsDisplayed()
        compose.onNodeWithText("定位进度").assertIsDisplayed()
        compose.onNodeWithText("触控锁").assertIsDisplayed()
    }

    @Test
    fun emptySearchShowsExplicitStateWhenReadyAndQueryHasNoMatches() {
        compose.setContent {
            MaterialTheme {
                SearchDialog(
                    results = emptyList(),
                    chapters = emptyList(),
                    indexState = BookSearchIndexState(
                        bookId = "book",
                        status = BookSearchIndexStatus.READY,
                        indexedUtf16Length = 100,
                    ),
                    contentLength = 100,
                    isSearching = false,
                    onSearch = {},
                    onRebuildIndex = {},
                    onCancelIndex = {},
                    onSelect = {},
                    onDismiss = {},
                )
            }
        }

        compose.onNode(hasSetTextAction()).performTextInput("不存在的词")
        compose.onNodeWithTag("reader-search-submit").performClick()
        compose.onNodeWithTag("reader-search-empty").assertIsDisplayed()
        compose.onNodeWithText("未找到匹配内容").assertIsDisplayed()
    }

    @Test
    fun drawerHasDirectoryAndBookmarksWithoutChapterPercentages() {
        compose.setContent {
            MaterialTheme {
                ReaderNavigationDrawer(
                    bookTitle = "公开样例",
                    chapters = listOf(
                        DetectedChapter("第一章", 0),
                        DetectedChapter("第二章", 100),
                    ),
                    bookmarks = emptyList(),
                    anchorOffset = 20,
                    content = "公开正文",
                    contentStartOffset = 0,
                    initialTab = ReaderNavigationTab.CHAPTERS,
                    onTabChanged = {},
                    onSelectChapter = {},
                    onSelectBookmark = {},
                    onDeleteBookmark = {},
                    onDismiss = {},
                )
            }
        }

        compose.onNodeWithTag("reader-navigation-drawer", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("• 目录").assertIsDisplayed()
        compose.onNodeWithText("书签").assertIsDisplayed()
        compose.onAllNodesWithText("%", substring = true).assertCountEquals(0)
        compose.onNodeWithText("第二章").assertIsDisplayed()
        compose.onNodeWithText("书签").performClick()
        compose.onNodeWithText("当前书籍还没有书签", substring = true).assertIsDisplayed()
    }

    @Test
    fun commonAppearanceExposesLineParagraphSpacingAndPageTurnMode() {
        compose.setContent {
            MaterialTheme {
                var settings by remember { mutableStateOf(ReaderSettings()) }
                Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    ReaderTypographyControls(
                        settings = settings,
                        importedFonts = emptyList(),
                        advanced = false,
                        includeGlobalBehavior = true,
                        onPreview = { settings = it },
                        onImportFont = {},
                        onRemoveImportedFont = {},
                        includeFontManagement = false,
                    )
                }
            }
        }

        compose.onNodeWithText("行间距", substring = true).assertExists()
        compose.onNodeWithText("段间距", substring = true).assertExists()
        compose.onNodeWithText("翻页方式").assertExists()
        compose.onNodeWithText("滑动", substring = true).assertExists()
        compose.onNodeWithText("覆盖", substring = true).assertExists()
        compose.onNodeWithText("无动画", substring = true).assertExists()
        compose.onNodeWithContentDescription("行间距，1.60 倍")
            .performSemanticsAction(SemanticsActions.SetProgress) { setProgress ->
                assertTrue(setProgress(1.2f))
            }
        compose.onNodeWithContentDescription("行间距，1.20 倍").assertExists()
        compose.onNodeWithContentDescription("段间距，0.00 em")
            .performSemanticsAction(SemanticsActions.SetProgress) { setProgress ->
                assertTrue(setProgress(0.05f))
            }
        compose.onNodeWithContentDescription("段间距，0.05 em").assertExists()
    }

    @Test
    fun emptyNotesStillOpensAndShowsAnEmptyStateHint() {
        compose.setContent {
            MaterialTheme {
                ReaderAnnotationPanel(
                    annotations = emptyList(),
                    onSelect = {},
                    onEditNote = { _, _ -> },
                    onDelete = {},
                    onDismiss = {},
                )
            }
        }

        compose.onNodeWithText("笔记").assertIsDisplayed()
        compose.onNodeWithText("当前书籍还没有笔记", substring = true).assertIsDisplayed()
    }

    @Test
    fun notesReplaceTheEmptyStateHint() {
        compose.setContent {
            MaterialTheme {
                ReaderAnnotationPanel(
                    annotations = listOf(
                        ReaderAnnotation(
                            id = "note",
                            bookId = "book",
                            kind = AnnotationKind.NOTE,
                            range = TextRangeAnchor(10, 20, "", "", null),
                            color = null,
                            note = "记住这条线索",
                            createdAtEpochMillis = 1,
                            updatedAtEpochMillis = 1,
                        ),
                    ),
                    onSelect = {},
                    onEditNote = { _, _ -> },
                    onDelete = {},
                    onDismiss = {},
                )
            }
        }

        compose.onNodeWithText("记住这条线索").assertIsDisplayed()
        compose.onAllNodesWithText("当前书籍还没有笔记", substring = true).assertCountEquals(0)
    }

    @Test
    fun notesPanelOffersMarkdownJsonAndOptionalBookmarksExport() {
        var chosenFormat: AnnotationExportFormat? = null
        var includeBookmarks = false
        compose.setContent {
            MaterialTheme {
                ReaderAnnotationPanel(
                    annotations = emptyList(),
                    onSelect = {},
                    onEditNote = { _, _ -> },
                    onDelete = {},
                    onDismiss = {},
                    onExport = { format, include ->
                        chosenFormat = format
                        includeBookmarks = include
                    },
                )
            }
        }

        compose.onNodeWithText("导出批注").performClick()
        compose.onNodeWithText("包含书签").performClick()
        compose.onNodeWithText("导出 JSON").performClick()

        assertTrue(chosenFormat == AnnotationExportFormat.JSON)
        assertTrue(includeBookmarks)
    }
}
