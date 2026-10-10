package com.example.axognition.ui

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.example.axognition.data.AssistantApi
import org.junit.Assert.*
import org.junit.Test
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class StreamingAssistantSpeechTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun chatAndFloatingAssistantReusePreloadedVoiceAndClosingOneKeepsTheOtherReady() {
        val firstReady = CountDownLatch(1)
        val secondReady = CountDownLatch(1)
        val began = CountDownLatch(1)
        val first = OfflineSpeechPlayer(context, onStatus = { if (it.isEmpty()) firstReady.countDown() },
            role = OfflineVoiceRole.ASSISTANT)
        val second = OfflineSpeechPlayer(context, onStatus = { if (it.isEmpty()) secondReady.countDown() },
            onStarted = { _, _ -> began.countDown() }, role = OfflineVoiceRole.ASSISTANT)
        try {
            first.warmUp("en")
            assertTrue(firstReady.await(30, TimeUnit.SECONDS))
            second.warmUp("en")
            assertTrue("Second assistant loaded a duplicate model", secondReady.await(500, TimeUnit.MILLISECONDS))
            first.close()
            assertTrue(second.speakStreamingChunk("A triangle has three sides.", "shared", "en"))
            assertTrue("Closing chat unloaded the active assistant voice", began.await(1500, TimeUnit.MILLISECONDS))
        } finally { first.close(); second.close() }
    }

    @Test fun preloadedOpeningStartsWithin1500msInBothLanguagesAndKeepsFormulaHighlights() {
        for ((language, answer) in listOf(
            "en" to "To add fractions you first find a common denominator. Then calculate 1/2 + 1/4 = 3/4 and check your answer.",
            "sq" to "Për të mbledhur thyesat gjej emëruesin e përbashkët. Pastaj llogarit 1/2 + 1/4 = 3/4 dhe kontrollo përgjigjen."
        )) {
            val ready = CountDownLatch(1)
            val started = CountDownLatch(1)
            val done = CountDownLatch(1)
            val startedAt = AtomicLong()
            val firstQueuedAt = AtomicLong()
            val failed = AtomicBoolean(false)
            val invalidRange = AtomicBoolean(false)
            val chunks = Collections.synchronizedMap(mutableMapOf<String, Pair<String, Int>>())
            val ranges = Collections.synchronizedList(mutableListOf<Pair<Int, Int>>())
            val speaker = OfflineSpeechPlayer(context,
                onStatus = { if (it.isEmpty()) ready.countDown() },
                onStarted = { id, _ -> if (id != "finish" && startedAt.compareAndSet(0, SystemClock.elapsedRealtime())) started.countDown() },
                onRange = { id, start, end ->
                    val chunk = chunks[id]
                    if (chunk == null || start < 0 || end <= start || end > chunk.first.length || Looper.myLooper() != Looper.getMainLooper()) {
                        invalidRange.set(true)
                    } else ranges.add((chunk.second + start) to (chunk.second + end))
                },
                onEnded = { id, success -> if (!success) failed.set(true); if (id == "finish") done.countDown() },
                role = OfflineVoiceRole.ASSISTANT)
            try {
                speaker.warmUp(language)
                assertTrue("Voice did not preload", ready.await(30, TimeUnit.SECONDS))
                var index = 0
                val chunker = StreamingSpeechChunker { text, offset ->
                    val id = "part-${index++}"
                    chunks[id] = text to offset
                    firstQueuedAt.compareAndSet(0, SystemClock.elapsedRealtime())
                    assertTrue(speaker.speakStreamingChunk(text, id, language, 2f))
                }
                chunker.accept(answer.take(48))
                assertTrue("Opening did not queue before the rest of the answer", firstQueuedAt.get() > 0)
                assertTrue("Opening did not start while waiting for remaining text", started.await(1500, TimeUnit.MILLISECONDS))
                val latency = startedAt.get() - firstQueuedAt.get()
                Log.i("AXO_STREAM_TEST", "$language openingStartMs=$latency")
                assertTrue("Opening took ${latency}ms", latency in 0..1500)
                chunker.accept(answer, final = true)
                assertTrue(speaker.enqueueSilence(1, "finish"))
                assertTrue("Queue did not finish", done.await(60, TimeUnit.SECONDS))
                assertFalse("Speech failed", failed.get())
                assertFalse("Original word offsets were lost", invalidRange.get())
                assertTrue("Highlights moved backwards", ranges.zipWithNext().all { (a, b) -> b.first >= a.first })
                val formula = answer.indexOf("1/2")
                assertTrue("Expanded fraction has no original highlight", ranges.any { it.first == formula && it.second == formula + 3 })
            } finally { speaker.close() }
        }
    }

    /** Measures the actual tablet → Ktor → LM Studio → voice path, with no saved chat changes. */
    @Test fun liveAnswerStreamReportsTimeToFirstTextAndFirstSpeech() {
        AppLanguage.initialize(context)
        val language = AppLanguage.code
        val ready = CountDownLatch(1)
        val began = CountDownLatch(1)
        val done = CountDownLatch(1)
        val failed = AtomicBoolean(false)
        val sentAt = AtomicLong()
        val textAt = AtomicLong()
        val speechAt = AtomicLong()
        val main = Handler(Looper.getMainLooper())
        val speaker = OfflineSpeechPlayer(context,
            onStatus = { if (it.isEmpty()) ready.countDown() },
            onStarted = { _, _ -> if (speechAt.compareAndSet(0, SystemClock.elapsedRealtime())) began.countDown() },
            onEnded = { id, success -> if (!success) failed.set(true); if (id == "live-finish") done.countDown() },
            role = OfflineVoiceRole.ASSISTANT)
        try {
            speaker.warmUp(language)
            assertTrue(ready.await(30, TimeUnit.SECONDS))
            var index = 0
            val chunker = StreamingSpeechChunker { text, _ ->
                if (!speaker.speakStreamingChunk(text, "live-${index++}", language, 2f)) failed.set(true)
            }
            sentAt.set(SystemClock.elapsedRealtime())
            val answer = AssistantApi.streamQuestion("Why do triangles have three sides?") { partial ->
                textAt.compareAndSet(0, SystemClock.elapsedRealtime())
                main.post { chunker.accept(partial) }
            }
            assertTrue(answer.isNotBlank())
            main.post {
                chunker.accept(answer, final = true)
                if (!speaker.enqueueSilence(1, "live-finish")) { failed.set(true); done.countDown() }
            }
            assertTrue("Live answer never started speaking", began.await(30, TimeUnit.SECONDS))
            Log.i("AXO_STREAM_TEST", "live firstTextMs=${textAt.get() - sentAt.get()} firstSpeechMs=${speechAt.get() - sentAt.get()} textToSpeechMs=${speechAt.get() - textAt.get()}")
            assertTrue("Live speech queue did not complete", done.await(60, TimeUnit.SECONDS))
            assertFalse("Live speech failed", failed.get())
        } finally { speaker.close() }
    }
}
