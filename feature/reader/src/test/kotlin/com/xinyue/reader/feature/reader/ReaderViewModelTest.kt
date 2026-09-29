package com.xinyue.reader.feature.reader

import com.google.common.truth.Truth.assertThat
import com.xinyue.reader.core.data.TextSource
import com.xinyue.reader.core.data.TextWindow
import com.xinyue.reader.core.data.ChapterIndexStore
import com.xinyue.reader.core.data.ChapterIndexSnapshot
import com.xinyue.reader.core.data.ImportSourceFactory
import com.xinyue.reader.core.data.ReaderPositionPreviewService
import com.xinyue.reader.core.text.ChapterRuleSet
import com.xinyue.reader.core.text.DetectedChapter
import com.xinyue.reader.core.text.TextFingerprint
import com.xinyue.reader.core.domain.model.Book
import com.xinyue.reader.core.domain.model.Bookmark
import com.xinyue.reader.core.domain.model.BookSearchIndexState
import com.xinyue.reader.core.domain.model.BookSearchIndexStatus
import com.xinyue.reader.core.domain.model.BookSearchResult
import com.xinyue.reader.core.domain.model.ReadingProgress
import com.xinyue.reader.core.domain.model.ReadingStatistics
import com.xinyue.reader.core.domain.model.ReaderSettings
import com.xinyue.reader.core.domain.model.ReaderSettingsOverrides
import com.xinyue.reader.core.domain.model.ReaderPageAnimation
import com.xinyue.reader.core.domain.model.ReaderThemePreset
import com.xinyue.reader.core.domain.model.ReaderThemeManualOverride
import com.xinyue.reader.core.domain.model.ReaderThemeSchedule
import com.xinyue.reader.core.domain.model.ThemeScheduleMode
import com.xinyue.reader.core.domain.model.ImportSource
import com.xinyue.reader.core.domain.model.ImportedFont
import com.xinyue.reader.core.domain.model.FontRemovalResult
import com.xinyue.reader.core.domain.model.TextAnchor
import com.xinyue.reader.core.domain.repository.BookRepository
import com.xinyue.reader.core.domain.repository.BookmarkRepository
import com.xinyue.reader.core.domain.repository.BookSearchRepository
import com.xinyue.reader.core.domain.repository.ReaderSettingsRepository
import com.xinyue.reader.core.domain.repository.ReaderThemeRepository
import com.xinyue.reader.core.domain.repository.ReaderThemeScheduleRepository
import com.xinyue.reader.core.domain.repository.ImportedFontRepository
import com.xinyue.reader.core.domain.repository.ReadingSessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import com.xinyue.reader.core.domain.time.EpochClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import kotlin.coroutines.CoroutineContext

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModelTest {
    @get:Rule
    private val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `statistics end failure does not block stable progress flush or reader exit`() =
        runTest(mainDispatcherRule.dispatcher) {
            val bookRepository = FakeRepository(savedOffset = 4, contentLength = 10)
            val statisticsRepository = FailingEndReadingSessionRepository()
            val viewModel = ReaderViewModel(
                bookRepository,
                FakeBookTextSource("鐢蹭箼涓欎竵鎴婂繁搴氳緵澹櫢"),
                EpochClock { 99L },
                FakeReaderSettingsRepository(),
                FakeBookPaginator(pageSize = 3),
                FakeBookmarkRepository(),
                Dispatchers.Unconfined,
                readingSessionRepository = statisticsRepository,
            )
            viewModel.onReaderForeground("book-1")
            viewModel.open("book-1", pageUtf16Units = 4)
            advanceUntilIdle()

            viewModel.goToPage(2)
            advanceUntilIdle()
            viewModel.flushProgress()
            assertThat(bookRepository.savedProgress?.anchor?.offset).isEqualTo(8L)
            statisticsRepository.failOnEnd = true
            var exited = false
            viewModel.leaveReader { exited = true }
            advanceUntilIdle()

            assertThat(bookRepository.savedProgress?.anchor?.offset).isEqualTo(8L)
            assertThat(statisticsRepository.endAttempts).isEqualTo(1)
            assertThat(exited).isTrue()
        }

    @Test
    fun `latest open wins when an older book lookup ignores cancellation`() =
        runTest(mainDispatcherRule.dispatcher) {
            val contentA = "A title\nA body\n"
            val contentB = "B title\nB body\n"
            val books = mapOf(
                "book-a" to sampleBook(contentA.length.toLong(), id = "book-a"),
                "book-b" to sampleBook(contentB.length.toLong(), id = "book-b"),
            )
            val repository = LatestWinsRepository(books)
            val chapterStore = LatestWinsChapterIndexStore(
                mapOf(
                    "book-a" to ChapterIndexSnapshot(
                        chapters = listOf(DetectedChapter("A title", 0)),
                        manuallyEdited = true,
                    ),
                    "book-b" to ChapterIndexSnapshot(
                        chapters = listOf(DetectedChapter("B title", 0)),
                        manuallyEdited = true,
                    ),
                ),
            )
            val searchRepository = RecordingSearchRepository(books)
            val settingsRepository = FakeReaderSettingsRepository()
            val textSource = LatestWinsTextSource(mapOf("book-a" to contentA, "book-b" to contentB))
            val viewModel = ReaderViewModel(
                repository,
                textSource,
                EpochClock { 99L },
                settingsRepository,
                FakeBookPaginator(pageSize = 3),
                FakeBookmarkRepository(),
                Dispatchers.Unconfined,
                chapterStore,
                searchRepository,
            )

            viewModel.open("book-a", pageUtf16Units = 3)
            runCurrent()
            assertThat(repository.bookALookupStarted.isCompleted).isTrue()

            viewModel.open("book-b", pageUtf16Units = 3)
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.book?.id).isEqualTo("book-b")

            repository.releaseBookA.complete(Unit)
            advanceUntilIdle()

            val final = viewModel.uiState.value
            assertThat(final.book?.id).isEqualTo("book-b")
            assertThat(final.content).isEqualTo(contentB)
            assertThat(final.layoutContent).isEqualTo(
                com.xinyue.reader.core.text.ReaderLayoutWhitespaceNormalizer.normalize(contentB),
            )
            assertThat(final.chapterTitleRanges).containsExactly(
                ReaderChapterTitleRange(0, "B title".length),
            )
            assertThat(final.chapters).containsExactly(DetectedChapter("B title", 0))
            assertThat(final.pages).isNotEmpty()
            assertThat(final.pages.first().startOffset).isEqualTo(0)
            assertThat(final.pages.last().endOffset).isEqualTo(contentB.length)
            assertThat(final.pages.joinToString(separator = "") { final.textFor(it) })
                .isEqualTo(contentB)
            assertThat(settingsRepository.observedBookIds).containsExactly("book-b")
            assertThat(settingsRepository.observedOverrideBookIds).containsExactly("book-b")
            assertThat(repository.markedOpenedBookIds).containsExactly("book-b")
            assertThat(textSource.readBookIds).containsExactly("book-b")
            assertThat(chapterStore.snapshotBookIds).containsExactly("book-b")
            assertThat(searchRepository.ensureIndexedBookIds).containsExactly("book-b")
        }

    @Test
    fun `latest open discards a stale global settings read before it reaches shared state`() =
        runTest(mainDispatcherRule.dispatcher) {
            val contentA = "A title\nA body\n"
            val contentB = "B title\nB body\n"
            val books = mapOf(
                "book-a" to sampleBook(contentA.length.toLong(), id = "book-a"),
                "book-b" to sampleBook(contentB.length.toLong(), id = "book-b"),
            )
            val repository = LatestWinsRepository(books).apply {
                releaseBookA.complete(Unit)
            }
            val settingsRepository = LatestWinsSettingsRepository(
                stale = ReaderSettings(fontSizeSp = 18f),
                current = ReaderSettings(fontSizeSp = 30f),
            )
            val viewModel = ReaderViewModel(
                repository,
                LatestWinsTextSource(mapOf("book-a" to contentA, "book-b" to contentB)),
                EpochClock { 99L },
                settingsRepository,
                FakeBookPaginator(pageSize = 3),
                FakeBookmarkRepository(),
                Dispatchers.Unconfined,
                LatestWinsChapterIndexStore(emptyMap()),
                RecordingSearchRepository(books),
            )

            viewModel.open("book-a", pageUtf16Units = 3)
            runCurrent()
            assertThat(settingsRepository.staleReadStarted.isCompleted).isTrue()

            viewModel.open("book-b", pageUtf16Units = 3)
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.book?.id).isEqualTo("book-b")

            settingsRepository.releaseStaleRead.complete(Unit)
            advanceUntilIdle()
            viewModel.beginAppearanceEdit(ReaderSettingsScope.GLOBAL)

            assertThat(viewModel.uiState.value.appearanceEdit?.original?.fontSizeSp).isEqualTo(30f)
        }

    @Test
    fun `rapid progress previews cancel stale reads and commit one temporary jump without saving`() =
        runTest(mainDispatcherRule.dispatcher) {
            val content = "甲".repeat(1_000)
            val source = CancellablePreviewTextSource(content)
            val repository = FakeRepository(savedOffset = 0, contentLength = content.length.toLong())
            val viewModel = ReaderViewModel(
                repository,
                source,
                EpochClock { 99L },
                FakeReaderSettingsRepository(),
                FakeBookPaginator(pageSize = 50),
                FakeBookmarkRepository(),
                Dispatchers.Unconfined,
                positionPreviewService = ReaderPositionPreviewService(source),
            )
            viewModel.open("book-1", pageUtf16Units = 50)
            advanceUntilIdle()

            viewModel.previewProgress(0.1f)
            runCurrent()
            assertThat(source.previewAnchors).containsExactly(100L)
            viewModel.previewProgress(0.8f)
            runCurrent()
            assertThat(source.previewAnchors.map(Long::toString)).containsExactly("100", "800").inOrder()
            assertThat(viewModel.uiState.value.anchorOffset).isEqualTo(0)
            viewModel.commitProgressPreview()
            advanceUntilIdle()
            repeat(10) {
                kotlinx.coroutines.yield()
                mainDispatcherRule.dispatcher.scheduler.runCurrent()
            }

            assertThat(source.completedPreviewAnchors).isEmpty()
            assertThat(viewModel.uiState.value.anchorOffset).isEqualTo(800)
            assertThat(viewModel.uiState.value.stableAnchorOffset).isEqualTo(0)
            assertThat(viewModel.uiState.value.isBrowsingTemporarily).isTrue()
            assertThat(viewModel.uiState.value.canNavigateBack).isTrue()
            assertThat(viewModel.uiState.value.progressPreview).isNull()
            assertThat(repository.savedProgress).isNull()

            viewModel.commitProgressPreview()
            viewModel.returnToOrigin()
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.anchorOffset).isEqualTo(0)
            viewModel.leaveReader {}
            runCurrent()
        }

    @Test
    fun `cancelling progress preview clears state without changing navigation`() =
        runTest(mainDispatcherRule.dispatcher) {
            val content = "乙".repeat(1_000)
            val source = FakeBookTextSource(content)
            val viewModel = ReaderViewModel(
                FakeRepository(savedOffset = 0, contentLength = content.length.toLong()),
                source,
                EpochClock { 99L },
                FakeReaderSettingsRepository(),
                FakeBookPaginator(pageSize = 50),
                FakeBookmarkRepository(),
                Dispatchers.Unconfined,
                positionPreviewService = ReaderPositionPreviewService(source),
            )
            viewModel.open("book-1", pageUtf16Units = 50)
            advanceUntilIdle()

            viewModel.previewProgress(0.6f)
            advanceUntilIdle()
            viewModel.cancelProgressPreview()

            assertThat(viewModel.uiState.value.progressPreview).isNull()
            assertThat(viewModel.uiState.value.isProgressPreviewLoading).isFalse()
            assertThat(viewModel.uiState.value.anchorOffset).isEqualTo(0)
            assertThat(viewModel.uiState.value.stableAnchorOffset).isEqualTo(0)
            assertThat(viewModel.uiState.value.canNavigateBack).isFalse()
            viewModel.leaveReader {}
            runCurrent()
        }

    @Test
    fun `theme automation starts with reader records manual choice and stops on leave`() =
        runTest(mainDispatcherRule.dispatcher) {
            suspend fun drainViewModel() {
                repeat(100) {
                    kotlinx.coroutines.yield()
                    mainDispatcherRule.dispatcher.scheduler.runCurrent()
                }
            }
            val settingsRepository = FakeReaderSettingsRepository()
            val scheduleRepository = ViewModelScheduleRepository(
                ReaderThemeSchedule(
                    mode = ThemeScheduleMode.FOLLOW_SYSTEM,
                    manualOverrideUntilNextSwitch = true,
                ),
            )
            val systemDark = MutableStateFlow(false)
            val themeRepository = ViewModelThemeRepository()
            val clock = EpochClock { 1_000L + testScheduler.currentTime }
            val themeManager = ReaderThemeManager(themeRepository, clock)
            val controller = ReaderThemeScheduleController(
                repository = scheduleRepository,
                themes = themeManager.observeThemes(),
                systemDark = systemDark,
                timeChanges = flowOf(Unit),
                clock = clock,
                zoneIdProvider = { java.time.ZoneId.of("UTC") },
                delayUntil = { kotlinx.coroutines.delay(it) },
            )
            val viewModel = ReaderViewModel(
                FakeRepository(savedOffset = 0),
                FakeBookTextSource("正文内容"),
                clock,
                settingsRepository,
                FakeBookPaginator(pageSize = 2),
                FakeBookmarkRepository(),
                Dispatchers.Unconfined,
                themeManager = themeManager,
                themeScheduleRepository = scheduleRepository,
                themeScheduleController = controller,
            )

            viewModel.open("book-1", pageUtf16Units = 2)
            drainViewModel()
            assertThat(controller.isRunning).isTrue()
            assertThat(systemDark.subscriptionCount.value).isAtLeast(1)
            assertThat(controller.currentResolvedThemeId)
                .isEqualTo(com.xinyue.reader.core.domain.model.BUILT_IN_PAPER_ID)
            assertThat(viewModel.uiState.value.settings.backgroundArgb).isEqualTo(0xFFF6F1E7)
            assertThat(settingsRepository.globalWriteCount).isEqualTo(0)

            viewModel.beginAppearanceEdit(ReaderSettingsScope.GLOBAL)
            viewModel.applyTheme(com.xinyue.reader.core.domain.model.BUILT_IN_SEPIA_ID)
            viewModel.commitAppearanceEdit()
            drainViewModel()
            assertThat(scheduleRepository.manual.value?.themeId)
                .isEqualTo(com.xinyue.reader.core.domain.model.BUILT_IN_SEPIA_ID)
            assertThat(viewModel.uiState.value.settings.backgroundArgb).isEqualTo(0xFFF4E8CE)

            viewModel.beginAppearanceEdit(ReaderSettingsScope.CURRENT_BOOK)
            viewModel.applyTheme(com.xinyue.reader.core.domain.model.BUILT_IN_DARK_ID)
            viewModel.commitAppearanceEdit()
            drainViewModel()
            assertThat(scheduleRepository.manual.value?.themeId)
                .isEqualTo(com.xinyue.reader.core.domain.model.BUILT_IN_SEPIA_ID)

            viewModel.beginAppearanceEdit(ReaderSettingsScope.GLOBAL)
            viewModel.applyTheme(com.xinyue.reader.core.domain.model.BUILT_IN_PAPER_ID)
            viewModel.changeAppearanceEditScope(ReaderSettingsScope.CURRENT_BOOK)
            viewModel.commitAppearanceEdit()
            drainViewModel()
            assertThat(scheduleRepository.manual.value?.themeId)
                .isEqualTo(com.xinyue.reader.core.domain.model.BUILT_IN_SEPIA_ID)

            scheduleRepository.failManualUpdates = true
            viewModel.beginAppearanceEdit(ReaderSettingsScope.GLOBAL)
            viewModel.applyTheme(com.xinyue.reader.core.domain.model.BUILT_IN_DARK_ID)
            viewModel.commitAppearanceEdit()
            drainViewModel()
            assertThat(viewModel.uiState.value.appearanceEdit).isNotNull()
            assertThat(viewModel.uiState.value.appearanceErrorMessage).isNotNull()
            assertThat(scheduleRepository.manual.value?.themeId)
                .isEqualTo(com.xinyue.reader.core.domain.model.BUILT_IN_SEPIA_ID)

            scheduleRepository.failManualUpdates = false
            viewModel.commitAppearanceEdit()
            drainViewModel()
            assertThat(viewModel.uiState.value.appearanceEdit).isNull()
            assertThat(scheduleRepository.manual.value?.themeId)
                .isEqualTo(com.xinyue.reader.core.domain.model.BUILT_IN_DARK_ID)

            viewModel.leaveReader {}
            runCurrent()
            assertThat(controller.isRunning).isFalse()
        }

    @Test
    fun `theme application respects scope materializes appearance and reset restores inheritance`() =
        runTest(mainDispatcherRule.dispatcher) {
            val settingsRepository = FakeReaderSettingsRepository(
                initial = ReaderSettings(
                    fontSizeSp = 20f,
                    pageAnimation = com.xinyue.reader.core.domain.model.ReaderPageAnimation.NONE,
                ),
            ).apply {
                bookOverrides["other-book"] = ReaderSettingsOverrides(fontSizeSp = 24f)
            }
            val themeRepository = ViewModelThemeRepository()
            val themeManager = ReaderThemeManager(
                repository = themeRepository,
                clock = EpochClock { 10L },
                idFactory = { "custom-theme" },
            )
            val theme = themeManager.create(
                "夜读",
                ReaderSettings(fontSizeSp = 30f, backgroundArgb = 0xFF010203),
            )
            val viewModel = ReaderViewModel(
                FakeRepository(savedOffset = 0),
                FakeBookTextSource("正文内容"),
                EpochClock { 99L },
                settingsRepository,
                FakeBookPaginator(pageSize = 2),
                FakeBookmarkRepository(),
                Dispatchers.Unconfined,
                themeManager = themeManager,
            )
            viewModel.open("book-1", pageUtf16Units = 2)
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.themes.map(ReaderThemePreset::id)).contains(theme.id)
            viewModel.beginAppearanceEdit(ReaderSettingsScope.CURRENT_BOOK)
            viewModel.applyTheme(theme.id)
            viewModel.commitAppearanceEdit()
            advanceUntilIdle()

            assertThat(settingsRepository.bookOverrides["book-1"]?.fontSizeSp).isEqualTo(30f)
            assertThat(settingsRepository.bookOverrides["book-1"]?.backgroundArgb).isEqualTo(0xFF010203)
            assertThat(viewModel.uiState.value.settings.pageAnimation)
                .isEqualTo(com.xinyue.reader.core.domain.model.ReaderPageAnimation.NONE)

            viewModel.beginAppearanceEdit(ReaderSettingsScope.GLOBAL)
            viewModel.applyTheme(com.xinyue.reader.core.domain.model.BUILT_IN_DARK_ID)
            viewModel.commitAppearanceEdit()
            advanceUntilIdle()

            assertThat(settingsRepository.current.backgroundArgb).isEqualTo(0xFF252423)
            assertThat(settingsRepository.bookOverrides["other-book"]?.fontSizeSp).isEqualTo(24f)

            viewModel.clearCurrentBookOverrides()
            advanceUntilIdle()
            assertThat(settingsRepository.bookOverrides).doesNotContainKey("book-1")
        }

    @Test
    fun `imports fonts through a source factory and reports referenced removal`() =
        runTest(mainDispatcherRule.dispatcher) {
            val fonts = FakeImportedFontRepository()
            val viewModel = ReaderViewModel(
                FakeRepository(),
                FakeBookTextSource("正文"),
                EpochClock { 1 },
                FakeReaderSettingsRepository(),
                FakeBookPaginator(2),
                FakeBookmarkRepository(),
                Dispatchers.Unconfined,
                importedFontRepository = fonts,
                importSourceFactory = object : ImportSourceFactory {
                    override suspend fun create(uriString: String): ImportSource =
                        ImportSource("font.ttf", 3) { "abc".byteInputStream() }
                },
            )

            viewModel.importFont("content://font/1")
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.appearanceErrorMessage).isNull()
            assertThat(viewModel.uiState.value.importedFonts.map(ImportedFont::id)).containsExactly("font-1")

            fonts.removalResult = FontRemovalResult.InUse(2)
            viewModel.removeImportedFont("font-1")
            advanceUntilIdle()
            assertThat(fonts.removeCallCount).isEqualTo(1)
            assertThat(viewModel.uiState.value.appearanceErrorMessage).contains("2")
        }

    @Test
    fun `opens normalized text at saved anchor and saves the next page anchor`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeRepository()
            val textSource = FakeBookTextSource("甲乙丙丁戊己庚辛壬癸")
            val viewModel = ReaderViewModel(
                repository,
                textSource,
                EpochClock { 99L },
                FakeReaderSettingsRepository(),
                FakeBookPaginator(pageSize = 3),
                FakeBookmarkRepository(),
                Dispatchers.Unconfined,
            )
            val session: ReaderSession = viewModel

            session.open(bookId = "book-1", pageUtf16Units = 4)
            advanceUntilIdle()

            assertThat(session.uiState.value.errorMessage).isNull()
            assertThat(session.uiState.value.book?.title).isEqualTo("测试小说")
            assertThat(pageTexts(session.uiState.value))
                .containsExactly("甲乙丙丁", "戊己庚辛", "壬癸")
                .inOrder()
            assertThat(viewModel.uiState.value.currentPageIndex).isEqualTo(1)
            assertThat(repository.openedAtEpochMillis).isEqualTo(99L)

            viewModel.goToPage(2)
            advanceUntilIdle()
            viewModel.flushProgress()

            assertThat(repository.savedProgress?.anchor?.offset).isEqualTo(8L)
            assertThat(repository.savedProgress?.contentLength).isEqualTo(10L)
            assertThat(repository.savedProgress?.updatedAtEpochMillis).isEqualTo(99L)
            val savedAnchor = requireNotNull(repository.savedProgress?.anchor)
            assertThat(savedAnchor.prefix).isEqualTo("甲乙丙丁戊己庚辛")
            assertThat(savedAnchor.suffix).isEqualTo("壬癸")
            assertThat(savedAnchor.contextHash).isEqualTo(
                TextFingerprint.contextSha256(
                    TextFingerprint(savedAnchor.prefix, savedAnchor.suffix, selectedSha256 = null),
                ),
            )
        }

    @Test
    fun `repairs a shifted persisted anchor before restoring the reader`() =
        runTest(mainDispatcherRule.dispatcher) {
            val original = "甲乙目标后文"
            val current = "新增$original"
            val fingerprint = TextFingerprint.capture(original, 2, 2)
            val repository = FakeRepository(
                savedOffset = 2,
                contentLength = current.length.toLong(),
                savedAnchor = TextAnchor(
                    offset = 2,
                    contextHash = TextFingerprint.contextSha256(fingerprint),
                    prefix = fingerprint.prefix,
                    suffix = fingerprint.suffix,
                ),
            )
            val viewModel = ReaderViewModel(
                repository,
                FakeBookTextSource(current),
                EpochClock { 99L },
                FakeReaderSettingsRepository(),
                FakeBookPaginator(pageSize = 3),
                FakeBookmarkRepository(),
                Dispatchers.Unconfined,
            )

            viewModel.open("book-1", pageUtf16Units = 3)
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.errorMessage).isNull()
            assertThat(viewModel.uiState.value.anchorOffset).isEqualTo(4)
            assertThat(repository.savedProgress?.anchor?.offset).isEqualTo(4)
        }

    @Test
    fun `exposes chapters search results and persistent reading settings`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeRepository(savedOffset = 0)
            val settingsRepository = FakeReaderSettingsRepository()
            val chapterIndexStore = FakeChapterIndexStore()
            val content = "第一章 初见\n星河璀璨。\n第二章 重逢\n她又看见星河。"
            val searchRepository = FakeBookSearchRepository(content)
            val viewModel = ReaderViewModel(
                repository,
                FakeBookTextSource(content),
                EpochClock { 99L },
                settingsRepository,
                FakeBookPaginator(pageSize = 7),
                FakeBookmarkRepository(),
                Dispatchers.Unconfined,
                chapterIndexStore,
                searchRepository,
            )

            viewModel.open(bookId = "book-1", pageUtf16Units = 10)
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.errorMessage).isNull()
            assertThat(viewModel.uiState.value.isLoading).isFalse()
            assertThat(viewModel.uiState.value.chapters.map { it.title })
                .containsExactly("第一章 初见", "第二章 重逢")
                .inOrder()
            assertThat(chapterIndexStore.saved.map { it.title })
                .containsExactly("第一章 初见", "第二章 重逢")
                .inOrder()

            viewModel.search("星河")
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.searchResults.map { it.offset })
                .containsExactly(content.indexOf("星河"), content.lastIndexOf("星河"))
                .inOrder()
            assertThat(searchRepository.ensureRequests).contains("book-1")

            viewModel.beginAppearanceEdit(ReaderSettingsScope.GLOBAL)
            viewModel.previewAppearance(ReaderSettings(fontSizeSp = 26f, lineHeightMultiplier = 1.8f))
            viewModel.commitAppearanceEdit()
            assertThat(viewModel.uiState.value.settings.lineHeightMultiplier).isEqualTo(1.8f)
            advanceUntilIdle()

            assertThat(settingsRepository.current.fontSizeSp).isEqualTo(26f)
            assertThat(viewModel.uiState.value.settings.lineHeightMultiplier).isEqualTo(1.8f)
        }

    @Test
    fun `appearance preview is reversible and only commit writes the selected scope once`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeRepository(savedOffset = 4)
            val settingsRepository = FakeReaderSettingsRepository(
                initial = ReaderSettings(fontSizeSp = 20f),
            )
            val viewModel = ReaderViewModel(
                repository,
                FakeBookTextSource("甲乙丙丁戊己庚辛壬癸"),
                EpochClock { 99L },
                settingsRepository,
                FakeBookPaginator(pageSize = 2),
                FakeBookmarkRepository(),
                Dispatchers.Unconfined,
            )
            viewModel.open("book-1", pageUtf16Units = 2)
            advanceUntilIdle()

            viewModel.beginAppearanceEdit(ReaderSettingsScope.CURRENT_BOOK)
            viewModel.previewAppearance(viewModel.uiState.value.settings.copy(fontSizeSp = 28f))

            assertThat(viewModel.uiState.value.appearanceEdit?.originalStableAnchorOffset).isEqualTo(4)
            assertThat(viewModel.uiState.value.settings.fontSizeSp).isEqualTo(28f)
            assertThat(settingsRepository.globalWriteCount).isEqualTo(0)
            assertThat(settingsRepository.bookWriteCount).isEqualTo(0)

            viewModel.goToPage(4)
            viewModel.cancelAppearanceEdit()
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.settings.fontSizeSp).isEqualTo(20f)
            assertThat(viewModel.uiState.value.anchorOffset).isEqualTo(4)
            assertThat(viewModel.uiState.value.currentPageIndex).isEqualTo(2)
            assertThat(viewModel.uiState.value.stableAnchorOffset).isEqualTo(4)
            assertThat(settingsRepository.bookWriteCount).isEqualTo(0)

            viewModel.beginAppearanceEdit(ReaderSettingsScope.CURRENT_BOOK)
            viewModel.previewAppearance(viewModel.uiState.value.settings.copy(fontSizeSp = 26f))
            viewModel.goToPage(3)
            viewModel.commitAppearanceEdit()
            advanceUntilIdle()

            assertThat(settingsRepository.globalWriteCount).isEqualTo(0)
            assertThat(settingsRepository.bookWriteCount).isEqualTo(1)
            assertThat(settingsRepository.bookOverrides["book-1"]?.fontSizeSp).isEqualTo(26f)
            assertThat(viewModel.uiState.value.appearanceEdit).isNull()
            assertThat(viewModel.uiState.value.settings.fontSizeSp).isEqualTo(26f)
            assertThat(viewModel.uiState.value.anchorOffset).isEqualTo(4)
            assertThat(viewModel.uiState.value.currentPageIndex).isEqualTo(2)
        }

    @Test
    fun `appearance scope change preserves migrated draft and saves only selected scope`() =
        runTest(mainDispatcherRule.dispatcher) {
            val settingsRepository = FakeReaderSettingsRepository(
                initial = ReaderSettings(
                    fontSizeSp = 20f,
                    brightness = 0.35f,
                    pageAnimation = ReaderPageAnimation.SLIDE,
                    showBookTitle = false,
                ),
            )
            settingsRepository.bookOverrides["book-1"] = ReaderSettingsOverrides(fontSizeSp = 24f)
            val viewModel = ReaderViewModel(
                FakeRepository(savedOffset = 4),
                FakeBookTextSource("甲乙丙丁戊己庚辛壬癸"),
                EpochClock { 99L },
                settingsRepository,
                FakeBookPaginator(pageSize = 2),
                FakeBookmarkRepository(),
                Dispatchers.Unconfined,
            )
            viewModel.open("book-1", pageUtf16Units = 2)
            advanceUntilIdle()

            viewModel.beginAppearanceEdit(ReaderSettingsScope.GLOBAL)
            val draft = viewModel.uiState.value.settings.copy(
                fontSizeSp = 30f,
                firstLineIndentEm = 2f,
                pageAnimation = ReaderPageAnimation.COVER,
                brightness = 0.9f,
                showBookTitle = true,
            )
            viewModel.previewAppearance(draft)
            val anchor = viewModel.uiState.value.appearanceEdit?.originalStableAnchorOffset

            viewModel.changeAppearanceEditScope(ReaderSettingsScope.CURRENT_BOOK)
            val edit = viewModel.uiState.value.appearanceEdit
            assertThat(edit?.scope).isEqualTo(ReaderSettingsScope.CURRENT_BOOK)
            assertThat(edit?.original?.fontSizeSp).isEqualTo(24f)
            assertThat(edit?.preview?.fontSizeSp).isEqualTo(30f)
            assertThat(edit?.preview?.firstLineIndentEm).isEqualTo(2f)
            assertThat(edit?.preview?.pageAnimation).isEqualTo(ReaderPageAnimation.SLIDE)
            assertThat(edit?.preview?.brightness).isEqualTo(0.35f)
            assertThat(edit?.preview?.showBookTitle).isFalse()
            assertThat(edit?.originalStableAnchorOffset).isEqualTo(anchor)

            viewModel.commitAppearanceEdit()
            advanceUntilIdle()

            assertThat(settingsRepository.globalWriteCount).isEqualTo(0)
            assertThat(settingsRepository.bookWriteCount).isEqualTo(1)
            assertThat(settingsRepository.bookOverrides["book-1"]?.fontSizeSp).isEqualTo(30f)
            assertThat(settingsRepository.bookOverrides["book-1"]?.firstLineIndentEm).isEqualTo(2f)
            assertThat(viewModel.uiState.value.appearanceEdit).isNull()
            assertThat(viewModel.uiState.value.settings.fontSizeSp).isEqualTo(30f)
            assertThat(viewModel.uiState.value.settings.pageAnimation).isEqualTo(ReaderPageAnimation.SLIDE)
            assertThat(viewModel.uiState.value.settings.brightness).isEqualTo(0.35f)
        }

    @Test
    fun `appearance scope change cancel restores target scope without writing`() =
        runTest(mainDispatcherRule.dispatcher) {
            val settingsRepository = FakeReaderSettingsRepository(
                initial = ReaderSettings(
                    fontSizeSp = 20f,
                    brightness = 0.35f,
                    pageAnimation = ReaderPageAnimation.SLIDE,
                ),
            )
            settingsRepository.bookOverrides["book-1"] = ReaderSettingsOverrides(fontSizeSp = 24f)
            val viewModel = ReaderViewModel(
                FakeRepository(savedOffset = 4),
                FakeBookTextSource("甲乙丙丁戊己庚辛壬癸"),
                EpochClock { 99L },
                settingsRepository,
                FakeBookPaginator(pageSize = 2),
                FakeBookmarkRepository(),
                Dispatchers.Unconfined,
            )
            viewModel.open("book-1", pageUtf16Units = 2)
            advanceUntilIdle()

            viewModel.beginAppearanceEdit(ReaderSettingsScope.CURRENT_BOOK)
            viewModel.previewAppearance(
                viewModel.uiState.value.settings.copy(
                    fontSizeSp = 28f,
                    pageAnimation = ReaderPageAnimation.COVER,
                    brightness = 0.9f,
                ),
            )
            val anchor = viewModel.uiState.value.appearanceEdit?.originalStableAnchorOffset

            viewModel.changeAppearanceEditScope(ReaderSettingsScope.GLOBAL)
            val edit = viewModel.uiState.value.appearanceEdit
            assertThat(edit?.scope).isEqualTo(ReaderSettingsScope.GLOBAL)
            assertThat(edit?.original?.fontSizeSp).isEqualTo(20f)
            assertThat(edit?.preview?.fontSizeSp).isEqualTo(28f)
            assertThat(edit?.preview?.pageAnimation).isEqualTo(ReaderPageAnimation.SLIDE)
            assertThat(edit?.preview?.brightness).isEqualTo(0.35f)
            assertThat(edit?.originalStableAnchorOffset).isEqualTo(anchor)

            viewModel.cancelAppearanceEdit()
            advanceUntilIdle()

            assertThat(settingsRepository.globalWriteCount).isEqualTo(0)
            assertThat(settingsRepository.bookWriteCount).isEqualTo(0)
            assertThat(viewModel.uiState.value.appearanceEdit).isNull()
            // Closing the global edit restores the current book's effective settings;
            // the global target baseline (20sp) was asserted before cancellation.
            assertThat(viewModel.uiState.value.settings.fontSizeSp).isEqualTo(24f)
            assertThat(viewModel.uiState.value.settings.pageAnimation).isEqualTo(ReaderPageAnimation.SLIDE)
            assertThat(viewModel.uiState.value.settings.brightness).isEqualTo(0.35f)
        }

    @Test
    fun `an older successful appearance commit never closes a newer edit`() =
        runTest(mainDispatcherRule.dispatcher) {
            val settingsRepository = BarrierResolvedSettingsRepository()
            val viewModel = ReaderViewModel(
                FakeRepository(savedOffset = 0),
                FakeBookTextSource("0123456789"),
                EpochClock { 99L },
                settingsRepository,
                FakeBookPaginator(pageSize = 2),
                FakeBookmarkRepository(),
                Dispatchers.Unconfined,
            )
            viewModel.open("book-1", pageUtf16Units = 2)
            advanceUntilIdle()

            viewModel.beginAppearanceEdit(ReaderSettingsScope.GLOBAL)
            viewModel.previewAppearance(viewModel.uiState.value.settings.copy(fontSizeSp = 28f))
            viewModel.commitAppearanceEdit()
            runCurrent()
            assertThat(settingsRepository.resolvedReadStarted.isCompleted).isTrue()

            viewModel.beginAppearanceEdit(ReaderSettingsScope.CURRENT_BOOK)
            viewModel.previewAppearance(viewModel.uiState.value.settings.copy(fontSizeSp = 31f))
            settingsRepository.releaseResolvedRead.complete(Unit)
            advanceUntilIdle()

            assertThat(settingsRepository.current.fontSizeSp).isEqualTo(28f)
            assertThat(viewModel.uiState.value.appearanceEdit?.scope)
                .isEqualTo(ReaderSettingsScope.CURRENT_BOOK)
            assertThat(viewModel.uiState.value.appearanceEdit?.preview?.fontSizeSp).isEqualTo(31f)
            assertThat(viewModel.uiState.value.settings.fontSizeSp).isEqualTo(31f)
            assertThat(viewModel.uiState.value.appearanceErrorMessage).isNull()
        }

    @Test
    fun `an older failed appearance commit never reports into a newer edit`() =
        runTest(mainDispatcherRule.dispatcher) {
            val settingsRepository = BarrierCommitSettingsRepository(failUpdate = true)
            val viewModel = ReaderViewModel(
                FakeRepository(savedOffset = 0),
                FakeBookTextSource("0123456789"),
                EpochClock { 99L },
                settingsRepository,
                FakeBookPaginator(pageSize = 2),
                FakeBookmarkRepository(),
                Dispatchers.Unconfined,
            )
            viewModel.open("book-1", pageUtf16Units = 2)
            advanceUntilIdle()

            viewModel.beginAppearanceEdit(ReaderSettingsScope.GLOBAL)
            viewModel.previewAppearance(viewModel.uiState.value.settings.copy(fontSizeSp = 28f))
            viewModel.commitAppearanceEdit()
            runCurrent()
            assertThat(settingsRepository.updateStarted.isCompleted).isTrue()

            viewModel.beginAppearanceEdit(ReaderSettingsScope.CURRENT_BOOK)
            viewModel.previewAppearance(viewModel.uiState.value.settings.copy(fontSizeSp = 31f))
            settingsRepository.releaseUpdate.complete(Unit)
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.appearanceEdit?.scope)
                .isEqualTo(ReaderSettingsScope.CURRENT_BOOK)
            assertThat(viewModel.uiState.value.appearanceEdit?.preview?.fontSizeSp).isEqualTo(31f)
            assertThat(viewModel.uiState.value.settings.fontSizeSp).isEqualTo(31f)
            assertThat(viewModel.uiState.value.appearanceErrorMessage).isNull()
        }

    @Test
    fun `page movement during appearance preview never changes stable progress`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeRepository(savedOffset = 2)
            val viewModel = ReaderViewModel(
                repository,
                FakeBookTextSource("甲乙丙丁戊己庚辛壬癸"),
                EpochClock { 99L },
                FakeReaderSettingsRepository(),
                FakeBookPaginator(pageSize = 2),
                FakeBookmarkRepository(),
                Dispatchers.Unconfined,
            )
            viewModel.open("book-1", pageUtf16Units = 2)
            advanceUntilIdle()
            val savedBeforePreview = repository.savedProgress

            viewModel.beginAppearanceEdit(ReaderSettingsScope.GLOBAL)
            viewModel.goToPage(4)
            advanceUntilIdle()
            viewModel.flushProgress()

            assertThat(viewModel.uiState.value.anchorOffset).isEqualTo(8)
            assertThat(viewModel.uiState.value.stableAnchorOffset).isEqualTo(2)
            assertThat(repository.savedProgress).isEqualTo(savedBeforePreview)
        }

    @Test
    fun `repository emissions are ignored during preview and reconciled after cancel`() =
        runTest(mainDispatcherRule.dispatcher) {
            val settingsRepository = FakeReaderSettingsRepository(
                initial = ReaderSettings(fontSizeSp = 20f),
            )
            val viewModel = ReaderViewModel(
                FakeRepository(savedOffset = 0),
                FakeBookTextSource("正文内容"),
                EpochClock { 99L },
                settingsRepository,
                FakeBookPaginator(pageSize = 2),
                FakeBookmarkRepository(),
                Dispatchers.Unconfined,
            )
            viewModel.open("book-1", pageUtf16Units = 2)
            advanceUntilIdle()
            viewModel.beginAppearanceEdit(ReaderSettingsScope.GLOBAL)
            viewModel.previewAppearance(ReaderSettings(fontSizeSp = 30f))

            settingsRepository.emitGlobal(ReaderSettings(fontSizeSp = 22f))
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.settings.fontSizeSp).isEqualTo(30f)

            viewModel.cancelAppearanceEdit()
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.settings.fontSizeSp).isEqualTo(22f)
        }

    @Test
    fun `a new appearance preview cancels older pagination and keeps the original anchor`() =
        runTest(mainDispatcherRule.dispatcher) {
            val paginator = ControllablePaginator()
            val viewModel = ReaderViewModel(
                FakeRepository(savedOffset = 4),
                FakeBookTextSource("甲乙丙丁戊己庚辛壬癸"),
                EpochClock { 99L },
                FakeReaderSettingsRepository(),
                paginator,
                FakeBookmarkRepository(),
                Dispatchers.Unconfined,
            )
            viewModel.open("book-1", pageUtf16Units = 2)
            advanceUntilIdle()
            viewModel.updateLayout(
                ReaderLayoutSpec(300, 600, 20f, 32f),
            )
            advanceUntilIdle()
            viewModel.beginAppearanceEdit(ReaderSettingsScope.GLOBAL)
            advanceUntilIdle()

            paginator.suspendNextCall = true
            viewModel.previewAppearance(viewModel.uiState.value.settings.copy(fontSizeSp = 26f))
            runCurrent()
            assertThat(paginator.suspendedCallCount).isEqualTo(1)
            viewModel.previewAppearance(viewModel.uiState.value.settings.copy(fontSizeSp = 28f))
            advanceUntilIdle()

            assertThat(paginator.cancelledCallCount).isAtLeast(1)
            assertThat(paginator.lastCompletedSpec?.fontSizePx).isEqualTo(28f)
            assertThat(viewModel.uiState.value.stableAnchorOffset).isEqualTo(4)
            assertThat(viewModel.uiState.value.isPaginating).isFalse()
        }

    @Test
    fun `a noncancellable older precise pagination cannot publish over the newer snapshot`() =
        runTest(mainDispatcherRule.dispatcher) {
            val paginator = NonCancellablePaginator()
            val content = "abcdef"
            val viewModel = ReaderViewModel(
                FakeRepository(savedOffset = 0, contentLength = content.length.toLong()),
                FakeBookTextSource(content),
                EpochClock { 99L },
                FakeReaderSettingsRepository(),
                paginator,
                FakeBookmarkRepository(),
                Dispatchers.Unconfined,
            )

            viewModel.open("book-1", pageUtf16Units = content.length)
            advanceUntilIdle()
            viewModel.updateLayout(ReaderLayoutSpec(300, 600, 20f, 32f))
            runCurrent()
            assertThat(paginator.firstCallStarted.isCompleted).isTrue()

            viewModel.updateLayout(ReaderLayoutSpec(300, 600, 28f, 44f))
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.pages).hasSize(2)

            paginator.releaseFirstCall.complete(Unit)
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.pages).hasSize(2)
            assertThat(viewModel.uiState.value.layoutContent).isEqualTo(content)
        }

    @Test
    fun `uses the persisted chapter index when background reanalysis cannot finish`() =
        runTest(mainDispatcherRule.dispatcher) {
            val cached = listOf(
                DetectedChapter("缓存第一章", 0),
                DetectedChapter("缓存第二章", 5),
            )
            val viewModel = ReaderViewModel(
                FakeRepository(savedOffset = 0, contentLength = 10),
                OneShotTextSource("普通正文内容"),
                EpochClock { 99L },
                FakeReaderSettingsRepository(),
                FakeBookPaginator(pageSize = 5),
                FakeBookmarkRepository(),
                Dispatchers.Unconfined,
                FakeChapterIndexStore(cached),
            )

            viewModel.open(bookId = "book-1", pageUtf16Units = 5)
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.chapters).containsExactlyElementsIn(cached).inOrder()
        }

    @Test
    fun `manual chapter index is not overwritten by background analysis`() =
        runTest(mainDispatcherRule.dispatcher) {
            val manual = listOf(DetectedChapter("我的目录", 3))
            val store = FakeChapterIndexStore().apply {
                snapshot = ChapterIndexSnapshot(
                    chapters = manual,
                    ruleSet = ChapterRuleSet.BROAD,
                    manuallyEdited = true,
                )
            }
            val viewModel = ReaderViewModel(
                FakeRepository(savedOffset = 0, contentLength = 20),
                FakeBookTextSource("第一章 自动结果\n正文内容"),
                EpochClock { 99L },
                FakeReaderSettingsRepository(),
                FakeBookPaginator(pageSize = 5),
                FakeBookmarkRepository(),
                Dispatchers.Unconfined,
                store,
            )

            viewModel.open("book-1", pageUtf16Units = 5)
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.chapters).containsExactlyElementsIn(manual)
            assertThat(viewModel.uiState.value.chaptersManuallyEdited).isTrue()
            assertThat(store.saved).isEmpty()
        }

    @Test
    fun `chapter edits persist and explicit reanalysis clears manual mode`() =
        runTest(mainDispatcherRule.dispatcher) {
            val content = "序章\n开场正文内容很长\n第一章 开始\n后续正文"
            val store = FakeChapterIndexStore()
            val viewModel = ReaderViewModel(
                FakeRepository(savedOffset = 0, contentLength = content.length.toLong()),
                FakeBookTextSource(content),
                EpochClock { 99L },
                FakeReaderSettingsRepository(),
                FakeBookPaginator(pageSize = 5),
                FakeBookmarkRepository(),
                Dispatchers.Unconfined,
                store,
            )
            viewModel.open("book-1", pageUtf16Units = 5)
            advanceUntilIdle()

            viewModel.goToPage(1)
            viewModel.addChapterAtCurrent("  人工开篇  ")
            advanceUntilIdle()
            assertThat(store.snapshot.chapters.first { it.startOffset == 5 }.title).isEqualTo("人工开篇")
            assertThat(store.snapshot.manuallyEdited).isTrue()

            viewModel.renameChapter(5, "重新命名")
            viewModel.goToPage(2)
            viewModel.moveChapterToCurrent(5)
            advanceUntilIdle()
            assertThat(store.snapshot.chapters.first { it.startOffset == 10 }.title).isEqualTo("重新命名")
            assertThat(store.snapshot.chapters.map(DetectedChapter::startOffset)).isInStrictOrder()

            viewModel.deleteChapter(10)
            advanceUntilIdle()
            assertThat(store.snapshot.chapters.none { it.startOffset == 10 }).isTrue()

            viewModel.reanalyzeChapters(ChapterRuleSet.BROAD)
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.chapters.map(DetectedChapter::title)).contains("序章")
            assertThat(store.snapshot.ruleSet).isEqualTo(ChapterRuleSet.BROAD)
            assertThat(store.snapshot.manuallyEdited).isFalse()
        }

    @Test
    fun `repaginates for the measured viewport while preserving the current anchor`() =
        runTest(mainDispatcherRule.dispatcher) {
            val viewModel = ReaderViewModel(
                FakeRepository(),
                FakeBookTextSource("甲乙丙丁戊己庚辛壬癸"),
                EpochClock { 99L },
                FakeReaderSettingsRepository(),
                FakeBookPaginator(pageSize = 3),
                FakeBookmarkRepository(),
                Dispatchers.Unconfined,
            )
            viewModel.open(bookId = "book-1", pageUtf16Units = 4)
            advanceUntilIdle()

            viewModel.updateLayout(
                ReaderLayoutSpec(widthPx = 300, heightPx = 500, fontSizePx = 40f, lineHeightPx = 64f),
            )
            advanceUntilIdle()
            val measured = viewModel.uiState.first { state ->
                !state.isPaginating && pageTexts(state) == listOf("甲乙丙", "丁戊己", "庚辛壬", "癸")
            }

            assertThat(pageTexts(measured))
                .containsExactly("甲乙丙", "丁戊己", "庚辛壬", "癸")
                .inOrder()
            assertThat(measured.currentPageIndex).isEqualTo(1)
            assertThat(measured.isPaginating).isFalse()
        }

    @Test
    fun `keeps the exact saved anchor until measured pagination is ready`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeRepository(savedOffset = 4)
            val viewModel = ReaderViewModel(
                repository,
                FakeBookTextSource("甲乙丙丁戊己庚辛壬癸"),
                EpochClock { 99L },
                FakeReaderSettingsRepository(),
                FakeBookPaginator(pageSize = 3),
                FakeBookmarkRepository(),
                Dispatchers.Unconfined,
            )

            // The coarse placeholder has a single page and must not erase offset 4.
            viewModel.open(bookId = "book-1", pageUtf16Units = 850)
            advanceUntilIdle()
            viewModel.goToPage(0)
            advanceUntilIdle()
            assertThat(repository.savedProgress).isNull()

            viewModel.updateLayout(
                ReaderLayoutSpec(widthPx = 300, heightPx = 500, fontSizePx = 40f, lineHeightPx = 64f),
            )
            advanceUntilIdle()
            val measured = viewModel.uiState.first { state ->
                !state.isPaginating && state.pages.getOrNull(1)?.startOffset == 3
            }

            assertThat(measured.currentPageIndex).isEqualTo(1)
            assertThat(measured.pages[1].startOffset).isEqualTo(3)
            assertThat(measured.anchorOffset).isEqualTo(4)
        }

    @Test
    fun `repeated viewport changes do not drift from the logical reading anchor`() =
        runTest(mainDispatcherRule.dispatcher) {
            val viewModel = ReaderViewModel(
                FakeRepository(savedOffset = 10),
                FakeBookTextSource("甲".repeat(24)),
                EpochClock { 99L },
                FakeReaderSettingsRepository(),
                VaryingBookPaginator(),
                FakeBookmarkRepository(),
                Dispatchers.Unconfined,
            )
            viewModel.open(bookId = "book-1", pageUtf16Units = 850)
            advanceUntilIdle()

            viewModel.updateLayout(
                ReaderLayoutSpec(widthPx = 400, heightPx = 500, fontSizePx = 40f, lineHeightPx = 64f),
            )
            advanceUntilIdle()
            viewModel.updateLayout(
                ReaderLayoutSpec(widthPx = 600, heightPx = 500, fontSizePx = 40f, lineHeightPx = 64f),
            )
            advanceUntilIdle()
            viewModel.updateLayout(
                ReaderLayoutSpec(widthPx = 900, heightPx = 500, fontSizePx = 40f, lineHeightPx = 64f),
            )
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.currentPageIndex).isEqualTo(1)
            assertThat(viewModel.uiState.value.pages[1].startOffset).isEqualTo(9)
            assertThat(viewModel.uiState.value.anchorOffset).isEqualTo(10)
        }

    @Test
    fun `adds and removes a bookmark at the current page anchor`() =
        runTest(mainDispatcherRule.dispatcher) {
            val bookmarks = FakeBookmarkRepository()
            val viewModel = ReaderViewModel(
                FakeRepository(),
                FakeBookTextSource("甲乙丙丁戊己庚辛壬癸"),
                EpochClock { 99L },
                FakeReaderSettingsRepository(),
                FakeBookPaginator(pageSize = 3),
                bookmarks,
                Dispatchers.Unconfined,
            )
            viewModel.open("book-1", pageUtf16Units = 4)
            advanceUntilIdle()

            viewModel.toggleBookmark()
            advanceUntilIdle()
            assertThat(bookmarks.current.single().offset).isEqualTo(4L)

            viewModel.toggleBookmark()
            advanceUntilIdle()
            assertThat(bookmarks.current).isEmpty()
        }

    @Test
    fun `opening a book runs CPU heavy text analysis on the computation dispatcher`() =
        runTest(mainDispatcherRule.dispatcher) {
            val computationDispatcher = RecordingDispatcher()
            val viewModel = ReaderViewModel(
                FakeRepository(),
                FakeBookTextSource("第一章 开始\n" + "正文".repeat(50_000)),
                EpochClock { 99L },
                FakeReaderSettingsRepository(),
                FakeBookPaginator(pageSize = 850),
                FakeBookmarkRepository(),
                computationDispatcher,
            )

            viewModel.open("book-1")

            assertThat(computationDispatcher.dispatchCount).isGreaterThan(0)
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.isLoading).isFalse()
            assertThat(viewModel.uiState.value.chapters).isNotEmpty()

        }

    @Test
    fun `opens a large book through bounded windows without retaining the whole text`() =
        runTest(mainDispatcherRule.dispatcher) {
            val content = "甲".repeat(600_000)
            val source = FakeBookTextSource(content)
            val viewModel = ReaderViewModel(
                FakeRepository(savedOffset = 300_000, contentLength = content.length.toLong()),
                source,
                EpochClock { 99L },
                FakeReaderSettingsRepository(),
                FakeBookPaginator(pageSize = 850),
                FakeBookmarkRepository(),
                Dispatchers.Unconfined,
            )

            viewModel.open("book-1")
            advanceUntilIdle()

            assertThat(source.requests.first().beforeUtf16Units).isEqualTo(50_000)
            assertThat(source.requests.first().afterUtf16Units).isEqualTo(100_000)
            assertThat(viewModel.uiState.value.content.length).isAtMost(150_001)
            assertThat(viewModel.uiState.value.contentLength).isEqualTo(600_000L)
            assertThat(viewModel.uiState.value.contentStartOffset).isGreaterThan(0)
            assertThat(viewModel.uiState.value.anchorOffset).isEqualTo(300_000)
        }

    @Test
    fun `coarse leading chapter boundary has no zero length page`() =
        runTest(mainDispatcherRule.dispatcher) {
            val content = "\n\n\u7b2c\u4e00\u7ae0\n\u6b63\u6587"
            val chapterStore = FakeChapterIndexStore().apply {
                snapshot = ChapterIndexSnapshot(
                    chapters = listOf(DetectedChapter("\u7b2c\u4e00\u7ae0", 2)),
                    manuallyEdited = true,
                )
            }
            val viewModel = ReaderViewModel(
                FakeRepository(savedOffset = 2, contentLength = content.length.toLong()),
                FakeBookTextSource(content),
                EpochClock { 99L },
                FakeReaderSettingsRepository(),
                FakeBookPaginator(pageSize = 850),
                FakeBookmarkRepository(),
                Dispatchers.Unconfined,
                chapterStore,
            )

            viewModel.open("book-1", pageUtf16Units = 850)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.pages).isNotEmpty()
            assertThat(state.pages.all { it.endOffset > it.startOffset }).isTrue()
            assertThat(state.pages.first().startOffset).isEqualTo(0)
            assertThat(state.pages.last().endOffset).isEqualTo(content.length)
            assertThat(state.pages.first().endOffset).isGreaterThan(2)
            assertThat(state.pages.joinToString(separator = "") { state.textFor(it) })
                .isEqualTo(content)
        }

    @Test
    fun `nonzero cached chapter boundaries rebuild layout whitespace and preserve page coverage`() =
        runTest(mainDispatcherRule.dispatcher) {
            val prefixLength = 60_000
            val chapterStart = prefixLength + 2
            val content = "x".repeat(prefixLength) +
                "\n\n\u7b2c\u4e00\u7ae0 \u6807\u9898\nbody"
            val chapterStore = FakeChapterIndexStore().apply {
                snapshot = ChapterIndexSnapshot(
                    chapters = listOf(DetectedChapter("\u7b2c\u4e00\u7ae0 \u6807\u9898", chapterStart)),
                    ruleSet = ChapterRuleSet.STANDARD,
                    manuallyEdited = true,
                )
            }
            val viewModel = ReaderViewModel(
                FakeRepository(savedOffset = chapterStart.toLong(), contentLength = content.length.toLong()),
                FakeBookTextSource(content),
                EpochClock { 99L },
                FakeReaderSettingsRepository(),
                FakeBookPaginator(pageSize = 850),
                FakeBookmarkRepository(),
                Dispatchers.Unconfined,
                chapterStore,
            )

            viewModel.open("book-1")
            advanceUntilIdle()

            val initial = viewModel.uiState.value
            assertThat(initial.contentStartOffset).isGreaterThan(0)
            val initialLocalChapter = chapterStart - initial.contentStartOffset
            assertThat(initial.layoutContent.substring(initialLocalChapter - 2, initialLocalChapter))
                .isEqualTo("\u2060\u2060")

            viewModel.reanalyzeChapters(ChapterRuleSet.STANDARD)
            advanceUntilIdle()

            val rebuilt = viewModel.uiState.value
            val rebuiltLocalChapter = chapterStart - rebuilt.contentStartOffset
            assertThat(rebuilt.chaptersManuallyEdited).isFalse()
            assertThat(rebuilt.layoutContent.substring(rebuiltLocalChapter - 2, rebuiltLocalChapter))
                .isEqualTo("\u2060\u2060")
            assertThat(rebuilt.pages.first().startOffset).isEqualTo(rebuilt.contentStartOffset)
            assertThat(rebuilt.pages.last().endOffset)
                .isEqualTo(rebuilt.contentStartOffset + rebuilt.content.length)
            assertThat(rebuilt.pages.zipWithNext().all { (first, second) ->
                first.endOffset == second.startOffset
            }).isTrue()
            assertThat(rebuilt.pages.joinToString(separator = "") { rebuilt.textFor(it) })
                .isEqualTo(rebuilt.content)
        }

    @Test
    fun `crlf and eof titles keep one nonzero snapshot through failed manual persistence`() =
        runTest(mainDispatcherRule.dispatcher) {
            val prefix = "p".repeat(60_000)
            val firstStart = prefix.length + 2
            val content = prefix + "\r\nfirst title\r\nbody\r\nsecond title"
            val secondStart = content.indexOf("second title")
            val chapters = listOf(
                DetectedChapter("first title", firstStart),
                DetectedChapter("second title", secondStart),
            )
            val store = FailingManualChapterIndexStore(
                ChapterIndexSnapshot(chapters = chapters, manuallyEdited = true),
            )
            val viewModel = ReaderViewModel(
                FakeRepository(
                    savedOffset = (firstStart + 3).toLong(),
                    contentLength = content.length.toLong(),
                ),
                FakeBookTextSource(content),
                EpochClock { 99L },
                FakeReaderSettingsRepository(),
                FakeBookPaginator(pageSize = 12),
                FakeBookmarkRepository(),
                Dispatchers.Unconfined,
                store,
            )

            viewModel.open("book-1", pageUtf16Units = 12)
            advanceUntilIdle()

            val initial = viewModel.uiState.value
            val expectedRanges = listOf(
                ReaderChapterTitleRange(firstStart, content.indexOf('\n', firstStart)),
                ReaderChapterTitleRange(secondStart, content.length),
            )
            assertThat(initial.contentStartOffset).isGreaterThan(0)
            assertThat(initial.chapterTitleRanges).containsExactlyElementsIn(expectedRanges).inOrder()
            assertCoherentLayoutSnapshot(initial)

            viewModel.renameChapter(firstStart, "renamed")
            advanceUntilIdle()

            val rolledBack = viewModel.uiState.value
            assertThat(rolledBack.chapters).containsExactlyElementsIn(chapters).inOrder()
            assertThat(rolledBack.chaptersManuallyEdited).isTrue()
            assertThat(rolledBack.isPaginating).isFalse()
            assertThat(rolledBack.chapterTitleRanges).containsExactlyElementsIn(expectedRanges).inOrder()
            assertCoherentLayoutSnapshot(rolledBack)
        }

    @Test
    fun `precise crlf eof rollback keeps one coherent snapshot after the cancelled mutation returns`() =
        runTest(mainDispatcherRule.dispatcher) {
            val prefix = "p".repeat(60_000)
            val firstStart = prefix.length + 2
            val content = prefix + "\r\nfirst title\r\nbody\r\nsecond title"
            val secondStart = content.indexOf("second title")
            val chapters = listOf(
                DetectedChapter("first title", firstStart),
                DetectedChapter("second title", secondStart),
            )
            val store = FailingManualChapterIndexStore(
                ChapterIndexSnapshot(chapters = chapters, manuallyEdited = true),
            )
            val paginator = RollbackInterleavingPaginator()
            val viewModel = ReaderViewModel(
                FakeRepository(
                    savedOffset = (firstStart + 3).toLong(),
                    contentLength = content.length.toLong(),
                ),
                FakeBookTextSource(content),
                EpochClock { 99L },
                FakeReaderSettingsRepository(),
                paginator,
                FakeBookmarkRepository(),
                Dispatchers.Unconfined,
                store,
            )

            viewModel.open("book-1", pageUtf16Units = 12)
            advanceUntilIdle()
            viewModel.updateLayout(ReaderLayoutSpec(300, 600, 20f, 32f))
            paginator.firstCallCompleted.await()
            val preciseBaseline = viewModel.uiState.first { state -> !state.isPaginating }
            val expectedRanges = listOf(
                ReaderChapterTitleRange(firstStart, content.indexOf('\n', firstStart)),
                ReaderChapterTitleRange(secondStart, content.length),
            )
            assertThat(preciseBaseline.contentStartOffset).isGreaterThan(0)
            assertThat(preciseBaseline.isPaginating).isFalse()
            assertThat(preciseBaseline.chapterTitleRanges)
                .containsExactlyElementsIn(expectedRanges).inOrder()
            assertCoherentLayoutSnapshot(preciseBaseline)
            assertThat(preciseBaseline.pages.joinToString(separator = "") { preciseBaseline.textFor(it) })
                .isEqualTo(preciseBaseline.content)

            paginator.blockNextCall()
            viewModel.deleteChapter(firstStart)
            runCurrent()
            assertThat(paginator.blockedCallStarted.isCompleted).isTrue()
            advanceUntilIdle()

            val rolledBack = viewModel.uiState.value
            assertThat(rolledBack.chapters).containsExactlyElementsIn(chapters).inOrder()
            assertThat(rolledBack.chaptersManuallyEdited).isTrue()
            assertThat(rolledBack.layoutContent).isEqualTo(preciseBaseline.layoutContent)
            assertThat(rolledBack.chapterTitleRanges)
                .containsExactlyElementsIn(expectedRanges).inOrder()
            assertThat(rolledBack.pages).containsExactlyElementsIn(preciseBaseline.pages).inOrder()
            assertCoherentLayoutSnapshot(rolledBack)
            assertThat(rolledBack.pages.joinToString(separator = "") { rolledBack.textFor(it) })
                .isEqualTo(rolledBack.content)

            paginator.releaseBlockedCall.complete(Unit)
            advanceUntilIdle()

            val final = viewModel.uiState.value
            assertThat(final.chapters).containsExactlyElementsIn(chapters).inOrder()
            assertThat(final.chaptersManuallyEdited).isTrue()
            assertThat(final.isPaginating).isFalse()
            assertThat(final.layoutContent).isEqualTo(preciseBaseline.layoutContent)
            assertThat(final.chapterTitleRanges)
                .containsExactlyElementsIn(expectedRanges).inOrder()
            assertThat(final.pages).containsExactlyElementsIn(preciseBaseline.pages).inOrder()
            assertCoherentLayoutSnapshot(final)
            assertThat(final.pages.joinToString(separator = "") { final.textFor(it) })
                .isEqualTo(final.content)
        }

    @Test
    fun `failed chapter persistence restarts an in flight navigation outside the old window`() =
        runTest(mainDispatcherRule.dispatcher) {
            val content = "chapter\n" + "x".repeat(400_000)
            val targetOffset = 300_000
            val source = RestartableWindowTextSource(content, targetOffset.toLong())
            val chapters = listOf(DetectedChapter("chapter", 0))
            val store = BarrierFailingManualChapterIndexStore(
                ChapterIndexSnapshot(chapters = chapters, manuallyEdited = true),
            )
            val viewModel = ReaderViewModel(
                FakeRepository(savedOffset = 0, contentLength = content.length.toLong()),
                source,
                EpochClock { 99L },
                FakeReaderSettingsRepository(),
                FakeBookPaginator(pageSize = 1_000),
                FakeBookmarkRepository(),
                Dispatchers.Unconfined,
                store,
            )

            viewModel.open("book-1", pageUtf16Units = 1_000)
            advanceUntilIdle()
            viewModel.updateLayout(ReaderLayoutSpec(300, 600, 20f, 32f))
            advanceUntilIdle()

            viewModel.renameChapter(0, "renamed")
            runCurrent()
            assertThat(store.replaceStarted.isCompleted).isTrue()
            assertThat(viewModel.uiState.value.chapters.single().title).isEqualTo("renamed")

            viewModel.jumpTemporarily(targetOffset, ReaderJumpReason.CHAPTER)
            runCurrent()
            assertThat(source.targetReadAttempts).isEqualTo(1)
            assertThat(viewModel.uiState.value.chapters.single().title).isEqualTo("renamed")
            assertThat(viewModel.uiState.value.anchorOffset).isEqualTo(targetOffset)
            assertThat(viewModel.uiState.value.isPaginating).isTrue()

            store.releaseReplace.complete(Unit)
            viewModel.uiState.first { it.errorMessage != null }
            kotlinx.coroutines.yield()
            runCurrent()

            assertThat(viewModel.uiState.value.errorMessage).isEqualTo("保存章节失败")

            assertThat(source.targetReadAttempts).isEqualTo(2)
            assertThat(viewModel.uiState.value.anchorOffset).isEqualTo(targetOffset)

            source.releaseTargetReads.complete(Unit)
            viewModel.uiState.first { it.contentStartOffset == targetOffset - 50_000 }

            val final = viewModel.uiState.value
            assertThat(final.chapters).containsExactlyElementsIn(chapters).inOrder()
            assertThat(final.contentStartOffset).isEqualTo(targetOffset - 50_000)
            assertThat(final.anchorOffset).isEqualTo(targetOffset)
            assertCoherentLayoutSnapshot(final)
        }

    @Test
    fun `jumping outside the loaded window reloads around the absolute offset`() =
        runTest(mainDispatcherRule.dispatcher) {
            val content = ("第1章\n" + "正文".repeat(300_000))
            val source = FakeBookTextSource(content)
            val viewModel = ReaderViewModel(
                FakeRepository(savedOffset = 1, contentLength = content.length.toLong()),
                source,
                EpochClock { 99L },
                FakeReaderSettingsRepository(),
                FakeBookPaginator(pageSize = 850),
                FakeBookmarkRepository(),
                Dispatchers.Unconfined,
            )
            viewModel.open("book-1")
            advanceUntilIdle()
            viewModel.updateLayout(
                ReaderLayoutSpec(widthPx = 300, heightPx = 500, fontSizePx = 40f, lineHeightPx = 64f),
            )
            advanceUntilIdle()

            viewModel.jumpToOffset(400_000)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.contentStartOffset).isAtMost(400_000)
            assertThat(state.contentStartOffset + state.content.length).isAtLeast(400_000)
            assertThat(state.anchorOffset).isEqualTo(400_000)
            assertThat(state.pages[state.currentPageIndex].startOffset).isAtMost(400_000)
            assertThat(source.requests.any { it.anchorOffset == 400_000L }).isTrue()
        }

    @Test
    fun `temporary browsing preserves stable progress and can return to its origin`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeRepository(savedOffset = 4, contentLength = 12)
            val viewModel = ReaderViewModel(
                repository,
                FakeBookTextSource("甲乙丙丁戊己庚辛壬癸子丑"),
                EpochClock { 99L },
                FakeReaderSettingsRepository(),
                FakeBookPaginator(pageSize = 4),
                FakeBookmarkRepository(),
                Dispatchers.Unconfined,
            )
            viewModel.open("book-1", pageUtf16Units = 4)
            advanceUntilIdle()

            viewModel.jumpTemporarily(8, ReaderJumpReason.SEARCH)
            viewModel.goToPage(0)
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.isBrowsingTemporarily).isTrue()
            assertThat(viewModel.uiState.value.stableAnchorOffset).isEqualTo(4)
            assertThat(repository.savedProgress).isNull()

            viewModel.returnToOrigin()
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.anchorOffset).isEqualTo(4)
            assertThat(viewModel.uiState.value.isBrowsingTemporarily).isFalse()
            assertThat(repository.savedProgress).isNull()
        }

    @Test
    fun `chapter jump starts the visible page at the chapter heading`() =
        runTest(mainDispatcherRule.dispatcher) {
            val content = "序言正文第一章正文第二章后续正文"
            val secondChapterStart = content.indexOf("第二章")
            val chapterStore = FakeChapterIndexStore().apply {
                snapshot = ChapterIndexSnapshot(
                    chapters = listOf(
                        DetectedChapter("第一章", content.indexOf("第一章")),
                        DetectedChapter("第二章", secondChapterStart),
                    ),
                    manuallyEdited = true,
                )
            }
            val viewModel = ReaderViewModel(
                FakeRepository(savedOffset = 0, contentLength = content.length.toLong()),
                FakeBookTextSource(content),
                EpochClock { 99L },
                FakeReaderSettingsRepository(),
                FakeBookPaginator(pageSize = 4),
                FakeBookmarkRepository(),
                Dispatchers.Unconfined,
                chapterStore,
            )
            viewModel.open("book-1", pageUtf16Units = 4)
            advanceUntilIdle()
            viewModel.updateLayout(
                ReaderLayoutSpec(widthPx = 300, heightPx = 500, fontSizePx = 40f, lineHeightPx = 64f),
            )
            advanceUntilIdle()

            viewModel.jumpTemporarily(secondChapterStart, ReaderJumpReason.CHAPTER)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.pages[state.currentPageIndex].startOffset).isEqualTo(secondChapterStart)
        }

    @Test
    fun `deleting a manual chapter removes its forced page boundary`() =
        runTest(mainDispatcherRule.dispatcher) {
            val content = "甲乙丙丁戊己庚辛壬癸子丑"
            val removedBoundary = 5
            val chapterStore = FakeChapterIndexStore().apply {
                snapshot = ChapterIndexSnapshot(
                    chapters = listOf(
                        DetectedChapter("第一章", 0),
                        DetectedChapter("第二章", removedBoundary),
                    ),
                    manuallyEdited = true,
                )
            }
            val viewModel = ReaderViewModel(
                FakeRepository(savedOffset = 0, contentLength = content.length.toLong()),
                FakeBookTextSource(content),
                EpochClock { 99L },
                FakeReaderSettingsRepository(),
                FakeBookPaginator(pageSize = 4),
                FakeBookmarkRepository(),
                Dispatchers.Unconfined,
                chapterStore,
            )
            viewModel.open("book-1", pageUtf16Units = 4)
            advanceUntilIdle()
            viewModel.updateLayout(
                ReaderLayoutSpec(widthPx = 300, heightPx = 500, fontSizePx = 40f, lineHeightPx = 64f),
            )
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.pages.any { it.startOffset == removedBoundary }).isTrue()

            viewModel.deleteChapter(removedBoundary)
            val repaginated = viewModel.uiState.first { state ->
                !state.isPaginating && state.pages.none { page ->
                    page.startOffset == removedBoundary
                }
            }

            assertThat(repaginated.pages.none { it.startOffset == removedBoundary }).isTrue()
        }

    @Test
    fun `chapter reanalysis applies newly detected page boundaries`() =
        runTest(mainDispatcherRule.dispatcher) {
            val content = "前言正文\n序章\n后续正文内容"
            val prefaceChapterStart = content.indexOf("序章")
            val chapterStore = FakeChapterIndexStore().apply {
                snapshot = ChapterIndexSnapshot(
                    chapters = listOf(DetectedChapter("正文", 0)),
                    manuallyEdited = true,
                )
            }
            val viewModel = ReaderViewModel(
                FakeRepository(savedOffset = 0, contentLength = content.length.toLong()),
                FakeBookTextSource(content),
                EpochClock { 99L },
                FakeReaderSettingsRepository(),
                FakeBookPaginator(pageSize = 4),
                FakeBookmarkRepository(),
                Dispatchers.Unconfined,
                chapterStore,
            )
            viewModel.open("book-1", pageUtf16Units = 4)
            advanceUntilIdle()
            viewModel.updateLayout(
                ReaderLayoutSpec(widthPx = 300, heightPx = 500, fontSizePx = 40f, lineHeightPx = 64f),
            )
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.pages.none { it.startOffset == prefaceChapterStart }).isTrue()

            viewModel.reanalyzeChapters(ChapterRuleSet.BROAD)
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.pages.any {
                it.startOffset <= prefaceChapterStart && it.endOffset > prefaceChapterStart
            }).isTrue()
        }

    @Test
    fun `coarse chapter reanalysis rebuilds layout and pages as one snapshot`() =
        runTest(mainDispatcherRule.dispatcher) {
            val content = "前言正文\n序章\n后续正文内容"
            val chapterStart = content.indexOf("序章")
            val chapterStore = FakeChapterIndexStore().apply {
                snapshot = ChapterIndexSnapshot(
                    chapters = listOf(DetectedChapter("正文", 0)),
                    ruleSet = ChapterRuleSet.STANDARD,
                    manuallyEdited = true,
                )
            }
            val viewModel = ReaderViewModel(
                FakeRepository(savedOffset = 0, contentLength = content.length.toLong()),
                FakeBookTextSource(content),
                EpochClock { 99L },
                FakeReaderSettingsRepository(),
                FakeBookPaginator(pageSize = 850),
                FakeBookmarkRepository(),
                Dispatchers.Unconfined,
                chapterStore,
            )

            viewModel.open("book-1", pageUtf16Units = 850)
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.pages.none { it.startOffset == chapterStart }).isTrue()

            viewModel.reanalyzeChapters(ChapterRuleSet.BROAD)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.pages.any {
                it.startOffset <= chapterStart && it.endOffset > chapterStart
            }).isTrue()
            assertThat(state.layoutContent.length).isEqualTo(state.content.length)
            assertThat(state.pages.first().startOffset).isEqualTo(state.contentStartOffset)
            assertThat(state.pages.last().endOffset)
                .isEqualTo(state.contentStartOffset + state.content.length)
            assertThat(state.pages.zipWithNext().all { (first, second) ->
                first.endOffset == second.startOffset
            }).isTrue()
        }

    @Test
    fun `manual chapter rename remains authoritative when older automatic analysis resumes`() =
        runTest(mainDispatcherRule.dispatcher) {
            val content = "第一章\n正文内容"
            val store = BlockingAutomaticChapterIndexStore()
            val viewModel = ReaderViewModel(
                FakeRepository(savedOffset = 0, contentLength = content.length.toLong()),
                FakeBookTextSource(content),
                EpochClock { 99L },
                FakeReaderSettingsRepository(),
                FakeBookPaginator(pageSize = 850),
                FakeBookmarkRepository(),
                Dispatchers.Unconfined,
                store,
            )

            viewModel.open("book-1", pageUtf16Units = 850)
            runCurrent()
            assertThat(store.automaticStarted.isCompleted).isTrue()

            viewModel.renameChapter(0, "人工标题")
            runCurrent()
            store.allowAutomatic.complete(Unit)
            store.manualCompleted.await()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.chaptersManuallyEdited).isTrue()
            assertThat(state.chapters.first { it.startOffset == 0 }.title).isEqualTo("人工标题")
            assertThat(store.snapshot.manuallyEdited).isTrue()
        }

    @Test
    fun `continue from temporary location commits the visible anchor`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeRepository(savedOffset = 4, contentLength = 12)
            val viewModel = ReaderViewModel(
                repository,
                FakeBookTextSource("甲乙丙丁戊己庚辛壬癸子丑"),
                EpochClock { 99L },
                FakeReaderSettingsRepository(),
                FakeBookPaginator(pageSize = 4),
                FakeBookmarkRepository(),
                Dispatchers.Unconfined,
            )
            viewModel.open("book-1", pageUtf16Units = 4)
            advanceUntilIdle()

            viewModel.jumpTemporarily(8, ReaderJumpReason.BOOKMARK)
            viewModel.continueFromHere()
            advanceUntilIdle()
            viewModel.flushProgress()

            assertThat(viewModel.uiState.value.isBrowsingTemporarily).isFalse()
            assertThat(viewModel.uiState.value.stableAnchorOffset).isEqualTo(8)
            assertThat(repository.savedProgress?.anchor?.offset).isEqualTo(8L)
        }

    @Test
    fun `page turn hot path stays in memory and prefetches only near the window edge`() =
        runTest(mainDispatcherRule.dispatcher) {
            val content = "甲".repeat(600_000)
            val source = FakeBookTextSource(content)
            val viewModel = ReaderViewModel(
                FakeRepository(savedOffset = 300_000, contentLength = content.length.toLong()),
                source,
                EpochClock { 99L },
                FakeReaderSettingsRepository(),
                FakeBookPaginator(pageSize = 850),
                FakeBookmarkRepository(),
                Dispatchers.Unconfined,
            )
            viewModel.open("book-1")
            advanceUntilIdle()
            viewModel.updateLayout(
                ReaderLayoutSpec(widthPx = 300, heightPx = 500, fontSizePx = 40f, lineHeightPx = 64f),
            )
            advanceUntilIdle()

            val initial = viewModel.uiState.value
            fun readerWindowRequests(): List<TextSourceRequest> = source.requests.filter {
                it.beforeUtf16Units == 50_000 && it.afterUtf16Units == 100_000
            }
            val readsAfterOpen = readerWindowRequests().size
            viewModel.goToPage(initial.currentPageIndex + 1)
            advanceUntilIdle()
            assertThat(readerWindowRequests()).hasSize(readsAfterOpen)

            val beforeEdge = viewModel.uiState.value
            val nearEndIndex = beforeEdge.pages.lastIndex - 1
            val expectedPrefetchAnchor = beforeEdge.pages[nearEndIndex].startOffset.toLong()
            viewModel.goToPage(nearEndIndex)
            advanceUntilIdle()

            val windowRequestsAfterEdgeTurn = readerWindowRequests()
            assertThat(windowRequestsAfterEdgeTurn.size).isGreaterThan(readsAfterOpen)
            assertThat(windowRequestsAfterEdgeTurn.last().anchorOffset).isEqualTo(expectedPrefetchAnchor)
        }

    private class RecordingDispatcher : CoroutineDispatcher() {
        var dispatchCount: Int = 0
            private set

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            dispatchCount += 1
            block.run()
        }
    }

    private class FakeRepository(
        private val savedOffset: Long = 4,
        contentLength: Long = 10,
        private val savedAnchor: TextAnchor? = null,
    ) : BookRepository {
        val book = sampleBook(contentLength)
        var savedProgress: ReadingProgress? = null
        var openedAtEpochMillis: Long? = null

        override fun observeBooks(): Flow<List<Book>> = flowOf(listOf(book))
        override fun observeProgress(): Flow<List<ReadingProgress>> = flowOf(emptyList())
        override suspend fun addBook(book: Book) = Unit
        override suspend fun getBook(bookId: String): Book? = book.takeIf { it.id == bookId }
        override suspend fun findBySha256(contentSha256: String): Book? = null
        override suspend fun saveProgress(progress: ReadingProgress) { savedProgress = progress }
        override suspend fun getProgress(bookId: String): ReadingProgress? = savedProgress ?: ReadingProgress(
            bookId = bookId,
            anchor = savedAnchor ?: TextAnchor(offset = savedOffset, contextHash = "old"),
            contentLength = book.contentLength,
            updatedAtEpochMillis = 1,
        )
        override suspend fun renameBook(bookId: String, title: String) = Unit
        override suspend fun deleteBook(bookId: String) = Unit
        override suspend fun replaceBook(existingBookId: String, replacement: Book) = Unit
        override suspend fun markOpened(bookId: String, epochMillis: Long) {
            openedAtEpochMillis = epochMillis
        }
    }

    private class LatestWinsRepository(
        private val books: Map<String, Book>,
    ) : BookRepository {
        val bookALookupStarted = kotlinx.coroutines.CompletableDeferred<Unit>()
        val releaseBookA = kotlinx.coroutines.CompletableDeferred<Unit>()
        val markedOpenedBookIds = mutableListOf<String>()

        override fun observeBooks(): Flow<List<Book>> = flowOf(books.values.toList())
        override fun observeProgress(): Flow<List<ReadingProgress>> = flowOf(emptyList())
        override suspend fun addBook(book: Book) = Unit

        override suspend fun getBook(bookId: String): Book? {
            if (bookId == "book-a") {
                bookALookupStarted.complete(Unit)
                kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                    releaseBookA.await()
                }
            }
            return books[bookId]
        }

        override suspend fun findBySha256(contentSha256: String): Book? = null
        override suspend fun saveProgress(progress: ReadingProgress) = Unit

        override suspend fun getProgress(bookId: String): ReadingProgress? = ReadingProgress(
            bookId = bookId,
            anchor = TextAnchor(offset = 0, contextHash = "old"),
            contentLength = books.getValue(bookId).contentLength,
            updatedAtEpochMillis = 1,
        )

        override suspend fun renameBook(bookId: String, title: String) = Unit
        override suspend fun deleteBook(bookId: String) = Unit
        override suspend fun replaceBook(existingBookId: String, replacement: Book) = Unit

        override suspend fun markOpened(bookId: String, epochMillis: Long) {
            markedOpenedBookIds += bookId
        }
    }

    private class LatestWinsTextSource(
        private val contents: Map<String, String>,
    ) : TextSource {
        val readBookIds = mutableListOf<String>()

        override suspend fun readWindow(
            normalizedPath: String,
            anchorOffset: Long,
            beforeUtf16Units: Int,
            afterUtf16Units: Int,
        ): TextWindow {
            val bookId = normalizedPath.substringAfter("books/").substringBefore('/')
            readBookIds += bookId
            val content = contents.getValue(bookId)
            return TextWindow(
                startOffset = 0,
                text = content,
                totalUtf16Length = content.length.toLong(),
            )
        }
    }

    private class LatestWinsChapterIndexStore(
        private val snapshots: Map<String, ChapterIndexSnapshot>,
    ) : ChapterIndexStore {
        val snapshotBookIds = mutableListOf<String>()

        override suspend fun getSnapshot(bookId: String): ChapterIndexSnapshot =
            snapshots[bookId]?.also { snapshotBookIds += bookId } ?: ChapterIndexSnapshot().also {
                snapshotBookIds += bookId
            }

        override suspend fun replaceAutomatically(
            bookId: String,
            chapters: List<DetectedChapter>,
            ruleSet: ChapterRuleSet,
        ): Boolean = false

        override suspend fun replaceManually(bookId: String, chapters: List<DetectedChapter>) = Unit

        override suspend fun replaceForRuleChange(
            bookId: String,
            chapters: List<DetectedChapter>,
            ruleSet: ChapterRuleSet,
        ) = Unit
    }

    private class RecordingSearchRepository(
        private val books: Map<String, Book>,
    ) : BookSearchRepository {
        val ensureIndexedBookIds = mutableListOf<String>()

        override suspend fun ensureIndexed(bookId: String) {
            ensureIndexedBookIds += bookId
        }

        override fun observeIndexState(bookId: String): Flow<BookSearchIndexState> = flowOf(
            BookSearchIndexState(
                bookId = bookId,
                status = BookSearchIndexStatus.READY,
                indexedUtf16Length = books.getValue(bookId).contentLength,
            ),
        )

        override suspend fun search(
            bookId: String,
            query: String,
            limit: Int,
        ): List<BookSearchResult> = emptyList()

        override suspend fun rebuild(bookId: String) = Unit
        override suspend fun cancelIndex(bookId: String) = Unit
    }

    private class FailingEndReadingSessionRepository : ReadingSessionRepository {
        var failOnEnd = false
        var endAttempts = 0

        override suspend fun begin(bookId: String, nowEpochMillis: Long): String = "session-1"
        override suspend fun recordInteraction(sessionId: String, nowEpochMillis: Long) = Unit
        override suspend fun end(sessionId: String, nowEpochMillis: Long) {
            endAttempts += 1
            if (failOnEnd) error("statistics unavailable")
        }
        override suspend fun closeStaleSessions(nowEpochMillis: Long) = Unit
        override fun observeBookStatistics(bookId: String): Flow<ReadingStatistics> =
            flowOf(ReadingStatistics(bookId, 0, 0, 0, 0))
        override fun observeStatistics(
            rangeStartEpochMillis: Long,
            rangeEndEpochMillis: Long,
        ): Flow<ReadingStatistics> = flowOf(
            ReadingStatistics(null, 0, 0, rangeStartEpochMillis, rangeEndEpochMillis),
        )
        override suspend fun clearStatistics() = Unit
    }

    private data class TextSourceRequest(
        val anchorOffset: Long,
        val beforeUtf16Units: Int,
        val afterUtf16Units: Int,
    )

    private class FakeBookTextSource(private val content: String) : TextSource {
        val requests = mutableListOf<TextSourceRequest>()

        override suspend fun readWindow(
            normalizedPath: String,
            anchorOffset: Long,
            beforeUtf16Units: Int,
            afterUtf16Units: Int,
        ): TextWindow {
            requests += TextSourceRequest(anchorOffset, beforeUtf16Units, afterUtf16Units)
            val safeAnchor = anchorOffset.coerceIn(0, content.length.toLong()).toInt()
            var start = (safeAnchor - beforeUtf16Units).coerceAtLeast(0)
            var end = (safeAnchor + afterUtf16Units).coerceAtMost(content.length)
            if (start in 1 until content.length && content[start].isLowSurrogate()) start -= 1
            if (end in 1 until content.length && content[end - 1].isHighSurrogate()) end += 1
            return TextWindow(
                startOffset = start.toLong(),
                text = content.substring(start, end),
                totalUtf16Length = content.length.toLong(),
            )
        }
    }

    private class CancellablePreviewTextSource(private val content: String) : TextSource {
        val previewAnchors = mutableListOf<Long>()
        val completedPreviewAnchors = mutableListOf<Long>()
        private val latestPreviewGate = kotlinx.coroutines.CompletableDeferred<Unit>()

        override suspend fun readWindow(
            normalizedPath: String,
            anchorOffset: Long,
            beforeUtf16Units: Int,
            afterUtf16Units: Int,
        ): TextWindow {
            val isPreview = beforeUtf16Units == 80 && afterUtf16Units == 160
            if (isPreview) previewAnchors += anchorOffset
            if (isPreview && anchorOffset < content.length / 2) {
                delay(10_000)
            } else if (isPreview) {
                latestPreviewGate.await()
            }
            if (isPreview) completedPreviewAnchors += anchorOffset
            val safeAnchor = anchorOffset.coerceIn(0, content.length.toLong()).toInt()
            val start = (safeAnchor - beforeUtf16Units).coerceAtLeast(0)
            val end = (safeAnchor + afterUtf16Units).coerceAtMost(content.length)
            return TextWindow(start.toLong(), content.substring(start, end), content.length.toLong())
        }
    }

    private class OneShotTextSource(private val content: String) : TextSource {
        private var requestCount = 0

        override suspend fun readWindow(
            normalizedPath: String,
            anchorOffset: Long,
            beforeUtf16Units: Int,
            afterUtf16Units: Int,
        ): TextWindow {
            if (requestCount++ > 0) error("background scan interrupted")
            return TextWindow(
                startOffset = 0,
                text = content,
                totalUtf16Length = content.length.toLong(),
            )
        }
    }

    private class FakeReaderSettingsRepository(
        initial: ReaderSettings = ReaderSettings(),
    ) : ReaderSettingsRepository {
        private val global = MutableStateFlow(initial)
        private val resolved = mutableMapOf<String, MutableStateFlow<ReaderSettings>>()
        val bookOverrides = mutableMapOf<String, ReaderSettingsOverrides>()
        val observedBookIds = mutableListOf<String>()
        val observedOverrideBookIds = mutableListOf<String>()
        var globalWriteCount = 0
        var bookWriteCount = 0
        val current: ReaderSettings get() = global.value

        override fun observe(bookId: String?): Flow<ReaderSettings> {
            if (bookId != null) observedBookIds += bookId
            return if (bookId == null) global else resolved.getOrPut(bookId) {
                MutableStateFlow(global.value.resolve(bookOverrides[bookId] ?: ReaderSettingsOverrides()))
            }
        }

        override fun observeBookOverrides(bookId: String): Flow<ReaderSettingsOverrides> {
            observedOverrideBookIds += bookId
            return flowOf(bookOverrides[bookId] ?: ReaderSettingsOverrides())
        }

        override suspend fun updateGlobal(settings: ReaderSettings) {
            globalWriteCount += 1
            emitGlobal(settings)
        }

        override suspend fun updateBookOverrides(bookId: String, overrides: ReaderSettingsOverrides) {
            bookWriteCount += 1
            bookOverrides[bookId] = overrides
            resolved.getOrPut(bookId) { MutableStateFlow(global.value) }.value = global.value.resolve(overrides)
        }

        override suspend fun clearBookOverrides(bookId: String) {
            bookOverrides.remove(bookId)
            resolved.getOrPut(bookId) { MutableStateFlow(global.value) }.value = global.value
        }

        fun emitGlobal(settings: ReaderSettings) {
            global.value = settings
            resolved.forEach { (bookId, flow) ->
                flow.value = settings.resolve(bookOverrides[bookId] ?: ReaderSettingsOverrides())
            }
        }
    }

    private class LatestWinsSettingsRepository(
        private val stale: ReaderSettings,
        private val current: ReaderSettings,
    ) : ReaderSettingsRepository {
        val staleReadStarted = kotlinx.coroutines.CompletableDeferred<Unit>()
        val releaseStaleRead = kotlinx.coroutines.CompletableDeferred<Unit>()
        private var globalReadCount = 0

        override fun observe(bookId: String?): Flow<ReaderSettings> {
            if (bookId != null) return flowOf(current)
            val readIndex = globalReadCount++
            return if (readIndex == 0) {
                object : Flow<ReaderSettings> {
                    override suspend fun collect(
                        collector: kotlinx.coroutines.flow.FlowCollector<ReaderSettings>,
                    ) {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                            staleReadStarted.complete(Unit)
                            releaseStaleRead.await()
                            collector.emit(stale)
                        }
                    }
                }
            } else {
                flowOf(current)
            }
        }

        override fun observeBookOverrides(bookId: String): Flow<ReaderSettingsOverrides> =
            flowOf(ReaderSettingsOverrides())

        override suspend fun updateGlobal(settings: ReaderSettings) = Unit

        override suspend fun updateBookOverrides(
            bookId: String,
            overrides: ReaderSettingsOverrides,
        ) = Unit

        override suspend fun clearBookOverrides(bookId: String) = Unit
    }

    private class BarrierCommitSettingsRepository(
        initial: ReaderSettings = ReaderSettings(fontSizeSp = 20f),
        private val failUpdate: Boolean = false,
    ) : ReaderSettingsRepository {
        private val global = MutableStateFlow(initial)
        val updateStarted = kotlinx.coroutines.CompletableDeferred<Unit>()
        val releaseUpdate = kotlinx.coroutines.CompletableDeferred<Unit>()

        override fun observe(bookId: String?): Flow<ReaderSettings> = global

        override fun observeBookOverrides(bookId: String): Flow<ReaderSettingsOverrides> =
            flowOf(ReaderSettingsOverrides())

        override suspend fun updateGlobal(settings: ReaderSettings) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                updateStarted.complete(Unit)
                releaseUpdate.await()
                if (failUpdate) error("controlled settings failure")
                global.value = settings
            }
        }

        override suspend fun updateBookOverrides(
            bookId: String,
            overrides: ReaderSettingsOverrides,
        ) = Unit

        override suspend fun clearBookOverrides(bookId: String) = Unit
    }

    private class BarrierResolvedSettingsRepository(
        initial: ReaderSettings = ReaderSettings(fontSizeSp = 20f),
    ) : ReaderSettingsRepository {
        private val global = MutableStateFlow(initial)
        private var bookObserveCount = 0
        val resolvedReadStarted = kotlinx.coroutines.CompletableDeferred<Unit>()
        val releaseResolvedRead = kotlinx.coroutines.CompletableDeferred<Unit>()
        val current: ReaderSettings get() = global.value

        override fun observe(bookId: String?): Flow<ReaderSettings> {
            if (bookId == null) return global
            val readIndex = bookObserveCount++
            if (readIndex == 0) return global
            return object : Flow<ReaderSettings> {
                override suspend fun collect(
                    collector: kotlinx.coroutines.flow.FlowCollector<ReaderSettings>,
                ) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                        resolvedReadStarted.complete(Unit)
                        releaseResolvedRead.await()
                        collector.emit(global.value)
                    }
                }
            }
        }

        override fun observeBookOverrides(bookId: String): Flow<ReaderSettingsOverrides> =
            flowOf(ReaderSettingsOverrides())

        override suspend fun updateGlobal(settings: ReaderSettings) {
            global.value = settings
        }

        override suspend fun updateBookOverrides(
            bookId: String,
            overrides: ReaderSettingsOverrides,
        ) = Unit

        override suspend fun clearBookOverrides(bookId: String) = Unit
    }

    private class ViewModelThemeRepository : ReaderThemeRepository {
        private val values = MutableStateFlow<List<ReaderThemePreset>>(emptyList())

        override fun observeAll(): Flow<List<ReaderThemePreset>> = values

        override suspend fun save(preset: ReaderThemePreset) {
            values.value = values.value.filterNot { it.id == preset.id } + preset
        }

        override suspend fun delete(presetId: String) {
            values.value = values.value.filterNot { it.id == presetId }
        }
    }

    private class ViewModelScheduleRepository(
        initial: ReaderThemeSchedule,
    ) : ReaderThemeScheduleRepository {
        val schedule = MutableStateFlow(initial)
        val manual = MutableStateFlow<ReaderThemeManualOverride?>(null)
        var failManualUpdates: Boolean = false

        override fun observeSchedule(): Flow<ReaderThemeSchedule> = schedule
        override fun observeManualOverride(): Flow<ReaderThemeManualOverride?> = manual

        override suspend fun updateSchedule(schedule: ReaderThemeSchedule) {
            this.schedule.value = schedule
        }

        override suspend fun updateManualOverride(override: ReaderThemeManualOverride?) {
            if (failManualUpdates) error("controlled manual theme failure")
            manual.value = override
        }
    }

    private class FakeImportedFontRepository : ImportedFontRepository {
        private val values = MutableStateFlow<List<ImportedFont>>(emptyList())
        var removalResult: FontRemovalResult = FontRemovalResult.Removed
        var removeCallCount: Int = 0

        override fun observeAll(): Flow<List<ImportedFont>> = values

        override suspend fun importFont(source: ImportSource): ImportedFont {
            source.openStream().use { it.readBytes() }
            val font = ImportedFont("font-1", source.displayName, "sha", source.sizeBytes, 1)
            values.value = listOf(font)
            return font
        }

        override suspend fun remove(fontId: String): FontRemovalResult {
            removeCallCount++
            if (removalResult == FontRemovalResult.Removed) {
                values.value = values.value.filterNot { it.id == fontId }
            }
            return removalResult
        }
    }

    private class FakeChapterIndexStore(
        private val cached: List<DetectedChapter> = emptyList(),
    ) : ChapterIndexStore {
        var saved: List<DetectedChapter> = emptyList()
        var snapshot = ChapterIndexSnapshot(chapters = cached)

        override suspend fun getSnapshot(bookId: String): ChapterIndexSnapshot = snapshot

        override suspend fun replaceAutomatically(
            bookId: String,
            chapters: List<DetectedChapter>,
            ruleSet: ChapterRuleSet,
        ): Boolean {
            if (snapshot.manuallyEdited) return false
            saved = chapters
            snapshot = ChapterIndexSnapshot(chapters, ruleSet, false)
            return true
        }

        override suspend fun replaceManually(bookId: String, chapters: List<DetectedChapter>) {
            saved = chapters
            snapshot = snapshot.copy(chapters = chapters, manuallyEdited = true)
        }

        override suspend fun replaceForRuleChange(
            bookId: String,
            chapters: List<DetectedChapter>,
            ruleSet: ChapterRuleSet,
        ) {
            saved = chapters
            snapshot = ChapterIndexSnapshot(chapters, ruleSet, false)
        }
    }

    private class BlockingAutomaticChapterIndexStore : ChapterIndexStore {
        val automaticStarted = kotlinx.coroutines.CompletableDeferred<Unit>()
        val allowAutomatic = kotlinx.coroutines.CompletableDeferred<Unit>()
        val manualCompleted = kotlinx.coroutines.CompletableDeferred<Unit>()
        var snapshot = ChapterIndexSnapshot()

        override suspend fun getSnapshot(bookId: String): ChapterIndexSnapshot = snapshot

        override suspend fun replaceAutomatically(
            bookId: String,
            chapters: List<DetectedChapter>,
            ruleSet: ChapterRuleSet,
        ): Boolean {
            automaticStarted.complete(Unit)
            return kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                allowAutomatic.await()
                snapshot = ChapterIndexSnapshot(chapters, ruleSet, false)
                true
            }
        }

        override suspend fun replaceManually(bookId: String, chapters: List<DetectedChapter>) {
            snapshot = ChapterIndexSnapshot(chapters, snapshot.ruleSet, true)
            manualCompleted.complete(Unit)
        }

        override suspend fun replaceForRuleChange(
            bookId: String,
            chapters: List<DetectedChapter>,
            ruleSet: ChapterRuleSet,
        ) {
            snapshot = ChapterIndexSnapshot(chapters, ruleSet, false)
        }
    }

    private class FailingManualChapterIndexStore(
        initial: ChapterIndexSnapshot,
    ) : ChapterIndexStore {
        private val snapshot = initial

        override suspend fun getSnapshot(bookId: String): ChapterIndexSnapshot = snapshot

        override suspend fun replaceAutomatically(
            bookId: String,
            chapters: List<DetectedChapter>,
            ruleSet: ChapterRuleSet,
        ): Boolean = false

        override suspend fun replaceManually(bookId: String, chapters: List<DetectedChapter>) {
            error("manual chapter persistence failed")
        }

        override suspend fun replaceForRuleChange(
            bookId: String,
            chapters: List<DetectedChapter>,
            ruleSet: ChapterRuleSet,
        ) = Unit
    }

    private class BarrierFailingManualChapterIndexStore(
        private val snapshot: ChapterIndexSnapshot,
    ) : ChapterIndexStore {
        val replaceStarted = kotlinx.coroutines.CompletableDeferred<Unit>()
        val releaseReplace = kotlinx.coroutines.CompletableDeferred<Unit>()

        override suspend fun getSnapshot(bookId: String): ChapterIndexSnapshot = snapshot

        override suspend fun replaceAutomatically(
            bookId: String,
            chapters: List<DetectedChapter>,
            ruleSet: ChapterRuleSet,
        ): Boolean = false

        override suspend fun replaceManually(bookId: String, chapters: List<DetectedChapter>) {
            replaceStarted.complete(Unit)
            releaseReplace.await()
            error("manual chapter persistence failed")
        }

        override suspend fun replaceForRuleChange(
            bookId: String,
            chapters: List<DetectedChapter>,
            ruleSet: ChapterRuleSet,
        ) = Unit
    }

    private class RestartableWindowTextSource(
        private val content: String,
        private val targetOffset: Long,
    ) : TextSource {
        var targetReadAttempts: Int = 0
            private set
        val releaseTargetReads = kotlinx.coroutines.CompletableDeferred<Unit>()

        override suspend fun readWindow(
            normalizedPath: String,
            anchorOffset: Long,
            beforeUtf16Units: Int,
            afterUtf16Units: Int,
        ): TextWindow {
            if (anchorOffset == targetOffset && beforeUtf16Units == 50_000 && afterUtf16Units == 100_000) {
                targetReadAttempts += 1
                releaseTargetReads.await()
            }
            val safeAnchor = anchorOffset.coerceIn(0, content.length.toLong()).toInt()
            val start = (safeAnchor - beforeUtf16Units).coerceAtLeast(0)
            val end = (safeAnchor + afterUtf16Units).coerceAtMost(content.length)
            return TextWindow(
                startOffset = start.toLong(),
                text = content.substring(start, end),
                totalUtf16Length = content.length.toLong(),
            )
        }
    }

    private class FakeBookPaginator(private val pageSize: Int) : BookPaginator {
        override suspend fun paginate(text: String, spec: ReaderLayoutSpec): List<ReaderPage> =
            text.chunked(pageSize).mapIndexed { index, page ->
                ReaderPage(
                    startOffset = index * pageSize,
                    endOffset = (index * pageSize + page.length).coerceAtMost(text.length),
                )
            }
    }

    private class VaryingBookPaginator : BookPaginator {
        override suspend fun paginate(text: String, spec: ReaderLayoutSpec): List<ReaderPage> {
            val pageSize = (spec.widthPx / 100).coerceAtLeast(1)
            return text.chunked(pageSize).mapIndexed { index, page ->
                ReaderPage(
                    startOffset = index * pageSize,
                    endOffset = (index * pageSize + page.length).coerceAtMost(text.length),
                )
            }
        }
    }

    private class ControllablePaginator : BookPaginator {
        var callCount = 0
        var cancelledCallCount = 0
        var suspendedCallCount = 0
        var suspendNextCall = false
        var lastCompletedSpec: ReaderLayoutSpec? = null

        override suspend fun paginate(text: String, spec: ReaderLayoutSpec): List<ReaderPage> {
            callCount += 1
            if (suspendNextCall) {
                suspendNextCall = false
                suspendedCallCount += 1
                return suspendCancellableCoroutine { continuation ->
                    continuation.invokeOnCancellation {
                        cancelledCallCount += 1
                    }
                }
            }
            lastCompletedSpec = spec
            return listOf(ReaderPage(0, text.length))
        }
    }

    private class NonCancellablePaginator : BookPaginator {
        val firstCallStarted = kotlinx.coroutines.CompletableDeferred<Unit>()
        val releaseFirstCall = kotlinx.coroutines.CompletableDeferred<Unit>()
        private var callCount = 0

        override suspend fun paginate(text: String, spec: ReaderLayoutSpec): List<ReaderPage> =
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                if (callCount++ == 0) {
                    firstCallStarted.complete(Unit)
                    releaseFirstCall.await()
                }
                if (spec.fontSizePx == 20f) {
                    listOf(ReaderPage(0, text.length))
                } else {
                    listOf(
                        ReaderPage(0, 2),
                        ReaderPage(2, text.length),
                    )
                }
            }
    }

    private class RollbackInterleavingPaginator : BookPaginator {
        var blockNext = false
        val firstCallCompleted = kotlinx.coroutines.CompletableDeferred<Unit>()
        val blockedCallStarted = kotlinx.coroutines.CompletableDeferred<Unit>()
        val releaseBlockedCall = kotlinx.coroutines.CompletableDeferred<Unit>()

        fun blockNextCall() {
            blockNext = true
        }

        override suspend fun paginate(text: String, spec: ReaderLayoutSpec): List<ReaderPage> {
            if (blockNext) {
                blockNext = false
                kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                    blockedCallStarted.complete(Unit)
                    releaseBlockedCall.await()
                }
            }
            return listOf(ReaderPage(0, text.length)).also {
                firstCallCompleted.complete(Unit)
            }
        }
    }

    private class FakeBookmarkRepository : BookmarkRepository {
        private val values = kotlinx.coroutines.flow.MutableStateFlow<List<Bookmark>>(emptyList())
        val current: List<Bookmark> get() = values.value

        override fun observe(bookId: String): Flow<List<Bookmark>> = values
        override suspend fun add(bookmark: Bookmark) { values.value += bookmark }
        override suspend fun delete(bookmarkId: String) {
            values.value = values.value.filterNot { it.id == bookmarkId }
        }
    }

    private class FakeBookSearchRepository(private val content: String) : BookSearchRepository {
        val ensureRequests = mutableListOf<String>()
        private val state = kotlinx.coroutines.flow.MutableStateFlow(
            BookSearchIndexState("book-1", BookSearchIndexStatus.READY, content.length.toLong()),
        )

        override suspend fun ensureIndexed(bookId: String) {
            ensureRequests += bookId
        }

        override fun observeIndexState(bookId: String): Flow<BookSearchIndexState> = state

        override suspend fun search(bookId: String, query: String, limit: Int): List<BookSearchResult> =
            buildList {
                var from = 0
                while (size < limit) {
                    val offset = content.indexOf(query, from)
                    if (offset < 0) break
                    add(
                        BookSearchResult(
                            offset = offset.toLong(),
                            endOffset = (offset + query.length).toLong(),
                            chapterStartOffset = null,
                            snippet = query,
                            highlightStart = 0,
                            highlightEnd = query.length,
                        ),
                    )
                    from = offset + query.length
                }
            }

        override suspend fun rebuild(bookId: String) {
            state.value = state.value.copy(status = BookSearchIndexStatus.BUILDING)
        }

        override suspend fun cancelIndex(bookId: String) {
            state.value = state.value.copy(status = BookSearchIndexStatus.ERROR, errorMessage = "已取消")
        }
    }

    private class MainDispatcherRule(
        val dispatcher: TestDispatcher = StandardTestDispatcher(),
    ) : TestWatcher() {
        override fun starting(description: Description) = Dispatchers.setMain(dispatcher)
        override fun finished(description: Description) = Dispatchers.resetMain()
    }

    private companion object {
        fun pageTexts(state: ReaderUiState): List<String> = state.pages.map(state::textFor)

        fun assertCoherentLayoutSnapshot(state: ReaderUiState) {
            assertThat(state.layoutContent).hasLength(state.content.length)
            assertThat(state.pages.first().startOffset).isEqualTo(state.contentStartOffset)
            assertThat(state.pages.last().endOffset)
                .isEqualTo(state.contentStartOffset + state.content.length)
            assertThat(state.pages.zipWithNext().all { (first, second) ->
                first.endOffset == second.startOffset
            }).isTrue()
            assertThat(state.pages.joinToString(separator = "") { state.layoutTextFor(it) })
                .isEqualTo(state.layoutContent)
        }

        fun sampleBook(contentLength: Long = 10, id: String = "book-1") = Book(
            id = id,
            title = "测试小说",
            author = null,
            originalFileName = "测试小说.txt",
            originalPath = "books/$id/original.txt",
            normalizedPath = "books/$id/content.txt",
            charsetName = "UTF-8",
            contentSha256 = "hash",
            contentLength = contentLength,
            createdAtEpochMillis = 1,
            lastOpenedAtEpochMillis = null,
        )
    }
}
