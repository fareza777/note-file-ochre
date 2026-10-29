package app.ochre.notes.data

import android.net.Uri
import androidx.room.withTransaction
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import app.ochre.notes.data.local.AttachmentDao
import app.ochre.notes.data.local.AttachmentEntity
import app.ochre.notes.data.local.AttachmentKind
import app.ochre.notes.data.local.LinkDao
import app.ochre.notes.data.local.MemoDao
import app.ochre.notes.data.local.MemoEntity
import app.ochre.notes.data.local.MemoLinkEntity
import app.ochre.notes.data.local.MemoTagEntity
import app.ochre.notes.data.local.MemoWithMeta
import app.ochre.notes.data.local.OchreDatabase
import app.ochre.notes.data.local.OutgoingLink
import app.ochre.notes.data.local.ReminderDao
import app.ochre.notes.data.local.ReminderEntity
import app.ochre.notes.data.local.TagDao
import app.ochre.notes.data.media.AttachmentStore
import app.ochre.notes.domain.EchoPlanner
import app.ochre.notes.domain.FeedFilter
import app.ochre.notes.domain.FeedOrder
import app.ochre.notes.domain.MarkdownUtils
import app.ochre.notes.domain.MemoUi
import app.ochre.notes.domain.OutgoingLinkUi
import app.ochre.notes.domain.SearchQuery
import app.ochre.notes.domain.TagUi
import app.ochre.notes.domain.toUi
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

