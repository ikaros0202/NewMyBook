package com.xinyue.reader.core.text

data class DetectedChapter(
    val title: String,
    val startOffset: Int,
)

enum class ChapterRuleSet {
    STANDARD,
    BROAD,
    NUMBERED,
}

object ChapterDetector {
    private const val MAX_TITLE_CHARACTERS = 80
    private const val CHINESE_NUMBER = "0-9零一二三四五六七八九十百千万两〇○"
    private const val HORIZONTAL_SPACE = "\\t \\u3000"
    private const val ORDINAL_HEADING =
        "第[$CHINESE_NUMBER]+[章节卷部回集篇](?:[$HORIZONTAL_SPACE]*[^\\r\\n]{0,60})?"
    private const val BROAD_HEADING =
        "(?:序章|楔子|前言|引子|序言|终章|尾声|后记)(?:[$HORIZONTAL_SPACE]+[^\\r\\n]{1,60})?"
    private const val NUMBERED_HEADING =
        "(?:[0-9]{1,4}(?:[、．)）][$HORIZONTAL_SPACE]*|\\.[$HORIZONTAL_SPACE]+)[^\\r\\n]{1,60}|" +
            "Chapter[$HORIZONTAL_SPACE]+[0-9]{1,4}(?:[$HORIZONTAL_SPACE]+[^\\r\\n]{1,60})?)"

    private val patterns = ChapterRuleSet.entries.associateWith { ruleSet ->
        val body = when (ruleSet) {
            ChapterRuleSet.STANDARD -> ORDINAL_HEADING
            ChapterRuleSet.BROAD -> "(?:$ORDINAL_HEADING|$BROAD_HEADING)"
            ChapterRuleSet.NUMBERED -> "(?:$ORDINAL_HEADING|$BROAD_HEADING|$NUMBERED_HEADING)"
        }
        Regex(
            pattern = "^[$HORIZONTAL_SPACE]*($body)[$HORIZONTAL_SPACE]*$",
            options = setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE),
        )
    }

    fun detect(
        text: String,
        ruleSet: ChapterRuleSet = ChapterRuleSet.STANDARD,
    ): List<DetectedChapter> {
        val chapters = patterns.getValue(ruleSet).findAll(text).map { match ->
            val titleGroup = match.groups[1] ?: match.groups[0]!!
            DetectedChapter(
                title = titleGroup.value.trim(),
                startOffset = titleGroup.range.first,
            )
        }.filter { chapter -> chapter.title.length <= MAX_TITLE_CHARACTERS }
            .toList()

        return chapters.ifEmpty {
            listOf(DetectedChapter(title = "正文", startOffset = 0))
        }
    }
}
