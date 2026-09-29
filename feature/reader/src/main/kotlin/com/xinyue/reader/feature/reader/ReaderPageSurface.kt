package com.xinyue.reader.feature.reader

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Bundle
import android.text.Selection
import android.text.Spannable
import android.util.TypedValue
import android.view.ActionMode
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.TextView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.xinyue.reader.core.domain.model.AnnotationKind
import com.xinyue.reader.core.domain.model.HighlightColor
import com.xinyue.reader.core.domain.model.ReaderAnnotation
import com.xinyue.reader.core.domain.model.ReaderFocusBandSettings
import com.xinyue.reader.core.domain.model.ReaderSettings
import com.xinyue.reader.core.domain.model.ReaderTapAction
import com.xinyue.reader.core.domain.model.ReaderTextAlignment
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlin.math.abs

data class ReaderSelection(
    val startOffset: Int,
    val endOffset: Int,
    val text: String,
)

sealed interface ReaderSelectionAction {
    data object Bookmark : ReaderSelectionAction
    data class Highlight(val color: HighlightColor) : ReaderSelectionAction
    data object Note : ReaderSelectionAction
}

internal object ReaderSelectionMenuIds {
    const val BOOKMARK = 10_001
    const val HIGHLIGHT_YELLOW = 10_002
    const val HIGHLIGHT_GREEN = 10_003
    const val HIGHLIGHT_BLUE = 10_004
    const val HIGHLIGHT_PINK = 10_005
    const val NOTE = 10_006
}

internal fun readerSelectionActionForMenuItem(itemId: Int): ReaderSelectionAction? = when (itemId) {
    ReaderSelectionMenuIds.BOOKMARK -> ReaderSelectionAction.Bookmark
    ReaderSelectionMenuIds.HIGHLIGHT_YELLOW -> ReaderSelectionAction.Highlight(HighlightColor.YELLOW)
    ReaderSelectionMenuIds.HIGHLIGHT_GREEN -> ReaderSelectionAction.Highlight(HighlightColor.GREEN)
    ReaderSelectionMenuIds.HIGHLIGHT_BLUE -> ReaderSelectionAction.Highlight(HighlightColor.BLUE)
    ReaderSelectionMenuIds.HIGHLIGHT_PINK -> ReaderSelectionAction.Highlight(HighlightColor.PINK)
    ReaderSelectionMenuIds.NOTE -> ReaderSelectionAction.Note
    else -> null
}

