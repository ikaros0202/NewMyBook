package com.xinyue.reader.feature.library

import com.google.common.truth.Truth.assertThat
import com.xinyue.reader.core.data.BookImporter
import com.xinyue.reader.core.data.BookManager
import com.xinyue.reader.core.data.ImportSource
import com.xinyue.reader.core.data.ImportSourceFactory
import com.xinyue.reader.core.domain.model.Book
import com.xinyue.reader.core.domain.model.BookCollectionMembership
import com.xinyue.reader.core.domain.model.BookGroup
import com.xinyue.reader.core.domain.model.LibraryGridDensity
import com.xinyue.reader.core.domain.model.LibraryLayoutMode
import com.xinyue.reader.core.domain.model.LibraryLayoutPreference
import com.xinyue.reader.core.domain.model.ReadingProgress
import com.xinyue.reader.core.domain.repository.BookGroupRepository
import com.xinyue.reader.core.domain.repository.BookCoverRepository
import com.xinyue.reader.core.domain.repository.BookRepository
import com.xinyue.reader.core.domain.repository.LibraryLayoutPreferences
import com.xinyue.reader.core.domain.time.EpochClock
import java.io.ByteArrayInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {
    @get:Rule
    private val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `progress labels distinguish unread tiny progress and regular percentages`() {
        assertThat(libraryProgressLabel(0.0)).isEqualTo("未开始")
        assertThat(libraryProgressLabel(0.001)).isEqualTo("阅读进度 <1%")
        assertThat(libraryProgressLabel(0.356)).isEqualTo("阅读进度 36%")
    }

    @Test
    fun `layout preference is observed and persisted without changing shelf data`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeRepository(initialBooks = listOf(sampleBook()))
            val layoutPreferences = FakeLibraryLayoutPreferences()
            val viewModel = LibraryViewModel(
                repository,
                FakeBookManager(repository),
                FakeBookGroupRepository(),
                FakeBookCoverRepository(),
                FakeImportSourceFactory(),
                EpochClock { NOW },
                mainDispatcherRule.dispatcher,
                libraryLayoutPreferences = layoutPreferences,
            )
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.layoutPreference).isEqualTo(
                LibraryLayoutPreference(
                    mode = LibraryLayoutMode.COVER_GRID,
                    gridDensity = LibraryGridDensity.STANDARD,
                ),
            )

            val selected = LibraryLayoutPreference(
                mode = LibraryLayoutMode.COMPACT_LIST,
                gridDensity = LibraryGridDensity.COMPACT,
            )
            viewModel.setLayoutPreference(selected)
            advanceUntilIdle()

            assertThat(layoutPreferences.writes).containsExactly(selected)
            assertThat(viewModel.uiState.value.layoutPreference).isEqualTo(selected)
            assertThat(viewModel.uiState.value.books).containsExactly(repository.getBook("book-1"))
        }

    @Test
    fun `layout preference write failure leaves shelf state usable`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeRepository(initialBooks = listOf(sampleBook()))
            val layoutPreferences = FakeLibraryLayoutPreferences(
                writeFailure = IllegalStateException("simulated preference failure"),
            )
            val viewModel = LibraryViewModel(
                repository,
                FakeBookManager(repository),
                FakeBookGroupRepository(),
                FakeBookCoverRepository(),
                FakeImportSourceFactory(),
                EpochClock { NOW },
                mainDispatcherRule.dispatcher,
                libraryLayoutPreferences = layoutPreferences,
            )
            advanceUntilIdle()

            viewModel.setLayoutPreference(
                LibraryLayoutPreference(
                    mode = LibraryLayoutMode.COMPACT_LIST,
                    gridDensity = LibraryGridDensity.COMFORTABLE,
                ),
            )
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.layoutPreference).isEqualTo(
                LibraryLayoutPreference(
                    mode = LibraryLayoutMode.COVER_GRID,
                    gridDensity = LibraryGridDensity.STANDARD,
                ),
            )
            assertThat(viewModel.uiState.value.books).hasSize(1)
            assertThat(viewModel.uiState.value.errorMessage).isNull()
        }

    @Test
    fun `continue book uses latest active progress independently of shelf filters and metadata edits`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeRepository(
                initialBooks = listOf(
                    sampleBook(id = "recent", title = "最近", lastOpenedAt = 300),
                    sampleBook(id = "older", title = "较早", lastOpenedAt = 200),
                    sampleBook(id = "finished", title = "已读完", lastOpenedAt = 400, finished = true),
                    sampleBook(id = "unopened", title = "未打开"),
                ),
            )
            val viewModel = LibraryViewModel(
                repository,
                FakeBookManager(repository),
                FakeBookGroupRepository(),
                FakeBookCoverRepository(),
                FakeImportSourceFactory(),
                EpochClock { NOW },
                mainDispatcherRule.dispatcher,
                libraryLayoutPreferences = FakeLibraryLayoutPreferences(),
            )
            advanceUntilIdle()

            repository.saveProgress(
                ReadingProgress(
                    bookId = "recent",
                    anchor = com.xinyue.reader.core.domain.model.TextAnchor(20, "recent"),
                    contentLength = 100,
                    updatedAtEpochMillis = 1,
                ),
            )
            repository.saveProgress(
                ReadingProgress(
                    bookId = "older",
                    anchor = com.xinyue.reader.core.domain.model.TextAnchor(10, "older"),
                    contentLength = 100,
                    updatedAtEpochMillis = 1,
                ),
            )
            repository.saveProgress(
                ReadingProgress(
                    bookId = "finished",
                    anchor = com.xinyue.reader.core.domain.model.TextAnchor(80, "finished"),
                    contentLength = 100,
                    updatedAtEpochMillis = 1,
                ),
            )
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.continueBookId).isEqualTo("recent")

            viewModel.setQuery("较早")
            viewModel.setSort(LibrarySort.TITLE)
            assertThat(viewModel.uiState.value.books.map(Book::id)).containsExactly("older")
            assertThat(viewModel.uiState.value.continueBookId).isEqualTo("recent")

            viewModel.updateBookMetadata("recent", "最近改名", null)
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.continueBookId).isEqualTo("recent")
        }

    @Test
    fun `filters renames and deletes books through the manager`() = runTest(mainDispatcherRule.dispatcher) {
        val repository = FakeRepository().apply { addBook(sampleBook()) }
        val manager = FakeBookManager(repository)
        val viewModel = LibraryViewModel(
            repository,
            manager,
            FakeBookGroupRepository(),
            FakeBookCoverRepository(),
            FakeImportSourceFactory(),
            EpochClock { NOW },
            mainDispatcherRule.dispatcher,
        )
        advanceUntilIdle()

        repository.saveProgress(
            ReadingProgress(
                bookId = "book-1",
                anchor = com.xinyue.reader.core.domain.model.TextAnchor(50, "context"),
                contentLength = 200,
                updatedAtEpochMillis = 2,
            ),
        )
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.progressFractions["book-1"]).isEqualTo(0.25)

        viewModel.setQuery("不存在")
        assertThat(viewModel.uiState.value.books).isEmpty()
        viewModel.setQuery("测试")
        assertThat(viewModel.uiState.value.books).hasSize(1)

        viewModel.renameBook("book-1", "新的书名")
        advanceUntilIdle()
        assertThat(manager.renames).containsExactly("book-1" to "新的书名")

        viewModel.setQuery("")
        viewModel.updateBookMetadata("book-1", "带作者的新书名", "  林川  ")
        advanceUntilIdle()
        assertThat(manager.metadataUpdates).containsExactly(
            Triple("book-1", "带作者的新书名", "  林川  "),
        )
        assertThat(viewModel.uiState.value.books.single().author).isEqualTo("林川")

        viewModel.deleteBook("book-1")
        advanceUntilIdle()
        assertThat(manager.deletions).containsExactly("book-1")
        assertThat(viewModel.uiState.value.books).isEmpty()
    }

    @Test
    fun `search requires every unicode separated term across title and author`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeRepository(
                initialBooks = listOf(
                    sampleBook(id = "matching", title = "Moon 星河", author = "林川"),
                    sampleBook(id = "title-only", title = "Moon 星河", author = "顾远"),
                    sampleBook(id = "author-only", title = "雾海来信", author = "林川"),
                    sampleBook(id = "missing-author", title = "星河旧梦", author = null),
                ),
            )
            val viewModel = LibraryViewModel(
                repository,
                FakeBookManager(repository),
                FakeBookGroupRepository(),
                FakeBookCoverRepository(),
                FakeImportSourceFactory(),
                EpochClock { NOW },
                mainDispatcherRule.dispatcher,
            )
            advanceUntilIdle()

            viewModel.setQuery(" moon　林 ")
            assertThat(viewModel.uiState.value.books.map(Book::id)).containsExactly("matching")

            viewModel.setQuery("星河 不存在")
            assertThat(viewModel.uiState.value.books).isEmpty()

            viewModel.setQuery("   ")
            assertThat(viewModel.uiState.value.books).hasSize(4)
        }

    @Test
    fun `search results update for every typed character and blank restores the shelf`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeRepository(
                initialBooks = listOf(
                    sampleBook(id = "a", title = "山城夜话", author = "林间客"),
                    sampleBook(id = "b", title = "山海旧闻", author = "纸上旅人"),
                    sampleBook(id = "c", title = "山海短篇集", author = "远舟"),
                ),
            )
            val viewModel = LibraryViewModel(
                repository,
                FakeBookManager(repository),
                FakeBookGroupRepository(),
                FakeBookCoverRepository(),
                FakeImportSourceFactory(),
                EpochClock { NOW },
                mainDispatcherRule.dispatcher,
            )
            advanceUntilIdle()

            viewModel.setQuery("山")
            assertThat(viewModel.uiState.value.books).hasSize(3)
            viewModel.setQuery("山海")
            assertThat(viewModel.uiState.value.books).hasSize(2)
            viewModel.setQuery("山海短")
            assertThat(viewModel.uiState.value.books.map(Book::id)).containsExactly("c")
            viewModel.setQuery("")
            assertThat(viewModel.uiState.value.query).isEmpty()
            assertThat(viewModel.uiState.value.books).hasSize(3)
        }

    @Test
    fun `multi collection author and series views use explicit memberships and stable series order`() =
        runTest(mainDispatcherRule.dispatcher) {
            val scienceFiction = sampleGroup("collection-scifi", "科幻")
            val favorites = sampleGroup("collection-favorites", "收藏")
            val repository = FakeRepository(
                initialBooks = listOf(
                    sampleBook(
                        id = "second",
                        title = "长夜列车 下",
                        author = "林川",
                        seriesName = "星海纪事",
                        seriesOrder = 2,
                    ),
                    sampleBook(
                        id = "first",
                        title = "长夜列车 上",
                        author = "林川",
                        seriesName = "星海纪事",
                        seriesOrder = 1,
                    ),
                    sampleBook(
                        id = "other",
                        title = "雾港来信",
                        author = "苏遥",
                        seriesName = "雾港",
                    ),
                    sampleBook(id = "standalone", title = "无系列短篇"),
                ),
            )
            val collections = FakeBookGroupRepository(
                initialGroups = listOf(scienceFiction, favorites),
                initialMemberships = listOf(
                    BookCollectionMembership("second", scienceFiction.id),
                    BookCollectionMembership("second", favorites.id),
                    BookCollectionMembership("first", scienceFiction.id),
                ),
            )
            val viewModel = LibraryViewModel(
                repository,
                FakeBookManager(repository),
                collections,
                FakeBookCoverRepository(),
                FakeImportSourceFactory(),
                EpochClock { NOW },
                mainDispatcherRule.dispatcher,
            )
            advanceUntilIdle()

            viewModel.setView(LibraryView.SERIES)
            assertThat(viewModel.uiState.value.sections.first { it.label == "星海纪事" }.books.map(Book::id))
                .containsExactly("first", "second").inOrder()

            viewModel.setView(LibraryView.AUTHORS)
            assertThat(viewModel.uiState.value.sections.first { it.label == "林川" }.books.map(Book::id))
                .containsExactly("first", "second")

            viewModel.setView(LibraryView.COLLECTIONS)
            assertThat(viewModel.uiState.value.sections.first { it.key == favorites.id }.books.map(Book::id))
                .containsExactly("second")
            assertThat(viewModel.uiState.value.sections.first { it.key == UNCOLLECTED_SECTION_KEY }.books.map(Book::id))
                .containsExactly("other", "standalone")

            viewModel.setFilter(LibraryFilter.Group(favorites.id))
            assertThat(viewModel.uiState.value.books.map(Book::id)).containsExactly("second")
            viewModel.setFilter(LibraryFilter.Uncollected)
            assertThat(viewModel.uiState.value.books.map(Book::id)).containsExactly("other", "standalone")

            viewModel.setFilter(LibraryFilter.All)
            viewModel.setQuery("林川 星海")
            assertThat(viewModel.uiState.value.books.map(Book::id)).containsExactly("first", "second")
            viewModel.setQuery("雾港")
            assertThat(viewModel.uiState.value.books.map(Book::id)).containsExactly("other")
        }

    @Test
    fun `editing a book updates series metadata and replaces only that books collections`() =
        runTest(mainDispatcherRule.dispatcher) {
            val oldCollection = sampleGroup("old", "旧集合")
            val newCollection = sampleGroup("new", "新集合")
            val repository = FakeRepository(initialBooks = listOf(sampleBook(seriesName = "旧系列")))
            val collections = FakeBookGroupRepository(
                initialGroups = listOf(oldCollection, newCollection),
                initialMemberships = listOf(BookCollectionMembership("book-1", oldCollection.id)),
            )
            val viewModel = LibraryViewModel(
                repository,
                FakeBookManager(repository),
                collections,
                FakeBookCoverRepository(),
                FakeImportSourceFactory(),
                EpochClock { NOW },
                mainDispatcherRule.dispatcher,
            )
            advanceUntilIdle()

            viewModel.updateBookMetadata(
                bookId = "book-1",
                title = "长夜列车",
                author = "林川",
                seriesName = "星海纪事",
                seriesOrder = 3,
                collectionIds = setOf(newCollection.id),
            )
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.books.single()).isEqualTo(
                sampleBook().copy(
                    title = "长夜列车",
                    author = "林川",
                    seriesName = "星海纪事",
                    seriesOrder = 3,
                ),
            )
            assertThat(viewModel.uiState.value.collectionIdsByBook["book-1"])
                .containsExactly(newCollection.id)
        }

    @Test
    fun `sorts the shelf by recent reading import time and title with stable ties`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeRepository(
                initialBooks = listOf(
                    sampleBook(
                        id = "book-a",
                        title = "Beta",
                        createdAt = 10,
                        lastOpenedAt = 20,
                    ),
                    sampleBook(
                        id = "book-b",
                        title = "Alpha",
                        createdAt = 30,
                        lastOpenedAt = null,
                    ),
                    sampleBook(
                        id = "book-c",
                        title = "Gamma",
                        createdAt = 20,
                        lastOpenedAt = 40,
                    ),
                ),
            )
            val viewModel = LibraryViewModel(
                repository,
                FakeBookManager(repository),
                FakeBookGroupRepository(),
                FakeBookCoverRepository(),
                FakeImportSourceFactory(),
                EpochClock { NOW },
                mainDispatcherRule.dispatcher,
            )
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.books.map(Book::id))
                .containsExactly("book-c", "book-b", "book-a").inOrder()

            viewModel.setSort(LibrarySort.IMPORTED)
            assertThat(viewModel.uiState.value.books.map(Book::id))
                .containsExactly("book-b", "book-c", "book-a").inOrder()

            viewModel.setSort(LibrarySort.TITLE)
            assertThat(viewModel.uiState.value.books.map(Book::id))
                .containsExactly("book-b", "book-a", "book-c").inOrder()
        }

    @Test
    fun `automatic and custom filters apply before search and sorting`() =
        runTest(mainDispatcherRule.dispatcher) {
            val group = sampleGroup("group-1", "科幻")
            val repository = FakeRepository(
                initialBooks = listOf(
                    sampleBook(id = "unread", title = "未读书"),
                    sampleBook(id = "reading", title = "阅读中"),
                    sampleBook(id = "finished", title = "已完成", finished = true),
                    sampleBook(id = "grouped", title = "分组小说", groupId = group.id),
                    sampleBook(id = "recent", title = "最近打开", lastOpenedAt = NOW - 1),
                    sampleBook(id = "old", title = "很久以前", lastOpenedAt = NOW - THIRTY_DAYS_MILLIS - 1),
                ),
            )
            repository.saveProgress(
                ReadingProgress(
                    bookId = "reading",
                    anchor = com.xinyue.reader.core.domain.model.TextAnchor(50, "context"),
                    contentLength = 100,
                    updatedAtEpochMillis = NOW,
                ),
            )
            val viewModel = LibraryViewModel(
                repository,
                FakeBookManager(repository),
                FakeBookGroupRepository(listOf(group)),
                FakeBookCoverRepository(),
                FakeImportSourceFactory(),
                EpochClock { NOW },
                mainDispatcherRule.dispatcher,
            )
            advanceUntilIdle()

            viewModel.setFilter(LibraryFilter.Reading)
            assertThat(viewModel.uiState.value.books.map(Book::id)).containsExactly("reading")
            viewModel.setFilter(LibraryFilter.Finished)
            assertThat(viewModel.uiState.value.books.map(Book::id)).containsExactly("finished")
            viewModel.setFilter(LibraryFilter.Recent)
            assertThat(viewModel.uiState.value.books.map(Book::id)).containsExactly("recent")
            viewModel.setFilter(LibraryFilter.Group(group.id))
            assertThat(viewModel.uiState.value.books.map(Book::id)).containsExactly("grouped")

            viewModel.setQuery("不存在")
            assertThat(viewModel.uiState.value.books).isEmpty()
            assertThat(viewModel.uiState.value.groups).containsExactly(group)
            assertThat(viewModel.uiState.value.filter).isEqualTo(LibraryFilter.Group(group.id))
        }

    @Test
    fun `group actions update through repository and deleted selection falls back to all`() =
        runTest(mainDispatcherRule.dispatcher) {
            val group = sampleGroup("group-1", "旧名称")
            val repository = FakeRepository(initialBooks = listOf(sampleBook(groupId = group.id)))
            val groups = FakeBookGroupRepository(listOf(group))
            val viewModel = LibraryViewModel(
                repository,
                FakeBookManager(repository),
                groups,
                FakeBookCoverRepository(),
                FakeImportSourceFactory(),
                EpochClock { NOW },
                mainDispatcherRule.dispatcher,
            )
            advanceUntilIdle()

            viewModel.createGroup(" 新分组 ")
            viewModel.renameGroup(group.id, "新名称")
            viewModel.setFilter(LibraryFilter.Group(group.id))
            viewModel.deleteGroup(group.id)
            advanceUntilIdle()

            assertThat(groups.createdNames).containsExactly(" 新分组 ")
            assertThat(groups.renames).containsExactly(group.id to "新名称")
            assertThat(groups.deletions).containsExactly(group.id)
            assertThat(viewModel.uiState.value.filter).isEqualTo(LibraryFilter.All)
        }

    @Test
    fun `imports and clears a private cover while exposing operation state`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeRepository(initialBooks = listOf(sampleBook()))
            val covers = FakeBookCoverRepository()
            val sources = FakeImportSourceFactory()
            val viewModel = LibraryViewModel(
                repository,
                FakeBookManager(repository),
                FakeBookGroupRepository(),
                covers,
                sources,
                EpochClock { NOW },
                mainDispatcherRule.dispatcher,
            )
            advanceUntilIdle()

            viewModel.importCover("book-1", "content://test/cover")
            advanceUntilIdle()

            assertThat(sources.requestedUris).containsExactly("content://test/cover")
            assertThat(covers.importedBookIds).containsExactly("book-1")
            assertThat(viewModel.uiState.value.coverUpdatingBookIds).isEmpty()

            viewModel.clearCover("book-1")
            advanceUntilIdle()

            assertThat(covers.clearedBookIds).containsExactly("book-1")
            assertThat(viewModel.uiState.value.coverUpdatingBookIds).isEmpty()
        }

    @Test
    fun `selection survives recomputation selects visible books and drops removed ids`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeRepository(
                initialBooks = listOf(
                    sampleBook(id = "book-a", title = "甲", groupId = "group-1"),
                    sampleBook(id = "book-b", title = "乙"),
                ),
            )
            val viewModel = LibraryViewModel(
                repository,
                FakeBookManager(repository),
                FakeBookGroupRepository(listOf(sampleGroup("group-1", "分组"))),
                FakeBookCoverRepository(),
                FakeImportSourceFactory(),
                EpochClock { NOW },
                mainDispatcherRule.dispatcher,
            )
            advanceUntilIdle()

            viewModel.enterSelection("book-a")
            viewModel.setSort(LibrarySort.TITLE)
            viewModel.setFilter(LibraryFilter.Group("group-1"))
            assertThat(viewModel.uiState.value.selectedBookIds).containsExactly("book-a")

            viewModel.clearSelection()
            viewModel.selectAllVisible()
            assertThat(viewModel.uiState.value.selectedBookIds).containsExactly("book-a")
            viewModel.toggleSelection("book-a")
            assertThat(viewModel.uiState.value.isSelectionMode).isFalse()

            viewModel.enterSelection("book-a")
            repository.deleteBook("book-a")
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.selectedBookIds).isEmpty()
            assertThat(viewModel.uiState.value.isSelectionMode).isFalse()
        }

    @Test
    fun `batch move and finished clear selection only after success`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeRepository(
                initialBooks = listOf(sampleBook(id = "book-a"), sampleBook(id = "book-b")),
            )
            val groups = FakeBookGroupRepository(listOf(sampleGroup("group-1", "分组")))
            val manager = FakeBookManager(repository)
            val viewModel = LibraryViewModel(
                repository,
                manager,
                groups,
                FakeBookCoverRepository(),
                FakeImportSourceFactory(),
                EpochClock { NOW },
                mainDispatcherRule.dispatcher,
            )
            advanceUntilIdle()
            viewModel.enterSelection("book-a")
            viewModel.toggleSelection("book-b")

            groups.moveFailure = IllegalStateException("move failed")
            viewModel.moveSelectedBooks("group-1")
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.selectedBookIds).containsExactly("book-a", "book-b")
            assertThat(viewModel.uiState.value.errorMessage).isEqualTo("批量操作失败")

            viewModel.dismissError()
            groups.moveFailure = null
            viewModel.moveSelectedBooks(null)
            advanceUntilIdle()
            assertThat(groups.moves.last()).isEqualTo(setOf("book-a", "book-b") to null)
            assertThat(viewModel.uiState.value.selectedBookIds).isEmpty()

            viewModel.enterSelection("book-a")
            viewModel.toggleSelection("book-b")
            viewModel.markSelectedFinished(true)
            advanceUntilIdle()
            assertThat(manager.finishedUpdates).containsExactly(setOf("book-a", "book-b") to true)
            assertThat(repository.getBook("book-a")!!.finished).isTrue()
            assertThat(viewModel.uiState.value.selectedBookIds).isEmpty()
        }

    @Test
    fun `batch deletion waits eight seconds and undo cancels without touching repository`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeRepository(
                initialBooks = listOf(sampleBook(id = "book-a"), sampleBook(id = "book-b")),
            )
            val manager = FakeBookManager(repository)
            val viewModel = LibraryViewModel(
                repository,
                manager,
                FakeBookGroupRepository(),
                FakeBookCoverRepository(),
                FakeImportSourceFactory(),
                EpochClock { testScheduler.currentTime },
                mainDispatcherRule.dispatcher,
            )
            advanceUntilIdle()
            viewModel.enterSelection("book-a")
            viewModel.toggleSelection("book-b")

            viewModel.requestDeleteSelected()
            assertThat(viewModel.uiState.value.pendingDeletion!!.bookIds)
                .containsExactly("book-a", "book-b")
            assertThat(viewModel.uiState.value.books).hasSize(2)
            advanceTimeBy(7_999)
            assertThat(manager.batchDeletions).isEmpty()

            viewModel.undoPendingDeletion()
            advanceUntilIdle()
            assertThat(manager.batchDeletions).isEmpty()
            assertThat(viewModel.uiState.value.pendingDeletion).isNull()
            assertThat(viewModel.uiState.value.selectedBookIds).containsExactly("book-a", "book-b")

            viewModel.requestDeleteSelected()
            advanceTimeBy(8_000)
            advanceUntilIdle()
            assertThat(manager.batchDeletions).containsExactly(setOf("book-a", "book-b"))
            assertThat(viewModel.uiState.value.pendingDeletion).isNull()
        }

    @Test
    fun `batch deletion failure keeps books visible and restores selection`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeRepository(initialBooks = listOf(sampleBook()))
            val manager = FakeBookManager(repository).apply {
                batchDeleteFailure = IllegalStateException("delete failed")
            }
            val viewModel = LibraryViewModel(
                repository,
                manager,
                FakeBookGroupRepository(),
                FakeBookCoverRepository(),
                FakeImportSourceFactory(),
                EpochClock { testScheduler.currentTime },
                mainDispatcherRule.dispatcher,
            )
            advanceUntilIdle()
            viewModel.enterSelection("book-1")
            viewModel.requestDeleteSelected()
            viewModel.requestDeleteSelected()
            assertThat(viewModel.uiState.value.errorMessage).isEqualTo("已有待撤销的删除操作")

            viewModel.dismissError()
            advanceTimeBy(8_000)
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.books).hasSize(1)
            assertThat(viewModel.uiState.value.selectedBookIds).containsExactly("book-1")
            assertThat(viewModel.uiState.value.errorMessage).isEqualTo("批量删除失败")
        }

    private class FakeBookManager(private val repository: FakeRepository) : BookManager {
        val renames = mutableListOf<Pair<String, String>>()
        val metadataUpdates = mutableListOf<Triple<String, String, String?>>()
        val deletions = mutableListOf<String>()
        val finishedUpdates = mutableListOf<Pair<Set<String>, Boolean>>()
        val batchDeletions = mutableListOf<Set<String>>()
        var batchDeleteFailure: Throwable? = null

        override suspend fun get(bookId: String): Book? = repository.getBook(bookId)

        override suspend fun rename(bookId: String, title: String) {
            renames += bookId to title
            repository.renameBook(bookId, title)
        }

        override suspend fun updateMetadata(bookId: String, title: String, author: String?) {
            metadataUpdates += Triple(bookId, title, author)
            repository.replaceBook(
                bookId,
                requireNotNull(repository.getBook(bookId)).copy(
                    title = title.trim(),
                    author = author?.trim()?.takeIf(String::isNotEmpty),
                ),
            )
        }

        override suspend fun updateMetadata(
            bookId: String,
            title: String,
            author: String?,
            seriesName: String?,
            seriesOrder: Int?,
        ) {
            metadataUpdates += Triple(bookId, title, author)
            repository.replaceBook(
                bookId,
                requireNotNull(repository.getBook(bookId)).copy(
                    title = title.trim(),
                    author = author?.trim()?.takeIf(String::isNotEmpty),
                    seriesName = seriesName?.trim()?.takeIf(String::isNotEmpty),
                    seriesOrder = seriesOrder,
                ),
            )
        }

        override suspend fun delete(bookId: String) {
            deletions += bookId
            repository.deleteBook(bookId)
        }

        override suspend fun markFinished(bookIds: Set<String>, finished: Boolean) {
            finishedUpdates += bookIds to finished
            bookIds.forEach { bookId ->
                val book = requireNotNull(repository.getBook(bookId))
                repository.replaceBook(bookId, book.copy(finished = finished))
            }
        }

        override suspend fun deleteBatch(bookIds: Set<String>) {
            batchDeleteFailure?.let { throw it }
            batchDeletions += bookIds
            bookIds.forEach { repository.deleteBook(it) }
        }
    }

    private class FakeRepository(initialBooks: List<Book> = emptyList()) : BookRepository {
        private val books = MutableStateFlow(initialBooks)
        private val progress = MutableStateFlow<List<ReadingProgress>>(emptyList())

        override fun observeBooks(): Flow<List<Book>> = books
        override fun observeProgress(): Flow<List<ReadingProgress>> = progress
        override suspend fun addBook(book: Book) { books.value += book }
        override suspend fun getBook(bookId: String): Book? = books.value.firstOrNull { it.id == bookId }
        override suspend fun findBySha256(contentSha256: String): Book? = null
        override suspend fun saveProgress(progress: ReadingProgress) {
            this.progress.value = this.progress.value.filterNot { it.bookId == progress.bookId } + progress
        }
        override suspend fun getProgress(bookId: String): ReadingProgress? =
            progress.value.firstOrNull { it.bookId == bookId }
        override suspend fun renameBook(bookId: String, title: String) {
            books.value = books.value.map { if (it.id == bookId) it.copy(title = title) else it }
        }
        override suspend fun deleteBook(bookId: String) {
            books.value = books.value.filterNot { it.id == bookId }
        }
        override suspend fun replaceBook(existingBookId: String, replacement: Book) {
            books.value = books.value.map {
                if (it.id == existingBookId) replacement.copy(id = existingBookId) else it
            }
        }
        override suspend fun markOpened(bookId: String, epochMillis: Long) = Unit
    }

    private class FakeBookGroupRepository(
        initialGroups: List<BookGroup> = emptyList(),
        initialMemberships: List<BookCollectionMembership> = emptyList(),
    ) : BookGroupRepository {
        private val groups = MutableStateFlow(initialGroups)
        private val memberships = MutableStateFlow(initialMemberships)
        val createdNames = mutableListOf<String>()
        val renames = mutableListOf<Pair<String, String>>()
        val deletions = mutableListOf<String>()
        val moves = mutableListOf<Pair<Set<String>, String?>>()
        var moveFailure: Throwable? = null

        override fun observeAll(): Flow<List<BookGroup>> = groups

        override fun observeMemberships(): Flow<List<BookCollectionMembership>> = memberships

        override suspend fun create(name: String): BookGroup {
            createdNames += name
            val now = NOW + groups.value.size
            return BookGroup("created-$now", name.trim(), groups.value.size, now, now).also {
                groups.value += it
            }
        }

        override suspend fun rename(groupId: String, name: String) {
            renames += groupId to name
            groups.value = groups.value.map {
                if (it.id == groupId) it.copy(name = name.trim(), updatedAtEpochMillis = NOW) else it
            }
        }

        override suspend fun delete(groupId: String) {
            deletions += groupId
            groups.value = groups.value.filterNot { it.id == groupId }
            memberships.value = memberships.value.filterNot { it.collectionId == groupId }
        }

        override suspend fun moveBooks(bookIds: Set<String>, groupId: String?) {
            moveFailure?.let { throw it }
            moves += bookIds to groupId
            replaceCollectionsForBooks(bookIds, groupId?.let(::setOf).orEmpty())
        }

        override suspend fun addBooksToCollections(bookIds: Set<String>, collectionIds: Set<String>) {
            memberships.value = (
                memberships.value +
                    bookIds.flatMap { bookId ->
                        collectionIds.map { collectionId -> BookCollectionMembership(bookId, collectionId) }
                    }
                ).distinct()
        }

        override suspend fun removeBooksFromCollections(bookIds: Set<String>, collectionIds: Set<String>) {
            memberships.value = memberships.value.filterNot {
                it.bookId in bookIds && it.collectionId in collectionIds
            }
        }

        override suspend fun replaceCollectionsForBooks(bookIds: Set<String>, collectionIds: Set<String>) {
            memberships.value = memberships.value.filterNot { it.bookId in bookIds } +
                bookIds.flatMap { bookId ->
                    collectionIds.map { collectionId -> BookCollectionMembership(bookId, collectionId) }
                }
        }
    }

    private class FakeBookCoverRepository : BookCoverRepository {
        val importedBookIds = mutableListOf<String>()
        val clearedBookIds = mutableListOf<String>()

        override suspend fun importCover(bookId: String, source: com.xinyue.reader.core.domain.model.ImportSource): String {
            importedBookIds += bookId
            source.openStream().use { it.readBytes() }
            return "books/$bookId/cover.webp"
        }

        override suspend fun clearCover(bookId: String) {
            clearedBookIds += bookId
        }
    }

    private class FakeImportSourceFactory : ImportSourceFactory {
        val requestedUris = mutableListOf<String>()

        override suspend fun create(uriString: String): ImportSource {
            requestedUris += uriString
            return ImportSource("cover.png", 3) { ByteArrayInputStream(byteArrayOf(1, 2, 3)) }
        }
    }

    private class FakeLibraryLayoutPreferences(
        initialPreference: LibraryLayoutPreference = LibraryLayoutPreference(),
        private val writeFailure: Throwable? = null,
    ) : LibraryLayoutPreferences {
        private val state = MutableStateFlow(initialPreference)
        val writes = mutableListOf<LibraryLayoutPreference>()

        override fun observe(): Flow<LibraryLayoutPreference> = state

        override suspend fun set(preference: LibraryLayoutPreference) {
            writeFailure?.let { throw it }
            writes += preference
            state.value = preference
        }
    }

    private class MainDispatcherRule(
        val dispatcher: TestDispatcher = UnconfinedTestDispatcher(),
    ) : TestWatcher() {
        override fun starting(description: Description) = Dispatchers.setMain(dispatcher)
        override fun finished(description: Description) = Dispatchers.resetMain()
    }

    private companion object {
        fun sampleBook(
            id: String = "book-1",
            title: String = "测试小说",
            author: String? = null,
            createdAt: Long = 1,
            lastOpenedAt: Long? = null,
            groupId: String? = null,
            finished: Boolean = false,
            seriesName: String? = null,
            seriesOrder: Int? = null,
        ) = Book(
            id = id,
            title = title,
            author = author,
            originalFileName = "测试小说.txt",
            originalPath = "books/book-1/original.txt",
            normalizedPath = "books/book-1/content.txt",
            charsetName = "UTF-8",
            contentSha256 = "hash",
            contentLength = 2,
            createdAtEpochMillis = createdAt,
            lastOpenedAtEpochMillis = lastOpenedAt,
            groupId = groupId,
            finished = finished,
            seriesName = seriesName,
            seriesOrder = seriesOrder,
        )

        fun sampleGroup(id: String, name: String) = BookGroup(
            id = id,
            name = name,
            sortOrder = 0,
            createdAtEpochMillis = 1,
            updatedAtEpochMillis = 1,
        )

        const val NOW = 4_000_000_000L
    }
}
