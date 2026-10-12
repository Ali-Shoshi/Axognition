package com.example.axognition.shared

import java.util.Locale

/** Shared by the server and Android speech, so interface language is only a fallback. */
object AssistantLanguage {
    private fun normalized(text: String) = text.lowercase(Locale.ROOT).replace('ë', 'e').replace('ç', 'c')

    private val requestedLanguage = Regex(
        """\b(?:answer|reply|respond|speak|explain|say|write|translate)(?:\s+\w+){0,4}\s+(?:in|into)\s+(albanian|shqip|english)\b|\b(?:pergjigju|pergjigje|fol|shpjego|thuaje|shkruaj|ktheje|perkth[e]?je)(?:\s+\w+){0,3}\s+(?:ne\s+)?(shqip|anglisht)\b|^\s*(?:ne\s+)?(shqip|anglisht|english|albanian)[.!?]*\s*$"""
    )
    private val albanianWords = setOf(
        "cfare", "cka", "eshte", "jane", "shpjego", "shpjegim", "pergjigju", "pergjigje",
        "pershendetje", "faleminderit", "ngjyra", "ngjyre", "preferuar", "quhem", "emri",
        "mban", "kujton", "kujtesen", "mend", "thyesa", "thyese", "thyesat", "shume",
        "pse", "kush", "cili", "cila", "cilat", "nuk", "dua", "mund", "kam",
        "ke", "nje", "kete", "kjo", "problemin", "zgjidh", "zgjidhje", "trego",
        "hapat", "shkurt", "gjate", "numri", "numrat", "siperfaqja", "trekendesh",
        "si", "sa", "shqip", "anglisht", "po", "jo", "per", "te", "dhe", "nga", "ne", "qe",
        "ose", "jep", "ben", "nese", "duhet", "ndersa", "ketij", "kesaj", "njejte",
        "siperfaqen", "perimetrin", "perimetri", "trekendeshit", "trekendeshi", "llogarit", "llogaritje"
    )
    private val englishWords = setOf(
        "what", "which", "why", "who", "how", "where", "when", "is", "are", "was",
        "the", "my", "your", "you", "can", "could", "do", "does", "explain", "answer",
        "remember", "favorite", "colour", "color", "hello", "thanks", "please", "more",
        "yes", "no", "english", "albanian", "of", "and", "to", "it", "for", "with", "by",
        "equals", "represents", "part", "whole", "multiply", "divide", "triangle", "area", "perimeter",
        "fraction", "shows", "image", "result", "square", "rectangle", "capital"
    )
    private val words = Regex("[a-z]+")

    private fun explicit(text: String): String? {
        val match = requestedLanguage.findAll(normalized(text)).lastOrNull() ?: return null
        val name = match.groupValues.drop(1).firstOrNull { it.isNotEmpty() }
        return if (name in setOf("albanian", "shqip")) "sq" else "en"
    }

    /** Recognizes ordinary Albanian, including transcripts/typing without ë and ç. */
    fun detected(text: String): String? {
        val tokens = words.findAll(normalized(text)).map { it.value }.toList()
        val sq = tokens.count { it in albanianWords }
        val en = tokens.count { it in englishWords }
        return when {
            sq > en -> "sq"
            en > sq -> "en"
            else -> null
        }
    }

    fun replyLanguage(question: String, previousQuestions: List<String> = emptyList(), fallback: String = "en"): String {
        explicit(question)?.let { return it }
        detected(question)?.let { return it }
        // A numeric/ambiguous follow-up inherits the last student's language, never an erroneous bot reply.
        for (previous in previousQuestions.asReversed()) {
            (explicit(previous) ?: detected(previous))?.let { return it }
        }
        return if (fallback == "sq") "sq" else "en"
    }
}
