package com.example.axognition.ui.screens

import com.example.axognition.ui.tr

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.axognition.R
import com.example.axognition.data.BookApi
import com.example.axognition.data.RemoteBook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL

private enum class BookCategory(val title: String, val icon: ImageVector) {
    LECTURES("Lectures", Icons.Default.School), COURSES("Courses", Icons.Default.Code),
    REGULAR("Assigned Reading", Icons.Default.MenuBook), GENERAL("General Books", Icons.Default.Public);

    companion object { fun fromServer(value: String) = entries.firstOrNull { it.name == value } ?: GENERAL }
}

private fun BookCategory.categoryImage() = when (this) {
    BookCategory.LECTURES -> R.drawable.book_category_lectures
    BookCategory.COURSES -> R.drawable.book_category_courses
    BookCategory.REGULAR -> R.drawable.book_category_assigned_reading
    BookCategory.GENERAL -> R.drawable.book_category_general
}

private sealed interface BooksState {
    data object Loading : BooksState
    data class Ready(val books: List<RemoteBook>) : BooksState
    data class Error(val message: String) : BooksState
}

@Composable
fun BooksScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<BooksState>(BooksState.Loading) }
    var category by remember { mutableStateOf<BookCategory?>(null) }
    var query by remember { mutableStateOf("") }
    var globalQuery by remember { mutableStateOf("") }
    var globalSearchOpen by remember { mutableStateOf(false) }
    var tab by remember { mutableIntStateOf(0) }
    var openedPdf by remember { mutableStateOf<File?>(null) }
    var openingBookId by remember { mutableStateOf<String?>(null) }
    var cachedBookIds by remember { mutableStateOf(emptySet<String>()) }

    fun cachedPdf(bookId: String) = File(File(context.cacheDir, "books"), "$bookId.pdf")

    suspend fun refresh() {
        state = BooksState.Loading
        state = runCatching { withContext(Dispatchers.IO) { BookApi.fetchBooks() } }
            .fold(
                { books ->
                    cachedBookIds = books.filter { cachedPdf(it.id).isFile }.map { it.id }.toSet()
                    BooksState.Ready(books)
                },
                { BooksState.Error(it.message ?: "Could not reach the server") }
            )
    }

    fun openBook(book: RemoteBook) {
        scope.launch {
            openingBookId = book.id
            runCatching {
                withContext(Dispatchers.IO) {
                    cachedPdf(book.id).takeIf { it.isFile } ?: BookApi.downloadBook(context, book.id)
                }
            }.onSuccess { file ->
                cachedBookIds = cachedBookIds + book.id
                openedPdf = file
            }.onFailure { state = BooksState.Error(it.message ?: "Could not open this PDF") }
            openingBookId = null
        }
    }

    fun deleteBook(book: RemoteBook) {
        cachedPdf(book.id).delete()
        cachedBookIds = cachedBookIds - book.id
    }
    LaunchedEffect(Unit) { refresh() }
    if (openedPdf != null) {
        BackHandler { openedPdf = null }
        PdfReader(openedPdf!!, { openedPdf = null })
        return
    }
    BackHandler { if (category != null) { category = null; query = "" } else onBack() }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (val current = state) {
            BooksState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            is BooksState.Error -> ErrorContent(current.message) { scope.launch { refresh() } }
            is BooksState.Ready -> {
                val books = current.books
                if (category == null) LibraryHome(
                    books = books, cachedIds = cachedBookIds,
                    onRefresh = { scope.launch { refresh() } },
                    onCategory = { category = it; query = ""; tab = 0 },
                    onGlobalSearch = { globalSearchOpen = true }
                ) else BookCategoryScreen(
                    category = category!!, books = books, query = query,
                    cachedIds = cachedBookIds, openingId = openingBookId,
                    tab = tab, onTab = { tab = it },
                    onBack = { category = null; query = "" }, onQuery = { query = it },
                    onRead = ::openBook,
                    onDelete = ::deleteBook
                )
            }
        }
        if (globalSearchOpen && state is BooksState.Ready) {
            GlobalBookSearch(
                books = (state as BooksState.Ready).books,
                query = globalQuery,
                onQuery = { globalQuery = it },
                onDismiss = { globalSearchOpen = false; globalQuery = "" },
                cachedIds = cachedBookIds,
                openingId = openingBookId,
                onRead = { book ->
                    globalSearchOpen = false
                    openBook(book)
                },
                onDelete = ::deleteBook
            )
        }
    }
}

