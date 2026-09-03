package com.example.axognition.ui.panels

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.axognition.ui.KioskTopBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsPanelScreen(
    darkModeEnabled: Boolean,
    onDarkModeChanged: (Boolean) -> Unit,
    onBack: () -> Unit
) {
    var notificationsEnabled by remember { mutableStateOf(true) }
    var dataSyncEnabled by remember { mutableStateOf(false) }
    var selectedLanguage by remember { mutableStateOf("English") }
    var expandedLanguageMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            KioskTopBar(title = "Settings", onBack = onBack)
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text("Preferences", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Push Notifications", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text("Receive reminders for doctor appointments", style = MaterialTheme.typography.bodySmall)
                            }
                            Switch(
                                checked = notificationsEnabled,
                                onCheckedChange = { notificationsEnabled = it }
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Divider()
                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Display", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text(if (darkModeEnabled) "Dark Mode is on" else "Light Mode is on", style = MaterialTheme.typography.bodySmall)
                            }
                            Switch(
                                checked = darkModeEnabled,
                                onCheckedChange = onDarkModeChanged
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Divider()
                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Cloud Data Sync", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text("Automatically back up health records", style = MaterialTheme.typography.bodySmall)
                            }
                            Switch(
                                checked = dataSyncEnabled,
                                onCheckedChange = { dataSyncEnabled = it }
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text("App Settings", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Language", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text("Select your preferred language", style = MaterialTheme.typography.bodySmall)
                            }
                            Box {
                                OutlinedButton(onClick = { expandedLanguageMenu = true }) {
                                    Text(selectedLanguage)
                                }
                                DropdownMenu(
                                    expanded = expandedLanguageMenu,
                                    onDismissRequest = { expandedLanguageMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("English") },
                                        onClick = {
                                            selectedLanguage = "English"
                                            expandedLanguageMenu = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Spanish") },
                                        onClick = {
                                            selectedLanguage = "Spanish"
                                            expandedLanguageMenu = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("French") },
                                        onClick = {
                                            selectedLanguage = "French"
                                            expandedLanguageMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text("About", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Axognition Health", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("Version 1.0.4", style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Secure health tracking panel designed to manage your doctor visits, allergies, and ongoing medical conditions.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
