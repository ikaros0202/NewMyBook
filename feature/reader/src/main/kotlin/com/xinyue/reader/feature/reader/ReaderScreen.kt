package com.xinyue.reader.feature.reader

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.BatteryManager
import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
import android.view.ViewTreeObserver
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.xinyue.reader.core.domain.model.ReaderColorTheme
import com.xinyue.reader.core.domain.model.BookSearchIndexStatus
import com.xinyue.reader.core.domain.model.ReaderSettings
import com.xinyue.reader.core.domain.model.ReaderFontRef
import com.xinyue.reader.core.domain.model.ReaderPageAnimation
import com.xinyue.reader.core.domain.model.ReaderTapAction
import com.xinyue.reader.core.domain.model.AnnotationKind
import com.xinyue.reader.core.domain.model.AnnotationExportFormat
import com.xinyue.reader.core.domain.model.AnnotationExportRequest
import com.xinyue.reader.core.domain.model.HighlightColor
import com.xinyue.reader.core.data.ReaderPositionPreview
import com.xinyue.reader.core.text.DetectedChapter
import com.xinyue.reader.core.ui.XinYueIcons
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import java.util.Date
import kotlin.math.roundToLong

@Composable
fun ReaderRoute(
    bookId: String,
    onBack: () -> Unit,
    registerVolumePageTurnHandler: (((Int) -> Boolean)?) -> Unit,
    viewModel: ReaderViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleScope = rememberCoroutineScope()
    val fontPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.importFont(it.toString()) }
    }
    var pendingAnnotationExport by remember { mutableStateOf<AnnotationExportRequest?>(null) }
    val markdownExportPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/markdown"),
    ) { uri ->
        val request = pendingAnnotationExport
        pendingAnnotationExport = null
        if (uri != null && request != null) viewModel.exportAnnotations(uri.toString(), request)
    }
    val jsonExportPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        val request = pendingAnnotationExport
        pendingAnnotationExport = null
        if (uri != null && request != null) viewModel.exportAnnotations(uri.toString(), request)
    }
    LaunchedEffect(bookId) { viewModel.open(bookId) }
    DisposableEffect(lifecycleOwner, viewModel, bookId) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewModel.onReaderForeground(bookId)
                Lifecycle.Event.ON_STOP -> {
                    viewModel.onReaderBackground()
                    lifecycleScope.launch { viewModel.flushProgress() }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            viewModel.onReaderForeground(bookId)
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.onReaderBackground()
        }
    }
    ReaderScreenWithAppearance(
        uiState = uiState,
        onPageChanged = viewModel::goToPage,
        onJumpTemporarily = { offset, reason ->
            viewModel.jumpTemporarily(offset, reason)
            viewModel.recordUserInteraction()
        },
        onNavigateBack = {
            viewModel.navigateBack()
            viewModel.recordUserInteraction()
        },
        onReturnToOrigin = {
            viewModel.returnToOrigin()
            viewModel.recordUserInteraction()
        },
        onContinueFromHere = {
            viewModel.continueFromHere()
            viewModel.recordUserInteraction()
        },
        onPreviewProgress = viewModel::previewProgress,
        onCommitProgressPreview = {
            viewModel.commitProgressPreview()
            viewModel.recordUserInteraction()
        },
        onCancelProgressPreview = viewModel::cancelProgressPreview,
        onImportFont = {
            viewModel.recordUserInteraction()
            fontPicker.launch(
                arrayOf(
                    "font/*",
                    "application/x-font-ttf",
                    "application/x-font-opentype",
                    "application/octet-stream",
                ),
            )
        },
        onRemoveImportedFont = { fontId ->
            viewModel.removeImportedFont(fontId)
            viewModel.recordUserInteraction()
        },
        onSearch = { query ->
            viewModel.search(query)
            viewModel.recordUserInteraction()
        },
        onRebuildSearchIndex = {
            viewModel.rebuildSearchIndex()
            viewModel.recordUserInteraction()
        },
        onCancelSearchIndex = viewModel::cancelSearchIndex,
        onSettingsChanged = { settings ->
            viewModel.updateSettings(settings)
            viewModel.recordUserInteraction()
        },
        onBeginAppearanceEdit = viewModel::beginAppearanceEdit,
        onChangeAppearanceEditScope = viewModel::changeAppearanceEditScope,
        onPreviewAppearance = viewModel::previewAppearance,
        onCommitAppearanceEdit = {
            viewModel.commitAppearanceEdit()
            viewModel.recordUserInteraction()
        },
        onCancelAppearanceEdit = viewModel::cancelAppearanceEdit,
        onClearCurrentBookOverrides = {
            viewModel.clearCurrentBookOverrides()
            viewModel.recordUserInteraction()
        },
        onApplyTheme = { themeId ->
            viewModel.applyTheme(themeId)
            viewModel.recordUserInteraction()
        },
        onCreateTheme = { name ->
            viewModel.createTheme(name)
            viewModel.recordUserInteraction()
        },
        onCopyTheme = { themeId, name ->
            viewModel.copyTheme(themeId, name)
            viewModel.recordUserInteraction()
        },
        onRenameTheme = { themeId, name ->
            viewModel.renameTheme(themeId, name)
            viewModel.recordUserInteraction()
        },
        onUpdateTheme = { themeId ->
            viewModel.updateTheme(themeId)
            viewModel.recordUserInteraction()
        },
        onDeleteTheme = { themeId ->
            viewModel.deleteTheme(themeId)
            viewModel.recordUserInteraction()
        },
        onUpdateThemeSchedule = { schedule ->
            viewModel.updateThemeSchedule(schedule)
            viewModel.recordUserInteraction()
        },
        onLayoutChanged = viewModel::updateLayout,
        onToggleBookmark = {
            viewModel.toggleBookmark()
            viewModel.recordUserInteraction()
        },
        onReanalyzeChapters = { rules ->
            viewModel.reanalyzeChapters(rules)
            viewModel.recordUserInteraction()
        },
        onAddChapterAtCurrent = { title ->
            viewModel.addChapterAtCurrent(title)
            viewModel.recordUserInteraction()
        },
        onRenameChapter = { offset, title ->
            viewModel.renameChapter(offset, title)
            viewModel.recordUserInteraction()
        },
        onMoveChapterToCurrent = { offset ->
            viewModel.moveChapterToCurrent(offset)
            viewModel.recordUserInteraction()
        },
        onDeleteChapter = { offset ->
            viewModel.deleteChapter(offset)
            viewModel.recordUserInteraction()
        },
        onAddAnnotation = { start, end, kind, color, note ->
            viewModel.addAnnotation(start, end, kind, color, note)
            viewModel.recordUserInteraction()
        },
        onUpdateAnnotation = { id, color, note ->
            viewModel.updateAnnotation(id, color, note)
            viewModel.recordUserInteraction()
        },
        onDeleteAnnotation = { id ->
            viewModel.deleteAnnotation(id)
            viewModel.recordUserInteraction()
        },
        onExportAnnotations = { format, includeBookmarks ->
            pendingAnnotationExport = AnnotationExportRequest(
                format = format,
                bookIds = setOf(bookId),
                includeBookmarks = includeBookmarks,
            )
            when (format) {
                AnnotationExportFormat.MARKDOWN -> markdownExportPicker.launch("xinyue-annotations.md")
                AnnotationExportFormat.JSON -> jsonExportPicker.launch("xinyue-annotations.json")
            }
        },
        onUserInteraction = viewModel::recordUserInteraction,
        registerVolumePageTurnHandler = registerVolumePageTurnHandler,
        onBack = { viewModel.leaveReader(onBack) },
    )
}

