package com.xinyue.reader

import com.xinyue.reader.core.data.RestoreDatabaseGateway
import com.xinyue.reader.core.data.RestoreJournalStore
import com.xinyue.reader.core.data.RestoreSnapshotStore
import com.xinyue.reader.core.domain.repository.ReaderThemeScheduleRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Acceptance-only access used by device tests to seed and verify restore state. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface BackupDebugEntryPoint {
    fun restoreDatabaseGateway(): RestoreDatabaseGateway
    fun restoreJournalStore(): RestoreJournalStore
    fun restoreSnapshotStore(): RestoreSnapshotStore
    fun readerThemeScheduleRepository(): ReaderThemeScheduleRepository
}
