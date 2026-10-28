package app.ochre.notes.data.backup

import app.ochre.notes.data.local.AttachmentEntity
import app.ochre.notes.data.local.AttachmentKind
import app.ochre.notes.data.local.LinkDao
import app.ochre.notes.data.local.MemoEntity
import app.ochre.notes.data.local.MemoLinkEntity
import app.ochre.notes.data.local.MemoTagEntity
import app.ochre.notes.data.local.OchreDatabase
import app.ochre.notes.data.local.ReminderEntity
import app.ochre.notes.data.local.TagDao
import app.ochre.notes.data.media.AttachmentStore
import app.ochre.notes.domain.MarkdownUtils
import androidx.room.withTransaction
import java.io.InputStream

data class MergeCounts(
    val imported: Int = 0,
    val updated: Int = 0,
    val skipped: Int = 0,
)

/**
 * Applies incoming vault records with newest-wins semantics, refreshing derived tags and links.
 * Shared by file restore and remote sync so both paths behave identically.
 */
class VaultMerger(
    private val db: OchreDatabase,
    private val attachmentStore: AttachmentStore,
) {

    suspend fun mergeMemos(memos: List<BackupMemo>): MergeCounts {
        var imported = 0
        var updated = 0
        var skipped = 0
        val memoDao = db.memoDao()
        val tagDao = db.tagDao()
        val linkDao = db.linkDao()
        memos.forEach { remote ->
            db.withTransaction {
                val local = memoDao.findByUid(remote.uid)
                when {
                    local == null -> {
                        if (remote.deletedAt != null) {
                            skipped++
                        } else {
                            memoDao.insert(remote.toEntity())
                            replaceDerived(remote, tagDao, linkDao)
                            imported++
                        }
                    }
                    remote.updatedAt > local.updatedAt -> {
                        memoDao.update(remote.toEntity(id = local.id))
                        replaceDerived(remote, tagDao, linkDao)
                        updated++
                    }
                    else -> skipped++
                }
            }
        }
        return MergeCounts(imported, updated, skipped)
    }

    suspend fun mergeReminders(reminders: List<BackupReminder>): Int {
        var count = 0
        val dao = db.reminderDao()
        reminders.forEach { remote ->
            val exists = dao.findByUid(remote.uid) != null
            if (!exists && db.memoDao().findByUid(remote.memoUid) != null) {
                dao.upsert(remote.toEntity())
                count++
            }
        }
        return count
    }

    suspend fun importAttachment(meta: BackupAttachment, bytes: InputStream): Boolean {
        if (db.attachmentDao().findByUid(meta.uid) != null) return false
        if (db.memoDao().findByUid(meta.memoUid) == null) return false
        val kind = runCatching { AttachmentKind.valueOf(meta.kind) }.getOrDefault(AttachmentKind.FILE)
        val entity = runCatching {
            attachmentStore.importFromStream(
                input = bytes,
                uid = meta.uid,
                memoUid = meta.memoUid,
                kind = kind,
                fileName = meta.fileName,
                originalName = meta.originalName,
                mimeType = meta.mimeType,
                createdAt = meta.createdAt,
                sizeBytes = meta.sizeBytes,
                width = meta.width,
                height = meta.height,
                durationMs = meta.durationMs,
                waveform = meta.waveform,
            )
        }.getOrNull() ?: return false
        db.attachmentDao().insert(entity)
        return true
    }

    private suspend fun replaceDerived(memo: BackupMemo, tagDao: TagDao, linkDao: LinkDao) {
        val parsed = MarkdownUtils.parse(memo.content)
        tagDao.clearFor(memo.uid)
        if (parsed.tags.isNotEmpty()) {
            tagDao.insertAll(parsed.tags.map { MemoTagEntity(memo.uid, it.norm, it.display) })
        }
        linkDao.clearFor(memo.uid)
        val links = parsed.links
            .filter { it.norm.isNotBlank() && it.norm != "untitled" }
            .map { MemoLinkEntity(memo.uid, it.norm, it.title) }
        if (links.isNotEmpty()) linkDao.insertAll(links)
    }
}

internal fun MemoEntity.toBackupMemo() = BackupMemo(
    uid = uid,
    content = content,
    title = title,
    titleNorm = titleNorm,
    createdAt = createdAt,
    updatedAt = updatedAt,
    pinnedAt = pinnedAt,
    archivedAt = archivedAt,
    deletedAt = deletedAt,
    sealedUntil = sealedUntil,
    openedAt = openedAt,
    echoLevel = echoLevel,
    echoedAt = echoedAt,
)

internal fun BackupMemo.toEntity(id: Long = 0) = MemoEntity(
    id = id,
    uid = uid,
    content = content,
    title = title,
    titleNorm = titleNorm,
    createdAt = createdAt,
    updatedAt = updatedAt,
    pinnedAt = pinnedAt,
    archivedAt = archivedAt,
    deletedAt = deletedAt,
    sealedUntil = sealedUntil,
    openedAt = openedAt,
    echoLevel = echoLevel,
    echoedAt = echoedAt,
)

internal fun AttachmentEntity.toBackupAttachment() = BackupAttachment(
    uid = uid,
    memoUid = memoUid,
    kind = kind.name,
    fileName = fileName,
    originalName = originalName,
    mimeType = mimeType,
    sizeBytes = sizeBytes,
    width = width,
    height = height,
    durationMs = durationMs,
    waveform = waveform,
    createdAt = createdAt,
)

internal fun ReminderEntity.toBackupReminder() = BackupReminder(uid, memoUid, fireAt, createdAt, completedAt)

internal fun BackupReminder.toEntity() = ReminderEntity(uid, memoUid, fireAt, createdAt, completedAt)