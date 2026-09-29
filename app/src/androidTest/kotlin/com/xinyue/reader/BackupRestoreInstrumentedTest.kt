package com.xinyue.reader

import android.content.Context
import android.widget.EditText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.xinyue.reader.core.data.BackupAnnotationRecord
import com.xinyue.reader.core.data.BackupBookRecord
import com.xinyue.reader.core.data.BackupBookMembershipRecord
import com.xinyue.reader.core.data.BackupBookSettingsRecord
import com.xinyue.reader.core.data.BackupBookSource
import com.xinyue.reader.core.data.BackupCatalogSnapshot
import com.xinyue.reader.core.data.BackupDailyStatRecord
import com.xinyue.reader.core.data.BackupEquivalenceVerifier
import com.xinyue.reader.core.data.BackupFontRecord
import com.xinyue.reader.core.data.BackupFontSource
import com.xinyue.reader.core.data.BackupGlobalSettingsRecord
import com.xinyue.reader.core.data.BackupGroupRecord
import com.xinyue.reader.core.data.BackupProgressRecord
import com.xinyue.reader.core.data.BackupSessionRecord
import com.xinyue.reader.core.data.BackupThemeRecord
import com.xinyue.reader.core.data.RestoreDatabaseGateway
import com.xinyue.reader.core.domain.model.ReaderFontRef
import com.xinyue.reader.core.domain.model.ReaderSettings
import com.xinyue.reader.core.domain.model.ReaderSettingsOverrides
import com.xinyue.reader.core.domain.model.ReaderThemeManualOverride
import com.xinyue.reader.core.domain.model.ReaderThemeSchedule
import dagger.hilt.android.EntryPointAccessors
import java.io.File
import java.security.MessageDigest
import java.util.regex.Pattern
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupRestoreInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device get() = UiDevice.getInstance(instrumentation)
    private val context get() = instrumentation.targetContext.applicationContext
    private val gateway: RestoreDatabaseGateway
        get() = EntryPointAccessors.fromApplication(context, BackupDebugEntryPoint::class.java)
            .restoreDatabaseGateway()

    @Before
    fun seedCompleteLibrary() = runBlocking {
        gateway.replaceAll(BackupCatalogSnapshot())
        File(context.filesDir, "books").deleteRecursively()
        File(context.filesDir, "fonts").deleteRecursively()
        gateway.replaceAll(completeCatalogAndFiles(context.filesDir))
        compose.waitForIdle()
    }

    @Test
    fun realDocumentsUiExportClearAndOverwriteRestoreIsEquivalent() {
        compose.onNodeWithTag("nav_library", useUnmergedTree = true).performClick()
        importPrivateSampleIfRequested()
        val expected = runBlocking { BackupEquivalenceVerifier(context.filesDir).capture(gateway.snapshot()) }

        openBackupHome()
        compose.onNodeWithText("开始导出").performClick()
        compose.onNodeWithText("选择保存位置").performClick()
        val backupName = saveFromDocumentsUi()
        awaitApp()
        compose.waitUntil(60_000) {
            compose.onAllNodesWithText("操作完成").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("操作完成").assertIsDisplayed()

        runBlocking { gateway.replaceAll(BackupCatalogSnapshot()) }
        File(context.filesDir, "books").deleteRecursively()
        File(context.filesDir, "fonts").deleteRecursively()

        compose.onNodeWithText("返回备份与恢复").performClick()
        compose.onNodeWithText("选择备份文件").performClick()
        selectExportedDocumentFromDocumentsUi(backupName)
        awaitApp()
        compose.waitUntil(60_000) {
            compose.onAllNodesWithText("恢复预览").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("覆盖整个书架").performClick()
        compose.onNodeWithTag("restore_confirm").performScrollTo().performClick()
        compose.onNodeWithText("创建快照并覆盖").performClick()
        compose.waitUntil(60_000) {
            compose.onAllNodesWithText("操作完成").fetchSemanticsNodes().isNotEmpty()
        }

        val result = runBlocking { BackupEquivalenceVerifier(context.filesDir).compare(expected, gateway.snapshot()) }
        assertTrue(result.mismatches.joinToString(), result.equivalent)
        assertTrue(result.mismatches.isEmpty())
        assertTrue(File(context.filesDir, "restore-journals").listFiles().orEmpty().isEmpty())
        assertTrue(File(context.filesDir, "restore-snapshots").listFiles().orEmpty().isEmpty())
        assertTrue(File(context.filesDir, "backup-staging").listFiles().orEmpty().isEmpty())
    }

    @Test
    fun realDocumentsUiAnnotationExportIncludesExplicitBookmarks() {
        var exportName: String? = null
        try {
            openBackupHome()
            compose.onNodeWithTag("backup_home_list").performScrollToIndex(3)
            compose.onNodeWithText("选择导出格式").performClick()
            compose.onNodeWithText("包含书签").performClick()
            compose.onNodeWithText("导出 JSON").performClick()
            exportName = saveFromDocumentsUi("xinyue-all-annotations.json")
            awaitApp()
            compose.waitUntil(60_000) {
                compose.onAllNodesWithText("已导出", substring = true).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("已导出", substring = true).assertIsDisplayed()

            val safeName = requireNotNull(exportName)
            val exported = device.executeShellCommand("cat /sdcard/Download/$safeName")
            assertTrue("annotation export version missing", exported.contains("\"schemaVersion\": 1"))
            assertTrue("explicit bookmark missing", exported.contains("\"id\": \"device-bookmark\""))
            assertTrue("note missing", exported.contains("\"id\": \"device-note\""))
        } finally {
            exportName?.let(::deleteSharedDownload)
        }
    }

    @Test
    fun realDocumentsUiHandoffWithTextImportsNewBookAndRestoresReadingState() {
        val source = runBlocking { gateway.snapshot() }
        val expectedBook = source.books.single { it.id == "device-book-a" }
        val expectedProgress = source.progress.single { it.bookId == expectedBook.id }
        val expectedAnnotationIds = source.annotations
            .filter { it.bookId == expectedBook.id }
            .map { it.id }
            .toSet()
        var handoffName: String? = null
        try {
            openBackupHome()
            compose.onNodeWithTag("backup_home_list").performScrollToIndex(4)
            compose.onNodeWithText("选择书籍").performClick()
            compose.onNodeWithTag("handoff_export_dialog").assertIsDisplayed()
            compose.onNodeWithText(expectedBook.title).performClick()
            compose.onNodeWithText("包含小说正文").performClick()
            compose.onNodeWithText("选择保存位置").performClick()
            handoffName = saveFromDocumentsUi("xinyue-device-handoff.xinyuehandoff")
            awaitApp()
            compose.waitUntil(60_000) {
                compose.onAllNodesWithText("接力操作完成").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("接力包已完整写入并通过本地校验。").assertIsDisplayed()

            runBlocking { gateway.replaceAll(BackupCatalogSnapshot()) }
            File(context.filesDir, "books").deleteRecursively()
            File(context.filesDir, "fonts").deleteRecursively()

            compose.onNodeWithText("返回本地数据").performClick()
            compose.onNodeWithTag("backup_home_list").performScrollToIndex(5)
            compose.onNodeWithText("选择 .xinyuehandoff").performClick()
            selectExportedDocumentFromDocumentsUi(requireNotNull(handoffName))
            awaitApp()
            compose.waitUntil(60_000) {
                compose.onAllNodesWithTag("handoff_preview").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("将导入为一本新书").assertIsDisplayed()
            compose.onNodeWithTag("handoff_import_confirm").performClick()
            compose.waitUntil(60_000) {
                compose.onAllNodesWithTag("handoff_result").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("接力操作完成").assertIsDisplayed()
            compose.onNodeWithText("已导入新书并应用阅读状态。").assertIsDisplayed()

            val restored = runBlocking { gateway.snapshot() }
            val restoredBook = restored.books.single()
            assertTrue(restoredBook.title == expectedBook.title)
            assertTrue(restoredBook.contentSha256 == expectedBook.contentSha256)
            assertTrue(restoredBook.seriesName == expectedBook.seriesName)
            assertTrue(restoredBook.seriesOrder == expectedBook.seriesOrder)
            val restoredProgress = restored.progress.single { it.bookId == restoredBook.id }
            assertTrue(restoredProgress.offset == expectedProgress.offset)
            assertTrue(restoredProgress.contextHash == expectedProgress.contextHash)
            assertTrue(
                restored.annotations.filter { it.bookId == restoredBook.id }.map { it.id }.toSet() ==
                    expectedAnnotationIds,
            )
            assertTrue(restored.memberships.single().bookId == restoredBook.id)
            assertTrue(restored.groups.single().name == "设备公开分组")
            val restoredSource = restored.bookSources.single { it.bookId == restoredBook.id }
            assertTrue(File(context.filesDir, restoredSource.originalRelativePath).isFile)
            assertTrue(File(context.filesDir, restoredSource.normalizedRelativePath).isFile)
            assertTrue(File(context.filesDir, "handoff-staging").listFiles().orEmpty().isEmpty())
            assertTrue(File(context.filesDir, "backup-staging").listFiles().orEmpty().isEmpty())
            assertTrue(File(context.filesDir, "restore-journals").listFiles().orEmpty().isEmpty())
            assertTrue(File(context.filesDir, "restore-snapshots").listFiles().orEmpty().isEmpty())

            compose.onNodeWithText("返回本地数据").performClick()
            device.pressBack()
            compose.waitUntil(10_000) {
                compose.onAllNodesWithTag("nav_library", useUnmergedTree = true)
                    .fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("nav_library", useUnmergedTree = true).performClick()
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText(expectedBook.title).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText(expectedBook.title).performClick()
            compose.waitUntil(30_000) {
                compose.onAllNodesWithTag("reader_root").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("reader_root").assertIsDisplayed()
        } finally {
            handoffName?.let(::deleteSharedDownload)
        }
    }

    private fun openBackupHome() {
        compose.onNodeWithTag("nav_settings", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("settings_backup"))
        compose.onNodeWithTag("settings_backup").performClick()
        compose.onNodeWithTag("backup_root").assertIsDisplayed()
    }

    private fun importPrivateSampleIfRequested() {
        val fileName = InstrumentationRegistry.getArguments().getString("privateSampleFile") ?: return
        check(fileName.matches(Regex("[A-Za-z0-9._-]+"))) { "private sample alias must be neutral" }
        val importTag = if (compose.onAllNodesWithTag("library_empty_import").fetchSemanticsNodes().isNotEmpty()) {
            "library_empty_import"
        } else {
            "library_import"
        }
        compose.onNodeWithTag(importTag).performClick()
        compose.onNodeWithText("选择 TXT 文件").performClick()
        check(device.wait(Until.hasObject(DOCUMENTS_PACKAGE), 10_000)) { "DocumentsUI did not open for TXT import" }
        val selector = By.res("android:id/title").text(fileName)
        var file = device.wait(Until.findObject(selector), 3_000)
        if (file == null) {
            openDownloads()
            file = device.wait(Until.findObject(selector), 5_000)
        }
        repeat(15) {
            if (file != null) return@repeat
            device.swipe(device.displayWidth / 2, device.displayHeight * 4 / 5, device.displayWidth / 2, device.displayHeight / 4, 24)
            device.waitForIdle()
            file = device.findObject(selector)
        }
        if (file == null) {
            val searchOpened = clickFresh(By.desc("Search"), 2_000) || clickFresh(By.desc("搜索"), 2_000)
            if (searchOpened) {
                val input = device.wait(
                    Until.findObject(By.res("com.google.android.documentsui:id/search_src_text")), 3_000,
                )
                input?.text = fileName
                device.executeShellCommand("input keyevent 66")
                file = device.wait(Until.findObject(selector), 15_000)
            }
        }
        check(file != null && clickFresh(selector, 5_000)) { "private sample alias missing from DocumentsUI" }
        awaitApp()
        compose.waitUntil(60_000) {
            compose.onAllNodesWithText("导入成功").fetchSemanticsNodes().isNotEmpty()
        }
        device.pressBack()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("library_root").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun saveFromDocumentsUi(fallbackName: String = "xinyue-device-test.xinyuebackup"): String {
        check(device.wait(Until.hasObject(DOCUMENTS_PACKAGE), 10_000)) { "DocumentsUI did not open for export" }
        openDownloads()
        val nameField = device.wait(Until.findObject(By.clazz(EditText::class.java)), 5_000)
            ?: device.wait(Until.findObject(By.res("android:id/title")), 3_000)
            ?: error("DocumentsUI filename field missing")
        val name = nameField.text.orEmpty().ifBlank { fallbackName }
        if (nameField.text.isNullOrBlank()) nameField.text = name
        check(clickFresh(By.text(Pattern.compile("保存|Save", Pattern.CASE_INSENSITIVE)), 5_000)) {
            "DocumentsUI save action missing"
        }
        val replace = device.wait(
            Until.findObject(By.text(Pattern.compile("替换|Replace", Pattern.CASE_INSENSITIVE))),
            1_500,
        )
        replace?.click()
        return name
    }

    private fun selectExportedDocumentFromDocumentsUi(fileName: String) {
        check(device.wait(Until.hasObject(DOCUMENTS_PACKAGE), 10_000)) { "DocumentsUI did not open for restore" }
        openDownloads()
        // AOSP DocumentsUI may append .zip from the application/zip MIME type.
        val displayedName = Pattern.compile("^${Pattern.quote(fileName)}(?:\\.zip)?$")
        val selector = By.res("android:id/title").text(displayedName)
        var file = device.wait(Until.findObject(selector), 3_000)
        if (file == null) {
            val searchOpened = clickFresh(By.desc("Search"), 2_000) || clickFresh(By.desc("搜索"), 2_000)
            if (searchOpened) {
                val input = device.wait(
                    Until.findObject(By.res("com.google.android.documentsui:id/search_src_text")), 3_000,
                )
                input?.text = fileName
                device.executeShellCommand("input keyevent 66")
                file = device.wait(Until.findObject(selector), 10_000)
            }
        }
        check(file != null && clickFresh(selector, 5_000)) { "exported backup missing from DocumentsUI" }
    }

    private fun deleteSharedDownload(fileName: String) {
        check(fileName.matches(Regex("[A-Za-z0-9._-]{1,200}"))) { "shared test filename must be neutral" }
        device.executeShellCommand(
            "rm -f /sdcard/Download/$fileName /sdcard/Download/$fileName.zip",
        )
    }

    private fun openDownloads() {
        clickFresh(By.desc("Show roots"), 1_500) ||
            clickFresh(By.desc("显示根目录"), 1_500)
        clickFresh(By.res("android:id/title").text(Pattern.compile("Downloads|下载")), 3_000) ||
            clickFresh(By.text(Pattern.compile("Downloads|下载")), 3_000)
    }

    private fun awaitApp() {
        check(device.wait(Until.hasObject(By.pkg(context.packageName)), 15_000)) { "app did not resume" }
        compose.waitForIdle()
    }

    private fun clickFresh(selector: BySelector, timeoutMillis: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMillis
        do {
            val candidate = device.findObject(selector)
            if (candidate != null && runCatching {
                    val bounds = candidate.visibleBounds
                    device.click(bounds.centerX(), bounds.centerY())
                }.getOrDefault(false)
            ) return true
            Thread.sleep(100)
        } while (System.currentTimeMillis() < deadline)
        return false
    }

    private fun completeCatalogAndFiles(root: File): BackupCatalogSnapshot {
        val fontId = "device-font"
        val groupId = "device-group"
        val themeId = "device-theme"
        val font = File(root, "fonts/$fontId/font.bin").also { it.parentFile!!.mkdirs(); it.writeText("public-device-font") }
        val books = listOf("device-book-a", "device-book-b").mapIndexed { index, id ->
            val dir = File(root, "books/$id").apply { mkdirs() }
            File(dir, "original.txt").writeText("public-device-original-$index")
            File(dir, "content.txt").writeText("same-public-device-normalized")
            File(dir, "offsets.xidx").writeBytes(byteArrayOf(1, index.toByte()))
            if (index == 0) File(dir, "cover.webp").writeText("public-device-cover")
            BackupBookRecord(
                id = id,
                title = "设备公开书名-$index",
                author = "设备公开作者",
                originalFileName = "public-$index.txt",
                charsetName = "UTF-8",
                contentSha256 = sha("same-public-device-normalized".encodeToByteArray()),
                contentLength = File(dir, "content.txt").length(),
                createdAtEpochMillis = index + 1L,
                lastOpenedAtEpochMillis = index + 10L,
                seriesName = "设备公开系列",
                seriesOrder = index + 1,
                finished = index == 1,
                originalAssetPath = "assets/books/$id/original.txt",
                normalizedAssetPath = "assets/books/$id/content.txt",
                offsetIndexAssetPath = "assets/books/$id/offsets.xidx",
                customCoverAssetPath = if (index == 0) "assets/books/$id/cover.webp" else null,
            )
        }
        val settings = ReaderSettings(font = ReaderFontRef.Imported(fontId), fontSizeSp = 23f)
        return BackupCatalogSnapshot(
            books = books,
            groups = listOf(BackupGroupRecord(groupId, "设备公开分组", 0, 1, 2)),
            memberships = books.map { BackupBookMembershipRecord(it.id, groupId) },
            progress = books.mapIndexed { index, book ->
                BackupProgressRecord(book.id, index + 1L, "anchor-$index", "prefix", "suffix", book.contentLength, 20 + index.toLong())
            },
            annotations = listOf(
                annotation("device-bookmark", books[0].id, "BOOKMARK", null),
                annotation("device-highlight", books[0].id, "HIGHLIGHT", null),
                annotation("device-note", books[1].id, "NOTE", "设备公开批注"),
            ),
            globalSettings = BackupGlobalSettingsRecord(
                json.encodeToString(settings),
                json.encodeToString(TestStoredSchedule(ReaderThemeSchedule(lightThemeId = themeId, darkThemeId = themeId))),
                30,
            ),
            bookSettings = listOf(
                BackupBookSettingsRecord(
                    books[0].id,
                    json.encodeToString(ReaderSettingsOverrides(font = ReaderFontRef.Imported(fontId), fontSizeSp = 25f)),
                    31,
                ),
            ),
            themes = listOf(BackupThemeRecord(themeId, "设备公开主题", json.encodeToString(settings), false, 30)),
            fonts = listOf(BackupFontRecord(fontId, "设备公开字体", sha(font.readBytes()), font.length(), 1, "assets/fonts/$fontId/font.bin")),
            sessions = listOf(
                BackupSessionRecord("device-session-a", books[0].id, 1, 2, 3, 1),
                BackupSessionRecord("device-session-b", books[1].id, 4, 5, 6, 1),
            ),
            dailyStats = listOf(
                BackupDailyStatRecord(books[0].id, 20_000, 1, 1),
                BackupDailyStatRecord(books[1].id, 20_001, 1, 1),
            ),
            bookSources = books.map { book ->
                BackupBookSource(
                    book.id, "books/${book.id}/original.txt", "books/${book.id}/content.txt",
                    "books/${book.id}/offsets.xidx",
                    if (book.customCoverAssetPath != null) "books/${book.id}/cover.webp" else null,
                )
            },
            fontSources = listOf(BackupFontSource(fontId, "fonts/$fontId/font.bin")),
        )
    }

    private fun annotation(id: String, bookId: String, kind: String, note: String?) = BackupAnnotationRecord(
        id, bookId, kind, 1, if (kind == "BOOKMARK") 1 else 2, "prefix", "suffix",
        if (kind == "BOOKMARK") null else "c".repeat(64), "YELLOW", note, 1, 2,
    )

    private fun sha(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }

    @Serializable
    private data class TestStoredSchedule(
        val schedule: ReaderThemeSchedule,
        val manualOverride: ReaderThemeManualOverride? = null,
    )

    private companion object {
        val DOCUMENTS_PACKAGE = By.pkg(Pattern.compile("com\\.(google\\.android|android)\\.documentsui"))
        val json = Json { encodeDefaults = true; explicitNulls = false }
    }
}
