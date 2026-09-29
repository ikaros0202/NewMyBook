package com.xinyue.reader.core.data

import com.xinyue.reader.core.database.dao.BookDao
import com.xinyue.reader.core.database.dao.BookGroupDao
import com.xinyue.reader.core.database.entity.BookGroupEntity
import com.xinyue.reader.core.database.entity.BookGroupMembershipEntity
import com.xinyue.reader.core.database.mapper.toDomain
import com.xinyue.reader.core.domain.model.BookGroup
import com.xinyue.reader.core.domain.model.BookCollectionMembership
import com.xinyue.reader.core.domain.repository.BookGroupRepository
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

@Singleton
class RoomBookGroupRepository @Inject constructor(
    private val groupDao: BookGroupDao,
    private val bookDao: BookDao,
) : BookGroupRepository {
    override fun observeAll(): Flow<List<BookGroup>> =
        groupDao.observeAll().map { groups -> groups.map(BookGroupEntity::toDomain) }

    override fun observeMemberships(): Flow<List<BookCollectionMembership>> =
        groupDao.observeMemberships().map { memberships -> memberships.map(BookGroupMembershipEntity::toDomain) }

    override suspend fun create(name: String): BookGroup {
        val normalizedName = normalizeName(name)
        requireUniqueName(normalizedName, excludingGroupId = null)
        val now = System.currentTimeMillis()
        val group = BookGroupEntity(
            id = UUID.randomUUID().toString(),
            name = normalizedName,
            sortOrder = groupDao.maxSortOrder() + 1,
            createdAtEpochMillis = now,
            updatedAtEpochMillis = now,
        )
        groupDao.insert(group)
        return group.toDomain()
    }

    override suspend fun rename(collectionId: String, name: String) {
        require(groupDao.get(collectionId) != null) { "找不到集合" }
        val normalizedName = normalizeName(name)
        requireUniqueName(normalizedName, excludingGroupId = collectionId)
        groupDao.rename(collectionId, normalizedName, System.currentTimeMillis())
    }

    override suspend fun delete(collectionId: String) {
        require(groupDao.get(collectionId) != null) { "找不到集合" }
        groupDao.deleteAndUnassign(collectionId)
    }

    override suspend fun addBooksToCollections(bookIds: Set<String>, collectionIds: Set<String>) {
        validateMembershipTargets(bookIds, collectionIds)
        groupDao.addMemberships(crossProduct(bookIds, collectionIds))
    }

    override suspend fun removeBooksFromCollections(bookIds: Set<String>, collectionIds: Set<String>) {
        validateMembershipTargets(bookIds, collectionIds)
        groupDao.removeMemberships(bookIds, collectionIds)
    }

    override suspend fun replaceCollectionsForBooks(bookIds: Set<String>, collectionIds: Set<String>) {
        validateMembershipTargets(bookIds, collectionIds)
        groupDao.replaceMemberships(bookIds, crossProduct(bookIds, collectionIds))
    }

    override suspend fun moveBooks(bookIds: Set<String>, groupId: String?) {
        replaceCollectionsForBooks(bookIds, groupId?.let(::setOf).orEmpty())
    }

    private suspend fun validateMembershipTargets(bookIds: Set<String>, collectionIds: Set<String>) {
        if (bookIds.isEmpty()) return
        require(bookDao.countByIds(bookIds) == bookIds.size) { "包含不存在的书籍" }
        require(collectionIds.all { groupDao.get(it) != null }) { "找不到目标集合" }
    }

    private fun crossProduct(
        bookIds: Set<String>,
        collectionIds: Set<String>,
    ): List<BookGroupMembershipEntity> = bookIds.flatMap { bookId ->
        collectionIds.map { collectionId -> BookGroupMembershipEntity(bookId, collectionId) }
    }

    private suspend fun requireUniqueName(name: String, excludingGroupId: String?) {
        val duplicate = groupDao.observeAll().first().any { group ->
            group.id != excludingGroupId && group.name.equals(name, ignoreCase = true)
        }
        require(!duplicate) { "分组名称已存在" }
    }

    private fun normalizeName(name: String): String {
        val normalized = name.trim()
        require(normalized.isNotEmpty()) { "分组名称不能为空" }
        require(normalized.codePointCount(0, normalized.length) <= MAX_GROUP_NAME_CODE_POINTS) {
            "分组名称不能超过 40 个字符"
        }
        return normalized
    }

    private companion object {
        const val MAX_GROUP_NAME_CODE_POINTS = 40
    }
}