@Composable
internal fun ReaderPageSurface(
    text: String,
    pageStartOffset: Int,
    layoutSpec: ReaderLayoutSpec,
    firstParagraphIsContinuation: Boolean,
    lastParagraphIsContinuation: Boolean,
    contentColor: Color,
    backgroundColor: Color,
    annotations: List<ReaderAnnotation>,
    focusBand: ReaderFocusBandSettings,
    tapActions: List<ReaderTapAction>,
    tapInputEnabled: Boolean,
    selectionEnabled: Boolean,
    isCurrentPage: Boolean,
    onTapAction: (ReaderTapAction) -> Unit,
    onSelectionActiveChanged: (Boolean) -> Unit,
    onSelectionAction: (ReaderSelection, ReaderSelectionAction) -> Unit,
    sourceText: String = text,
    chapterTitleRanges: List<ReaderChapterTitleRange> = emptyList(),
    modifier: Modifier = Modifier,
) {
    val applicationContext = LocalContext.current.applicationContext
    var selectionActive by remember { mutableStateOf(false) }
    var selectableView by remember { mutableStateOf<ReaderSelectableTextView?>(null) }
    val styledTextFactory = remember(applicationContext) {
        EntryPointAccessors.fromApplication(
            applicationContext,
            ReaderTypographyEntryPoint::class.java,
        ).styledTextFactory()
    }
    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                ReaderSelectableTextView(context).apply {
                    id = R.id.reader_selectable_text
                    includeFontPadding = false
                    gravity = Gravity.TOP or Gravity.START
                    setPadding(0, 0, 0, 0)
                    selectableView = this
                }
            },
            update = { view ->
                view.pageStartOffset = pageStartOffset
                view.tapActions = tapActions.takeIf { it.size == ReaderSettings.TAP_ZONE_COUNT }
                    ?: ReaderSettings.DEFAULT_TAP_ZONE_ACTIONS
                view.tapInputEnabled = tapInputEnabled
                view.isEnabled = selectionEnabled
                view.isCurrentPage = isCurrentPage
                view.onTapAction = onTapAction
                view.onSelectionActiveChanged = { active ->
                    selectionActive = active
                    onSelectionActiveChanged(active)
                }
                view.onSelectionAction = onSelectionAction
                view.focusBandSettings = focusBand.normalized()
                view.applyPresentation(
                    spec = layoutSpec,
                    contentColorArgb = contentColor.toArgb(),
                    backgroundColorArgb = backgroundColor.toArgb(),
                    styledTextFactory = styledTextFactory,
                )
                view.showPage(
                    pageText = text,
                    sourceText = sourceText,
                    annotations = annotations,
                    spec = layoutSpec,
                    styledTextFactory = styledTextFactory,
                    firstParagraphIsContinuation = firstParagraphIsContinuation,
                    lastParagraphIsContinuation = lastParagraphIsContinuation,
                    titleRanges = chapterTitleRanges,
                )
                view.publishPageProbe(settled = false)
                view.post {
                    view.publishPageProbe(
                        settled = view.isShown && view.layout != null && view.width > 0 && view.height > 0,
                    )
                }
            },
        )
        if (selectionActive) {
            ReaderSelectionActionBar(
                onAction = { action -> selectableView?.performCurrentSelectionAction(action) },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface ReaderTypographyEntryPoint {
    fun styledTextFactory(): ReaderStyledTextFactory
}

internal class ReaderSelectableTextView(context: Context) : TextView(context) {
    var pageStartOffset: Int = 0
    var tapActions: List<ReaderTapAction> = ReaderSettings.DEFAULT_TAP_ZONE_ACTIONS
    var tapInputEnabled: Boolean = true
    var isCurrentPage: Boolean = false
    var onTapAction: (ReaderTapAction) -> Unit = {}
    var onSelectionActiveChanged: (Boolean) -> Unit = {}
    var onSelectionAction: (ReaderSelection, ReaderSelectionAction) -> Unit = { _, _ -> }
    var focusBandSettings: ReaderFocusBandSettings = ReaderFocusBandSettings()
        set(value) {
            field = value.normalized()
            invalidate()
        }

    internal var shownText: String = ""
        private set
    internal var sourceText: String = ""
        private set
    private var lastRenderKey: ReaderPageRenderKey? = null
    private var lastPresentationKey: ReaderPagePresentationKey? = null
    private var lastSelectionActive = false
    private var selectionActionMode: ActionMode? = null
    private var downX = 0f
    private var downY = 0f
    private var downAtMillis = 0L
    private var pageTurnInputEnabledAtDown = false
    private var selectionWasActiveAtDown = false
    private var pendingSwipeAction: ReaderTapAction? = null
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val focusBandPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    internal fun publishPageProbe(settled: Boolean) {
        setTag(
            R.id.reader_page_probe,
            Bundle().apply {
                putInt(PAGE_PROBE_START_KEY, pageStartOffset)
                putInt(PAGE_PROBE_END_KEY, pageStartOffset + shownText.length)
                putInt(PAGE_PROBE_DISPLAY_LENGTH_KEY, shownText.length)
                putBoolean(PAGE_PROBE_SETTLED_KEY, settled)
                putBoolean(PAGE_PROBE_CURRENT_KEY, isCurrentPage)
            },
        )
    }

    init {
        setTextIsSelectable(true)
        isLongClickable = true
        isFocusable = true
        isFocusableInTouchMode = true
        customSelectionActionModeCallback = object : ActionMode.Callback {
            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                selectionActionMode = mode
                menu.ensureReaderSelectionActions()
                post {
                    if (selectionActionMode === mode) publishSelectionActive(true)
                }
                return true
            }

            override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
                return false
            }

            override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                if (item.itemId == android.R.id.copy) {
                    return copyCurrentSelection().also { copied ->
                        if (copied) mode.finish()
                    }
                }
                val action = readerSelectionActionForMenuItem(item.itemId) ?: return false
                val selection = currentSelection() ?: return false
                onSelectionAction(selection, action)
                mode.finish()
                return true
            }

            override fun onDestroyActionMode(mode: ActionMode) {
                if (selectionActionMode === mode) selectionActionMode = null
                notifySelectionActionModeDestroyed()
            }
        }
    }

    fun showPage(
        pageText: String,
        annotations: List<ReaderAnnotation>,
        spec: ReaderLayoutSpec,
        styledTextFactory: ReaderStyledTextFactory,
        firstParagraphIsContinuation: Boolean = false,
        lastParagraphIsContinuation: Boolean = false,
        sourceText: String = pageText,
        titleRanges: List<ReaderChapterTitleRange> = emptyList(),
    ) {
        val safeSourceText = sourceText.takeIf { it.length == pageText.length } ?: pageText
        val renderKey = ReaderPageRenderKey(
            pageText = pageText,
            sourceText = safeSourceText,
            annotations = annotations,
            spec = spec,
            pageStartOffset = pageStartOffset,
            firstParagraphIsContinuation = firstParagraphIsContinuation,
            lastParagraphIsContinuation = lastParagraphIsContinuation,
            titleRanges = titleRanges,
        )
        if (lastRenderKey == renderKey && text is Spannable) return
        clearCurrentSelection()
        shownText = pageText
        this.sourceText = safeSourceText
        lastRenderKey = renderKey
        text = styledTextFactory.create(
            text = pageText,
            spec = spec,
            annotations = annotations,
            textStartOffset = pageStartOffset.toLong(),
            firstParagraphIsContinuation = firstParagraphIsContinuation,
            lastParagraphIsContinuation = lastParagraphIsContinuation,
            titleRanges = titleRanges,
        )
        setTextIsSelectable(true)
        isLongClickable = true
    }

    fun applyPresentation(
        spec: ReaderLayoutSpec,
        contentColorArgb: Int,
        backgroundColorArgb: Int,
        styledTextFactory: ReaderStyledTextFactory,
    ) {
        val presentationKey = ReaderPagePresentationKey(
            spec = spec,
            contentColorArgb = contentColorArgb,
            backgroundColorArgb = backgroundColorArgb,
        )
        if (lastPresentationKey == presentationKey) return
        lastPresentationKey = presentationKey
        setTextColor(contentColorArgb)
        setBackgroundColor(backgroundColorArgb)
        setTextSize(TypedValue.COMPLEX_UNIT_PX, spec.fontSizePx)
        letterSpacing = spec.letterSpacingEm
        typeface = styledTextFactory.resolveTypeface(spec)
        setLineSpacing(spec.lineHeightPx - paint.fontSpacing, 1f)
        justificationMode = if (spec.alignment == ReaderTextAlignment.JUSTIFY) {
            android.text.Layout.JUSTIFICATION_MODE_INTER_WORD
        } else {
            android.text.Layout.JUSTIFICATION_MODE_NONE
        }
    }

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        publishSelectionActive(selStart >= 0 && selEnd >= 0 && selStart != selEnd)
    }

    override fun onDraw(canvas: Canvas) {
        currentFocusBandRect()?.let { rect ->
            focusBandPaint.color = focusBandSettings.effectiveColorArgb()
            canvas.drawRect(0f, rect.topPx, width.toFloat(), rect.bottomPx, focusBandPaint)
        }
        super.onDraw(canvas)
    }

    internal fun currentFocusBandRect(): ReaderFocusBandRect? {
        val textLayout = layout ?: return null
        val lines = (0 until textLayout.lineCount).map { line ->
            ReaderLineBounds(
                topPx = textLayout.getLineTop(line).toFloat(),
                bottomPx = textLayout.getLineBottom(line).toFloat(),
            )
        }
        return calculateReaderFocusBandRect(
            enabled = focusBandSettings.enabled,
            visibleLines = focusBandSettings.visibleLines,
            viewportHeightPx = height.toFloat(),
            lines = lines,
        )
    }

    internal fun setSelection(start: Int, end: Int) {
        val selectableText = text as? Spannable ?: return
        Selection.setSelection(
            selectableText,
            start.coerceIn(0, selectableText.length),
            end.coerceIn(0, selectableText.length),
        )
    }

    internal fun performCurrentSelectionAction(action: ReaderSelectionAction): Boolean {
        val selection = currentSelection() ?: return false
        onSelectionAction(selection, action)
        selectionActionMode?.finish()
        clearCurrentSelection()
        return true
    }

    internal fun notifySelectionActionModeDestroyed() {
        clearCurrentSelection()
    }

    private fun clearCurrentSelection() {
        (text as? Spannable)?.let(Selection::removeSelection)
        publishSelectionActive(false)
    }

    private fun publishSelectionActive(active: Boolean) {
        if (lastSelectionActive == active) return
        lastSelectionActive = active
        onSelectionActiveChanged(active)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pageTurnInputEnabledAtDown = tapInputEnabled
                selectionWasActiveAtDown = selectionStart != selectionEnd || selectionActionMode != null
                pendingSwipeAction = null
                if (pageTurnInputEnabledAtDown && !selectionWasActiveAtDown) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
                downX = event.x
                downY = event.y
                downAtMillis = event.eventTime
            }
            MotionEvent.ACTION_MOVE -> {
                if (pageTurnInputEnabledAtDown && !selectionWasActiveAtDown && selectionActionMode == null) {
                    val swipeAction = readerSwipePageAction(
                        downX = downX,
                        downY = downY,
                        upX = event.x,
                        upY = event.y,
                        viewportWidth = width.toFloat(),
                        touchSlop = touchSlop.toFloat(),
                    )
                    if (swipeAction != null) {
                        pendingSwipeAction = swipeAction
                        cancelLongPress()
                        clearCurrentSelection()
                        return true
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                val swipeAction = pendingSwipeAction ?: if (
                    pageTurnInputEnabledAtDown && !selectionWasActiveAtDown && selectionActionMode == null
                ) {
                    readerSwipePageAction(
                        downX = downX,
                        downY = downY,
                        upX = event.x,
                        upY = event.y,
                        viewportWidth = width.toFloat(),
                        touchSlop = touchSlop.toFloat(),
                    )
                } else {
                    null
                }
                if (swipeAction != null) {
                    cancelLongPress()
                    clearCurrentSelection()
                    pendingSwipeAction = null
                    dispatchTapAction(swipeAction)
                    return true
                }
                val isTap = event.eventTime - downAtMillis < ViewConfiguration.getLongPressTimeout() &&
                    abs(event.x - downX) <= touchSlop && abs(event.y - downY) <= touchSlop
                if (
                    pageTurnInputEnabledAtDown && !selectionWasActiveAtDown &&
                    isTap && selectionStart == selectionEnd && width > 0 && height > 0
                ) {
                    val column = (event.x / (width / 3f)).toInt().coerceIn(0, 2)
                    val row = (event.y / (height / 3f)).toInt().coerceIn(0, 2)
                    dispatchTapAction(tapActions[row * 3 + column])
                }
                pendingSwipeAction = null
            }
            MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                pendingSwipeAction = null
            }
        }
        return super.onTouchEvent(event)
    }

    private fun dispatchTapAction(action: ReaderTapAction) {
        onTapAction(action)
    }
}

