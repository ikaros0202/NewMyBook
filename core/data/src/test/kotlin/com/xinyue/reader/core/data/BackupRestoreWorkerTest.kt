package com.xinyue.reader.core.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.google.common.truth.Truth.assertThat
import com.xinyue.reader.core.domain.model.BackupError
import com.xinyue.reader.core.domain.model.BackupErrorCode
import com.xinyue.reader.core.domain.model.RestoreMode
import com.xinyue.reader.core.domain.model.RestoreRequest
import com.xinyue.reader.core.domain.model.RestoreResult
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertFailsWith

@RunWith(RobolectricTestRunner::class)
class BackupRestoreWorkerTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun `success loads private confirmed request emits typed counts and cleans descriptor`() = runTest {
        val fixture = fixture()
        fixture.service.restoreResult = RestoreResult.Success(3, 2, 1, 4)

        val result = fixture.worker.doWork()

        assertThat(result).isEqualTo(
            ListenableWorker.Result.success(
                Data.Builder()
                    .putString(BackupWork.KEY_RESULT_CODE, "SUCCESS")
                    .putInt(BackupWork.KEY_RESTORED_COUNT, 3)
                    .putInt(BackupWork.KEY_SKIPPED_COUNT, 2)
                    .putInt(BackupWork.KEY_CONFLICT_COPY_COUNT, 1)
                    .putInt(BackupWork.KEY_METADATA_ONLY_BOOK_COUNT, 4)
                    .build(),
            ),
        )
        assertThat(fixture.requests.load(TOKEN)).isNull()
        assertThat(fixture.service.discardCalls).isEqualTo(1)
    }

    @Test
    fun `provider error retries preserving descriptor but conflict is terminal`() = runTest {
        val retry = fixture()
        retry.service.restoreResult = RestoreResult.Failure(BackupError(BackupErrorCode.PROVIDER, "redacted"))
        assertThat(retry.worker.doWork()).isEqualTo(ListenableWorker.Result.retry())
        assertThat(retry.requests.load(TOKEN)).isNotNull()
        assertThat(retry.service.discardCalls).isEqualTo(0)

        val conflict = fixture("conflict")
        conflict.service.restoreResult = RestoreResult.Failure(BackupError(BackupErrorCode.CONFLICT, "redacted"))
        assertThat(conflict.worker.doWork()).isEqualTo(
            ListenableWorker.Result.failure(BackupWork.error(BackupErrorCode.CONFLICT)),
        )
        assertThat(conflict.requests.load(TOKEN)).isNull()
    }

    @Test
    fun `cancellation rethrows after cleanup`() = runTest {
        val fixture = fixture("cancel")
        fixture.service.restoreFailure = CancellationException("cancelled")

        assertFailsWith<CancellationException> { fixture.worker.doWork() }
        assertThat(fixture.requests.load(TOKEN)).isNull()
        assertThat(fixture.service.discardCalls).isAtLeast(1)
    }

    @Test
    fun `missing confirmed descriptor fails before live restore`() = runTest {
        val fixture = fixture("missing")
        fixture.requests.delete(TOKEN)
        assertThat(fixture.worker.doWork()).isEqualTo(
            ListenableWorker.Result.failure(BackupWork.error(BackupErrorCode.CONFLICT)),
        )
        assertThat(fixture.service.discardCalls).isEqualTo(0)
    }

    private fun fixture(name: String = "success"): Fixture {
        val privateRoot = temporary.newFolder(name)
        val staging = File(privateRoot, "backup-staging").apply { mkdirs() }
        File(staging, "import-$TOKEN").mkdirs()
        val requests = RestoreRequestStore(staging)
        requests.save(RestoreRequest(TOKEN, RestoreMode.MERGE, emptyList()))
        val service = BackupExportWorkerTest.FakeBackupService(com.xinyue.reader.core.domain.model.BackupExportResult.Cancelled)
        val database = object : RestoreDatabaseGateway {
            override suspend fun snapshot() = BackupCatalogSnapshot()
            override suspend fun replaceAll(snapshot: BackupCatalogSnapshot) = Unit
        }
        val registry = StagedRestorePlanRegistry()
        val recovery = RestoreRecovery(
            privateRoot, database, RestorePlanResolver { _, _ -> error("not used") },
            RestoreJournalStore(privateRoot), RestoreSnapshotStore(privateRoot), registry,
        )
        val context = ApplicationProvider.getApplicationContext<Context>()
        val factory = object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters,
            ): ListenableWorker = BackupRestoreWorker(appContext, workerParameters, service, recovery, requests)
        }
        val worker = TestListenableWorkerBuilder.from(context, BackupRestoreWorker::class.java)
            .setInputData(
                Data.Builder()
                    .putString(BackupWork.KEY_OPERATION_ID, "restore-operation")
                    .putString(BackupWork.KEY_STAGED_PLAN_TOKEN, TOKEN)
                    .build(),
            )
            .setWorkerFactory(factory)
            .build()
        return Fixture(worker, service, requests)
    }

    private data class Fixture(
        val worker: BackupRestoreWorker,
        val service: BackupExportWorkerTest.FakeBackupService,
        val requests: RestoreRequestStore,
    )

    private companion object { const val TOKEN = "staged-token" }
}
