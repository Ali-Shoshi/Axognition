package com.example.axognition.ui

import org.junit.Assert.*
import org.junit.Test

class VoiceInterruptionTest {
    @Test fun newWakeQuestionInterruptsAndSkipsRecognizedSpeakerEcho() {
        val command = VoiceInterruption.command(
            "A triangle has three sides. Hey AI what about circles?",
            "A triangle has three sides.", true)
        assertEquals("Hey AI what about circles?", command)
        val session = VoiceConversation().apply { start() }
        assertEquals(VoiceConversation.Turn.Ask("what about circles"),
            session.accept(command!!, VoiceListeningMode.CONVERSATION))
    }

    @Test fun ordinaryResponseAndBackgroundSpeechNeverInterrupt() {
        assertNull(VoiceInterruption.command("A triangle has three sides", "A triangle has three sides.", true))
        assertNull(VoiceInterruption.command("What about circles?", "A triangle has three sides.", true))
        assertNull(VoiceInterruption.command("", "A triangle has three sides.", true))
    }

    @Test fun quotedWakePhraseInTheAnswerIsNotAnInterruption() {
        val answer = "Say hej AI to ask another question."
        assertNull(VoiceInterruption.command("Hey AI", answer, true))
        assertNull(VoiceInterruption.command("hey I to ask another question", answer, true))
        assertEquals("Hey AI explain circles", VoiceInterruption.command("Hey AI explain circles", answer, true))
    }

    @Test fun wakePhraseAloneStopsSpeechAndWaitsForTheNextQuestion() {
        val command = VoiceInterruption.command("Hej AI", "A triangle has three sides.", true)
        assertEquals(VoiceConversation.Turn.Listen,
            VoiceConversation().accept(command!!, VoiceListeningMode.CONVERSATION))
    }

    @Test fun goodbyeCanStopConversationPlaybackButCannotEchoFromAnswer() {
        assertEquals("buy AI", VoiceInterruption.command("buy AI", "Here is another example.", true))
        assertNull(VoiceInterruption.command("buy AI", "Here is another example.", false))
        assertNull(VoiceInterruption.command("buy AI", "Say bye AI to finish.", true))
    }
}
