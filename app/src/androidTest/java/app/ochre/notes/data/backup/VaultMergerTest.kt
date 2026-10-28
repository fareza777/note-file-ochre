package app.ochre.notes.data.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.ochre.notes.data.local.MemoEntity
import app.ochre.notes.data.local.OchreDatabase
import app.ochre.notes.data.media.AttachmentStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VaultMergerTest {

    private lateinit var db: OchreDatabase
    private lateinit var merger: VaultMerger

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, OchreDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        merger = VaultMerger(db, AttachmentStore(context))
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun local(uid: String, content: String, updatedAt: Long, deletedAt: Long? = null) = MemoEntity(
        uid = uid,
        content = content,
        title = "Local",
        titleNorm = "local",
        createdAt = 1L,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
    )

    private fun remote(uid: String, content: String, updatedAt: Long, deletedAt: Long? = null) = BackupMemo(
        uid = uid,
        content = content,
        title = "Remote",
        titleNorm = "remote",
        createdAt = 1L,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
    )

    @Test
    fun newNotesArriveWithDerivedTags() = runBlocking {
        val counts = merger.mergeMemos(listOf(remote("u1", "# Hello #work", updatedAt = 10)))
        assertEquals(1, counts.imported)
        assertEquals(listOf("work"), db.tagDao().tagsFor("u1").map { it.tagNorm })
        assertEquals("# Hello #work", db.memoDao().findByUid("u1")?.content)
    }

    @Test
    fun newerRemoteWinsAndKeepsLocalRowId() = runBlocking {
        db.memoDao().insert(local("u1", "old body", updatedAt = 10))
        db.tagDao().insertAll(listOf(app.ochre.notes.data.local.MemoTagEntity("u1", "legacy", "legacy")))
        val before = db.memoDao().findByUid("u1")!!

        val counts = merger.mergeMemos(listOf(remote("u1", "new body #fresh", updatedAt = 20)))
        val after = db.memoDao().findByUid("u1")!!

        assertEquals(1, counts.updated)
        assertEquals(before.id, after.id)
        assertEquals("new body #fresh", after.content)
        assertEquals(listOf("fresh"), db.tagDao().tagsFor("u1").map { it.tagNorm })
    }

    @Test
    fun olderRemoteIsSkipped() = runBlocking {
        db.memoDao().insert(local("u1", "local wins", updatedAt = 30))
        val counts = merger.mergeMemos(listOf(remote("u1", "stale", updatedAt = 20)))
        assertEquals(1, counts.skipped)
        assertEquals("local wins", db.memoDao().findByUid("u1")?.content)
    }

    @Test
    fun tombstonesDoNotResurrectPurgedNotes() = runBlocking {
        val counts = merger.mergeMemos(listOf(remote("gone", "deleted on another device", updatedAt = 40, deletedAt = 40)))
        assertEquals(0, counts.imported)
        assertNull(db.memoDao().findByUid("gone"))
    }

    @Test
    fun deletingOnOneDeviceMovesNoteToTrashElsewhere() = runBlocking {
        db.memoDao().insert(local("u1", "still here", updatedAt = 10))
        merger.mergeMemos(listOf(remote("u1", "still here", updatedAt = 20, deletedAt = 20)))
        val merged = db.memoDao().findByUid("u1")!!
        assertEquals(20L, merged.deletedAt)
    }

    @Test
    fun remindersAttachOnlyToKnownNotes() = runBlocking {
        db.memoDao().insert(local("u1", "body", updatedAt = 10))
        val count = merger.mergeReminders(
            listOf(
                BackupReminder(uid = "r1", memoUid = "u1", fireAt = 5_000L, createdAt = 1L),
                BackupReminder(uid = "r2", memoUid = "missing", fireAt = 5_000L, createdAt = 1L),
            ),
        )
        assertEquals(1, count)
        assertTrue(db.reminderDao().findByUid("r1") != null)
        assertNull(db.reminderDao().findByUid("r2"))
    }
}