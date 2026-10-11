package com.xinyue.reader.feature.reader

import android.content.Context
import android.graphics.Paint
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.Spannable
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.xinyue.reader.core.domain.model.ReaderTextAlignment
import com.xinyue.reader.core.text.ReaderLayoutWhitespaceNormalizer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs against the platform TextView. The API 37 reader fixture uses the same kind of title,
 * paragraph boundary and dense CJK text that exposed the original bottom-line clipping.
 */
@RunWith(AndroidJUnit4::class)
class ReaderPageLayoutDeviceTest {
    @Test
    fun paginatorPagesKeepFinalRenderedLineInsideViewport() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val styledTextFactory = ReaderStyledTextFactory(ReaderTypefaceResolver.forTests())
        val rawText = buildString {
            append("第一章 潮退以后\n")
            repeat(160) {
                append("林澄沿着旧港口往前走，潮声在石阶下缓慢回响，远处的灯塔穿过雾气。\n")
            }
            append("\n\u3000她正想问路，蓝色仓门忽然从里面打开。")
        }
        val layoutText = ReaderLayoutWhitespaceNormalizer.normalize(rawText)
        val title = "第一章 潮退以后"
        val titleRanges = listOf(ReaderChapterTitleRange(0, title.length))
        val displayMetrics = context.resources.displayMetrics
        val scaledDensity = displayMetrics.scaledDensity
        val specs = listOf(
            ReaderLayoutSpec(
                widthPx = 954,
                heightPx = 2035,
                fontSizePx = 20f * scaledDensity,
                lineHeightPx = 20f * 1.6f * scaledDensity,
            ),
            ReaderLayoutSpec(
                widthPx = 954,
                heightPx = 2035,
                fontSizePx = 26f * scaledDensity,
                lineHeightPx = 26f * 1.8f * scaledDensity,
            ),
        )

