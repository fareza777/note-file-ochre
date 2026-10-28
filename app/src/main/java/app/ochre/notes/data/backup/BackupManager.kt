package app.ochre.notes.data.backup

import android.content.Context
import android.net.Uri
import app.ochre.notes.data.local.MemoEntity
import app.ochre.notes.data.local.OchreDatabase
import app.ochre.notes.data.media.AttachmentStore
import app.ochre.notes.domain.MarkdownUtils
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class BackupMemo(
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

@Serializable
data class BackupAttachment(
    val uid: String,
    val memoUid: String,
    val kind: String,
    val fileName: String,
    val originalName: String? = null,
    val mimeType: String,
    val sizeBytes: Long,
    val width: Int? = null,
    val height: Int? = null,
    val durationMs: Long? = null,
    val waveform: String? = null,
    val createdAt: Long,
)

@Serializable
data class BackupReminder(
    val uid: String,
    val memoUid: String,
    val fireAt: Long,
    val createdAt: Long,
    val completedAt: Long? = null,
)

@Serializable
data class BackupManifest(
    val format: Int = 1,
    val app: String = "Ochre",
    val createdAt: Long,
    val memos: List<BackupMemo> = emptyList(),
    val attachments: List<BackupAttachment> = emptyList(),
    val reminders: List<BackupReminder> = emptyList(),
)

data class BackupSummary(
    val memos: Int,
    val attachments: Int,
    val bytes: Long,
)

data class RestoreSummary(
    val importedMemos: Int,
    val updatedMemos: Int,
    val skippedMemos: Int,
    val importedAttachments: Int,
    val reminders: Int,
)

class BackupManager(
    private val context: Context,
    private val db: OchreDatabase,
    private val attachmentStore: AttachmentStore,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val merger = VaultMerger(db, attachmentStore)

    suspend fun exportEncrypted(uri: Uri, passphrase: CharArray): BackupSummary =
        withContext(Dispatchers.IO) {
            val temp = File.createTempFile("ochre-vault-", ".zip", context.cacheDir)
            try {
                val manifest = buildZip(temp)
                context.contentResolver.openOutputStream(uri, "wt")?.use { raw ->
                    raw.buffered().use { out ->
                        temp.inputStream().use { input ->
                            VaultCrypto.encrypt(input, out, passphrase)
                        }
                    }
                } ?: throw IOException("Could not write backup")
                BackupSummary(
                    memos = manifest.memos.size,
                    attachments = manifest.attachments.size,
                    bytes = manifest.attachments.sumOf { it.sizeBytes },
                )
            } finally {
                temp.delete()
            }
        }

    suspend fun restoreEncrypted(uri: Uri, passphrase: CharArray): RestoreSummary =
        withContext(Dispatchers.IO) {
            val temp = File.createTempFile("ochre-restore-", ".zip", context.cacheDir)
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(temp).use { out ->
                        VaultCrypto.decrypt(input, out, passphrase)
                    }
                } ?: throw IOException("Could not read backup")
                ZipFile(temp).use { zip -> merge(zip) }
            } finally {
                temp.delete()
            }
        }

    suspend fun exportMarkdown(uri: Uri): BackupSummary = withContext(Dispatchers.IO) {
        val memos = db.memoDao().allMemos()
        val attachments = db.attachmentDao().allActive()
        context.contentResolver.openOutputStream(uri, "wt")?.use { raw ->
            ZipOutputStream(raw.buffered()).use { zip ->
                memos.filter { it.deletedAt == null }.forEach { memo ->
                    val name = "notes/${safeName(memo.title, memo.uid)}.md"
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(markdownFor(memo).toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                }
                attachments.forEach { attachment ->
                    val file = attachmentStore.fileFor(attachment)
                    if (!file.exists()) return@forEach
                    zip.putNextEntry(ZipEntry("attachments/${attachment.fileName}"))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
                zip.putNextEntry(ZipEntry("README.txt"))
                zip.write(
                    (
                        "Exported from Ochre on ${java.time.LocalDate.now()}.\n\n" +
                            "Each note is a Markdown file in /notes. Attachments are in /attachments.\n"
                        ).toByteArray(Charsets.UTF_8),
                )
                zip.closeEntry()
            }
        } ?: throw IOException("Could not write export")
        BackupSummary(memos.size, attachments.size, 0)
    }

    private suspend fun buildZip(destination: File): BackupManifest {
        val memos = db.memoDao().allMemos()
        val attachments = db.attachmentDao().allActive()
        val reminders = db.reminderDao().all()
        val manifest = BackupManifest(
            createdAt = System.currentTimeMillis(),
            memos = memos.map { it.toBackupMemo() },
            attachments = attachments.map { it.toBackupAttachment() },
            reminders = reminders.map { it.toBackupReminder() },
        )
        ZipOutputStream(FileOutputStream(destination).buffered()).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(json.encodeToString(BackupManifest.serializer(), manifest).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            attachments.forEach { attachment ->
                val file = attachmentStore.fileFor(attachment)
                if (!file.exists()) return@forEach
                zip.putNextEntry(ZipEntry("attachments/${attachment.fileName}"))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        return manifest
    }

    private suspend fun merge(zip: ZipFile): RestoreSummary {
        val manifestEntry = zip.getEntry("manifest.json") ?: throw IOException("Not an Ochre backup")
        val manifest = zip.getInputStream(manifestEntry).use { input ->
            json.decodeFromString(BackupManifest.serializer(), input.readBytes().toString(Charsets.UTF_8))
        }
        if (manifest.format != 1) throw IOException("Unsupported backup format")

        val memos = merger.mergeMemos(manifest.memos)

        var attachmentCount = 0
        manifest.attachments.forEach { remote ->
            val entry = zip.getEntry("attachments/${remote.fileName}") ?: return@forEach
            val imported = zip.getInputStream(entry).use { input ->
                merger.importAttachment(remote, input)
            }
            if (imported) attachmentCount++
        }

        val reminders = merger.mergeReminders(manifest.reminders)

        return RestoreSummary(
            importedMemos = memos.imported,
            updatedMemos = memos.updated,
            skippedMemos = memos.skipped,
            importedAttachments = attachmentCount,
            reminders = reminders,
        )
    }

    private fun markdownFor(memo: MemoEntity): String {
        val tags = MarkdownUtils.parse(memo.content).tags.joinToString(" ") { "#${it.display}" }
        val created = java.time.Instant.ofEpochMilli(memo.createdAt)
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalDate()
        return buildString {
            append("---\n")
            append("title: \"${memo.title.replace("\"", "\\\"")}\"\n")
            append("created: $created\n")
            if (tags.isNotBlank()) append("tags: $tags\n")
            append("---\n\n")
            append(memo.content)
            append("\n")
        }
    }

    private fun safeName(title: String, uid: String): String {
        val cleaned = title.lowercase()
            .replace(Regex("[^\\p{L}\\p{N}]+"), "-")
            .trim('-')
            .take(48)
        val suffix = uid.take(8)
        return if (cleaned.isBlank()) "note-$suffix" else "$cleaned-$suffix"
    }
}