package com.example.axognition.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeechTextTest {
    @Test fun englishCalculationsSpeakFractionsAndOperators() {
        assertEquals("one half plus three quarters minus one third divided by two fifths equals 4",
            naturalizeSpeechForTts("1/2 + 3/4 - 1/3 ÷ 2/5 = 4", "en"))
        assertEquals("three halves times one quarter", naturalizeSpeechForTts("3/2 × 1/4", "en"))
    }

    @Test fun albanianCalculationsKeepAccentsAndUseAlbanianFractionNames() {
        assertEquals("një e dyta plus tre të katërtat minus një e treta pjesëtuar me dy të pestat është e barabartë me 4",
            naturalizeSpeechForTts("1/2 + 3/4 − 1/3 ÷ 2/5 = 4", "sq"))
        assertEquals("Çdo pjesë është një e gjashta.", naturalizeSpeechForTts("Çdo pjesë është 1/6.", "sq"))
    }

    @Test fun unitsAndLargerFractionsRemainReadable() {
        assertEquals("one half centimetres, 12 square centimetres, five over 24",
            naturalizeSpeechForTts("1/2 cm, 12 cm², 5/24", "en"))
        assertEquals("dy të katërtat centimetra, 12 metra katrorë",
            naturalizeSpeechForTts("2/4 cm, 12 m²", "sq"))
    }

    @Test fun ordinaryHyphenatedWordsAndSpokenExplanationStayIntact() {
        val text = "A two-step calculation. Take one half, then add a quarter."
        assertEquals(text, naturalizeSpeechForTts(text, "en"))
        assertEquals("", naturalizeSpeechForTts("", "sq"))
    }
}
