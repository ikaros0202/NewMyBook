package com.xinyue.reader.feature.reader

import android.text.Layout
import android.graphics.text.LineBreaker
import android.text.StaticLayout
import android.os.Build
import android.widget.TextView

/**
 * Keeps the platform layout knobs identical while a page is measured and displayed.
 *
 * Android chooses different defaults for some TextView configurations.  A reader page must
 * use one deterministic policy because a page boundary calculated by [StaticLayout] is later
 * rendered by a TextView.  Simple breaking and disabled hyphenation are appropriate for the
 * local TXT reader's CJK-first text and avoid platform-dependent reflow at a page boundary.
 * Fallback line spacing is also enabled on both paths. TextView enables it by default on API 28+
 * while StaticLayout.Builder defaults it off, which can otherwise make CJK fallback metrics
 * consume different vertical space during pagination and display.
 */
internal object ReaderTextLayoutPolicy {
    const val HYPHENATION_FREQUENCY: Int = Layout.HYPHENATION_FREQUENCY_NONE

    fun configure(builder: StaticLayout.Builder): StaticLayout.Builder {
        val configured = builder
            .setBreakStrategy(LineBreaker.BREAK_STRATEGY_SIMPLE)
            .setHyphenationFrequency(HYPHENATION_FREQUENCY)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            configured.setUseLineSpacingFromFallbacks(true)
        }
        return configured
    }

    fun configure(view: TextView) {
        view.includeFontPadding = false
        view.breakStrategy = LineBreaker.BREAK_STRATEGY_SIMPLE
        view.hyphenationFrequency = HYPHENATION_FREQUENCY
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            view.setFallbackLineSpacing(true)
        }
    }
}
