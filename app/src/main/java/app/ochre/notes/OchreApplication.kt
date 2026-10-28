package app.ochre.notes

import android.app.Application
import app.ochre.notes.data.sync.SyncWorker
import app.ochre.notes.notifications.ReminderNotifier
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class OchreApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        ReminderNotifier.ensureChannels(this)
        container.appScope.launch {
            val settings = container.settingsRepository.settings.first()
            if (!settings.welcomeSeeded) {
                if (container.noteRepository.countAll() == 0) {
                    container.noteRepository.createWelcomeNote(WELCOME_NOTE)
                }
                container.settingsRepository.markWelcomeSeeded()
            }
            container.noteRepository.purgeOldTrash()
            container.reminderScheduler.rescheduleAll()
            SyncWorker.schedule(this@OchreApplication, settings.syncEnabled)
        }
    }

    private companion object {
        val WELCOME_NOTE = """
            # Welcome to Ochre

            Ochre is a quiet place for your thinking. Everything lives on this device first, so you can write with the screen off the grid.

            ## Try this

            - Write in **Markdown** - headings, lists, quotes, checkboxes, code fences
            - Add #tags anywhere to organise across notebooks
            - Link notes with [[Ideas]] and follow threads both ways
            - Attach photos or record a voice note in one tap
            - Long-press a note to pin it, seal it in a capsule, or set a reminder

            ## Made for focus

            - Smart search understands `tag:work is:pinned has:image before:2026-01-01`
            - Echo resurfaces older notes at the right interval
            - Capsules stay sealed until the day you choose
            - Your vault is encrypted and never leaves the device unless you ask it to

            Delete this note whenever you like. It will not come back.
        """.trimIndent()
    }
}