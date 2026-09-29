package com.xinyue.reader.core.data

import com.google.common.truth.Truth.assertThat
import com.xinyue.reader.core.database.entity.AnnotationEntity
import com.xinyue.reader.core.database.entity.BookEntity
import com.xinyue.reader.core.database.entity.BookGroupEntity
import com.xinyue.reader.core.database.entity.BookGroupMembershipEntity
import com.xinyue.reader.core.database.entity.BookReaderOverridesEntity
import com.xinyue.reader.core.database.entity.ImportedFontEntity
import com.xinyue.reader.core.database.entity.ReaderGlobalSettingsEntity
import com.xinyue.reader.core.database.entity.ReaderThemeEntity
import com.xinyue.reader.core.database.entity.ReadingDailyStatEntity
import com.xinyue.reader.core.database.entity.ReadingProgressEntity
import com.xinyue.reader.core.database.entity.ReadingSessionEntity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import org.junit.Test

class RoomBackupCatalogDataSourceTest {
    @Test
    fun `snapshot maps every schema twelve user table in stable order without operational data`() = runTest {
        val snapshot = RoomBackupCatalogDataSource.forTest(queries = FakeQueries()).snapshot()

        assertThat(snapshot.books.single().id).isEqualTo("book-1")
        assertThat(snapshot.books.single().customCoverAssetPath).isEqualTo("assets/books/book-1/cover.webp")
        assertThat(snapshot.books.single().seriesName).isEqualTo("公开系列")
        assertThat(snapshot.books.single().seriesOrder).isEqualTo(2)
        assertThat(snapshot.groups.single().id).isEqualTo("group-1")
        assertThat(snapshot.memberships).containsExactly(BackupBookMembershipRecord("book-1", "group-1"))
        assertThat(snapshot.progress.single().offset).isEqualTo(7)
        assertThat(snapshot.annotations.single().id).isEqualTo("annotation-1")
        assertThat(snapshot.globalSettings!!.settingsJson).isEqualTo("{\"fontSizeSp\":24}")
        assertThat(snapshot.bookSettings.single().bookId).isEqualTo("book-1")
        assertThat(snapshot.themes.single().id).isEqualTo("theme-1")
        assertThat(snapshot.fonts.single().assetPath).isEqualTo("assets/fonts/font-1/font.bin")
        assertThat(snapshot.sessions.single().id).isEqualTo("session-1")
        assertThat(snapshot.dailyStats.single().activeMillis).isEqualTo(300)
        assertThat(snapshot.bookSources.single().offsetIndexRelativePath).isEqualTo("books/book-1/offsets.xidx")
        assertThat(snapshot.javaClass.declaredFields.map { it.name }).containsNoneOf(
            "importTasks", "pendingFileCleanup", "searchChunks", "searchIndexStates", "journals", "staging",
        )
    }

    @Test
    fun `one read transaction prevents a mixed concurrent snapshot`() = runTest {
        val mutex = Mutex()
        val booksRead = CompletableDeferred<Unit>()
        val writerAttempted = CompletableDeferred<Unit>()
        val queries = FakeQueries()
        val source = RoomBackupCatalogDataSource.forTest(
            queries = queries,
            readTransaction = { block -> mutex.withLock { block() } },
            afterBooksRead = {
                booksRead.complete(Unit)
                writerAttempted.await()
            },
        )

        val snapshot = async { source.snapshot() }
        booksRead.await()
        val mutation = async {
            writerAttempted.complete(Unit)
            mutex.withLock { queries.version = 2 }
        }

        assertThat(snapshot.await().books.single().title).isEqualTo("旧书名")
        assertThat(snapshot.await().progress.single().offset).isEqualTo(7)
        mutation.await()
        assertThat(source.snapshot().books.single().title).isEqualTo("新书名")
        assertThat(source.snapshot().progress.single().offset).isEqualTo(70)
    }

    private class FakeQueries : BackupCatalogQueries {
        var version = 1
        override suspend fun books() = listOf(
            BookEntity(
                id = "book-1",
                title = if (version == 1) "旧书名" else "新书名",
                author = "作者",
                originalFileName = "public.txt",
                originalPath = "books/book-1/original.txt",
                normalizedPath = "books/book-1/content.txt",
                charsetName = "UTF-8",
                contentSha256 = "a".repeat(64),
                contentLength = 100,
                createdAtEpochMillis = 1,
                lastOpenedAtEpochMillis = 2,
                seriesName = "公开系列",
                seriesOrder = 2,
                customCoverPath = "books/book-1/cover.webp",
                finished = true,
            ),
        )
        override suspend fun memberships() = listOf(BookGroupMembershipEntity("book-1", "group-1"))
        override suspend fun groups() = listOf(BookGroupEntity("group-1", "分组", 0, 1, 2))
        override suspend fun progress() = listOf(ReadingProgressEntity("book-1", if (version == 1) 7 else 70, "hash", "前", "后", 100, 3))
        override suspend fun annotations() = listOf(
            AnnotationEntity("annotation-1", "book-1", "NOTE", 7, 8, "前", "后", "b".repeat(64), "yellow", "公开笔记", 4, 5),
        )
        override suspend fun globalSettings() = ReaderGlobalSettingsEntity(settingsJson = "{\"fontSizeSp\":24}", scheduleJson = "{}", updatedAtEpochMillis = 6)
        override suspend fun bookSettings() = listOf(BookReaderOverridesEntity("book-1", "{}", 7))
        override suspend fun themes() = listOf(ReaderThemeEntity("theme-1", "主题", "{}", false, 8))
        override suspend fun fonts() = listOf(ImportedFontEntity("font-1", "字体", "fonts/font-1/font.bin", "c".repeat(64), 10, 9))
        override suspend fun sessions() = listOf(ReadingSessionEntity("session-1", "book-1", 10, 11, 12, 300))
        override suspend fun dailyStats() = listOf(ReadingDailyStatEntity("book-1", 20_650, 300, 1))
    }
}
