package com.xinyue.reader.feature.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.xinyue.reader.core.data.TextSource
import com.xinyue.reader.core.data.TextWindow
import com.xinyue.reader.core.data.AnchorRepairCoordinator
import com.xinyue.reader.core.data.ChapterIndexStore
import com.xinyue.reader.core.data.ChapterIndexSnapshot
import com.xinyue.reader.core.data.ImportSourceFactory
import com.xinyue.reader.core.data.ReaderPositionPreview
import com.xinyue.reader.core.data.ReaderPositionPreviewService
import com.xinyue.reader.core.domain.model.Book
import com.xinyue.reader.core.domain.model.AnnotationKind
import com.xinyue.reader.core.domain.model.AnnotationExportRequest
import com.xinyue.reader.core.domain.model.AnnotationExportResult
import com.xinyue.reader.core.domain.model.HighlightColor
import com.xinyue.reader.core.domain.model.ReaderAnnotation
import com.xinyue.reader.core.domain.model.TextRangeAnchor
import com.xinyue.reader.core.domain.model.Bookmark
import com.xinyue.reader.core.domain.model.BookSearchIndexState
import com.xinyue.reader.core.domain.model.BookSearchIndexStatus
import com.xinyue.reader.core.domain.model.BookSearchResult
import com.xinyue.reader.core.domain.model.ReaderSettings
import com.xinyue.reader.core.domain.model.ReaderSettingsOverrides
import com.xinyue.reader.core.domain.model.ReaderThemePreset
import com.xinyue.reader.core.domain.model.ReaderThemeManualOverride
import com.xinyue.reader.core.domain.model.ReaderThemeSchedule
import com.xinyue.reader.core.domain.model.ImportSource
import com.xinyue.reader.core.domain.model.ImportedFont
import com.xinyue.reader.core.domain.model.FontRemovalResult
import com.xinyue.reader.core.domain.model.ReadingProgress
import com.xinyue.reader.core.domain.model.TextAnchor
import com.xinyue.reader.core.domain.repository.BookRepository
import com.xinyue.reader.core.domain.repository.BookmarkRepository
import com.xinyue.reader.core.domain.repository.BookSearchRepository
import com.xinyue.reader.core.domain.repository.AnnotationRepository
import com.xinyue.reader.core.domain.repository.AnnotationExportService
import com.xinyue.reader.core.domain.repository.ReaderSettingsRepository
import com.xinyue.reader.core.domain.repository.ReaderThemeScheduleRepository
import com.xinyue.reader.core.domain.repository.ImportedFontRepository
import com.xinyue.reader.core.domain.repository.ReadingSessionRepository
import com.xinyue.reader.core.domain.time.EpochClock
import com.xinyue.reader.core.text.ChapterDetector
import com.xinyue.reader.core.text.ReaderLayoutWhitespaceNormalizer
import com.xinyue.reader.core.text.ChapterRuleSet
import com.xinyue.reader.core.text.CharacterPaginator
import com.xinyue.reader.core.text.DetectedChapter
import com.xinyue.reader.core.text.TextFingerprint
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlin.math.roundToLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class ReaderPage(
    /** Absolute UTF-16 offset in the normalized book text. */
    val startOffset: Int,
    /** Absolute UTF-16 offset in the normalized book text. */
    val endOffset: Int,
)

