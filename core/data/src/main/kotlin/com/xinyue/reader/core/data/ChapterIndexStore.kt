package com.xinyue.reader.core.data

import com.xinyue.reader.core.database.dao.ChapterIndexDao
import com.xinyue.reader.core.database.entity.ChapterEntity
import com.xinyue.reader.core.domain.time.EpochClock
import com.xinyue.reader.core.text.ChapterRuleSet
import com.xinyue.reader.core.text.DetectedChapter
import javax.inject.Inject
import javax.inject.Singleton

data class ChapterIndexSnapshot(
    val chapters: List<DetectedChapter> = emptyList(),
    val ruleSet: ChapterRuleSet = ChapterRuleSet.STANDARD,
    val manuallyEdited: Boolean = false,
)

interface ChapterIndexStore {
    suspend fun getSnapshot(bookId: String): ChapterIndexSnapshot

    suspend fun replaceAutomatically(
        bookId: String,
        chapters: List<DetectedChapter>,
        ruleSet: ChapterRuleSet,
    ): Boolean

    suspend fun replaceManually(bookId: String, chapters: List<DetectedChapter>)

    suspend fun replaceForRuleChange(
        bookId: String,
        chapters: List<DetectedChapter>,
        ruleSet: ChapterRuleSet,
    )
}

@Singleton
class RoomChapterIndexStore @Inject constructor(
    private val chapterIndexDao: ChapterIndexDao,
    private val clock: EpochClock,
) : ChapterIndexStore {
    override suspend fun getSnapshot(bookId: String): ChapterIndexSnapshot {
        val config = chapterIndexDao.getConfig(bookId)
        return ChapterIndexSnapshot(
            chapters = chapterIndexDao.getChapters(bookId)
                .map { DetectedChapter(it.title, it.startOffset) },
            ruleSet = config?.ruleSet.toRuleSet(),
            manuallyEdited = config?.manuallyEdited ?: false,
        )
    }

    override suspend fun replaceAutomatically(
        bookId: String,
        chapters: List<DetectedChapter>,
        ruleSet: ChapterRuleSet,
    ): Boolean = chapterIndexDao.replaceAutomatically(
        bookId = bookId,
        chapters = chapters.toEntities(bookId),
        ruleSet = ruleSet.name,
        updatedAtEpochMillis = clock.nowEpochMillis(),
    )

    override suspend fun replaceManually(bookId: String, chapters: List<DetectedChapter>) {
        chapterIndexDao.replaceManually(
            bookId = bookId,
            chapters = chapters.toEntities(bookId),
            fallbackRuleSet = ChapterRuleSet.STANDARD.name,
            updatedAtEpochMillis = clock.nowEpochMillis(),
        )
    }

    override suspend fun replaceForRuleChange(
        bookId: String,
        chapters: List<DetectedChapter>,
        ruleSet: ChapterRuleSet,
    ) {
        chapterIndexDao.replaceForRuleChange(
            bookId = bookId,
            chapters = chapters.toEntities(bookId),
            ruleSet = ruleSet.name,
            updatedAtEpochMillis = clock.nowEpochMillis(),
        )
    }
}

private fun List<DetectedChapter>.toEntities(bookId: String): List<ChapterEntity> =
    map { ChapterEntity(bookId, it.startOffset, it.title) }

private fun String?.toRuleSet(): ChapterRuleSet =
    runCatching { ChapterRuleSet.valueOf(this ?: ChapterRuleSet.STANDARD.name) }
        .getOrDefault(ChapterRuleSet.STANDARD)
