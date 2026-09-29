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
import com.xinyue.reader.core.domain.model.BackupExportResult
import com.xinyue.reader.core.domain.model.BackupInspectResult
import com.xinyue.reader.core.domain.model.BackupOptions
import com.xinyue.reader.core.domain.model.BackupPhase
import com.xinyue.reader.core.domain.model.BackupProgress
import com.xinyue.reader.core.domain.model.RestoreRequest
import com.xinyue.reader.core.domain.model.RestoreResult
import com.xinyue.reader.core.domain.repository.BackupService
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BackupExportWorkerTest {
    @Test
    fun `success returns safe output releases grant and progress mapping is monotonic`() = runTest {
        val service = FakeBackupService(
            exportResult = BackupExportResult.Success("internal", "a".repeat(64), 42),
        )
        val permissions = FakePermissions()
        val result = worker(service, permissions).doWork()

        assertThat(result).isEqualTo(
            ListenableWorker.Result.success(
                Data.Builder()
                    .putString(BackupWork.KEY_RESULT_CODE, "SUCCESS")
                    .putString(BackupWork.KEY_ARCHIVE_SHA256, "a".repeat(64))
                    .putLong(BackupWork.KEY_BYTES_WRITTEN, 42)
                    .build(),
            ),
        )
        assertThat(permissions.released).containsExactly(DESTINATION)
        val first = BackupWork.progress(BackupProgress(BackupPhase.WRITING, 10, 100, "private-label"))
        val second = BackupWork.progress(BackupProgress(BackupPhase.WRITING, 2, 5, "private-label"), first.getLong(BackupWork.KEY_COMPLETED, 0))
        assertThat(second.getLong(BackupWork.KEY_COMPLETED, 0)).isEqualTo(10)
        assertThat(second.getLong(BackupWork.KEY_TOTAL, 0)).isAtLeast(10)
        assertThat(second.getString(BackupWork.KEY_LABEL)).isEqualTo("writing")
        assertThat(second.toString()).doesNotContain("private-label")
    }

    @Test
    fun `provider failure retries only while bounded and permission failure never retries`() = runTest {
        val provider = FakeBackupService(
            exportResult = BackupExportResult.Failure(BackupError(BackupErrorCode.PROVIDER, "redacted")),
        )
        val firstPermissions = FakePermissions()
        assertThat(worker(provider, firstPermissions, attempt = 0).doWork()).isEqualTo(ListenableWorker.Result.retry())
        assertThat(firstPermissions.released).isEmpty()

        val exhaustedPermissions = FakePermissions()
        assertThat(worker(provider, exhaustedPermissions, attempt = BackupWork.MAX_RETRIES).doWork()).isEqualTo(
            ListenableWorker.Result.failure(BackupWork.error(BackupErrorCode.PROVIDER)),
        )
        assertThat(exhaustedPermissions.released).containsExactly(DESTINATION)

        val permission = FakeBackupService(
            exportResult = BackupExportResult.Failure(BackupError(BackupErrorCode.PERMISSION, "redacted")),
        )
        assertThat(worker(permission, FakePermissions()).doWork()).isEqualTo(
            ListenableWorker.Result.failure(BackupWork.error(BackupErrorCode.PERMISSION)),
        )
    }

    @Test
    fun `invalid input fails before service and exposes no uri`() = runTest {
        val service = FakeBackupService(BackupExportResult.Cancelled)
        val result = worker(service, FakePermissions(), destination = "file:///private/path").doWork()
        assertThat(result).isEqualTo(ListenableWorker.Result.failure(BackupWork.error(BackupErrorCode.INVALID_FORMAT)))
        assertThat(service.exportCalls).isEqualTo(0)
        assertThat(result.toString()).doesNotContain("private/path")
    }

    private fun worker(
        service: FakeBackupService,
        permissions: FakePermissions,
        attempt: Int = 0,
        destination: String = DESTINATION,
    ): BackupExportWorker {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val factory = object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters,
            ): ListenableWorker = BackupExportWorker(appContext, workerParameters, service, permissions)
        }
        return TestListenableWorkerBuilder.from(context, BackupExportWorker::class.java)
            .setInputData(
                Data.Builder()
                    .putString(BackupWork.KEY_OPERATION_ID, "operation-1")
                    .putString(BackupWork.KEY_DESTINATION_URI, destination)
                    .putBoolean(BackupWork.KEY_INCLUDE_BOOK_TEXT, true)
                    .putBoolean(BackupWork.KEY_INCLUDE_FONTS, false)
                    .build(),
            )
            .setRunAttemptCount(attempt)
            .setWorkerFactory(factory)
            .build()
    }

    private class FakePermissions : BackupUriPermissionManager {
        val released = mutableListOf<String>()
        override fun persistWrite(uriString: String) = Unit
        override fun releaseWrite(uriString: String) { released += uriString }
        override fun persistRead(uriString: String) = Unit
        override fun releaseRead(uriString: String) = Unit
    }

    internal class FakeBackupService(var exportResult: BackupExportResult) : BackupService {
        var exportCalls = 0
        var restoreResult: RestoreResult = RestoreResult.Cancelled
        var restoreFailure: Throwable? = null
        var discardCalls = 0
        override suspend fun export(
            destinationUri: String,
            options: BackupOptions,
            onProgress: (BackupProgress) -> Unit,
        ): BackupExportResult {
            exportCalls++
            onProgress(BackupProgress(BackupPhase.PREPARING, 1, 2, "private"))
            onProgress(BackupProgress(BackupPhase.WRITING, 2, 2, "private"))
            return exportResult
        }
        override suspend fun inspect(sourceUri: String, onProgress: (BackupProgress) -> Unit) = BackupInspectResult.Cancelled
        override suspend fun restore(request: RestoreRequest, onProgress: (BackupProgress) -> Unit): RestoreResult {
            restoreFailure?.let { throw it }
            onProgress(BackupProgress(BackupPhase.RESTORING, 1, 2, "private"))
            return restoreResult
        }
        override suspend fun discardRestorePlan(stagedPlanToken: String): Boolean { discardCalls++; return true }
    }

    private companion object { const val DESTINATION = "content://documents/public-backup" }
}
