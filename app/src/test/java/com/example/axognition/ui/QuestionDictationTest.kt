package com.example.axognition.ui

import org.junit.Assert.*
import org.junit.Test

class QuestionDictationTest {
    @Test fun sendingClearsTheQuestionAndLatePartialAndFinalCallbacksCannotRestoreIt() {
        val dictation = QuestionDictation()
        var draft = ""
        val recording = dictation.begin { draft = it }
        recording.partial(listOf("Çfarë është një thyesë?"))
        val sentQuestion = draft
        dictation.cancel() // Send button cancels recognition before clearing the input.
        draft = ""
        recording.partial(listOf(sentQuestion))
        recording.finish(listOf(sentQuestion))
        assertEquals("Çfarë është një thyesë?", sentQuestion)
        assertEquals("", draft)
        draft = "Next question"
        recording.partial(listOf(sentQuestion))
        assertEquals("Next question", draft)
    }

    @Test fun oldCallbacksCannotEditAnotherChatOrReplaceANewerRecording() {
        val dictation = QuestionDictation()
        var firstDraft = ""
        var nextDraft = ""
        val old = dictation.begin { firstDraft = it }
        old.partial(listOf("Old question"))
        dictation.cancel()
        val fresh = dictation.begin { nextDraft = it }
        fresh.partial(listOf("New question"))
        old.finish(listOf("Late result"))
        assertEquals("Old question", firstDraft)
        assertEquals("New question", nextDraft)
        fresh.finish(emptyList()) // Blank finals keep useful partial speech.
        assertEquals("New question", nextDraft)
        fresh.partial(listOf("Repeated late result"))
        assertEquals("New question", nextDraft)
    }
}
