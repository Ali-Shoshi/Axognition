package com.example

import com.example.axognition.shared.AssistantLanguage
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.*

class AssistantLanguageTest {
    @Test fun `actual English replies are rejected even with Albanian input and formulas stay neutral`() {
        for (reply in listOf("The image shows a triangle with base a = 3 and height h = 4.",
            "The area of a triangle is calculated by multiplying its base by half its height.",
            "Multiply base by height and divide by two.")) {
            assertTrue(assistantReplyNeedsLanguageRepair(reply, "sq"), reply)
        }
        assertFalse(assistantReplyNeedsLanguageRepair("A = b × h / 2.", "sq"))
        assertFalse(assistantReplyNeedsLanguageRepair("56.", "sq"))
        assertFalse(assistantReplyNeedsLanguageRepair("Sipërfaqja është baza shumëzuar me lartësinë, pjesëtuar me dy.", "sq"))
        assertTrue(assistantReplyNeedsLanguageRepair("Sipërfaqja është baza shumëzuar me lartësinë, pjesëtuar me dy.", "en"))
    }
    @Test fun `Albanian questions override English interface and earlier wrong English replies`() {
        val history = listOf(AssistantHistoryMessage("user", "Hello"),
            AssistantHistoryMessage("assistant", "Please ask your question in English."))
        for (question in listOf("Çfarë është një thyesë?", "Cfare eshte nje thyese?", "Si zgjidhet ky problem?", "Cila eshte ngjyra ime e preferuar?")) {
            assertEquals("sq", AssistantLanguage.replyLanguage(question, history.filter { it.role == "user" }.map { it.content }, "en"))
            for (stream in listOf(false, true)) {
                val system = LmStudioClient.requestBody(question, history, "en", stream)["messages"]!!
                    .jsonArray.first().jsonObject["content"]!!.jsonPrimitive.content
                assertContains(system, "current reply language is Albanian (Shqip)")
            }
        }
    }

    @Test fun `English questions override Albanian interface and explicit requests override question language`() {
        assertEquals("en", AssistantLanguage.replyLanguage("What is a fraction?", fallback = "sq"))
        assertEquals("sq", AssistantLanguage.replyLanguage("What is a fraction? Answer in Albanian."))
        assertEquals("en", AssistantLanguage.replyLanguage("Çfarë është një thyesë? Përgjigju anglisht."))
        assertEquals("sq", AssistantLanguage.replyLanguage("Please respond in English. Actually answer in Albanian."))
        assertEquals("en", AssistantLanguage.replyLanguage("What does the Albanian word 'thyesë' mean?"))
    }

    @Test fun `ambiguous followups inherit latest student language with UI as final fallback`() {
        assertEquals("sq", AssistantLanguage.replyLanguage("2 + 2?", listOf("Hello", "Çfarë është një thyesë?")))
        assertEquals("en", AssistantLanguage.replyLanguage("7?", listOf("Çfarë është një thyesë?", "What is a fraction?"), "sq"))
        assertEquals("sq", AssistantLanguage.replyLanguage("2 + 2?", fallback = "sq"))
        assertEquals("en", AssistantLanguage.replyLanguage("2 + 2?"))
    }
}
