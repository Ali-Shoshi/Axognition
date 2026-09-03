package com.example.axognition.ui.screens

import androidx.compose.foundation.background
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// --- Data Models ---
enum class BookCategory(val title: String, val icon: ImageVector) {
    LECTURES("Lectures", Icons.Default.School),
    COURSES("Courses", Icons.Default.Code),
    REGULAR("Assigned Reading", Icons.Default.MenuBook),
    GENERAL("General Books", Icons.Default.Public)
}

data class Book(
    val id: String,
    val title: String,
    val author: String,
    val category: BookCategory,
    val fileSize: String,
    var isDownloaded: Boolean = false
)

@Composable
fun BooksScreen(onBack: () -> Unit) {
    // Simulated global book catalog spanning multiple categories
    val bookList = remember {
        mutableStateListOf(
            Book("1", "Software Engineering Lecture 1-5", "Prof. FIEK", BookCategory.LECTURES, "12.4 MB", true),
            Book("2", "Algorithms & Data Structures Slides", "FIEK Staff", BookCategory.LECTURES, "8.1 MB", false),
            Book("3", "Clean Code: A Handbook of Agile Software Craftsmanship", "Robert C. Martin", BookCategory.REGULAR, "14.2 MB", true),
            Book("4", "The Pragmatic Programmer", "Andrew Hunt & David Thomas", BookCategory.REGULAR, "9.8 MB", false),
            Book("5", "Jetpack Compose for Android Development", "Google Developers", BookCategory.COURSES, "25.0 MB", true),
            Book("6", "Advanced React & Node.js Fullstack Blueprint", "Dev Academy", BookCategory.COURSES, "19.3 MB", false),
            Book("7", "Atomic Habits", "James Clear", BookCategory.GENERAL, "5.5 MB", false),
            Book("8", "Sapiens: A Brief History of Humankind", "Yuval Noah Harari", BookCategory.GENERAL, "11.0 MB", true)
        )
    }

    // Navigation state: null means showing category hub, non-null means inside that category view
    var selectedCategory by remember { mutableStateOf<BookCategory?>(null) }
    // View tab state: 0 = Server Catalog, 1 = Downloaded Library
    var selectedTab by remember { mutableIntStateOf(0) }

    // Search query state
    var searchQuery by remember { mutableStateOf("") }
    BackHandler {
        if (selectedCategory != null) {
            selectedCategory = null
            searchQuery = ""
        } else onBack()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // --- COMPACT CUSTOM HEADER ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = selectedCategory?.title ?: "Digital Library Hub",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (selectedCategory == null) {
                // --- CATEGORY HUB 2x2 GRID VIEW ---
                Text(
                    text = "Select a Category to Explore",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 2x2 Grid Layout container filling the screen weight
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val categories = BookCategory.entries.chunked(2)
                    for (rowCategories in categories) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            for (category in rowCategories) {
                                val count = bookList.count { it.category == category }
                                val downloadedCount = bookList.count { it.category == category && it.isDownloaded }

                                Card(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .clickable { selectedCategory = category },
                                    shape = RoundedCornerShape(16.dp),
                                    elevation = CardDefaults.cardElevation(3.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(12.dp),
                                        verticalArrangement = Arrangement.Center,
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        // Larger Icon Container & Larger Icon Size
                                        Box(
                                            modifier = Modifier
                                                .size(80.dp)
                                                .clip(RoundedCornerShape(20.dp))
                                                .background(MaterialTheme.colorScheme.primaryContainer),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                category.icon,
                                                contentDescription = null,
                                                modifier = Modifier.size(44.dp),
                                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(12.dp))

                                        Text(
                                            text = category.title,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 17.sp,
                                            textAlign = TextAlign.Center,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )

                                        Spacer(modifier = Modifier.height(4.dp))

                                        Text(
                                            text = "$count books • $downloadedCount down.",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.secondary,
                                            textAlign = TextAlign.Center,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                            if (rowCategories.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }

            } else {
                // --- BOOK LIST VIEW FOR SELECTED CATEGORY ---

                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search in ${selectedCategory!!.title}...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Segmented Tabs (Server vs Downloaded)
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SegmentedButton(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) {
                        Text("Server Catalog")
                    }
                    SegmentedButton(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) {
                        val catDownloadedCount = bookList.count { it.category == selectedCategory && it.isDownloaded }
                        Text("Downloaded ($catDownloadedCount)")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Filtering logic (Category + Tab + Search Query)
                val displayedBooks = bookList.filter { book ->
                    val matchesCategory = book.category == selectedCategory
                    val matchesTab = if (selectedTab == 0) true else book.isDownloaded
                    val matchesSearch = searchQuery.isBlank() ||
                            book.title.contains(searchQuery, ignoreCase = true) ||
                            book.author.contains(searchQuery, ignoreCase = true)
                    matchesCategory && matchesTab && matchesSearch
                }

                if (displayedBooks.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                selectedCategory!!.icon,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.outline
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (searchQuery.isNotBlank()) "No matching books found."
                                else if (selectedTab == 0) "No books available in this category."
                                else "No downloaded books in this category.",
                                color = MaterialTheme.colorScheme.outline,
                                fontSize = 15.sp
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(displayedBooks, key = { it.id }) { book ->
                            BookCard(
                                book = book,
                                onDownloadToggle = {
                                    val index = bookList.indexOfFirst { it.id == book.id }
                                    if (index != -1) {
                                        bookList[index] = bookList[index].copy(isDownloaded = !book.isDownloaded)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BookCard(book: Book, onDownloadToggle: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.MenuBook,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = book.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = book.author,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = book.fileSize,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(onClick = onDownloadToggle) {
                if (book.isDownloaded) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete Book",
                        tint = MaterialTheme.colorScheme.error
                    )
                } else {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = "Download Book",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
