package com.example.axognition.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** Exercises the actual bundled native models and Android audio playback. */
@RunWith(AndroidJUnit4::class)
class OfflineLectureNarratorTest {
    @Test fun bothBundledLanguagesGenerateAndPlayWithoutAnExternalSpeechEngine() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        for ((language, text) in listOf("en" to "One half plus one quarter equals three quarters.",
                "sq" to "Një e dyta plus një e katërta është e barabartë me tre të katërtat.")) {
            val done = CountDownLatch(1)
            val ok = AtomicBoolean(false)
            val duration = AtomicLong()
            val narrator = OfflineSpeechPlayer(context, {}, { _, milliseconds -> duration.set(milliseconds) },
                { _, success -> ok.set(success); done.countDown() })
            try {
                assertTrue(narrator.speak(text, language, language, 2f))
                assertTrue("$language narration did not finish", done.await(60, TimeUnit.SECONDS))
                assertTrue("$language narration failed", ok.get())
                assertTrue("$language voice produced no playable duration", duration.get() > 500)
            } finally { narrator.close() }
        }
    }

    @Test fun replayCancelsOldSpeech() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val done = CountDownLatch(1)
        val wrongCallback = AtomicBoolean(false)
        val narrator = OfflineSpeechPlayer(context, {}, { _, _ -> },
            { id, ok -> if (id == "new" && ok) done.countDown() else wrongCallback.set(true) })
        try {
            narrator.speak("This old explanation must not continue. ".repeat(40), "old", "en", 1f)
            narrator.speak("One half.", "new", "en", 2f)
            assertTrue("Replacement narration did not finish", done.await(60, TimeUnit.SECONDS))
            assertFalse("Cancelled narration reported completion", wrongCallback.get())
        } finally { narrator.close() }
    }

    @Test fun stopDuringPlaybackAllowsANewRequestWithoutAStaleCompletion() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val started = CountDownLatch(1)
        val stoppedCompletion = CountDownLatch(1)
        val replacement = CountDownLatch(1)
        val narrator = OfflineSpeechPlayer(context, {},
            { id, _ -> if (id == "long") started.countDown() },
            { id, ok -> if (id == "long") stoppedCompletion.countDown()
                else if (id == "replacement" && ok) replacement.countDown() })
        try {
            narrator.speak("Look at these equal parts. One half plus one quarter makes three quarters. ".repeat(3),
                "long", "en", 1f)
            assertTrue("Narration never started", started.await(60, TimeUnit.SECONDS))
            narrator.stop()
            assertFalse("Stopped playback reported completion", stoppedCompletion.await(2, TimeUnit.SECONDS))
            narrator.speak("One half.", "replacement", "en", 0.5f)
            assertTrue("Narration did not restart", replacement.await(60, TimeUnit.SECONDS))
            assertEquals(1L, stoppedCompletion.count)
        } finally { narrator.close() }
    }
}
