package app.ochre.notes.data.sync

import app.ochre.notes.data.backup.BackupAttachment
import app.ochre.notes.data.backup.BackupMemo
import app.ochre.notes.data.backup.BackupReminder
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncIndexTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun `index survives a roundtrip`() {
        val index = SyncIndex(
            memos = mapOf("a" to 1_000L, "b" to 2_000L),
            media = mapOf("photo" to 500L),
        )
        val decoded = json.decodeFromString(SyncIndex.serializer(), json.encodeToString(SyncIndex.serializer(), index))
        assertEquals(index, decoded)
        assertEquals(1, decoded.version)
    }

    @Test
    fun `newer server fields are ignored`() {
        val decoded = json.decodeFromString(
            SyncIndex.serializer(),
            """{"version":2,"memos":{"x":5},"media":{},"future":true}""",
        )
        assertEquals(mapOf("x" to 5L), decoded.memos)
    }

    @Test
    fun `memo object carries its reminders and attachments`() {
        val payload = SyncMemoObject(
            memo = BackupMemo(
                uid = "memo-1",
                content = "# Hello",
                title = "Hello",
                titleNorm = "hello",
                createdAt = 1L,
                updatedAt = 2L,
            ),
            reminders = listOf(BackupReminder(uid = "r1", memoUid = "memo-1", fireAt = 99L, createdAt = 3L)),
            attachments = listOf(
                BackupAttachment(
                    uid = "a1",
                    memoUid = "memo-1",
                    kind = "IMAGE",
                    fileName = "a1.jpg",
                    mimeType = "image/jpeg",
                    sizeBytes = 42L,
                    createdAt = 4L,
                ),
            ),
        )
        val text = json.encodeToString(SyncMemoObject.serializer(), payload)
        assertTrue(text.contains("\"a1.jpg\""))
        val decoded = json.decodeFromString(SyncMemoObject.serializer(), text)
        assertEquals(1, decoded.reminders.size)
        assertEquals(1, decoded.attachments.size)
        assertEquals("Hello", decoded.memo.title)
    }
}