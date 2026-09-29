package com.xinyue.reader.core.data

import com.google.common.truth.Truth.assertThat
import com.xinyue.reader.core.domain.model.AnnotationExportFormat
import com.xinyue.reader.core.domain.model.AnnotationKind
import com.xinyue.reader.core.domain.model.HighlightColor
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

class AnnotationExportFormatterTest {
    @Test
    fun `markdown groups by book and chapter escapes syntax and preserves bounded context`() {
        val markdown = AnnotationExportFormatter().render(document(), AnnotationExportFormat.MARKDOWN)

        assertThat(markdown).contains("# 阅读批注导出")
        assertThat(markdown).contains("## A \\*title\\*")
        assertThat(markdown).contains("### 第一章")
        assertThat(markdown).contains("位置：12–20")
        assertThat(markdown).contains("> 摘录：line \\*one\\*")
        assertThat(markdown).contains("> 上文：before")
        assertThat(markdown).contains("> 下文：after")
        assertThat(markdown).contains("批注：important \\[note\\]")
    }

    @Test
    fun `json is versioned and carries stable annotation identity`() {
        val jsonText = AnnotationExportFormatter().render(document(), AnnotationExportFormat.JSON)
        val root = Json.parseToJsonElement(jsonText).jsonObject

        assertThat(root.getValue("schemaVersion").jsonPrimitive.content).isEqualTo("1")
        assertThat(jsonText).contains("\"id\": \"annotation-1\"")
        assertThat(jsonText).contains("\"kind\": \"NOTE\"")
        assertThat(jsonText).contains("\"selectedTextTruncated\": false")
    }

    private fun document() = AnnotationExportDocument(
        exportedAtEpochMillis = 1_700_000_000_000,
        includeBookmarks = false,
        books = listOf(
            AnnotationExportBook(
                bookId = "book-1",
                title = "A *title*",
                author = "林川",
                seriesName = "星海纪事",
                seriesOrder = 2,
                chapters = listOf(
                    AnnotationExportChapter(
                        title = "第一章",
                        startOffset = 0,
                        annotations = listOf(
                            AnnotationExportItem(
                                id = "annotation-1",
                                kind = AnnotationKind.NOTE,
                                startOffset = 12,
                                endOffset = 20,
                                selectedText = "line *one*",
                                selectedTextTruncated = false,
                                contextBefore = "before",
                                contextAfter = "after",
                                note = "important [note]",
                                color = HighlightColor.YELLOW,
                                createdAtEpochMillis = 100,
                                updatedAtEpochMillis = 200,
                            ),
                        ),
                    ),
                ),
            ),
        ),
    )
}
