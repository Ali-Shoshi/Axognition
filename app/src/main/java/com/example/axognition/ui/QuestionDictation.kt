package com.example.axognition.ui

/** Cancelling/sending a draft invalidates every callback belonging to that recording. */
internal class QuestionDictation {
    private var generation = 0
    var currentAttempt: Attempt? = null
        private set
    fun cancel() { generation++; currentAttempt = null }

    fun begin(writeDraft: (String) -> Unit): Attempt {
        val token = ++generation
        return Attempt({ token == generation }, writeDraft).also { currentAttempt = it }
    }

    class Attempt(private val current: () -> Boolean, private val writeDraft: (String) -> Unit) {
        private val capture = RecognitionCapture(waitingForWake = false)
        private var finished = false
        val active: Boolean get() = !finished && current()

        fun partial(candidates: List<String>) {
            if (active && capture.partial(candidates)) writeDraft(capture.partialText)
        }

        fun finish(candidates: List<String> = emptyList()) {
            if (!active) return
            val text = capture.finish(candidates)?.text.orEmpty()
            finished = true
            if (text.isNotBlank()) writeDraft(text)
        }
    }
}
