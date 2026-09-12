package com.example.axognition.ui

/**
 * Turns notation into words that installed TTS engines pronounce naturally.
 * Captions keep their original text; this is only used for spoken output.
 */
internal fun naturalizeSpeechForTts(text: String, language: String): String {
    if (text.isBlank()) return text
    val albanian = language == "sq"
    val times = if (albanian) " herë " else " times "
    val equals = if (albanian) " është e barabartë me " else " equals "
    val approximately = if (albanian) " afërsisht " else " approximately "
    val squareCentimetres = if (albanian) " centimetra katrorë" else " square centimetres"
    val squareMetres = if (albanian) " metra katrorë" else " square metres"
    val centimetres = if (albanian) " centimetra" else " centimetres"
    val metres = if (albanian) " metra" else " metres"
    val dividedBy = if (albanian) " pjesëtuar me " else " divided by "
    val then = if (albanian) ", pastaj " else ", then "

    return text
        .replace(Regex("(?<=\\d)\\s*cm²(?![A-Za-z])"), squareCentimetres)
        .replace(Regex("(?<=\\d)\\s*m²(?![A-Za-z])"), squareMetres)
        .replace(Regex("(?<=\\d)\\s*cm\\b"), centimetres)
        .replace(Regex("(?<=\\d)\\s*m\\b"), metres)
        .replace("×", times)
        .replace("÷", dividedBy)
        .replace("≈", approximately)
        .replace("=", equals)
        .replace("→", then)
        .replace("·", ", ")
        // Parentheses are visual grouping; spoken pauses around them sound clipped.
        .replace("(", " ")
        .replace(")", " ")
        .replace("²", if (albanian) " në katror" else " squared")
        .replace(Regex("\\s+"), " ")
        .trim()
}
