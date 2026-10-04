package com.example.axognition.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.axognition.data.AssistantConversation
import java.text.DateFormat
import java.util.Date

/** A full-screen home for the same conversation and composer as the floating chat. */
@Composable
internal fun AssistantChatScreen(
    onBack: () -> Unit,
    conversations: List<AssistantConversation>,
    selectedConversationId: String,
    onSelectConversation: (String) -> Unit,
    onNewConversation: () -> Unit,
    content: @Composable () -> Unit
) {
    var choosingConversation by remember { mutableStateOf(false) }
    BackHandler(onBack = onBack)
    if (choosingConversation) {
        AssistantConversationPicker(
            conversations, selectedConversationId,
            onSelect = { id -> choosingConversation = false; onSelectConversation(id) },
            onNew = { choosingConversation = false; onNewConversation() },
            onDismiss = { choosingConversation = false }
        )
    }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Back to floating chat"))
                }
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                    Icon(Icons.Default.SmartToy, null, Modifier.padding(12.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Column(Modifier.weight(1f)) {
                    Text(tr("Learning assistant"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        conversations.firstOrNull { it.id == selectedConversationId }?.title ?: tr("New conversation"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = { choosingConversation = true }) {
                    Icon(Icons.Default.ChatBubbleOutline, tr("Choose conversation"))
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Box(Modifier.fillMaxWidth().weight(1f)) { content() }
        }
    }
}

@Composable
private fun AssistantConversationPicker(
    conversations: List<AssistantConversation>,
    selectedId: String,
    onSelect: (String) -> Unit,
    onNew: () -> Unit,
    onDismiss: () -> Unit
) {
    val dateFormat = remember(AppLanguage.code) { DateFormat.getDateInstance(DateFormat.MEDIUM, AppLanguage.locale) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Conversations")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalButton(onClick = onNew, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text(tr("New conversation"))
                }
                LazyColumn(Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(conversations.sortedByDescending { it.updatedAt }, key = { it.id }) { conversation ->
                        val selected = conversation.id == selectedId
                        Surface(
                            modifier = Modifier.fillMaxWidth().selectable(
                                selected = selected, role = Role.RadioButton, onClick = { onSelect(conversation.id) }
                            ),
                            shape = RoundedCornerShape(14.dp),
                            color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Column(Modifier.weight(1f)) {
                                    Text(conversation.title ?: tr("New conversation"), fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    conversation.messages.lastOrNull()?.let { message ->
                                        Text(message.text, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    Text(dateFormat.format(Date(conversation.updatedAt)), style = MaterialTheme.typography.labelSmall)
                                }
                                if (selected) Icon(Icons.Default.Check, tr("Selected conversation"))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("Close")) } }
    )
}

/** Keep sending, draft and speech state in the caller while switching presentations. */
@Composable
internal fun AssistantChatWindow(
    fullScreen: Boolean,
    onBack: () -> Unit,
    modifier: Modifier,
    conversations: List<AssistantConversation>,
    selectedConversationId: String,
    onSelectConversation: (String) -> Unit,
    onNewConversation: () -> Unit,
    content: @Composable () -> Unit
) {
    if (fullScreen) {
        AssistantChatScreen(onBack, conversations, selectedConversationId, onSelectConversation, onNewConversation, content)
    } else {
        Card(
            modifier = modifier,
            shape = RoundedCornerShape(28.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
        ) { content() }
    }
}
