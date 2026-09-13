package com.example.axognition.ui

import org.junit.Assert.*
import org.junit.Test

class VoiceConversationTest {
    @Test fun offNeverSendsQuestions() {
        val session = VoiceConversation()
        session.start()
        assertEquals(VoiceConversation.Turn.Wait, session.accept("hej AI explain triangles", VoiceListeningMode.OFF))
    }

    @Test fun wakeModeRequiresWakePhraseForEveryTurn() {
        val session = VoiceConversation()
        assertEquals(VoiceConversation.Turn.Wait, session.accept("explain triangles", VoiceListeningMode.WAKE_WORD))
        assertEquals(VoiceConversation.Turn.Ask("explain triangles"), session.accept("hej A.I. explain triangles", VoiceListeningMode.WAKE_WORD))
        assertFalse(session.active)
        assertEquals(VoiceConversation.Turn.Wait, session.accept("give an example", VoiceListeningMode.WAKE_WORD))
    }

    @Test fun conversationSurvivesSilenceAndEndsWithoutSendingGoodbye() {
        val session = VoiceConversation()
        val mode = VoiceListeningMode.CONVERSATION
        assertEquals(VoiceConversation.Turn.Listen, session.accept("hey AI", mode))
        repeat(10) { assertEquals(VoiceConversation.Turn.Wait, session.accept("  ", mode)) }
        assertTrue(session.active)
        assertEquals(VoiceConversation.Turn.Ask("What about a square?"), session.accept("What about a square?", mode))
        assertEquals(VoiceConversation.Turn.End, session.accept("thank you, by AI", mode))
        assertFalse(session.active)
        assertEquals(VoiceConversation.Turn.Wait, session.accept("background conversation", mode))
        assertEquals(VoiceConversation.Turn.Ask("start again"), session.accept("hej AI start again", mode))
    }

    @Test fun separateWakeAndQuestionWorksAndEmptyInputNeverSends() {
        val session = VoiceConversation()
        assertEquals(VoiceConversation.Turn.Listen, session.accept("Hej AI!", VoiceListeningMode.WAKE_WORD))
        assertEquals(VoiceConversation.Turn.Wait, session.accept("...", VoiceListeningMode.WAKE_WORD, true))
        assertEquals(VoiceConversation.Turn.Ask("Sa brinjë ka trekëndëshi?"), session.accept("Sa brinjë ka trekëndëshi?", VoiceListeningMode.WAKE_WORD, true))
    }

    @Test fun wakePhraseFromPartialTranscriptSurvivesShortenedFinalResult() {
        val session = VoiceConversation()
        assertEquals(VoiceConversation.Turn.Listen,
            session.accept("Hej", VoiceListeningMode.CONVERSATION, wakeDetected = true))
        assertTrue(session.active)
        assertEquals(VoiceConversation.Turn.Ask("Explain angles"),
            session.accept("Explain angles", VoiceListeningMode.CONVERSATION))
    }

    @Test fun commonAndroidWakePhraseSpellingsAreAccepted() {
        listOf("hej AI", "hey A.I.", "hey I", "hey ay", "hey eye").forEach {
            assertTrue("Wake phrase was not recognized: $it", VoiceConversation.containsWakePhrase(it))
        }
    }

    @Test fun commandsRespectWordBoundariesAndAlbanian() {
        assertFalse(VoiceConversation.containsWakePhrase("they aim to learn"))
        assertFalse(VoiceConversation.containsEndPhrase("by airplane"))
        assertTrue(VoiceConversation.containsEndPhrase("bye A.I."))
        assertTrue(VoiceConversation.containsEndPhrase("mirupafshim AI"))
        val session = VoiceConversation()
        session.start()
        session.reset()
        assertEquals(VoiceConversation.Turn.Wait, session.accept("next question", VoiceListeningMode.CONVERSATION))
    }
}
