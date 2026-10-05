package com.example.axognition.ui

import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** Runs the assistant's bundled male models through the real native synthesis and audio queue. */
@RunWith(AndroidJUnit4::class)
class OfflineAssistantSpeechTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun assistantVoicesGenerateAndPlayInBothLanguages() {
        for ((language, text) in listOf(
            "en" to "One half plus one quarter makes three quarters.",
            "sq" to "Një e dyta plus një e katërta bëjnë tre të katërtat."
        )) {
            val done = CountDownLatch(1)
            val ok = AtomicBoolean(false)
            val callbacksOnMain = AtomicBoolean(true)
            val duration = AtomicLong()
            val speaker = OfflineSpeechPlayer(
                context,
                onStarted = { _, milliseconds ->
                    if (Looper.myLooper() != Looper.getMainLooper()) callbacksOnMain.set(false)
                    duration.set(milliseconds)
                },
                onEnded = { _, success ->
                    if (Looper.myLooper() != Looper.getMainLooper()) callbacksOnMain.set(false)
                    ok.set(success)
                    done.countDown()
                },
                role = OfflineVoiceRole.ASSISTANT
            )
            try {
                assertTrue(speaker.speak(text, language, language, 2f))
                assertTrue("$language assistant speech did not finish", done.await(60, TimeUnit.SECONDS))
                assertTrue("$language assistant speech failed", ok.get())
                assertTrue("$language assistant voice produced no playable duration", duration.get() > 500)
                assertTrue("Speech callbacks must run on main", callbacksOnMain.get())
            } finally {
                speaker.close()
            }
        }
    }

    @Test fun streamedChunksPlayInOrderAndFinishSentinelRunsLast() {
        val started = Collections.synchronizedList(mutableListOf<String>())
        val ended = Collections.synchronizedList(mutableListOf<String>())
        val failed = AtomicBoolean(false)
        val done = CountDownLatch(1)
        val speaker = OfflineSpeechPlayer(
            context,
            onStarted = { id, _ -> started.add(id) },
            onEnded = { id, success ->
                if (!success) failed.set(true)
                ended.add(id)
                if (id == "finish") done.countDown()
            },
            role = OfflineVoiceRole.ASSISTANT
        )
        try {
            assertTrue(speaker.speak("A fraction is part of a whole.", "first", "en", 2f,
                queueMode = SpeechQueueMode.ADD))
            assertTrue(speaker.speak("Equal parts have the same size.", "second", "en", 2f,
                queueMode = SpeechQueueMode.ADD))
            assertTrue(speaker.enqueueSilence(1, "finish"))
            assertTrue("The speech queue did not drain", done.await(60, TimeUnit.SECONDS))
            assertFalse("A queued chunk failed", failed.get())
            assertEquals(listOf("first", "second", "finish"), started.toList())
            assertEquals(listOf("first", "second", "finish"), ended.toList())
        } finally {
            speaker.close()
        }
    }

    @Test fun replacingOrStoppingPlayingSpeechDropsQueuedChunksAndFinishSentinel() {
        for (stopFirst in listOf(false, true)) {
            val playing = CountDownLatch(1)
            val done = CountDownLatch(1)
            val started = Collections.synchronizedList(mutableListOf<String>())
            val ended = Collections.synchronizedList(mutableListOf<String>())
            val probeOk = AtomicBoolean(false)
            val speaker = OfflineSpeechPlayer(
                context,
                onStarted = { id, _ ->
                    started.add(id)
                    if (id == "playing") playing.countDown()
                },
                onEnded = { id, success ->
                    ended.add(id)
                    if (id == "probe") {
                        probeOk.set(success)
                        done.countDown()
                    }
                },
                role = OfflineVoiceRole.ASSISTANT
            )
            try {
                assertTrue(speaker.speak(
                    "Let us look carefully at these equal parts and work out the answer together. ".repeat(3),
                    "playing", "en", 1f
                ))
                assertTrue(speaker.speak("This queued answer must be cancelled.", "pending", "en", 2f,
                    queueMode = SpeechQueueMode.ADD))
                assertTrue(speaker.enqueueSilence(1, "cancelled-finish"))
                assertTrue("Current audio never started", playing.await(60, TimeUnit.SECONDS))
                if (stopFirst) speaker.stop()
                assertTrue(speaker.speak("All done.", "probe", "en", 2f))
                assertTrue("Replacement audio did not finish", done.await(60, TimeUnit.SECONDS))
                assertTrue("Replacement audio failed", probeOk.get())
                assertEquals("Cancelled chunks must never start", listOf("playing", "probe"), started.toList())
                assertEquals("Cancellation must drop old completion callbacks", listOf("probe"), ended.toList())
            } finally {
                speaker.close()
            }
        }
    }

    @Test fun normalizedFractionSpeechRangesStayWithinOriginalAnswerText() {
        for ((language, text) in listOf(
            "en" to "1/2 + 1/4 = 3/4. Now calculate 2/3 × 3/5.",
            "sq" to "1/2 + 1/4 = 3/4. Tani llogarit 2/3 × 3/5."
        )) {
            val done = CountDownLatch(1)
            val ok = AtomicBoolean(false)
            val invalidRange = AtomicBoolean(false)
            val ranges = Collections.synchronizedList(mutableListOf<Pair<Int, Int>>())
            val speaker = OfflineSpeechPlayer(
                context,
                onRange = { id, start, end ->
                    if (id != "fractions" || start < 0 || end <= start || end > text.length ||
                        Looper.myLooper() != Looper.getMainLooper()) invalidRange.set(true)
                    ranges.add(start to end)
                },
                onEnded = { _, success -> ok.set(success); done.countDown() },
                role = OfflineVoiceRole.ASSISTANT
            )
            try {
                assertTrue(speaker.speak(text, "fractions", language, 2f, naturalize = true))
                assertTrue("$language fraction speech did not finish", done.await(60, TimeUnit.SECONDS))
                assertTrue("$language fraction speech failed", ok.get())
                assertFalse("Normalized speech returned invalid original-text offsets", invalidRange.get())
                assertTrue("Playback produced no highlight ranges", ranges.isNotEmpty())
                assertTrue("Speech ranges moved backwards", ranges.zipWithNext().all { (a, b) -> b.first >= a.first })
            } finally {
                speaker.close()
            }
        }
    }
}
