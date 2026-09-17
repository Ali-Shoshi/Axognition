package com.example.axognition.ui

import org.junit.Assert.*
import org.junit.Test

class RecognitionCaptureTest {
    @Test fun emptyFinalKeepsTheWholeSpokenQuestion() {
        val capture = RecognitionCapture(true)
        capture.partial(listOf("Hey AI"))
        capture.partial(listOf("Hey AI what is a triangle?"))
        capture.partial(listOf(""))
        val result = capture.finish(listOf(""))!!
        assertEquals("Hey AI what is a triangle?", result.text)
        assertEquals(VoiceConversation.Turn.Ask("what is a triangle"),
            VoiceConversation().accept(result.text, VoiceListeningMode.WAKE_WORD, wakeDetected = result.wakeDetected))
        assertNull(capture.finish(listOf("Hey AI what is a triangle?")))
    }

    @Test fun wakeOnlyEmptyFinalStillOpensQuestionListening() {
        val capture = RecognitionCapture(true)
        capture.partial(listOf("Hey AI"))
        val result = capture.finish()!!
        val conversation = VoiceConversation()
        assertEquals(VoiceConversation.Turn.Listen,
            conversation.accept(result.text, VoiceListeningMode.CONVERSATION, wakeDetected = result.wakeDetected))
        assertTrue(conversation.active)
    }

    @Test fun blankOrMissingResultWithoutAnySpeechNeverSendsAQuestion() {
        val capture = RecognitionCapture(false)
        capture.partial(listOf("", "..."))
        val result = capture.finish()!!
        assertEquals(VoiceConversation.Turn.Wait,
            VoiceConversation().accept(result.text, VoiceListeningMode.CONVERSATION, expectingQuestion = true))
    }

    @Test fun finalCorrectionsWinAndAlternateWakeCandidateIsRecognized() {
        val capture = RecognitionCapture(true)
        capture.partial(listOf("a triangle", "Hey AI a triangle"))
        val result = capture.finish(listOf("Hey AI explain a square"))!!
        assertTrue(result.wakeDetected)
        assertEquals("Hey AI explain a square", result.text)
    }

    @Test fun eachListeningAttemptHasItsOwnTranscript() {
        val first = RecognitionCapture(false)
        first.partial(listOf("What is a circle?"))
        assertEquals("What is a circle?", first.finish()!!.text)
        val second = RecognitionCapture(false)
        assertEquals("", second.finish()!!.text)
    }
}
