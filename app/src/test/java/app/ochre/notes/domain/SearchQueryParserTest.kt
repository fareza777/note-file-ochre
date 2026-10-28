package app.ochre.notes.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchQueryParserTest {

    private val today = LocalDate.of(2026, 10, 27)

    @Test
    fun `understands filters and free terms`() {
        val query = SearchQueryParser.parse("holiday tag:work is:pinned has:image before:2026-01-01", today)
        assertEquals(listOf("holiday"), query.terms)
        assertEquals(listOf("work"), query.tags)
        assertTrue(query.pinnedOnly)
        assertTrue(query.imageOnly)
        assertEquals(LocalDate.of(2026, 1, 1), query.beforeDate)
        assertFalse(query.isEmpty)
    }

    @Test
    fun `keeps quoted phrases together`() {
        val query = SearchQueryParser.parse("\"deep work\" retreat", today)
        assertEquals(listOf("deep work"), query.phrases)
        assertEquals(listOf("retreat"), query.terms)
    }

    @Test
    fun `unknown is value stays a plain term`() {
        val query = SearchQueryParser.parse("is:weird", today)
        assertEquals(listOf("is:weird"), query.terms)
        assertFalse(query.pinnedOnly)
    }

    @Test
    fun `builds a prefix fts expression`() {
        val query = SearchQueryParser.parse("deep work \"morning pages\"", today)
        assertEquals("deep* AND work* AND \"morning pages\"", query.toFtsExpression())
    }

    @Test
    fun `date shorthands resolve against today`() {
        assertEquals(today, SearchQueryParser.parseDate("today", today))
        assertEquals(today.minusDays(1), SearchQueryParser.parseDate("yesterday", today))
        assertEquals(today.minusDays(7), SearchQueryParser.parseDate("7d", today))
        assertNull(SearchQueryParser.parseDate("whenever", today))
    }

    @Test
    fun `since and until map to after and before`() {
        val query = SearchQueryParser.parse("since:2026-03-01 until:2026-04-01", today)
        assertEquals(LocalDate.of(2026, 3, 1), query.afterDate)
        assertEquals(LocalDate.of(2026, 4, 1), query.beforeDate)
        assertEquals(2, query.filterLabels.size)
    }

    @Test
    fun `empty input is recognised as empty`() {
        assertTrue(SearchQueryParser.parse("   ", today).isEmpty)
        assertNull(SearchQueryParser.parse("", today).toFtsExpression())
    }

    @Test
    fun `tag filter normalises display value`() {
        val query = SearchQueryParser.parse("#Deep-Work tag:Ide,Baru", today)
        assertEquals(listOf("deep-work", "ide", "baru"), query.tags)
    }
}