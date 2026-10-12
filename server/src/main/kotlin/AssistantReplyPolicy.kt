package com.example

import java.util.Locale

private val personalWords = Regex("""(?i)\b(?:my|mine|our|i|i'm|me|im|ime|jam|quhem|më|mua)\b""")
private val questionOpening = Regex("""(?i)^\s*(?:what|why|how|when|where|who|do|does|did|can|could|would|is|are|tell me|explain|çfarë|pse|si|a\s)\b""")
private val topicWords = Regex("""[\p{L}\p{N}]+""")
private val commonWords = setOf("what", "whats", "is", "are", "was", "the", "a", "an", "my", "your", "our", "i", "me", "it", "do", "you", "did", "tell", "told", "remember", "can", "please", "of", "about", "that", "this", "and", "to", "in", "çfarë", "është", "im", "ime", "më", "a", "e", "si")

private fun factKey(text: String): String = topicWords.findAll(text.lowercase(Locale.ROOT)).joinToString(" ") {
    when (it.value) { "colour" -> "color"; "favourite", "fav" -> "favorite"; else -> it.value }
}

/** Exact personal assertions belong to this conversation and outrank generated assistant claims. */
internal fun assistantRecalledFact(question: String, history: List<AssistantHistoryMessage>): String? {
    val asked = Regex("""(?i)^\s*(?:please\s+)?(?:tell me\s+)?(?:what (?:is|was)|what['’]s|(?:do|can|could) you (?:still )?remember)\s+my\s+([^.!?]+)[.!?]*\s*$""")
        .matchEntire(question) ?: return null
    val key = factKey(asked.groupValues[1])
    val assertion = Regex("""(?i)^\s*(?:(?:remember|actually|that|and|now)[,\s]+)*my\s+(.+?)\s+(is|are)\s+([^.!?]+)[.!]*\s*$""")
    for (message in history.asReversed()) {
        if (message.role != "user") continue
        val fact = assertion.matchEntire(message.content) ?: continue
        if (factKey(fact.groupValues[1]) != key) continue
        val value = fact.groupValues[3].trim()
        // Leave complex clauses to the model, retaining the complete original conversation.
        if (assistantWordCount(value) > 12 || Regex("""(?i)\b(?:if|but|because|i|you)\b|[;\n]""").containsMatchIn(value)) return null
        return "Your ${fact.groupValues[1]} ${fact.groupValues[2]} $value."
    }
    return null
}

/** Repeat original user statements near a recall question, without inventing facts or dropping history. */
internal fun assistantRecallContext(question: String, history: List<AssistantHistoryMessage>): List<String> {
    if (!personalWords.containsMatchIn(question) && !Regex("(?i)\\b(?:remember|told|said|kujton)\\b").containsMatchIn(question)) return emptyList()
    val topics = topicWords.findAll(question.lowercase(Locale.ROOT)).map { it.value }.toSet() - commonWords
    val statements = history.filter { it.role == "user" && personalWords.containsMatchIn(it.content) &&
        !questionOpening.containsMatchIn(it.content) && !it.content.trimEnd().endsWith('?') }
    if (topics.isEmpty()) return statements.map { it.content }
    val scored = statements.map { statement ->
        statement.content to topicWords.findAll(statement.content.lowercase(Locale.ROOT)).map { it.value }.toSet().intersect(topics).size
    }
    val best = scored.maxOfOrNull { it.second } ?: 0
    return if (best == 0) emptyList() else scored.filter { it.second == best }.map { it.first }
}

/** Wait for punctuation followed by whitespace so a streamed decimal such as 3.14 stays intact. */
internal fun firstAssistantSentence(text: String, final: Boolean = false): String? {
    val endings = Regex("""[.!?]["'”’)]*(?:\s|$)""")
    for (match in endings.findAll(text)) {
        if (!final && match.range.last == text.lastIndex && !match.value.last().isWhitespace()) continue
        val before = text.substring(0, match.range.first)
        if (before.trim().all { it.isDigit() } || Regex("""(?i)\b(?:mr|mrs|ms|dr|prof|e\.g|i\.e)$""").containsMatchIn(before)) continue
        return text.substring(0, match.range.last + 1).trim()
    }
    return if (final) text.trim().takeIf { it.isNotEmpty() } else null
}

internal fun assistantWordCount(text: String): Int = Regex("\\S+").findAll(text).count()

/** Keep both parts of a brief answer; never split a decimal or a sentence mid-stream. */
internal fun firstAssistantSentences(text: String, count: Int = 2, final: Boolean = false): String? {
    var end = 0
    repeat(count) {
        val remaining = text.substring(end)
        if (remaining.isBlank()) return if (final && end > 0) text.take(end).trim() else null
        val sentence = firstAssistantSentence(remaining, final) ?: return null
        end += remaining.indexOf(sentence) + sentence.length
    }
    return text.take(end).trim()
}
