package com.xinyue.reader.core.data

import android.content.ContentResolver
import android.content.Context
import android.provider.OpenableColumns
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.FileNotFoundException
import java.io.OutputStream
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

interface BackupDocumentGateway {
    fun openForWrite(destinationUri: String): OutputStream
    fun invalidate(destinationUri: String): Boolean
    fun openForRead(sourceUri: String): InputStream = throw UnsupportedOperationException("读取未实现")
    fun querySize(sourceUri: String): Long? = null
}

@Singleton
class AndroidBackupDocumentGateway @Inject constructor(@ApplicationContext context: Context) : BackupDocumentGateway {
    private val resolver: ContentResolver = context.contentResolver

    override fun openForWrite(destinationUri: String): OutputStream =
        resolver.openOutputStream(destinationUri.toUri(), "wt")
            ?: throw FileNotFoundException("无法打开备份目标")

    override fun openForRead(sourceUri: String): InputStream =
        resolver.openInputStream(sourceUri.toUri()) ?: throw FileNotFoundException("无法打开备份来源")

    override fun querySize(sourceUri: String): Long? = runCatching {
        resolver.query(sourceUri.toUri(), arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst() || cursor.isNull(0)) null else cursor.getLong(0).takeIf { it >= 0 }
        }
    }.getOrNull()

    override fun invalidate(destinationUri: String): Boolean {
        val uri = destinationUri.toUri()
        if (runCatching { resolver.delete(uri, null, null) > 0 }.getOrDefault(false)) return true
        return runCatching {
            val output = resolver.openOutputStream(uri, "wt") ?: return@runCatching false
            output.use { }
            true
        }.getOrDefault(false)
    }
}