class NoteRepository(
    private val db: OchreDatabase,
    private val attachmentStore: AttachmentStore,
) {
    private val memoDao: MemoDao = db.memoDao()
    private val attachmentDao: AttachmentDao = db.attachmentDao()
    private val tagDao: TagDao = db.tagDao()
    private val linkDao: LinkDao = db.linkDao()
    private val reminderDao: ReminderDao = db.reminderDao()

    val tags: Flow<List<TagUi>> = nowTicker()
        .flatMapLatest { now -> tagDao.observeTagCounts(now) }
        .map { counts -> counts.map { TagUi(it.tagNorm, it.tagDisplay, it.count) } }
        .distinctUntilChanged()

    fun observeFeed(
        filter: FeedFilter,
        query: SearchQuery,
        limit: Int = 60,
        order: FeedOrder = FeedOrder.NEWEST,
    ): Flow<List<MemoUi>> =
        nowTicker()
            .flatMapLatest { now -> memoDao.observeFeed(buildFeedQuery(filter, query, limit, now, order)) }
            .map(::toUiList)
            .distinctUntilChanged()

    fun observeMemo(uid: String): Flow<MemoUi?> =
        memoDao.observeWithMeta(uid).map { it?.toUi() }.distinctUntilChanged()

    fun observeBacklinks(titleNorm: String): Flow<List<MemoUi>> =
        nowTicker()
            .flatMapLatest { now ->
                if (titleNorm.isBlank() || titleNorm == UNTITLED) {
                    flow { emit(emptyList()) }
                } else {
                    linkDao.observeBacklinks(titleNorm, now)
                }
            }
            .map(::toUiList)
            .distinctUntilChanged()

    fun observeOutgoingLinks(uid: String): Flow<List<OutgoingLinkUi>> {
        val sql = """
            SELECT l.targetTitleNorm AS titleNorm, l.targetTitle AS title, m.uid AS targetUid
            FROM memo_links l
            LEFT JOIN memos m ON m.titleNorm = l.targetTitleNorm AND m.deletedAt IS NULL
            WHERE l.fromUid = ?
            GROUP BY l.targetTitleNorm
            ORDER BY l.targetTitle ASC
        """.trimIndent()
        return linkDao.observeOutgoing(SimpleSQLiteQuery(sql, arrayOf(uid)))
            .map { links -> links.map { OutgoingLinkUi(it.title, it.targetUid) } }
            .distinctUntilChanged()
    }

    fun observeTrash(): Flow<List<MemoUi>> =
        memoDao.observeTrash().map(::toUiList).distinctUntilChanged()

    fun observeCapsules(): Flow<List<MemoUi>> =
        memoDao.observeCapsules().map(::toUiList).distinctUntilChanged()

    fun observeActiveCount(): Flow<Int> = nowTicker().flatMapLatest { memoDao.observeActiveCount(it) }

    suspend fun countAll(): Int = memoDao.countAll()

    suspend fun createMemo(content: String, createdAt: Long = System.currentTimeMillis()): String =
        db.withTransaction {
            val uid = UUID.randomUUID().toString()
            val parsed = MarkdownUtils.parse(content)
            val memo = MemoEntity(
                uid = uid,
                content = content,
                title = parsed.title,
                titleNorm = parsed.titleNorm,
                createdAt = createdAt,
                updatedAt = createdAt,
            )
            memoDao.insert(memo)
            replaceTags(uid, parsed.tags.map { MemoTagEntity(uid, it.norm, it.display) })
            replaceLinks(
                uid,
                parsed.links
                    .filter { it.norm.isNotBlank() && it.norm != UNTITLED }
                    .map { MemoLinkEntity(uid, it.norm, it.title) },
            )
            uid
        }

    suspend fun createMemoWithAttachments(
        content: String,
        staged: List<AttachmentEntity>,
        createdAt: Long = System.currentTimeMillis(),
    ): String = db.withTransaction {
        val uid = UUID.randomUUID().toString()
        val parsed = MarkdownUtils.parse(content)
        memoDao.insert(
            MemoEntity(
                uid = uid,
                content = content,
                title = parsed.title,
                titleNorm = parsed.titleNorm,
                createdAt = createdAt,
                updatedAt = createdAt,
            ),
        )
        replaceTags(uid, parsed.tags.map { MemoTagEntity(uid, it.norm, it.display) })
        replaceLinks(
            uid,
            parsed.links
                .filter { it.norm.isNotBlank() && it.norm != UNTITLED }
                .map { MemoLinkEntity(uid, it.norm, it.title) },
        )
        staged.forEach { attachmentDao.insert(it.copy(memoUid = uid)) }
        uid
    }

    suspend fun updateContent(uid: String, content: String) {
        db.withTransaction {
            val memo = memoDao.findByUid(uid) ?: return@withTransaction
            if (memo.content == content) return@withTransaction
            val parsed = MarkdownUtils.parse(content)
            val now = System.currentTimeMillis()
            val titleChanged = parsed.titleNorm != memo.titleNorm
            memoDao.update(
                memo.copy(
                    content = content,
                    title = parsed.title,
                    titleNorm = parsed.titleNorm,
                    updatedAt = now,
                ),
            )
            if (titleChanged && memo.titleNorm.isNotBlank() && parsed.titleNorm != UNTITLED) {
                linkDao.renameTarget(memo.titleNorm, parsed.titleNorm, parsed.title)
            }
            replaceTags(uid, parsed.tags.map { MemoTagEntity(uid, it.norm, it.display) })
            replaceLinks(
                uid,
                parsed.links
                    .filter { it.norm.isNotBlank() && it.norm != UNTITLED }
                    .map { MemoLinkEntity(uid, it.norm, it.title) },
            )
        }
    }

    suspend fun duplicateMemo(uid: String): String? {
        val meta = memoDao.withMetaByUids(listOf(uid)).firstOrNull() ?: return null
        val now = System.currentTimeMillis()
        val newUid = UUID.randomUUID().toString()
        val copied = mutableListOf<AttachmentEntity>()
        try {
            meta.attachments.forEach { attachment ->
                val source = attachmentStore.fileFor(attachment)
                if (!source.exists()) return@forEach
                val copyUid = UUID.randomUUID().toString()
                val extension = attachment.fileName.substringAfterLast('.', "bin")
                copied += attachmentStore.importFromStream(
                    input = source.inputStream(),
                    uid = copyUid,
                    memoUid = newUid,
                    kind = attachment.kind,
                    fileName = "$copyUid.$extension",
                    originalName = attachment.originalName,
                    mimeType = attachment.mimeType,
                    createdAt = now,
                    width = attachment.width,
                    height = attachment.height,
                    durationMs = attachment.durationMs,
                    waveform = attachment.waveform,
                )
            }
            db.withTransaction {
                val parsed = MarkdownUtils.parse(meta.memo.content)
                memoDao.insert(
                    MemoEntity(
                        uid = newUid,
                        content = meta.memo.content,
                        title = parsed.title,
                        titleNorm = parsed.titleNorm,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
                replaceTags(newUid, parsed.tags.map { MemoTagEntity(newUid, it.norm, it.display) })
                replaceLinks(
                    newUid,
                    parsed.links
                        .filter { it.norm.isNotBlank() && it.norm != UNTITLED }
                        .map { MemoLinkEntity(newUid, it.norm, it.title) },
                )
                copied.forEach { attachmentDao.insert(it) }
            }
            return newUid
        } catch (error: Throwable) {
            copied.forEach { runCatching { attachmentStore.deleteFiles(it) } }
            throw error
        }
    }

    suspend fun addTag(uid: String, display: String): Boolean {
        val cleaned = display.trim().removePrefix("#")
            .replace(Regex("[^\\p{L}\\p{N}_-]+"), "-")
            .trim('-')
        if (cleaned.isBlank()) return false
        val memo = memoDao.findByUid(uid) ?: return false
        val norm = MarkdownUtils.normalizeTag(cleaned)
        val parsed = MarkdownUtils.parse(memo.content)
        if (parsed.tags.any { it.norm == norm }) return false
        val base = memo.content.trimEnd()
        val updated = if (base.isBlank()) "#$cleaned" else "$base\n\n#$cleaned"
        updateContent(uid, updated)
        return true
    }

    suspend fun togglePin(uid: String) {
        val memo = memoDao.findByUid(uid) ?: return
        val now = System.currentTimeMillis()
        memoDao.setPinned(uid, if (memo.pinnedAt == null) now else null, now)
    }

    suspend fun setPinned(uid: String, pinned: Boolean) {
        val now = System.currentTimeMillis()
        memoDao.setPinned(uid, if (pinned) now else null, now)
    }

    suspend fun toggleArchive(uid: String) {
        val memo = memoDao.findByUid(uid) ?: return
        val now = System.currentTimeMillis()
        memoDao.setArchived(uid, if (memo.archivedAt == null) now else null, now)
    }

    suspend fun setArchived(uid: String, archived: Boolean) {
        val now = System.currentTimeMillis()
        memoDao.setArchived(uid, if (archived) now else null, now)
    }

    suspend fun trash(uid: String) {
        memoDao.setDeleted(uid, System.currentTimeMillis(), System.currentTimeMillis())
    }

    suspend fun restore(uid: String) {
        memoDao.setDeleted(uid, null, System.currentTimeMillis())
    }

    suspend fun deleteForever(uid: String) {
        val files = attachmentDao.allFor(uid)
        files.forEach { runCatching { attachmentStore.deleteFiles(it) } }
        memoDao.hardDelete(uid)
    }

    suspend fun emptyTrash() {
        val trashed = memoDao.trashedOlderThan(Long.MAX_VALUE)
        trashed.forEach { memo ->
            attachmentDao.allFor(memo.uid).forEach { runCatching { attachmentStore.deleteFiles(it) } }
        }
        memoDao.purgeTrash(Long.MAX_VALUE)
    }

    suspend fun purgeOldTrash(retentionDays: Long = 30) {
        val cutoff = System.currentTimeMillis() - retentionDays * 86_400_000L
        val trashed = memoDao.trashedOlderThan(cutoff)
        trashed.forEach { memo ->
            attachmentDao.allFor(memo.uid).forEach { runCatching { attachmentStore.deleteFiles(it) } }
        }
        memoDao.purgeTrash(cutoff)
    }

    suspend fun setCapsule(uid: String, unlockAt: Long?) {
        memoDao.setSealed(uid, unlockAt, System.currentTimeMillis())
    }

    suspend fun markCapsuleOpened(uid: String) {
        memoDao.markOpened(uid, System.currentTimeMillis())
    }

    suspend fun stageAttachment(
        uri: Uri,
        kind: AttachmentKind,
        draftUid: String,
    ): AttachmentEntity = attachmentStore.importUri(uri, memoUid = draftUid, kind = kind)

    suspend fun stageRecording(
        file: File,
        draftUid: String,
        durationMs: Long?,
        waveform: String?,
    ): AttachmentEntity = attachmentStore.importLocalFile(
        source = file,
        memoUid = draftUid,
        kind = AttachmentKind.AUDIO,
        mimeType = "audio/mp4",
        originalName = "Voice note",
        durationMs = durationMs,
        waveform = waveform,
    )

    suspend fun attach(memoUid: String, uri: Uri, kind: AttachmentKind): AttachmentEntity {
        requireNotNull(memoDao.findByUid(memoUid)) { "Memo not found" }
        val entity = attachmentStore.importUri(uri, memoUid, kind)
        attachmentDao.insert(entity)
        return entity
    }

    suspend fun attachRecording(
        memoUid: String,
        file: File,
        durationMs: Long?,
        waveform: String?,
    ): AttachmentEntity {
        requireNotNull(memoDao.findByUid(memoUid)) { "Memo not found" }
        val entity = attachmentStore.importLocalFile(
            source = file,
            memoUid = memoUid,
            kind = AttachmentKind.AUDIO,
            mimeType = "audio/mp4",
            originalName = "Voice note",
            durationMs = durationMs,
            waveform = waveform,
        )
        attachmentDao.insert(entity)
        return entity
    }

    suspend fun stageSketch(file: File, draftUid: String): AttachmentEntity =
        attachmentStore.importLocalFile(
            source = file,
            memoUid = draftUid,
            kind = AttachmentKind.IMAGE,
            mimeType = "image/png",
            originalName = "Sketch",
        )

    suspend fun attachSketch(memoUid: String, file: File): AttachmentEntity {
        requireNotNull(memoDao.findByUid(memoUid)) { "Memo not found" }
        val entity = attachmentStore.importLocalFile(
            source = file,
            memoUid = memoUid,
            kind = AttachmentKind.IMAGE,
            mimeType = "image/png",
            originalName = "Sketch",
        )
        attachmentDao.insert(entity)
        return entity
    }

    suspend fun removeAttachment(uid: String) {
        val entity = attachmentDao.findByUid(uid) ?: return
        attachmentDao.markDeleted(uid, System.currentTimeMillis())
        runCatching { attachmentStore.deleteFiles(entity) }
    }

    suspend fun discardAttachment(entity: AttachmentEntity) {
        runCatching { attachmentStore.deleteFiles(entity) }
    }

    suspend fun setReminder(memoUid: String, fireAt: Long): ReminderEntity {
        reminderDao.activeFor(memoUid).forEach { reminderDao.delete(it.uid) }
        val reminder = ReminderEntity(
            uid = UUID.randomUUID().toString(),
            memoUid = memoUid,
            fireAt = fireAt,
            createdAt = System.currentTimeMillis(),
        )
        reminderDao.upsert(reminder)
        return reminder
    }

    suspend fun removeReminder(uid: String) = reminderDao.delete(uid)

    suspend fun completeReminder(uid: String) {
        reminderDao.markCompleted(uid, System.currentTimeMillis())
    }

    suspend fun echoPicks(now: Long = System.currentTimeMillis(), count: Int = 3): List<MemoUi> {
        val seed = EchoPlanner.seedForDate(
            java.time.Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay(),
        )
        val candidates = memoDao.echoCandidates(now, now - EchoPlanner.MIN_AGE_DAYS * 86_400_000L)
        val picks = EchoPlanner.pick(candidates, now, seed, count)
        if (picks.isEmpty()) return emptyList()
        val metas = memoDao.withMetaByUids(picks.map { it.uid }).associateBy { it.memo.uid }
        return picks.mapNotNull { meta -> metas[meta.uid]?.toUi() }
    }

    suspend fun keepEcho(uid: String) {
        val memo = memoDao.findByUid(uid) ?: return
        memoDao.keepEcho(uid, System.currentTimeMillis(), (memo.echoLevel + 1).coerceAtMost(5))
    }

    suspend fun dismissEcho(uid: String) {
        memoDao.markEchoed(uid, System.currentTimeMillis())
    }

    suspend fun renameTag(oldNorm: String, newDisplay: String) {
        val cleaned = newDisplay.removePrefix("#")
            .replace(Regex("[^\\p{L}\\p{N}_-]+"), "-")
            .trim('-')
        val uids = tagDao.memoUidsWithTag(oldNorm)
        uids.forEach { uid ->
            val memo = memoDao.findByUid(uid) ?: return@forEach
            val updated = MarkdownUtils.replaceTag(memo.content, oldNorm, cleaned)
            updateContent(uid, updated)
        }
    }

    suspend fun createLinkedMemo(title: String): String {
        val clean = title.trim().ifBlank { "Untitled" }
        return createMemo("# $clean\n\n")
    }

    suspend fun createWelcomeNote(content: String) = createMemo(content)

    fun fileFor(entity: AttachmentEntity): File = attachmentStore.fileFor(entity)

    private suspend fun replaceTags(uid: String, tags: List<MemoTagEntity>) {
        tagDao.clearFor(uid)
        if (tags.isNotEmpty()) tagDao.insertAll(tags)
    }

    private suspend fun replaceLinks(uid: String, links: List<MemoLinkEntity>) {
        linkDao.clearFor(uid)
        if (links.isNotEmpty()) linkDao.insertAll(links)
    }

    private fun toUiList(list: List<MemoWithMeta>): List<MemoUi> = list.map { it.toUi() }

    private fun MemoWithMeta.toUi(): MemoUi = toUi(
        fileResolver = { attachmentStore.fileFor(it) },
        thumbnailResolver = { attachmentStore.thumbnailFor(it.uid) },
    )

    private fun buildFeedQuery(
        filter: FeedFilter,
        query: SearchQuery,
        limit: Int,
        now: Long,
        order: FeedOrder,
    ): SupportSQLiteQuery {
        val clauses = mutableListOf<String>()
        val args = mutableListOf<Any?>()
        val fts = query.toFtsExpression()
        val join = if (fts != null) "JOIN memo_fts ON memo_fts.rowid = m.id" else ""
        if (fts != null) {
            clauses += "memo_fts MATCH ?"
            args += fts
        }

        clauses += "m.deletedAt IS NULL"
        if (filter == FeedFilter.ARCHIVED || query.archivedOnly) {
            clauses += "m.archivedAt IS NOT NULL"
        } else {
            clauses += "m.archivedAt IS NULL"
        }
        clauses += "(m.sealedUntil IS NULL OR m.sealedUntil <= ?)"
        args += now

        when (filter) {
            FeedFilter.PINNED -> clauses += "m.pinnedAt IS NOT NULL"
            FeedFilter.MEDIA ->
                clauses += "EXISTS (SELECT 1 FROM attachments a WHERE a.memoUid = m.uid AND a.deletedAt IS NULL)"
            FeedFilter.REMINDERS ->
                clauses += "EXISTS (SELECT 1 FROM reminders r WHERE r.memoUid = m.uid AND r.completedAt IS NULL AND r.fireAt > $now)"
            else -> Unit
        }

        if (query.pinnedOnly && filter != FeedFilter.PINNED) clauses += "m.pinnedAt IS NOT NULL"
        if (query.imageOnly) {
            clauses += "EXISTS (SELECT 1 FROM attachments a WHERE a.memoUid = m.uid AND a.deletedAt IS NULL AND a.kind = 'IMAGE')"
        }
        if (query.audioOnly) {
            clauses += "EXISTS (SELECT 1 FROM attachments a WHERE a.memoUid = m.uid AND a.deletedAt IS NULL AND a.kind = 'AUDIO')"
        }
        if (query.attachmentOnly) {
            clauses += "EXISTS (SELECT 1 FROM attachments a WHERE a.memoUid = m.uid AND a.deletedAt IS NULL)"
        }
        if (query.reminderOnly) {
            clauses += "EXISTS (SELECT 1 FROM reminders r WHERE r.memoUid = m.uid AND r.completedAt IS NULL)"
        }
        query.tags.forEach { tag ->
            clauses += "EXISTS (SELECT 1 FROM memo_tags t WHERE t.memoUid = m.uid AND t.tagNorm = ?)"
            args += tag
        }
        val zone = ZoneId.systemDefault()
        query.afterMillis(zone)?.let { clauses += "m.createdAt >= ?".also { args += it } }
        query.beforeMillis(zone)?.let { clauses += "m.createdAt < ?".also { args += it } }

        val orderClause = when (order) {
            FeedOrder.NEWEST -> "CASE WHEN m.pinnedAt IS NULL THEN 1 ELSE 0 END ASC, m.pinnedAt DESC, m.createdAt DESC"
            FeedOrder.OLDEST -> "CASE WHEN m.pinnedAt IS NULL THEN 1 ELSE 0 END ASC, m.pinnedAt DESC, m.createdAt ASC"
        }
        val sql = buildString {
            append("SELECT m.* FROM memos m ")
            append(join)
            append(" WHERE ")
            append(clauses.joinToString(" AND "))
            append(" ORDER BY ")
            append(orderClause)
            append(" LIMIT ?")
        }
        args += limit.coerceIn(1, 500)
        return SimpleSQLiteQuery(sql, args.toTypedArray())
    }

    private fun nowTicker(): Flow<Long> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(60_000L)
        }
    }

    companion object {
        const val UNTITLED = "untitled"
    }
}