package com.xinyue.reader.core.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class ReadingStatistics(
    val bookId: String?,
    val activeMillis: Long,
    val sessionCount: Long,
    val rangeStartEpochMillis: Long,
    val rangeEndEpochMillis: Long,
)
