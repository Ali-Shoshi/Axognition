package com.example.axognition.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

enum class BookCategory(val title: String, val icon: ImageVector) {
    LECTURES("Lectures", Icons.Default.School), COURSES("Courses", Icons.Default.Code),
    REGULAR("Assigned Reading", Icons.Default.MenuBook), GENERAL("General Books", Icons.Default.Public)
}
data class Book(val id: String, val title: String, val author: String, val category: BookCategory, val fileSize: String, var isDownloaded: Boolean = false)

@Composable
fun BooksScreen(onBack: () -> Unit) {
    val books = remember { mutableStateListOf(
        Book("1", "Software Engineering Lecture 1-5", "Prof. FIEK", BookCategory.LECTURES, "12.4 MB", true), Book("2", "Algorithms & Data Structures Slides", "FIEK Staff", BookCategory.LECTURES, "8.1 MB"),
        Book("3", "Clean Code: A Handbook of Agile Software Craftsmanship", "Robert C. Martin", BookCategory.REGULAR, "14.2 MB", true), Book("4", "The Pragmatic Programmer", "Andrew Hunt & David Thomas", BookCategory.REGULAR, "9.8 MB"),
        Book("5", "Jetpack Compose for Android Development", "Google Developers", BookCategory.COURSES, "25.0 MB", true), Book("6", "Advanced React & Node.js Fullstack Blueprint", "Dev Academy", BookCategory.COURSES, "19.3 MB"),
        Book("7", "Atomic Habits", "James Clear", BookCategory.GENERAL, "5.5 MB"), Book("8", "Sapiens: A Brief History of Humankind", "Yuval Noah Harari", BookCategory.GENERAL, "11.0 MB", true)
    ) }
    var category by remember { mutableStateOf<BookCategory?>(null) }
    var tab by remember { mutableIntStateOf(1) }
    var query by remember { mutableStateOf("") }
    var globalSearchOpen by remember { mutableStateOf(false) }
    val hubSearchInteraction = remember { MutableInteractionSource() }
    val hubSearchFocused by hubSearchInteraction.collectIsFocusedAsState()
    LaunchedEffect(hubSearchFocused) {
        if (hubSearchFocused && !globalSearchOpen) globalSearchOpen = true
    }
    BackHandler {
        when {
            globalSearchOpen -> { globalSearchOpen = false; query = "" }
            category != null -> { category = null; query = "" }
            else -> onBack()
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
                if (category == null) {
                LibraryHeader("My library", onBack, false)
                Text("A calm space for every kind of learning", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = "",
                    onValueChange = { value -> query = value; globalSearchOpen = true },
                    modifier = Modifier.fillMaxWidth(),
                    interactionSource = hubSearchInteraction,
                    placeholder = { Text("Search every book, author, or topic") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp)
                )
                Spacer(Modifier.height(22.dp)); Text("Browse by collection", fontWeight = FontWeight.SemiBold, fontSize = 16.sp); Spacer(Modifier.height(12.dp))
                Column(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    BookCategory.entries.chunked(2).forEach { row -> Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        row.forEach { item ->
                            CategoryCard(item, books.count { it.category == item }, books.count { it.category == item && it.isDownloaded }, Modifier.weight(1f).fillMaxHeight()) {
                                category = item; tab = 1; query = "" // The downloaded library is always the first view.
                            }
                        }
                    } }
                }
                } else {
                val selected = category!!
                LibraryHeader(selected.title, { category = null }, true); Spacer(Modifier.height(10.dp))
                OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), placeholder = { Text("Search ${selected.title.lowercase()}") }, singleLine = true, shape = RoundedCornerShape(18.dp), leadingIcon = { Icon(Icons.Default.Search, null) }, trailingIcon = { if (query.isNotEmpty()) IconButton({ query = "" }) { Icon(Icons.Default.Clear, "Clear search") } })
                Spacer(Modifier.height(12.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(tab == 1, { tab = 1 }, SegmentedButtonDefaults.itemShape(0, 2)) { Text("Downloaded (${books.count { it.category == selected && it.isDownloaded }})") }
                    SegmentedButton(tab == 0, { tab = 0 }, SegmentedButtonDefaults.itemShape(1, 2)) { Text("Catalog") }
                }
                Spacer(Modifier.height(16.dp))
                val displayed = books.filter { it.category == selected && (tab == 0 || it.isDownloaded) && (query.isBlank() || it.title.contains(query, true) || it.author.contains(query, true)) }
                val columns = if (LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) 5 else 3
                if (displayed.isEmpty()) EmptyLibrary(selected, tab, query) else LazyVerticalGrid(GridCells.Fixed(columns), Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                    items(displayed, key = { it.id }) { book -> BookCoverCard(book) { val index = books.indexOfFirst { it.id == book.id }; if (index >= 0) books[index] = books[index].copy(isDownloaded = !book.isDownloaded) } }
                }
                }
            }
            if (globalSearchOpen) {
                Dialog(
                    onDismissRequest = { globalSearchOpen = false; query = "" },
                    properties = DialogProperties(usePlatformDefaultWidth = false)
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(.96f).fillMaxHeight(.80f),
                        shape = RoundedCornerShape(28.dp),
                        tonalElevation = 8.dp,
                        shadowElevation = 12.dp
                    ) {
                        GlobalBookSearch(
                            books = books,
                            query = query,
                            onQueryChange = { query = it },
                            onBack = { globalSearchOpen = false; query = "" },
                            modifier = Modifier.fillMaxSize().padding(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GlobalBookSearch(
    books: MutableList<Book>,
    query: String,
    onQueryChange: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier
) {
    val searchFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { searchFocus.requestFocus() }
    val results = books.filter { book ->
        query.isNotBlank() && (book.title.contains(query, ignoreCase = true) || book.author.contains(query, ignoreCase = true) || book.category.title.contains(query, ignoreCase = true))
    }
    val columns = if (LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) 5 else 3
    Column(modifier.fillMaxWidth()) {
        LibraryHeader("Search books", onBack, true)
        Text("Find something from every collection", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth().focusRequester(searchFocus),
            placeholder = { Text("Title, author, or topic") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = { if (query.isNotEmpty()) IconButton({ onQueryChange("") }) { Icon(Icons.Default.Clear, "Clear search") } },
            singleLine = true,
            shape = RoundedCornerShape(18.dp)
        )
        Spacer(Modifier.height(14.dp))
        if (query.isBlank()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text("Start typing to search the complete library", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
        } else if (results.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text("No books match “$query”", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Text("${results.size} ${if (results.size == 1) "result" else "results"}", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Spacer(Modifier.height(10.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(results, key = { it.id }) { book ->
                    BookCoverCard(book) {
                        val index = books.indexOfFirst { it.id == book.id }
                        if (index >= 0) books[index] = books[index].copy(isDownloaded = !book.isDownloaded)
                    }
                }
            }
        }
    }
}

@Composable private fun LibraryHeader(title: String, onBack: () -> Unit, showBack: Boolean) = Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) { if (showBack) IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }; Text(title, fontSize = 25.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis) }

@Composable private fun CategoryCard(category: BookCategory, total: Int, downloaded: Int, modifier: Modifier, onClick: () -> Unit) {
    val accent = listOf(Color(0xFF625BFF), Color(0xFF00796B), Color(0xFFE0683D), Color(0xFF5B5EAA))[category.ordinal]
    Card(modifier.clickable(onClick = onClick), shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = accent)) { Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.SpaceBetween) {
        Box(Modifier.size(54.dp).clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = .18f)), contentAlignment = Alignment.Center) { Icon(category.icon, null, Modifier.size(30.dp), Color.White) }
        Column { Text(category.title, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis); Spacer(Modifier.height(5.dp)); Text("$downloaded downloaded • $total titles", fontSize = 12.sp, color = Color.White.copy(alpha = .82f), maxLines = 1, overflow = TextOverflow.Ellipsis) }
    } }
}

@Composable private fun BookCoverCard(book: Book, onDownloadToggle: () -> Unit) {
    val cover = listOf(Color(0xFF342E55), Color(0xFF165A6D), Color(0xFF863E36), Color(0xFF455A35))[book.category.ordinal]
    Column(Modifier.fillMaxWidth()) {
        Card(Modifier.fillMaxWidth().aspectRatio(.64f).shadow(5.dp, RoundedCornerShape(16.dp)), shape = RoundedCornerShape(16.dp)) { Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(cover, cover.copy(alpha = .72f)))).padding(12.dp)) {
            Icon(book.category.icon, null, Modifier.align(Alignment.TopEnd).size(32.dp), Color.White.copy(alpha = .25f)); Surface(Modifier.align(Alignment.TopStart), shape = RoundedCornerShape(20.dp), color = Color.Black.copy(alpha = .18f)) { Text(book.fileSize, Modifier.padding(horizontal = 7.dp, vertical = 3.dp), color = Color.White, fontSize = 10.sp) }
            Column(Modifier.align(Alignment.BottomStart)) { Text(book.title, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, lineHeight = 18.sp, maxLines = 4, overflow = TextOverflow.Ellipsis); Spacer(Modifier.height(6.dp)); Text(book.author, color = Color.White.copy(alpha = .82f), fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        } }
        Row(Modifier.fillMaxWidth().padding(top = 7.dp), verticalAlignment = Alignment.CenterVertically) { Text(if (book.isDownloaded) "Saved" else "Available", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, modifier = Modifier.weight(1f)); IconButton(onDownloadToggle, Modifier.size(30.dp)) { Icon(if (book.isDownloaded) Icons.Default.Delete else Icons.Default.Download, if (book.isDownloaded) "Remove download" else "Download", Modifier.size(19.dp), if (book.isDownloaded) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) } }
    }
}

@Composable private fun EmptyLibrary(category: BookCategory, tab: Int, query: String) = Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(category.icon, null, Modifier.size(58.dp), MaterialTheme.colorScheme.outline); Spacer(Modifier.height(10.dp)); Text(if (query.isNotBlank()) "No matching books found" else if (tab == 1) "No downloaded books yet" else "This catalog is empty", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center) } }