internal fun readerSwipePageAction(
    downX: Float,
    downY: Float,
    upX: Float,
    upY: Float,
    viewportWidth: Float,
    touchSlop: Float,
): ReaderTapAction? {
    if (viewportWidth <= 0f) return null
    val horizontalDistance = upX - downX
    val verticalDistance = upY - downY
    val minimumDistance = maxOf(touchSlop * 4f, viewportWidth * 0.12f)
    if (abs(horizontalDistance) < minimumDistance) return null
    if (abs(horizontalDistance) <= abs(verticalDistance) * 1.25f) return null
    return if (horizontalDistance < 0f) {
        ReaderTapAction.NEXT_PAGE
    } else {
        ReaderTapAction.PREVIOUS_PAGE
    }
}

private data class ReaderPageRenderKey(
    val pageText: String,
    val sourceText: String,
    val annotations: List<ReaderAnnotation>,
    val spec: ReaderLayoutSpec,
    val pageStartOffset: Int,
    val firstParagraphIsContinuation: Boolean,
    val lastParagraphIsContinuation: Boolean,
    val titleRanges: List<ReaderChapterTitleRange>,
)

private data class ReaderPagePresentationKey(
    val spec: ReaderLayoutSpec,
    val contentColorArgb: Int,
    val backgroundColorArgb: Int,
)

