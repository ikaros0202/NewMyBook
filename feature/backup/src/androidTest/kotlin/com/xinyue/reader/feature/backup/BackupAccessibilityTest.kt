package com.xinyue.reader.feature.backup

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xinyue.reader.core.domain.model.BackupOptions
import com.xinyue.reader.core.domain.model.AnnotationExportFormat
import com.xinyue.reader.core.domain.model.HandoffBookSummary
import com.xinyue.reader.core.domain.model.HandoffPreview
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupAccessibilityTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun privacyChoicesAndActionsRemainReachableAtTwoHundredPercentFont() {
        compose.setContent {
            val systemDensity = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(systemDensity.density, 2f)) {
                MaterialTheme {
                    BackupPrivacyDialog(
                        options = BackupOptions(includeBookText = false, includeFonts = false),
                        onIncludeBookTextChanged = {},
                        onIncludeFontsChanged = {},
                        onConfirm = {},
                        onDismiss = {},
                    )
                }
            }
        }

        compose.onNodeWithContentDescription(
            "包含小说原文，未选，关闭后，新书正文不能从此备份独立恢复；已有同内容书籍仍可合并元数据",
        ).assertIsDisplayed()
        compose.onNodeWithContentDescription(
            "包含导入字体，未选，关闭后，导入字体不能独立恢复，相关设置将需要系统字体回退",
        ).assertIsDisplayed()
        listOf("取消", "选择保存位置").forEach { label ->
            val action = compose.onNode(hasText(label) and hasClickAction())
            action.assertIsDisplayed()
            val bounds = action.fetchSemanticsNode().boundsInRoot
            val minimum = 48f * compose.density.density
            assertTrue("$label width ${bounds.width}px is below 48dp ($minimum px)", bounds.width >= minimum)
            assertTrue("$label height ${bounds.height}px is below 48dp ($minimum px)", bounds.height >= minimum)
        }
    }

    @Test
    fun progressUpdatesAreAnnouncedPolitely() {
        compose.setContent {
            MaterialTheme {
                ProgressContent(
                    padding = PaddingValues(0.dp),
                    label = "正在安全恢复",
                    completed = 3,
                    total = 10,
                    canCancel = true,
                    onCancel = {},
                )
            }
        }

        compose.onNodeWithTag("backup_progress")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
    }

    @Test
    fun annotationExportDialogKeepsFormatAndBookmarkChoicesReachable() {
        var chosen = mutableStateOf<Pair<AnnotationExportFormat, Boolean>?>(null)
        compose.setContent {
            val systemDensity = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(systemDensity.density, 2f)) {
                MaterialTheme {
                    AnnotationExportDialog(
                        title = "导出全部书籍批注",
                        onChoose = { format, include -> chosen.value = format to include },
                        onDismiss = {},
                    )
                }
            }
        }

        compose.onNodeWithText("包含书签").performClick()
        compose.onNodeWithText("导出 JSON").performClick()
        assertTrue(chosen.value == (AnnotationExportFormat.JSON to true))
    }

    @Test
    fun handoffPrivacyChoiceAndConfirmRemainReachableAtTwoHundredPercentFont() {
        val includeText = mutableStateOf(false)
        compose.setContent {
            val systemDensity = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(systemDensity.density, 2f)) {
                MaterialTheme {
                    HandoffExportDialog(
                        books = listOf(HandoffBookSummary("book-1", "公开书名", "公开作者", "公开系列")),
                        selectedBookId = "book-1",
                        includeBookText = includeText.value,
                        onBookSelected = {},
                        onIncludeBookTextChanged = { includeText.value = it },
                        onConfirm = {},
                        onDismiss = {},
                    )
                }
            }
        }

        compose.onNodeWithText("包含小说正文").performClick()
        assertTrue(includeText.value)
        compose.onNodeWithText("选择保存位置").assertIsDisplayed()
    }

    @Test
    fun handoffPreviewExposesIdempotencyAndConfirmation() {
        compose.setContent {
            MaterialTheme {
                HandoffPreviewScreen(
                    preview = HandoffPreview(
                        stagedPlanToken = "token",
                        formatVersion = 1,
                        createdAtEpochMillis = 1,
                        sourceBookId = "book-1",
                        targetBookId = "local-book",
                        title = "公开书名",
                        includesBookText = false,
                        importsNewBook = false,
                        incomingProgressIsNewer = true,
                        annotationInsertCount = 1,
                        annotationConflictCopyCount = 0,
                        collectionCount = 1,
                        conflicts = emptyList(),
                    ),
                    selections = emptyMap(),
                    canImport = true,
                    onSelectionChanged = { _, _ -> },
                    onImport = {},
                    onCancel = {},
                )
            }
        }

        compose.onNodeWithTag("handoff_preview").assertIsDisplayed()
        compose.onNodeWithText("同一接力包可重复应用；已合并的标注不会再次创建副本。").assertIsDisplayed()
        compose.onNodeWithTag("handoff_import_confirm").assertIsDisplayed()
    }
}