        val instrumentation = InstrumentationRegistry.getInstrumentation()
        specs.forEach { spec ->
            val pages = AndroidTextPaginator().paginateWindowWithTitleRanges(
                text = layoutText,
                spec = spec,
                startOffset = 0,
                endOffset = layoutText.length,
                titleRanges = titleRanges,
            )

            pages.forEach { page ->
                var measured: RenderedPageMetrics? = null
                var failure: Throwable? = null
                instrumentation.runOnMainSync {
                    try {
                        measured = measurePageOnMainThread(
                            context = context,
                            styledTextFactory = styledTextFactory,
                            layoutText = layoutText,
                            sourceText = rawText,
                            page = page,
                            spec = spec,
                            titleRanges = titleRanges,
                        )
                    } catch (throwable: Throwable) {
                        failure = throwable
                    }
                }
                failure?.let { throwable ->
                    throw AssertionError("could not measure page ${page.startOffset} on main thread", throwable)
                }
                val metrics = requireNotNull(measured)
                assertTrue("page ${page.startOffset} has no rendered lines", metrics.lastLine >= 0)
                Log.i(
                    "ReaderPageLayout",
                    "spec=${spec.fontSizePx}sp-like/${spec.lineHeightPx}px " +
                        "page=${page.startOffset}-${page.endOffset} " +
                        "viewport=${metrics.viewportWidth}x${metrics.viewportHeight} " +
                        "layout=${metrics.layoutWidth}x${metrics.layoutHeight} " +
                        "lines=${metrics.lineCount} last=${metrics.lastLineTop}.." +
                        "${metrics.lastLineBottom} fontSpacing=${metrics.fontSpacing} " +
                        "break=${metrics.breakStrategy} hyphenation=${metrics.hyphenationFrequency} " +
                        "fallback=${metrics.fallbackLineSpacing} " +
                        "staticLegacy=${metrics.legacyLayout} staticFallback=${metrics.fallbackLayout}",
                )
                assertTrue(
                    "TextView line count ${metrics.lineCount} differs from fallback-enabled " +
                        "StaticLayout ${metrics.fallbackLayout.lineCount}",
                    metrics.lineCount == metrics.fallbackLayout.lineCount,
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    assertTrue(
                        "TextView fallback line spacing was not enabled",
                        metrics.fallbackLineSpacing == true,
                    )
                }
                assertTrue(
                    "TextView height ${metrics.layoutHeight} differs from fallback-enabled " +
                        "StaticLayout ${metrics.fallbackLayout.height}",
                    metrics.layoutHeight == metrics.fallbackLayout.height,
                )
                assertTrue(
                    "TextView last line bottom ${metrics.lastLineBottom} differs from " +
                        "fallback-enabled StaticLayout ${metrics.fallbackLayout.lastLineBottom}",
                    metrics.lastLineBottom == metrics.fallbackLayout.lastLineBottom,
                )
                assertTrue(
                    "page ${page.startOffset} last line bottom ${metrics.lastLineBottom} " +
                        "exceeds viewport ${metrics.viewportHeight}",
                    metrics.lastLine >= 0 && metrics.lastLineBottom <= metrics.viewportHeight,
                )
            }
        }
    }

    private fun measurePageOnMainThread(
        context: Context,
        styledTextFactory: ReaderStyledTextFactory,
        layoutText: String,
        sourceText: String,
        page: ReaderPage,
        spec: ReaderLayoutSpec,
        titleRanges: List<ReaderChapterTitleRange>,
    ): RenderedPageMetrics {
        val pageText = layoutText.substring(page.startOffset, page.endOffset)
        val firstParagraphIsContinuation = page.startOffset > 0 &&
            layoutText[page.startOffset - 1] != '\n'
        val lastParagraphIsContinuation = page.endOffset < layoutText.length &&
            layoutText[page.endOffset - 1] != '\n'
        val view = ReaderSelectableTextView(context).apply {
            layoutParams = ViewGroup.LayoutParams(spec.widthPx, spec.heightPx)
            pageStartOffset = page.startOffset
            applyPresentation(
                spec = spec,
                contentColorArgb = 0xFF222222.toInt(),
                backgroundColorArgb = 0xFFFFFFFF.toInt(),
                styledTextFactory = styledTextFactory,
            )
            showPage(
                pageText = pageText,
                sourceText = sourceText.substring(page.startOffset, page.endOffset),
                annotations = emptyList(),
                spec = spec,
                styledTextFactory = styledTextFactory,
                firstParagraphIsContinuation = firstParagraphIsContinuation,
                lastParagraphIsContinuation = lastParagraphIsContinuation,
                titleRanges = titleRanges,
            )
        }
        view.measure(
            View.MeasureSpec.makeMeasureSpec(spec.widthPx, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(spec.heightPx, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, spec.widthPx, spec.heightPx)

        val textLayout = requireNotNull(view.layout)
        val lastLine = textLayout.lineCount - 1
        val styledPage = styledTextFactory.create(
            text = pageText,
            spec = spec,
            textStartOffset = page.startOffset.toLong(),
            firstParagraphIsContinuation = firstParagraphIsContinuation,
            lastParagraphIsContinuation = lastParagraphIsContinuation,
            titleRanges = titleRanges,
        )
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            styledTextFactory.configurePaint(this, spec)
        }
        val lineSpacingAdd = styledTextFactory.lineSpacingAdd(paint, spec)
        val legacyLayout = buildStaticLayout(
            styledPage = styledPage,
            paint = paint,
            spec = spec,
            lineSpacingAdd = lineSpacingAdd,
            useFallbackLineSpacing = false,
        )
        val fallbackLayout = buildStaticLayout(
            styledPage = styledPage,
            paint = paint,
            spec = spec,
            lineSpacingAdd = lineSpacingAdd,
            useFallbackLineSpacing = true,
        )
        return RenderedPageMetrics(
            viewportWidth = view.width,
            viewportHeight = view.height,
            layoutWidth = textLayout.width,
            layoutHeight = textLayout.height,
            lineCount = textLayout.lineCount,
            lastLine = lastLine,
            lastLineTop = if (lastLine >= 0) textLayout.getLineTop(lastLine) else 0,
            lastLineBottom = if (lastLine >= 0) textLayout.getLineBottom(lastLine) else 0,
            fontSpacing = view.paint.fontSpacing,
            breakStrategy = view.breakStrategy,
            hyphenationFrequency = view.hyphenationFrequency,
            fallbackLineSpacing = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                view.isFallbackLineSpacing
            } else {
                null
            },
            legacyLayout = layoutMetrics(legacyLayout),
            fallbackLayout = layoutMetrics(fallbackLayout),
        )
    }

    private fun buildStaticLayout(
        styledPage: Spannable,
        paint: TextPaint,
        spec: ReaderLayoutSpec,
        lineSpacingAdd: Float,
        useFallbackLineSpacing: Boolean,
    ): StaticLayout {
        val builder = ReaderTextLayoutPolicy.configure(
            StaticLayout.Builder.obtain(styledPage, 0, styledPage.length, paint, spec.widthPx),
        )
            .setAlignment(spec.alignment.toLayoutAlignment())
            .setIncludePad(false)
            .setLineSpacing(lineSpacingAdd, 1f)
            .setJustificationMode(
                if (spec.alignment == ReaderTextAlignment.JUSTIFY) {
                    Layout.JUSTIFICATION_MODE_INTER_WORD
                } else {
                    Layout.JUSTIFICATION_MODE_NONE
                },
            )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            builder.setUseLineSpacingFromFallbacks(useFallbackLineSpacing)
        }
        return builder.build()
    }

    private fun layoutMetrics(layout: StaticLayout): StaticLayoutMetrics {
        val lastLine = layout.lineCount - 1
        return StaticLayoutMetrics(
            height = layout.height,
            lineCount = layout.lineCount,
            lastLineTop = if (lastLine >= 0) layout.getLineTop(lastLine) else 0,
            lastLineBottom = if (lastLine >= 0) layout.getLineBottom(lastLine) else 0,
        )
    }

    private data class StaticLayoutMetrics(
        val height: Int,
        val lineCount: Int,
        val lastLineTop: Int,
        val lastLineBottom: Int,
    )

    private data class RenderedPageMetrics(
        val viewportWidth: Int,
        val viewportHeight: Int,
        val layoutWidth: Int,
        val layoutHeight: Int,
        val lineCount: Int,
        val lastLine: Int,
        val lastLineTop: Int,
        val lastLineBottom: Int,
        val fontSpacing: Float,
        val breakStrategy: Int,
        val hyphenationFrequency: Int,
        val fallbackLineSpacing: Boolean?,
        val legacyLayout: StaticLayoutMetrics,
        val fallbackLayout: StaticLayoutMetrics,
    )
}