private const val PAGE_PROBE_START_KEY = "startOffset"
private const val PAGE_PROBE_END_KEY = "endOffset"
private const val PAGE_PROBE_DISPLAY_LENGTH_KEY = "displayLength"
private const val PAGE_PROBE_SETTLED_KEY = "settled"
private const val PAGE_PROBE_CURRENT_KEY = "currentPage"

internal fun ReaderSelectableTextView.currentSelection(): ReaderSelection? {
    val start = minOf(selectionStart, selectionEnd).coerceAtLeast(0)
    val end = maxOf(selectionStart, selectionEnd).coerceAtMost(shownText.length)
    if (start >= end) return null
    return ReaderSelection(
        startOffset = pageStartOffset + start,
        endOffset = pageStartOffset + end,
        text = sourceText.takeIf { it.length == shownText.length }
            ?.substring(start, end)
            ?: shownText.substring(start, end),
    )
}

internal fun ReaderSelectableTextView.copyCurrentSelection(): Boolean {
    val selected = currentSelection() ?: return false
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(
        ClipData.newPlainText(context.getString(R.string.reader_copy_label), selected.text),
    )
    return true
}

private fun Menu.ensureReaderSelectionActions(): Boolean {
    var changed = false
    changed = addOnce(ReaderSelectionMenuIds.BOOKMARK, 100, R.string.reader_bookmark_selection) || changed
    changed = addOnce(ReaderSelectionMenuIds.HIGHLIGHT_YELLOW, 101, R.string.reader_highlight_yellow) || changed
    changed = addOnce(ReaderSelectionMenuIds.HIGHLIGHT_GREEN, 102, R.string.reader_highlight_green) || changed
    changed = addOnce(ReaderSelectionMenuIds.HIGHLIGHT_BLUE, 103, R.string.reader_highlight_blue) || changed
    changed = addOnce(ReaderSelectionMenuIds.HIGHLIGHT_PINK, 104, R.string.reader_highlight_pink) || changed
    changed = addOnce(ReaderSelectionMenuIds.NOTE, 105, R.string.reader_note_selection) || changed
    return changed
}

private fun Menu.addOnce(itemId: Int, order: Int, titleRes: Int): Boolean {
    if (findItem(itemId) != null) return false
    add(0, itemId, order, titleRes)
    return true
}
