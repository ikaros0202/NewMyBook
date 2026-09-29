package com.xinyue.reader.core.data

interface ImportSourceFactory {
    suspend fun create(uriString: String): ImportSource
}
