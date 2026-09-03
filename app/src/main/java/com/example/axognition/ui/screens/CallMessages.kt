package com.example.axognition.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// --- Dummy Data Models ---
data class Contact(
    val id: String,
    val name: String,
    val avatarInitial: String,
    val lastMessage: String,
    val time: String,
    val isOnline: Boolean
)

data class ChatMessage(
    val id: String,
    val senderId: String, // "me" or contact id
    val text: String,
    val time: String
)

val initialContacts = mutableListOf(
    Contact("1", "Sarah Jenkins", "S", "See you in the study group tomorrow!", "10:42 AM", true),
    Contact("2", "Alex Rivera", "A", "Did you check the new course test?", "Yesterday", false),
    Contact("3", "Dr. Emily Vance", "E", "Your assignment submission looks great.", "Tuesday", true),
    Contact("4", "Marcus Chen", "M", "Let's review physics chapters later.", "Aug 24", false)
)

val dummyMessages = mutableMapOf(
    "1" to mutableListOf(
        ChatMessage("m1", "1", "Hey! Are we still on for studying?", "10:30 AM"),
        ChatMessage("m2", "me", "Yes, absolutely. What time works best?", "10:35 AM"),
        ChatMessage("m3", "1", "See you in the study group tomorrow!", "10:42 AM")
    ),
    "2" to mutableListOf(
        ChatMessage("m4", "2", "Did you check the new course test?", "Yesterday")
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallMessagesScreen(onBack: () -> Unit) {
    var selectedContact by remember { mutableStateOf<Contact?>(null) }
    var currentTab by remember { mutableStateOf(0) } // 0: Chats, 1: Contacts
    var searchQuery by remember { mutableStateOf("") }
    var showAddContactDialog by remember { mutableStateOf(false) }

    // Mutable state lists to support adding contacts dynamically
    val contactsList = remember { mutableStateListOf<Contact>().apply { addAll(initialContacts) } }

    val filteredContacts = contactsList.filter {
        it.name.contains(searchQuery, ignoreCase = true)
    }

    if (selectedContact != null) {
        ChatDetailScreen(
            contact = selectedContact!!,
            onBack = { selectedContact = null }
        )
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // Header with Add Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Messages & Calls",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { showAddContactDialog = true }) {
                        Icon(Icons.Default.PersonAdd, contentDescription = "Add Contact", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search messages or contacts...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear Search")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Navigation Tabs (Chats vs All Contacts)
            TabRow(selectedTabIndex = currentTab) {
                Tab(
                    selected = currentTab == 0,
                    onClick = { currentTab = 0 },
                    text = { Text("Recent Chats") }
                )
                Tab(
                    selected = currentTab == 1,
                    onClick = { currentTab = 1 },
                    text = { Text("All Contacts") }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Content based on selected tab
            when (currentTab) {
                0 -> {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredContacts) { contact ->
                            ChatListItem(contact = contact) {
                                selectedContact = contact
                            }
                        }
                    }
                }
                1 -> {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredContacts) { contact ->
                            ContactListItem(contact = contact, onCallClick = {
                                selectedContact = contact
                            }, onChatClick = {
                                selectedContact = contact
                            })
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Add Contact Dialog
        if (showAddContactDialog) {
            var newName by remember { mutableStateOf("") }
            var newStatus by remember { mutableStateOf("") }

            AlertDialog(
                onDismissRequest = { showAddContactDialog = false },
                title = { Text("Add New Contact") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = newName,
                            onValueChange = { newName = it },
                            placeholder = { Text("Contact Name") },
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = newStatus,
                            onValueChange = { newStatus = it },
                            placeholder = { Text("Initial message or status") },
                            singleLine = true
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (newName.isNotBlank()) {
                                val newId = (contactsList.size + 1).toString()
                                val initial = newName.take(1).uppercase()
                                val newContact = Contact(
                                    id = newId,
                                    name = newName,
                                    avatarInitial = initial,
                                    lastMessage = if (newStatus.isNotBlank()) newStatus else "Say hello!",
                                    time = "Just now",
                                    isOnline = true
                                )
                                contactsList.add(newContact)
                                dummyMessages[newId] = mutableListOf(
                                    ChatMessage("init", newId, newContact.lastMessage, "Just now")
                                )
                                showAddContactDialog = false
                            }
                        }
                    ) {
                        Text("Add")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddContactDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
fun ChatListItem(contact: Contact, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = contact.avatarInitial,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                if (contact.isOnline) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                            .align(Alignment.BottomEnd)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = contact.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(text = contact.time, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = contact.lastMessage,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun ContactListItem(contact: Contact, onCallClick: () -> Unit, onChatClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onChatClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.size(44.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(text = contact.avatarInitial, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = contact.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(text = if (contact.isOnline) "Active now" else "Offline", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onCallClick) {
            Icon(Icons.Default.Call, contentDescription = "Call", tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun ChatDetailScreen(contact: Contact, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var messageInput by remember { mutableStateOf("") }
    val chatMessages = remember {
        dummyMessages.getOrPut(contact.id) {
            mutableListOf(ChatMessage("1", contact.id, contact.lastMessage, "Just now"))
        }
    }
    var isCalling by remember { mutableStateOf(false) }

    if (isCalling) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(shape = CircleShape, modifier = Modifier.size(96.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(text = contact.avatarInitial, fontSize = 36.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
                Text(text = "Calling ${contact.name}...", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(48.dp))
                FloatingActionButton(
                    onClick = { isCalling = false },
                    containerColor = MaterialTheme.colorScheme.error
                ) {
                    Icon(Icons.Default.CallEnd, contentDescription = "End Call", tint = MaterialTheme.colorScheme.onError)
                }
            }
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            Surface(
                shadowElevation = 4.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = contact.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(text = if (contact.isOnline) "Online" else "Offline", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { isCalling = true }) {
                        Icon(Icons.Default.Call, contentDescription = "Voice Call", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = { isCalling = true }) {
                        Icon(Icons.Default.Videocam, contentDescription = "Video Call", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(chatMessages) { msg ->
                    val isMe = msg.senderId == "me"
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isMe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.widthIn(max = 260.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = msg.text,
                                    color = if (isMe) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = msg.time,
                                    fontSize = 10.sp,
                                    color = if (isMe) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    modifier = Modifier.align(Alignment.End)
                                )
                            }
                        }
                    }
                }
            }

            Surface(
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = messageInput,
                        onValueChange = { messageInput = it },
                        placeholder = { Text("Type a message...") },
                        modifier = Modifier.weight(1f),
                        maxLines = 3,
                        shape = RoundedCornerShape(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            if (messageInput.isNotBlank()) {
                                chatMessages.add(ChatMessage(id = System.currentTimeMillis().toString(), senderId = "me", text = messageInput, time = "Just now"))
                                messageInput = ""
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                    ) {
                        Icon(Icons.Default.Send, contentDescription = "Send", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }
        }
    }
}
