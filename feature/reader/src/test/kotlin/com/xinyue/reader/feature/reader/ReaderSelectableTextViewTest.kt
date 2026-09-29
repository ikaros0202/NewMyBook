package com.xinyue.reader.feature.reader

import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.xinyue.reader.core.domain.model.HighlightColor
import com.xinyue.reader.core.domain.model.ReaderFocusBandSettings
import com.xinyue.reader.core.domain.model.ReaderTapAction
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReaderSelectableTextViewTest {
    private val styledTextFactory = ReaderStyledTextFactory(ReaderTypefaceResolver.forTests())
    private val layoutSpec = ReaderLayoutSpec(320, 480, 30f, 48f, firstLineIndentPx = 60f)

    @Test
    fun `horizontal text gestures map to page turns without stealing taps or vertical drags`() {
        assertThat(readerSwipePageAction(280f, 200f, 40f, 210f, 320f, 8f))
            .isEqualTo(ReaderTapAction.NEXT_PAGE)
        assertThat(readerSwipePageAction(40f, 200f, 280f, 190f, 320f, 8f))
            .isEqualTo(ReaderTapAction.PREVIOUS_PAGE)
        assertThat(readerSwipePageAction(160f, 200f, 170f, 202f, 320f, 8f)).isNull()
        assertThat(readerSwipePageAction(160f, 80f, 180f, 360f, 320f, 8f)).isNull()
    }

    @Test
    fun `horizontal page swipe wins over transient text selection created during the drag`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val actions = mutableListOf<ReaderTapAction>()
        val view = ReaderSelectableTextView(context).apply {
            layoutParams = ViewGroup.LayoutParams(320, 480)
            tapInputEnabled = true
            onTapAction = actions::add
            onSelectionActiveChanged = { active -> tapInputEnabled = !active }
            showPage("0123456789".repeat(20), emptyList(), layoutSpec, styledTextFactory)
        }
        view.measure(
            View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(480, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, 320, 480)
        val downAt = SystemClock.uptimeMillis()

        view.onTouchEvent(MotionEvent.obtain(downAt, downAt, MotionEvent.ACTION_DOWN, 280f, 200f, 0))
        view.setSelection(0, 5)
        view.onTouchEvent(MotionEvent.obtain(downAt, downAt + 20, MotionEvent.ACTION_MOVE, 40f, 205f, 0))
        view.onTouchEvent(MotionEvent.obtain(downAt, downAt + 40, MotionEvent.ACTION_UP, 40f, 205f, 0))

        assertThat(actions).containsExactly(ReaderTapAction.NEXT_PAGE)
        assertThat(view.currentSelection()).isNull()
    }

    @Test
    fun `copy selection uses the Android clipboard`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val view = ReaderSelectableTextView(context)
        view.showPage("可复制正文", emptyList(), layoutSpec, styledTextFactory)
        view.setSelection(0, 3)

        assertThat(view.copyCurrentSelection()).isTrue()
        val clip = context.getSystemService(ClipboardManager::class.java).primaryClip
        assertThat(clip?.getItemAt(0)?.coerceToText(context).toString()).isEqualTo("可复制")
    }

    @Test
    fun `selection and copy return raw source whitespace behind a display placeholder`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val view = ReaderSelectableTextView(context).apply {
            pageStartOffset = 200
            showPage(
                pageText = "a\u2060b",
                sourceText = "a\nb",
                annotations = emptyList(),
                spec = layoutSpec,
                styledTextFactory = styledTextFactory,
            )
            setSelection(0, 3)
        }

        assertThat(view.currentSelection())
            .isEqualTo(ReaderSelection(startOffset = 200, endOffset = 203, text = "a\nb"))
        assertThat(view.copyCurrentSelection()).isTrue()
        val clip = context.getSystemService(ClipboardManager::class.java).primaryClip
        assertThat(clip?.getItemAt(0)?.coerceToText(context).toString()).isEqualTo("a\nb")
    }

    @Test
    fun `stable menu ids map to every annotation action`() {
        assertThat(readerSelectionActionForMenuItem(ReaderSelectionMenuIds.BOOKMARK))
            .isEqualTo(ReaderSelectionAction.Bookmark)
        assertThat(readerSelectionActionForMenuItem(ReaderSelectionMenuIds.HIGHLIGHT_YELLOW))
            .isEqualTo(ReaderSelectionAction.Highlight(HighlightColor.YELLOW))
        assertThat(readerSelectionActionForMenuItem(ReaderSelectionMenuIds.HIGHLIGHT_GREEN))
            .isEqualTo(ReaderSelectionAction.Highlight(HighlightColor.GREEN))
        assertThat(readerSelectionActionForMenuItem(ReaderSelectionMenuIds.HIGHLIGHT_BLUE))
            .isEqualTo(ReaderSelectionAction.Highlight(HighlightColor.BLUE))
        assertThat(readerSelectionActionForMenuItem(ReaderSelectionMenuIds.HIGHLIGHT_PINK))
            .isEqualTo(ReaderSelectionAction.Highlight(HighlightColor.PINK))
        assertThat(readerSelectionActionForMenuItem(ReaderSelectionMenuIds.NOTE))
            .isEqualTo(ReaderSelectionAction.Note)
        assertThat(readerSelectionActionForMenuItem(Int.MIN_VALUE)).isNull()
    }

    @Test
    fun `selection lifecycle is idempotent and ends when the page changes`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val events = mutableListOf<Boolean>()
        val view = ReaderSelectableTextView(context).apply {
            onSelectionActiveChanged = events::add
            showPage("第一页正文", emptyList(), layoutSpec, styledTextFactory)
        }

        view.setSelection(0, 3)
        view.setSelection(0, 3)
        view.showPage("第二页正文", emptyList(), layoutSpec, styledTextFactory)

        assertThat(events).containsExactly(true, false).inOrder()
    }

    @Test
    fun `ending action mode emits inactive only once`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val events = mutableListOf<Boolean>()
        val view = ReaderSelectableTextView(context).apply {
            onSelectionActiveChanged = events::add
            showPage("选择正文", emptyList(), layoutSpec, styledTextFactory)
            setSelection(0, 2)
        }

        view.notifySelectionActionModeDestroyed()
        view.notifySelectionActionModeDestroyed()

        assertThat(events).containsExactly(true, false).inOrder()
    }

    @Test
    fun `application selection action dispatches current range and clears selection`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val actions = mutableListOf<Pair<ReaderSelection, ReaderSelectionAction>>()
        val active = mutableListOf<Boolean>()
        val view = ReaderSelectableTextView(context).apply {
            pageStartOffset = 40
            onSelectionAction = { selection, action -> actions += selection to action }
            onSelectionActiveChanged = active::add
            showPage("选择正文", emptyList(), layoutSpec, styledTextFactory)
            setSelection(0, 2)
        }

        assertThat(
            view.performCurrentSelectionAction(
                ReaderSelectionAction.Highlight(HighlightColor.YELLOW),
            ),
        ).isTrue()
        assertThat(actions).containsExactly(
            ReaderSelection(startOffset = 40, endOffset = 42, text = "选择") to
                ReaderSelectionAction.Highlight(HighlightColor.YELLOW),
        )
        assertThat(view.currentSelection()).isNull()
        assertThat(active).containsExactly(true, false).inOrder()
    }

    @Test
    fun `styled rendering keeps absolute selection offsets stable`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val view = ReaderSelectableTextView(context).apply {
            pageStartOffset = 40
            showPage("第一段。\n第二段。", emptyList(), layoutSpec, styledTextFactory)
            setSelection(5, 8)
        }

        assertThat(view.text.toString()).isEqualTo("第一段。\n第二段。")
        assertThat(view.currentSelection())
            .isEqualTo(ReaderSelection(startOffset = 45, endOffset = 48, text = "第二段"))
    }

    @Test
    fun `focus band draws inside the text surface without changing selection offsets`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val view = ReaderSelectableTextView(context).apply {
            layoutParams = ViewGroup.LayoutParams(320, 480)
            pageStartOffset = 100
            focusBandSettings = ReaderFocusBandSettings(
                enabled = true,
                visibleLines = 3,
                colorArgb = 0xFF336699,
                opacity = 0.5f,
            )
            showPage("第一行\n第二行\n第三行\n第四行\n第五行", emptyList(), layoutSpec, styledTextFactory)
        }
        view.measure(
            View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(480, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, 320, 480)
        view.draw(Canvas(Bitmap.createBitmap(320, 480, Bitmap.Config.ARGB_8888)))
        view.setSelection(4, 7)

        assertThat(view.currentFocusBandRect()).isNotNull()
        assertThat(view.currentSelection())
            .isEqualTo(ReaderSelection(startOffset = 104, endOffset = 107, text = "第二行"))
    }
}
