package com.xinyue.reader.core.data

import com.xinyue.reader.core.database.dao.ReadingSessionDao
import com.xinyue.reader.core.database.entity.ReadingSessionEntity
import com.xinyue.reader.core.domain.model.ReadingStatistics
import com.xinyue.reader.core.domain.repository.ReadingSessionRepository
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

@Singleton
class RoomReadingSessionRepository internal constructor(
    private val dao: ReadingSessionDao,
    private val zoneIdProvider: () -> ZoneId,
    private val idFactory: () -> String,
) : ReadingSessionRepository {
    @Inject
    constructor(dao: ReadingSessionDao) : this(dao, ZoneId::systemDefault, { UUID.randomUUID().toString() })

    override suspend fun begin(bookId: String, nowEpochMillis: Long): String {
        val id = idFactory()
        val session = ReadingSessionEntity(
            id = id,
            bookId = bookId,
            startedAtEpochMillis = nowEpochMillis,
            lastInteractionAtEpochMillis = nowEpochMillis,
            endedAtEpochMillis = null,
            activeMillis = 0,
        )
        val startDay = Instant.ofEpochMilli(nowEpochMillis).atZone(zoneIdProvider()).toLocalDate().toEpochDay()
        dao.insertSessionAndIncrementStartDay(session, startDay)
        return id
    }

    override suspend fun recordInteraction(sessionId: String, nowEpochMillis: Long) {
        dao.accountSession(sessionId, nowEpochMillis, zoneIdProvider().id, close = false)
    }

    override suspend fun end(sessionId: String, nowEpochMillis: Long) {
        dao.accountSession(sessionId, nowEpochMillis, zoneIdProvider().id, close = true)
    }

    override suspend fun closeStaleSessions(nowEpochMillis: Long) {
        dao.getOpenSessions().forEach { session -> end(session.id, nowEpochMillis) }
    }

    override fun observeBookStatistics(bookId: String): Flow<ReadingStatistics> =
        dao.observeBookDailyStats(bookId).map { rows ->
            if (rows.isEmpty()) {
                ReadingStatistics(bookId, 0, 0, 0, 0)
            } else {
                val zone = zoneIdProvider()
                ReadingStatistics(
                    bookId = bookId,
                    activeMillis = rows.sumOf { it.activeMillis },
                    sessionCount = rows.sumOf { it.sessionCount },
                    rangeStartEpochMillis = java.time.LocalDate.ofEpochDay(rows.first().localEpochDay)
                        .atStartOfDay(zone).toInstant().toEpochMilli(),
                    rangeEndEpochMillis = java.time.LocalDate.ofEpochDay(rows.last().localEpochDay + 1)
                        .atStartOfDay(zone).toInstant().toEpochMilli(),
                )
            }
        }

    override fun observeBookStatistics(
        bookId: String,
        rangeStartEpochMillis: Long,
        rangeEndEpochMillis: Long,
    ): Flow<ReadingStatistics> {
        if (rangeEndEpochMillis <= rangeStartEpochMillis) {
            return flowOf(ReadingStatistics(bookId, 0, 0, rangeStartEpochMillis, rangeEndEpochMillis))
        }
        val zone = zoneIdProvider()
        val startDay = Instant.ofEpochMilli(rangeStartEpochMillis).atZone(zone).toLocalDate().toEpochDay()
        val endDay = Instant.ofEpochMilli(rangeEndEpochMillis - 1).atZone(zone).toLocalDate().toEpochDay()
        return dao.observeBookDailyStats(bookId, startDay, endDay).map { rows ->
            ReadingStatistics(
                bookId = bookId,
                activeMillis = rows.sumOf { it.activeMillis },
                sessionCount = rows.sumOf { it.sessionCount },
                rangeStartEpochMillis = rangeStartEpochMillis,
                rangeEndEpochMillis = rangeEndEpochMillis,
            )
        }
    }

    override fun observeStatistics(
        rangeStartEpochMillis: Long,
        rangeEndEpochMillis: Long,
    ): Flow<ReadingStatistics> {
        if (rangeEndEpochMillis <= rangeStartEpochMillis) {
            return flowOf(ReadingStatistics(null, 0, 0, rangeStartEpochMillis, rangeEndEpochMillis))
        }
        val zone = zoneIdProvider()
        val startDay = Instant.ofEpochMilli(rangeStartEpochMillis).atZone(zone).toLocalDate().toEpochDay()
        val endDay = Instant.ofEpochMilli(rangeEndEpochMillis - 1).atZone(zone).toLocalDate().toEpochDay()
        return dao.observeDailyStats(startDay, endDay).map { rows ->
            ReadingStatistics(
                bookId = null,
                activeMillis = rows.sumOf { it.activeMillis },
                sessionCount = rows.sumOf { it.sessionCount },
                rangeStartEpochMillis = rangeStartEpochMillis,
                rangeEndEpochMillis = rangeEndEpochMillis,
            )
        }
    }

    override suspend fun clearStatistics() {
        dao.clearStatisticsData()
    }
}
