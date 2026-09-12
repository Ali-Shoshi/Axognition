package com.example.axognition.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslationCatalogTest {
    private val catalog = TranslationCatalog(mapOf(
        "Search books" to "Kërko libra",
        "Search {0}" to "Kërko {0}",
        "Welcome back, {0}" to "Mirë se u ktheve, {0}",
        "{0} of {1} completed" to "{0} nga {1} të përfunduara",
        "Chapter {0}: {1}" to "{1} · Kapitulli {0}"
    ))

    @Test fun exactLabelsWinOverTemplates() {
        assertEquals("Kërko libra", catalog.translate("Search books"))
    }
    @Test fun namesAndSpecialCharactersRemainIntact() {
        assertEquals("Mirë se u ktheve, Çlirim $ & \\ {0}", catalog.translate("Welcome back, Çlirim $ & \\ {0}"))
        assertEquals("My own text: English and Shqip", catalog.translate("My own text: English and Shqip"))
    }
    @Test fun countsAndReorderedPlaceholdersRemainCorrect() {
        assertEquals("3 nga 10 të përfunduara", catalog.translate("3 of 10 completed"))
        assertEquals("Rrethi · Kapitulli 7", catalog.translate("Chapter 7: Rrethi"))
    }

    @Test fun speechNotationIsMadePronounceableWithoutChangingCaptions() {
        val english = naturalizeSpeechForTts("P = 2 × (8 cm + 5 cm) = 26 m", "en")
        assertTrue(english.contains("equals"))
        assertTrue(english.contains("times"))
        assertTrue(english.contains("square") || english.contains("centimetres"))
        assertFalse(english.contains("×"))
        assertEquals("A është e barabartë me 40 metra katrorë", naturalizeSpeechForTts("A = 40 m²", "sq"))
    }

    @Test fun streamedSpeechChunksAreEmittedOnceWithResponseOffsets() {
        val chunks = mutableListOf<Pair<String, Int>>()
        val chunker = StreamingSpeechChunker { text, offset -> chunks += text to offset }

        chunker.accept("This is the first")
        assertTrue(chunks.isEmpty())
        chunker.accept("This is the first complete sentence. The second")
        chunker.accept("This is the first complete sentence. The second one is ready!", final = true)

        assertEquals(
            listOf(
                "This is the first complete sentence." to 0,
                "The second one is ready!" to 37
            ),
            chunks
        )
    }
}
