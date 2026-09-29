package com.xinyue.reader.core.data

import com.xinyue.reader.core.domain.model.AnnotationExportFormat
import com.xinyue.reader.core.domain.model.AnnotationKind
import com.xinyue.reader.core.domain.model.HighlightColor
import java.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class AnnotationExportDocument(
    val schemaVersion: Int = 1,
    val exportedAtEpochMillis: Long,
    val includeBookmarks: Boolean,
    val books: List<AnnotationExportBook>,
)

@Serializable
data class AnnotationExportBook(
    val bookId: String,
    val title: String,
    val author: String?,
    val seriesName: String?,
    val seriesOrder: Int?,
    val chapters: List<AnnotationExportChapter>,
)

@Serializable
data class AnnotationExportChapter(
    val title: String,
    val startOffset: Long,
    val annotations: List<AnnotationExportItem>,
)

@Serializable
data class AnnotationExportItem(
    val id: String,
    val kind: AnnotationKind,
    val startOffset: Long,
    val endOffset: Long,
    val selectedText: String,
    val selectedTextTruncated: Boolean,
    val contextBefore: String,
    val contextAfter: String,
    val note: String?,
    val color: HighlightColor?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

class AnnotationExportFormatter(
    private val json: Json = Json {
        prettyPrint = true
        encodeDefaults = true
        explicitNulls = true
    },
) {
    fun render(document: AnnotationExportDocument, format: AnnotationExportFormat): String = when (format) {
        AnnotationExportFormat.JSON -> json.encodeToString(document)
        AnnotationExportFormat.MARKDOWN -> renderMarkdown(document)
    }

    private fun renderMarkdown(document: AnnotationExportDocument): String = buildString {
        appendLine("# 阅读批注导出")
        appendLine()
        appendLine("- 格式版本：${document.schemaVersion}")
        appendLine("- 导出时间：${Instant.ofEpochMilli(document.exportedAtEpochMillis)}")
        appendLine("- 包含书签：${if (document.includeBookmarks) "是" else "否"}")
        document.books.forEach { book ->
            appendLine()
            appendLine("## ${book.title.markdownEscaped()}")
            book.author?.let { appendLine("- 作者：${it.markdownEscaped()}") }
            book.seriesName?.let { series ->
                val order = book.seriesOrder?.let { " · 第 $it 卷" }.orEmpty()
                appendLine("- 系列：${series.markdownEscaped()}$order")
            }
            book.chapters.forEach { chapter ->
                appendLine()
                appendLine("### ${chapter.title.markdownEscaped()}")
                chapter.annotations.forEach { annotation ->
                    appendLine()
                    appendLine("#### ${annotation.kind.displayName()} · 位置：${annotation.startOffset}–${annotation.endOffset}")
                    appendQuoted("摘录", annotation.selectedText)
                    appendQuoted("上文", annotation.contextBefore)
                    appendQuoted("下文", annotation.contextAfter)
                    annotation.note?.takeIf(String::isNotBlank)?.let {
                        appendLine("批注：${it.markdownEscaped()}")
                    }
                    annotation.color?.let { appendLine("颜色：${it.name}") }
                    appendLine("创建：${Instant.ofEpochMilli(annotation.createdAtEpochMillis)}")
                    appendLine("更新：${Instant.ofEpochMilli(annotation.updatedAtEpochMillis)}")
                }
            }
        }
    }

    private fun StringBuilder.appendQuoted(label: String, value: String) {
        if (value.isBlank()) return
        value.lines().forEachIndexed { index, line ->
            appendLine("> ${if (index == 0) "$label：" else ""}${line.markdownEscaped()}")
        }
    }
}

private fun AnnotationKind.displayName(): String = when (this) {
    AnnotationKind.BOOKMARK -> "书签"
    AnnotationKind.HIGHLIGHT -> "高亮"
    AnnotationKind.NOTE -> "批注"
}

private fun String.markdownEscaped(): String = buildString(length) {
    this@markdownEscaped.forEach { character ->
        if (character in "\\`*_{}[]()#+-.!|>") append('\\')
        append(character)
    }
}
