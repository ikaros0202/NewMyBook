package com.xinyue.reader.core.database.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Upsert
import androidx.room3.Transaction
import com.xinyue.reader.core.database.entity.ReadingDailyStatEntity
import com.xinyue.reader.core.database.entity.ReadingSessionEntity
import kotlinx.coroutines.flow.Flow
import com.xinyue.reader.core.domain.model.splitEffectiveReadingInterval
import java.time.ZoneId

@Dao
interface ReadingSessionDao {
    @Query("SELECT * FROM reading_sessions ORDER BY id")
    suspend fun getAllForBackup(): List<ReadingSessionEntity>

    @Query("SELECT * FROM reading_daily_stats ORDER BY bookId, localEpochDay")
    suspend fun getAllDailyStatsForBackup(): List<ReadingDailyStatEntity>

    @Insert
    suspend fun insertSession(session: ReadingSessionEntity)

    @Query("SELECT * FROM reading_sessions WHERE id = :sessionId LIMIT 1")
    suspend fun getSession(sessionId: String): ReadingSessionEntity?

    @Query("SELECT * FROM reading_sessions WHERE endedAtEpochMillis IS NULL ORDER BY startedAtEpochMillis ASC")
    suspend fun getOpenSessions(): List<ReadingSessionEntity>

    @Query(
        "UPDATE reading_sessions SET lastInteractionAtEpochMillis = :nowEpochMillis " +
            "WHERE id = :sessionId AND endedAtEpochMillis IS NULL",
    )
    suspend fun recordInteraction(sessionId: String, nowEpochMillis: Long): Int

    @Query(
        "UPDATE reading_sessions SET lastInteractionAtEpochMillis = :lastInteractionAtEpochMillis, " +
            "activeMillis = :activeMillis WHERE id = :sessionId AND endedAtEpochMillis IS NULL",
    )
    suspend fun updateActiveSession(
        sessionId: String,
        lastInteractionAtEpochMillis: Long,
        activeMillis: Long,
    ): Int

    @Query(
        "UPDATE reading_sessions SET endedAtEpochMillis = :endedAtEpochMillis, activeMillis = :activeMillis " +
            "WHERE id = :sessionId AND endedAtEpochMillis IS NULL",
    )
    suspend fun endSession(sessionId: String, endedAtEpochMillis: Long, activeMillis: Long): Int

    @Upsert
    suspend fun upsertDailyStat(stat: ReadingDailyStatEntity)

    @Query(
        "SELECT * FROM reading_daily_stats " +
            "WHERE bookId = :bookId AND localEpochDay = :localEpochDay LIMIT 1",
    )
    suspend fun getDailyStat(bookId: String, localEpochDay: Long): ReadingDailyStatEntity?

    @Query(
        "SELECT COUNT(*) FROM reading_daily_stats " +
            "WHERE bookId = :bookId AND localEpochDay = :localEpochDay",
    )
    suspend fun countDailyStats(bookId: String, localEpochDay: Long): Int

    @Query("SELECT * FROM reading_daily_stats WHERE bookId = :bookId ORDER BY localEpochDay ASC")
    fun observeBookDailyStats(bookId: String): Flow<List<ReadingDailyStatEntity>>

    @Query(
        "SELECT * FROM reading_daily_stats WHERE bookId = :bookId " +
            "AND localEpochDay BETWEEN :startEpochDay AND :endEpochDay ORDER BY localEpochDay ASC",
    )
    fun observeBookDailyStats(
        bookId: String,
        startEpochDay: Long,
        endEpochDay: Long,
    ): Flow<List<ReadingDailyStatEntity>>

    @Query(
        "SELECT * FROM reading_daily_stats " +
            "WHERE localEpochDay BETWEEN :startEpochDay AND :endEpochDay " +
            "ORDER BY localEpochDay ASC, bookId ASC",
    )
    fun observeDailyStats(
        startEpochDay: Long,
        endEpochDay: Long,
    ): Flow<List<ReadingDailyStatEntity>>

    @Query("DELETE FROM reading_sessions")
    suspend fun deleteAllSessions()

    @Query("DELETE FROM reading_daily_stats")
    suspend fun deleteAllDailyStats()

    @Transaction
    suspend fun insertSessionAndIncrementStartDay(session: ReadingSessionEntity, localEpochDay: Long) {
        insertSession(session)
        val current = getDailyStat(session.bookId, localEpochDay)
        upsertDailyStat(
            ReadingDailyStatEntity(
                bookId = session.bookId,
                localEpochDay = localEpochDay,
                activeMillis = current?.activeMillis ?: 0,
                sessionCount = (current?.sessionCount ?: 0) + 1,
            ),
        )
    }

    @Transaction
    suspend fun accountSession(
        sessionId: String,
        nowEpochMillis: Long,
        zoneId: String,
        close: Boolean,
    ): Boolean {
        val session = getSession(sessionId)?.takeIf { it.endedAtEpochMillis == null } ?: return false
        val portions = splitEffectiveReadingInterval(
            session.lastInteractionAtEpochMillis,
            nowEpochMillis,
            ZoneId.of(zoneId),
        )
        portions.forEach { portion ->
            val current = getDailyStat(session.bookId, portion.localEpochDay)
            upsertDailyStat(
                ReadingDailyStatEntity(
                    bookId = session.bookId,
                    localEpochDay = portion.localEpochDay,
                    activeMillis = (current?.activeMillis ?: 0) + portion.activeMillis,
                    sessionCount = current?.sessionCount ?: 0,
                ),
            )
        }
        val activeMillis = session.activeMillis + portions.sumOf { it.activeMillis }
        return if (close) {
            endSession(
                sessionId = sessionId,
                endedAtEpochMillis = maxOf(nowEpochMillis, session.lastInteractionAtEpochMillis),
                activeMillis = activeMillis,
            ) == 1
        } else if (nowEpochMillis > session.lastInteractionAtEpochMillis) {
            updateActiveSession(sessionId, nowEpochMillis, activeMillis) == 1
        } else {
            true
        }
    }

    @Transaction
    suspend fun clearStatisticsData() {
        deleteAllSessions()
        deleteAllDailyStats()
    }
}
