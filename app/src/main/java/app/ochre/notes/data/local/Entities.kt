package app.ochre.notes.data.local

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(
    tableName = "memos",
    indices = [
        Index(value = ["uid"], unique = true),
        Index("createdAt"),
        Index("updatedAt"),
        Index("pinnedAt"),
        Index("archivedAt"),
        Index("deletedAt"),
        Index("sealedUntil"),
        Index("titleNorm"),
    ],
)
data class MemoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uid: String,
    val content: String,
    val title: String,
    val titleNorm: String,
    val createdAt: Long,
    val updatedAt: Long,
    val pinnedAt: Long? = null,
    val archivedAt: Long? = null,
    val deletedAt: Long? = null,
    val sealedUntil: Long? = null,
    val openedAt: Long? = null,
    val echoLevel: Int = 0,
    val echoedAt: Long? = null,
)

enum class AttachmentKind { IMAGE, AUDIO, FILE }

@Entity(
    tableName = "attachments",
    foreignKeys = [
        ForeignKey(
            entity = MemoEntity::class,
            parentColumns = ["uid"],
            childColumns = ["memoUid"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["uid"], unique = true),
        Index("memoUid"),
        Index("deletedAt"),
    ],
)
data class AttachmentEntity(
    @PrimaryKey val uid: String,
    val memoUid: String,
    val kind: AttachmentKind,
    val fileName: String,
    val originalName: String?,
    val mimeType: String,
    val sizeBytes: Long,
    val width: Int? = null,
    val height: Int? = null,
    val durationMs: Long? = null,
    val waveform: String? = null,
    val createdAt: Long,
    val deletedAt: Long? = null,
)

@Entity(
    tableName = "reminders",
    foreignKeys = [
        ForeignKey(
            entity = MemoEntity::class,
            parentColumns = ["uid"],
            childColumns = ["memoUid"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["uid"], unique = true),
        Index("memoUid"),
        Index("fireAt"),
    ],
)
data class ReminderEntity(
    @PrimaryKey val uid: String,
    val memoUid: String,
    val fireAt: Long,
    val createdAt: Long,
    val completedAt: Long? = null,
)

@Entity(
    tableName = "memo_tags",
    primaryKeys = ["memoUid", "tagNorm"],
    foreignKeys = [
        ForeignKey(
            entity = MemoEntity::class,
            parentColumns = ["uid"],
            childColumns = ["memoUid"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("tagNorm")],
)
data class MemoTagEntity(
    val memoUid: String,
    val tagNorm: String,
    val tagDisplay: String,
)

@Entity(
    tableName = "memo_links",
    primaryKeys = ["fromUid", "targetTitleNorm"],
    foreignKeys = [
        ForeignKey(
            entity = MemoEntity::class,
            parentColumns = ["uid"],
            childColumns = ["fromUid"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("targetTitleNorm")],
)
data class MemoLinkEntity(
    val fromUid: String,
    val targetTitleNorm: String,
    val targetTitle: String,
)

@Fts4
@Entity(tableName = "memo_fts")
data class MemoFtsEntity(
    @PrimaryKey
    @ColumnInfo(name = "rowid")
    val rowId: Long,
    val content: String,
    val title: String,
)

data class MemoWithMeta(
    @Embedded val memo: MemoEntity,
    @Relation(parentColumn = "uid", entityColumn = "memoUid")
    val tags: List<MemoTagEntity> = emptyList(),
    @Relation(parentColumn = "uid", entityColumn = "memoUid")
    val attachments: List<AttachmentEntity> = emptyList(),
    @Relation(parentColumn = "uid", entityColumn = "memoUid")
    val reminders: List<ReminderEntity> = emptyList(),
)

data class TagCount(
    val tagNorm: String,
    val tagDisplay: String,
    val count: Int,
)

data class OutgoingLink(
    val titleNorm: String,
    val title: String,
    val targetUid: String?,
)

data class ReminderWithMemo(
    @Embedded val reminder: ReminderEntity,
    @Relation(parentColumn = "memoUid", entityColumn = "uid")
    val memo: MemoEntity?,
)
