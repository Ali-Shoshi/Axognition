package com.example.axognition

import com.example.axognition.data.AssistantApi
import org.junit.Assert.*
import org.junit.Test

class AssistantConversationRequestTest {
    @Test fun fullHistoryIncludesTheOldestFactsAndPreservesEveryMessageInOrder() {
        val history = (1..100).flatMap { listOf(
            AssistantApi.HistoryMessage("Question $it: ë ç 1/2", true),
            AssistantApi.HistoryMessage("Answer $it", false)
        ) }
        val body = AssistantApi.requestBody("What did I ask first?", history, "sq")
        val sent = body.getJSONArray("history")
        assertEquals(200, sent.length())
        history.forEachIndexed { index, message ->
            assertEquals(message.text, sent.getJSONObject(index).getString("content"))
            assertEquals(if (message.fromStudent) "user" else "assistant", sent.getJSONObject(index).getString("role"))
        }
        assertEquals("What did I ask first?", body.getString("message"))
        assertEquals("en", body.getString("language"))
    }

    @Test fun albanianQuestionsOverrideEnglishInterfaceAndOldAssistantReplies() {
        val history = listOf(AssistantApi.HistoryMessage("Çfarë është një thyesë?", true),
            AssistantApi.HistoryMessage("A fraction represents part of a whole.", false))
        assertEquals("sq", AssistantApi.requestBody("Cfare eshte nje thyese?", history, "en").getString("language"))
        assertEquals("sq", AssistantApi.requestBody("2+2?", history, "en").getString("language"))
        assertEquals("en", AssistantApi.requestBody("What is a fraction?", history, "sq").getString("language"))
        assertEquals("en", AssistantApi.requestBody("Çfarë është një thyesë? Përgjigju anglisht.", history, "sq").getString("language"))
    }
}