@Composable
fun ReaderScreen(
    uiState: ReaderUiState,
    onPageChanged: (Int) -> Unit,
    onJumpTemporarily: (Int, ReaderJumpReason) -> Unit,
    onNavigateBack: () -> Unit,
    onReturnToOrigin: () -> Unit,
    onContinueFromHere: () -> Unit,
    onPreviewProgress: (Float) -> Unit,
    onCommitProgressPreview: () -> Unit,
    onCancelProgressPreview: () -> Unit,
    onImportFont: () -> Unit,
    onRemoveImportedFont: (String) -> Unit,
    onSearch: (String) -> Unit,
    onRebuildSearchIndex: () -> Unit,
    onCancelSearchIndex: () -> Unit,
    onSettingsChanged: (ReaderSettings) -> Unit,
    onLayoutChanged: (ReaderLayoutSpec) -> Unit,
    onToggleBookmark: () -> Unit,
    onReanalyzeChapters: (com.xinyue.reader.core.text.ChapterRuleSet) -> Unit,
    onAddChapterAtCurrent: (String) -> Unit,
    onRenameChapter: (Int, String) -> Unit,
    onMoveChapterToCurrent: (Int) -> Unit,
    onDeleteChapter: (Int) -> Unit,
    onAddAnnotation: (Int, Int, AnnotationKind, HighlightColor?, String?) -> Unit,
    onUpdateAnnotation: (String, HighlightColor?, String?) -> Unit,
    onDeleteAnnotation: (String) -> Unit,
    onExportAnnotations: (AnnotationExportFormat, Boolean) -> Unit = { _, _ -> },
    registerVolumePageTurnHandler: (((Int) -> Boolean)?) -> Unit,
    onBack: () -> Unit,
) {
    var localEdit by remember(uiState.book?.id) { mutableStateOf<ReaderAppearanceEdit?>(null) }
    val effectiveState = localEdit?.let { edit ->
        uiState.copy(settings = edit.preview, appearanceEdit = edit)
    } ?: uiState
    ReaderScreenWithAppearance(
        uiState = effectiveState,
        onPageChanged = onPageChanged,
        onJumpTemporarily = onJumpTemporarily,
        onNavigateBack = onNavigateBack,
        onReturnToOrigin = onReturnToOrigin,
        onContinueFromHere = onContinueFromHere,
        onPreviewProgress = onPreviewProgress,
        onCommitProgressPreview = onCommitProgressPreview,
        onCancelProgressPreview = onCancelProgressPreview,
        onImportFont = onImportFont,
        onRemoveImportedFont = onRemoveImportedFont,
        onSearch = onSearch,
        onRebuildSearchIndex = onRebuildSearchIndex,
        onCancelSearchIndex = onCancelSearchIndex,
        onSettingsChanged = onSettingsChanged,
        onBeginAppearanceEdit = { scope ->
            localEdit = ReaderAppearanceEdit(
                scope = scope,
                original = uiState.settings,
                preview = uiState.settings,
                originalStableAnchorOffset = uiState.stableAnchorOffset,
            )
        },
        onChangeAppearanceEditScope = { scope ->
            localEdit = localEdit?.changeScope(scope, uiState.settings)
        },
        onPreviewAppearance = { settings ->
            localEdit = localEdit?.copy(preview = settings.normalized())
        },
        onCommitAppearanceEdit = {
            localEdit?.preview?.let(onSettingsChanged)
            localEdit = null
        },
        onCancelAppearanceEdit = { localEdit = null },
        onClearCurrentBookOverrides = { localEdit = null },
        onApplyTheme = { themeId ->
            val theme = uiState.themes.firstOrNull { it.id == themeId }
            if (theme != null) {
                localEdit = localEdit?.let { it.copy(preview = it.preview.applyAppearanceFrom(theme.settings)) }
            }
        },
        onCreateTheme = {},
        onCopyTheme = { _, _ -> },
        onRenameTheme = { _, _ -> },
        onUpdateTheme = {},
        onDeleteTheme = {},
        onUpdateThemeSchedule = {},
        onLayoutChanged = onLayoutChanged,
        onToggleBookmark = onToggleBookmark,
        onReanalyzeChapters = onReanalyzeChapters,
        onAddChapterAtCurrent = onAddChapterAtCurrent,
        onRenameChapter = onRenameChapter,
        onMoveChapterToCurrent = onMoveChapterToCurrent,
        onDeleteChapter = onDeleteChapter,
        onAddAnnotation = onAddAnnotation,
        onUpdateAnnotation = onUpdateAnnotation,
        onDeleteAnnotation = onDeleteAnnotation,
        onExportAnnotations = onExportAnnotations,
        onUserInteraction = {},
        registerVolumePageTurnHandler = registerVolumePageTurnHandler,
        onBack = onBack,
    )
}

