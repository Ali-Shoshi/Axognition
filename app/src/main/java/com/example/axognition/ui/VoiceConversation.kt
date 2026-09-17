package com.example.axognition.ui

enum class VoiceListeningMode(val label: String, val hint: String) {
    OFF("Off", "Automatic listening is off"),
    WAKE_WORD("Hej AI", "Say “hej AI” before each question"),
    CONVERSATION("Conversation", "Say “hej AI” to start · “bye AI” to end")
}

/** Only final, non-empty recognition results can become a server request. */
internal class VoiceConversation {
    var active = false
        private set

    sealed interface Turn {
        data object Wait : Turn
        data object Listen : Turn
        data object End : Turn
        data class Ask(val question: String) : Turn
    }

    fun reset() { active = false }
    fun start() { active = true }

    fun accept(text: String, mode: VoiceListeningMode, expectingQuestion: Boolean = false, wakeDetected: Boolean = false): Turn {
        if (mode == VoiceListeningMode.OFF) return Turn.Wait
        val clean = text.trim()
        if (clean.none { it.isLetterOrDigit() }) {
            if (!wakeDetected) return Turn.Wait
            if (mode == VoiceListeningMode.CONVERSATION) active = true
            return Turn.Listen
        }
        if (mode == VoiceListeningMode.CONVERSATION && containsEndPhrase(clean)) {
            active = false
            return Turn.End
        }
        val wake = wakePhrase.find(clean)
        if (wake != null || wakeDetected) {
            if (mode == VoiceListeningMode.CONVERSATION) active = true
            val question = (if (wake != null) clean.substring(wake.range.last + 1)
                else clean.replaceFirst(Regex("""(?i)^\s*(?:hej|hey|hi)\b"""), ""))
                .trim(' ', ',', '.', ':', ';', '!', '?')
            return if (question.any { it.isLetterOrDigit() }) Turn.Ask(question) else Turn.Listen
        }
        return if (active || expectingQuestion) Turn.Ask(clean) else Turn.Wait
    }

    companion object {
        // Word boundaries avoid activating on fragments embedded in ordinary words.
        private val wakePhrase = Regex("""(?i)\b(?:hej|hey|hi)[\s,.:;-]*(?:a[.\s]*i|i|ay|eye)\b\.?""")
        private val endPhrase = Regex("""(?i)\b(?:bye|by|goodbye|baj|mirupafshim|mirëupafshim)[\s,.:;-]+(?:a[.\s]*i|i|ay)\b\.?""")
        // "Buy AI" is a common transcription of "bye AI". Restrict this
        // spelling to a short command so shopping questions do not end a chat.
        private val misheardEndPhrase = Regex("""(?i)^\s*(?:(?:ok|okay|thanks|thank you)[\s,.:;!-]+)?buy[\s,.:;-]+(?:a[.\s]*i|i|ay|eye)[\s,.!?]*(?:(?:thanks|thank you)[\s,.!?]*)?$""")
        fun containsWakePhrase(text: String): Boolean {
            if (wakePhrase.containsMatchIn(text)) return true
            // Android commonly returns A.I. as "I", "eye", "ay", or merges
            // both words in partial recognition results.
            return Regex("""(?i)(?:^|[^\p{L}\p{N}])(?:hejai|heyai|hiai|heji|heyi|heyay|heyeye)(?:$|[^\p{L}\p{N}])""")
                .containsMatchIn(text)
        }
        fun containsEndPhrase(text: String) = endPhrase.containsMatchIn(text) || misheardEndPhrase.matches(text)
        fun fromWakePhrase(text: String): String? = wakePhrase.find(text)?.let { text.substring(it.range.first) }
        fun normalizeWakePhrases(text: String): String = wakePhrase.replace(text, "hej ai")
    }
}
