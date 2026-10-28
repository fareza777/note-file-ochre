package app.ochre.notes.domain

import app.ochre.notes.data.local.AttachmentEntity
import app.ochre.notes.data.local.AttachmentKind
import app.ochre.notes.data.local.MemoWithMeta
import app.ochre.notes.data.local.ReminderEntity
import java.io.File

data class TagUi(
    val norm: String,
    val display: String,
    val count: Int = 0,
)

data class AttachmentUi(
    val uid: String,
    val kind: AttachmentKind,
    val mimeType: String,
    val originalName: String?,
    val sizeBytes: Long,
    val width: Int?,
    val height: Int?,
    val durationMs: Long?,
    val waveform: List<Float>,
    val file: File?,
    val thumbnail: File?,
)

data class ReminderUi(
    val uid: String,
    val fireAt: Long,
    val completedAt: Long?,
)

data class OutgoingLinkUi(
    val title: String,
    val targetUid: String?,
)

data class MemoUi(
    val uid: String,
    val content: String,
    val title: String,
    val titleNorm: String,
    val createdAt: Long,
    val updatedAt: Long,
    val pinnedAt: Long?,
    val archivedAt: Long?,
    val deletedAt: Long?,
    val sealedUntil: Long?,
    val openedAt: Long?,
    val echoLevel: Int,
    val echoedAt: Long?,
    val tags: List<TagUi>,
    val attachments: List<AttachmentUi>,
    val reminders: List<ReminderUi>,
) {
    val isPinned: Boolean get() = pinnedAt != null
    val isArchived: Boolean get() = archivedAt != null
    val isTrashed: Boolean get() = deletedAt != null
    val activeReminder: ReminderUi? get() = reminders.minByOrNull { it.fireAt }
    val wordCount: Int get() = MarkdownUtils.wordCount(content)

    fun isSealed(now: Long): Boolean = sealedUntil != null && sealedUntil > now
    val hasImages: Boolean get() = attachments.any { it.kind == AttachmentKind.IMAGE }
    val hasAudio: Boolean get() = attachments.any { it.kind == AttachmentKind.AUDIO }
}

enum class FeedFilter { ALL, PINNED, MEDIA, REMINDERS, ARCHIVED }

enum class FeedOrder { NEWEST, OLDEST }

fun AttachmentEntity.toUi(file: File?, thumbnail: File?): AttachmentUi = AttachmentUi(
    uid = uid,
    kind = kind,
    mimeType = mimeType,
    originalName = originalName,
    sizeBytes = sizeBytes,
    width = width,
    height = height,
    durationMs = durationMs,
    waveform = waveform?.split(',')?.mapNotNull { it.trim().toFloatOrNull() } ?: emptyList(),
    file = file,
    thumbnail = thumbnail,
)

fun ReminderEntity.toUi(): ReminderUi = ReminderUi(uid, fireAt, completedAt)

fun MemoWithMeta.toUi(
    fileResolver: (AttachmentEntity) -> File?,
    thumbnailResolver: (AttachmentEntity) -> File?,
): MemoUi = MemoUi(
    uid = memo.uid,
    content = memo.content,
    title = memo.title,
    titleNorm = memo.titleNorm,
    createdAt = memo.createdAt,
    updatedAt = memo.updatedAt,
    pinnedAt = memo.pinnedAt,
    archivedAt = memo.archivedAt,
    deletedAt = memo.deletedAt,
    sealedUntil = memo.sealedUntil,
    openedAt = memo.openedAt,
    echoLevel = memo.echoLevel,
    echoedAt = memo.echoedAt,
    tags = tags.sortedBy { it.tagDisplay.lowercase() }.map { TagUi(it.tagNorm, it.tagDisplay) },
    attachments = attachments
        .filter { it.deletedAt == null }
        .sortedBy { it.createdAt }
        .map { it.toUi(fileResolver(it), thumbnailResolver(it)) },
    reminders = reminders
        .filter { it.completedAt == null }
        .sortedBy { it.fireAt }
        .map { it.toUi() },
)