package com.example.axognition.ui

import org.junit.Assert.*
import org.junit.Test

class StreamingSpeechChunkerTest {
    @Test fun firstPhraseStartsBefore96CharactersWithoutWaitingForASentence() {
        val chunks = mutableListOf<Pair<String, Int>>()
        val chunker = StreamingSpeechChunker { text, offset -> chunks.add(text to offset) }
        val answer = "When you add fractions with different denominators you first find a common denominator and then add the numerators."
        chunker.accept(answer.take(48))
        assertEquals(1, chunks.size)
        assertTrue(chunks.single().first.length in 24..48)
        assertEquals(0, chunks.single().second)
        chunker.accept(answer, final = true)
        assertEquals(answer, chunks.joinToString(" ") { it.first })
        for ((text, offset) in chunks) assertEquals(text, answer.substring(offset, offset + text.length))
    }

    @Test fun shortSentencesAndOneWordRepliesAreNotHeldUntilALargerBuffer() {
        val chunks = mutableListOf<String>()
        val chunker = StreamingSpeechChunker { text, _ -> chunks.add(text) }
        chunker.accept("Yes. Here is why")
        assertEquals(listOf("Yes."), chunks)
        chunker.accept("Yes. Here is why", final = true)
        assertEquals(listOf("Yes.", "Here is why"), chunks)
    }

    @Test fun everyIncrementPreservesDecimalsAlbanianAndOriginalOffsetsExactlyOnce() {
        for (answer in listOf(
            "The result is 2.5 meters. Next calculate 1/2 + 1/4 = 3/4 and check the result carefully.",
            "Përgjigjja është 2.5 metra. Tani llogarit 1/2 + 1/4 = 3/4 dhe kontrollo përgjigjen me kujdes."
        )) {
            val chunks = mutableListOf<Pair<String, Int>>()
            val chunker = StreamingSpeechChunker { text, offset -> chunks.add(text to offset) }
            for (length in 1..answer.length) chunker.accept(answer.take(length))
            chunker.accept(answer, final = true)
            assertEquals(answer, chunks.joinToString(" ") { it.first })
            assertTrue(chunks.any { it.first.contains("2.5") })
            for ((text, offset) in chunks) assertEquals(text, answer.substring(offset, offset + text.length))
        }
    }

    @Test fun aCompletedAnswerArrivingInOneBatchStillHasAShortOpeningAndNoLostText() {
        val chunks = mutableListOf<String>()
        val chunker = StreamingSpeechChunker { text, _ -> chunks.add(text) }
        val answer = "A triangle is a polygon with three sides and three corners which connect to create a closed shape without curved edges."
        chunker.accept(answer, final = true)
        assertTrue(chunks.first().length <= 48)
        assertEquals(answer, chunks.joinToString(" "))
    }

    @Test fun unfinishedWordsAreNotSpokenAndShortFinalTailsAreEmitted() {
        val chunks = mutableListOf<String>()
        val chunker = StreamingSpeechChunker { text, _ -> chunks.add(text) }
        chunker.accept("A".repeat(60))
        assertTrue(chunks.isEmpty())
        chunker.accept("A".repeat(60) + " done", final = true)
        assertEquals("A".repeat(60) + " done", chunks.joinToString(" "))
    }
}
