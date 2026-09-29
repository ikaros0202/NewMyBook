package com.xinyue.reader.feature.reader

import com.google.common.truth.Truth.assertThat
import com.xinyue.reader.core.domain.model.AnnotationKind
import com.xinyue.reader.core.domain.model.HighlightColor
import com.xinyue.reader.core.domain.model.ReaderAnnotation
import com.xinyue.reader.core.domain.model.TextRangeAnchor
import org.junit.Test

class ReaderNotesTest {
    @Test
    fun `notes include written notes but never duplicate plain bookmarks`() {
        val bookmark = annotation("bookmark", AnnotationKind.BOOKMARK, null)
        val highlight = annotation("highlight", AnnotationKind.HIGHLIGHT, null)
        val notedHighlight = annotation("noted-highlight", AnnotationKind.HIGHLIGHT, "重要线索")
        val note = annotation("note", AnnotationKind.NOTE, "回头再看")

        assertThat(readerNotes(listOf(bookmark, highlight, notedHighlight, note)))
            .containsExactly(notedHighlight, note)
            .inOrder()
    }

    private fun annotation(
        id: String,
        kind: AnnotationKind,
        note: String?,
    ) = ReaderAnnotation(
        id = id,
        bookId = "book",
        kind = kind,
        range = TextRangeAnchor(10, 20, "", "", null),
        color = if (kind == AnnotationKind.HIGHLIGHT) HighlightColor.YELLOW else null,
        note = note,
        createdAtEpochMillis = 1,
        updatedAtEpochMillis = 1,
    )
}
