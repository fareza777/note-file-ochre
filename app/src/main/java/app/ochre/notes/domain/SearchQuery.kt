package app.ochre.notes.domain

import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

data class SearchQuery(
    val terms: List<String> = emptyList(),
    val phrases: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val pinnedOnly: Boolean = false,
    val archivedOnly: Boolean = false,
    val imageOnly: Boolean = false,
    val audioOnly: Boolean = false,
    val attachmentOnly: Boolean = false,
    val reminderOnly: Boolean = false,
    val beforeDate: LocalDate? = null,
    val afterDate: LocalDate? = null,
    val raw: String = "",
) {
    val isEmpty: Boolean
        get() = terms.isEmpty() && phrases.isEmpty() && tags.isEmpty() && filterLabels.isEmpty()

    val filterLabels: List<String>
        get() = buildList {
            if (pinnedOnly) add("Pinned")
            if (archivedOnly) add("Archived")
            if (imageOnly) add("Photos")
            if (audioOnly) add("Voice")
            if (attachmentOnly) add("Attachments")
            if (reminderOnly) add("Reminders")
            tags.forEach { add("#$it") }
            beforeDate?.let { add("Before $it") }
            afterDate?.let { add("After $it") }
        }

    fun toFtsExpression(): String? {
        val tokens = buildList {
            terms.forEach { term ->
                sanitizeWords(term).forEach { add("$it*") }
            }
            phrases.forEach { phrase ->
                val words = sanitizeWords(phrase)
                if (words.isNotEmpty()) add("\"${words.joinToString(" ")}\"")
            }
        }
        return tokens.takeIf { it.isNotEmpty() }?.joinToString(" AND ")
    }

    fun highlightTokens(): List<String> = buildList {
        terms.forEach { addAll(sanitizeWords(it)) }
        phrases.forEach { addAll(sanitizeWords(it)) }
        tags.forEach { add(it) }
    }.distinct()

    fun beforeMillis(zone: ZoneId): Long? = beforeDate?.atStartOfDay(zone)?.toInstant()?.toEpochMilli()

    fun afterMillis(zone: ZoneId): Long? = afterDate?.atStartOfDay(zone)?.toInstant()?.toEpochMilli()

    private fun sanitizeWords(value: String): List<String> =
        value.lowercase(Locale.ROOT)
            .map { ch -> if (ch.isLetterOrDigit()) ch else ' ' }
            .joinToString("")
            .split(' ')
            .filter { it.isNotBlank() }
}

object SearchQueryParser {

    fun parse(input: String, today: LocalDate = LocalDate.now()): SearchQuery {
        val rawTokens = tokenize(input)
        val terms = mutableListOf<String>()
        val phrases = mutableListOf<String>()
        val tags = linkedSetOf<String>()
        var pinned = false
        var archived = false
        var images = false
        var audio = false
        var attachments = false
        var reminders = false
        var before: LocalDate? = null
        var after: LocalDate? = null

        rawTokens.forEach { token ->
            if (token.quoted) {
                if (token.value.isNotBlank()) phrases.add(token.value.trim())
                return@forEach
            }
            val value = token.value.trim()
            if (value.isBlank()) return@forEach
            if (value.startsWith("#") && value.length > 1) {
                val tag = MarkdownUtils.normalizeTag(value)
                if (tag.isNotBlank()) tags.add(tag)
                return@forEach
            }
            val separator = value.indexOf(':')
            if (separator <= 0) {
                terms.add(value)
                return@forEach
            }
            val key = value.substring(0, separator).lowercase(Locale.ROOT)
            val argument = value.substring(separator + 1).trim().trim('"')
            when (key) {
                "tag", "tags" -> argument.split(',').forEach { part ->
                    val tag = MarkdownUtils.normalizeTag(part.trim())
                    if (tag.isNotBlank()) tags.add(tag)
                }
                "is" -> when (argument.lowercase(Locale.ROOT)) {
                    "pinned", "starred", "pin" -> pinned = true
                    "archived", "archive" -> archived = true
                    "reminder", "reminders" -> reminders = true
                    "media", "attachment", "attachments" -> attachments = true
                    else -> terms.add(value)
                }
                "has" -> when (argument.lowercase(Locale.ROOT)) {
                    "image", "images", "photo", "photos" -> images = true
                    "audio", "voice", "recording" -> audio = true
                    "attachment", "attachments", "media", "file", "files" -> attachments = true
                    "reminder", "reminders" -> reminders = true
                    else -> terms.add(value)
                }
                "before", "after", "since", "until" -> {
                    val parsed = parseDate(argument, today)
                    if (parsed == null) {
                        terms.add(value)
                    } else {
                        if (key == "after" || key == "since") after = parsed else before = parsed
                    }
                }
                else -> terms.add(value)
            }
        }

        return SearchQuery(
            terms = terms,
            phrases = phrases,
            tags = tags.toList(),
            pinnedOnly = pinned,
            archivedOnly = archived,
            imageOnly = images,
            audioOnly = audio,
            attachmentOnly = attachments,
            reminderOnly = reminders,
            beforeDate = before,
            afterDate = after,
            raw = input,
        )
    }

    fun parseDate(value: String, today: LocalDate = LocalDate.now()): LocalDate? {
        val cleaned = value.trim().lowercase(Locale.ROOT)
        if (cleaned.isBlank()) return null
        return when (cleaned) {
            "today" -> today
            "yesterday" -> today.minusDays(1)
            "tomorrow" -> today.plusDays(1)
            else -> {
                if (cleaned.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
                    runCatching { LocalDate.parse(cleaned) }.getOrNull()
                } else if (cleaned.matches(Regex("\\d+d"))) {
                    today.minusDays(cleaned.dropLast(1).toLongOrNull() ?: return null)
                } else {
                    null
                }
            }
        }
    }

    private data class RawToken(val value: String, val quoted: Boolean)

    private fun tokenize(input: String): List<RawToken> {
        val tokens = mutableListOf<RawToken>()
        val current = StringBuilder()
        var inQuotes = false
        var wasQuoted = false
        var escaped = false
        input.forEach { char ->
            when {
                escaped -> {
                    current.append(char)
                    escaped = false
                }
                char == '\\' -> escaped = true
                char == '"' -> {
                    if (inQuotes) {
                        tokens.add(RawToken(current.toString(), true))
                        current.clear()
                        inQuotes = false
                        wasQuoted = true
                    } else {
                        inQuotes = true
                        wasQuoted = true
                    }
                }
                char.isWhitespace() && !inQuotes -> {
                    if (current.isNotEmpty()) {
                        tokens.add(RawToken(current.toString(), wasQuoted))
                        current.clear()
                    }
                    wasQuoted = false
                }
                else -> current.append(char)
            }
        }
        if (current.isNotEmpty()) tokens.add(RawToken(current.toString(), wasQuoted && inQuotes))
        return tokens
    }
}