package com.xinyue.reader.core.text

import com.google.common.truth.Truth.assertThat
import com.xinyue.reader.core.domain.model.TextAnchor
import com.xinyue.reader.core.domain.model.TextRangeAnchor
import org.junit.Test

class TextAnchorRepairerTest {
    @Test
    fun `exact point returns its unchanged absolute offset`() {
        val text = "甲乙丙丁戊己"
        val fingerprint = TextFingerprint.capture(text, 3, 3)

        val result = TextAnchorRepairer.repairPoint(
            windowText = text,
            windowStartOffset = 100,
            anchor = TextAnchor(103, "legacy", fingerprint.prefix, fingerprint.suffix),
        )

        assertThat(result).isEqualTo(AnchorRepairResult.Exact(103, 103))
    }

    @Test
    fun `insertion before point returns the nearest shifted offset`() {
        val original = "甲乙目标后文"
        val fingerprint = TextFingerprint.capture(original, 2, 2)

        val result = TextAnchorRepairer.repairPoint(
            windowText = "新增$original",
            windowStartOffset = 0,
            anchor = TextAnchor(2, "legacy", fingerprint.prefix, fingerprint.suffix),
        )

        assertThat(result).isEqualTo(AnchorRepairResult.Repaired(4, 4, distance = 2))
    }

    @Test
    fun `shifted range preserves selected text and surrogate pair boundaries`() {
        val original = "前文😀目标后文"
        val start = original.indexOf("😀")
        val end = original.indexOf("后文")
        val fingerprint = TextFingerprint.capture(original, start, end)
        val shifted = "新增$original"

        val result = TextAnchorRepairer.repairRange(
            windowText = shifted,
            windowStartOffset = 0,
            anchor = TextRangeAnchor(
                startOffset = start.toLong(),
                endOffset = end.toLong(),
                prefix = fingerprint.prefix,
                suffix = fingerprint.suffix,
                selectedSha256 = fingerprint.selectedSha256,
            ),
        )

        val repaired = result as AnchorRepairResult.Repaired
        assertThat(repaired).isEqualTo(
            AnchorRepairResult.Repaired(start + 2L, end + 2L, distance = 2),
        )
        assertThat(shifted.substring(repaired.startOffset.toInt(), repaired.endOffset.toInt()))
            .isEqualTo("😀目标")
    }

    @Test
    fun `equal distance point candidates are unresolved as ambiguous`() {
        val result = TextAnchorRepairer.repairPoint(
            windowText = "aXb---aXb",
            windowStartOffset = 0,
            anchor = TextAnchor(offset = 4, contextHash = "legacy", prefix = "a", suffix = "Xb"),
        )

        assertThat(result).isEqualTo(
            AnchorRepairResult.Unresolved(AnchorRepairFailure.AMBIGUOUS),
        )
    }

    @Test
    fun `equal distance selected range candidates are unresolved as ambiguous`() {
        val selectedHash = TextFingerprint.capture("a目标b", 1, 3).selectedSha256
        val result = TextAnchorRepairer.repairRange(
            windowText = "a目标b--a目标b",
            windowStartOffset = 0,
            anchor = TextRangeAnchor(
                startOffset = 4,
                endOffset = 6,
                prefix = "a",
                suffix = "b",
                selectedSha256 = selectedHash,
            ),
        )

        assertThat(result).isEqualTo(
            AnchorRepairResult.Unresolved(AnchorRepairFailure.AMBIGUOUS),
        )
    }

    @Test
    fun `changed selected text returns no match`() {
        val fingerprint = TextFingerprint.capture("前文目标后文", 2, 4)

        val result = TextAnchorRepairer.repairRange(
            windowText = "前文改变后文",
            windowStartOffset = 0,
            anchor = TextRangeAnchor(
                startOffset = 2,
                endOffset = 4,
                prefix = fingerprint.prefix,
                suffix = fingerprint.suffix,
                selectedSha256 = fingerprint.selectedSha256,
            ),
        )

        assertThat(result).isEqualTo(
            AnchorRepairResult.Unresolved(AnchorRepairFailure.NO_MATCH),
        )
    }

    @Test
    fun `anchor outside supplied window is not clamped into that window`() {
        val result = TextAnchorRepairer.repairPoint(
            windowText = "窗口正文",
            windowStartOffset = 100,
            anchor = TextAnchor(99, "legacy", prefix = "窗", suffix = "口"),
        )

        assertThat(result).isEqualTo(
            AnchorRepairResult.Unresolved(AnchorRepairFailure.OUTSIDE_WINDOW),
        )
    }

    @Test
    fun `legacy point without context remains unresolved`() {
        val result = TextAnchorRepairer.repairPoint(
            windowText = "窗口正文",
            windowStartOffset = 0,
            anchor = TextAnchor(2, "legacy"),
        )

        assertThat(result).isEqualTo(
            AnchorRepairResult.Unresolved(AnchorRepairFailure.MISSING_FINGERPRINT),
        )
    }
}
