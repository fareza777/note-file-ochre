package app.ochre.notes.data.backup

import android.content.Context
import androidx.core.content.FileProvider
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.ochre.notes.data.local.MemoEntity
import app.ochre.notes.data.local.OchreDatabase
import app.ochre.notes.data.media.AttachmentStore
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupRoundTripTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        File(context.cacheDir, "shared").mkdirs()
    }

    @After
    fun tearDown() {
        File(context.cacheDir, "shared").listFiles()?.forEach { it.delete() }
    }

    private fun database(): OchreDatabase =
        Room.inMemoryDatabaseBuilder(context, OchreDatabase::class.java).allowMainThreadQueries().build()

    private fun fileUri(name: String) =
        FileProvider.getUriForFile(context, "${context.packageName}.files", File(context.cacheDir, "shared/$name"))

    @Test
    fun encryptedBackupRestoresIntoAFreshVault() = runBlocking {
        val source = database()
        source.memoDao().insert(
            MemoEntity(
                uid = "memo-1",
                content = "# Lantern\n\nA quiet thought with #work and [[Ideas]].",
                title = "Lantern",
                titleNorm = "lantern",
                createdAt = 1_000L,
                updatedAt = 2_000L,
            ),
        )
        val file = File(context.cacheDir, "shared/roundtrip.ochre")
        val summary = BackupManager(context, source, AttachmentStore(context))
            .exportEncrypted(fileUri("roundtrip.ochre"), "backup-passphrase".toCharArray())
        assertEquals(1, summary.memos)

        val stored = file.readBytes()
        assertTrue(stored.size > 64)
        assertFalse(String(stored, Charsets.ISO_8859_1).contains("Lantern"))

        val target = database()
        val restored = BackupManager(context, target, AttachmentStore(context))
            .restoreEncrypted(fileUri("roundtrip.ochre"), "backup-passphrase".toCharArray())
        assertEquals(1, restored.importedMemos)
        assertEquals("Lantern", target.memoDao().findByUid("memo-1")?.title)
        assertEquals(listOf("work"), target.tagDao().tagsFor("memo-1").map { it.tagNorm })

        target.close()
        source.close()
    }

    @Test
    fun wrongPassphraseLeavesTheVaultUntouched() {
        val source = database()
        File(context.cacheDir, "shared/wrong.ochre").delete()
        runBlocking {
            source.memoDao().insert(
                MemoEntity(
                    uid = "memo-1",
                    content = "Private",
                    title = "Private",
                    titleNorm = "private",
                    createdAt = 1L,
                    updatedAt = 1L,
                ),
            )
            BackupManager(context, source, AttachmentStore(context))
                .exportEncrypted(fileUri("wrong.ochre"), "right-passphrase".toCharArray())
        }

        val target = database()
        assertThrows(VaultCrypto.WrongPassphraseException::class.java) {
            runBlocking {
                BackupManager(context, target, AttachmentStore(context))
                    .restoreEncrypted(fileUri("wrong.ochre"), "wrong-passphrase".toCharArray())
            }
        }
        assertEquals(0, runBlocking { target.memoDao().countAll() })

        target.close()
        source.close()
    }
}