data class ReaderUiState(
    val book: Book? = null,
    /** Only the bounded text window around the reading anchor, never the whole large book. */
    val content: String = "",
    /** Same-length display copy with only pathological chapter-boundary whitespace collapsed. */
    val layoutContent: String = "",
    val contentStartOffset: Int = 0,
    val contentLength: Long = 0,
    val pages: List<ReaderPage> = emptyList(),
    val currentPageIndex: Int = 0,
    val anchorOffset: Int = 0,
    /** Last position allowed to persist as reading progress. */
    val stableAnchorOffset: Int = 0,
    val isBrowsingTemporarily: Boolean = false,
    val canNavigateBack: Boolean = false,
    val progressPreview: ReaderPositionPreview? = null,
    val isProgressPreviewLoading: Boolean = false,
    val isPaginating: Boolean = false,
    val chapters: List<DetectedChapter> = emptyList(),
    /** Absolute title ranges used by the same display layout as [layoutContent]. */
    val chapterTitleRanges: List<ReaderChapterTitleRange> = emptyList(),
    val chapterRuleSet: ChapterRuleSet = ChapterRuleSet.STANDARD,
    val chaptersManuallyEdited: Boolean = false,
    val isAnalyzingChapters: Boolean = false,
    val searchResults: List<ReaderSearchResult> = emptyList(),
    val isSearching: Boolean = false,
    val searchIndexState: BookSearchIndexState? = null,
    val settings: ReaderSettings = ReaderSettings(),
    val appearanceEdit: ReaderAppearanceEdit? = null,
    val appearanceErrorMessage: String? = null,
    val themes: List<ReaderThemePreset> = ReaderThemeManager.BUILT_IN_THEMES,
    val activeThemeId: String? = null,
    val themeSchedule: ReaderThemeSchedule = ReaderThemeSchedule(),
    val manualThemeOverride: ReaderThemeManualOverride? = null,
    val bookAppearanceOverrides: ReaderSettingsOverrides = ReaderSettingsOverrides(),
    val importedFonts: List<ImportedFont> = emptyList(),
    val bookmarks: List<Bookmark> = emptyList(),
    val annotations: List<ReaderAnnotation> = emptyList(),
    val isAnnotationExporting: Boolean = false,
    val annotationExportMessage: String? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

/** Returns page text by translating its absolute offsets into this state's bounded window. */
internal fun ReaderUiState.textFor(page: ReaderPage): String {
    val localStart = page.startOffset - contentStartOffset
    val localEnd = page.endOffset - contentStartOffset
    if (localStart !in 0..content.length || localEnd !in localStart..content.length) return ""
    return content.substring(localStart, localEnd)
}

/** Returns the display-only text while preserving the absolute offsets used by [ReaderPage]. */
internal fun ReaderUiState.layoutTextFor(page: ReaderPage): String {
    val source = layoutContent.takeIf { it.length == content.length } ?: content
    val localStart = page.startOffset - contentStartOffset
    val localEnd = page.endOffset - contentStartOffset
    if (localStart !in 0..source.length || localEnd !in localStart..source.length) return ""
    return source.substring(localStart, localEnd)
}

data class ReaderSearchResult(
    val offset: Int,
    val snippet: String,
    val endOffset: Int = offset,
    val highlightStart: Int = 0,
    val highlightEnd: Int = 0,
)

interface ReaderSession {
    val uiState: StateFlow<ReaderUiState>

    fun open(bookId: String, pageUtf16Units: Int = 850)
    fun onReaderForeground(bookId: String)
    fun onReaderBackground()
    fun recordUserInteraction()
    fun goToPage(index: Int)
    fun jumpToOffset(offset: Int)
    fun jumpTemporarily(offset: Int, reason: ReaderJumpReason)
    fun navigateBack()
    fun returnToOrigin()
    fun continueFromHere()
    fun previewProgress(fraction: Float)
    fun commitProgressPreview()
    fun cancelProgressPreview()
    fun search(query: String)
    fun rebuildSearchIndex()
    fun cancelSearchIndex()
    fun updateSettings(settings: ReaderSettings)
    fun beginAppearanceEdit(scope: ReaderSettingsScope)
    fun changeAppearanceEditScope(scope: ReaderSettingsScope)
    fun previewAppearance(settings: ReaderSettings)
    fun commitAppearanceEdit()
    fun cancelAppearanceEdit()
    fun clearCurrentBookOverrides()
    fun applyTheme(themeId: String)
    fun createTheme(name: String)
    fun copyTheme(themeId: String, name: String)
    fun renameTheme(themeId: String, name: String)
    fun updateTheme(themeId: String)
    fun deleteTheme(themeId: String)
    fun updateThemeSchedule(schedule: ReaderThemeSchedule)
    fun importFont(uriString: String)
    fun removeImportedFont(fontId: String)
    fun updateLayout(spec: ReaderLayoutSpec)
    fun toggleBookmark()
    fun reanalyzeChapters(ruleSet: ChapterRuleSet)
    fun addChapterAtCurrent(title: String)
    fun renameChapter(startOffset: Int, title: String)
    fun moveChapterToCurrent(startOffset: Int)
    fun deleteChapter(startOffset: Int)
    fun addAnnotation(
        startOffset: Int,
        endOffset: Int,
        kind: AnnotationKind,
        color: HighlightColor? = null,
        note: String? = null,
    )
    fun updateAnnotation(annotationId: String, color: HighlightColor?, note: String?)
    fun deleteAnnotation(annotationId: String)
    fun exportAnnotations(destinationUri: String, request: AnnotationExportRequest)
    suspend fun flushProgress()
    fun leaveReader(afterFlush: () -> Unit)
}

@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val repository: BookRepository,
    private val textSource: TextSource,
    private val clock: EpochClock,
    private val settingsRepository: ReaderSettingsRepository,
    private val paginator: BookPaginator,
    private val bookmarkRepository: BookmarkRepository,
    @param:ReaderComputationDispatcher private val computationDispatcher: CoroutineDispatcher,
    private val chapterIndexStore: ChapterIndexStore = EmptyChapterIndexStore,
    private val bookSearchRepository: BookSearchRepository = EmptyBookSearchRepository,
    private val annotationRepository: AnnotationRepository = EmptyAnnotationRepository,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
    private val anchorRepairCoordinator: AnchorRepairCoordinator =
        AnchorRepairCoordinator(repository, annotationRepository, textSource),
    private val importedFontRepository: ImportedFontRepository = EmptyImportedFontRepository,
    private val importSourceFactory: ImportSourceFactory = EmptyImportSourceFactory,
    private val themeManager: ReaderThemeManager = ReaderThemeManager(EmptyReaderThemeRepository, clock),
    private val themeScheduleRepository: ReaderThemeScheduleRepository = EmptyReaderThemeScheduleRepository,
    private val themeScheduleController: ReaderThemeScheduleController = ReaderThemeScheduleController(
        repository = themeScheduleRepository,
        themes = themeManager.observeThemes(),
        systemDark = flowOf(false),
        timeChanges = flowOf(Unit),
        clock = clock,
        zoneIdProvider = java.time.ZoneId::systemDefault,
        delayUntil = { kotlinx.coroutines.delay(it) },
    ),
    private val positionPreviewService: ReaderPositionPreviewService =
        ReaderPositionPreviewService(textSource),
    private val readingSessionRepository: ReadingSessionRepository = EmptyReadingSessionRepository,
    private val annotationExportService: AnnotationExportService = EmptyAnnotationExportService,
) : ViewModel(), ReaderSession {
    private val mutableUiState = MutableStateFlow(ReaderUiState())
    override val uiState = mutableUiState.asStateFlow()
    private val readingSessionTracker = ReadingSessionTracker(readingSessionRepository, clock, viewModelScope)

    override fun onReaderForeground(bookId: String) = readingSessionTracker.onReaderForeground(bookId)

    override fun onReaderBackground() = readingSessionTracker.onReaderBackground()

    override fun recordUserInteraction() = readingSessionTracker.onInteraction()

    private var loadedWindow: TextWindow? = null
    private var loadedLayoutText: String = ""
    private var currentSettings = ReaderSettings()
    private var baseResolvedSettings = ReaderSettings()
    private var currentGlobalSettings = ReaderSettings()
    private var activeOpenBookId: String? = null
    private var openGeneration: Long = 0L
    private var scheduledThemeId: String? = null
    private var pendingManualThemeId: String? = null
    private var currentLayoutSpec: ReaderLayoutSpec? = null
    private var coarsePageUtf16Units: Int = DEFAULT_PAGE_UTF16_UNITS
    private var layoutSnapshotGeneration: Long = 0L
    private var chapterMutationGeneration: Long = 0L
    private val chapterStoreMutex = Mutex()
    private var openJob: Job? = null
    private var settingsJob: Job? = null
    private var settingsObserverBookId: String? = null
    private var bookOverridesJob: Job? = null
    private var paginationJob: Job? = null
    private var layoutContentJob: Job? = null
    private var windowJob: Job? = null
    private var chapterJob: Job? = null
    private var searchJob: Job? = null
    private var searchIndexJob: Job? = null
    private var bookmarkJob: Job? = null
    private var annotationJob: Job? = null
    private var progressPreviewJob: Job? = null
    private var progressPreviewRequestId: Long = 0
    private var progressPreviewTargetOffset: Int? = null
    private var currentBookmarks: List<Bookmark> = emptyList()
    private var pendingRestoreOffset: Int? = null
    private var currentAnchorOffset: Int = 0
    private var stableAnchorOffset: Int = 0
    private val navigationController = ReaderNavigationController()
    private val progressWriteCoordinator = ProgressWriteCoordinator<PendingProgress>(viewModelScope) { pending ->
        saveProgress(pending.book, pending.anchorOffset, pending.window)
        if (savedStateHandle.get<String>(PENDING_PROGRESS_BOOK_ID) == pending.book.id &&
            savedStateHandle.get<Int>(PENDING_PROGRESS_OFFSET) == pending.anchorOffset
        ) {
            savedStateHandle.remove<String>(PENDING_PROGRESS_BOOK_ID)
            savedStateHandle.remove<Int>(PENDING_PROGRESS_OFFSET)
        }
    }

    init {
        observeSettings(bookId = null, requestGeneration = openGeneration)
        viewModelScope.launch {
            themeManager.observeThemes().collectLatest { themes ->
                mutableUiState.update { state ->
                    state.copy(
                        themes = themes,
                        activeThemeId = themeManager.activeThemeId(state.settings, themes),
                    )
                }
            }
        }
        viewModelScope.launch {
            importedFontRepository.observeAll().collectLatest { fonts ->
                mutableUiState.update { it.copy(importedFonts = fonts) }
            }
        }
        viewModelScope.launch {
            themeScheduleRepository.observeSchedule().collectLatest { schedule ->
                mutableUiState.update { it.copy(themeSchedule = schedule) }
            }
        }
        viewModelScope.launch {
            themeScheduleRepository.observeManualOverride().collectLatest { manual ->
                mutableUiState.update { it.copy(manualThemeOverride = manual) }
            }
        }
    }

    override fun open(bookId: String, pageUtf16Units: Int) {
        require(pageUtf16Units > 0) { "每页字符数必须大于零" }
        if (mutableUiState.value.book?.id == bookId && loadedWindow?.text?.isNotEmpty() == true) return

        val requestGeneration = ++openGeneration
        activeOpenBookId = bookId
        openJob?.cancel()
        openJob = viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            cancelBookWork()
            coarsePageUtf16Units = pageUtf16Units
            val retainedUi = mutableUiState.value
            loadedWindow = null
            loadedLayoutText = ""
            currentAnchorOffset = 0
            stableAnchorOffset = 0
            navigationController.clear()
            pendingRestoreOffset = null
            mutableUiState.value = ReaderUiState(
                isLoading = true,
                settings = currentSettings,
                importedFonts = retainedUi.importedFonts,
                themes = retainedUi.themes,
                activeThemeId = retainedUi.activeThemeId,
                themeSchedule = retainedUi.themeSchedule,
                manualThemeOverride = retainedUi.manualThemeOverride,
            )
            try {
                val book = repository.getBook(bookId) ?: error("找不到这本书")
                if (!isCurrentOpenRequest(bookId, requestGeneration)) return@launch
                try {
                    val observedGlobalSettings = settingsRepository.observe(null).first().normalized()
                    if (!isCurrentOpenRequest(book.id, requestGeneration)) return@launch
                    currentGlobalSettings = observedGlobalSettings
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Throwable) {
                    // Settings are optional; a settings read failure must not block readable content.
                }
                if (!isCurrentOpenRequest(book.id, requestGeneration)) return@launch
                observeSettings(book.id, requestGeneration)
                try {
                    anchorRepairCoordinator.repairBook(bookId)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Throwable) {
                    // Optional repair must never block opening readable content.
                }
                if (!isCurrentOpenRequest(book.id, requestGeneration)) return@launch
                repository.markOpened(bookId, clock.nowEpochMillis())
                if (!isCurrentOpenRequest(book.id, requestGeneration)) return@launch
                observeBookmarks(bookId, requestGeneration)
                observeAnnotations(bookId, requestGeneration)
                observeSearchIndex(bookId, requestGeneration)
                val chapterSnapshot = try {
                    chapterIndexStore.getSnapshot(bookId)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Throwable) {
                    ChapterIndexSnapshot()
                }
                if (!isCurrentOpenRequest(book.id, requestGeneration)) return@launch
                val savedStateOffset = savedStateHandle.get<Int>(PENDING_PROGRESS_OFFSET)
                    ?.takeIf { savedStateHandle.get<String>(PENDING_PROGRESS_BOOK_ID) == bookId }
                    ?.toLong()
                val savedOffset = (savedStateOffset ?: repository.getProgress(bookId)?.anchor
                    ?.clampTo(book.contentLength)
                    ?.offset
                    ?: 0L).coerceIn(0, book.contentLength)
                if (!isCurrentOpenRequest(book.id, requestGeneration)) return@launch
                val window = textSource.readWindow(
                    normalizedPath = book.normalizedPath,
                    anchorOffset = savedOffset,
                    beforeUtf16Units = TEXT_WINDOW_BEFORE,
                    afterUtf16Units = TEXT_WINDOW_AFTER,
                )
                require(window.totalUtf16Length <= Int.MAX_VALUE) { "正文长度超过当前版本支持范围" }
                if (!isCurrentOpenRequest(book.id, requestGeneration)) return@launch
                val safeOffset = savedOffset.coerceIn(0, window.totalUtf16Length).toInt()
                val (preparedLayout, pages, visibleChapters) = withContext(computationDispatcher) {
                    val detectedChapters = detectChapters(window)
                    val paginationChapters = chapterSnapshot.chapters.ifEmpty { detectedChapters }
                    val preparedLayout = prepareLayout(
                        window = window,
                        chapters = paginationChapters,
                        chaptersManuallyEdited = chapterSnapshot.manuallyEdited,
                    )
                    Triple(
                        preparedLayout,
                        paginate(window, pageUtf16Units, preparedLayout.text, paginationChapters),
                        detectedChapters,
                    )
                }
                if (!isCurrentOpenRequest(book.id, requestGeneration)) return@launch
                loadedWindow = window
                loadedLayoutText = preparedLayout.text
                currentAnchorOffset = safeOffset
                stableAnchorOffset = safeOffset
                val pageIndex = pageIndexAt(pages, safeOffset)
                pendingRestoreOffset = safeOffset.takeIf { pages.getOrNull(pageIndex)?.startOffset != it }
                val supplementalUi = mutableUiState.value
                mutableUiState.value = ReaderUiState(
                    book = book,
                    content = window.text,
                    layoutContent = preparedLayout.text,
                    contentStartOffset = window.startOffset.toInt(),
                    contentLength = window.totalUtf16Length,
                    pages = pages,
                    currentPageIndex = pageIndex,
                    anchorOffset = safeOffset,
                    stableAnchorOffset = safeOffset,
                    chapters = chapterSnapshot.chapters.ifEmpty { visibleChapters },
                    chapterTitleRanges = preparedLayout.titleRanges,
                    chapterRuleSet = chapterSnapshot.ruleSet,
                    chaptersManuallyEdited = chapterSnapshot.manuallyEdited,
                    settings = currentSettings,
                    importedFonts = supplementalUi.importedFonts,
                    themes = supplementalUi.themes,
                    activeThemeId = themeManager.activeThemeId(currentSettings, supplementalUi.themes),
                    themeSchedule = supplementalUi.themeSchedule,
                    manualThemeOverride = supplementalUi.manualThemeOverride,
                    bookAppearanceOverrides = supplementalUi.bookAppearanceOverrides,
                    bookmarks = currentBookmarks,
                )
                if (!isCurrentOpenRequest(book.id, requestGeneration)) return@launch
                themeScheduleController.start(viewModelScope) { themeId ->
                    applyScheduledTheme(themeId)
                }
                if (savedStateOffset != null) submitStableProgress(book, safeOffset, window)
                if (!isCurrentOpenRequest(book.id, requestGeneration)) return@launch
                if (!chapterSnapshot.manuallyEdited) {
                    analyzeChapters(book, window.totalUtf16Length, chapterSnapshot.ruleSet)
                }
                if (!isCurrentOpenRequest(book.id, requestGeneration)) return@launch
                currentLayoutSpec?.let { requestRepagination(it, pendingRestoreOffset) }
                if (!isCurrentOpenRequest(book.id, requestGeneration)) return@launch
                bookSearchRepository.ensureIndexed(bookId)
                if (!isCurrentOpenRequest(book.id, requestGeneration)) return@launch
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (!isCurrentOpenRequest(bookId, requestGeneration)) return@launch
                mutableUiState.value = ReaderUiState(
                    settings = currentSettings,
                    importedFonts = mutableUiState.value.importedFonts,
                    themes = mutableUiState.value.themes,
                    activeThemeId = mutableUiState.value.activeThemeId,
                    themeSchedule = mutableUiState.value.themeSchedule,
                    manualThemeOverride = mutableUiState.value.manualThemeOverride,
                    errorMessage = "打开书籍失败",
                )
            }
        }
    }

    override fun importFont(uriString: String) {
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                val imported = importedFontRepository.importFont(importSourceFactory.create(uriString))
                mutableUiState.update { state ->
                    state.copy(
                        importedFonts = state.importedFonts.filterNot { it.id == imported.id } + imported,
                        appearanceErrorMessage = null,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                mutableUiState.update {
                    it.copy(appearanceErrorMessage = "字体导入失败")
                }
            }
        }
    }

    override fun removeImportedFont(fontId: String) {
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                when (val result = importedFontRepository.remove(fontId)) {
                    FontRemovalResult.Removed,
                    FontRemovalResult.NotFound,
                    -> mutableUiState.update { state ->
                        state.copy(
                            importedFonts = state.importedFonts.filterNot { it.id == fontId },
                            appearanceErrorMessage = null,
                        )
                    }
                    is FontRemovalResult.InUse -> mutableUiState.update {
                        it.copy(appearanceErrorMessage = "该字体仍被 ${result.referenceCount} 处阅读设置使用")
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                mutableUiState.update {
                    it.copy(appearanceErrorMessage = "字体删除失败")
                }
            }
        }
    }

    private fun cancelBookWork() {
        themeScheduleController.stop()
        if (settingsObserverBookId != null) {
            settingsJob?.cancel()
            settingsJob = null
            settingsObserverBookId = null
        }
        bookOverridesJob?.cancel()
        bookOverridesJob = null
        layoutSnapshotGeneration += 1
        chapterMutationGeneration += 1
        progressPreviewJob?.cancel()
        progressPreviewJob = null
        progressPreviewRequestId += 1
        progressPreviewTargetOffset = null
        paginationJob?.cancel()
        layoutContentJob?.cancel()
        windowJob?.cancel()
        chapterJob?.cancel()
        searchJob?.cancel()
        searchIndexJob?.cancel()
        bookmarkJob?.cancel()
        annotationJob?.cancel()
    }

    private fun isCurrentOpenRequest(bookId: String, generation: Long): Boolean =
        generation == openGeneration && activeOpenBookId == bookId

    private fun isCurrentOpenState(bookId: String, generation: Long): Boolean =
        isCurrentOpenRequest(bookId, generation) && mutableUiState.value.book?.id == bookId

    private fun beginLayoutSnapshotRequest(): Long {
        layoutSnapshotGeneration += 1
        paginationJob?.cancel()
        layoutContentJob?.cancel()
        windowJob?.cancel()
        return layoutSnapshotGeneration
    }

    private fun isCurrentLayoutSnapshot(
        generation: Long,
        bookId: String,
        window: TextWindow,
        chapters: List<DetectedChapter>,
        chaptersManuallyEdited: Boolean,
        spec: ReaderLayoutSpec?,
    ): Boolean {
        val state = mutableUiState.value
        return generation == layoutSnapshotGeneration &&
            state.book?.id == bookId &&
            loadedWindow === window &&
            state.chapters == chapters &&
            state.chaptersManuallyEdited == chaptersManuallyEdited &&
            currentLayoutSpec == spec
    }

    private fun beginChapterAnalysis(): Long {
        chapterJob?.cancel()
        chapterMutationGeneration += 1
        return chapterMutationGeneration
    }

    private fun isCurrentChapterAnalysis(bookId: String, generation: Long): Boolean =
        generation == chapterMutationGeneration && mutableUiState.value.book?.id == bookId

    private fun observeSettings(bookId: String?, requestGeneration: Long? = null) {
        val isCurrentBoundBook = {
            if (bookId == null) {
                requestGeneration == null || requestGeneration == openGeneration
            } else if (requestGeneration != null) {
                isCurrentOpenRequest(bookId, requestGeneration)
            } else {
                isCurrentOpenState(bookId, openGeneration)
            }
        }
        if (!isCurrentBoundBook()) return
        settingsJob?.cancel()
        settingsObserverBookId = bookId
        observeBookOverrides(bookId, requestGeneration)
        settingsJob = viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            settingsRepository.observe(bookId).collect { observed ->
                if (!isCurrentBoundBook()) return@collect
                val normalized = observed.normalized()
                if (bookId == null) currentGlobalSettings = normalized
                baseResolvedSettings = normalized
                if (mutableUiState.value.appearanceEdit != null) return@collect
                val effective = resolveScheduledSettings(normalized)
                currentSettings = effective
                mutableUiState.update { state ->
                    if (bookId == null && state.book == null || state.book?.id == bookId) {
                        state.copy(
                            settings = effective,
                            activeThemeId = themeManager.activeThemeId(effective, state.themes),
                        )
                    } else {
                        state
                    }
                }
            }
        }
    }

    private fun observeBookOverrides(bookId: String?, requestGeneration: Long? = null) {
        bookOverridesJob?.cancel()
        if (bookId == null) {
            mutableUiState.update { it.copy(bookAppearanceOverrides = ReaderSettingsOverrides()) }
            return
        }
        val isCurrentBoundBook = {
            if (requestGeneration != null) {
                isCurrentOpenRequest(bookId, requestGeneration)
            } else {
                isCurrentOpenState(bookId, openGeneration)
            }
        }
        if (!isCurrentBoundBook()) return
        bookOverridesJob = viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            settingsRepository.observeBookOverrides(bookId).collectLatest { overrides ->
                if (!isCurrentBoundBook()) return@collectLatest
                mutableUiState.update { state ->
                    if (state.book?.id == bookId) state.copy(bookAppearanceOverrides = overrides) else state
                }
            }
        }
    }

    private fun observeBookmarks(bookId: String, requestGeneration: Long) {
        if (!isCurrentOpenRequest(bookId, requestGeneration)) return
        bookmarkJob?.cancel()
        currentBookmarks = emptyList()
        bookmarkJob = viewModelScope.launch {
            bookmarkRepository.observe(bookId).collectLatest { bookmarks ->
                if (!isCurrentOpenRequest(bookId, requestGeneration)) return@collectLatest
                currentBookmarks = bookmarks
                mutableUiState.update { state ->
                    if (state.book?.id == bookId) state.copy(bookmarks = bookmarks) else state
                }
            }
        }
    }

    private fun observeAnnotations(bookId: String, requestGeneration: Long) {
        if (!isCurrentOpenRequest(bookId, requestGeneration)) return
        annotationJob?.cancel()
        annotationJob = viewModelScope.launch {
            annotationRepository.observe(bookId).collectLatest { annotations ->
                if (!isCurrentOpenRequest(bookId, requestGeneration)) return@collectLatest
                mutableUiState.update { state ->
                    if (state.book?.id == bookId) state.copy(annotations = annotations) else state
                }
            }
        }
    }

    override fun addAnnotation(
        startOffset: Int,
        endOffset: Int,
        kind: AnnotationKind,
        color: HighlightColor?,
        note: String?,
    ) {
        val state = mutableUiState.value
        val book = state.book ?: return
        val window = loadedWindow ?: return
        val safeStart = minOf(startOffset, endOffset).toLong().coerceIn(0, state.contentLength).toInt()
        val requestedEnd = maxOf(startOffset, endOffset).toLong().coerceIn(0, state.contentLength).toInt()
        val safeEnd = if (kind == AnnotationKind.BOOKMARK) safeStart else requestedEnd
        if (kind != AnnotationKind.BOOKMARK && safeStart == safeEnd) return
        val localStart = (safeStart.toLong() - window.startOffset).toInt()
        val localEnd = (safeEnd.toLong() - window.startOffset).toInt()
        if (localStart !in 0..window.text.length || localEnd !in localStart..window.text.length) return
        val fingerprint = TextFingerprint.capture(window.text, localStart, localEnd)
        val timestamp = clock.nowEpochMillis()
        val annotation = ReaderAnnotation(
            id = UUID.randomUUID().toString(),
            bookId = book.id,
            kind = kind,
            range = TextRangeAnchor(
                startOffset = safeStart.toLong(),
                endOffset = safeEnd.toLong(),
                prefix = fingerprint.prefix,
                suffix = fingerprint.suffix,
                selectedSha256 = fingerprint.selectedSha256,
            ),
            color = color.takeIf { kind == AnnotationKind.HIGHLIGHT },
            note = note?.trim()?.takeIf(String::isNotEmpty),
            createdAtEpochMillis = timestamp,
            updatedAtEpochMillis = timestamp,
        )
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            annotationRepository.upsert(annotation)
        }
    }

    override fun updateAnnotation(annotationId: String, color: HighlightColor?, note: String?) {
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            val existing = annotationRepository.get(annotationId) ?: return@launch
            annotationRepository.upsert(
                existing.copy(
                    color = color.takeIf { existing.kind == AnnotationKind.HIGHLIGHT },
                    note = note?.trim()?.takeIf(String::isNotEmpty),
                    updatedAtEpochMillis = clock.nowEpochMillis(),
                ),
            )
        }
    }

    override fun deleteAnnotation(annotationId: String) {
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            annotationRepository.delete(annotationId)
        }
    }

    override fun exportAnnotations(destinationUri: String, request: AnnotationExportRequest) {
        if (mutableUiState.value.isAnnotationExporting) return
        viewModelScope.launch {
            mutableUiState.update {
                it.copy(isAnnotationExporting = true, annotationExportMessage = "正在导出批注…")
            }
            val result = annotationExportService.export(destinationUri, request)
            mutableUiState.update { state ->
                when (result) {
                    is AnnotationExportResult.Success -> state.copy(
                        isAnnotationExporting = false,
                        annotationExportMessage = "已导出 ${result.annotationCount} 条批注",
                    )
                    is AnnotationExportResult.Failure -> state.copy(
                        isAnnotationExporting = false,
                        annotationExportMessage = result.safeMessage,
                    )
                }
            }
        }
    }

    private fun observeSearchIndex(bookId: String, requestGeneration: Long) {
        if (!isCurrentOpenRequest(bookId, requestGeneration)) return
        searchIndexJob?.cancel()
        searchIndexJob = viewModelScope.launch {
            bookSearchRepository.observeIndexState(bookId).collectLatest { indexState ->
                if (!isCurrentOpenRequest(bookId, requestGeneration)) return@collectLatest
                mutableUiState.update { state ->
                    if (state.book?.id == bookId) state.copy(searchIndexState = indexState) else state
                }
            }
        }
    }

    private fun analyzeChapters(
        book: Book,
        totalLength: Long,
        ruleSet: ChapterRuleSet,
        explicitRuleChange: Boolean = false,
    ) {
        val analysisGeneration = beginChapterAnalysis()
        mutableUiState.update { it.copy(isAnalyzingChapters = true) }
        chapterJob = viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                val chaptersByOffset = sortedMapOf<Int, DetectedChapter>()
                var cursor = 0L
                while (cursor < totalLength) {
                    val window = textSource.readWindow(
                        normalizedPath = book.normalizedPath,
                        anchorOffset = cursor,
                        beforeUtf16Units = if (cursor == 0L) 0 else CHAPTER_SCAN_OVERLAP,
                        afterUtf16Units = CHAPTER_SCAN_CHUNK,
                    )
                    val detected = withContext(computationDispatcher) {
                        ChapterDetector.detect(window.text, ruleSet)
                    }
                    detected.forEach { chapter ->
                        val isFallback = detected.size == 1 &&
                            chapter.title == "正文" && chapter.startOffset == 0
                        if (!isFallback) {
                            val absoluteOffset = window.startOffset + chapter.startOffset
                            if (absoluteOffset <= Int.MAX_VALUE) {
                                chaptersByOffset.putIfAbsent(
                                    absoluteOffset.toInt(),
                                    DetectedChapter(chapter.title, absoluteOffset.toInt()),
                                )
                            }
                        }
                    }
                    val next = window.endOffset
                    if (next <= cursor) break
                    cursor = next
                }
                val chapters = chaptersByOffset.values.toList().ifEmpty {
                    listOf(DetectedChapter(title = "正文", startOffset = 0))
                }
                try {
                    if (!isCurrentChapterAnalysis(book.id, analysisGeneration)) return@launch
                    val accepted = chapterStoreMutex.withLock {
                        if (!isCurrentChapterAnalysis(book.id, analysisGeneration)) return@withLock false
                        if (explicitRuleChange) {
                            chapterIndexStore.replaceForRuleChange(book.id, chapters, ruleSet)
                            true
                        } else {
                            chapterIndexStore.replaceAutomatically(book.id, chapters, ruleSet)
                        }
                    }
                    if (accepted && isCurrentChapterAnalysis(book.id, analysisGeneration)) {
                        mutableUiState.update { state ->
                            if (state.book?.id == book.id &&
                                chapterMutationGeneration == analysisGeneration
                            ) {
                                state.copy(
                                    chapters = chapters,
                                    chapterRuleSet = ruleSet,
                                    chaptersManuallyEdited = false,
                                )
                            } else state
                        }
                        if (isCurrentChapterAnalysis(book.id, analysisGeneration)) {
                            currentLayoutSpec?.let { requestRepagination(it, currentAnchorOffset) }
                                ?: requestLayoutContentRebuild(book.id)
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Throwable) {
                    // The derived index can be rebuilt from the normalized text.
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                // The visible window remains readable even if the derived chapter index is damaged.
            } finally {
                mutableUiState.update { state ->
                    if (state.book?.id == book.id &&
                        chapterMutationGeneration == analysisGeneration
                    ) {
                        state.copy(isAnalyzingChapters = false)
                    } else {
                        state
                    }
                }
            }
        }
    }

    override fun reanalyzeChapters(ruleSet: ChapterRuleSet) {
        val state = mutableUiState.value
        val book = state.book ?: return
        if (state.isAnalyzingChapters) return
        analyzeChapters(book, state.contentLength, ruleSet, explicitRuleChange = true)
    }

    override fun addChapterAtCurrent(title: String) {
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty()) {
            mutableUiState.update { it.copy(errorMessage = "章节名称不能为空") }
            return
        }
        persistManualChapterEdit { chapters ->
            addChapter(chapters, DetectedChapter(cleanTitle, currentChapterEditOffset()))
        }
    }

    override fun renameChapter(startOffset: Int, title: String) {
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty()) {
            mutableUiState.update { it.copy(errorMessage = "章节名称不能为空") }
            return
        }
        persistManualChapterEdit { chapters ->
            chapters.map { if (it.startOffset == startOffset) it.copy(title = cleanTitle) else it }
        }
    }

    override fun moveChapterToCurrent(startOffset: Int) {
        val target = currentChapterEditOffset()
        val state = mutableUiState.value
        if (target != startOffset && state.chapters.any { it.startOffset == target }) {
            mutableUiState.update { it.copy(errorMessage = "当前位置已经有章节") }
            return
        }
        persistManualChapterEdit { chapters -> moveChapter(chapters, startOffset, target) }
    }

    override fun deleteChapter(startOffset: Int) {
        persistManualChapterEdit { chapters -> chapters.filterNot { it.startOffset == startOffset } }
    }

    private fun currentChapterEditOffset(): Int {
        val state = mutableUiState.value
        return state.pages.getOrNull(state.currentPageIndex)?.startOffset ?: state.anchorOffset
    }

    private fun persistManualChapterEdit(
        edit: (List<DetectedChapter>) -> List<DetectedChapter>,
    ) {
        val state = mutableUiState.value
        val book = state.book ?: return
        val previousWindow = loadedWindow
        val previousLayoutSpec = currentLayoutSpec
        val previousPendingRestoreOffset = pendingRestoreOffset
        val previous = state.chapters
        val edited = edit(previous).sortedBy(DetectedChapter::startOffset)
        if (edited == previous) return
        chapterJob?.cancel()
        val editGeneration = ++chapterMutationGeneration
        mutableUiState.update {
            it.copy(
                chapters = edited,
                chaptersManuallyEdited = true,
                isAnalyzingChapters = false,
                errorMessage = null,
            )
        }
        currentLayoutSpec?.let { requestRepagination(it, currentAnchorOffset) }
            ?: requestLayoutContentRebuild(book.id)
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                if (editGeneration != chapterMutationGeneration) return@launch
                chapterStoreMutex.withLock {
                    if (editGeneration == chapterMutationGeneration) {
                        chapterIndexStore.replaceManually(book.id, edited)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                if (editGeneration != chapterMutationGeneration) return@launch
                val shouldRollback = mutableUiState.value.let { current ->
                    current.book?.id == book.id && current.chapters == edited
                }
                if (shouldRollback) {
                    ++chapterMutationGeneration
                    val rollbackTargetAnchor = currentAnchorOffset
                    val previousCoverageStart = state.pages.firstOrNull()?.startOffset
                    val previousCoverageEnd = state.pages.lastOrNull()?.endOffset
                    val anchorInsidePreviousSnapshot = previousCoverageStart != null &&
                        previousCoverageEnd != null &&
                        rollbackTargetAnchor in previousCoverageStart..previousCoverageEnd
                    val hasActiveWindowRequest = windowJob?.isActive == true
                    val canRestoreLayoutSnapshot =
                        loadedWindow === previousWindow &&
                            currentLayoutSpec == previousLayoutSpec &&
                            !hasActiveWindowRequest &&
                            anchorInsidePreviousSnapshot &&
                            pendingRestoreOffset == previousPendingRestoreOffset
                    if (canRestoreLayoutSnapshot) {
                        beginLayoutSnapshotRequest()
                        loadedLayoutText = state.layoutContent
                        val rollbackAnchor = rollbackTargetAnchor.coerceIn(
                            checkNotNull(previousCoverageStart),
                            checkNotNull(previousCoverageEnd),
                        )
                        val rollbackPageIndex = pageIndexAt(state.pages, rollbackAnchor)
                        pendingRestoreOffset = previousPendingRestoreOffset
                            .takeIf { currentAnchorOffset == state.anchorOffset }
                        mutableUiState.update { current ->
                            current.copy(
                                chapters = previous,
                                chaptersManuallyEdited = state.chaptersManuallyEdited,
                                layoutContent = state.layoutContent,
                                chapterTitleRanges = state.chapterTitleRanges,
                                pages = state.pages,
                                currentPageIndex = rollbackPageIndex,
                                anchorOffset = rollbackAnchor,
                                stableAnchorOffset = stableAnchorOffset,
                                isPaginating = false,
                                errorMessage = "保存章节失败",
                            )
                        }
                        if (state.isPaginating) {
                            previousLayoutSpec?.let { requestRepagination(it, rollbackAnchor) }
                        }
                    } else {
                        mutableUiState.update { current ->
                            current.copy(
                                chapters = previous,
                                chaptersManuallyEdited = state.chaptersManuallyEdited,
                                errorMessage = "保存章节失败",
                            )
                        }
                        if (hasActiveWindowRequest || !anchorInsidePreviousSnapshot) {
                            beginLayoutSnapshotRequest()
                            windowJob = null
                            requestTextWindow(rollbackTargetAnchor, saveAfterLoad = false)
                        } else {
                            currentLayoutSpec?.let { requestRepagination(it, rollbackTargetAnchor) }
                                ?: requestLayoutContentRebuild(book.id)
                        }
                    }
                }
            }
        }
    }

    override fun toggleBookmark() {
        val state = mutableUiState.value
        val book = state.book ?: return
        val page = state.pages.getOrNull(state.currentPageIndex) ?: return
        val existing = currentBookmarks.firstOrNull { it.offset == page.startOffset.toLong() }
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            if (existing != null) {
                bookmarkRepository.delete(existing.id)
                currentBookmarks = currentBookmarks.filterNot { it.id == existing.id }
            } else {
                val bookmark = Bookmark(
                    id = UUID.randomUUID().toString(),
                    bookId = book.id,
                    offset = page.startOffset.toLong(),
                    note = null,
                    createdAtEpochMillis = clock.nowEpochMillis(),
                )
                bookmarkRepository.add(bookmark)
                currentBookmarks = currentBookmarks.filterNot { it.offset == bookmark.offset } + bookmark
            }
            mutableUiState.update { it.copy(bookmarks = currentBookmarks.sortedBy(Bookmark::offset)) }
        }
    }

    override fun updateLayout(spec: ReaderLayoutSpec) {
        if (currentLayoutSpec == spec) {
            if (loadedWindow != null && pendingRestoreOffset != null) {
                requestRepagination(spec, pendingRestoreOffset)
            }
            return
        }
        currentLayoutSpec = spec
        if (loadedWindow != null) requestRepagination(spec, pendingRestoreOffset)
    }

    private fun requestRepagination(spec: ReaderLayoutSpec, requestedAnchorOffset: Int? = null) {
        val window = loadedWindow ?: return
        val chapterState = mutableUiState.value
        val chapters = chapterState.chapters
        val chaptersManuallyEdited = chapterState.chaptersManuallyEdited
        val bookId = chapterState.book?.id ?: return
        val windowStart = window.startOffset.toInt()
        val windowEnd = window.endOffset.toInt()
        val anchorOffset = requestedAnchorOffset?.coerceIn(windowStart, windowEnd)
            ?: currentAnchorOffset.coerceIn(windowStart, windowEnd)
        val generation = beginLayoutSnapshotRequest()
        mutableUiState.update { it.copy(isPaginating = true) }
        paginationJob = viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                val preparedLayout = withContext(computationDispatcher) {
                    prepareLayout(window, chapters, chaptersManuallyEdited)
                }
                val pages = paginatePrecisely(
                    window = window,
                    spec = spec,
                    layoutText = preparedLayout.text,
                    chapters = chapters,
                    titleRanges = preparedLayout.titleRanges,
                )
                if (!isCurrentLayoutSnapshot(
                        generation = generation,
                        bookId = bookId,
                        window = window,
                        chapters = chapters,
                        chaptersManuallyEdited = chaptersManuallyEdited,
                        spec = spec,
                    )
                ) return@launch
                loadedLayoutText = preparedLayout.text
                val pageIndex = pageIndexAt(pages, anchorOffset)
                pendingRestoreOffset = null
                mutableUiState.update {
                    it.copy(
                        layoutContent = preparedLayout.text,
                        chapterTitleRanges = preparedLayout.titleRanges,
                        pages = pages,
                        currentPageIndex = pageIndex,
                        anchorOffset = anchorOffset,
                        isPaginating = false,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (isCurrentLayoutSnapshot(
                        generation = generation,
                        bookId = bookId,
                        window = window,
                        chapters = chapters,
                        chaptersManuallyEdited = chaptersManuallyEdited,
                        spec = spec,
                    )
                ) {
                    mutableUiState.update {
                        it.copy(isPaginating = false, errorMessage = "重新排版失败")
                    }
                }
            }
        }
    }

    private fun requestLayoutContentRebuild(bookId: String) {
        val window = loadedWindow ?: return
        val state = mutableUiState.value
        if (state.book?.id != bookId) return
        val chapters = state.chapters
        val chaptersManuallyEdited = state.chaptersManuallyEdited
        val windowStart = window.startOffset.toInt()
        val windowEnd = window.endOffset.toInt()
        val anchorOffset = currentAnchorOffset.coerceIn(windowStart, windowEnd)
        val generation = beginLayoutSnapshotRequest()
        layoutContentJob = viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            val (preparedLayout, pages) = withContext(computationDispatcher) {
                val preparedLayout = prepareLayout(window, chapters, chaptersManuallyEdited)
                preparedLayout to paginate(
                    window = window,
                    pageUtf16Units = coarsePageUtf16Units,
                    layoutText = preparedLayout.text,
                    chapters = chapters,
                )
            }
            if (!isCurrentLayoutSnapshot(
                    generation = generation,
                    bookId = bookId,
                    window = window,
                    chapters = chapters,
                    chaptersManuallyEdited = chaptersManuallyEdited,
                    spec = null,
                )
            ) return@launch
            loadedLayoutText = preparedLayout.text
            val pageIndex = pageIndexAt(pages, anchorOffset)
            pendingRestoreOffset = anchorOffset.takeIf { pages.getOrNull(pageIndex)?.startOffset != it }
            mutableUiState.update {
                it.copy(
                    layoutContent = preparedLayout.text,
                    chapterTitleRanges = preparedLayout.titleRanges,
                    pages = pages,
                    currentPageIndex = pageIndex,
                    anchorOffset = anchorOffset,
                )
            }
        }
    }

    override fun search(query: String) {
        val normalizedQuery = query.trim()
        searchJob?.cancel()
        if (normalizedQuery.codePointCount(0, normalizedQuery.length) < 2) {
            mutableUiState.update { it.copy(searchResults = emptyList(), isSearching = false) }
            if (normalizedQuery.isNotEmpty()) {
                mutableUiState.update { it.copy(errorMessage = "请至少输入两个字符") }
            }
            return
        }
        val state = mutableUiState.value
        val book = state.book ?: return
        mutableUiState.update { it.copy(isSearching = true, errorMessage = null) }
        searchJob = viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                val results = bookSearchRepository.search(book.id, normalizedQuery, MAX_SEARCH_RESULTS)
                    .mapNotNull(BookSearchResult::toReaderResult)
                mutableUiState.update { current ->
                    if (current.book?.id == book.id) {
                        current.copy(searchResults = results, isSearching = false)
                    } else {
                        current
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                mutableUiState.update {
                    it.copy(isSearching = false, errorMessage = "搜索失败")
                }
            }
        }
    }

    override fun rebuildSearchIndex() {
        val bookId = mutableUiState.value.book?.id ?: return
        searchJob?.cancel()
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            bookSearchRepository.rebuild(bookId)
        }
    }

    override fun cancelSearchIndex() {
        val bookId = mutableUiState.value.book?.id ?: return
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            bookSearchRepository.cancelIndex(bookId)
        }
    }

    override fun jumpToOffset(offset: Int) {
        jumpTemporarily(offset, ReaderJumpReason.PROGRESS)
    }

    override fun jumpTemporarily(offset: Int, reason: ReaderJumpReason) {
        val state = mutableUiState.value
        if (state.book == null) return
        val safeOffset = offset.toLong().coerceIn(0, state.contentLength).toInt()
        navigationController.jump(currentAnchorOffset, safeOffset, reason)
        publishNavigationState()
        navigateToOffset(safeOffset, commitProgress = false)
    }

    override fun navigateBack() {
        val target = navigationController.back() ?: return
        publishNavigationState()
        navigateToOffset(target, commitProgress = false)
    }

    override fun returnToOrigin() {
        val target = navigationController.returnToOrigin() ?: return
        publishNavigationState()
        navigateToOffset(target, commitProgress = false)
    }

    override fun continueFromHere() {
        if (!navigationController.isBrowsingTemporarily) return
        navigationController.continueHere()
        stableAnchorOffset = currentAnchorOffset
        publishNavigationState()
        val book = mutableUiState.value.book ?: return
        val window = loadedWindow ?: return
        submitStableProgress(book, stableAnchorOffset, window)
    }

    override fun previewProgress(fraction: Float) {
        val state = mutableUiState.value
        val book = state.book ?: return
        progressPreviewJob?.cancel()
        val requestId = ++progressPreviewRequestId
        val safeFraction = fraction.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 0f
        val safeLength = state.contentLength.coerceIn(0, Int.MAX_VALUE.toLong())
        progressPreviewTargetOffset = (safeLength * safeFraction.toDouble()).roundToLong()
            .coerceIn(0, safeLength)
            .toInt()
        mutableUiState.update {
            it.copy(progressPreview = null, isProgressPreviewLoading = true, errorMessage = null)
        }
        progressPreviewJob = viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                if (requestId != progressPreviewRequestId || mutableUiState.value.book?.id != book.id) {
                    return@launch
                }
                val preview = positionPreviewService.preview(
                    normalizedPath = book.normalizedPath,
                    totalUtf16Length = state.contentLength,
                    chapters = state.chapters,
                    fraction = fraction,
                )
                if (requestId != progressPreviewRequestId || mutableUiState.value.book?.id != book.id) {
                    return@launch
                }
                mutableUiState.update {
                    it.copy(progressPreview = preview, isProgressPreviewLoading = false)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                if (requestId == progressPreviewRequestId) {
                    progressPreviewTargetOffset = null
                    mutableUiState.update {
                        it.copy(
                            progressPreview = null,
                            isProgressPreviewLoading = false,
                            errorMessage = "位置预览失败",
                        )
                    }
                }
            }
        }
    }

    override fun commitProgressPreview() {
        val state = mutableUiState.value
        val targetOffset = state.progressPreview?.targetOffset
            ?: progressPreviewTargetOffset?.takeIf { state.isProgressPreviewLoading }
            ?: return
        clearProgressPreview()
        jumpTemporarily(targetOffset, ReaderJumpReason.PROGRESS)
    }

    override fun cancelProgressPreview() {
        clearProgressPreview()
    }

    private fun clearProgressPreview() {
        progressPreviewJob?.cancel()
        progressPreviewJob = null
        progressPreviewRequestId += 1
        progressPreviewTargetOffset = null
        mutableUiState.update {
            it.copy(progressPreview = null, isProgressPreviewLoading = false)
        }
    }

    private fun publishNavigationState() {
        mutableUiState.update {
            it.copy(
                stableAnchorOffset = stableAnchorOffset,
                isBrowsingTemporarily = navigationController.isBrowsingTemporarily,
                canNavigateBack = navigationController.canGoBack,
            )
        }
    }

    private fun navigateToOffset(offset: Int, commitProgress: Boolean) {
        val state = mutableUiState.value
        if (state.book == null) return
        val safeOffset = offset.toLong().coerceIn(0, state.contentLength).toInt()
        pendingRestoreOffset = null
        currentAnchorOffset = safeOffset
        val window = loadedWindow
        val outsideLoadedWindow = window == null ||
            safeOffset.toLong() < window.startOffset ||
            (safeOffset.toLong() >= window.endOffset && safeOffset.toLong() < state.contentLength)
        if (outsideLoadedWindow) {
            requestTextWindow(safeOffset, saveAfterLoad = commitProgress)
            return
        }
        val coveredStart = state.pages.firstOrNull()?.startOffset ?: Int.MAX_VALUE
        val coveredEnd = state.pages.lastOrNull()?.endOffset ?: Int.MIN_VALUE
        if (safeOffset !in coveredStart..coveredEnd && currentLayoutSpec != null) {
            requestRepagination(currentLayoutSpec!!, safeOffset)
            return
        }
        val pageIndex = pageIndexAt(state.pages, safeOffset)
        moveToPage(pageIndex, safeOffset, commitProgress)
    }

    override fun updateSettings(settings: ReaderSettings) {
        val normalized = settings.normalized()
        val updatedGlobal = currentGlobalSettings.withGlobalBehaviorFrom(normalized)
        currentGlobalSettings = updatedGlobal
        currentSettings = currentSettings.withGlobalBehaviorFrom(updatedGlobal)
        mutableUiState.update { state ->
            state.copy(settings = state.settings.withGlobalBehaviorFrom(updatedGlobal))
        }
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            settingsRepository.updateGlobal(updatedGlobal)
        }
    }

    override fun beginAppearanceEdit(scope: ReaderSettingsScope) {
        pendingManualThemeId = null
        val state = mutableUiState.value
        val original = when (scope) {
            ReaderSettingsScope.GLOBAL -> currentGlobalSettings
            ReaderSettingsScope.CURRENT_BOOK -> currentSettings
        }.normalized()
        val edit = ReaderAppearanceEdit(
            scope = scope,
            original = original,
            preview = original,
            originalStableAnchorOffset = stableAnchorOffset,
        )
        mutableUiState.update {
            it.copy(
                settings = original,
                appearanceEdit = edit,
                appearanceErrorMessage = null,
                activeThemeId = themeManager.activeThemeId(original, it.themes),
                errorMessage = null,
            )
        }
        reflowAppearance(previous = state.settings, next = original, anchorOffset = edit.originalStableAnchorOffset)
    }

    override fun changeAppearanceEditScope(scope: ReaderSettingsScope) {
        val state = mutableUiState.value
        val edit = state.appearanceEdit ?: return
        if (edit.scope == scope) return
        pendingManualThemeId = null
        val targetOriginal = when (scope) {
            ReaderSettingsScope.GLOBAL -> currentGlobalSettings
            ReaderSettingsScope.CURRENT_BOOK -> currentSettings
        }.normalized()
        val migrated = edit.changeScope(scope, targetOriginal)
        mutableUiState.update {
            it.copy(
                settings = migrated.preview,
                appearanceEdit = migrated,
                appearanceErrorMessage = null,
                activeThemeId = themeManager.activeThemeId(migrated.preview, it.themes),
            )
        }
        if (state.settings.normalized() != migrated.preview.normalized()) {
            reflowAppearance(
                previous = state.settings,
                next = migrated.preview,
                anchorOffset = migrated.originalStableAnchorOffset,
            )
        }
    }

    override fun previewAppearance(settings: ReaderSettings) {
        val state = mutableUiState.value
        val edit = state.appearanceEdit ?: return
        pendingManualThemeId = null
        val preview = settings.normalized()
        mutableUiState.update {
            it.copy(
                settings = preview,
                appearanceEdit = edit.copy(preview = preview),
                appearanceErrorMessage = null,
                activeThemeId = themeManager.activeThemeId(preview, it.themes),
            )
        }
        reflowAppearance(previous = state.settings, next = preview, anchorOffset = edit.originalStableAnchorOffset)
    }

    override fun commitAppearanceEdit() {
        val edit = mutableUiState.value.appearanceEdit ?: return
        val bookId = mutableUiState.value.book?.id
        val manualThemeId = pendingManualThemeId.takeIf { edit.scope == ReaderSettingsScope.GLOBAL }
        pendingManualThemeId = null
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                var committedGlobalSettings: ReaderSettings? = null
                val committed = when (edit.scope) {
                    ReaderSettingsScope.GLOBAL -> {
                        val normalized = edit.preview.normalized()
                        settingsRepository.updateGlobal(normalized)
                        if (mutableUiState.value.appearanceEdit !== edit) return@launch
                        val resolved = bookId?.let {
                            settingsRepository.observe(it).first().normalized()
                        } ?: normalized
                        if (mutableUiState.value.appearanceEdit !== edit) return@launch
                        committedGlobalSettings = normalized
                        resolved
                    }
                    ReaderSettingsScope.CURRENT_BOOK -> {
                        val id = bookId ?: error("尚未打开书籍，无法保存本书设置")
                        val global = settingsRepository.observe(null).first().normalized()
                        if (mutableUiState.value.appearanceEdit !== edit) return@launch
                        val overrides = edit.preview.appearanceOverridesComparedWith(global)
                        settingsRepository.updateBookOverrides(id, overrides)
                        if (mutableUiState.value.appearanceEdit !== edit) return@launch
                        committedGlobalSettings = global
                        global.resolve(overrides)
                    }
                }
                manualThemeId?.let { themeId ->
                    themeScheduleController.recordManualTheme(themeId)
                    if (mutableUiState.value.appearanceEdit !== edit) return@launch
                    scheduledThemeId = themeId
                }
                if (mutableUiState.value.appearanceEdit !== edit) return@launch
                currentGlobalSettings = committedGlobalSettings
                currentSettings = committed
                baseResolvedSettings = committed
                currentAnchorOffset = edit.originalStableAnchorOffset
                mutableUiState.update {
                    it.copy(
                        settings = committed,
                        appearanceEdit = null,
                        appearanceErrorMessage = null,
                        activeThemeId = themeManager.activeThemeId(committed, it.themes),
                        anchorOffset = currentAnchorOffset,
                        stableAnchorOffset = stableAnchorOffset,
                        errorMessage = null,
                    )
                }
                navigateToOffset(edit.originalStableAnchorOffset, commitProgress = false)
                reflowAppearance(edit.preview, committed, edit.originalStableAnchorOffset)
                observeSettings(bookId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                if (mutableUiState.value.appearanceEdit === edit) {
                    if (manualThemeId != null) pendingManualThemeId = manualThemeId
                    mutableUiState.update {
                        it.copy(appearanceErrorMessage = "保存阅读外观失败")
                    }
                }
            }
        }
    }

    override fun cancelAppearanceEdit() {
        val edit = mutableUiState.value.appearanceEdit ?: return
        val previous = mutableUiState.value.settings
        pendingManualThemeId = null
        currentAnchorOffset = edit.originalStableAnchorOffset
        mutableUiState.update {
            it.copy(
                settings = edit.original,
                appearanceEdit = null,
                appearanceErrorMessage = null,
                anchorOffset = currentAnchorOffset,
                stableAnchorOffset = stableAnchorOffset,
                errorMessage = null,
                activeThemeId = themeManager.activeThemeId(edit.original, it.themes),
            )
        }
        navigateToOffset(edit.originalStableAnchorOffset, commitProgress = false)
        reflowAppearance(previous, edit.original, edit.originalStableAnchorOffset)
        observeSettings(mutableUiState.value.book?.id)
    }

    override fun clearCurrentBookOverrides() {
        val bookId = mutableUiState.value.book?.id ?: return
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                settingsRepository.clearBookOverrides(bookId)
                mutableUiState.update {
                    it.copy(bookAppearanceOverrides = ReaderSettingsOverrides())
                }
                observeSettings(bookId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                mutableUiState.update {
                    it.copy(appearanceErrorMessage = "清除本书设置失败")
                }
            }
        }
    }

    override fun applyTheme(themeId: String) {
        val edit = mutableUiState.value.appearanceEdit ?: return
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            runThemeOperation {
                val theme = themeManager.find(themeId)
                if (mutableUiState.value.appearanceEdit !== edit) return@runThemeOperation
                previewAppearance(edit.preview.applyAppearanceFrom(theme.settings))
                pendingManualThemeId = themeId.takeIf { edit.scope == ReaderSettingsScope.GLOBAL }
            }
        }
    }

    override fun createTheme(name: String) {
        val settings = mutableUiState.value.appearanceEdit?.preview ?: mutableUiState.value.settings
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            runThemeOperation { themeManager.create(name, settings) }
        }
    }

    override fun copyTheme(themeId: String, name: String) {
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            runThemeOperation { themeManager.copy(themeId, name) }
        }
    }

    override fun renameTheme(themeId: String, name: String) {
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            runThemeOperation { themeManager.rename(themeId, name) }
        }
    }

    override fun updateTheme(themeId: String) {
        val settings = mutableUiState.value.appearanceEdit?.preview ?: mutableUiState.value.settings
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            runThemeOperation { themeManager.update(themeId, settings) }
        }
    }

    override fun deleteTheme(themeId: String) {
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            runThemeOperation { themeManager.delete(themeId) }
        }
    }

    override fun updateThemeSchedule(schedule: ReaderThemeSchedule) {
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                themeScheduleController.updateSchedule(schedule)
                mutableUiState.update { it.copy(appearanceErrorMessage = null) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                mutableUiState.update {
                    it.copy(appearanceErrorMessage = "保存自动主题设置失败")
                }
            }
        }
    }

    private suspend fun resolveScheduledSettings(base: ReaderSettings): ReaderSettings {
        val themeId = scheduledThemeId ?: return base
        return try {
            base.applyAppearanceFrom(themeManager.find(themeId).settings)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            base
        }
    }

    private suspend fun applyScheduledTheme(themeId: String?) {
        val previous = currentSettings
        scheduledThemeId = themeId
        if (mutableUiState.value.appearanceEdit != null) return
        val effective = resolveScheduledSettings(baseResolvedSettings)
        currentSettings = effective
        mutableUiState.update { state ->
            state.copy(
                settings = effective,
                activeThemeId = themeManager.activeThemeId(effective, state.themes),
            )
        }
        reflowAppearance(previous, effective, stableAnchorOffset)
    }

    private suspend fun runThemeOperation(operation: suspend () -> Unit) {
        try {
            operation()
            mutableUiState.update { it.copy(appearanceErrorMessage = null) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            mutableUiState.update {
                it.copy(appearanceErrorMessage = "主题操作失败")
            }
        }
    }

    private fun reflowAppearance(
        previous: ReaderSettings,
        next: ReaderSettings,
        anchorOffset: Int,
    ) {
        val spec = currentLayoutSpec ?: return
        val adjusted = spec.withAppearance(previous, next)
        currentLayoutSpec = adjusted
        requestRepagination(adjusted, anchorOffset)
    }

    override fun goToPage(index: Int) {
        val page = mutableUiState.value.pages.getOrNull(index) ?: return
        moveToPage(
            index = index,
            anchorOffset = page.startOffset,
            commitProgress = !navigationController.isBrowsingTemporarily &&
                mutableUiState.value.appearanceEdit == null,
        )
    }

    private fun moveToPage(index: Int, anchorOffset: Int, commitProgress: Boolean) {
        val state = mutableUiState.value
        // A coarse placeholder page must never overwrite an exact saved anchor. Once measured
        // pagination has settled, a leftover restore marker must not permanently disable reading.
        if (readerPageMoveBlockedByRestore(
                pendingRestoreOffset = pendingRestoreOffset,
                isPaginating = state.isPaginating,
                hasMeasuredLayout = currentLayoutSpec != null,
            )
        ) return
        pendingRestoreOffset = null
        val book = state.book ?: return
        if (index !in state.pages.indices) return
        currentAnchorOffset = anchorOffset.toLong().coerceIn(0, state.contentLength).toInt()
        if (commitProgress) stableAnchorOffset = currentAnchorOffset
        mutableUiState.update {
            it.copy(
                currentPageIndex = index,
                anchorOffset = currentAnchorOffset,
                stableAnchorOffset = stableAnchorOffset,
            )
        }
        val windowSnapshot = loadedWindow
        if (windowSnapshot != null) {
            if (commitProgress) {
                submitStableProgress(book, currentAnchorOffset, windowSnapshot)
            }
            prefetchIfNeeded(index, currentAnchorOffset, state, windowSnapshot)
        }
    }

    private fun prefetchIfNeeded(
        pageIndex: Int,
        anchorOffset: Int,
        state: ReaderUiState,
        window: TextWindow,
    ) {
        if (windowJob?.isActive == true || currentLayoutSpec == null) return
        val nearStart = pageIndex <= PREFETCH_PAGE_THRESHOLD && window.startOffset > 0
        val nearEnd = state.pages.lastIndex - pageIndex <= PREFETCH_PAGE_THRESHOLD &&
            window.endOffset < window.totalUtf16Length
        if (nearStart || nearEnd) requestTextWindow(anchorOffset, saveAfterLoad = false)
    }

    private fun requestTextWindow(anchorOffset: Int, saveAfterLoad: Boolean) {
        val state = mutableUiState.value
        val book = state.book ?: return
        if (windowJob?.isActive == true) return
        val chapters = state.chapters
        val chaptersManuallyEdited = state.chaptersManuallyEdited
        val spec = currentLayoutSpec
        val generation = beginLayoutSnapshotRequest()
        val safeAnchor = anchorOffset.toLong().coerceIn(0, state.contentLength).toInt()
        pendingRestoreOffset = safeAnchor
        currentAnchorOffset = safeAnchor
        if (saveAfterLoad) stableAnchorOffset = safeAnchor
        mutableUiState.update {
            it.copy(
                isPaginating = true,
                anchorOffset = safeAnchor,
                stableAnchorOffset = stableAnchorOffset,
            )
        }
        windowJob = viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                val window = textSource.readWindow(
                    normalizedPath = book.normalizedPath,
                    anchorOffset = safeAnchor.toLong(),
                    beforeUtf16Units = TEXT_WINDOW_BEFORE,
                    afterUtf16Units = TEXT_WINDOW_AFTER,
                )
                val preparedLayout = withContext(computationDispatcher) {
                    prepareLayout(window, chapters, chaptersManuallyEdited)
                }
                val pages = if (spec == null) {
                        withContext(computationDispatcher) {
                            paginate(window, coarsePageUtf16Units, preparedLayout.text, chapters)
                        }
                } else {
                    paginatePrecisely(
                        window = window,
                        spec = spec,
                        layoutText = preparedLayout.text,
                        chapters = chapters,
                        titleRanges = preparedLayout.titleRanges,
                    )
                }
                if (!isCurrentWindowSnapshot(
                        generation = generation,
                        bookId = book.id,
                        chapters = chapters,
                        chaptersManuallyEdited = chaptersManuallyEdited,
                        spec = spec,
                    )
                ) return@launch
                loadedWindow = window
                loadedLayoutText = preparedLayout.text
                val pageIndex = pageIndexAt(pages, safeAnchor)
                pendingRestoreOffset = safeAnchor.takeIf {
                    spec == null && pages.getOrNull(pageIndex)?.startOffset != it
                }
                mutableUiState.update {
                    it.copy(
                        content = window.text,
                        layoutContent = preparedLayout.text,
                        chapterTitleRanges = preparedLayout.titleRanges,
                        contentStartOffset = window.startOffset.toInt(),
                        contentLength = window.totalUtf16Length,
                        pages = pages,
                        currentPageIndex = pageIndex,
                        anchorOffset = safeAnchor,
                        isPaginating = false,
                    )
                }
                if (saveAfterLoad && spec != null) submitStableProgress(book, safeAnchor, window)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (!isCurrentWindowSnapshot(
                        generation = generation,
                        bookId = book.id,
                        chapters = chapters,
                        chaptersManuallyEdited = chaptersManuallyEdited,
                        spec = spec,
                    )
                ) return@launch
                pendingRestoreOffset = null
                mutableUiState.update {
                    it.copy(isPaginating = false, errorMessage = "读取正文失败")
                }
            }
        }
    }

    private fun isCurrentWindowSnapshot(
        generation: Long,
        bookId: String,
        chapters: List<DetectedChapter>,
        chaptersManuallyEdited: Boolean,
        spec: ReaderLayoutSpec?,
    ): Boolean {
        val state = mutableUiState.value
        return generation == layoutSnapshotGeneration &&
            state.book?.id == bookId &&
            state.chapters == chapters &&
            state.chaptersManuallyEdited == chaptersManuallyEdited &&
            currentLayoutSpec == spec
    }

    private suspend fun saveProgress(book: Book, anchorOffset: Int, window: TextWindow) {
        val localOffset = (anchorOffset.toLong() - window.startOffset)
            .coerceIn(0, window.text.length.toLong())
            .toInt()
        val fingerprint = TextFingerprint.capture(window.text, localOffset, localOffset)
        repository.saveProgress(
            ReadingProgress(
                bookId = book.id,
                anchor = TextAnchor(
                    offset = anchorOffset.toLong(),
                    contextHash = TextFingerprint.contextSha256(fingerprint),
                    prefix = fingerprint.prefix,
                    suffix = fingerprint.suffix,
                ),
                contentLength = window.totalUtf16Length,
                updatedAtEpochMillis = clock.nowEpochMillis(),
            ),
        )
    }

    private fun submitStableProgress(book: Book, anchorOffset: Int, window: TextWindow) {
        savedStateHandle[PENDING_PROGRESS_BOOK_ID] = book.id
        savedStateHandle[PENDING_PROGRESS_OFFSET] = anchorOffset
        progressWriteCoordinator.submit(PendingProgress(book, anchorOffset, window))
    }

    override suspend fun flushProgress() {
        progressWriteCoordinator.flushNow()
    }

    override fun leaveReader(afterFlush: () -> Unit) {
        themeScheduleController.stop()
        clearProgressPreview()
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                flushProgress()
                readingSessionTracker.flushAndClose()
                afterFlush()
            } catch (error: Throwable) {
                mutableUiState.update {
                    it.copy(errorMessage = "保存阅读进度失败，请重试")
                }
            }
        }
    }

    override fun onCleared() {
        themeScheduleController.stop()
        progressPreviewJob?.cancel()
        readingSessionTracker.onReaderBackground()
    }

    private suspend fun paginatePrecisely(
        window: TextWindow,
        spec: ReaderLayoutSpec,
        layoutText: String,
        chapters: List<DetectedChapter>,
        titleRanges: List<ReaderChapterTitleRange> = emptyList(),
    ): List<ReaderPage> {
        require(layoutText.length == window.text.length) { "排版文本必须保持原始 UTF-16 长度" }
        val localTitleRanges = titleRanges.mapNotNull { range ->
            val localStart = range.startOffset.toLong() - window.startOffset
            val localEnd = range.endOffset.toLong() - window.startOffset
            if (localStart < 0L || localEnd > layoutText.length.toLong() || localEnd <= localStart) {
                null
            } else {
                ReaderChapterTitleRange(localStart.toInt(), localEnd.toInt())
            }
        }
        return shiftPages(
            window,
            paginator.paginateAtChapterStarts(
                text = layoutText,
                spec = spec,
                chapterStarts = localChapterStarts(window, chapters),
                titleRanges = localTitleRanges,
            ),
        )
    }

    private fun paginate(
        window: TextWindow,
        pageUtf16Units: Int,
        layoutText: String,
        chapters: List<DetectedChapter>,
    ): List<ReaderPage> {
        require(layoutText.length == window.text.length) { "排版文本必须保持原始 UTF-16 长度" }
        if (layoutText.isEmpty()) {
            val offset = window.startOffset.toInt()
            return listOf(ReaderPage(offset, offset))
        }
        val pages = mutableListOf<ReaderPage>()
        chapterPaginationBoundaries(layoutText, localChapterStarts(window, chapters))
            .zipWithNext()
            .forEach { (rangeStart, rangeEnd) ->
                val segment = layoutText.substring(rangeStart, rangeEnd)
                var offset = 0
                while (offset < segment.length) {
                    val page = CharacterPaginator.pageAt(segment, offset, pageUtf16Units)
                    pages += ReaderPage(
                        startOffset = window.startOffset.toInt() + rangeStart + page.startOffset,
                        endOffset = window.startOffset.toInt() + rangeStart + page.endOffset,
                    )
                    check(page.endOffset > offset) { "分页器没有向前推进" }
                    offset = page.endOffset
                }
            }
        return pages
    }

    private fun localChapterStarts(
        window: TextWindow,
        chapters: List<DetectedChapter>,
    ): List<Int> {
        val textLength = window.text.length
        return chapters.mapNotNull { chapter ->
            val localOffset = chapter.startOffset.toLong() - window.startOffset
            if (localOffset in 0..textLength.toLong()) localOffset.toInt() else null
        }
    }

    private fun prepareLayout(
        window: TextWindow,
        chapters: List<DetectedChapter>,
        chaptersManuallyEdited: Boolean,
    ): PreparedLayout {
        val localChapterStarts = localChapterStarts(window, chapters)
        return PreparedLayout(
            text = ReaderLayoutWhitespaceNormalizer.normalize(
                window.text,
                hardBoundaries = localChapterStarts,
            ),
            titleRanges = readerChapterTitleRanges(
                rawText = window.text,
                windowStartOffset = window.startOffset.toInt(),
                chapters = chapters,
                chaptersManuallyEdited = chaptersManuallyEdited,
            ),
        )
    }

    private data class PreparedLayout(
        val text: String,
        val titleRanges: List<ReaderChapterTitleRange>,
    )

    private fun shiftPages(window: TextWindow, localPages: List<ReaderPage>): List<ReaderPage> {
        val base = window.startOffset.toInt()
        return localPages.map { page ->
            ReaderPage(startOffset = base + page.startOffset, endOffset = base + page.endOffset)
        }.ifEmpty { listOf(ReaderPage(base, base)) }
    }

    private fun detectChapters(window: TextWindow): List<DetectedChapter> {
        val local = ChapterDetector.detect(window.text)
        val fallback = local.size == 1 && local.single().title == "正文" &&
            local.single().startOffset == 0
        if (fallback) return listOf(DetectedChapter("正文", 0))
        return local.map { chapter ->
            DetectedChapter(
                title = chapter.title,
                startOffset = window.startOffset.toInt() + chapter.startOffset,
            )
        }
    }

    private fun pageIndexAt(pages: List<ReaderPage>, anchorOffset: Int): Int =
        pages.indexOfLast { it.startOffset <= anchorOffset }.coerceAtLeast(0)

    private companion object {
        const val DEFAULT_PAGE_UTF16_UNITS = 850
        const val MAX_SEARCH_RESULTS = 100
        const val CHAPTER_SCAN_CHUNK = 256 * 1024
        const val CHAPTER_SCAN_OVERLAP = 512
        const val TEXT_WINDOW_BEFORE = 50_000
        const val TEXT_WINDOW_AFTER = 100_000
        const val PREFETCH_PAGE_THRESHOLD = 8
        const val PENDING_PROGRESS_BOOK_ID = "reader.progress.bookId"
        const val PENDING_PROGRESS_OFFSET = "reader.progress.offset"
    }
}

