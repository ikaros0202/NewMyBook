package com.xinyue.reader.core.data

import android.content.ContentResolver
import android.content.Context
import android.provider.OpenableColumns
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidImportSourceFactory @Inject constructor(
    @ApplicationContext context: Context,
) : ImportSourceFactory {
    private val contentResolver: ContentResolver = context.contentResolver

    override suspend fun create(uriString: String): ImportSource = withContext(Dispatchers.IO) {
        val uri = uriString.toUri()
        val metadata = contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            val name = if (nameIndex >= 0) cursor.getString(nameIndex) else null
            val size = if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) cursor.getLong(sizeIndex) else null
            name to size
        }

        val displayName = metadata?.first?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment
            ?: "未命名文件"

        val sizeBytes = metadata?.second
            ?: contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
            ?: ImportSource.UNKNOWN_SIZE_BYTES
        require(sizeBytes != 0L) { "所选文件不能为空" }

        ImportSource(
            displayName = displayName,
            sizeBytes = sizeBytes,
            openStream = {
                contentResolver.openInputStream(uri)
                    ?: error("无法打开所选文件")
            },
        )
    }
}
