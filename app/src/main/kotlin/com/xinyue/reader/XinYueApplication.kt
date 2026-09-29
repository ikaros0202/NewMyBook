package com.xinyue.reader

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.WorkManager
import com.xinyue.reader.core.data.BookFileCleanupWork
import com.xinyue.reader.core.data.LegacyReaderSettingsMigrator
import com.xinyue.reader.core.data.RestoreRecovery
import com.xinyue.reader.core.domain.repository.ReadingSessionRepository
import com.xinyue.reader.core.domain.time.EpochClock
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class XinYueApplication : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var legacyReaderSettingsMigrator: LegacyReaderSettingsMigrator
    @Inject lateinit var readingSessionRepository: ReadingSessionRepository
    @Inject lateinit var epochClock: EpochClock
    @Inject lateinit var restoreRecovery: RestoreRecovery
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        WorkManager.initialize(this, workManagerConfiguration)
        applicationScope.launch {
            try {
                restoreRecovery.reconcile()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                // Do not start cleanup while a durable restore journal still needs attention.
                return@launch
            }
            BookFileCleanupWork.enqueue(WorkManager.getInstance(this@XinYueApplication))
            legacyReaderSettingsMigrator.migrateIfNeeded()
            try {
                readingSessionRepository.closeStaleSessions(epochClock.nowEpochMillis())
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                // Statistics recovery is best-effort and must never block the offline reader.
            }
        }
    }
}
