package app.ochre.notes.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AttachmentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(attachment: AttachmentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(attachments: List<AttachmentEntity>)

    @Query("SELECT * FROM attachments WHERE memoUid = :memoUid AND deletedAt IS NULL ORDER BY createdAt ASC")
    suspend fun activeFor(memoUid: String): List<AttachmentEntity>

    @Query("SELECT * FROM attachments WHERE memoUid = :memoUid ORDER BY createdAt ASC")
    suspend fun allFor(memoUid: String): List<AttachmentEntity>

    @Query("SELECT * FROM attachments WHERE uid = :uid")
    suspend fun findByUid(uid: String): AttachmentEntity?

    @Query("SELECT * FROM attachments WHERE deletedAt IS NULL ORDER BY createdAt ASC")
    suspend fun allActive(): List<AttachmentEntity>

    @Query("UPDATE attachments SET deletedAt = :now WHERE uid = :uid")
    suspend fun markDeleted(uid: String, now: Long)

    @Query("UPDATE attachments SET deletedAt = NULL WHERE uid = :uid")
    suspend fun restore(uid: String)

    @Query("SELECT * FROM attachments WHERE deletedAt IS NOT NULL AND deletedAt < :before")
    suspend fun deletedOlderThan(before: Long): List<AttachmentEntity>

    @Query("DELETE FROM attachments WHERE uid = :uid")
    suspend fun hardDelete(uid: String)

    @Query("DELETE FROM attachments WHERE memoUid = :memoUid")
    suspend fun deleteForMemo(memoUid: String)
}

@Dao
interface TagDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tags: List<MemoTagEntity>)

    @Query("DELETE FROM memo_tags WHERE memoUid = :memoUid")
    suspend fun clearFor(memoUid: String)

    @Query(
        """
        SELECT t.tagNorm AS tagNorm, MIN(t.tagDisplay) AS tagDisplay, COUNT(*) AS count
        FROM memo_tags t
        JOIN memos m ON m.uid = t.memoUid
        WHERE m.deletedAt IS NULL
          AND m.archivedAt IS NULL
          AND (m.sealedUntil IS NULL OR m.sealedUntil <= :now)
        GROUP BY t.tagNorm
        ORDER BY count DESC, t.tagNorm ASC
        """
    )
    fun observeTagCounts(now: Long): Flow<List<TagCount>>

    @Query("SELECT tagNorm AS tagNorm, MIN(tagDisplay) AS tagDisplay, COUNT(*) AS count FROM memo_tags GROUP BY tagNorm ORDER BY count DESC")
    suspend fun allTagCounts(): List<TagCount>

    @Query("SELECT DISTINCT memoUid FROM memo_tags WHERE tagNorm = :tagNorm")
    suspend fun memoUidsWithTag(tagNorm: String): List<String>

    @Query("SELECT * FROM memo_tags WHERE memoUid = :memoUid")
    suspend fun tagsFor(memoUid: String): List<MemoTagEntity>

    @Query("DELETE FROM memo_tags WHERE tagNorm = :tagNorm")
    suspend fun clearTag(tagNorm: String)
}

@Dao
interface LinkDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(links: List<MemoLinkEntity>)

    @Query("DELETE FROM memo_links WHERE fromUid = :memoUid")
    suspend fun clearFor(memoUid: String)

    @Query("UPDATE memo_links SET targetTitleNorm = :newNorm, targetTitle = :newTitle WHERE targetTitleNorm = :oldNorm")
    suspend fun renameTarget(oldNorm: String, newNorm: String, newTitle: String)

    @androidx.room.RawQuery(observedEntities = [MemoLinkEntity::class, MemoEntity::class])
    fun observeOutgoing(query: androidx.sqlite.db.SupportSQLiteQuery): Flow<List<OutgoingLink>>

    @androidx.room.Transaction
    @Query(
        """
        SELECT m.* FROM memos m
        JOIN memo_links l ON l.fromUid = m.uid
        WHERE l.targetTitleNorm = :titleNorm
          AND m.deletedAt IS NULL
          AND m.archivedAt IS NULL
          AND (m.sealedUntil IS NULL OR m.sealedUntil <= :now)
        ORDER BY m.createdAt DESC
        """
    )
    fun observeBacklinks(titleNorm: String, now: Long): Flow<List<MemoWithMeta>>
}

@Dao
interface ReminderDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(reminder: ReminderEntity)

    @Query("SELECT * FROM reminders WHERE memoUid = :memoUid AND completedAt IS NULL ORDER BY fireAt ASC")
    suspend fun activeFor(memoUid: String): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE uid = :uid")
    suspend fun findByUid(uid: String): ReminderEntity?

    @Query("SELECT * FROM reminders WHERE memoUid = :memoUid")
    suspend fun allFor(memoUid: String): List<ReminderEntity>

    @Query(
        """
        SELECT * FROM reminders
        WHERE uid = :uid
        """
    )
    @androidx.room.Transaction
    fun observeWithMemo(uid: String): Flow<ReminderWithMemo?>

    @androidx.room.Transaction
    @Query(
        """
        SELECT * FROM reminders
        WHERE completedAt IS NULL AND fireAt > :now
          AND memoUid IN (SELECT uid FROM memos WHERE deletedAt IS NULL)
        ORDER BY fireAt ASC
        """
    )
    fun observeUpcoming(now: Long): Flow<List<ReminderWithMemo>>

    @androidx.room.Transaction
    @Query(
        """
        SELECT * FROM reminders
        WHERE completedAt IS NULL AND fireAt > :now
          AND memoUid IN (SELECT uid FROM memos WHERE deletedAt IS NULL AND (sealedUntil IS NULL OR sealedUntil <= :now))
        ORDER BY fireAt ASC LIMIT 1
        """
    )
    fun observeNext(now: Long): Flow<ReminderWithMemo?>

    @Query("DELETE FROM reminders WHERE uid = :uid")
    suspend fun delete(uid: String)

    @Query("DELETE FROM reminders WHERE memoUid = :memoUid")
    suspend fun deleteForMemo(memoUid: String)

    @Query("UPDATE reminders SET completedAt = :now WHERE uid = :uid")
    suspend fun markCompleted(uid: String, now: Long)

    @Query("SELECT * FROM reminders")
    suspend fun all(): List<ReminderEntity>
}
