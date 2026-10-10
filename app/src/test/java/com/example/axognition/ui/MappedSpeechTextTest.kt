package com.example.axognition.ui

import org.junit.Assert.*
import org.junit.Test

class MappedSpeechTextTest {
    @Test fun expandedFractionWordsMapBackToTheWrittenFraction() {
        val text = "Add 1/2 + 1/4 = 3/4. Then measure 12 cm²."
        for (language in listOf("en", "sq")) {
            val mapped = naturalizeSpeechWithRanges(text, language)
            val words = mapped.words()
            val halfStart = text.indexOf("1/2")
            assertTrue(words.count { it.start == halfStart && it.end == halfStart + 3 } >= 2)
            val plusStart = text.indexOf('+')
            assertTrue(words.any { text.substring(it.start, it.end).trim() == "+" && it.start <= plusStart })
            assertTrue(words.all { it.start >= 0 && it.end > it.start && it.end <= text.length })
            assertTrue(words.zipWithNext().all { (a, b) -> b.start >= a.start })
            assertEquals(text.length, words.last().end)
        }
    }

    @Test fun originalOffsetsContinueAcrossEveryAudioPart() {
        val text = "The garden has an outside boundary. ".repeat(4) + "Its area is 12 cm² and one part is 1/2 of the whole."
        val mapped = naturalizeSpeechWithRanges(text, "en")
        var offset = 0
        val words = lectureSpeechParts(mapped.text).flatMap { part ->
            val start = mapped.text.indexOf(part, offset)
            offset = start + part.length
            mapped.words(start, offset)
        }
        assertEquals(mapped.words(), words)
        assertEquals(0, words.first().start)
        assertEquals(text.length, words.last().end)
    }

    @Test fun unicodeAndWhitespaceKeepOriginalCaptionOffsets() {
        val text = "  Çdo pjesë është 1/6.\nTani shiko figurën.  "
        val mapped = naturalizeSpeechWithRanges(text, "sq")
        assertEquals(2, mapped.words().first().start)
        val last = mapped.words().last()
        assertEquals("figurën.", text.substring(last.start, last.end))
        assertEquals(text.lastIndexOf('.') + 1, mapped.words().last().end)
        assertTrue(mapped.words().any { text.substring(it.start, it.end) == "është" })
    }
}