@Composable
private fun ReaderScreenWithAppearance(
    uiState: ReaderUiState,
    onPageChanged: (Int) -> Unit,
    onJumpTemporarily: (Int, ReaderJumpReason) -> Unit,
    onNavigateBack: () -> Unit,
    onReturnToOrigin: () -> Unit,
    onContinueFromHere: () -> Unit,
    onPreviewProgress: (Float) -> Unit,
    onCommitProgressPreview: () -> Unit,
    onCancelProgressPreview: () -> Unit,
    onImportFont: () -> Unit,
    onRemoveImportedFont: (String) -> Unit,
    onSearch: (String) -> Unit,
    onRebuildSearchIndex: () -> Unit,
    onCancelSearchIndex: () -> Unit,
    onSettingsChanged: (ReaderSettings) -> Unit,
    onBeginAppearanceEdit: (ReaderSettingsScope) -> Unit,
    onChangeAppearanceEditScope: (ReaderSettingsScope) -> Unit,
    onPreviewAppearance: (ReaderSettings) -> Unit,
    onCommitAppearanceEdit: () -> Unit,
    onCancelAppearanceEdit: () -> Unit,
    onClearCurrentBookOverrides: () -> Unit,
    onApplyTheme: (String) -> Unit,
    onCreateTheme: (String) -> Unit,
    onCopyTheme: (String, String) -> Unit,
    onRenameTheme: (String, String) -> Unit,
    onUpdateTheme: (String) -> Unit,
    onDeleteTheme: (String) -> Unit,
    onUpdateThemeSchedule: (com.xinyue.reader.core.domain.model.ReaderThemeSchedule) -> Unit,
    onLayoutChanged: (ReaderLayoutSpec) -> Unit,
    onToggleBookmark: () -> Unit,
    onReanalyzeChapters: (com.xinyue.reader.core.text.ChapterRuleSet) -> Unit,
    onAddChapterAtCurrent: (String) -> Unit,
    onRenameChapter: (Int, String) -> Unit,
    onMoveChapterToCurrent: (Int) -> Unit,
    onDeleteChapter: (Int) -> Unit,
    onAddAnnotation: (Int, Int, AnnotationKind, HighlightColor?, String?) -> Unit,
    onUpdateAnnotation: (String, HighlightColor?, String?) -> Unit,
    onDeleteAnnotation: (String) -> Unit,
    onExportAnnotations: (AnnotationExportFormat, Boolean) -> Unit,
    onUserInteraction: () -> Unit,
    registerVolumePageTurnHandler: (((Int) -> Boolean)?) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    ImmersiveSystemBars(uiState.settings)
    ReaderWindowEffects(uiState.settings)
    var controlsVisible by remember { mutableStateOf(false) }
    var activePanel by remember { mutableStateOf<ReaderPanel?>(null) }
    var appearanceCommitRequested by remember { mutableStateOf(false) }
    var touchLocked by rememberSaveable { mutableStateOf(false) }
    var selectionActive by remember { mutableStateOf(false) }
    var pendingNoteSelection by remember { mutableStateOf<ReaderSelection?>(null) }
    var navigationTab by rememberSaveable { mutableStateOf(ReaderNavigationTab.CHAPTERS) }
    var brightnessPreview by remember { mutableStateOf<Float?>(null) }
    var autoAnimationDisabled by remember(uiState.book?.id, uiState.settings.pageAnimation) {
        mutableStateOf(false)
    }
    var performanceNoticeVisible by remember(uiState.book?.id) { mutableStateOf(false) }
    val ttsController = remember(context.applicationContext) {
        ReaderTextToSpeechController(context.applicationContext)
    }
    var ttsActive by remember { mutableStateOf(false) }
    var ttsRequestedPage by remember { mutableStateOf<Int?>(null) }
    DisposableEffect(ttsController) {
        onDispose { ttsController.shutdown() }
    }
    LaunchedEffect(controlsVisible, activePanel) {
        if (controlsVisible && activePanel == null) {
            delay(CONTROLS_AUTO_HIDE_MILLIS)
            controlsVisible = false
        }
    }
    LaunchedEffect(performanceNoticeVisible) {
        if (performanceNoticeVisible) {
            delay(PERFORMANCE_NOTICE_MILLIS)
            performanceNoticeVisible = false
        }
    }
    LaunchedEffect(uiState.appearanceEdit, appearanceCommitRequested) {
        if (appearanceCommitRequested && uiState.appearanceEdit == null) {
            appearanceCommitRequested = false
            activePanel = null
        }
    }

    when {
        uiState.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        uiState.errorMessage != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(uiState.errorMessage)
                Button(onClick = onBack, modifier = Modifier.padding(top = 16.dp)) { Text("返回书架") }
            }
        }
        else -> {
            val palette = readerPalette(uiState.settings)
            val positionInfo = readerPositionInfo(
                anchorOffset = uiState.anchorOffset,
                contentLength = uiState.contentLength,
                chapters = uiState.chapters,
            )
            val topInfo = buildList {
                if (uiState.settings.showBookTitle) uiState.book?.title?.takeIf(String::isNotBlank)?.let(::add)
                if (
                    readerShouldShowRunningChapterTitle(
                        showChapterTitle = uiState.settings.showChapterTitle,
                        pageStartOffset = uiState.pages.getOrNull(uiState.currentPageIndex)?.startOffset
                            ?: uiState.anchorOffset,
                        titleRanges = uiState.chapterTitleRanges,
                        pageText = uiState.pages.getOrNull(uiState.currentPageIndex)
                            ?.let(uiState::layoutTextFor)
                            .orEmpty(),
                    )
                ) {
                    add(positionInfo.chapterTitle)
                }
            }.distinct().joinToString(" · ")
            val reserveTopInfoSpace = readerShouldReserveRunningHeaderSpace(
                showBookTitle = uiState.settings.showBookTitle,
                bookTitleAvailable = !uiState.book?.title.isNullOrBlank(),
                showChapterTitle = uiState.settings.showChapterTitle,
                chapterTitleAvailable = uiState.chapters.isNotEmpty(),
            )
            val clockText = rememberReaderClock(uiState.settings.showClock)
            val batteryPercent = rememberBatteryPercent(uiState.settings.showBattery)
            val pageNumberInfo = readerPageNumberInfo(
                anchorOffset = uiState.anchorOffset,
                contentLength = uiState.contentLength,
                pages = uiState.pages,
                currentPageIndex = uiState.currentPageIndex,
                chapters = uiState.chapters,
            )
            val bottomInfo = buildList {
                if (uiState.settings.showPageNumber) pageNumberInfo?.let { add(it.displayText) }
                if (uiState.settings.showChapterProgress) {
                    add("本章 ${positionInfo.chapterProgressPercent}%")
                }
                if (uiState.settings.showBookProgress) add("全书 ${positionInfo.bookProgressPercent}%")
                if (uiState.settings.showClock) clockText?.let(::add)
                if (uiState.settings.showBattery) batteryPercent?.let { add("电量 $it%") }
            }.joinToString(" · ")
            val pagerState = rememberPagerState(
                initialPage = readerInitialPage(uiState.currentPageIndex, uiState.pages.size),
            ) { uiState.pages.size.coerceAtLeast(1) }
            val effectivePageAnimation = if (autoAnimationDisabled) {
                ReaderPageAnimation.NONE
            } else {
                uiState.settings.pageAnimation
            }
            val contentInputEnabled = readerContentInputEnabled(
                touchLocked = touchLocked,
                controlsVisible = controlsVisible,
                selectionActive = selectionActive,
                isPaginating = uiState.isPaginating,
            )
            val coroutineScope = rememberCoroutineScope()
            val turnToPage: (Int, Boolean) -> Unit = { targetPage, userInitiated ->
                if (targetPage in uiState.pages.indices) {
                    if (userInitiated) onUserInteraction()
                    coroutineScope.launch {
                        turnReaderPagerToPage(
                            targetPage = targetPage,
                            pageAnimation = effectivePageAnimation,
                            scrollToPage = { animated ->
                                if (animated) {
                                    pagerState.animateScrollToPage(targetPage)
                                } else {
                                    pagerState.scrollToPage(targetPage)
                                }
                            },
                            onPageChanged = onPageChanged,
                        )
                    }
                }

            }
            DisposableEffect(
                registerVolumePageTurnHandler,
                pagerState,
                uiState.settings.volumeKeyPageTurn,
                contentInputEnabled,
                effectivePageAnimation,
                uiState.pages.size,
            ) {
                if (uiState.settings.volumeKeyPageTurn && contentInputEnabled) {
                    registerVolumePageTurnHandler { direction ->
                        val target = pagerState.currentPage + direction
                        if (target in uiState.pages.indices) {
                            turnToPage(target, true)
                            true
                        } else {
                            false
                        }
                    }
                } else {
                    registerVolumePageTurnHandler(null)
                }
                onDispose { registerVolumePageTurnHandler(null) }
            }
            fun speakPage(pageIndex: Int) {
                val page = uiState.pages.getOrNull(pageIndex) ?: run {
                    ttsActive = false
                    return
                }
                val text = uiState.textFor(page)
                ttsController.speak(text) {
                    val nextPage = pageIndex + 1
                    if (ttsActive && nextPage in uiState.pages.indices) {
                        ttsRequestedPage = nextPage
                        turnToPage(nextPage, false)
                    } else {
                        ttsActive = false
                        ttsRequestedPage = null
                    }
                }
            }
            LaunchedEffect(ttsRequestedPage, pagerState.settledPage, ttsActive) {
                val requested = ttsRequestedPage
                if (ttsActive && requested != null && pagerState.settledPage == requested) {
                    ttsRequestedPage = null
                    speakPage(requested)
                }
            }
            LaunchedEffect(uiState.pages, uiState.currentPageIndex) {
                val restoredPage = readerInitialPage(uiState.currentPageIndex, uiState.pages.size)
                if (uiState.pages.isNotEmpty() && pagerState.currentPage != restoredPage) {
                    pagerState.scrollToPage(restoredPage)
                }
            }
            LaunchedEffect(pagerState, uiState.pages.size) {
                pagerState.interactionSource.interactions.collect { interaction ->
                    if (interaction is DragInteraction.Stop || interaction is DragInteraction.Cancel) {
                        snapshotFlow { pagerState.isScrollInProgress }.first { !it }
                        if (pagerState.settledPage in uiState.pages.indices) {
                            onPageChanged(pagerState.settledPage)
                            onUserInteraction()
                        }
                    }
                }
            }
            LaunchedEffect(pagerState, effectivePageAnimation) {
                if (effectivePageAnimation == ReaderPageAnimation.NONE) return@LaunchedEffect
                snapshotFlow { pagerState.isScrollInProgress }.collect { scrolling ->
                    if (!scrolling) return@collect
                    val frameDurationsMillis = mutableListOf<Float>()
                    var previousFrameNanos = withFrameNanos { it }
                    while (pagerState.isScrollInProgress) {
                        val frameNanos = withFrameNanos { it }
                        frameDurationsMillis += (frameNanos - previousFrameNanos) / 1_000_000f
                        previousFrameNanos = frameNanos
                    }
                    if (shouldDisablePageAnimation(frameDurationsMillis)) {
                        autoAnimationDisabled = true
                        performanceNoticeVisible = true
                    }
                }
            }

            Box(
                modifier = Modifier.fillMaxSize()
                    .testTag("reader_root")
                    .background(palette.background),
            ) {
                BoxWithConstraints(
                    modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.displayCutout),
                ) {
                    val density = LocalDensity.current
                    val horizontalPadding = uiState.settings.horizontalPaddingDp.dp
                    val requestedVerticalPadding = uiState.settings.verticalPaddingDp.dp
                    val topPadding = requestedVerticalPadding + if (reserveTopInfoSpace) 30.dp else 0.dp
                    val bottomPadding = when {
                        uiState.isBrowsingTemporarily -> 76.dp
                        bottomInfo.isBlank() -> requestedVerticalPadding
                        else -> requestedVerticalPadding + 28.dp
                    }
                    val textViewportWidth = minOf(maxWidth, MAX_READER_COLUMN_WIDTH)
                    val layoutSpec = with(density) {
                        val fontSizePx = uiState.settings.fontSizeSp.sp.toPx()
                        ReaderLayoutSpec(
                            widthPx = (textViewportWidth - horizontalPadding * 2).roundToPx().coerceAtLeast(1),
                            heightPx = (maxHeight - topPadding - bottomPadding).roundToPx().coerceAtLeast(1),
                            fontSizePx = fontSizePx,
                            lineHeightPx = (
                                uiState.settings.fontSizeSp * uiState.settings.lineHeightMultiplier
                            ).sp.toPx(),
                            font = uiState.settings.font,
                            fontWeight = uiState.settings.fontWeight,
                            letterSpacingEm = uiState.settings.letterSpacingEm,
                            paragraphSpacingPx = fontSizePx * uiState.settings.paragraphSpacingEm,
                            firstLineIndentPx = fontSizePx * uiState.settings.firstLineIndentEm,
                            alignment = uiState.settings.alignment,
                        )
                    }
                    LaunchedEffect(layoutSpec) { onLayoutChanged(layoutSpec) }

                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        userScrollEnabled = contentInputEnabled,
                        beyondViewportPageCount = 1,
                    ) { index ->
                        val page = uiState.pages.getOrNull(index)
                        Box(
                            modifier = Modifier.fillMaxSize()
                                .graphicsLayer {
                                    if (effectivePageAnimation == ReaderPageAnimation.COVER) {
                                        val pageOffset = (
                                            (pagerState.currentPage - index) + pagerState.currentPageOffsetFraction
                                        )
                                        translationX = readerCoverTranslation(pageOffset, size.width)
                                    }
                                }
                                .background(palette.background),
                        ) {
                            ReaderPageSurface(
                                text = page?.let(uiState::layoutTextFor).orEmpty(),
                                pageStartOffset = page?.startOffset ?: 0,
                                sourceText = page?.let(uiState::textFor).orEmpty(),
                                chapterTitleRanges = uiState.chapterTitleRanges,
                                layoutSpec = layoutSpec,
                                firstParagraphIsContinuation = page?.let {
                                    uiState.pageStartsInsideParagraph(it)
                                } ?: false,
                                lastParagraphIsContinuation = page?.let {
                                    uiState.pageEndsInsideParagraph(it)
                                } ?: false,
                                contentColor = palette.content,
                                backgroundColor = palette.background,
                                annotations = page?.let { currentPage ->
                                    uiState.annotations.filter { annotation ->
                                        annotation.range.startOffset < currentPage.endOffset &&
                                            annotation.range.endOffset > currentPage.startOffset
                                    }
                                }.orEmpty(),
                                focusBand = uiState.settings.focusBand,
                                tapActions = uiState.settings.tapZoneActions,
                                tapInputEnabled = contentInputEnabled,
                                selectionEnabled = !touchLocked && !controlsVisible && !uiState.isPaginating,
                                isCurrentPage = pagerState.settledPage == index,
                                onTapAction = { action ->
                                    when (action) {
                                        ReaderTapAction.PREVIOUS_PAGE -> turnToPage(index - 1, true)
                                        ReaderTapAction.NEXT_PAGE -> turnToPage(index + 1, true)
                                        ReaderTapAction.MENU -> {
                                            controlsVisible = !controlsVisible
                                            onUserInteraction()
                                        }
                                        ReaderTapAction.NONE -> Unit
                                    }
                                },
                                onSelectionActiveChanged = { selectionActive = it },
                                onSelectionAction = { selection, action ->
                                    onUserInteraction()
                                    when (action) {
                                        ReaderSelectionAction.Bookmark -> onAddAnnotation(
                                            selection.startOffset,
                                            selection.startOffset,
                                            AnnotationKind.BOOKMARK,
                                            null,
                                            null,
                                        )
                                        is ReaderSelectionAction.Highlight -> onAddAnnotation(
                                            selection.startOffset,
                                            selection.endOffset,
                                            AnnotationKind.HIGHLIGHT,
                                            action.color,
                                            null,
                                        )
                                        ReaderSelectionAction.Note -> pendingNoteSelection = selection
                                    }
                                },
                                modifier = Modifier.testTag("reader_page_surface")
                                    .align(Alignment.Center)
                                    .widthIn(max = MAX_READER_COLUMN_WIDTH)
                                    .fillMaxWidth()
                                    .fillMaxHeight()
                                    .padding(
                                        start = horizontalPadding,
                                        top = topPadding,
                                        end = horizontalPadding,
                                        bottom = bottomPadding,
                                    ),
                            )
                        }
                    }
                    ReaderWarmOverlay(
                        colorArgb = uiState.settings.warmOverlayArgb,
                        opacity = uiState.settings.warmOverlayOpacity,
                        modifier = Modifier.fillMaxSize(),
                    )
                    ReaderBrightnessGesture(
                        currentBrightness = uiState.settings.brightness,
                        enabled = contentInputEnabled,
                        onPreview = { value ->
                            brightnessPreview = value
                            applyReaderBrightness(context, value)
                        },
                        onCommit = { value ->
                            onSettingsChanged(uiState.settings.copy(brightness = value))
                            onUserInteraction()
                            brightnessPreview = null
                        },
                        onCancel = {
                            applyReaderBrightness(context, uiState.settings.brightness)
                            brightnessPreview = null
                        },
                        modifier = Modifier.align(Alignment.CenterStart)
                            .windowInsetsPadding(
                                WindowInsets.systemGestures.only(WindowInsetsSides.Start),
                            )
                            .width(BRIGHTNESS_GESTURE_WIDTH)
                            .fillMaxHeight(),
                    )
                }

                AnimatedVisibility(
                    visible = !controlsVisible && topInfo.isNotBlank(),
                    modifier = Modifier.align(Alignment.TopCenter)
                        .windowInsetsPadding(WindowInsets.displayCutout),
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    Text(
                        text = topInfo,
                        modifier = Modifier
                            .testTag("reader_running_header")
                            .padding(horizontal = 24.dp, vertical = 14.dp),
                        color = palette.secondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                    )
                }

                AnimatedVisibility(
                    visible = controlsVisible,
                    modifier = Modifier.fillMaxSize(),
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.16f))
                            .semantics {
                                contentDescription = "隐藏阅读菜单"
                                role = Role.Button
                            }
                            .clickable { controlsVisible = false },
                    )
                }

                AnimatedVisibility(
                    visible = controlsVisible,
                    modifier = Modifier.fillMaxSize(),
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    ReaderControls(
                        title = uiState.book?.title.orEmpty(),
                        chapterTitle = positionInfo.chapterTitle,
                        bookProgressPercent = positionInfo.bookProgressPercent,
                        onBack = onBack,
                        onOpenNavigation = { activePanel = ReaderPanel.NAVIGATION },
                        onOpenChapterManagement = { activePanel = ReaderPanel.CHAPTERS },
                        onOpenProgress = { activePanel = ReaderPanel.PROGRESS },
                        onOpenSearch = { activePanel = ReaderPanel.SEARCH },
                        onOpenSettings = {
                            appearanceCommitRequested = false
                            onBeginAppearanceEdit(ReaderSettingsScope.GLOBAL)
                            activePanel = ReaderPanel.SETTINGS
                        },
                        onOpenNotes = { activePanel = ReaderPanel.NOTES },
                        onToggleBookmark = onToggleBookmark,
                        isBookmarked = uiState.pages.getOrNull(pagerState.currentPage)?.let { page ->
                            uiState.bookmarks.any { it.offset == page.startOffset.toLong() }
                        } == true,
                        isSpeaking = ttsActive,
                        onLockTouch = {
                            controlsVisible = false
                            touchLocked = true
                        },
                        backgroundColor = palette.background,
                        contentColor = palette.content,
                        onToggleSpeech = {
                            if (ttsActive) {
                                ttsController.stop()
                                ttsActive = false
                                ttsRequestedPage = null
                            } else {
                                ttsActive = true
                                speakPage(pagerState.currentPage)
                            }
                        },
                    )
                }

                brightnessPreview?.let { value ->
                    Surface(
                        modifier = Modifier.align(Alignment.CenterStart).padding(start = 72.dp),
                        color = Color.Black.copy(alpha = 0.72f),
                        contentColor = Color.White,
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Text(
                            text = stringResource(R.string.reader_brightness_percent, (value * 100).toInt()),
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                        )
                    }
                }

                AnimatedVisibility(
                    visible = performanceNoticeVisible,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 72.dp),
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.76f),
                        contentColor = Color.White,
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Text(
                            text = stringResource(R.string.reader_animation_auto_disabled),
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                        )
                    }
                }

                AnimatedVisibility(
                    visible = !controlsVisible && !uiState.isBrowsingTemporarily && bottomInfo.isNotBlank(),
                    modifier = Modifier.align(Alignment.BottomCenter)
                        .windowInsetsPadding(WindowInsets.displayCutout),
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    Text(
                        text = bottomInfo,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
                        color = palette.secondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                    )
                }
                AnimatedVisibility(
                    visible = uiState.isBrowsingTemporarily,
                    modifier = Modifier.align(Alignment.BottomCenter)
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    enter = fadeIn() + slideInVertically { it },
                    exit = fadeOut() + slideOutVertically { it },
                ) {
                    TemporaryBrowsingBar(
                        canNavigateBack = uiState.canNavigateBack,
                        onNavigateBack = onNavigateBack,
                        onReturnToOrigin = onReturnToOrigin,
                        onContinueFromHere = onContinueFromHere,
                    )
                }
                if (uiState.isPaginating) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.BottomEnd)
                            .testTag("reader_pagination_progress")
                            .windowInsetsPadding(WindowInsets.displayCutout)
                            .padding(16.dp),
                    )
                }
                if (touchLocked) {
                    TouchLockOverlay(onUnlock = { touchLocked = false })
                }
            }

            when (activePanel) {
                ReaderPanel.NAVIGATION -> ReaderNavigationDrawer(
                    bookTitle = uiState.book?.title.orEmpty(),
                    chapters = uiState.chapters,
                    bookmarks = uiState.bookmarks,
                    anchorOffset = uiState.anchorOffset,
                    content = uiState.content,
                    contentStartOffset = uiState.contentStartOffset,
                    initialTab = navigationTab,
                    onTabChanged = { navigationTab = it },
                    onSelectChapter = { chapter ->
                        onJumpTemporarily(chapter.startOffset, ReaderJumpReason.CHAPTER)
                        activePanel = null
                        controlsVisible = false
                    },
                    onSelectBookmark = { bookmark ->
                        onJumpTemporarily(bookmark.offset.toInt(), ReaderJumpReason.BOOKMARK)
                        activePanel = null
                        controlsVisible = false
                    },
                    onDeleteBookmark = { bookmark -> onDeleteAnnotation(bookmark.id) },
                    onDismiss = { activePanel = null },
                )
                ReaderPanel.CHAPTERS -> ReaderChapterPanel(
                    chapters = uiState.chapters,
                    ruleSet = uiState.chapterRuleSet,
                    manuallyEdited = uiState.chaptersManuallyEdited,
                    isAnalyzing = uiState.isAnalyzingChapters,
                    onSelect = { chapter ->
                        onJumpTemporarily(chapter.startOffset, ReaderJumpReason.CHAPTER)
                        activePanel = null
                    },
                    onReanalyze = onReanalyzeChapters,
                    onAddAtCurrent = onAddChapterAtCurrent,
                    onRename = onRenameChapter,
                    onMoveToCurrent = onMoveChapterToCurrent,
                    onDelete = onDeleteChapter,
                    onDismiss = { activePanel = null },
                )
                ReaderPanel.SEARCH -> SearchDialog(
                    results = uiState.searchResults,
                    chapters = uiState.chapters,
                    indexState = uiState.searchIndexState,
                    contentLength = uiState.contentLength,
                    isSearching = uiState.isSearching,
                    onSearch = onSearch,
                    onRebuildIndex = onRebuildSearchIndex,
                    onCancelIndex = onCancelSearchIndex,
                    onSelect = { result ->
                        onJumpTemporarily(result.offset, ReaderJumpReason.SEARCH)
                        activePanel = null
                    },
                    onDismiss = { activePanel = null },
                )
                ReaderPanel.PROGRESS -> ProgressDialog(
                    anchorOffset = uiState.anchorOffset,
                    contentLength = uiState.contentLength,
                    chapters = uiState.chapters,
                    preview = uiState.progressPreview,
                    isLoading = uiState.isProgressPreviewLoading,
                    onPreview = onPreviewProgress,
                    onCommit = {
                        onCommitProgressPreview()
                        activePanel = null
                    },
                    onDismiss = {
                        onCancelProgressPreview()
                        activePanel = null
                    },
                )
                ReaderPanel.SETTINGS -> uiState.appearanceEdit?.let { edit ->
                    ReaderSettingsSheet(
                        edit = edit,
                        importedFonts = uiState.importedFonts,
                        themes = uiState.themes,
                        activeThemeId = uiState.activeThemeId,
                        themeSchedule = uiState.themeSchedule,
                        manualThemeOverride = uiState.manualThemeOverride,
                        bookTitle = uiState.book?.title,
                        bookOverrides = uiState.bookAppearanceOverrides,
                        errorMessage = uiState.appearanceErrorMessage,
                        onScopeChanged = onChangeAppearanceEditScope,
                        onPreview = onPreviewAppearance,
                        onCommit = {
                            appearanceCommitRequested = true
                            onCommitAppearanceEdit()
                        },
                        onCancel = {
                            appearanceCommitRequested = false
                            onCancelAppearanceEdit()
                            activePanel = null
                        },
                        onClearCurrentBookOverrides = {
                            appearanceCommitRequested = false
                            onCancelAppearanceEdit()
                            onClearCurrentBookOverrides()
                            activePanel = null
                        },
                        onApplyTheme = onApplyTheme,
                        onCreateTheme = onCreateTheme,
                        onCopyTheme = onCopyTheme,
                        onRenameTheme = onRenameTheme,
                        onUpdateTheme = onUpdateTheme,
                        onDeleteTheme = onDeleteTheme,
                        onUpdateThemeSchedule = onUpdateThemeSchedule,
                        onImportFont = onImportFont,
                        onRemoveImportedFont = onRemoveImportedFont,
                    )
                }
                ReaderPanel.NOTES -> ReaderAnnotationPanel(
                    annotations = uiState.annotations,
                    onSelect = { annotation ->
                        onJumpTemporarily(
                            annotation.range.startOffset.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                            ReaderJumpReason.ANNOTATION,
                        )
                        activePanel = null
                    },
                    onEditNote = { annotation, note ->
                        onUpdateAnnotation(annotation.id, annotation.color, note)
                    },
                    onDelete = { annotation -> onDeleteAnnotation(annotation.id) },
                    onExport = onExportAnnotations,
                    isExporting = uiState.isAnnotationExporting,
                    exportMessage = uiState.annotationExportMessage,
                    onDismiss = { activePanel = null },
                    modifier = Modifier.testTag("reader_annotations"),
                )
                null -> Unit
            }
            pendingNoteSelection?.let { selection ->
                var note by remember(selection) { mutableStateOf("") }
                AlertDialog(
                    onDismissRequest = { pendingNoteSelection = null },
                    title = { Text("添加批注") },
                    text = {
                        OutlinedTextField(
                            value = note,
                            onValueChange = { note = it },
                            label = { Text("批注内容") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                onAddAnnotation(
                                    selection.startOffset,
                                    selection.endOffset,
                                    AnnotationKind.NOTE,
                                    null,
                                    note,
                                )
                                pendingNoteSelection = null
                            },
                            enabled = note.isNotBlank(),
                        ) { Text("保存") }
                    },
                    dismissButton = {
                        TextButton(onClick = { pendingNoteSelection = null }) { Text("取消") }
                    },
                )
            }
        }
    }
}

