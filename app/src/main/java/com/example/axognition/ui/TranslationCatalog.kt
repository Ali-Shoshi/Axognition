package com.example.axognition.ui

/** Exact labels take priority over templates. Captured names/numbers are never translated. */
internal class TranslationCatalog(private val entries: Map<String, String>) {
    private val templates = entries.filterKeys { it.contains("{0}") }.map { (english, translated) ->
        // Android's ICU regex engine requires both literal braces to be escaped.
        val parts = english.split(Regex("\\{\\d+\\}"))
        Regex(parts.joinToString("(.+?)") { Regex.escape(it) }, RegexOption.DOT_MATCHES_ALL) to translated
    }

    fun translate(text: String): String {
        entries[text]?.let { return it }
        for ((pattern, translated) in templates) {
            val match = pattern.matchEntire(text) ?: continue
            return Regex("\\{(\\d+)\\}").replace(translated) { token ->
                match.groupValues[token.groupValues[1].toInt() + 1]
            }
        }
        return text
    }
}
