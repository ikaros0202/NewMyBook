package com.xinyue.reader.core.database.mapper

import com.xinyue.reader.core.database.entity.BookEntity
import com.xinyue.reader.core.database.entity.BookGroupEntity
import com.xinyue.reader.core.database.entity.BookGroupMembershipEntity
import com.xinyue.reader.core.database.entity.ReadingSessionEntity
import com.xinyue.reader.core.database.entity.ReadingProgressEntity
import com.xinyue.reader.core.domain.model.Book
import com.xinyue.reader.core.domain.model.BookGroup
import com.xinyue.reader.core.domain.model.BookCollectionMembership
import com.xinyue.reader.core.domain.model.ReadingProgress
import com.xinyue.reader.core.domain.model.ReadingSession
import com.xinyue.reader.core.domain.model.TextAnchor

fun BookEntity.toDomain(): Book = Book(
    id = id,
    title = title,
    author = author,
    originalFileName = originalFileName,
    originalPath = originalPath,
    normalizedPath = normalizedPath,
    charsetName = charsetName,
    contentSha256 = contentSha256,
    contentLength = contentLength,
    createdAtEpochMillis = createdAtEpochMillis,
    lastOpenedAtEpochMillis = lastOpenedAtEpochMillis,
    seriesName = seriesName,
    seriesOrder = seriesOrder,
    groupId = groupId,
    customCoverPath = customCoverPath,
    finished = finished,
)

fun BookGroupEntity.toDomain(): BookGroup = BookGroup(
    id = id,
    name = name,
    sortOrder = sortOrder,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
)

fun BookGroupMembershipEntity.toDomain(): BookCollectionMembership = BookCollectionMembership(
    bookId = bookId,
    collectionId = groupId,
)

fun ReadingSessionEntity.toDomain(): ReadingSession = ReadingSession(
    id = id,
    bookId = bookId,
    startedAtEpochMillis = startedAtEpochMillis,
    lastInteractionAtEpochMillis = lastInteractionAtEpochMillis,
    endedAtEpochMillis = endedAtEpochMillis,
    activeMillis = activeMillis,
)

fun ReadingProgressEntity.toDomain(): ReadingProgress = ReadingProgress(
    bookId = bookId,
    anchor = TextAnchor(
        offset = offset,
        contextHash = contextHash,
        prefix = prefix,
        suffix = suffix,
    ),
    contentLength = contentLength,
    updatedAtEpochMillis = updatedAtEpochMillis,
)

fun Book.toEntity(): BookEntity = BookEntity(
    id = id,
    title = title,
    author = author,
    originalFileName = originalFileName,
    originalPath = originalPath,
    normalizedPath = normalizedPath,
    charsetName = charsetName,
    contentSha256 = contentSha256,
    contentLength = contentLength,
    createdAtEpochMillis = createdAtEpochMillis,
    lastOpenedAtEpochMillis = lastOpenedAtEpochMillis,
    seriesName = seriesName,
    seriesOrder = seriesOrder,
    groupId = groupId,
    customCoverPath = customCoverPath,
    finished = finished,
)

fun BookGroup.toEntity(): BookGroupEntity = BookGroupEntity(
    id = id,
    name = name,
    sortOrder = sortOrder,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
)

fun ReadingSession.toEntity(): ReadingSessionEntity = ReadingSessionEntity(
    id = id,
    bookId = bookId,
    startedAtEpochMillis = startedAtEpochMillis,
    lastInteractionAtEpochMillis = lastInteractionAtEpochMillis,
    endedAtEpochMillis = endedAtEpochMillis,
    activeMillis = activeMillis,
)

fun ReadingProgress.toEntity(): ReadingProgressEntity = ReadingProgressEntity(
    bookId = bookId,
    offset = anchor.offset,
    contextHash = anchor.contextHash,
    prefix = anchor.prefix,
    suffix = anchor.suffix,
    contentLength = contentLength,
    updatedAtEpochMillis = updatedAtEpochMillis,
)
