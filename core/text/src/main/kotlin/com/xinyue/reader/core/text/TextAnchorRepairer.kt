package com.xinyue.reader.core.text

import com.xinyue.reader.core.domain.model.TextAnchor
import com.xinyue.reader.core.domain.model.TextRangeAnchor
import kotlin.math.abs

sealed interface AnchorRepairResult {
    data class Exact(
        val startOffset: Long,
        val endOffset: Long,
    ) : AnchorRepairResult

    data class Repaired(
        val startOffset: Long,
        val endOffset: Long,
        val distance: Long,
    ) : AnchorRepairResult

    data class Unresolved(val reason: AnchorRepairFailure) : AnchorRepairResult
}

enum class AnchorRepairFailure {
    MISSING_FINGERPRINT,
    OUTSIDE_WINDOW,
    NO_MATCH,
    AMBIGUOUS,
}

object TextAnchorRepairer {
    fun repairPoint(
        windowText: String,
        windowStartOffset: Long,
        anchor: TextAnchor,
    ): AnchorRepairResult {
        if (anchor.prefix.isEmpty() && anchor.suffix.isEmpty()) {
            return AnchorRepairResult.Unresolved(AnchorRepairFailure.MISSING_FINGERPRINT)
        }
        val expectedStart = localOffsetOrNull(
            absoluteOffset = anchor.offset,
            windowStartOffset = windowStartOffset,
            windowLength = windowText.length,
        ) ?: return AnchorRepairResult.Unresolved(AnchorRepairFailure.OUTSIDE_WINDOW)

        return repair(
            windowText = windowText,
            windowStartOffset = windowStartOffset,
            expectedStart = expectedStart,
            selectedUtf16Length = 0,
            fingerprint = TextFingerprint(
                prefix = anchor.prefix,
                suffix = anchor.suffix,
                selectedSha256 = null,
            ),
        )
    }

    fun repairRange(
        windowText: String,
        windowStartOffset: Long,
        anchor: TextRangeAnchor,
    ): AnchorRepairResult {
        if (anchor.prefix.isEmpty() && anchor.suffix.isEmpty() && anchor.selectedSha256 == null) {
            return AnchorRepairResult.Unresolved(AnchorRepairFailure.MISSING_FINGERPRINT)
        }
        val selectedLength = anchor.endOffset - anchor.startOffset
        if (selectedLength > Int.MAX_VALUE) {
            return AnchorRepairResult.Unresolved(AnchorRepairFailure.OUTSIDE_WINDOW)
        }
        val expectedStart = localOffsetOrNull(
            absoluteOffset = anchor.startOffset,
            windowStartOffset = windowStartOffset,
            windowLength = windowText.length,
        ) ?: return AnchorRepairResult.Unresolved(AnchorRepairFailure.OUTSIDE_WINDOW)
        val expectedEnd = localOffsetOrNull(
            absoluteOffset = anchor.endOffset,
            windowStartOffset = windowStartOffset,
            windowLength = windowText.length,
        ) ?: return AnchorRepairResult.Unresolved(AnchorRepairFailure.OUTSIDE_WINDOW)
        if (expectedEnd - expectedStart != selectedLength.toInt()) {
            return AnchorRepairResult.Unresolved(AnchorRepairFailure.OUTSIDE_WINDOW)
        }

        return repair(
            windowText = windowText,
            windowStartOffset = windowStartOffset,
            expectedStart = expectedStart,
            selectedUtf16Length = selectedLength.toInt(),
            fingerprint = TextFingerprint(
                prefix = anchor.prefix,
                suffix = anchor.suffix,
                selectedSha256 = anchor.selectedSha256,
            ),
        )
    }

    private fun repair(
        windowText: String,
        windowStartOffset: Long,
        expectedStart: Int,
        selectedUtf16Length: Int,
        fingerprint: TextFingerprint,
    ): AnchorRepairResult {
        if (selectedUtf16Length > windowText.length) {
            return AnchorRepairResult.Unresolved(AnchorRepairFailure.NO_MATCH)
        }
        val lastStart = windowText.length - selectedUtf16Length
        val matches = buildList {
            for (candidate in 0..lastStart) {
                if (TextFingerprint.matchesAt(windowText, candidate, selectedUtf16Length, fingerprint)) {
                    add(candidate)
                }
            }
        }
        if (matches.isEmpty()) {
            return AnchorRepairResult.Unresolved(AnchorRepairFailure.NO_MATCH)
        }

        val minimumDistance = matches.minOf { abs(it - expectedStart) }
        val nearestMatches = matches.filter { abs(it - expectedStart) == minimumDistance }
        if (nearestMatches.size != 1) {
            return AnchorRepairResult.Unresolved(AnchorRepairFailure.AMBIGUOUS)
        }

        val repairedStart = windowStartOffset + nearestMatches.single()
        val repairedEnd = repairedStart + selectedUtf16Length
        return if (minimumDistance == 0) {
            AnchorRepairResult.Exact(repairedStart, repairedEnd)
        } else {
            AnchorRepairResult.Repaired(repairedStart, repairedEnd, minimumDistance.toLong())
        }
    }

    private fun localOffsetOrNull(
        absoluteOffset: Long,
        windowStartOffset: Long,
        windowLength: Int,
    ): Int? {
        if (windowStartOffset < 0 || absoluteOffset < windowStartOffset) return null
        val localOffset = absoluteOffset - windowStartOffset
        return localOffset.takeIf { it <= windowLength }?.toInt()
    }
}
