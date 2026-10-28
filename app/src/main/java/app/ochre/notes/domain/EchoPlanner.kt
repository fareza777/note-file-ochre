package app.ochre.notes.domain

import app.ochre.notes.data.local.MemoEntity
import java.util.Locale
import kotlin.math.abs

object EchoPlanner {

    private const val DAY_MS = 86_400_000L
    const val MIN_AGE_DAYS = 14

    data class Scored(val memo: MemoEntity, val score: Double)

    fun cooldownDays(level: Int): Int = when (level.coerceIn(0, 5)) {
        0 -> 14
        1 -> 30
        2 -> 60
        3 -> 120
        4 -> 240
        else -> 365
    }

    fun pick(
        candidates: List<MemoEntity>,
        now: Long,
        seed: Long,
        count: Int = 3,
    ): List<MemoEntity> {
        val minCreatedAt = now - MIN_AGE_DAYS * DAY_MS
        val eligible = candidates.asSequence()
            .filter { it.deletedAt == null && it.archivedAt == null }
            .filter { it.sealedUntil == null || it.sealedUntil <= now }
            .filter { it.createdAt <= minCreatedAt }
            .filter { memo ->
                val last = memo.echoedAt ?: return@filter true
                now - last >= cooldownDays(memo.echoLevel) * DAY_MS
            }
            .map { memo -> memo to score(memo, now, seed) }
            .sortedWith(
                compareByDescending<Pair<MemoEntity, Double>> { it.second }
                    .thenBy { it.first.uid },
            )
            .map { it.first }
            .toList()

        if (eligible.isEmpty()) return emptyList()
        val picks = mutableListOf<MemoEntity>()
        val usedLevels = mutableSetOf<Int>()
        for (memo in eligible) {
            if (picks.size >= count) break
            if (usedLevels.contains(memo.echoLevel) && eligible.size - picks.size > count) continue
            picks.add(memo)
            usedLevels.add(memo.echoLevel)
        }
        for (memo in eligible) {
            if (picks.size >= count) break
            if (picks.none { it.uid == memo.uid }) picks.add(memo)
        }
        return picks
    }

    fun score(memo: MemoEntity, now: Long, seed: Long): Double {
        val ageDays = ((now - memo.createdAt).coerceAtLeast(0)) / DAY_MS.toDouble()
        val sinceEchoDays = memo.echoedAt?.let { (now - it).coerceAtLeast(0) / DAY_MS.toDouble() } ?: ageDays
        val jitter = (abs((memo.uid + seed).hashCode()) % 1000) / 1000.0 * 8.0
        val pinnedBoost = if (memo.pinnedAt != null) 4.0 else 0.0
        return sinceEchoDays * 0.55 + ageDays * 0.08 + memo.echoLevel * 1.5 + pinnedBoost + jitter
    }

    fun ageLabel(createdAt: Long, now: Long, locale: Locale = Locale.getDefault()): String {
        val days = ((now - createdAt).coerceAtLeast(0)) / DAY_MS
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

    fun seedForDate(epochDay: Long): Long = epochDay * 31_337L
}