package com.example

import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.*

class AssistantReplyPolicyTest {
    @Test fun `brief replies retain both requested results instead of truncating after the first sentence`() {
        val text = "The perimeter is 12. The area is 6. More explanation follows."
        assertEquals("The perimeter is 12. The area is 6.", firstAssistantSentences(text))
        assertNull(firstAssistantSentences("The perimeter is 12. The area is 6."))
        assertEquals("The perimeter is 12. The area is 6.", firstAssistantSentences("The perimeter is 12. The area is 6.", final = true))
        assertEquals("The area is 3.14.", firstAssistantSentences("The area is 3.14.", final = true))
        assertEquals("56.", firstAssistantSentences("56.", final = true))
    }
    @Test fun `exact recall uses the latest student assertion even after assistant memory denials`() {
        val history = listOf(AssistantHistoryMessage("user", "Remember my favorite color is blue"),
            AssistantHistoryMessage("assistant", "I cannot remember your personal information."),
            AssistantHistoryMessage("user", "My name is Mira."),
            AssistantHistoryMessage("user", "Actually, my favourite colour is turquoise now."),
            AssistantHistoryMessage("assistant", "Your favorite color is red."))
        for (question in listOf("What is my favorite color?", "Do you remember my favorite color?", "Tell me what is my favourite colour", "What's my favorite color?")) {
            assertEquals("Your favourite colour is turquoise now.", assistantRecalledFact(question, history))
        }
        assertEquals("Your name is Mira.", assistantRecalledFact("What is my name?", history))
        assertNull(assistantRecalledFact("What is my favorite food?", history))
        assertNull(assistantRecalledFact("What is my favorite color?", emptyList()))
        assertNull(assistantRecalledFact("What is my favorite color?", listOf(
            AssistantHistoryMessage("user", "If my favorite color is blue, what does that mean?"))))
    }

    @Test fun `recall reminders use original student facts and corrections despite incorrect earlier replies`() {
        val history = listOf(
            AssistantHistoryMessage("user", "Remember my favorite color is blue"),
            AssistantHistoryMessage("assistant", "I cannot remember your personal information."),
            AssistantHistoryMessage("user", "My favorite food is pizza"),
            AssistantHistoryMessage("assistant", "Let me know if you need more help."),
            AssistantHistoryMessage("user", "Actually my favorite color is green now"),
            AssistantHistoryMessage("assistant", "I cannot see you."),
            AssistantHistoryMessage("user", "What is my favorite color?")
        )
        assertEquals(listOf(history[0].content, history[4].content), assistantRecallContext("What is my favorite color?", history))
        for (stream in listOf(false, true)) {
            val messages = LmStudioClient.requestBody("What is my favorite color?", history, "en", stream)["messages"]!!.jsonArray
            assertEquals(history.map { it.content }, messages.drop(1).dropLast(1).map { it.jsonObject["content"]!!.jsonPrimitive.content })
            val reminder = messages.last().jsonObject["content"]!!.jsonPrimitive.content
            assertTrue(reminder.indexOf(history[0].content) < reminder.indexOf(history[4].content))
            assertFalse(reminder.contains("I cannot"))
            assertFalse(reminder.contains("pizza"))
        }
        assertTrue(assistantRecallContext("What is my favorite color?", emptyList()).isEmpty())
    }

    @Test fun `facts cover names and lesson data and do not invent missing personal details`() {
        val history = listOf(AssistantHistoryMessage("user", "My name is Mira"),
            AssistantHistoryMessage("user", "My triangle has sides 3, 4 and 5"))
        assertEquals(listOf("My name is Mira"), assistantRecallContext("What is my name?", history))
        assertEquals(listOf(history[1].content), assistantRecallContext("What is the perimeter of my triangle?", history))
        assertTrue(assistantRecallContext("What is my favorite color?", history).isEmpty())
        assertTrue(assistantRecallContext("Why?", history).isEmpty())
    }

    @Test fun `brief streaming waits for a whole sentence and preserves decimals abbreviations and quotes`() {
        assertNull(firstAssistantSentence("The answer is 3."))
        assertNull(firstAssistantSentence("The answer is 3.14"))
        assertEquals("The answer is 3.14.", firstAssistantSentence("The answer is 3.14. More explanation follows."))
        assertEquals("Dr. Smith said \"blue.\"", firstAssistantSentence("Dr. Smith said \"blue.\" Then continued."))
        assertEquals("Blue is your favorite color.", firstAssistantSentence("Blue is your favorite color.", final = true))
        assertEquals("A shape with three sides", firstAssistantSentence("A shape with three sides", final = true))
        assertEquals("1. Add all three sides.", firstAssistantSentence("1. Add all three sides. 2. Keep going."))
    }
}
