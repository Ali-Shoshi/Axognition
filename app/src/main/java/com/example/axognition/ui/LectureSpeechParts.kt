package com.example.axognition.ui

/** Keep the first audio file small; following parts are generated while it plays. */
internal fun lectureSpeechParts(text: String, firstPartLimit: Int = 80): List<String> {
    val parts = mutableListOf<String>()
    var remaining = text.trim()
    val sentenceEnd = Regex("""[.!?](?:["'”’)]*)(?:\s|$)""")
    while (remaining.isNotEmpty()) {
        val limit = if (parts.isEmpty()) firstPartLimit else 100
        val sentence = sentenceEnd.find(remaining)?.range?.last?.plus(1)
        val end = when {
            sentence != null && sentence <= limit -> sentence
            remaining.length <= limit -> remaining.length
            else -> remaining.lastIndexOf(' ', limit).takeIf { it > 0 }
                ?: remaining.indexOf(' ').takeIf { it > 0 } ?: remaining.length
        }
        parts.add(remaining.substring(0, end).trim())
        remaining = remaining.substring(end).trimStart()
    }
    return parts
}
