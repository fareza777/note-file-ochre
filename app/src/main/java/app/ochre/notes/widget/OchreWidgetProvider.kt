package app.ochre.notes.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import app.ochre.notes.MainActivity
import app.ochre.notes.OchreApplication
import app.ochre.notes.R
import kotlinx.coroutines.launch

class OchreWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { refresh(context, manager, it) }
    }

    companion object {

        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, OchreWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(component)
            if (ids.isEmpty()) return
            ids.forEach { refresh(context, manager, it) }
        }

        private fun refresh(context: Context, manager: AppWidgetManager, id: Int) {
            val app = context.applicationContext as? OchreApplication ?: return
            val views = RemoteViews(context.packageName, R.layout.widget_ochre)
            views.setOnClickPendingIntent(R.id.widget_root, openApp(context))
            views.setOnClickPendingIntent(R.id.widget_new, newNote(context))
            app.container.appScope.launch {
                val now = System.currentTimeMillis()
                val count = app.container.database.memoDao().countActive()
                val latest = app.container.database.memoDao().latestActive(now)
                views.setTextViewText(
                    R.id.widget_count,
                    when {
                        count == 0 -> context.getString(R.string.widget_no_notes)
                        count == 1 -> context.getString(R.string.widget_one_note)
                        else -> context.getString(R.string.widget_many_notes, count)
                    },
                )
                views.setTextViewText(
                    R.id.widget_snippet,
                    latest?.let { it.title.ifBlank { it.content.lineSequence().firstOrNull { line -> line.isNotBlank() } ?: "" } }
                        ?.takeIf { it.isNotBlank() }
                        ?: context.getString(R.string.brand_tagline),
                )
                manager.updateAppWidget(id, views)
            }
        }

        private fun openApp(context: Context): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            return PendingIntent.getActivity(context, 1, intent, flags())
        }

        private fun newNote(context: Context): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                data = Uri.parse("ochre://new")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            return PendingIntent.getActivity(context, 2, intent, flags())
        }

        private fun flags() = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    }
}