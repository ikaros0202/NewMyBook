package com.xinyue.reader.core.database.entity

import androidx.room3.Entity
import androidx.room3.Fts5
import androidx.room3.FtsOptions

@Fts5(
    tokenizer = FtsOptions.TOKENIZER_TRIGRAM,
    contentEntity = SearchChunkEntity::class,
    contentRowId = "rowid",
)
@Entity(tableName = "search_chunks_fts")
data class SearchChunkFtsEntity(
    val content: String,
)
