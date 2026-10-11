package com.xinyue.reader.feature.reader

internal data class ReaderSpeechSegment(val startOffset: Int, val endOffset: Int, val text: String)

/** Re-resolve an absolute continuation after a window replacement or a layout change. */
internal fun ReaderUiState.speechSegmentAt(offset: Int): ReaderSpeechSegment? {
    val page = pages.firstOrNull { offset >= it.startOffset && offset < it.endOffset } ?: return null
    val text = textFor(page).drop(offset - page.startOffset)
    return text.takeIf { it.isNotEmpty() }?.let { ReaderSpeechSegment(offset, page.endOffset, it) }
}
