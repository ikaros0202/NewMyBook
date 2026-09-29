package com.xinyue.reader.core.text

data class ExtractedBookMetadata(
    val title: String,
    val author: String?,
    val seriesName: String? = null,
    val seriesOrder: Int? = null,
)

object TxtMetadataExtractor {
    private val titleField = Regex("^(?:书名|作品名|小说名|TITLE)\\s*[:：]\\s*(.+)$", RegexOption.IGNORE_CASE)
    private val authorField = Regex("^(?:作\\s*者|著者|AUTHOR)\\s*[:：]\\s*(.+)$", RegexOption.IGNORE_CASE)
    private val seriesField = Regex("^(?:系列|丛书|SERIES)\\s*[:：]\\s*(.+)$", RegexOption.IGNORE_CASE)
    private val seriesOrderField = Regex("^(?:卷序|序号|VOLUME)\\s*[:：]\\s*([0-9０-９]+)$", RegexOption.IGNORE_CASE)
    private val inlineAuthor = Regex("^(?:文|著)\\s*[/／:：]\\s*(.+)$")
    private val chapterHeading = Regex(
        "^(?:第[0-9０-９一二三四五六七八九十百千万零〇两]+[章节卷回部篇]|Chapter\\s+\\d+|序章|楔子|引子|尾声|后记|番外)",
        RegexOption.IGNORE_CASE,
    )

    fun extract(text: String, fallbackTitle: String): ExtractedBookMetadata {
        val lines = text.lineSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .take(MAX_SCAN_LINES)
            .toList()
        val explicitTitle = lines.firstNotNullOfOrNull { line ->
            titleField.matchEntire(line)?.groupValues?.get(1)?.cleanTitle()
        }
        val author = lines.firstNotNullOfOrNull { line ->
            val raw = authorField.matchEntire(line)?.groupValues?.get(1)
                ?: inlineAuthor.matchEntire(line)?.groupValues?.get(1)
            raw?.cleanAuthor()
        }
        val seriesName = lines.firstNotNullOfOrNull { line ->
            seriesField.matchEntire(line)?.groupValues?.get(1)?.cleanSeriesName()
        }
        val seriesOrder = lines.firstNotNullOfOrNull { line ->
            seriesOrderField.matchEntire(line)?.groupValues?.get(1)?.parseSeriesOrder()
        }
        val headingTitle = lines.firstOrNull()?.takeIf { line ->
            line.length in 2..MAX_HEADING_UNITS &&
                !chapterHeading.containsMatchIn(line) &&
                titleField.matchEntire(line) == null &&
                authorField.matchEntire(line) == null &&
                seriesField.matchEntire(line) == null &&
                seriesOrderField.matchEntire(line) == null &&
                inlineAuthor.matchEntire(line) == null &&
                !line.startsWith("http", ignoreCase = true)
        }?.cleanTitle()
        return ExtractedBookMetadata(
            title = explicitTitle ?: headingTitle ?: fallbackTitle.cleanTitle().ifBlank { "未命名书籍" },
            author = author,
            seriesName = seriesName,
            seriesOrder = seriesOrder,
        )
    }

    private fun String.cleanTitle(): String = trim()
        .removeSurrounding("《", "》")
        .removeSurrounding("〈", "〉")
        .removeSurrounding("\"", "\"")
        .trim()
        .take(MAX_TITLE_UNITS)

    private fun String.cleanAuthor(): String? = trim()
        .removePrefix("作者")
        .removeSuffix("著")
        .trim(' ', '：', ':', '《', '》')
        .take(MAX_AUTHOR_UNITS)
        .takeIf(String::isNotBlank)

    private fun String.cleanSeriesName(): String? = trim()
        .removeSurrounding("《", "》")
        .removeSurrounding("〈", "〉")
        .trim()
        .take(MAX_SERIES_NAME_UNITS)
        .takeIf(String::isNotBlank)

    private fun String.parseSeriesOrder(): Int? = map { character ->
        when (character) {
            in '０'..'９' -> '0' + (character - '０')
            else -> character
        }
    }.joinToString(separator = "")
        .toIntOrNull()
        ?.takeIf { it in MIN_SERIES_ORDER..MAX_SERIES_ORDER }

    private const val MAX_SCAN_LINES = 80
    private const val MAX_HEADING_UNITS = 40
    private const val MAX_TITLE_UNITS = 100
    private const val MAX_AUTHOR_UNITS = 80
    private const val MAX_SERIES_NAME_UNITS = 100
    private const val MIN_SERIES_ORDER = 0
    private const val MAX_SERIES_ORDER = 9_999
}
