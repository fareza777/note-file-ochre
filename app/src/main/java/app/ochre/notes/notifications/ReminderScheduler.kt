package app.ochre.notes.notifications

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import app.ochre.notes.data.local.ReminderDao
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ReminderScheduler(
    private val context: Context,
    private val reminderDao: ReminderDao,
) {
    fun schedule(reminderUid: String, fireAt: Long) {
        val delay = (fireAt - System.currentTimeMillis()).coerceAtLeast(0L)
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(ReminderWorker.KEY_REMINDER to reminderUid))
            .addTag(TAG)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(workName(reminderUid), ExistingWorkPolicy.REPLACE, request)
    }

    fun cancel(reminderUid: String) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(reminderUid))
        ReminderNotifier.cancel(context, reminderUid)
    }

    suspend fun rescheduleAll() = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        reminderDao.all()
            .filter { it.completedAt == null }
            .forEach { reminder ->
                if (reminder.fireAt > now - 86_400_000L) {
                    schedule(reminder.uid, reminder.fireAt)
                } else {
                    reminderDao.markCompleted(reminder.uid, now)
                }
            }
    }

    private fun workName(uid: String) = "ochre-reminder-$uid"

    private companion object {
        const val TAG = "ochre-reminders"
    }
}