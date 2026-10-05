package com.example.axognition.ui

/**
 * Turns notation into words that neural and installed TTS engines pronounce naturally.
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
        .replace(Regex("(?<=[\\d)])\\s*-\\s*(?=[\\d(])"), " minus ")
        .replace(Regex("(?<=\\d)\\s*cm²(?![A-Za-z])"), squareCentimetres)
        .replace(Regex("(?<=\\d)\\s*m²(?![A-Za-z])"), squareMetres)
        .replace(Regex("(?<=\\d)\\s*cm\\b"), centimetres)
        .replace(Regex("(?<=\\d)\\s*m\\b"), metres)
        .replace(Regex("(?<![\\d/])(\\d{1,6})\\s*/\\s*(\\d{1,6})(?![\\d/])")) {
            spokenFraction(it.groupValues[1].toInt(), it.groupValues[2].toInt(), albanian)
        }
        .replace("×", times)
        .replace("+", " plus ")
        .replace("−", " minus ")
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

private fun spokenFraction(numerator: Int, denominator: Int, albanian: Boolean): String {
    val englishNumbers = listOf("zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven", "twelve")
    val albanianNumbers = listOf("zero", "një", "dy", "tre", "katër", "pesë", "gjashtë", "shtatë", "tetë", "nëntë", "dhjetë", "njëmbëdhjetë", "dymbëdhjetë")
    val number = (if (albanian) albanianNumbers else englishNumbers).getOrNull(numerator) ?: numerator.toString()
    if (albanian) {
        val ordinal = mapOf(2 to "dyta", 3 to "treta", 4 to "katërta", 5 to "pesta", 6 to "gjashta", 7 to "shtata", 8 to "teta", 9 to "nënta", 10 to "dhjeta", 11 to "njëmbëdhjeta", 12 to "dymbëdhjeta")[denominator]
        return if (ordinal == null) "$number mbi $denominator"
        else if (numerator == 1) "$number e $ordinal" else "$number të ${ordinal}t"
    }
    val ordinal = mapOf(2 to "half", 3 to "third", 4 to "quarter", 5 to "fifth", 6 to "sixth", 7 to "seventh", 8 to "eighth", 9 to "ninth", 10 to "tenth", 11 to "eleventh", 12 to "twelfth")[denominator]
    return if (ordinal == null) "$number over $denominator" else if (numerator == 1) "$number $ordinal"
    else "$number ${if (denominator == 2) "halves" else ordinal + "s"}"
}
