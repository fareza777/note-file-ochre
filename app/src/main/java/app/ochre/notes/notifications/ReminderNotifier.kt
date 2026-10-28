package app.ochre.notes.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.ochre.notes.MainActivity
import app.ochre.notes.R
import app.ochre.notes.data.local.MemoEntity
import app.ochre.notes.data.local.ReminderEntity

object ReminderNotifier {

    const val CHANNEL_REMINDERS = "reminders"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_REMINDERS,
            context.getString(R.string.channel_reminders),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.channel_reminders_description)
            enableVibration(true)
            setSound(
                android.provider.Settings.System.DEFAULT_NOTIFICATION_URI,
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                    .build(),
            )
        }
        manager.createNotificationChannel(channel)
    }

    fun show(context: Context, reminder: ReminderEntity, memo: MemoEntity) {
        ensureChannels(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        val notificationId = notificationId(reminder.uid)
        val sealed = memo.sealedUntil != null && memo.sealedUntil > System.currentTimeMillis()
        val title = if (memo.title.isBlank()) context.getString(R.string.untitled_note) else memo.title
        val body = if (sealed) {
            context.getString(R.string.reminder_sealed_body)
        } else {
            previewOf(memo.content).ifBlank { context.getString(R.string.reminder_generic_body) }
        }

        val openIntent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = android.net.Uri.parse("ochre://note/${memo.uid}")
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPending = PendingIntent.getActivity(
            context,
            notificationId,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val snooze = PendingIntent.getBroadcast(
            context,
            notificationId + 1,
            ReminderActionReceiver.intent(context, ReminderActionReceiver.ACTION_SNOOZE, reminder.uid),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val done = PendingIntent.getBroadcast(
            context,
            notificationId + 2,
            ReminderActionReceiver.intent(context, ReminderActionReceiver.ACTION_DONE, reminder.uid),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(openPending)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .addAction(0, context.getString(R.string.reminder_snooze), snooze)
            .addAction(0, context.getString(R.string.reminder_done), done)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        }
    }

    fun cancel(context: Context, reminderUid: String) {
        runCatching { NotificationManagerCompat.from(context).cancel(notificationId(reminderUid)) }
    }

    private fun notificationId(uid: String): Int = uid.hashCode() and 0x7FFFFFF

    private fun previewOf(content: String): String = content
        .replace(Regex("[#>*_`\\[\\]()~-]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(140)
}