private data class PendingProgress(
    val book: Book,
    val anchorOffset: Int,
    val window: TextWindow,
)

private object EmptyAnnotationRepository : AnnotationRepository {
    override fun observe(bookId: String) = flowOf(emptyList<ReaderAnnotation>())
    override fun observe(bookId: String, kind: AnnotationKind) = flowOf(emptyList<ReaderAnnotation>())
    override suspend fun getForBook(bookId: String): List<ReaderAnnotation> = emptyList()
    override suspend fun get(annotationId: String): ReaderAnnotation? = null
    override suspend fun findOverlapping(
        bookId: String,
        startOffset: Long,
        endOffset: Long,
    ): List<ReaderAnnotation> = emptyList()
    override suspend fun upsert(annotation: ReaderAnnotation) = Unit
    override suspend fun delete(annotationId: String) = Unit
}

private object EmptyAnnotationExportService : AnnotationExportService {
    override suspend fun export(
        destinationUri: String,
        request: AnnotationExportRequest,
    ): AnnotationExportResult = AnnotationExportResult.Failure("批注导出服务不可用")
}

private object EmptyChapterIndexStore : ChapterIndexStore {
    override suspend fun getSnapshot(bookId: String): ChapterIndexSnapshot = ChapterIndexSnapshot()

    override suspend fun replaceAutomatically(
        bookId: String,
        chapters: List<DetectedChapter>,
        ruleSet: ChapterRuleSet,
    ): Boolean = true

