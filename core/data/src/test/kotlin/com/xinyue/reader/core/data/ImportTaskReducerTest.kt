package com.xinyue.reader.core.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ImportTaskReducerTest {
    @Test
    fun `keeps the newest attempt per item and orders by original selection`() {
        val state = reduceImportWorkSnapshots(
            listOf(
                snapshot(index = 1, attempt = 0, status = ImportItemStatus.FAILED),
                snapshot(index = 0, attempt = 0, status = ImportItemStatus.NEEDS_DECISION),
                snapshot(index = 0, attempt = 1, status = ImportItemStatus.SUCCEEDED),
            ),
        )

        assertThat(state?.items?.map(ImportItemState::index)).containsExactly(0, 1).inOrder()
        assertThat(state?.items?.first()?.attempt).isEqualTo(1)
        assertThat(state?.items?.first()?.status).isEqualTo(ImportItemStatus.SUCCEEDED)
    }

    @Test
    fun `shows only the newest import batch`() {
        val state = reduceImportWorkSnapshots(
            listOf(
                snapshot(batchId = "0001-old", index = 0),
                snapshot(batchId = "0002-new", index = 0),
                snapshot(batchId = "0002-new", index = 1),
            ),
        )

        assertThat(state?.batchId).isEqualTo("0002-new")
        assertThat(state?.items).hasSize(2)
    }

    private fun snapshot(
        batchId: String = "0002-new",
        index: Int,
        attempt: Int = 0,
        status: ImportItemStatus = ImportItemStatus.QUEUED,
    ) = ImportWorkSnapshot(
        workId = "$batchId-$index-$attempt",
        batchId = batchId,
        index = index,
        total = 2,
        attempt = attempt,
        uriString = "content://books/$index",
        displayName = "小说$index.txt",
        preferredCharsetName = null,
        duplicateResolution = DuplicateResolution.ASK,
        status = status,
        progressPercent = 0,
        bookId = null,
        existingBookId = null,
        existingBookTitle = null,
        errorMessage = null,
    )
}
