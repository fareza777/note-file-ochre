package app.ochre.notes

import android.content.Context
import app.ochre.notes.data.NoteRepository
import app.ochre.notes.data.backup.BackupManager
import app.ochre.notes.data.local.OchreDatabase
import app.ochre.notes.data.media.AttachmentStore
import app.ochre.notes.data.security.SecretStore
import app.ochre.notes.data.settings.SettingsRepository
import app.ochre.notes.data.sync.SyncManager
import app.ochre.notes.notifications.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class DraftInbox {
    @Volatile
    private var pending: String? = null

    fun offer(text: String) {
        if (text.isNotBlank()) pending = text
    }

    fun consume(): String? {
        val value = pending
        pending = null
        return value
    }
}

class AppContainer(context: Context) {

    val appContext: Context = context.applicationContext

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database: OchreDatabase = OchreDatabase.build(appContext)

    val attachmentStore = AttachmentStore(appContext)

    val settingsRepository = SettingsRepository(appContext)

    val noteRepository = NoteRepository(database, attachmentStore)

    val reminderScheduler = ReminderScheduler(appContext, database.reminderDao())

    val secretStore = SecretStore(appContext)

    val backupManager = BackupManager(appContext, database, attachmentStore)

    val syncManager = SyncManager(
        db = database,
        attachmentStore = attachmentStore,
        settingsRepository = settingsRepository,
        secretStore = secretStore,
        reminderScheduler = reminderScheduler,
    )

    val draftInbox = DraftInbox()
}