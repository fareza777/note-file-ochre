package app.ochre.notes.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.ochre.notes.OchreApplication

class ReminderWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val reminderUid = inputData.getString(KEY_REMINDER) ?: return Result.failure()
        val container = (applicationContext as OchreApplication).container
        val reminderDao = container.database.reminderDao()
        val reminder = reminderDao.findByUid(reminderUid) ?: return Result.success()
        if (reminder.completedAt != null) return Result.success()
        val memo = container.database.memoDao().findByUid(reminder.memoUid)
        if (memo == null || memo.deletedAt != null) {
            reminderDao.markCompleted(reminderUid, System.currentTimeMillis())
            return Result.success()
        }
        reminderDao.markCompleted(reminderUid, System.currentTimeMillis())
        ReminderNotifier.show(applicationContext, reminder, memo)
        return Result.success()
    }

    companion object {
        const val KEY_REMINDER = "reminder_uid"
    }
}