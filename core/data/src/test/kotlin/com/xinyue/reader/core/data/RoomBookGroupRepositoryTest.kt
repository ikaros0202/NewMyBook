package com.xinyue.reader.core.data

import com.google.common.truth.Truth.assertThat
import com.xinyue.reader.core.database.dao.BookDao
import com.xinyue.reader.core.database.dao.BookGroupDao
import com.xinyue.reader.core.database.entity.BookEntity
import com.xinyue.reader.core.database.entity.BookGroupEntity
import com.xinyue.reader.core.database.entity.BookGroupMembershipEntity
import com.xinyue.reader.core.database.entity.PendingFileCleanupEntity
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertFailsWith

class RoomBookGroupRepositoryTest {
    @Test
    fun `create trims names validates Unicode length creates UUIDs and appends sort order`() = runTest {
        val groupDao = FakeBookGroupDao()
        val repository = RoomBookGroupRepository(groupDao, FakeBookDao())

        val first = repository.create("  科幻  ")
        val second = repository.create("历史")

        assertThat(first.name).isEqualTo("科幻")
        assertThat(UUID.fromString(first.id).toString()).isEqualTo(first.id)
        assertThat(first.sortOrder).isEqualTo(0)
        assertThat(second.sortOrder).isEqualTo(1)
        assertFailsWith<IllegalArgumentException> { repository.create("   ") }
        assertFailsWith<IllegalArgumentException> { repository.create("𠀀".repeat(41)) }
    }

    @Test
    fun `create and rename reject case insensitive duplicates while rename preserves order`() = runTest {
        val groupDao = FakeBookGroupDao()
        val repository = RoomBookGroupRepository(groupDao, FakeBookDao())
        val first = repository.create("SciFi")
        val second = repository.create("历史")

        assertFailsWith<IllegalArgumentException> { repository.create(" scifi ") }
        assertFailsWith<IllegalArgumentException> { repository.rename(second.id, "SCIFI") }

        repository.rename(first.id, "  科幻  ")
        val renamed = requireNotNull(groupDao.get(first.id))
        assertThat(renamed.name).isEqualTo("科幻")
        assertThat(renamed.sortOrder).isEqualTo(first.sortOrder)
        assertThat(groupDao.observableGroups.value.map(BookGroupEntity::id))
            .containsExactly(first.id, second.id).inOrder()
    }

    @Test
    fun `batch move validates every ID and group deletion only unassigns books`() = runTest {
        val bookDao = FakeBookDao(setOf("book-1", "book-2"))
        val groupDao = FakeBookGroupDao(bookDao)
        val repository = RoomBookGroupRepository(groupDao, bookDao)
        val group = repository.create("分组")

        repository.moveBooks(setOf("book-1", "book-2"), group.id)
        assertThat(bookDao.groupByBook).containsExactly("book-1", group.id, "book-2", group.id)
        assertFailsWith<IllegalArgumentException> {
            repository.moveBooks(setOf("book-1", "missing"), group.id)
        }
        assertFailsWith<IllegalArgumentException> {
            repository.moveBooks(setOf("book-1"), "missing-group")
        }

        repository.delete(group.id)
        assertThat(groupDao.get(group.id)).isNull()
        assertThat(bookDao.bookIds).containsExactly("book-1", "book-2")
        assertThat(bookDao.groupByBook.values).containsExactly(null, null)
    }

    @Test
    fun `missing groups fail and cancellation is never converted to validation`() = runTest {
        val repository = RoomBookGroupRepository(FakeBookGroupDao(), FakeBookDao())
        assertFailsWith<IllegalArgumentException> { repository.rename("missing", "新名称") }
        assertFailsWith<IllegalArgumentException> { repository.delete("missing") }

        val cancellation = CancellationException("cancel group read")
        val cancellingRepository = RoomBookGroupRepository(
            FakeBookGroupDao(observeFailure = cancellation),
            FakeBookDao(),
        )
        assertThat(assertFailsWith<CancellationException> { cancellingRepository.create("新分组") })
            .isSameInstanceAs(cancellation)
    }

    @Test
    fun `one book can belong to multiple collections without replacing earlier memberships`() = runTest {
        val bookDao = FakeBookDao(setOf("book-1"))
        val groupDao = FakeBookGroupDao(bookDao)
        val repository = RoomBookGroupRepository(groupDao, bookDao)
        val scienceFiction = repository.create("科幻")
        val favorites = repository.create("收藏")

        repository.addBooksToCollections(
            bookIds = setOf("book-1"),
            collectionIds = setOf(scienceFiction.id, favorites.id),
        )

        assertThat(repository.observeMemberships().first().map { it.collectionId })
            .containsExactly(scienceFiction.id, favorites.id)
        repository.removeBooksFromCollections(setOf("book-1"), setOf(scienceFiction.id))
        assertThat(repository.observeMemberships().first().map { it.collectionId })
            .containsExactly(favorites.id)

        repository.delete(favorites.id)
        assertThat(repository.observeMemberships().first()).isEmpty()
        assertThat(bookDao.bookIds).containsExactly("book-1")
    }

