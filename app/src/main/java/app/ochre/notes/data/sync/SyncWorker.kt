package app.ochre.notes.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.ochre.notes.OchreApplication
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

class SyncWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as? OchreApplication)?.container ?: return Result.success()
        val settings = container.settingsRepository.settings.first()
        if (!settings.syncEnabled || settings.syncUrl.isBlank()) return Result.success()
        return runCatching { container.syncManager.syncNow() }
            .fold(
                onSuccess = { Result.success() },
                onFailure = { error ->
                    container.settingsRepository.setSyncResult(
                        System.currentTimeMillis(),
                        error.message ?: "Sync could not finish",
                    )
                    if (runAttemptCount < 2) Result.retry() else Result.failure()
                },
            )
    }

    companion object {
        private const val WORK_NAME = "ochre-auto-sync"

        fun schedule(context: Context, enabled: Boolean) {
            val manager = WorkManager.getInstance(context)
            if (!enabled) {
                manager.cancelUniqueWork(WORK_NAME)
                return
            }
            val request = PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
                .addTag(WORK_NAME)
                .build()
            manager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }
}