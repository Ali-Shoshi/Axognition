package com.example.axognition.ui

import org.junit.Assert.assertEquals
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
}
