package com.example.axognition.data

import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.axognition.ui.ChatMessage
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class AssistantConversation(
    val id: String = UUID.randomUUID().toString(),
    val messages: List<ChatMessage> = emptyList(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    val title: String? get() = messages.firstOrNull { it.fromStudent }?.text
        ?.replace(Regex("\\s+"), " ")?.trim()?.take(60)
}

/** Uses the existing child-specific preferences; the former single chat is migrated once. */
class AssistantConversationStore(private val preferences: SharedPreferences) {
    val conversations = mutableStateListOf<AssistantConversation>()
    var selectedId by mutableStateOf("")
        private set
    val selected: AssistantConversation get() = conversations.first { it.id == selectedId }

    init {
        val saved = preferences.getString("conversations", null)
        if (saved != null) {
            runCatching {
                val list = JSONArray(saved)
                for (index in 0 until list.length()) {
                    val item = list.getJSONObject(index)
                    val id = item.getString("id")
                    if (conversations.none { it.id == id }) {
                        conversations.add(AssistantConversation(id, readMessages(item.getJSONArray("messages")), item.getLong("updatedAt")))
                    }
                }
            }
        } else {
            val legacy = runCatching { readMessages(JSONArray(preferences.getString("messages", "[]"))) }.getOrDefault(emptyList())
            conversations.add(AssistantConversation(messages = legacy))
        }
        if (conversations.isEmpty()) conversations.add(AssistantConversation())
        selectedId = preferences.getString("selectedConversation", null)
            ?.takeIf { id -> conversations.any { it.id == id } } ?: conversations.first().id
        save()
    }

    fun select(id: String) {
        if (conversations.none { it.id == id }) return
        selectedId = id
        save()
    }

    fun create() {
        val conversation = AssistantConversation()
        conversations.add(0, conversation)
        selectedId = conversation.id
        save()
    }

    // The ID is captured before sending so a late reply always reaches its original chat.
    fun append(id: String, message: ChatMessage) {
        val index = conversations.indexOfFirst { it.id == id }
        if (index < 0) return
        conversations[index] = conversations[index].copy(
            messages = conversations[index].messages + message,
            updatedAt = System.currentTimeMillis()
        )
        save()
    }

    private fun save() {
        val list = JSONArray()
        conversations.forEach { conversation ->
            val messages = JSONArray()
            conversation.messages.forEach { messages.put(JSONObject().put("text", it.text).put("fromStudent", it.fromStudent)) }
            list.put(JSONObject().put("id", conversation.id).put("messages", messages).put("updatedAt", conversation.updatedAt))
        }
        preferences.edit().putString("conversations", list.toString())
            .putString("selectedConversation", selectedId).apply()
    }

    private fun readMessages(list: JSONArray): List<ChatMessage> = (0 until list.length()).map { index ->
        val message = list.getJSONObject(index)
        ChatMessage(message.getString("text"), message.getBoolean("fromStudent"))
    }
}