    override suspend fun replaceManually(bookId: String, chapters: List<DetectedChapter>) = Unit

    override suspend fun replaceForRuleChange(
        bookId: String,
        chapters: List<DetectedChapter>,
        ruleSet: ChapterRuleSet,
    ) = Unit
}

private object EmptyBookSearchRepository : BookSearchRepository {
    override suspend fun ensureIndexed(bookId: String) = Unit

    override fun observeIndexState(bookId: String) = flowOf(
        BookSearchIndexState(bookId, BookSearchIndexStatus.NOT_INDEXED, 0),
    )

    override suspend fun search(bookId: String, query: String, limit: Int): List<BookSearchResult> =
        emptyList()

    override suspend fun rebuild(bookId: String) = Unit
    override suspend fun cancelIndex(bookId: String) = Unit
}

private object EmptyImportedFontRepository : ImportedFontRepository {
    override fun observeAll() = flowOf(emptyList<ImportedFont>())
    override suspend fun importFont(source: ImportSource): ImportedFont =
        error("Font import repository is unavailable")
    override suspend fun remove(fontId: String): FontRemovalResult = FontRemovalResult.NotFound
}

private object EmptyImportSourceFactory : ImportSourceFactory {
    override suspend fun create(uriString: String): ImportSource =
        error("Font import source is unavailable")
}

