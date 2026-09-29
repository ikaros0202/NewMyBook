package com.xinyue.reader.core.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import javax.inject.Inject
import javax.inject.Singleton

interface BackupUriPermissionManager {
    fun persistWrite(uriString: String)
    fun releaseWrite(uriString: String)
    fun persistRead(uriString: String)
    fun releaseRead(uriString: String)
}

@Singleton
class AndroidBackupUriPermissionManager @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext context: Context,
) : BackupUriPermissionManager {
    private val resolver = context.contentResolver

    override fun persistWrite(uriString: String) {
        resolver.takePersistableUriPermission(
            requireContentUri(uriString),
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
    }

    override fun releaseWrite(uriString: String) {
        runCatching {
            resolver.releasePersistableUriPermission(
                requireContentUri(uriString),
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
    }

    override fun persistRead(uriString: String) {
        resolver.takePersistableUriPermission(
            requireContentUri(uriString),
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
    }

    override fun releaseRead(uriString: String) {
        runCatching {
            resolver.releasePersistableUriPermission(
                requireContentUri(uriString),
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
    }

    private fun requireContentUri(value: String): Uri = Uri.parse(value).also {
        require(it.scheme == "content" && it.authority?.isNotBlank() == true) { "SAF URI 无效" }
    }
}