@Composable
private fun LibraryHome(books: List<RemoteBook>, cachedIds: Set<String>, onRefresh: () -> Unit, onCategory: (BookCategory) -> Unit, onGlobalSearch: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("My library"), fontSize = 25.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, tr("Refresh library")) }
        }

        OutlinedCard(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onGlobalSearch),
            shape = RoundedCornerShape(18.dp)
        ) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(tr("Search every book, author, or topic"), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(22.dp)); 
        val isLandscape = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val cardWeight = if (isLandscape) .92f else 1f
        Column(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            BookCategory.entries.chunked(2).forEach { row ->
                Row(
                    Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally)
                ) {
                    row.forEach { item ->
                        val matching = books.filter { BookCategory.fromServer(it.category) == item }
                        CategoryCard(
                            item,
                            matching.size,
                            matching.count { it.id in cachedIds },
                            Modifier.weight(cardWeight).fillMaxHeight()
                        ) { onCategory(item) }
                    }
                }
            }
        }
    }
}

@Composable
private fun BookCategoryScreen(category: BookCategory, books: List<RemoteBook>, query: String, cachedIds: Set<String>, openingId: String?, tab: Int, onTab: (Int) -> Unit, onBack: () -> Unit, onQuery: (String) -> Unit, onRead: (RemoteBook) -> Unit, onDelete: (RemoteBook) -> Unit) {
    val shown = books.filter {
        BookCategory.fromServer(it.category) == category &&
            (query.isBlank() || it.title.contains(query, true) || (it.author?.contains(query, true) == true))
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Back")) }
            Text(tr(category.title), fontSize = 25.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        }
        OutlinedTextField(query, onQuery, Modifier.fillMaxWidth(), placeholder = { Text(tr("Search ${tr(category.title).lowercase()}")) }, singleLine = true, shape = RoundedCornerShape(18.dp), leadingIcon = { Icon(Icons.Default.Search, null) }, trailingIcon = { if (query.isNotEmpty()) IconButton({ onQuery("") }) { Icon(Icons.Default.Clear, tr("Clear")) } })
        Spacer(Modifier.height(12.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(tab == 1, { onTab(1) }, SegmentedButtonDefaults.itemShape(0, 2)) { Text(tr("Downloaded (${shown.count { it.id in cachedIds }})")) }
            SegmentedButton(tab == 0, { onTab(0) }, SegmentedButtonDefaults.itemShape(1, 2)) { Text(tr("Catalog")) }
        }
        Spacer(Modifier.height(16.dp))
        val columns = if (LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) 5 else 3
        val displayed = if (tab == 1) shown.filter { it.id in cachedIds } else shown
        if (displayed.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(tr(if (tab == 1) "No downloaded books yet" else "No books in this collection")) }
        else LazyVerticalGrid(GridCells.Fixed(columns), Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
            items(displayed, key = { it.id }) { book -> BookCoverCard(book, category, book.id in cachedIds, openingId == book.id, { onRead(book) }, { onDelete(book) }) }
        }
    }
}

@Composable
private fun GlobalBookSearch(books: List<RemoteBook>, query: String, onQuery: (String) -> Unit, onDismiss: () -> Unit, cachedIds: Set<String>, openingId: String?, onRead: (RemoteBook) -> Unit, onDelete: (RemoteBook) -> Unit) {
    val results = books.filter { book ->
        query.isNotBlank() && (book.title.contains(query, true) || (book.author?.contains(query, true) == true) || book.category.contains(query, true))
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(.96f).fillMaxHeight(.80f), shape = RoundedCornerShape(28.dp), tonalElevation = 8.dp) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Close search")) }
                    Text(tr("Search books"), fontSize = 25.sp, fontWeight = FontWeight.Bold)
                }
                Text(tr("Find something from every collection"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(query, onQuery, Modifier.fillMaxWidth(), placeholder = { Text(tr("Title, author, or topic")) }, leadingIcon = { Icon(Icons.Default.Search, null) }, trailingIcon = { if (query.isNotEmpty()) IconButton({ onQuery("") }) { Icon(Icons.Default.Clear, tr("Clear")) } }, singleLine = true, shape = RoundedCornerShape(18.dp))
                Spacer(Modifier.height(14.dp))
                if (query.isBlank()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(tr("Start typing to search the complete library"), textAlign = TextAlign.Center) }
                else if (results.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(tr("No matching books found")) }
                else {
                    Text(tr("${results.size} ${if (results.size == 1) "result" else "results"}"), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Spacer(Modifier.height(10.dp))
                    val columns = if (LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) 5 else 3
                    LazyVerticalGrid(GridCells.Fixed(columns), Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(results, key = { it.id }) { book -> BookCoverCard(book, BookCategory.fromServer(book.category), book.id in cachedIds, openingId == book.id, { onRead(book) }, { onDelete(book) }) }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryCard(category: BookCategory, total: Int, downloaded: Int, modifier: Modifier, onClick: () -> Unit) {
    val accent = listOf(Color(0xFF625BFF), Color(0xFF00796B), Color(0xFFE0683D), Color(0xFF5B5EAA))[category.ordinal]
    Card(modifier.clickable(onClick = onClick), shape = RoundedCornerShape(26.dp)) {
        Box(Modifier.fillMaxSize()) {
            Image(
                painter = painterResource(category.categoryImage()),
                contentDescription = null,
                // The square bitmap includes seamless padding and a darker-corner gradient.
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Row(
                Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(category.icon, null, Modifier.size(24.dp), Color.White)
                Column {
                    Text(tr(category.title), fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color.White)
                    Spacer(Modifier.height(5.dp))
                    Text(tr("$downloaded downloaded • $total titles"), fontSize = 12.sp, color = Color.White.copy(alpha = .88f))
                }
            }
        }
    }
}

@Composable
private fun BookCoverCard(book: RemoteBook, category: BookCategory, downloaded: Boolean, opening: Boolean, onRead: () -> Unit, onDelete: () -> Unit) {
    val accent = listOf(Color(0xFF342E55), Color(0xFF165A6D), Color(0xFF863E36), Color(0xFF455A35))[category.ordinal]
    Column(Modifier.fillMaxWidth()) {
        Card(Modifier.fillMaxWidth().aspectRatio(.64f).clickable(onClick = onRead), shape = RoundedCornerShape(16.dp)) {
            if (book.coverUrl != null) CoverImage(book.coverUrl, Modifier.fillMaxSize())
            else Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(accent, accent.copy(alpha = .72f)))).padding(12.dp)) {
                Icon(category.icon, null, Modifier.align(Alignment.TopEnd).size(32.dp), Color.White.copy(alpha = .25f))
                Text(book.title, Modifier.align(Alignment.BottomStart), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
            }
        }
        Text(book.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 7.dp))
        Text(tr(book.author ?: "Unknown author"), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(5.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(tr(if (downloaded) "Downloaded" else formatBytes(book.fileSizeBytes)), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            when {
                opening -> CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 2.dp)
                downloaded -> OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier.height(34.dp),
                    contentPadding = PaddingValues(horizontal = 9.dp, vertical = 0.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.Delete, tr("Delete downloaded PDF"), Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp)); Text(tr("Delete"), fontSize = 11.sp)
                }
                else -> FilledTonalButton(
                    onClick = onRead,
                    enabled = book.isDownloadable,
                    modifier = Modifier.height(34.dp),
                    contentPadding = PaddingValues(horizontal = 9.dp, vertical = 0.dp)
                ) {
                    Icon(Icons.Default.Download, tr("Download PDF"), Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp)); Text(tr("Download"), fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun CoverImage(url: String, modifier: Modifier) {
    val bitmap by produceState<Bitmap?>(null, url) { value = withContext(Dispatchers.IO) { runCatching { URL(url).openStream().use(BitmapFactory::decodeStream) }.getOrNull() } }
    Box(modifier.background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
        if (bitmap == null) Icon(Icons.Default.MenuBook, tr("Book cover unavailable"), Modifier.size(34.dp)) else Image(bitmap!!.asImageBitmap(), tr("Book cover"), Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}

@Composable
private fun PdfReader(file: File, onBack: () -> Unit) {
    val renderer = remember(file.path) { PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)) }
    DisposableEffect(renderer) { onDispose { renderer.close() } }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Back")) }
            Text(tr(file.nameWithoutExtension), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(tr("${renderer.pageCount} pages"), fontSize = 12.sp)
        }
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) { items(renderer.pageCount) { PdfPage(renderer, it) } }
    }
}

@Composable
private fun PdfPage(renderer: PdfRenderer, index: Int) {
    val bitmap by produceState<Bitmap?>(null, renderer, index) { value = withContext(Dispatchers.IO) { renderer.openPage(index).use { page -> val width = 1200; Bitmap.createBitmap(width, (width.toFloat() * page.height / page.width).toInt(), Bitmap.Config.ARGB_8888).also { page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY) } } } }
    Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp), contentAlignment = Alignment.Center) { if (bitmap == null) CircularProgressIndicator(Modifier.padding(24.dp)) else Image(bitmap!!.asImageBitmap(), tr("Page ${index + 1}"), Modifier.fillMaxWidth()) }
}

@Composable
private fun ErrorContent(message: String, retry: () -> Unit) = Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) { Text(tr("Could not load books"), fontWeight = FontWeight.Bold); Spacer(Modifier.height(8.dp)); Text(tr(message), textAlign = TextAlign.Center); Spacer(Modifier.height(16.dp)); OutlinedButton(onClick = retry) { Text(tr("Try again")) } } }

private fun formatBytes(bytes: Long) = if (bytes >= 1_000_000) "%.1f MB".format(bytes / 1_000_000.0) else "%.1f KB".format(bytes / 1_000.0)
