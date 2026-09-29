package com.xinyue.reader.core.data

import com.xinyue.reader.core.domain.repository.AnnotationRepository
import com.xinyue.reader.core.domain.repository.BookRepository
import com.xinyue.reader.core.text.AnchorRepairFailure
import com.xinyue.reader.core.text.AnchorRepairResult
import com.xinyue.reader.core.text.TextAnchorRepairer
import javax.inject.Inject
import javax.inject.Singleton

data class AnchorRepairReport(
    val progressResult: AnchorRepairResult?,
    val repairedAnnotationIds: Set<String>,
    val unresolvedAnnotationIds: Set<String>,
)

@Singleton
class AnchorRepairCoordinator @Inject constructor(
    private val bookRepository: BookRepository,
    private val annotationRepository: AnnotationRepository,
    private val textSource: TextSource,
) {
    suspend fun repairBook(bookId: String): AnchorRepairReport {
        val book = requireNotNull(bookRepository.getBook(bookId)) { "找不到需要修复锚点的书籍" }
        val progress = bookRepository.getProgress(bookId)
        val progressResult = progress?.let { savedProgress ->
            if (savedProgress.anchor.prefix.isEmpty() && savedProgress.anchor.suffix.isEmpty()) {
                return@let AnchorRepairResult.Unresolved(AnchorRepairFailure.MISSING_FINGERPRINT)
            }
            val window = textSource.readWindow(
                normalizedPath = book.normalizedPath,
                anchorOffset = savedProgress.anchor.offset,
                beforeUtf16Units = REPAIR_WINDOW_BEFORE,
                afterUtf16Units = REPAIR_WINDOW_AFTER,
            )
            TextAnchorRepairer.repairPoint(
                windowText = window.text,
                windowStartOffset = window.startOffset,
                anchor = savedProgress.anchor,
            ).also { result ->
                if (result is AnchorRepairResult.Repaired) {
                    bookRepository.saveProgress(
                        savedProgress.copy(
                            anchor = savedProgress.anchor.copy(offset = result.startOffset),
                            contentLength = window.totalUtf16Length,
                        ),
                    )
                }
            }
        }

        val repairedAnnotationIds = linkedSetOf<String>()
        val unresolvedAnnotationIds = linkedSetOf<String>()
        annotationRepository.getForBook(bookId).forEach { annotation ->
            if (annotation.range.prefix.isEmpty() && annotation.range.suffix.isEmpty() &&
                annotation.range.selectedSha256 == null
            ) {
                unresolvedAnnotationIds += annotation.id
                return@forEach
            }
            val window = textSource.readWindow(
                normalizedPath = book.normalizedPath,
                anchorOffset = annotation.range.startOffset,
                beforeUtf16Units = REPAIR_WINDOW_BEFORE,
                afterUtf16Units = REPAIR_WINDOW_AFTER,
            )
            when (
                val result = TextAnchorRepairer.repairRange(
                    windowText = window.text,
                    windowStartOffset = window.startOffset,
                    anchor = annotation.range,
                )
            ) {
                is AnchorRepairResult.Exact -> Unit
                is AnchorRepairResult.Repaired -> {
                    annotationRepository.upsert(
                        annotation.copy(
                            range = annotation.range.copy(
                                startOffset = result.startOffset,
                                endOffset = result.endOffset,
                            ),
                        ),
                    )
                    repairedAnnotationIds += annotation.id
                }
                is AnchorRepairResult.Unresolved -> unresolvedAnnotationIds += annotation.id
            }
        }

        return AnchorRepairReport(
            progressResult = progressResult,
            repairedAnnotationIds = repairedAnnotationIds,
            unresolvedAnnotationIds = unresolvedAnnotationIds,
        )
    }

    companion object {
        const val REPAIR_WINDOW_BEFORE = 128 * 1024
        const val REPAIR_WINDOW_AFTER = 256 * 1024
    }
}
