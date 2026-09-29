package com.xinyue.reader.core.domain.model

import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class Book(
    val id: String,
    val title: String,
    val author: String?,
    val originalFileName: String,
    val originalPath: String,
    val normalizedPath: String,
    val charsetName: String,
    val contentSha256: String,
    val contentLength: Long,
    val createdAtEpochMillis: Long,
    val lastOpenedAtEpochMillis: Long?,
    val seriesName: String? = null,
    val seriesOrder: Int? = null,
    /** Compatibility projection during V1.5 migration; collection membership is the durable truth. */
    val groupId: String? = null,
    val customCoverPath: String? = null,
    val finished: Boolean = false,
    @Transient val customCoverModel: File? = null,
)
