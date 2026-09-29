package com.xinyue.reader.core.data

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImportUriPermissionManager @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val contentResolver = context.contentResolver

    fun persistRead(uriString: String) {
        val uri = uriString.toUri()
        if (contentResolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }) return
        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    fun releaseRead(uriString: String) {
        val uri = uriString.toUri()
        if (contentResolver.persistedUriPermissions.none { it.uri == uri && it.isReadPermission }) return
        contentResolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
