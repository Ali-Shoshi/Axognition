package com.example.axognition.ui

/** Require an explicit address while the speaker is audible; discard its echo. */
internal object VoiceInterruption {
    fun command(heard: String, answer: String, canEndConversation: Boolean): String? {
        val wake = VoiceConversation.fromWakePhrase(heard)
        if (wake != null) {
            val candidate = normalized(wake)
            val spoken = normalized(answer)
            if (candidate.isNotEmpty() && spoken.contains(candidate)) return null
            return wake
        }
        if (canEndConversation && VoiceConversation.containsEndPhrase(heard) &&
            !VoiceConversation.containsEndPhrase(answer)) return heard
        return null
    }

    private fun normalized(text: String) = VoiceConversation.normalizeWakePhrases(text)
        .lowercase(java.util.Locale.ROOT)
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
}