@Composable
private fun TemporaryBrowsingBar(
    canNavigateBack: Boolean,
    onNavigateBack: () -> Unit,
    onReturnToOrigin: () -> Unit,
    onContinueFromHere: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.extraLarge,
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = onReturnToOrigin,
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text("返回原位置") }
            TextButton(
                onClick = onNavigateBack,
                enabled = canNavigateBack,
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text("后退") }
            Button(
                onClick = onContinueFromHere,
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text("从这里继续") }
        }
    }
}

@Composable
private fun ReaderTapGrid(
    actions: List<ReaderTapAction>,
    onAction: (ReaderTapAction) -> Unit,
) {
    val normalizedActions = actions.takeIf { it.size == ReaderSettings.TAP_ZONE_COUNT }
        ?: ReaderSettings.DEFAULT_TAP_ZONE_ACTIONS
    Box(
        modifier = Modifier.fillMaxSize().pointerInput(normalizedActions) {
            detectTapGestures { offset ->
                val zone = readerTapZone(
                    x = offset.x,
                    y = offset.y,
                    width = size.width.toFloat(),
                    height = size.height.toFloat(),
                )
                onAction(normalizedActions[zone])
            }
        },
    ) {
        Column(Modifier.fillMaxSize()) {
            repeat(3) { row ->
                Row(Modifier.fillMaxWidth().weight(1f)) {
                    repeat(3) { column ->
                        val zone = row * 3 + column
                        val action = normalizedActions[zone]
                        val label = action.displayName()
                        val description = stringResource(
                            R.string.reader_tap_zone_description,
                            zone + 1,
                            label,
                        )
                        Box(
                            modifier = Modifier.fillMaxHeight().weight(1f).semantics {
                                contentDescription = description
                                role = Role.Button
                                onClick {
                                    onAction(action)
                                    true
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReaderBrightnessGesture(
    currentBrightness: Float,
    enabled: Boolean,
    onPreview: (Float) -> Unit,
    onCommit: (Float) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Box(
        modifier = modifier.pointerInput(enabled, currentBrightness) {
            if (enabled) {
                var start = 0.5f
                var accumulatedDrag = 0f
                var latest = start
                detectVerticalDragGestures(
                    onDragStart = {
                        start = resolveReaderBrightness(context, currentBrightness)
                        latest = start
                        accumulatedDrag = 0f
                        onPreview(start)
                    },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        accumulatedDrag += dragAmount
                        latest = adjustReaderBrightness(
                            start = start,
                            dragDeltaY = accumulatedDrag,
                            height = size.height.toFloat(),
                        )
                        onPreview(latest)
                    },
                    onDragEnd = { onCommit(latest) },
                    onDragCancel = onCancel,
                )
            }
        },
    )
}

@Composable
private fun TouchLockOverlay(onUnlock: () -> Unit) {
    val hint = stringResource(R.string.reader_touch_locked_hint)
    val unlockLabel = stringResource(R.string.reader_unlock_touch)
    Box(
        modifier = Modifier.fillMaxSize()
            .background(Color.Black.copy(alpha = 0.06f))
            .semantics {
                contentDescription = hint
                role = Role.Button
                onLongClick(label = unlockLabel) {
                    onUnlock()
                    true
                }
            }
            .pointerInput(onUnlock) {
                detectTapGestures(onLongPress = { onUnlock() })
            },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            color = Color.Black.copy(alpha = 0.72f),
            contentColor = Color.White,
            shape = MaterialTheme.shapes.large,
        ) {
            Text(hint, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
        }
    }
}

@Composable
internal fun ReaderControls(
    title: String,
    chapterTitle: String,
    bookProgressPercent: Int,
    onBack: () -> Unit,
    onOpenNavigation: () -> Unit,
    onOpenChapterManagement: () -> Unit,
    onOpenProgress: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenNotes: () -> Unit,
    onToggleBookmark: () -> Unit,
    isBookmarked: Boolean,
    isSpeaking: Boolean,
    onLockTouch: () -> Unit,
    backgroundColor: Color,
    contentColor: Color,
    onToggleSpeech: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var moreExpanded by remember { mutableStateOf(false) }
    Box(modifier = modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(),
            color = backgroundColor,
            contentColor = contentColor,
            shadowElevation = 6.dp,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                ReaderMenuIconButton(
                    icon = XinYueIcons.ArrowBack,
                    contentDescription = "返回书架",
                    onClick = onBack,
                    tint = contentColor,
                    testTag = "reader-menu-back",
                )
                Text(
                    title,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                ReaderMenuIconButton(
                    icon = if (isBookmarked) XinYueIcons.BookmarkSelected else XinYueIcons.BookmarkOutlined,
                    contentDescription = if (isBookmarked) "取消当前页书签" else "添加当前页书签",
                    onClick = onToggleBookmark,
                    tint = contentColor,
                    testTag = "reader-menu-bookmark",
                )
                Box {
                    ReaderMenuIconButton(
                        icon = XinYueIcons.MoreHorizontal,
                        contentDescription = "更多阅读操作",
                        onClick = { moreExpanded = true },
                        tint = contentColor,
                        testTag = "reader-menu-more",
                    )
                    DropdownMenu(
                        expanded = moreExpanded,
                        onDismissRequest = { moreExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("目录管理") },
                            onClick = {
                                moreExpanded = false
                                onOpenChapterManagement()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("定位进度") },
                            onClick = {
                                moreExpanded = false
                                onOpenProgress()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.reader_touch_lock)) },
                            onClick = {
                                moreExpanded = false
                                onLockTouch()
                            },
                            leadingIcon = {
                                Icon(
                                    painterResource(XinYueIcons.Lock),
                                    contentDescription = null,
                                )
                            },
                        )
                    }
                }
            }
        }
        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            color = backgroundColor,
            contentColor = contentColor,
            shadowElevation = 0.dp,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
                    )
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(32.dp)
                        .padding(horizontal = 4.dp)
                        .testTag("reader-progress-info"),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        chapterTitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text("全书 $bookProgressPercent%", style = MaterialTheme.typography.bodySmall)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("reader-menu-actions"),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ReaderMenuActionButton(
                        XinYueIcons.Library,
                        "打开目录和书签",
                        "目录",
                        onOpenNavigation,
                        contentColor,
                        "reader-menu-navigation",
                    )
                    ReaderMenuActionButton(
                        XinYueIcons.Search,
                        "搜索正文",
                        "搜索",
                        onOpenSearch,
                        contentColor,
                        "reader-menu-search",
                    )
                    ReaderMenuActionButton(
                        XinYueIcons.Headphones,
                        if (isSpeaking) "停止朗读" else "开始朗读",
                        "朗读",
                        onToggleSpeech,
                        if (isSpeaking) MaterialTheme.colorScheme.primary else contentColor,
                        "reader-menu-speech",
                    )
                    ReaderMenuActionButton(
                        XinYueIcons.Appearance,
                        "阅读外观",
                        "外观",
                        onOpenSettings,
                        contentColor,
                        "reader-menu-appearance",
                    )
                    ReaderMenuActionButton(
                        XinYueIcons.Notes,
                        "查看笔记",
                        "笔记",
                        onOpenNotes,
                        contentColor,
                        "reader-menu-notes",
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.ReaderMenuActionButton(
    icon: Int,
    contentDescription: String,
    label: String,
    onClick: () -> Unit,
    tint: Color,
    testTag: String,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .height(56.dp)
            .weight(1f)
            .testTag(testTag)
            .semantics { this.contentDescription = contentDescription },
        color = Color.Transparent,
        contentColor = tint,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = tint,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ReaderMenuIconButton(
    icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
    tint: Color,
    testTag: String,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(48.dp).testTag(testTag),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = contentDescription,
            tint = tint,
        )
    }
}

@Composable
private fun ProgressDialog(
    anchorOffset: Int,
    contentLength: Long,
    chapters: List<DetectedChapter>,
    preview: ReaderPositionPreview?,
    isLoading: Boolean,
    onPreview: (Float) -> Unit,
    onCommit: () -> Unit,
    onDismiss: () -> Unit,
) {
    val safeLength = contentLength.coerceAtLeast(0)
    var fraction by remember(anchorOffset, safeLength) {
        mutableStateOf(
            if (safeLength == 0L) 0f else (anchorOffset.toDouble() / safeLength).toFloat().coerceIn(0f, 1f),
        )
    }
    val targetOffset = readerProgressOffset(fraction, safeLength)
    val targetInfo = readerPositionInfo(targetOffset, safeLength, chapters)
    LaunchedEffect(anchorOffset, safeLength) {
        if (safeLength > 0) onPreview(fraction)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("阅读进度") },
        text = {
            Column {
                val chapterTitle = preview?.chapterTitle ?: targetInfo.chapterTitle
                val percent = preview?.percent ?: targetInfo.bookProgressPercent
                Text("$chapterTitle · 全书 $percent%")
                Slider(
                    value = fraction,
                    onValueChange = {
                        fraction = it
                        onPreview(it)
                    },
                    onValueChangeFinished = {
                        if (!isLoading && preview != null) onCommit()
                    },
                    valueRange = 0f..1f,
                    enabled = safeLength > 0,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .testTag("reader-progress-slider"),
                )
                if (isLoading) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .testTag("reader-progress-preview-loading"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.width(20.dp).height(20.dp))
                        Text("正在读取目标位置…", style = MaterialTheme.typography.bodySmall)
                    }
                } else if (preview != null) {
                    Text(
                        text = preview.snippet.ifBlank { "目标位置附近没有可预览文字" },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .testTag("reader-progress-preview-snippet"),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onCommit,
                enabled = safeLength > 0 && preview != null && !isLoading,
            ) { Text("跳转") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
internal fun SearchDialog(
    results: List<ReaderSearchResult>,
    chapters: List<DetectedChapter>,
    indexState: com.xinyue.reader.core.domain.model.BookSearchIndexState?,
    contentLength: Long,
    isSearching: Boolean,
    onSearch: (String) -> Unit,
    onRebuildIndex: () -> Unit,
    onCancelIndex: () -> Unit,
    onSelect: (ReaderSearchResult) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var submittedQuery by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("正文搜索") },
        text = {
            val statusText = when (indexState?.status) {
                BookSearchIndexStatus.BUILDING -> {
                    val percent = if (contentLength > 0) {
                        (indexState.indexedUtf16Length * 100 / contentLength).coerceIn(0, 100)
                    } else 0
                    "正在建立中文索引 $percent%"
                }
                BookSearchIndexStatus.READY -> "中文索引已就绪"
                BookSearchIndexStatus.ERROR -> indexState.errorMessage ?: "索引建立失败"
                else -> "正在准备中文索引"
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .testTag("reader-search-content")
                    .semantics { stateDescription = "搜索结果：${results.size} 条" },
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(statusText, style = MaterialTheme.typography.bodySmall)
                        if (indexState?.status == BookSearchIndexStatus.BUILDING) {
                            TextButton(onClick = onCancelIndex) { Text("取消") }
                        } else {
                            TextButton(onClick = onRebuildIndex) { Text("重建索引") }
                        }
                    }
                }
                item {
                    OutlinedTextField(
                        value = query,
                        onValueChange = {
                            query = it
                            if (it != submittedQuery) submittedQuery = ""
                        },
                        label = { Text("关键词") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    Button(
                        onClick = {
                            submittedQuery = query
                            onSearch(query)
                        },
                        enabled = query.codePointCount(0, query.length) >= 2 && !isSearching,
                        modifier = Modifier.padding(top = 8.dp).testTag("reader-search-submit"),
                    ) { Text("搜索") }
                }
                if (isSearching) {
                    item { CircularProgressIndicator(modifier = Modifier.padding(12.dp)) }
                }
                if (
                    !isSearching &&
                    submittedQuery.isNotBlank() &&
                    indexState?.status == BookSearchIndexStatus.READY &&
                    results.isEmpty()
                ) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp)
                                .testTag("reader-search-empty"),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text("未找到匹配内容", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "换个关键词试试",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (submittedQuery.isNotBlank()) items(results, key = { it.offset }) { result ->
                    val chapter = chapters.lastOrNull { it.startOffset <= result.offset }?.title ?: "正文"
                    val prefix = "$chapter · "
                    val highlighted = buildAnnotatedString {
                        append(prefix)
                        append(result.snippet)
                        val start = prefix.length + result.highlightStart
                        val end = prefix.length + result.highlightEnd
                        if (start in 0..length && end in start..length) {
                            addStyle(
                                SpanStyle(
                                    fontWeight = FontWeight.Bold,
                                    background = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                                ),
                                start,
                                end,
                            )
                        }
                    }
                    Text(
                        text = highlighted,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("reader-search-result")
                            .clickable { onSelect(result) }
                            .padding(vertical = 12.dp),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

private enum class ReaderPanel {
    NAVIGATION,
    CHAPTERS,
    PROGRESS,
    SEARCH,
    SETTINGS,
    NOTES,
}

private data class ReaderPalette(
    val background: Color,
    val content: Color,
    val secondary: Color,
)

private fun readerPalette(settings: ReaderSettings): ReaderPalette = ReaderPalette(
    background = Color(settings.backgroundArgb.toInt()),
    content = Color(settings.foregroundArgb.toInt()),
    secondary = Color(settings.foregroundArgb.toInt()).copy(alpha = 0.68f),
)

@Composable
private fun ReaderTapAction.displayName(): String = when (this) {
    ReaderTapAction.PREVIOUS_PAGE -> stringResource(R.string.reader_previous_page)
    ReaderTapAction.NEXT_PAGE -> stringResource(R.string.reader_next_page)
    ReaderTapAction.MENU -> stringResource(R.string.reader_menu)
    ReaderTapAction.NONE -> stringResource(R.string.reader_no_action)
}

private fun ReaderTapAction.next(): ReaderTapAction = when (this) {
    ReaderTapAction.PREVIOUS_PAGE -> ReaderTapAction.NEXT_PAGE
    ReaderTapAction.NEXT_PAGE -> ReaderTapAction.MENU
    ReaderTapAction.MENU -> ReaderTapAction.NONE
    ReaderTapAction.NONE -> ReaderTapAction.PREVIOUS_PAGE
}

private fun ReaderFontRef.toComposeFontFamily(): FontFamily = when (this) {
    ReaderFontRef.System -> FontFamily.Default
    ReaderFontRef.Serif -> FontFamily.Serif
    ReaderFontRef.SansSerif -> FontFamily.SansSerif
    is ReaderFontRef.Imported -> FontFamily.Default
}

internal data class ReaderPositionInfo(
    val chapterTitle: String,
    val bookProgressPercent: Int,
    val chapterProgressPercent: Int,
)

internal data class ReaderPageNumberInfo(
    val current: Int,
    val total: Int,
    val isEstimated: Boolean,
) {
    val displayText: String
        get() = "第$current/${total}页"
}

internal fun readerPageNumberInfo(
    anchorOffset: Int,
    contentLength: Long,
    pages: List<ReaderPage>,
    currentPageIndex: Int,
    chapters: List<DetectedChapter>,
): ReaderPageNumberInfo? {
    if (contentLength <= 0 || pages.isEmpty()) return null
    val safeAnchor = anchorOffset.toLong().coerceIn(0, contentLength)
    val orderedChapters = chapters
        .filter { it.startOffset.toLong() in 0..contentLength }
        .sortedBy(DetectedChapter::startOffset)
        .distinctBy(DetectedChapter::startOffset)
    val chapterIndex = orderedChapters.indexOfLast { it.startOffset.toLong() <= safeAnchor }
    val chapterStart = orderedChapters.getOrNull(chapterIndex)?.startOffset?.toLong() ?: 0L
    val chapterEnd = orderedChapters.getOrNull(chapterIndex + 1)?.startOffset?.toLong() ?: contentLength
    val safeChapterEnd = chapterEnd.coerceAtLeast(chapterStart)
    val orderedPages = pages.sortedBy(ReaderPage::startOffset)
    val chapterPages = orderedPages.filter { page ->
        page.endOffset.toLong() > chapterStart && page.startOffset.toLong() < safeChapterEnd
    }
    val coversWholeChapter = chapterPages.isNotEmpty() &&
        chapterPages.first().startOffset.toLong() <= chapterStart &&
        chapterPages.last().endOffset.toLong() >= safeChapterEnd &&
        chapterPages.zipWithNext().all { (first, second) -> first.endOffset >= second.startOffset }
    if (coversWholeChapter) {
        val pageAtAnchor = chapterPages.indexOfFirst { page ->
            safeAnchor >= page.startOffset &&
                (safeAnchor < page.endOffset || safeAnchor == contentLength && page.endOffset.toLong() == contentLength)
        }
        val fallbackPage = orderedPages.getOrNull(currentPageIndex)
        val fallbackIndex = fallbackPage?.let(chapterPages::indexOf) ?: -1
        return ReaderPageNumberInfo(
            current = ((if (pageAtAnchor >= 0) pageAtAnchor else fallbackIndex) + 1)
                .coerceIn(1, chapterPages.size),
            total = chapterPages.size,
            isEstimated = false,
        )
    }

    val spans = orderedPages.map { (it.endOffset - it.startOffset).coerceAtLeast(1) }.sorted()
    val typicalPageSpan = spans[spans.size / 2].toLong()
    val chapterLength = (safeChapterEnd - chapterStart).coerceAtLeast(1)
    val total = ((chapterLength + typicalPageSpan - 1) / typicalPageSpan)
        .coerceIn(1, Int.MAX_VALUE.toLong())
        .toInt()
    val current = ((safeAnchor - chapterStart).coerceAtLeast(0) / typicalPageSpan + 1)
        .coerceIn(1, total.toLong())
        .toInt()
    return ReaderPageNumberInfo(current = current, total = total, isEstimated = true)
}

internal fun readerPositionInfo(
    anchorOffset: Int,
    contentLength: Long,
    chapters: List<DetectedChapter>,
): ReaderPositionInfo {
    val safeLength = contentLength.coerceAtLeast(0)
    val safeOffset = anchorOffset.toLong().coerceIn(0, safeLength)
    val orderedChapters = chapters.sortedBy(DetectedChapter::startOffset)
    val chapterIndex = orderedChapters.indexOfLast { it.startOffset.toLong() <= safeOffset }
    val chapterTitle = orderedChapters.getOrNull(chapterIndex)?.title ?: "正文"
    val chapterStart = orderedChapters.getOrNull(chapterIndex)?.startOffset?.toLong() ?: 0L
    val chapterEnd = orderedChapters.getOrNull(chapterIndex + 1)?.startOffset?.toLong()
        ?.coerceAtMost(safeLength)
        ?: safeLength
    val chapterPercent = if (chapterEnd <= chapterStart) {
        0
    } else {
        (((safeOffset - chapterStart).coerceAtLeast(0).toDouble() /
            (chapterEnd - chapterStart)) * 100).toInt().coerceIn(0, 100)
    }
    val bookPercent = if (safeLength == 0L) {
        0
    } else {
        (safeOffset.toDouble() / safeLength * 100).toInt().coerceIn(0, 100)
    }
    return ReaderPositionInfo(chapterTitle, bookPercent, chapterPercent)
}

internal fun readerProgressOffset(fraction: Float, contentLength: Long): Int {
    val safeLength = contentLength.coerceIn(0, Int.MAX_VALUE.toLong())
    return (safeLength * fraction.coerceIn(0f, 1f).toDouble()).roundToLong()
        .coerceIn(0, safeLength)
        .toInt()
}

@Composable
private fun rememberReaderClock(enabled: Boolean): String? {
    val context = LocalContext.current
    var value by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(enabled, context) {
        if (!enabled) {
            value = null
            return@LaunchedEffect
        }
        while (true) {
            value = DateFormat.getTimeFormat(context).format(Date())
            delay(CLOCK_REFRESH_MILLIS)
        }
    }
    return value
}

@Composable
private fun rememberBatteryPercent(enabled: Boolean): Int? {
    val context = LocalContext.current
    var value by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(enabled, context) {
        if (!enabled) {
            value = null
            return@LaunchedEffect
        }
        val batteryManager = context.getSystemService(BatteryManager::class.java)
        while (true) {
            value = batteryManager
                ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                ?.takeIf { it in 0..100 }
            delay(BATTERY_REFRESH_MILLIS)
        }
    }
    return value
}

@Composable
private fun ImmersiveSystemBars(settings: ReaderSettings) {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = view.context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        val originalLightStatusBars = controller?.isAppearanceLightStatusBars
        val originalLightNavigationBars = controller?.isAppearanceLightNavigationBars
        val originalNavigationBarContrast = if (Build.VERSION.SDK_INT >= 29) {
            window?.isNavigationBarContrastEnforced
        } else {
            null
        }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        if (Build.VERSION.SDK_INT >= 29) window?.isNavigationBarContrastEnforced = false
        val focusListener = ViewTreeObserver.OnWindowFocusChangeListener { hasFocus ->
            if (hasFocus) controller?.hide(WindowInsetsCompat.Type.systemBars())
        }
        view.viewTreeObserver.addOnWindowFocusChangeListener(focusListener)
        onDispose {
            if (view.viewTreeObserver.isAlive) {
                view.viewTreeObserver.removeOnWindowFocusChangeListener(focusListener)
            }
            controller?.show(WindowInsetsCompat.Type.systemBars())
            originalLightStatusBars?.let { controller.isAppearanceLightStatusBars = it }
            originalLightNavigationBars?.let { controller.isAppearanceLightNavigationBars = it }
            if (Build.VERSION.SDK_INT >= 29 && originalNavigationBarContrast != null) {
                window?.isNavigationBarContrastEnforced = originalNavigationBarContrast
            }
        }
    }
    LaunchedEffect(
        view,
        settings.backgroundArgb,
        settings.warmOverlayArgb,
        settings.warmOverlayOpacity,
    ) {
        val window = view.context.findActivity()?.window ?: return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        val useDarkIcons = readerUsesDarkSystemBarIcons(settings)
        controller.isAppearanceLightStatusBars = useDarkIcons
        controller.isAppearanceLightNavigationBars = useDarkIcons
    }
}

internal fun readerEffectiveBackgroundArgb(settings: ReaderSettings): Long =
    com.xinyue.reader.core.domain.model.ReaderColorContrast.composite(
        foregroundArgb = settings.warmOverlayArgb,
        backgroundArgb = settings.backgroundArgb,
        opacity = settings.warmOverlayOpacity.toDouble(),
    )

internal fun readerUsesDarkSystemBarIcons(settings: ReaderSettings): Boolean =
    com.xinyue.reader.core.domain.model.ReaderColorContrast.isLight(readerEffectiveBackgroundArgb(settings))

@Composable
private fun ReaderWindowEffects(settings: ReaderSettings) {
    val view = LocalView.current
    DisposableEffect(view, settings.brightness, settings.keepScreenOn) {
        val window = view.context.findActivity()?.window
        val originalBrightness = window?.attributes?.screenBrightness
        val originalKeepScreenOn = view.keepScreenOn
        view.keepScreenOn = settings.keepScreenOn
        if (window != null) {
            val attributes = window.attributes
            attributes.screenBrightness = settings.brightness
            window.attributes = attributes
        }
        onDispose {
            view.keepScreenOn = originalKeepScreenOn
            if (window != null && originalBrightness != null) {
                val attributes = window.attributes
                attributes.screenBrightness = originalBrightness
                window.attributes = attributes
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Moves the pager first and publishes the settled page only after that movement completes.
 * Publishing earlier lets the state-restoration effect call [scrollToPage] and cancel an
 * in-flight animated turn.
 */
internal suspend fun turnReaderPagerToPage(
    targetPage: Int,
    pageAnimation: ReaderPageAnimation,
    scrollToPage: suspend (animated: Boolean) -> Unit,
    onPageChanged: (Int) -> Unit,
) {
    scrollToPage(pageAnimation != ReaderPageAnimation.NONE)
    onPageChanged(targetPage)
}

internal fun readerContentInputEnabled(
    touchLocked: Boolean,
    controlsVisible: Boolean,
    selectionActive: Boolean = false,
    isPaginating: Boolean = false,
): Boolean = !touchLocked && !controlsVisible && !selectionActive && !isPaginating

internal fun readerShouldReserveRunningHeaderSpace(
    showBookTitle: Boolean,
    bookTitleAvailable: Boolean,
    showChapterTitle: Boolean,
    chapterTitleAvailable: Boolean,
): Boolean =
    (showBookTitle && bookTitleAvailable) || (showChapterTitle && chapterTitleAvailable)

internal fun shouldDisablePageAnimation(frameDurationsMillis: List<Float>): Boolean {
    if (frameDurationsMillis.size < MIN_ANIMATION_FRAME_SAMPLES) return false
    if (frameDurationsMillis.any { it >= SEVERE_FRAME_MILLIS }) return true
    val slowFrames = frameDurationsMillis.count { it >= SLOW_FRAME_MILLIS }
    return slowFrames >= MIN_SLOW_FRAMES &&
        slowFrames.toFloat() / frameDurationsMillis.size >= SLOW_FRAME_RATIO
}

internal fun readerCoverTranslation(pageOffset: Float, pageWidth: Float): Float =
    pageOffset.coerceAtLeast(0f) * pageWidth.coerceAtLeast(0f)

internal fun readerTapZone(x: Float, y: Float, width: Float, height: Float): Int {
    val safeWidth = width.coerceAtLeast(1f)
    val safeHeight = height.coerceAtLeast(1f)
    val column = (x.coerceIn(0f, safeWidth - 0.001f) / safeWidth * 3).toInt().coerceIn(0, 2)
    val row = (y.coerceIn(0f, safeHeight - 0.001f) / safeHeight * 3).toInt().coerceIn(0, 2)
    return row * 3 + column
}

internal fun adjustReaderBrightness(start: Float, dragDeltaY: Float, height: Float): Float =
    (start - dragDeltaY / height.coerceAtLeast(1f)).coerceIn(MIN_READER_BRIGHTNESS, 1f)

private fun resolveReaderBrightness(context: Context, configuredBrightness: Float): Float {
    if (configuredBrightness >= 0f) return configuredBrightness.coerceIn(MIN_READER_BRIGHTNESS, 1f)
    val windowBrightness = context.findActivity()?.window?.attributes?.screenBrightness ?: -1f
    if (windowBrightness >= 0f) return windowBrightness.coerceIn(MIN_READER_BRIGHTNESS, 1f)
    return (Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128) / 255f)
        .coerceIn(MIN_READER_BRIGHTNESS, 1f)
}

private fun applyReaderBrightness(context: Context, brightness: Float) {
    val window = context.findActivity()?.window ?: return
    val attributes = window.attributes
    attributes.screenBrightness = if (brightness < 0f) -1f else brightness.coerceIn(MIN_READER_BRIGHTNESS, 1f)
    window.attributes = attributes
}

private val MAX_READER_COLUMN_WIDTH = 720.dp
private val BRIGHTNESS_GESTURE_WIDTH = 44.dp
private const val MIN_READER_BRIGHTNESS = 0.02f
private const val CONTROLS_AUTO_HIDE_MILLIS = 4_000L
private const val CLOCK_REFRESH_MILLIS = 15_000L
private const val BATTERY_REFRESH_MILLIS = 60_000L
private const val PERFORMANCE_NOTICE_MILLIS = 2_800L
private const val MIN_ANIMATION_FRAME_SAMPLES = 8
private const val MIN_SLOW_FRAMES = 3
private const val SLOW_FRAME_MILLIS = 34f
private const val SEVERE_FRAME_MILLIS = 100f
private const val SLOW_FRAME_RATIO = 0.25f

internal fun readerInitialPage(restoredPageIndex: Int, pageCount: Int): Int =
    if (pageCount <= 0) 0 else restoredPageIndex.coerceIn(0, pageCount - 1)

private fun ReaderUiState.pageStartsInsideParagraph(page: ReaderPage): Boolean {
    if (page.startOffset <= 0) return false
    val previousLocalOffset = page.startOffset - contentStartOffset - 1
    return content.getOrNull(previousLocalOffset)?.let { it != '\n' } ?: true
}

private fun ReaderUiState.pageEndsInsideParagraph(page: ReaderPage): Boolean {
    if (page.endOffset.toLong() >= contentLength) return false
    val lastLocalOffset = page.endOffset - contentStartOffset - 1
    return content.getOrNull(lastLocalOffset)?.let { it != '\n' } ?: true
}
