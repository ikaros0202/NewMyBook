package com.xinyue.reader.core.data

import com.google.common.truth.Truth.assertThat
import com.xinyue.reader.core.database.dao.ReadingSessionDao
import com.xinyue.reader.core.database.entity.ReadingDailyStatEntity
import com.xinyue.reader.core.database.entity.ReadingSessionEntity
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class RoomReadingSessionRepositoryTest {
    @Test
    fun `begin interactions end and stale recovery account capped time exactly once`() = runTest {
        val dao = FakeReadingSessionDao()
        var nextId = 0
        val repository = RoomReadingSessionRepository(dao, { ZoneId.of("UTC") }, { "session-${++nextId}" })

        val first = repository.begin("book-1", 0)
        repository.recordInteraction(first, 10 * 60_000L)
        repository.end(first, 20 * 60_000L)
        repository.end(first, 30 * 60_000L)

        assertThat(dao.sessions.value.single().activeMillis).isEqualTo(10 * 60_000L)
        assertThat(dao.sessions.value.single().endedAtEpochMillis).isEqualTo(20 * 60_000L)
        assertThat(dao.stats.value.single().sessionCount).isEqualTo(1)
        assertThat(dao.stats.value.single().activeMillis).isEqualTo(10 * 60_000L)

        val stale = repository.begin("book-1", 40 * 60_000L)
        repository.closeStaleSessions(50 * 60_000L)
        assertThat(dao.sessions.value.single { it.id == stale }.endedAtEpochMillis).isEqualTo(50 * 60_000L)
        assertThat(dao.stats.value.single().sessionCount).isEqualTo(2)
        assertThat(dao.stats.value.single().activeMillis).isEqualTo(15 * 60_000L)
    }

    @Test
    fun `aggregates emit exact ranges and clearing leaves unrelated reading data`() = runTest {
        val dao = FakeReadingSessionDao()
        val repository = RoomReadingSessionRepository(dao, { ZoneId.of("UTC") }, { "session" })
        val session = repository.begin("book-1", 0)
        repository.end(session, 60_000)

        assertThat(repository.observeBookStatistics("book-1").first().activeMillis).isEqualTo(60_000)
        val ranged = repository.observeStatistics(0, 86_400_000).first()
        assertThat(ranged.activeMillis).isEqualTo(60_000)
        assertThat(ranged.rangeStartEpochMillis).isEqualTo(0)
        assertThat(ranged.rangeEndEpochMillis).isEqualTo(86_400_000)

        repository.clearStatistics()
        assertThat(dao.sessions.value).isEmpty()
        assertThat(dao.stats.value).isEmpty()
        assertThat(dao.unrelatedBookCount).isEqualTo(1)
    }

    private class FakeReadingSessionDao : ReadingSessionDao {
        val sessions = MutableStateFlow<List<ReadingSessionEntity>>(emptyList())
        val stats = MutableStateFlow<List<ReadingDailyStatEntity>>(emptyList())
        var unrelatedBookCount = 1

        override suspend fun getAllForBackup(): List<ReadingSessionEntity> = sessions.value
        override suspend fun getAllDailyStatsForBackup(): List<ReadingDailyStatEntity> = stats.value

        override suspend fun insertSession(session: ReadingSessionEntity) {
            sessions.value += session
        }

        override suspend fun getSession(sessionId: String): ReadingSessionEntity? =
            sessions.value.firstOrNull { it.id == sessionId }

        override suspend fun getOpenSessions(): List<ReadingSessionEntity> =
            sessions.value.filter { it.endedAtEpochMillis == null }

        override suspend fun recordInteraction(sessionId: String, nowEpochMillis: Long): Int = error("legacy API unused")
        override suspend fun endSession(sessionId: String, endedAtEpochMillis: Long, activeMillis: Long): Int =
            updateSession(sessionId, endedAtEpochMillis, activeMillis, null)

        override suspend fun updateActiveSession(
            sessionId: String,
            lastInteractionAtEpochMillis: Long,
            activeMillis: Long,
        ): Int = updateSession(sessionId, null, activeMillis, lastInteractionAtEpochMillis)

        private fun updateSession(sessionId: String, endedAt: Long?, activeMillis: Long, lastInteractionAt: Long?): Int {
            val exists = sessions.value.any { it.id == sessionId && it.endedAtEpochMillis == null }
            sessions.value = sessions.value.map {
                if (it.id == sessionId && it.endedAtEpochMillis == null) {
                    it.copy(
                        endedAtEpochMillis = endedAt ?: it.endedAtEpochMillis,
                        activeMillis = activeMillis,
                        lastInteractionAtEpochMillis = lastInteractionAt ?: it.lastInteractionAtEpochMillis,
                    )
                } else it
            }
            return if (exists) 1 else 0
        }

        override suspend fun upsertDailyStat(stat: ReadingDailyStatEntity) {
            stats.value = stats.value.filterNot {
                it.bookId == stat.bookId && it.localEpochDay == stat.localEpochDay
            } + stat
        }

        override suspend fun getDailyStat(bookId: String, localEpochDay: Long): ReadingDailyStatEntity? =
            stats.value.firstOrNull { it.bookId == bookId && it.localEpochDay == localEpochDay }

        override suspend fun countDailyStats(bookId: String, localEpochDay: Long): Int =
            stats.value.count { it.bookId == bookId && it.localEpochDay == localEpochDay }

        override fun observeBookDailyStats(bookId: String, startEpochDay: Long, endEpochDay: Long): Flow<List<ReadingDailyStatEntity>> =
            MutableStateFlow(stats.value.filter { it.bookId == bookId && it.localEpochDay in startEpochDay..endEpochDay })

        override fun observeBookDailyStats(bookId: String): Flow<List<ReadingDailyStatEntity>> =
            MutableStateFlow(stats.value.filter { it.bookId == bookId })

        override fun observeDailyStats(startEpochDay: Long, endEpochDay: Long): Flow<List<ReadingDailyStatEntity>> =
            MutableStateFlow(stats.value.filter { it.localEpochDay in startEpochDay..endEpochDay })

        override suspend fun deleteAllSessions() { sessions.value = emptyList() }
        override suspend fun deleteAllDailyStats() { stats.value = emptyList() }
    }
}
