package com.xinyue.reader.core.domain.time

fun interface EpochClock {
    fun nowEpochMillis(): Long
}
