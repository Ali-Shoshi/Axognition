package com.example.axognition.ui

import org.junit.Assert.*
import org.junit.Test

class LectureSpeechPartsTest {
    @Test fun partsRetainEveryWordAndDoNotSplitDecimalsOrFractions() {
        val text = "A side measures 1.5 metres. Compare 1/2 + 1/4 = 3/4, then trace the whole boundary. " +
            "This is a longer sentence that describes how the outside boundary differs from all the space on the inside of the figure."
        val spoken = naturalizeSpeechForTts(text, "en")
        val parts = lectureSpeechParts(spoken)
        assertEquals(spoken, parts.joinToString(" "))
        assertTrue(parts.size > 2)
        assertTrue(parts.all { it.length <= 100 })
        assertTrue(parts.first().contains("1.5"))
    }

    @Test fun albanianPartsRetainAllNormalizedMathText() {
        val spoken = naturalizeSpeechForTts("1/2 + 1/4 = 3/4. ".repeat(12), "sq")
        assertEquals(spoken, lectureSpeechParts(spoken).joinToString(" "))
    }
}
