package com.example.axognition.ui

/** One Android recognition attempt. A blank final result must not erase speech. */
internal class RecognitionCapture(private val waitingForWake: Boolean) {
    data class Result(val text: String, val wakeDetected: Boolean)
    var partialText = ""
        private set
    var wakeDetected = false
        private set
    private var completed = false

    fun partial(candidates: List<String>): Boolean {
        if (completed) return false
        val text = choose(candidates)
        if (text.isBlank()) return false
        wakeDetected = wakeDetected || VoiceConversation.containsWakePhrase(text)
        val changed = text != partialText
        partialText = text
        return changed
    }

    fun finish(candidates: List<String> = emptyList()): Result? {
        if (completed) return null
        completed = true
        val finalText = choose(candidates)
        val text = finalText.ifBlank { partialText }
        return Result(text, wakeDetected || VoiceConversation.containsWakePhrase(text))
    }

    private fun choose(candidates: List<String>): String {
        val nonEmpty = candidates.map(String::trim).filter { text -> text.any(Char::isLetterOrDigit) }
        return (if (waitingForWake) nonEmpty.firstOrNull(VoiceConversation::containsWakePhrase) else null)
            ?: nonEmpty.firstOrNull().orEmpty()
    }
}
