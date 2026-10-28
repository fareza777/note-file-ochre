package app.ochre.notes.ui.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object Formatting {

    private val zone: ZoneId get() = ZoneId.systemDefault()

    fun dayOf(epochMillis: Long): LocalDate =
        Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()

    fun dayHeader(epochMillis: Long, now: Long = System.currentTimeMillis()): String {
        val date = dayOf(epochMillis)
        val today = dayOf(now)
        return when {
            date == today -> "Today"
            date == today.minusDays(1) -> "Yesterday"
            date.year == today.year -> capitalize(date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault())))
            else -> capitalize(date.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.getDefault())))
        }
    }

    fun shortDate(epochMillis: Long): String =
        capitalize(dayOf(epochMillis).format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())))

    fun time(epochMillis: Long): String =
        Instant.ofEpochMilli(epochMillis).atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm"))

    fun dateTime(epochMillis: Long): String =
        capitalize(
            Instant.ofEpochMilli(epochMillis).atZone(zone)
                .format(DateTimeFormatter.ofPattern("d MMM yyyy · HH:mm", Locale.getDefault())),
        )

    fun reminderTime(epochMillis: Long, now: Long = System.currentTimeMillis()): String {
        val date = dayOf(epochMillis)
        val today = dayOf(now)
        val time = time(epochMillis)
        return when {
            date == today -> "Today, $time"
            date == today.plusDays(1) -> "Tomorrow, $time"
            date.year == today.year -> "${capitalize(date.format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault())))}, $time"
            else -> "${capitalize(date.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())))}, $time"
        }
    }

    fun greeting(epochMillis: Long = System.currentTimeMillis()): String {
        val hour = Instant.ofEpochMilli(epochMillis).atZone(zone).hour
        return when {
            hour < 5 -> "Still awake?"
            hour < 12 -> "Good morning."
            hour < 18 -> "Good afternoon."
            else -> "Good evening."
        }
    }

    fun duration(millis: Long?): String {
        if (millis == null || millis <= 0) return "0:00"
        val totalSeconds = millis / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%d:%02d".format(minutes, seconds)
    }

    fun bytes(size: Long): String = when {
        size < 1024 -> "$size B"
        size < 1024 * 1024 -> "%.0f KB".format(size / 1024f)
        size < 1024L * 1024 * 1024 -> "%.1f MB".format(size / (1024f * 1024f))
        else -> "%.1f GB".format(size / (1024f * 1024f * 1024f))
    }

    fun number(value: Int): String = String.format(Locale.getDefault(), "%,d", value)

    fun relativeAge(epochMillis: Long, now: Long = System.currentTimeMillis()): String {
        val days = ((now - epochMillis).coerceAtLeast(0)) / 86_400_000L
        return when {
            days < 1 -> "today"
            days < 2 -> "yesterday"
            days < 14 -> "$days days ago"
            days < 45 -> "${days / 7} weeks ago"
            days < 365 -> "${days / 30} months ago"
            days < 730 -> "a year ago"
            else -> "${days / 365} years ago"
        }
    }

    fun countdown(until: Long, now: Long = System.currentTimeMillis()): String {
        val remaining = (until - now).coerceAtLeast(0)
        val days = remaining / 86_400_000L
        val hours = (remaining % 86_400_000L) / 3_600_000L
        return when {
            days > 0 -> "in $days ${if (days == 1L) "day" else "days"}"
            hours > 0 -> "in $hours ${if (hours == 1L) "hour" else "hours"}"
            else -> "in a moment"
        }
    }

    fun capitalize(value: String): String =
        value.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
}