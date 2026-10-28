package app.ochre.notes.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Transaction
import androidx.room.Update
import androidx.sqlite.db.SupportSQLiteQuery
import kotlinx.coroutines.flow.Flow

@Dao
abstract class MemoDao {

    @Insert
    abstract suspend fun insert(memo: MemoEntity): Long

    @Update
    abstract suspend fun update(memo: MemoEntity)

    @Query("SELECT * FROM memos WHERE uid = :uid")
    abstract suspend fun findByUid(uid: String): MemoEntity?

    @Query("SELECT * FROM memos WHERE uid = :uid")
    abstract fun observeByUid(uid: String): Flow<MemoEntity?>

    @Transaction
    @Query("SELECT * FROM memos WHERE uid = :uid")
    abstract fun observeWithMeta(uid: String): Flow<MemoWithMeta?>

    @Transaction
    @Query("SELECT * FROM memos WHERE uid IN (:uids)")
    abstract suspend fun withMetaByUids(uids: List<String>): List<MemoWithMeta>

    @Transaction
    @RawQuery(observedEntities = [MemoEntity::class, MemoTagEntity::class, AttachmentEntity::class, ReminderEntity::class])
    abstract fun observeFeed(query: SupportSQLiteQuery): Flow<List<MemoWithMeta>>

    @Transaction
    @Query("SELECT * FROM memos WHERE sealedUntil IS NOT NULL AND deletedAt IS NULL ORDER BY sealedUntil ASC")
    abstract fun observeCapsules(): Flow<List<MemoWithMeta>>

    @Transaction
    @Query("SELECT * FROM memos WHERE sealedUntil IS NOT NULL AND sealedUntil > :now AND deletedAt IS NULL AND archivedAt IS NULL ORDER BY sealedUntil ASC")
    abstract fun observeLockedCapsules(now: Long): Flow<List<MemoWithMeta>>

    @Transaction
    @Query("SELECT * FROM memos WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    abstract fun observeTrash(): Flow<List<MemoWithMeta>>

    @Query("SELECT COUNT(*) FROM memos WHERE deletedAt IS NULL AND archivedAt IS NULL AND (sealedUntil IS NULL OR sealedUntil <= :now)")
    abstract fun observeActiveCount(now: Long): Flow<Int>

    @Query("SELECT * FROM memos WHERE deletedAt IS NULL AND archivedAt IS NULL AND (sealedUntil IS NULL OR sealedUntil <= :now) ORDER BY updatedAt DESC LIMIT 1")
    abstract suspend fun latestActive(now: Long): MemoEntity?

    @Query("SELECT COUNT(*) FROM memos WHERE deletedAt IS NULL")
    abstract suspend fun countActive(): Int

    @Query("SELECT COUNT(*) FROM memos")
    abstract suspend fun countAll(): Int

    @Query("SELECT MAX(updatedAt) FROM memos")
    abstract suspend fun maxUpdatedAt(): Long?

    @Query("SELECT * FROM memos WHERE deletedAt IS NULL AND archivedAt IS NULL AND (sealedUntil IS NULL OR sealedUntil <= :now) AND createdAt <= :createdBefore ORDER BY createdAt ASC LIMIT 2000")
    abstract suspend fun echoCandidates(now: Long, createdBefore: Long): List<MemoEntity>

    @Query("SELECT * FROM memos WHERE titleNorm = :titleNorm AND deletedAt IS NULL ORDER BY updatedAt DESC LIMIT 1")
    abstract suspend fun findByTitleNorm(titleNorm: String): MemoEntity?

    @Query("SELECT * FROM memos WHERE deletedAt IS NOT NULL AND deletedAt < :before")
    abstract suspend fun trashedOlderThan(before: Long): List<MemoEntity>

    @Query("SELECT * FROM memos")
    abstract suspend fun allMemos(): List<MemoEntity>

    @Query("SELECT * FROM memos WHERE uid IN (:uids)")
    abstract suspend fun findByUids(uids: List<String>): List<MemoEntity>

    @Query("UPDATE memos SET pinnedAt = :pinnedAt, updatedAt = :now WHERE uid = :uid")
    abstract suspend fun setPinned(uid: String, pinnedAt: Long?, now: Long)

    @Query("UPDATE memos SET archivedAt = :archivedAt, updatedAt = :now WHERE uid = :uid")
    abstract suspend fun setArchived(uid: String, archivedAt: Long?, now: Long)

    @Query("UPDATE memos SET deletedAt = :deletedAt, updatedAt = :now WHERE uid = :uid")
    abstract suspend fun setDeleted(uid: String, deletedAt: Long?, now: Long)

    @Query("UPDATE memos SET sealedUntil = :sealedUntil, updatedAt = :now WHERE uid = :uid")
    abstract suspend fun setSealed(uid: String, sealedUntil: Long?, now: Long)

    @Query("UPDATE memos SET openedAt = :now WHERE uid = :uid")
    abstract suspend fun markOpened(uid: String, now: Long)

    @Query("UPDATE memos SET echoedAt = :now WHERE uid = :uid")
    abstract suspend fun markEchoed(uid: String, now: Long)

    @Query("UPDATE memos SET echoedAt = :now, openedAt = :now, echoLevel = :level WHERE uid = :uid")
    abstract suspend fun keepEcho(uid: String, now: Long, level: Int)

    @Query("UPDATE memos SET updatedAt = :now WHERE uid = :uid")
    abstract suspend fun touch(uid: String, now: Long)

    @Query("DELETE FROM memos WHERE uid = :uid")
    abstract suspend fun hardDelete(uid: String)

    @Query("DELETE FROM memos WHERE deletedAt IS NOT NULL AND deletedAt < :before")
    abstract suspend fun purgeTrash(before: Long)

    @Query("SELECT COUNT(*) FROM memos WHERE deletedAt IS NULL AND archivedAt IS NULL AND (sealedUntil IS NULL OR sealedUntil <= :now) AND createdAt >= :since")
    abstract fun observeCreatedSince(now: Long, since: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM memos WHERE deletedAt IS NULL AND archivedAt IS NULL AND (sealedUntil IS NULL OR sealedUntil <= :now) AND createdAt >= :since")
    abstract suspend fun countCreatedSince(now: Long, since: Long): Int
}
