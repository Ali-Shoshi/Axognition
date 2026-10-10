package com.example.axognition.ui

internal data class SpeechWordRange(val start: Int, val end: Int, val weight: Int)

/** Preserve caption offsets when one written symbol becomes several spoken words. */
internal class MappedSpeechText private constructor(
    val text: String,
    private val sources: List<IntRange>
) {
    fun replace(old: String, replacement: String): MappedSpeechText =
        replace(Regex(Regex.escape(old)), replacement)

    fun replace(pattern: Regex, replacement: String): MappedSpeechText =
        replace(pattern) { replacement }

    fun replace(pattern: Regex, replacement: (MatchResult) -> String): MappedSpeechText {
        val matches = pattern.findAll(text).toList()
        if (matches.isEmpty()) return this
        val output = StringBuilder()
        val mapped = mutableListOf<IntRange>()
        var previousEnd = 0
        for (match in matches) {
            output.append(text, previousEnd, match.range.first)
            mapped.addAll(sources.subList(previousEnd, match.range.first))
            val inserted = replacement(match)
            val source = sources.subList(match.range.first, match.range.last + 1)
            val original = source.minOf { it.first }..source.maxOf { it.last }
            output.append(inserted)
            repeat(inserted.length) { mapped.add(original) }
            previousEnd = match.range.last + 1
        }
        output.append(text, previousEnd, text.length)
        mapped.addAll(sources.subList(previousEnd, text.length))
        return MappedSpeechText(output.toString(), mapped)
    }

    fun trim(): MappedSpeechText {
        val first = text.indexOfFirst { !it.isWhitespace() }
        if (first < 0) return MappedSpeechText("", emptyList())
        val end = text.indexOfLast { !it.isWhitespace() } + 1
        return MappedSpeechText(text.substring(first, end), sources.subList(first, end))
    }

    fun words(start: Int = 0, end: Int = text.length): List<SpeechWordRange> =
        Regex("\\S+").findAll(text.substring(start, end)).map { word ->
            val original = sources.subList(start + word.range.first, start + word.range.last + 1)
            SpeechWordRange(original.minOf { it.first }, original.maxOf { it.last } + 1,
                word.value.length.coerceAtLeast(2))
        }.toList()

    companion object {
        fun original(text: String): MappedSpeechText = MappedSpeechText(text, text.indices.map { it..it })
    }
}
