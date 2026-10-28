package app.ochre.notes.data.sync

import app.ochre.notes.data.backup.BackupAttachment
import app.ochre.notes.data.backup.BackupMemo
import app.ochre.notes.data.backup.BackupReminder
import app.ochre.notes.data.backup.VaultCrypto
import app.ochre.notes.data.backup.VaultMerger
import app.ochre.notes.data.backup.toBackupAttachment
import app.ochre.notes.data.backup.toBackupMemo
import app.ochre.notes.data.backup.toBackupReminder
import app.ochre.notes.data.local.OchreDatabase
import app.ochre.notes.data.media.AttachmentStore
import app.ochre.notes.data.security.SecretStore
import app.ochre.notes.data.settings.SettingsRepository
import app.ochre.notes.notifications.ReminderScheduler
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class SyncMemoObject(
    val memo: BackupMemo,
    val reminders: List<BackupReminder> = emptyList(),
    val attachments: List<BackupAttachment> = emptyList(),
)

@Serializable
data class SyncIndex(
    val version: Int = 1,
    val memos: Map<String, Long> = emptyMap(),
    val media: Map<String, Long> = emptyMap(),
)

data class SyncResult(
    val pushed: Int,
    val pulled: Int,
    val media: Int,
    val bytesUp: Long,
)

class SyncException(message: String) : IOException(message)

/**
 * Optional end-to-end encrypted sync over any WebDAV folder. Objects are sealed locally with a
 * key derived from the user's sync passphrase, so the server only ever stores ciphertext.
 */
class SyncManager(
    private val db: OchreDatabase,
    private val attachmentStore: AttachmentStore,
    private val settingsRepository: SettingsRepository,
    private val secretStore: SecretStore,
    private val reminderScheduler: ReminderScheduler,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val merger = VaultMerger(db, attachmentStore)

    suspend fun syncNow(): SyncResult = withContext(Dispatchers.IO) {
        val settings = settingsRepository.settings.first()
        val passphrase = secretStore.get(SecretStore.SYNC_PASSPHRASE)
            ?: throw SyncException("Set a sync passphrase in Settings first.")
        if (settings.syncUrl.isBlank()) throw SyncException("Add your WebDAV address in Settings first.")
        val password = secretStore.get(SecretStore.SYNC_PASSWORD)
            ?: throw SyncException("Add your WebDAV password in Settings first.")

        val client = WebDavClient(settings.syncUrl, settings.syncUsername, password)
        client.ping()
        client.ensureRoot()

        val salt = client.get("salt") ?: VaultCrypto.randomSalt().also { client.put("salt", it) }
        val key = VaultCrypto.keyFrom(passphrase.toCharArray(), salt)
        try {
            val index = client.get("index")?.let { decodeIndex(it, key) } ?: SyncIndex()
            val now = System.currentTimeMillis()

            val memos = db.memoDao().allMemos()
            val localByUid = memos.associateBy { it.uid }
            val attachmentsByMemo = db.attachmentDao().allActive().groupBy { it.memoUid }
            val remindersByMemo = db.reminderDao().all().groupBy { it.memoUid }

            val nextMemos = index.memos.toMutableMap()
            val nextMedia = index.media.toMutableMap()
            var pushed = 0
            var bytesUp = 0L

            memos.forEach { memo ->
                val remoteVersion = index.memos[memo.uid] ?: 0L
                if (memo.updatedAt <= remoteVersion) return@forEach
                val attachments = attachmentsByMemo[memo.uid].orEmpty()
                val payload = SyncMemoObject(
                    memo = memo.toBackupMemo(),
                    reminders = remindersByMemo[memo.uid].orEmpty().map { it.toBackupReminder() },
                    attachments = attachments.map { it.toBackupAttachment() },
                )
                val body = sealed(json.encodeToString(SyncMemoObject.serializer(), payload), key)
                client.put("objects/${memo.uid}.ochre", body)
                bytesUp += body.size
                nextMemos[memo.uid] = memo.updatedAt
                pushed++

                attachments.forEach { attachment ->
                    val version = index.media[attachment.uid] ?: 0L
                    if (attachment.createdAt <= version) return@forEach
                    val file = attachmentStore.fileFor(attachment)
                    if (!file.exists()) return@forEach
                    val media = sealed(file.readBytes(), key)
                    client.put("media/${attachment.uid}.ochre", media)
                    bytesUp += media.size
                    nextMedia[attachment.uid] = attachment.createdAt
                }
            }

            var pulled = 0
            var media = 0
            nextMemos.forEach { (uid, remoteVersion) ->
                val local = localByUid[uid]
                if (local != null && local.updatedAt >= remoteVersion) return@forEach
                val body = client.get("objects/$uid.ochre") ?: return@forEach
                val payload = runCatching { decodeMemo(body, key) }.getOrNull() ?: return@forEach
                merger.mergeMemos(listOf(payload.memo))
                payload.reminders.let { merger.mergeReminders(it) }
                payload.attachments.forEach { meta ->
                    if (db.attachmentDao().findByUid(meta.uid) != null) return@forEach
                    val bytes = client.get("media/${meta.uid}.ochre") ?: return@forEach
                    val plain = runCatching { unsealed(bytes, key) }.getOrNull() ?: return@forEach
                    if (merger.importAttachment(meta, ByteArrayInputStream(plain))) media++
                }
                pulled++
            }

            val newIndex = SyncIndex(memos = nextMemos, media = nextMedia)
            client.put("index", sealed(json.encodeToString(SyncIndex.serializer(), newIndex), key))

            reminderScheduler.rescheduleAll()
            settingsRepository.setSyncResult(now, "Synced · pushed $pushed · pulled $pulled")
            SyncResult(pushed = pushed, pulled = pulled, media = media, bytesUp = bytesUp)
        } finally {
            key.fill(0)
        }
    }

    private fun sealed(payload: String, key: ByteArray): ByteArray =
        sealed(payload.toByteArray(Charsets.UTF_8), key)

    private fun sealed(bytes: ByteArray, key: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        VaultCrypto.seal(key, bytes.inputStream(), out)
        return out.toByteArray()
    }

    private fun unsealed(bytes: ByteArray, key: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        VaultCrypto.unseal(key, bytes.inputStream(), out)
        return out.toByteArray()
    }

    private fun decodeIndex(bytes: ByteArray, key: ByteArray): SyncIndex =
        json.decodeFromString(SyncIndex.serializer(), unsealed(bytes, key).toString(Charsets.UTF_8))

    private fun decodeMemo(bytes: ByteArray, key: ByteArray): SyncMemoObject =
        json.decodeFromString(SyncMemoObject.serializer(), unsealed(bytes, key).toString(Charsets.UTF_8))
}