private object EmptyReaderThemeScheduleRepository : ReaderThemeScheduleRepository {
    override fun observeSchedule() = flowOf(ReaderThemeSchedule())
    override fun observeManualOverride() = flowOf<ReaderThemeManualOverride?>(null)
    override suspend fun updateSchedule(schedule: ReaderThemeSchedule) = Unit
    override suspend fun updateManualOverride(override: ReaderThemeManualOverride?) = Unit
}

private fun BookSearchResult.toReaderResult(): ReaderSearchResult? {
    if (offset !in 0..Int.MAX_VALUE.toLong()) return null
    return ReaderSearchResult(
        offset = offset.toInt(),
        endOffset = endOffset.coerceIn(offset, Int.MAX_VALUE.toLong()).toInt(),
        snippet = snippet,
        highlightStart = highlightStart,
        highlightEnd = highlightEnd,
    )
}

internal fun addChapter(
    chapters: List<DetectedChapter>,
    chapter: DetectedChapter,
): List<DetectedChapter> =
    (chapters.filterNot { it.startOffset == chapter.startOffset } + chapter)
        .sortedBy(DetectedChapter::startOffset)

internal fun moveChapter(
    chapters: List<DetectedChapter>,
    fromOffset: Int,
    toOffset: Int,
): List<DetectedChapter> {
    val moving = chapters.firstOrNull { it.startOffset == fromOffset } ?: return chapters
    return addChapter(
        chapters.filterNot { it.startOffset == fromOffset },
        moving.copy(startOffset = toOffset),
    )
}

internal fun readerPageMoveBlockedByRestore(
    pendingRestoreOffset: Int?,
    isPaginating: Boolean,
    hasMeasuredLayout: Boolean,
): Boolean = pendingRestoreOffset != null && (!hasMeasuredLayout || isPaginating)
