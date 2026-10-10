package com.example.axognition.ui

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
class OfflineLectureSpeechTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun longSlidesStartWithin1500msAndCompleteOnceInBothLanguages() {
        for ((language, text) in listOf(
            "en" to "The perimeter is the distance all the way around the outside of a shape, measured in length units. " +
                "Trace the boundary carefully. Add the side lengths to find the total distance. Now look inside the shape to compare its area.",
            "sq" to "Perimetri është largësia përgjatë gjithë kufirit të një figure dhe matet me njësi të gjatësisë. " +
                "Ndiq me kujdes kufirin e figurës. Mblidh gjatësitë e brinjëve për të gjetur largësinë e plotë. Tani shiko hapësirën brenda figurës."
        )) {
            val warmed = CountDownLatch(1)
            val finished = CountDownLatch(1)
            val beganAt = AtomicLong()
            val firstStartMs = AtomicLong(-1)
            val ends = AtomicInteger()
            val ok = AtomicBoolean(true)
            val starts = AtomicInteger()
            val speaker = OfflineSpeechPlayer(context,
                onStatus = { if (it.isEmpty()) warmed.countDown() },
                onStarted = { _, _ ->
                    starts.incrementAndGet()
                    firstStartMs.compareAndSet(-1, SystemClock.elapsedRealtime() - beganAt.get())
                },
                onEnded = { _, success -> ends.incrementAndGet(); ok.set(success); finished.countDown() },
                role = OfflineVoiceRole.LECTURE)
            try {
                speaker.warmUp(language)
                assertTrue("Voice did not warm up", warmed.await(30, TimeUnit.SECONDS))
                beganAt.set(SystemClock.elapsedRealtime())
                assertTrue(speaker.speak(text, "long-slide-$language", language, 2f))
                assertTrue("Slide did not finish", finished.await(60, TimeUnit.SECONDS))
                assertTrue("Narration failed", ok.get())
                assertTrue("Expected audio to start before all parts were generated", starts.get() > 1)
                assertEquals("A slide must complete exactly once", 1, ends.get())
                android.util.Log.i("AXO_LECTURE_TEST", "$language firstStartMs=${firstStartMs.get()} parts=${starts.get()}")
                assertTrue("$language startup took ${firstStartMs.get()}ms", firstStartMs.get() in 0..1500)
            } finally { speaker.close() }
        }
    }

    @Test fun replacingALectureDropsAllRemainingParts() {
        val began = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val ended = Collections.synchronizedList(mutableListOf<String>())
        val replacementOk = AtomicBoolean(false)
        val speaker = OfflineSpeechPlayer(context,
            onStarted = { id, _ -> if (id == "old") began.countDown() },
            onEnded = { id, ok ->
                ended.add(id)
                if (id == "new") { replacementOk.set(ok); finished.countDown() }
            }, role = OfflineVoiceRole.LECTURE)
        try {
            speaker.speak("Meet a garden. Follow the boundary around the outside. Look at the space inside. ".repeat(4), "old", "en")
            assertTrue(began.await(30, TimeUnit.SECONDS))
            speaker.speak("Now look inside.", "new", "en", 2f)
            assertTrue(finished.await(30, TimeUnit.SECONDS))
            assertTrue(replacementOk.get())
            assertEquals(listOf("new"), ended.toList())
        } finally { speaker.close() }
    }

    @Test fun cachedNarrationDoesNotWaitForVoiceInitialization() {
        val text = "Trace the outside edge of the garden."
        val prepared = CountDownLatch(1)
        val first = OfflineSpeechPlayer(context, onEnded = { _, ok -> if (ok) prepared.countDown() },
            role = OfflineVoiceRole.LECTURE)
        first.speak(text, "prepare", "en", 2f)
        assertTrue(prepared.await(30, TimeUnit.SECONDS))
        first.close()
        val started = CountDownLatch(1)
        val elapsed = AtomicLong()
        val beganAt = SystemClock.elapsedRealtime()
        val second = OfflineSpeechPlayer(context, onStarted = { _, _ ->
            elapsed.set(SystemClock.elapsedRealtime() - beganAt); started.countDown()
        }, role = OfflineVoiceRole.LECTURE)
        try {
            second.warmUp("en")
            second.speak(text, "cached", "en", 2f)
            assertTrue(started.await(5, TimeUnit.SECONDS))
            assertTrue("Cached audio waited ${elapsed.get()}ms for the neural worker", elapsed.get() < 500)
        } finally { second.close() }
    }

    @Test fun spokenMathRangesFollowTheOriginalCaptionAcrossPartsInBothLanguages() {
        for ((language, text) in listOf(
            "en" to "Add 1/2 + 1/4 = 3/4. Now look at the garden and follow its outside boundary. Its area is 12 cm².",
            "sq" to "Mblidh 1/2 + 1/4 = 3/4. Tani shiko kopshtin dhe ndiq me kujdes kufirin e tij. Sipërfaqja është 12 cm²."
        )) {
            val done = CountDownLatch(1)
            val ok = AtomicBoolean(false)
            val ranges = Collections.synchronizedList(mutableListOf<Pair<Int, Int>>())
            val parts = AtomicInteger()
            val speaker = OfflineSpeechPlayer(context,
                onStarted = { _, _ -> parts.incrementAndGet() },
                onRange = { _, start, end -> ranges.add(start to end) },
                onEnded = { _, success -> ok.set(success); done.countDown() },
                role = OfflineVoiceRole.LECTURE)
            try {
                assertTrue(speaker.speak(text, "math-$language", language, 2f))
                assertTrue(done.await(60, TimeUnit.SECONDS))
                assertTrue(ok.get())
                assertTrue(parts.get() > 1)
                assertTrue(ranges.isNotEmpty())
                assertTrue(ranges.all { (start, end) -> start >= 0 && end > start && end <= text.length })
                assertTrue("Offsets reset at an audio part boundary", ranges.zipWithNext().all { (a, b) -> b.first >= a.first })
                assertEquals(text.length, ranges.last().second)
                val half = text.indexOf("1/2")
                assertTrue("Spoken fraction words must highlight the written fraction", ranges.count { it == (half to half + 3) } >= 2)
            } finally { speaker.close() }
        }
    }
}
