package app.ochre.notes.domain

import app.ochre.notes.data.local.MemoEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EchoPlannerTest {

    private val day = 86_400_000L
    private val now = 1_800_000_000_000L

    private fun memo(
        uid: String,
        ageDays: Long,
        level: Int = 0,
        echoedAgoDays: Long? = null,
        pinned: Boolean = false,
        archived: Boolean = false,
        deleted: Boolean = false,
        sealedInDays: Long? = null,
    ) = MemoEntity(
        uid = uid,
        content = "note $uid",
        title = uid,
        titleNorm = uid,
        createdAt = now - ageDays * day,
        updatedAt = now - ageDays * day,
        pinnedAt = if (pinned) now - ageDays * day else null,
        archivedAt = if (archived) now - ageDays * day else null,
        deletedAt = if (deleted) now - ageDays * day else null,
        sealedUntil = sealedInDays?.let { now + it * day },
        echoLevel = level,
        echoedAt = echoedAgoDays?.let { now - it * day },
    )

    @Test
    fun `only notes older than two weeks surface`() {
        val picks = EchoPlanner.pick(listOf(memo("young", 3), memo("old", 30)), now, seed = 1)
        assertEquals(listOf("old"), picks.map { it.uid })
    }

    @Test
    fun `recently echoed notes respect their cooldown`() {
        val fresh = memo("fresh", 100, echoedAgoDays = 5)
        val due = memo("due", 100, echoedAgoDays = 20)
        val picks = EchoPlanner.pick(listOf(fresh, due), now, seed = 1)
        assertEquals(listOf("due"), picks.map { it.uid })
    }

    @Test
    fun `deleted archived and sealed notes never resurface`() {
        val picks = EchoPlanner.pick(
            listOf(
                memo("deleted", 60, deleted = true),
                memo("archived", 60, archived = true),
                memo("sealed", 60, sealedInDays = 10),
                memo("open", 60),
            ),
            now,
            seed = 3,
        )
        assertEquals(listOf("open"), picks.map { it.uid })
    }

    @Test
    fun `capsule that already opened is eligible`() {
        val opened = memo("opened", 60).copy(sealedUntil = now - 2 * day)
        assertEquals(listOf("opened"), EchoPlanner.pick(listOf(opened), now, seed = 2).map { it.uid })
    }

    @Test
    fun `picks are capped and unique`() {
        val candidates = (1..12).map { memo("note-$it", 40L + it) }
        val picks = EchoPlanner.pick(candidates, now, seed = 9, count = 3)
        assertEquals(3, picks.size)
        assertEquals(picks.size, picks.map { it.uid }.distinct().size)
    }

    @Test
    fun `cooldown grows with echo level`() {
        assertEquals(14, EchoPlanner.cooldownDays(0))
        assertEquals(30, EchoPlanner.cooldownDays(1))
        assertEquals(365, EchoPlanner.cooldownDays(9))
    }

    @Test
    fun `older untouched notes score higher than fresh echoes`() {
        val untouched = memo("same", 200, echoedAgoDays = 100)
        val justSeen = memo("same", 200, echoedAgoDays = 1)
        assertTrue(EchoPlanner.score(untouched, now, 7) > EchoPlanner.score(justSeen, now, 7))
    }

    @Test
    fun `seed is stable per day`() {
        assertEquals(EchoPlanner.seedForDate(20_000), EchoPlanner.seedForDate(20_000))
    }
}