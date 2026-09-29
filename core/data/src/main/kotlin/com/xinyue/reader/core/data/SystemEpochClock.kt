package com.xinyue.reader.core.data

import com.xinyue.reader.core.domain.time.EpochClock
import javax.inject.Inject

class SystemEpochClock @Inject constructor() : EpochClock {
    override fun nowEpochMillis(): Long = System.currentTimeMillis()
}