    private class FakeBookGroupDao(
        private val books: FakeBookDao? = null,
        private val observeFailure: Throwable? = null,
    ) : BookGroupDao() {
        private val groups = MutableStateFlow<List<BookGroupEntity>>(emptyList())
        private val memberships = MutableStateFlow<List<BookGroupMembershipEntity>>(emptyList())

        override fun observeAll(): Flow<List<BookGroupEntity>> = observeFailure?.let { failure ->
            flow { throw failure }
        } ?: groups

        override suspend fun getAllForBackup(): List<BookGroupEntity> = groups.value
        override fun observeMemberships(): Flow<List<BookGroupMembershipEntity>> = memberships
        override suspend fun getAllMembershipsForBackup(): List<BookGroupMembershipEntity> = memberships.value

        val observableGroups: MutableStateFlow<List<BookGroupEntity>> get() = groups

        override suspend fun get(groupId: String): BookGroupEntity? = groups.value.firstOrNull { it.id == groupId }

        override suspend fun maxSortOrder(): Int = groups.value.maxOfOrNull(BookGroupEntity::sortOrder) ?: -1

        override suspend fun insertStored(group: BookGroupEntity) {
            groups.value = (groups.value + group).sortedWith(groupComparator)
        }

        override suspend fun renameStored(groupId: String, name: String, updatedAtEpochMillis: Long) {
            groups.value = groups.value.map {
                if (it.id == groupId) it.copy(name = name, updatedAtEpochMillis = updatedAtEpochMillis) else it
            }.sortedWith(groupComparator)
        }

        override suspend fun insertMembershipsStored(newMemberships: List<BookGroupMembershipEntity>) {
            memberships.value = (memberships.value + newMemberships)
                .distinct()
                .sortedWith(compareBy(BookGroupMembershipEntity::bookId, BookGroupMembershipEntity::groupId))
        }

        override suspend fun deleteMembershipsStored(bookIds: Set<String>, groupIds: Set<String>) {
            memberships.value = memberships.value.filterNot {
                it.bookId in bookIds && it.groupId in groupIds
            }
        }

        override suspend fun deleteAllMembershipsForBooksStored(bookIds: Set<String>) {
            memberships.value = memberships.value.filterNot { it.bookId in bookIds }
        }

        override suspend fun replaceMemberships(
            bookIds: Set<String>,
            memberships: List<BookGroupMembershipEntity>,
        ) {
            super.replaceMemberships(bookIds, memberships)
            books?.move(bookIds, memberships.map(BookGroupMembershipEntity::groupId).distinct().singleOrNull())
        }

        override suspend fun deleteStored(groupId: String) {
            groups.value = groups.value.filterNot { it.id == groupId }
            memberships.value = memberships.value.filterNot { it.groupId == groupId }
            books?.unassign(groupId)
        }

        private companion object {
            val groupComparator = compareBy<BookGroupEntity>(BookGroupEntity::sortOrder)
                .thenBy(BookGroupEntity::createdAtEpochMillis)
                .thenBy(BookGroupEntity::id)
        }
    }

    private class FakeBookDao(initialIds: Set<String> = emptySet()) : BookDao {
        val bookIds = initialIds.toMutableSet()
        val groupByBook = initialIds.associateWith { null as String? }.toMutableMap()

        fun move(ids: Set<String>, groupId: String?) {
            ids.forEach { groupByBook[it] = groupId }
        }

        fun unassign(groupId: String) {
            groupByBook.keys.toList().forEach { bookId ->
                if (groupByBook[bookId] == groupId) groupByBook[bookId] = null
            }
        }

        override suspend fun countByIds(bookIds: Set<String>): Int = bookIds.count(this.bookIds::contains)
        override suspend fun insert(book: BookEntity) { bookIds += book.id }
        override fun observeAll(): Flow<List<BookEntity>> = flow { emit(emptyList()) }
        override suspend fun getAllForBackup(): List<BookEntity> = emptyList()
        override suspend fun get(bookId: String): BookEntity? = null
        override suspend fun getMany(bookIds: Set<String>): List<BookEntity> = emptyList()
        override suspend fun findBySha256(contentSha256: String): BookEntity? = null
        override suspend fun rename(bookId: String, title: String) = Unit
        override suspend fun setCustomCoverPath(bookId: String, customCoverPath: String?): Int =
            if (bookId in bookIds) 1 else 0
        override suspend fun updateFinished(bookIds: Set<String>, finished: Boolean) = Unit
        override suspend fun delete(bookId: String) { bookIds -= bookId; groupByBook -= bookId }
        override suspend fun deleteMany(bookIds: Set<String>) { this.bookIds -= bookIds; bookIds.forEach(groupByBook::remove) }
        override suspend fun queueFileCleanup(cleanup: PendingFileCleanupEntity) = Unit
        override suspend fun update(book: BookEntity) = Unit
        override suspend fun deleteChapterIndex(bookId: String) = Unit
        override suspend fun markOpened(bookId: String, epochMillis: Long) = Unit
    }
}
