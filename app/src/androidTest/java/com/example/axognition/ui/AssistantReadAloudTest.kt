package com.example.axognition.ui

import android.os.Looper
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

@RunWith(AndroidJUnit4::class)
class AssistantReadAloudTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun longAnswersStartWithin1500msInBothLanguagesAndCompleteOnce() {
        for ((language, text) in listOf(
            "en" to "When you add fractions with different denominators, you first need to find a common denominator. " +
                "Rewrite each fraction using equal sized parts. Add the numerators and keep the denominator. Finally, simplify your answer if possible.",
            "sq" to "Kur mbledh thyesa me emërues të ndryshëm, fillimisht duhet të gjesh një emërues të përbashkët. " +
                "Shkruaje çdo thyesë duke përdorur pjesë të barabarta. Mblidh numëruesit dhe mbaje emëruesin. Në fund, thjeshto përgjigjen nëse është e mundur."
        )) {
            val warmed = CountDownLatch(1)
            val done = CountDownLatch(1)
            val beganAt = AtomicLong()
            val firstStartMs = AtomicLong(-1)
            val starts = AtomicInteger()
            val ends = AtomicInteger()
            val ok = AtomicBoolean(false)
            val mainCallbacks = AtomicBoolean(true)
            val speaker = OfflineSpeechPlayer(context,
                onStatus = { if (it.isEmpty()) warmed.countDown() },
                onStarted = { _, _ ->
                    if (Looper.myLooper() != Looper.getMainLooper()) mainCallbacks.set(false)
                    starts.incrementAndGet()
                    firstStartMs.compareAndSet(-1, SystemClock.elapsedRealtime() - beganAt.get())
                },
                onEnded = { _, success ->
                    if (Looper.myLooper() != Looper.getMainLooper()) mainCallbacks.set(false)
                    ends.incrementAndGet(); ok.set(success); done.countDown()
                }, role = OfflineVoiceRole.ASSISTANT)
            try {
                speaker.warmUp(language)
                assertTrue("The chat voice did not prepare", warmed.await(30, TimeUnit.SECONDS))
                beganAt.set(SystemClock.elapsedRealtime())
                assertTrue(speaker.readAloud(text, "read-aloud-$language", language, 2f))
                assertTrue("The full answer did not finish", done.await(60, TimeUnit.SECONDS))
                assertTrue("Read-aloud failed", ok.get())
                assertTrue("The answer should play in multiple parts", starts.get() > 1)
                assertEquals("Only the final part should complete the answer", 1, ends.get())
                assertTrue("Callbacks must run on main", mainCallbacks.get())
                android.util.Log.i("AXO_READ_ALOUD_TEST", "$language firstStartMs=${firstStartMs.get()} parts=${starts.get()}")
                assertTrue("$language read-aloud took ${firstStartMs.get()}ms to start", firstStartMs.get() in 0..1500)
            } finally { speaker.close() }
        }
    }

    @Test fun stoppingReadAloudCancelsRemainingPartsAndAllowsAnotherAnswer() {
        val playing = CountDownLatch(1)
        val done = CountDownLatch(1)
        val completed = Collections.synchronizedList(mutableListOf<String>())
        val replacementOk = AtomicBoolean(false)
        val speaker = OfflineSpeechPlayer(context,
            onStarted = { id, _ -> if (id == "old") playing.countDown() },
            onEnded = { id, success ->
                completed.add(id)
                if (id == "new") { replacementOk.set(success); done.countDown() }
            }, role = OfflineVoiceRole.ASSISTANT)
        try {
            assertTrue(speaker.readAloud("Look at these equal parts. Add the fractions carefully and then simplify the result. ".repeat(5), "old", "en"))
            assertTrue(playing.await(30, TimeUnit.SECONDS))
            speaker.stop()
            assertTrue(speaker.readAloud("The answer is three quarters. Now check your work.", "new", "en", 2f))
            assertTrue(done.await(30, TimeUnit.SECONDS))
            assertTrue(replacementOk.get())
            assertEquals("Stopped answers must never complete later", listOf("new"), completed.toList())
        } finally { speaker.close() }
    }
}
