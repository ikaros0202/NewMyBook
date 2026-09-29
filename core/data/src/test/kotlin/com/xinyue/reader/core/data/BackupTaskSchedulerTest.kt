package com.xinyue.reader.core.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.google.common.truth.Truth.assertThat
import com.xinyue.reader.core.domain.model.BackupOptions
import com.xinyue.reader.core.domain.model.HandoffExportRequest
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BackupTaskSchedulerTest {
    private lateinit var context: Context
    private lateinit var workManager: WorkManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
        workManager = WorkManager.getInstance(context)
    }

    @Test
    fun `recreated scheduler observes same unique export without second operation`() = runBlocking {
        val permissions = RecordingPermissions()
        val requestStore = RestoreRequestStore(File(context.cacheDir, "scheduler-${System.nanoTime()}").apply { mkdirs() })
        WorkManagerBackupTaskScheduler(workManager, permissions, requestStore).enqueueExport(
            "stable-operation", "content://documents/public-backup", BackupOptions(),
        )

        val recreated = WorkManagerBackupTaskScheduler(workManager, permissions, requestStore)
        val state = recreated.observeExport("stable-operation").first { it != null }
        recreated.enqueueExport("stable-operation", "content://documents/public-backup", BackupOptions())
        val infos = workManager.getWorkInfosForUniqueWork("xinyue_backup_export_stable-operation").get()

        assertThat(state?.operationId).isEqualTo("stable-operation")
        assertThat(state?.type).isEqualTo(BackupWorkType.EXPORT)
        assertThat(infos).hasSize(1)
    }

    @Test
    fun `handoff export is durable unique work with a distinct identity`() = runBlocking {
        val permissions = RecordingPermissions()
        val root = File(context.cacheDir, "handoff-scheduler-${System.nanoTime()}").apply { mkdirs() }
        val scheduler = WorkManagerHandoffTaskScheduler(
            workManager,
            permissions,
            HandoffImportRequestStore(root),
        )
        scheduler.enqueueExport(
            "handoff-operation",
            "content://documents/public-handoff",
            HandoffExportRequest("book-1", includeBookText = false),
        )

        val state = scheduler.observeExport("handoff-operation").first { it != null }
        scheduler.enqueueExport(
            "handoff-operation",
            "content://documents/public-handoff",
            HandoffExportRequest("book-1", includeBookText = false),
        )
        val infos = workManager.getWorkInfosForUniqueWork(
            "xinyue_handoff_export_handoff-operation",
        ).get()

        assertThat(state?.type).isEqualTo(HandoffWorkType.EXPORT)
        assertThat(infos).hasSize(1)
    }

    private class RecordingPermissions : BackupUriPermissionManager {
        override fun persistWrite(uriString: String) = Unit
        override fun releaseWrite(uriString: String) = Unit
        override fun persistRead(uriString: String) = Unit
        override fun releaseRead(uriString: String) = Unit
    }
}
