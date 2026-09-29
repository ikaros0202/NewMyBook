package com.xinyue.reader.core.database.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import com.xinyue.reader.core.database.entity.BookGroupEntity
import com.xinyue.reader.core.database.entity.BookGroupMembershipEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class BookGroupDao {
    @Query(
        "SELECT * FROM book_groups " +
            "ORDER BY sortOrder ASC, createdAtEpochMillis ASC, id ASC",
    )
    abstract fun observeAll(): Flow<List<BookGroupEntity>>

    @Query("SELECT * FROM book_groups ORDER BY id")
    abstract suspend fun getAllForBackup(): List<BookGroupEntity>

    @Query("SELECT * FROM book_group_memberships ORDER BY bookId ASC, groupId ASC")
    abstract fun observeMemberships(): Flow<List<BookGroupMembershipEntity>>

    @Query("SELECT * FROM book_group_memberships ORDER BY bookId ASC, groupId ASC")
    abstract suspend fun getAllMembershipsForBackup(): List<BookGroupMembershipEntity>

    @Query("SELECT * FROM book_groups WHERE id = :groupId LIMIT 1")
    abstract suspend fun get(groupId: String): BookGroupEntity?

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM book_groups")
    abstract suspend fun maxSortOrder(): Int

    @Insert
    protected abstract suspend fun insertStored(group: BookGroupEntity)

    @Transaction
    open suspend fun insert(group: BookGroupEntity) {
        val normalizedName = group.name.trim()
        require(normalizedName.isNotEmpty()) { "分组名称不能为空" }
        insertStored(group.copy(name = normalizedName))
    }

    @Query(
        "UPDATE book_groups SET name = :name, updatedAtEpochMillis = :updatedAtEpochMillis " +
            "WHERE id = :groupId",
    )
    protected abstract suspend fun renameStored(groupId: String, name: String, updatedAtEpochMillis: Long)

    @Transaction
    open suspend fun rename(groupId: String, name: String, updatedAtEpochMillis: Long) {
        val normalizedName = name.trim()
        require(normalizedName.isNotEmpty()) { "分组名称不能为空" }
        renameStored(groupId, normalizedName, updatedAtEpochMillis)
    }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertMembershipsStored(memberships: List<BookGroupMembershipEntity>)

    @Query(
        "DELETE FROM book_group_memberships " +
            "WHERE bookId IN (:bookIds) AND groupId IN (:groupIds)",
    )
    protected abstract suspend fun deleteMembershipsStored(bookIds: Set<String>, groupIds: Set<String>)

    @Query("DELETE FROM book_group_memberships WHERE bookId IN (:bookIds)")
    protected abstract suspend fun deleteAllMembershipsForBooksStored(bookIds: Set<String>)

    @Transaction
    open suspend fun addMemberships(memberships: List<BookGroupMembershipEntity>) {
        if (memberships.isEmpty()) return
        insertMembershipsStored(memberships.distinct())
    }

    @Transaction
    open suspend fun removeMemberships(bookIds: Set<String>, groupIds: Set<String>) {
        if (bookIds.isEmpty() || groupIds.isEmpty()) return
        deleteMembershipsStored(bookIds, groupIds)
    }

    @Transaction
    open suspend fun replaceMemberships(
        bookIds: Set<String>,
        memberships: List<BookGroupMembershipEntity>,
    ) {
        if (bookIds.isEmpty()) return
        deleteAllMembershipsForBooksStored(bookIds)
        insertMembershipsStored(memberships.distinct())
    }

    @Query("DELETE FROM book_groups WHERE id = :groupId")
    protected abstract suspend fun deleteStored(groupId: String)

    @Transaction
    open suspend fun deleteAndUnassign(groupId: String) {
        deleteStored(groupId)
    }
}
