package app.ochre.notes.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.ochre.notes.OchreApplication
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ReminderActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val reminderUid = intent.getStringExtra(EXTRA_REMINDER) ?: return
        val action = intent.action ?: return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val container = (context.applicationContext as OchreApplication).container
                if (action == ACTION_SNOOZE) {
                    val dao = container.database.reminderDao()
                    val reminder = dao.findByUid(reminderUid)
                    if (reminder != null) {
                        val now = System.currentTimeMillis()
                        val next = reminder.copy(
                            uid = UUID.randomUUID().toString(),
                            fireAt = now + SNOOZE_MILLIS,
                            createdAt = now,
                            completedAt = null,
                        )
                        dao.upsert(next)
                        container.reminderScheduler.schedule(next.uid, next.fireAt)
                    }
                }
                ReminderNotifier.cancel(context, reminderUid)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_DONE = "app.ochre.notes.action.REMINDER_DONE"
        const val ACTION_SNOOZE = "app.ochre.notes.action.REMINDER_SNOOZE"
        private const val EXTRA_REMINDER = "reminder_uid"
        private const val SNOOZE_MILLIS = 10 * 60_000L

        fun intent(context: Context, action: String, reminderUid: String): Intent =
            Intent(context, ReminderActionReceiver::class.java).apply {
                this.action = action
                putExtra(EXTRA_REMINDER, reminderUid)
            }
    }
}