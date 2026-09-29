package com.xinyue.reader.core.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class ReadingSession(
    val id: String,
    val bookId: String,
    val startedAtEpochMillis: Long,
    val lastInteractionAtEpochMillis: Long,
    val endedAtEpochMillis: Long?,
    val activeMillis: Long,
)
