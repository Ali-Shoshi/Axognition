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
        assertEquals("sq", body.getString("language"))
    }
}
