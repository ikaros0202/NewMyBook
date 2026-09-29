package com.xinyue.reader.core.data

import com.xinyue.reader.core.database.dao.AnnotationDao
import com.xinyue.reader.core.database.entity.AnnotationEntity
import com.xinyue.reader.core.domain.model.AnnotationKind
import com.xinyue.reader.core.domain.model.HighlightColor
import com.xinyue.reader.core.domain.model.ReaderAnnotation
import com.xinyue.reader.core.domain.model.TextRangeAnchor
import com.xinyue.reader.core.domain.repository.AnnotationRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class RoomAnnotationRepository @Inject constructor(
    private val dao: AnnotationDao,
) : AnnotationRepository {
    override fun observe(bookId: String): Flow<List<ReaderAnnotation>> =
        dao.observeForBook(bookId).map { annotations -> annotations.map(AnnotationEntity::toDomain) }

    override fun observe(bookId: String, kind: AnnotationKind): Flow<List<ReaderAnnotation>> =
        dao.observeForBookAndKind(bookId, kind.name)
            .map { annotations -> annotations.map(AnnotationEntity::toDomain) }

    override suspend fun getForBook(bookId: String): List<ReaderAnnotation> =
        dao.getForBook(bookId).map(AnnotationEntity::toDomain)

    override suspend fun get(annotationId: String): ReaderAnnotation? = dao.get(annotationId)?.toDomain()

    override suspend fun findOverlapping(
        bookId: String,
        startOffset: Long,
        endOffset: Long,
    ): List<ReaderAnnotation> = dao.findOverlapping(bookId, startOffset, endOffset).map(AnnotationEntity::toDomain)

    override suspend fun upsert(annotation: ReaderAnnotation) {
        dao.upsert(annotation.toEntity())
    }

    override suspend fun delete(annotationId: String) {
        dao.delete(annotationId)
    }
}

private fun ReaderAnnotation.toEntity() = AnnotationEntity(
    id = id,
    bookId = bookId,
    kind = kind.name,
    startOffset = range.startOffset,
    endOffset = range.endOffset,
    prefix = range.prefix,
    suffix = range.suffix,
    selectedSha256 = range.selectedSha256,
    color = color?.name,
    note = note,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
)

private fun AnnotationEntity.toDomain() = ReaderAnnotation(
    id = id,
    bookId = bookId,
    kind = AnnotationKind.valueOf(kind),
    range = TextRangeAnchor(
        startOffset = startOffset,
        endOffset = endOffset,
        prefix = prefix,
        suffix = suffix,
        selectedSha256 = selectedSha256,
    ),
    color = color?.let(HighlightColor::valueOf),
    note = note,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
)
