package com.xinyue.reader.core.data

import android.graphics.Typeface
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface FontValidator {
    suspend fun validate(file: File)
}

class AndroidFontValidator @Inject constructor() : FontValidator {
    override suspend fun validate(file: File) = withContext(Dispatchers.IO) {
        Typeface.createFromFile(file)
        Unit
    }
}
