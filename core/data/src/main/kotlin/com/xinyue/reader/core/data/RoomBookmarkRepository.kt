package com.xinyue.reader.core.data

import com.xinyue.reader.core.database.dao.AnnotationDao
import com.xinyue.reader.core.database.entity.AnnotationEntity
import com.xinyue.reader.core.domain.model.Bookmark
import com.xinyue.reader.core.domain.repository.BookmarkRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class RoomBookmarkRepository @Inject constructor(
    private val dao: AnnotationDao,
) : BookmarkRepository {
    override fun observe(bookId: String): Flow<List<Bookmark>> =
        dao.observeForBookAndKind(bookId, "BOOKMARK").map { entities ->
            entities.map { entity ->
                Bookmark(entity.id, entity.bookId, entity.startOffset, entity.note, entity.createdAtEpochMillis)
            }
        }

    override suspend fun add(bookmark: Bookmark) {
        dao.upsert(
            AnnotationEntity(
                id = bookmark.id,
                bookId = bookmark.bookId,
                kind = "BOOKMARK",
                startOffset = bookmark.offset,
                endOffset = bookmark.offset,
                prefix = "",
                suffix = "",
                selectedSha256 = null,
                color = null,
                note = bookmark.note,
                createdAtEpochMillis = bookmark.createdAtEpochMillis,
                updatedAtEpochMillis = bookmark.createdAtEpochMillis,
            ),
        )
    }

    override suspend fun delete(bookmarkId: String) {
        dao.delete(bookmarkId)
    }
}
